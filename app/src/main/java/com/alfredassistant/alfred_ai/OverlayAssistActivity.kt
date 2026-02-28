package com.alfredassistant.alfred_ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.assistant.AlfredBrain
import com.alfredassistant.alfred_ai.speech.SpeechHelper
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.OverlayAssistantScreen

/**
 * Transparent overlay activity that appears on top of whatever is on screen.
 * Triggered when Alfred is set as the default assistant.
 */
class OverlayAssistActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        setContent {
            var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
            var spokenText by remember { mutableStateOf("") }
            var responseText by remember { mutableStateOf("") }

            DisposableEffect(Unit) {
                speechHelper = SpeechHelper(
                    context = this@OverlayAssistActivity,
                    onListeningStarted = {
                        assistantState = AssistantState.LISTENING
                    },
                    onResult = { text ->
                        spokenText = text
                        assistantState = AssistantState.PROCESSING
                        val response = AlfredBrain.generateResponse(text)
                        responseText = response
                        speechHelper.speak(response)
                    },
                    onSpeakingStarted = {
                        runOnUiThread { assistantState = AssistantState.SPEAKING }
                    },
                    onSpeakingDone = {
                        runOnUiThread {
                            assistantState = AssistantState.IDLE
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            responseText = error
                            assistantState = AssistantState.IDLE
                        }
                    }
                )
                speechHelper.init()

                // Auto-start listening when overlay opens
                if (ContextCompat.checkSelfPermission(
                        this@OverlayAssistActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Small delay to let speech recognizer initialize
                    android.os.Handler(mainLooper).postDelayed({
                        speechHelper.startListening()
                    }, 300)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                onDispose {
                    speechHelper.shutdown()
                }
            }

            OverlayAssistantScreen(
                state = assistantState,
                spokenText = spokenText,
                responseText = responseText,
                onMicTap = {
                    when (assistantState) {
                        AssistantState.LISTENING -> speechHelper.stopListening()
                        AssistantState.IDLE -> {
                            spokenText = ""
                            responseText = ""
                            speechHelper.startListening()
                        }
                        else -> { /* ignore taps while processing/speaking */ }
                    }
                },
                onDismiss = { finish() }
            )
        }
    }
}
