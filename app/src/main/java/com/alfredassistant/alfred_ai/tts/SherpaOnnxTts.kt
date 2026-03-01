package com.alfredassistant.alfred_ai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.alfredassistant.alfred_ai.models.ModelDownloader
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

private const val TAG = "SherpaOnnxTts"

object SherpaOnnxTts {

    var tts: OfflineTts? = null
        private set

    @Volatile
    private var currentTrack: AudioTrack? = null

    @Volatile
    private var stopped = false

    /** Call once from Application or Activity. Safe to call multiple times — loads only once. */
    fun createTts(context: Context) {
        if (tts != null) return
        Log.i(TAG, "Initializing sherpa-onnx TTS")

        val ttsDir = ModelDownloader.getTtsDir(context)
        val modelFile = File(ttsDir, "model.onnx")

        if (!modelFile.exists()) {
            Log.e(TAG, "TTS model.onnx not found in ${ttsDir.absolutePath}. Run model download first.")
            return
        }

        val tokensFile = File(ttsDir, "tokens.txt")
        if (!tokensFile.exists()) {
            Log.e(TAG, "TTS tokens.txt not found in ${ttsDir.absolutePath}. Run model download first.")
            return
        }

        // espeak-ng-data is downloaded during onboarding to the same tts directory
        val espeakDataDir = File(ttsDir, "espeak-ng-data").absolutePath

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = espeakDataDir,
                ),
                numThreads = 2,
                debug = false,
                provider = "nnapi",
            ),
        )

        tts = OfflineTts(config = config)
        Log.i(TAG, "TTS ready — sampleRate=${tts?.sampleRate()} speakers=${tts?.numSpeakers()}")
    }

    private fun createAudioTrack(): AudioTrack {
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

        return AudioTrack(
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
        Log.d(TAG, "🔊 TTS generating: \"$text\"")
        val audio = engine.generate(text = text, sid = speakerId, speed = speed)

        if (stopped || audio.samples.isEmpty()) {
            Log.d(TAG, "🔊 TTS cancelled or empty audio")
            onDone()
            return
        }

        val track = createAudioTrack()
        currentTrack = track

        Log.d(TAG, "🔊 TTS playing — ${audio.samples.size} samples (${audio.samples.size / (tts?.sampleRate() ?: 22050)}s)")
        track.play()
        onStart()

        try {
            val chunkSize = 4096
            var offset = 0
            while (offset < audio.samples.size && !stopped) {
                val end = minOf(offset + chunkSize, audio.samples.size)
                track.write(audio.samples, offset, end - offset, AudioTrack.WRITE_BLOCKING)
                offset = end
            }
            track.stop()
        } catch (e: Exception) {
            Log.w(TAG, "🔊 AudioTrack error during playback: $e")
        } finally {
            track.release()
            if (currentTrack === track) currentTrack = null
        }
        Log.d(TAG, "🔊 TTS done: \"$text\"")
        onDone()
    }

    fun stop() {
        stopped = true
        try {
            currentTrack?.pause()
            currentTrack?.flush()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack already released during stop: $e")
        }
    }

    /**
     * Fade out the current TTS audio over [durationMs] then stop.
     * Call from any thread — the fade runs on a background thread.
     * Invokes [onFaded] on completion (on the bg thread).
     */
    fun stopWithFade(durationMs: Long = 300, onFaded: () -> Unit = {}) {
        val track = currentTrack
        if (track == null || stopped) {
            stopped = true
            onFaded()
            return
        }
        stopped = true // prevent further chunk writes in speak()
        Thread {
            try {
                val steps = 15
                val stepDelay = durationMs / steps
                for (i in steps downTo 0) {
                    val vol = i.toFloat() / steps
                    try {
                        track.setVolume(vol)
                    } catch (_: IllegalStateException) { break }
                    Thread.sleep(stepDelay)
                }
                try {
                    track.pause()
                    track.flush()
                } catch (_: IllegalStateException) { /* already released */ }
            } catch (_: Exception) { /* best-effort fade */ }
            onFaded()
        }.start()
    }

    fun shutdown() {
        stop()
        tts?.free()
        tts = null
    }
}
