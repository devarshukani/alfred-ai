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
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningStarted() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
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

        SherpaOnnxTts.createTts(context)
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

    private fun startSpeakingSimulation() {
        isSpeaking = true
        var tick = 0L
        speakingSimRunnable = object : Runnable {
            override fun run() {
                if (!isSpeaking) return
                tick++
                val t = tick * 0.08
                val level = (0.4 +
                    0.25 * kotlin.math.sin(t * 2.3) +
                    0.15 * kotlin.math.sin(t * 5.7) +
                    0.10 * kotlin.math.sin(t * 1.1)
                ).toFloat().coerceIn(0.1f, 0.95f)
                onAudioLevel(level)
                handler.postDelayed(this, 50)
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
        // Stop recognizer so we don't pick up our own TTS
        speechRecognizer?.stopListening()

        // Strip any markdown that the LLM might have snuck in
        val cleanText = stripMarkdown(text)

        if (SherpaOnnxTts.tts == null) {
            onError("TTS not ready")
            return
        }
        Thread {
            SherpaOnnxTts.speak(
                text = cleanText,
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

    /**
     * Strip markdown formatting characters that sound bad when read aloud.
     */
    private fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // **bold**
            .replace(Regex("\\*(.+?)\\*"), "$1")          // *italic*
            .replace(Regex("__(.+?)__"), "$1")            // __bold__
            .replace(Regex("_(.+?)_"), "$1")              // _italic_
            .replace(Regex("```[\\s\\S]*?```"), "")       // code blocks
            .replace(Regex("`(.+?)`"), "$1")              // inline code
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "") // headers
            .replace(Regex("^[\\-*]\\s+", RegexOption.MULTILINE), "") // bullet points
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "") // numbered lists
            .trim()
    }

    /**
     * Gracefully stop all speech activity with a smooth audio fade-out.
     * Use when the UI is dismissed or goes off-screen.
     */
    fun stopGracefully() {
        stopSpeakingSimulation()
        speechRecognizer?.stopListening()
        SherpaOnnxTts.stopWithFade(durationMs = 300)
    }

    fun shutdown() {
        stopSpeakingSimulation()
        speechRecognizer?.destroy()
        // Don't shutdown the TTS singleton — it lives for the app's lifetime
    }
}
