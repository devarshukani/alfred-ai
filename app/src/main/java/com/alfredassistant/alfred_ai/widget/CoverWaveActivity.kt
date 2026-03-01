package com.alfredassistant.alfred_ai.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.assistant.AlfredBrain
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.speech.SpeechHelper
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import com.alfredassistant.alfred_ai.ui.theme.AlfredaiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen Alfred experience on the Flex Window cover screen.
 * Launches directly into listening mode with the animated wave filling the entire screen.
 * Confirmation dialogs are shown on the cover screen itself.
 */
class CoverWaveActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper
    private lateinit var brain: AlfredBrain
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun finish() {
        if (::speechHelper.isInitialized) speechHelper.stopGracefully()
        super.finish()
    }

    override fun onStop() {
        super.onStop()
        if (::speechHelper.isInitialized) speechHelper.stopGracefully()
    }

    override fun onResume() {
        super.onResume()
        // When returning from a redirected app (e.g. phone call ended),
        // resume listening on the cover screen
        if (::speechHelper.isInitialized) {
            android.os.Handler(mainLooper).postDelayed({
                speechHelper.startListening()
            }, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        ObjectBoxStore.init(this)
        brain = AlfredBrain(this)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            if (!micGranted) {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())

        setContent {
            AlfredaiTheme {
                var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
                var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
                var audioLevel by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(Unit) {
                    brain.onConfirmationNeeded = { request ->
                        runOnUiThread {
                            confirmation = request
                            assistantState = AssistantState.AWAITING_CONFIRMATION
                            speechHelper.speak(request.spokenPrompt)
                        }
                    }
                }

                DisposableEffect(Unit) {
                    speechHelper = SpeechHelper(
                        context = this@CoverWaveActivity,
                        onListeningStarted = { assistantState = AssistantState.LISTENING },
                        onResult = { text ->
                            if (brain.isAwaitingSelection) {
                                speechHelper.stopGracefully()
                                confirmation = null
                                assistantState = AssistantState.PROCESSING
                                brain.submitOptionSelection(text)
                            } else {
                                assistantState = AssistantState.PROCESSING
                                scope.launch {
                                    val response = brain.processInput(text)
                                    // On cover screen: never finish after redirect (e.g. call).
                                    // Just speak the response and resume listening.
                                    speechHelper.speak(response)
                                }
                            }
                        },
                        onSpeakingStarted = {
                            runOnUiThread {
                                if (assistantState != AssistantState.AWAITING_CONFIRMATION) {
                                    assistantState = AssistantState.SPEAKING
                                }
                            }
                        },
                        onSpeakingDone = {
                            runOnUiThread {
                                if (brain.isAwaitingSelection) {
                                    assistantState = AssistantState.AWAITING_CONFIRMATION
                                    speechHelper.startListening()
                                } else {
                                    assistantState = AssistantState.IDLE
                                    speechHelper.startListening()
                                }
                            }
                        },
                        onError = { runOnUiThread { assistantState = AssistantState.IDLE } },
                        onAudioLevel = { level -> audioLevel = level }
                    )
                    speechHelper.init()

                    // Start listening immediately
                    if (ContextCompat.checkSelfPermission(
                            this@CoverWaveActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        android.os.Handler(mainLooper).postDelayed({
                            speechHelper.startListening()
                        }, 300)
                    }

                    onDispose { speechHelper.shutdown() }
                }

                CoverWaveScreen(
                    state = assistantState,
                    audioLevel = audioLevel,
                    confirmation = confirmation,
                    onMicTap = {
                        when (assistantState) {
                            AssistantState.LISTENING -> speechHelper.stopListening()
                            AssistantState.IDLE -> speechHelper.startListening()
                            AssistantState.AWAITING_CONFIRMATION -> speechHelper.startListening()
                            else -> {}
                        }
                    },
                    onOptionSelected = { option ->
                        speechHelper.stopGracefully()
                        confirmation = null
                        assistantState = AssistantState.PROCESSING
                        brain.submitOptionSelection(option)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
