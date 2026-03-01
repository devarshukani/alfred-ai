package com.alfredassistant.alfred_ai.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.alfredassistant.alfred_ai.asr.SherpaOnnxAsr
import com.alfredassistant.alfred_ai.tts.SherpaOnnxTts

class SpeechHelper(
    private val context: Context,
    private val onListeningStarted: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingDone: () -> Unit,
    private val onError: (String) -> Unit,
    private val onAudioLevel: (Float) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isSpeaking = false
    private var speakingSimRunnable: Runnable? = null

    fun init() {
        // Initialise sherpa-onnx ASR (model loaded once, reused across calls)
        SherpaOnnxAsr.create(context)
        // Initialise sherpa-onnx TTS singleton (model loaded once, reused across calls)
        SherpaOnnxTts.createTts(context)
    }

    fun startListening() {
        SherpaOnnxAsr.startListening(
            onReady = {
                mainHandler.post { onListeningStarted() }
            },
            onPartial = { /* partial results ignored — we wait for final */ },
            onFinal = { text ->
                // Stop mic immediately so we don't transcribe TTS output
                SherpaOnnxAsr.stopListening()
                mainHandler.post { onResult(text) }
            },
            onError = { msg ->
                mainHandler.post { onError(msg) }
            },
            // Called from audio thread — write directly, Compose state is thread-safe
            onAudioLevel = { level ->
                onAudioLevel(level)
            },
        )
    }

    fun stopListening() {
        SherpaOnnxAsr.stopListening()
    }

    /**
     * Simulate audio level during TTS playback using a varying pattern.
     * TTS doesn't expose real audio levels, so we generate a natural-feeling
     * rhythm that mimics speech cadence.
     */
    private fun startSpeakingSimulation() {
        isSpeaking = true
        var tick = 0L
        speakingSimRunnable = object : Runnable {
            override fun run() {
                if (!isSpeaking) return
                tick++
                // Combine multiple sine waves for natural speech-like rhythm
                val t = tick * 0.08
                val level = (0.4 +
                    0.25 * kotlin.math.sin(t * 2.3) +
                    0.15 * kotlin.math.sin(t * 5.7) +
                    0.10 * kotlin.math.sin(t * 1.1)
                ).toFloat().coerceIn(0.1f, 0.95f)
                onAudioLevel(level)
                handler.postDelayed(this, 50) // ~20fps
            }
        }
        handler.post(speakingSimRunnable!!)
    }

    private fun stopSpeakingSimulation() {
        isSpeaking = false
        speakingSimRunnable?.let { handler.removeCallbacks(it) }
        speakingSimRunnable = null
        onAudioLevel(0f)
    }

    fun speak(text: String) {
        // Stop ASR so we don't transcribe our own TTS output
        SherpaOnnxAsr.stopListening()

        if (SherpaOnnxTts.tts == null) {
            onError("TTS not ready")
            return
        }
        Thread {
            SherpaOnnxTts.speak(
                text = text,
                speakerId = 0,
                speed = 1.0f,
                onStart = {
                    mainHandler.post {
                        onSpeakingStarted()
                        startSpeakingSimulation()
                    }
                },
                onDone = {
                    mainHandler.post {
                        stopSpeakingSimulation()
                        onSpeakingDone()
                    }
                },
            )
        }.start()
    }

    fun shutdown() {
        stopSpeakingSimulation()
        SherpaOnnxAsr.stopListening()
        // Don't shutdown singletons — they live for the app's lifetime
    }
}
