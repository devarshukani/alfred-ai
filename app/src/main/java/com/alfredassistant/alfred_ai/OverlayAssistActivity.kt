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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayAssistActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper
    private val brain = AlfredBrain()
    private val scope = CoroutineScope(Dispatchers.Main)

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

            DisposableEffect(Unit) {
                speechHelper = SpeechHelper(
                    context = this@OverlayAssistActivity,
                    onListeningStarted = {
                        assistantState = AssistantState.LISTENING
                    },
                    onResult = { text ->
                        assistantState = AssistantState.PROCESSING
                        scope.launch {
                            val response = brain.processInput(text)
                            speechHelper.speak(response)
                        }
                    },
                    onSpeakingStarted = {
                        runOnUiThread { assistantState = AssistantState.SPEAKING }
                    },
                    onSpeakingDone = {
                        runOnUiThread { assistantState = AssistantState.IDLE }
                    },
                    onError = {
                        runOnUiThread { assistantState = AssistantState.IDLE }
                    }
                )
                speechHelper.init()

                if (ContextCompat.checkSelfPermission(
                        this@OverlayAssistActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
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
                onMicTap = {
                    when (assistantState) {
                        AssistantState.LISTENING -> speechHelper.stopListening()
                        AssistantState.IDLE -> speechHelper.startListening()
                        else -> {}
                    }
                },
                onDismiss = { finish() }
            )
        }
    }
}
