package com.alfredassistant.alfred_ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.alfredassistant.alfred_ai.tts.SherpaOnnxTts
import java.util.Locale

class SpeechHelper(
    private val context: Context,
    private val onListeningStarted: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingDone: () -> Unit,
    private val onError: (String) -> Unit,
    private val onAudioLevel: (Float) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isSpeaking = false
    private var speakingSimRunnable: Runnable? = null
    private var sherpaTts: SherpaOnnxTts? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningStarted() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Normalize RMS: typically -2 to 10 dB → 0.0 to 1.0
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onAudioLevel(normalized)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onAudioLevel(0f) }
            override fun onError(error: Int) {
                onAudioLevel(0f)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Speech error ($error)"
                }
                onError(msg)
            }
            override fun onResults(results: Bundle?) {
                onAudioLevel(0f)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) onResult(text)
                else onError("Didn't catch that.")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Initialize sherpa-onnx TTS (British male voice — Alfred the butler)
        sherpaTts = SherpaOnnxTts(context).also { it.init() }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
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
        val tts = sherpaTts
        if (tts == null) {
            onError("TTS not ready")
            return
        }
        // sherpa-onnx generate+stream is blocking, run on background thread
        Thread {
            tts.speak(
                text = text,
                speakerId = 0, // speaker 0 of the southern_english_male model
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
        speechRecognizer?.destroy()
        sherpaTts?.shutdown()
        sherpaTts = null
    }
}
