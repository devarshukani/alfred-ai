package com.alfredassistant.alfred_ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var isSpeaking = false
    private var speakingSimRunnable: Runnable? = null

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

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
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
        if (!ttsReady) {
            onError("TTS not ready")
            return
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStarted()
                handler.post { startSpeakingSimulation() }
            }
            override fun onDone(utteranceId: String?) {
                handler.post { stopSpeakingSimulation() }
                onSpeakingDone()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { stopSpeakingSimulation() }
                onSpeakingDone()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alfred_response")
    }

    fun shutdown() {
        stopSpeakingSimulation()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
