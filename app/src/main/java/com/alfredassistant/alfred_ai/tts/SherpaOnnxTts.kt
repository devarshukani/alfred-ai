package com.alfredassistant.alfred_ai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SherpaOnnxTts"

// Generic asset directory — the setup script places any model here.
// Change the model by editing setup-sherpa-tts.sh only.
private const val MODEL_DIR = "tts-model"
private const val DATA_DIR = "$MODEL_DIR/espeak-ng-data"

object SherpaOnnxTts {

    var tts: OfflineTts? = null
        private set

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var stopped = false

    /** Call once from Application or Activity. Safe to call multiple times — loads only once. */
    fun createTts(context: Context) {
        if (tts != null) return
        Log.i(TAG, "Initializing sherpa-onnx TTS")

        val assets = context.assets

        // Find the .onnx model file automatically
        val modelName = assets.list(MODEL_DIR)
            ?.firstOrNull { it.endsWith(".onnx") }
            ?: error("No .onnx model found in assets/$MODEL_DIR")

        // espeak-ng-data must live on the real filesystem for native code
        val externalDataDir = copyDataDir(context, DATA_DIR)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_DIR/$modelName",
                    tokens = "$MODEL_DIR/tokens.txt",
                    dataDir = externalDataDir,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )

        tts = OfflineTts(assetManager = assets, config = config)
        initAudioTrack()
        Log.i(TAG, "TTS ready — model=$modelName sampleRate=${tts?.sampleRate()} speakers=${tts?.numSpeakers()}")
    }

    private fun initAudioTrack() {
        val sampleRate = tts?.sampleRate() ?: 22050
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        audioTrack = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    /**
     * Generate and play TTS audio. Blocking — call from a background thread.
     */
    fun speak(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        val engine = tts ?: run {
            Log.e(TAG, "TTS not initialized")
            onDone()
            return
        }

        stopped = false
        val audio = engine.generate(text = text, sid = speakerId, speed = speed)

        if (stopped || audio.samples.isEmpty()) {
            onDone()
            return
        }

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        onStart()

        val chunkSize = 4096
        var offset = 0
        while (offset < audio.samples.size && !stopped) {
            val end = minOf(offset + chunkSize, audio.samples.size)
            audioTrack?.write(audio.samples, offset, end - offset, AudioTrack.WRITE_BLOCKING)
            offset = end
        }

        audioTrack?.stop()
        onDone()
    }

    fun stop() {
        stopped = true
        audioTrack?.pause()
        audioTrack?.flush()
    }

    fun shutdown() {
        stop()
        audioTrack?.release()
        audioTrack = null
        tts?.free()
        tts = null
    }

    // --- Asset copying (espeak-ng-data needs real filesystem paths) ---

    private fun copyDataDir(context: Context, dataDir: String): String {
        copyAssets(context, dataDir)
        return "${context.getExternalFilesDir(null)!!.absolutePath}/$dataDir"
    }

    private fun copyAssets(context: Context, path: String) {
        try {
            val list = context.assets.list(path)
            if (list.isNullOrEmpty()) {
                copyFile(context, path)
            } else {
                File("${context.getExternalFilesDir(null)}/$path").mkdirs()
                for (item in list) {
                    copyAssets(context, "$path/$item")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path: $ex")
        }
    }

    private fun copyFile(context: Context, filename: String) {
        try {
            val dest = File("${context.getExternalFilesDir(null)}/$filename")
            if (dest.exists()) return
            context.assets.open(filename).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename: $ex")
        }
    }
}
