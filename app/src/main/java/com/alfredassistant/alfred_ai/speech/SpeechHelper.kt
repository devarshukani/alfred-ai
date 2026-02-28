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
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var sherpaTts: SherpaOnnxTts? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListeningStarted() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Speech error ($error)"
                }
                onError(msg)
            }
            override fun onResults(results: Bundle?) {
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
                onStart = { mainHandler.post { onSpeakingStarted() } },
                onDone = { mainHandler.post { onSpeakingDone() } },
            )
        }.start()
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        sherpaTts?.shutdown()
        sherpaTts = null
    }
}
