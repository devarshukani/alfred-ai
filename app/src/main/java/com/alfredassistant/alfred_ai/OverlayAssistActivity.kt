package com.alfredassistant.alfred_ai

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
import com.alfredassistant.alfred_ai.speech.SpeechHelper
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import com.alfredassistant.alfred_ai.ui.OverlayAssistantScreen
import com.alfredassistant.alfred_ai.ui.theme.AlfredaiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayAssistActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper
    private lateinit var brain: AlfredBrain
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun finish() {
        if (::speechHelper.isInitialized) {
            speechHelper.stopGracefully()
        }
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onStop() {
        super.onStop()
        // App went off-screen (dismissed, home pressed, task-switched)
        if (::speechHelper.isInitialized) {
            speechHelper.stopGracefully()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable real background blur on Android 12+ for frosted glass effect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(60)
            window.attributes = window.attributes.also {
                it.blurBehindRadius = 60
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }

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

        val neededPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.READ_CONTACTS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.CALL_PHONE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.READ_CALENDAR)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.WRITE_CALENDAR)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }

        setContent {
            AlfredaiTheme {
            var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
            var currentConfirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
            var audioLevel by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(Unit) {
                brain.onConfirmationNeeded = { request ->
                    runOnUiThread {
                        currentConfirmation = request
                        assistantState = AssistantState.AWAITING_CONFIRMATION
                        speechHelper.speak(request.prompt)
                    }
                }
            }

            DisposableEffect(Unit) {
                speechHelper = SpeechHelper(
                    context = this@OverlayAssistActivity,
                    onListeningStarted = {
                        assistantState = AssistantState.LISTENING
                    },
                    onResult = { text ->
                        if (assistantState == AssistantState.AWAITING_CONFIRMATION && currentConfirmation != null) {
                            currentConfirmation = null
                            assistantState = AssistantState.PROCESSING
                            brain.submitOptionSelection(text)
                        } else {
                            assistantState = AssistantState.PROCESSING
                            scope.launch {
                                val response = brain.processInput(text)
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
                            if (assistantState == AssistantState.AWAITING_CONFIRMATION) {
                                speechHelper.startListening()
                            } else {
                                assistantState = AssistantState.IDLE
                                speechHelper.startListening()
                            }
                        }
                    },
                    onError = {
                        runOnUiThread { assistantState = AssistantState.IDLE }
                    },
                    onAudioLevel = { level ->
                        audioLevel = level
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
                }

                onDispose {
                    speechHelper.shutdown()
                }
            }

            OverlayAssistantScreen(
                state = assistantState,
                audioLevel = audioLevel,
                confirmation = currentConfirmation,
                onMicTap = {
                    when (assistantState) {
                        AssistantState.LISTENING -> speechHelper.stopListening()
                        AssistantState.IDLE -> speechHelper.startListening()
                        AssistantState.AWAITING_CONFIRMATION -> speechHelper.startListening()
                        else -> {}
                    }
                },
                onOptionSelected = { selectedOption ->
                    currentConfirmation = null
                    assistantState = AssistantState.PROCESSING
                    brain.submitOptionSelection(selectedOption)
                },
                onDismiss = { finish() }
            )
            }
        }
    }
}
