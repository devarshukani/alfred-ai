package com.alfredassistant.alfred_ai.tts

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "SherpaOnnxTts"

// Model: vits-piper-en_GB-southern_english_male-medium
// British male voice — fits the Alfred butler persona
private const val MODEL_DIR = "vits-piper-en_GB-southern_english_male-medium"
private const val MODEL_NAME = "en_GB-southern_english_male-medium.onnx"
private const val DATA_DIR = "$MODEL_DIR/espeak-ng-data"

class SherpaOnnxTts(private val context: Context) {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var stopped = false

    fun init() {
        Log.i(TAG, "Initializing sherpa-onnx TTS")

        // espeak-ng-data must be copied to external files (native code needs filesystem paths)
        val externalDataDir = copyDataDir(DATA_DIR)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_DIR/$MODEL_NAME",
                    tokens = "$MODEL_DIR/tokens.txt",
                    dataDir = externalDataDir,
                    lengthScale = 1.0f,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )

        tts = OfflineTts(assetManager = context.assets, config = config)
        initAudioTrack()
        Log.i(TAG, "sherpa-onnx TTS ready, sampleRate=${tts?.sampleRate()}, speakers=${tts?.numSpeakers()}")
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
     * Generate and play TTS audio. Calls [onStart] when playback begins,
     * [onDone] when finished. Runs blocking — call from a background thread.
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

        // Use generate() instead of generateWithCallback() to avoid
        // JNI lambda desugaring issues with R8/D8 synthetic classes
        val audio = engine.generate(text = text, sid = speakerId, speed = speed)

        if (stopped || audio.samples.isEmpty()) {
            onDone()
            return
        }

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        onStart()

        // Stream in chunks for responsiveness and stop support
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

    // --- Asset copying for espeak-ng-data (native code needs real file paths) ---

    private fun copyDataDir(dataDir: String): String {
        copyAssets(dataDir)
        val base = context.getExternalFilesDir(null)!!.absolutePath
        return "$base/$dataDir"
    }

    private fun copyAssets(path: String) {
        try {
            val list = context.assets.list(path)
            if (list.isNullOrEmpty()) {
                copyFile(path)
            } else {
                val dir = File("${context.getExternalFilesDir(null)}/$path")
                dir.mkdirs()
                for (item in list) {
                    copyAssets("$path/$item")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path: $ex")
        }
    }

    private fun copyFile(filename: String) {
        try {
            val dest = File("${context.getExternalFilesDir(null)}/$filename")
            if (dest.exists()) return // skip if already copied
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
