package com.alfredassistant.alfred_ai.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.alfredassistant.alfred_ai.ui.RichCard
import com.alfredassistant.alfred_ai.ui.theme.AlfredaiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        if (::speechHelper.isInitialized) {
            android.os.Handler(mainLooper).postDelayed({ speechHelper.startListening() }, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        ObjectBoxStore.init(this)
        brain = AlfredBrain(this)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        setContent {
            AlfredaiTheme {
                var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
                var richCard by remember { mutableStateOf<RichCard?>(null) }
                var audioLevel by remember { mutableFloatStateOf(0f) }

                LaunchedEffect(Unit) {
                    brain.onDisplayCard = { card ->
                        runOnUiThread {
                            richCard = card
                            assistantState = AssistantState.DISPLAYING_CARD
                            if (brain.isAwaitingCardAction) {
                                speechHelper.stopListening()
                                if (card.spokenSummary.isNotBlank()) {
                                    speechHelper.speak(card.spokenSummary)
                                }
                            }
                        }
                    }
                }

                DisposableEffect(Unit) {
                    speechHelper = SpeechHelper(
                        context = this@CoverWaveActivity,
                        onListeningStarted = { assistantState = AssistantState.LISTENING },
                        onResult = { text ->
                            richCard = null
                            assistantState = AssistantState.PROCESSING
                            scope.launch {
                                val response = brain.processInput(text)
                                speechHelper.speak(response)
                            }
                        },
                        onSpeakingStarted = {
                            runOnUiThread {
                                if (assistantState != AssistantState.DISPLAYING_CARD) {
                                    assistantState = AssistantState.SPEAKING
                                }
                            }
                        },
                        onSpeakingDone = {
                            runOnUiThread {
                                if (brain.isAwaitingCardAction) {
                                    assistantState = AssistantState.DISPLAYING_CARD
                                    // Don't start listening — user needs to interact with the card
                                } else if (richCard != null) {
                                    assistantState = AssistantState.DISPLAYING_CARD
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

                    if (ContextCompat.checkSelfPermission(
                            this@CoverWaveActivity, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        android.os.Handler(mainLooper).postDelayed({ speechHelper.startListening() }, 300)
                    }

                    onDispose { speechHelper.shutdown() }
                }

                CoverWaveScreen(
                    state = assistantState,
                    audioLevel = audioLevel,
                    richCard = richCard,
                    onMicTap = {
                        when (assistantState) {
                            AssistantState.LISTENING -> speechHelper.stopListening()
                            AssistantState.IDLE -> speechHelper.startListening()
                            AssistantState.DISPLAYING_CARD -> {
                                if (!brain.isAwaitingCardAction) speechHelper.startListening()
                            }
                            AssistantState.SPEAKING -> {
                                speechHelper.stopGracefully()
                                assistantState = AssistantState.IDLE
                                speechHelper.startListening()
                            }
                            else -> {}
                        }
                    },
                    onCardAction = { actionId ->
                        if (actionId.startsWith("dismiss")) {
                            richCard = null
                            assistantState = AssistantState.IDLE
                            brain.submitCardAction(actionId)
                        } else if (actionId.startsWith("directions:")) {
                            val coords = actionId.removePrefix("directions:")
                            val parts = coords.split(",")
                            if (parts.size == 2) {
                                try {
                                    val uri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${parts[0].trim()},${parts[1].trim()}")
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                } catch (_: Exception) {}
                            }
                        } else {
                            richCard = null
                            assistantState = AssistantState.PROCESSING
                            brain.submitCardAction(actionId)
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
