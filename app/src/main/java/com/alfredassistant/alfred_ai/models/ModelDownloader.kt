package com.alfredassistant.alfred_ai.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloader"

/**
 * Downloads TTS and embedding ONNX models at runtime (during onboarding).
 * Files are stored in: getExternalFilesDir(null)/models/
 *
 * Downloaded files (~150 MB total):
 *   models/tts/model.onnx        (~60 MB)  — sherpa-onnx compatible (csukuangfj repo)
 *   models/tts/tokens.txt        (~4 KB)   — matching tokens for the model
 *   models/embedding/model.onnx   (~86 MB)
 *   models/embedding/vocab.txt    (~228 KB)
 *
 * Kept in APK assets (small, never change):
 *   models/tts/espeak-ng-data/    (~18 MB, 120 dictionary files)
 */
object ModelDownloader {

    // ── Bump this whenever model URLs change to force re-download ──
    private const val MODEL_VERSION = 2

    // ── Sherpa-onnx compatible TTS model (Ryan medium, English) ──
    // From csukuangfj's HuggingFace repo — has correct metadata for sherpa-onnx
    private const val HF_TTS_BASE =
        "https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-medium/resolve/main"
    private const val TTS_MODEL_URL = "$HF_TTS_BASE/en_US-ryan-medium.onnx"
    private const val TTS_TOKENS_URL = "$HF_TTS_BASE/tokens.txt"

    // ── Embedding model (all-MiniLM-L6-v2) ──
    private const val EMB_MODEL_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
    private const val EMB_VOCAB_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt"

    data class DownloadTask(
        val label: String,
        val url: String,
        val relativePath: String
    )

    private val tasks = listOf(
        DownloadTask("Voice model", TTS_MODEL_URL, "models/tts/model.onnx"),
        DownloadTask("Voice tokens", TTS_TOKENS_URL, "models/tts/tokens.txt"),
        DownloadTask("Language model", EMB_MODEL_URL, "models/embedding/model.onnx"),
        DownloadTask("Vocabulary", EMB_VOCAB_URL, "models/embedding/vocab.txt"),
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("alfred_models", Context.MODE_PRIVATE)

    /**
     * If the stored model version doesn't match [MODEL_VERSION], delete all
     * downloaded model files so they get re-downloaded from the correct URLs.
     */
    private fun migrateIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val stored = prefs.getInt("model_version", 0)
        if (stored == MODEL_VERSION) return

        Log.w(TAG, "Model version changed ($stored → $MODEL_VERSION). Wiping old models.")
        val base = context.getExternalFilesDir(null) ?: return
        for (task in tasks) {
            val f = File(base, task.relativePath)
            if (f.exists()) {
                f.delete()
                Log.d(TAG, "  Deleted ${task.relativePath}")
            }
        }
        prefs.edit().putInt("model_version", MODEL_VERSION).apply()
    }

    /** Check if all model files are already present (and correct version) */
    fun isComplete(context: Context): Boolean {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null) ?: return false
        return tasks.all { task ->
            val f = File(base, task.relativePath)
            f.exists() && f.length() > 0
        }
    }

    /**
     * Download all models. Calls [onProgress] with (stepIndex, totalSteps, stepLabel, bytesDownloaded).
     * Skips files that already exist.
     */
    suspend fun downloadAll(
        context: Context,
        onProgress: (step: Int, totalSteps: Int, label: String, bytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null)
            ?: return@withContext Result.failure(Exception("No external storage"))

        for ((index, task) in tasks.withIndex()) {
            val dest = File(base, task.relativePath)

            if (dest.exists() && dest.length() > 0) {
                Log.d(TAG, "Skipping ${task.label} — already exists")
                onProgress(index, tasks.size, task.label, -1)
                continue
            }

            try {
                dest.parentFile?.mkdirs()
                onProgress(index, tasks.size, task.label, 0)
                downloadFile(task.url, dest) { bytes ->
                    onProgress(index, tasks.size, task.label, bytes)
                }
                Log.d(TAG, "Downloaded ${task.label} → ${dest.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ${task.label}: $e")
                // Clean up partial file
                dest.delete()
                return@withContext Result.failure(e)
            }
        }
        // Mark version so future migrations know these files are correct
        getPrefs(context).edit().putInt("model_version", MODEL_VERSION).apply()
        Result.success(Unit)
    }

    private fun downloadFile(urlStr: String, dest: File, onBytes: (Long) -> Unit) {
        var conn: HttpURLConnection? = null
        try {
            conn = followRedirects(urlStr)
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode} for $urlStr")
            }

            val tmpFile = File(dest.absolutePath + ".tmp")
            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
            }
            tmpFile.renameTo(dest)
        } finally {
            conn?.disconnect()
        }
    }

    /** Follow HTTP redirects (HuggingFace redirects to CDN, sometimes with relative URLs) */
    private fun followRedirects(urlStr: String, maxRedirects: Int = 10): HttpURLConnection {
        var url = URL(urlStr)
        var redirects = 0
        while (redirects < maxRedirects) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw Exception("Redirect without Location header")
                conn.disconnect()
                // Handle both absolute and relative redirect URLs
                url = if (location.startsWith("http://") || location.startsWith("https://")) {
                    URL(location)
                } else {
                    URL(url, location)
                }
                redirects++
            } else {
                return conn
            }
        }
        throw Exception("Too many redirects for $urlStr")
    }

    fun getModelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "models")

    fun getTtsDir(context: Context): File =
        File(getModelsDir(context), "tts")

    fun getEmbeddingDir(context: Context): File =
        File(getModelsDir(context), "embedding")
}
