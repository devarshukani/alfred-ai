package com.alfredassistant.alfred_ai.models

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloader"

/**
 * Downloads TTS and embedding ONNX models at runtime (during onboarding).
 * Files are stored in: getExternalFilesDir(null)/models/
 *
 * TTS bundle (~80 MB tar.bz2) is downloaded as a single archive from sherpa-onnx
 * GitHub releases, then extracted. Contains model.onnx, tokens.txt, espeak-ng-data/.
 *
 * Embedding model (~86 MB) + vocab (~228 KB) downloaded individually via DownloadManager.
 */
object ModelDownloader {

    private const val MODEL_VERSION = 3

    private const val TTS_MODEL_NAME = "vits-piper-en_US-ryan-medium"
    private const val TTS_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$TTS_MODEL_NAME.tar.bz2"

    private const val EMB_MODEL_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
    private const val EMB_VOCAB_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("alfred_models", Context.MODE_PRIVATE)

    private fun migrateIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val stored = prefs.getInt("model_version", 0)
        if (stored == MODEL_VERSION) return
        Log.w(TAG, "Model version changed ($stored → $MODEL_VERSION). Wiping old models.")
        val modelsDir = getModelsDir(context)
        if (modelsDir.exists()) modelsDir.deleteRecursively()
        prefs.edit().putInt("model_version", MODEL_VERSION).apply()
    }

    fun isComplete(context: Context): Boolean {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null) ?: return false
        val ttsOk = File(base, "models/tts/model.onnx").let { it.exists() && it.length() > 0 }
        val espeakOk = File(base, "models/tts/espeak-ng-data/phontab").exists()
        val embOk = File(base, "models/embedding/model.onnx").let { it.exists() && it.length() > 0 }
        val vocabOk = File(base, "models/embedding/vocab.txt").let { it.exists() && it.length() > 0 }
        return ttsOk && espeakOk && embOk && vocabOk
    }

    /**
     * Download all models. 3 steps:
     *   0 → TTS archive (download + extract)
     *   1 → Embedding model
     *   2 → Vocabulary
     */
    suspend fun downloadAll(
        context: Context,
        onProgress: (step: Int, totalSteps: Int, label: String, bytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        migrateIfNeeded(context)
        val base = context.getExternalFilesDir(null)
            ?: return@withContext Result.failure(Exception("No external storage"))

        val totalSteps = 3
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Step 0: TTS archive (model + tokens + espeak-ng-data in one tar.bz2)
        val ttsDir = File(base, "models/tts")
        val ttsComplete = File(ttsDir, "model.onnx").let { it.exists() && it.length() > 0 }
                && File(ttsDir, "espeak-ng-data/phontab").exists()

        if (ttsComplete) {
            Log.d(TAG, "Skipping TTS — already extracted")
            onProgress(0, totalSteps, "Voice pack", -1)
        } else {
            try {
                onProgress(0, totalSteps, "Voice pack", 0)
                val archive = File(base, "tts-archive.tar.bz2")
                downloadWithManager(dm, TTS_ARCHIVE_URL, archive, "Voice pack") { bytes ->
                    onProgress(0, totalSteps, "Voice pack", bytes)
                }
                onProgress(0, totalSteps, "Extracting voice pack", -1)
                extractTarBz2(archive, ttsDir)
                archive.delete()
                Log.d(TAG, "TTS archive extracted to ${ttsDir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed TTS download/extract: $e")
                return@withContext Result.failure(e)
            }
        }

        // Step 1: Embedding model
        val embModel = File(base, "models/embedding/model.onnx")
        if (embModel.exists() && embModel.length() > 0) {
            Log.d(TAG, "Skipping embedding model — already exists")
            onProgress(1, totalSteps, "Language model", -1)
        } else {
            try {
                embModel.parentFile?.mkdirs()
                onProgress(1, totalSteps, "Language model", 0)
                downloadWithManager(dm, EMB_MODEL_URL, embModel, "Language model") { bytes ->
                    onProgress(1, totalSteps, "Language model", bytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed embedding model download: $e")
                embModel.delete()
                return@withContext Result.failure(e)
            }
        }

        // Step 2: Vocabulary
        val vocabFile = File(base, "models/embedding/vocab.txt")
        if (vocabFile.exists() && vocabFile.length() > 0) {
            Log.d(TAG, "Skipping vocab — already exists")
            onProgress(2, totalSteps, "Vocabulary", -1)
        } else {
            try {
                vocabFile.parentFile?.mkdirs()
                onProgress(2, totalSteps, "Vocabulary", 0)
                downloadWithManager(dm, EMB_VOCAB_URL, vocabFile, "Vocabulary") { bytes ->
                    onProgress(2, totalSteps, "Vocabulary", bytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed vocab download: $e")
                vocabFile.delete()
                return@withContext Result.failure(e)
            }
        }

        getPrefs(context).edit().putInt("model_version", MODEL_VERSION).apply()
        Result.success(Unit)
    }

    /**
     * Extract tar.bz2 archive. The archive contains a top-level folder
     * (e.g. "vits-piper-en_US-ryan-medium/") — we strip it and extract
     * contents directly into [destDir].
     */
    private fun extractTarBz2(archive: File, destDir: File) {
        destDir.mkdirs()
        val prefix = "$TTS_MODEL_NAME/"

        archive.inputStream().buffered().use { fileIn ->
            BZip2CompressorInputStream(BufferedInputStream(fileIn)).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        // Strip the top-level directory prefix
                        val name = if (entry.name.startsWith(prefix))
                            entry.name.removePrefix(prefix)
                        else entry.name

                        if (name.isBlank()) { entry = tar.nextEntry; continue }

                        val outFile = File(destDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                tar.copyTo(out, bufferSize = 16384)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }

        // Rename the onnx file to model.onnx (archive has "en_US-ryan-medium.onnx")
        val onnxFiles = destDir.listFiles { f -> f.extension == "onnx" && f.name != "model.onnx" }
        onnxFiles?.firstOrNull()?.renameTo(File(destDir, "model.onnx"))
    }

    /**
     * Resolve redirects (302s from GitHub/HuggingFace) to get the final direct URL.
     * DownloadManager doesn't always follow redirects reliably on all OEMs.
     */
    private fun resolveRedirects(url: String): String {
        var currentUrl = url
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "Alfred-AI/1.0")
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 301..303 || code == 307 || code == 308) {
                currentUrl = location ?: break
                redirects++
            } else {
                break
            }
        }
        return currentUrl
    }

    /**
     * Download a file using Android DownloadManager with redirect pre-resolution.
     * We resolve 302 chains first (GitHub releases, HuggingFace CDN) because
     * DownloadManager can silently stall on some redirect chains depending on OEM.
     */
    private suspend fun downloadWithManager(
        dm: DownloadManager, url: String, dest: File, label: String,
        onBytes: (Long) -> Unit
    ) {
        val tmpFile = File(dest.absolutePath + ".tmp")
        tmpFile.delete()
        dest.parentFile?.mkdirs()

        // Resolve redirects so DownloadManager gets a direct CDN URL
        val directUrl = try { resolveRedirects(url) } catch (_: Exception) { url }
        Log.d(TAG, "Resolved $label URL: $directUrl")

        val request = DownloadManager.Request(Uri.parse(directUrl))
            .setTitle("Alfred: $label")
            .setDescription("Downloading AI model")
            .setDestinationUri(Uri.fromFile(tmpFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val downloadId = dm.enqueue(request)
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var complete = false
            var stallCount = 0
            var lastBytes = -1L

            while (!complete) {
                dm.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                    val currentBytes = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L

                    when (cursor.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            onBytes(currentBytes)
                            complete = true
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            throw Exception("Download failed (reason=$reason) for $url")
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            onBytes(currentBytes)
                            // Detect stall: bytes haven't changed for ~30s (60 polls × 500ms)
                            if (currentBytes == lastBytes) stallCount++ else stallCount = 0
                            lastBytes = currentBytes
                            if (stallCount > 60) {
                                throw Exception("Download stalled at $currentBytes bytes for $url")
                            }
                        }
                        DownloadManager.STATUS_PENDING -> {
                            // Detect stuck pending: hasn't started for ~30s
                            stallCount++
                            if (stallCount > 60) {
                                throw Exception("Download stuck in PENDING state for $url")
                            }
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            Log.w(TAG, "Download paused (reason=$reason) for $label")
                        }
                    }
                }
                if (!complete) delay(500)
            }
            if (tmpFile.exists()) {
                dest.delete()
                tmpFile.renameTo(dest)
            }
            if (!dest.exists() || dest.length() == 0L) {
                throw Exception("Download produced empty file for $url")
            }
        } catch (e: Exception) {
            dm.remove(downloadId)
            tmpFile.delete()
            throw e
        }
    }

    fun getModelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "models")

    fun getTtsDir(context: Context): File =
        File(getModelsDir(context), "tts")

    fun getEmbeddingDir(context: Context): File =
        File(getModelsDir(context), "embedding")
}
