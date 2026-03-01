package com.alfredassistant.alfred_ai.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloader"

/**
 * Downloads TTS and embedding ONNX models at runtime (during onboarding).
 * Files are stored in: getExternalFilesDir(null)/models/
 *
 * Downloaded files (~170 MB total):
 *   models/tts/model.onnx           (~60 MB)  — sherpa-onnx compatible (csukuangfj repo)
 *   models/tts/tokens.txt           (~4 KB)
 *   models/tts/espeak-ng-data/      (~18 MB, ~250 files)
 *   models/embedding/model.onnx     (~86 MB)
 *   models/embedding/vocab.txt      (~228 KB)
 */
object ModelDownloader {

    // ── Bump this whenever model URLs change to force re-download ──
    private const val MODEL_VERSION = 3

    // ── HuggingFace repo for TTS model + espeak-ng-data ──
    private const val HF_TTS_REPO = "csukuangfj/vits-piper-en_US-ryan-medium"
    private const val HF_TTS_BASE =
        "https://huggingface.co/$HF_TTS_REPO/resolve/main"
    private const val HF_TTS_API =
        "https://huggingface.co/api/models/$HF_TTS_REPO"

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

    /** Fixed download tasks (large files) */
    private val fixedTasks = listOf(
        DownloadTask("Voice model", TTS_MODEL_URL, "models/tts/model.onnx"),
        DownloadTask("Voice tokens", TTS_TOKENS_URL, "models/tts/tokens.txt"),
        DownloadTask("Language model", EMB_MODEL_URL, "models/embedding/model.onnx"),
        DownloadTask("Vocabulary", EMB_VOCAB_URL, "models/embedding/vocab.txt"),
    )

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("alfred_models", Context.MODE_PRIVATE)

    private fun migrateIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val stored = prefs.getInt("model_version", 0)
        if (stored == MODEL_VERSION) return

        Log.w(TAG, "Model version changed ($stored → $MODEL_VERSION). Wiping old models.")
        val modelsDir = getModelsDir(context)
        if (modelsDir.exists()) {
            modelsDir.deleteRecursively()
            Log.d(TAG, "  Deleted entire models directory")
        }
        prefs.edit().putInt("model_version", MODEL_VERSION).apply()
    }

    /** Check if all model files are present (and correct version) */
    fun isComplete(context: Context): Boolean {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null) ?: return false
        // Check fixed tasks
        val fixedOk = fixedTasks.all { task ->
            val f = File(base, task.relativePath)
            f.exists() && f.length() > 0
        }
        // Check espeak-ng-data critical file
        val espeakOk = File(base, "models/tts/espeak-ng-data/phontab").exists()
        return fixedOk && espeakOk
    }

    /**
     * Download all models including espeak-ng-data.
     * Calls [onProgress] with (stepIndex, totalSteps, stepLabel, bytesDownloaded).
     */
    suspend fun downloadAll(
        context: Context,
        onProgress: (step: Int, totalSteps: Int, label: String, bytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null)
            ?: return@withContext Result.failure(Exception("No external storage"))

        // Total steps = fixed tasks + 1 for espeak-ng-data batch
        val totalSteps = fixedTasks.size + 1

        // 1. Download fixed tasks (model.onnx, tokens.txt, embedding model, vocab)
        for ((index, task) in fixedTasks.withIndex()) {
            val dest = File(base, task.relativePath)
            if (dest.exists() && dest.length() > 0) {
                Log.d(TAG, "Skipping ${task.label} — already exists")
                onProgress(index, totalSteps, task.label, -1)
                continue
            }
            try {
                dest.parentFile?.mkdirs()
                onProgress(index, totalSteps, task.label, 0)
                downloadFile(task.url, dest) { bytes ->
                    onProgress(index, totalSteps, task.label, bytes)
                }
                Log.d(TAG, "Downloaded ${task.label} → ${dest.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ${task.label}: $e")
                dest.delete()
                return@withContext Result.failure(e)
            }
        }

        // 2. Download espeak-ng-data (many small files)
        val espeakStep = fixedTasks.size
        val espeakDir = File(base, "models/tts/espeak-ng-data")
        val espeakMarker = File(espeakDir, ".complete")

        if (espeakMarker.exists()) {
            Log.d(TAG, "Skipping espeak-ng-data — already complete")
            onProgress(espeakStep, totalSteps, "Voice data", -1)
        } else {
            try {
                onProgress(espeakStep, totalSteps, "Voice data", 0)
                downloadEspeakData(base, espeakDir) { bytes ->
                    onProgress(espeakStep, totalSteps, "Voice data", bytes)
                }
                // Mark complete so we don't re-download
                espeakMarker.createNewFile()
                Log.d(TAG, "espeak-ng-data download complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download espeak-ng-data: $e")
                return@withContext Result.failure(e)
            }
        }

        getPrefs(context).edit().putInt("model_version", MODEL_VERSION).apply()
        Result.success(Unit)
    }

    /**
     * Fetch the file list from HuggingFace API and download all espeak-ng-data/ files.
     */
    private fun downloadEspeakData(base: File, espeakDir: File, onBytes: (Long) -> Unit) {
        // Fetch repo file list from HuggingFace API
        val conn = followRedirects(HF_TTS_API)
        if (conn.responseCode != 200) {
            conn.disconnect()
            throw Exception("Failed to fetch repo info: HTTP ${conn.responseCode}")
        }
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val repo = JSONObject(json)
        val siblings = repo.getJSONArray("siblings")

        // Collect all espeak-ng-data files
        val espeakFiles = mutableListOf<String>()
        for (i in 0 until siblings.length()) {
            val rfilename = siblings.getJSONObject(i).getString("rfilename")
            if (rfilename.startsWith("espeak-ng-data/")) {
                espeakFiles.add(rfilename)
            }
        }

        Log.d(TAG, "espeak-ng-data: ${espeakFiles.size} files to download")
        var totalBytes = 0L

        for (filename in espeakFiles) {
            val dest = File(base, "models/tts/$filename")
            if (dest.exists() && dest.length() > 0) continue

            dest.parentFile?.mkdirs()
            val url = "$HF_TTS_BASE/$filename"
            downloadFile(url, dest) { bytes ->
                // Report cumulative bytes across all espeak files
            }
            totalBytes += dest.length()
            onBytes(totalBytes)
        }
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
