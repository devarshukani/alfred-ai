package com.alfredassistant.alfred_ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.assistant.AlfredBrain
import com.alfredassistant.alfred_ai.db.ObjectBoxStore
import com.alfredassistant.alfred_ai.speech.SpeechHelper
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import com.alfredassistant.alfred_ai.ui.OnboardingScreen
import com.alfredassistant.alfred_ai.ui.OverlayAssistantScreen
import com.alfredassistant.alfred_ai.ui.onboardingSteps
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

        // Initialize ObjectBox before anything that depends on it
        ObjectBoxStore.init(this)

        brain = AlfredBrain(this)

        // Track granted permissions reactively for onboarding
        val grantedPermissions = mutableStateOf(getCurrentGrantedPermissions())

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            // Refresh the full set so onboarding UI updates instantly
            grantedPermissions.value = getCurrentGrantedPermissions()

            // If mic was explicitly denied during onboarding, just let the UI reflect it
            val micDenied = results.containsKey(Manifest.permission.RECORD_AUDIO)
                    && results[Manifest.permission.RECORD_AUDIO] == false
            if (micDenied && !isOnboardingComplete()) {
                // Don't finish — let user retry on the onboarding screen
            }
        }

        val prefs = getSharedPreferences("alfred_onboarding", Context.MODE_PRIVATE)

        setContent {
            AlfredaiTheme {
                var onboardingDone by remember {
                    mutableStateOf(prefs.getBoolean("onboarding_complete", false))
                }
                val granted by grantedPermissions

                AnimatedContent(
                    targetState = onboardingDone,
                    transitionSpec = {
                        fadeIn(tween(500)) togetherWith fadeOut(tween(300))
                    },
                    label = "onboarding"
                ) { done ->
                    if (!done) {
                        OnboardingScreen(
                            grantedPermissions = granted,
                            onRequestPermissions = { perms ->
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            onFinish = {
                                prefs.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingDone = true
                                // Request any remaining permissions the user skipped
                                val remaining = getAllRequiredPermissions()
                                    .filter { ContextCompat.checkSelfPermission(this@OverlayAssistActivity, it) != PackageManager.PERMISSION_GRANTED }
                                if (remaining.isNotEmpty()) {
                                    permissionLauncher.launch(remaining.toTypedArray())
                                }
                            }
                        )
                    } else {
                        AssistantContent(brain = brain)
                    }
                }
            }
        }
    }

    private fun isOnboardingComplete(): Boolean {
        return getSharedPreferences("alfred_onboarding", Context.MODE_PRIVATE)
            .getBoolean("onboarding_complete", false)
    }

    private fun getCurrentGrantedPermissions(): Set<String> {
        val all = onboardingSteps.flatMap { it.permissions }.toSet()
        return all.filter {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }.toSet()
    }

    private fun getAllRequiredPermissions(): List<String> {
        return listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
    }

    @Composable
    private fun AssistantContent(brain: AlfredBrain) {
        var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
        var currentConfirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
        var audioLevel by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            brain.onConfirmationNeeded = { request ->
                runOnUiThread {
                    currentConfirmation = request
                    assistantState = AssistantState.AWAITING_CONFIRMATION
                    speechHelper.speak(request.spokenPrompt)
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
                    if (brain.isAwaitingSelection) {
                        speechHelper.stopGracefully()
                        currentConfirmation = null
                        assistantState = AssistantState.PROCESSING
                        brain.submitOptionSelection(text)
                    } else {
                        assistantState = AssistantState.PROCESSING
                        scope.launch {
                            val response = brain.processInput(text)
                            if (brain.didRedirect) {
                                finish()
                            } else {
                                speechHelper.speak(response)
                            }
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
            brain = brain,
            onMicTap = {
                when (assistantState) {
                    AssistantState.LISTENING -> speechHelper.stopListening()
                    AssistantState.IDLE -> speechHelper.startListening()
                    AssistantState.AWAITING_CONFIRMATION -> speechHelper.startListening()
                    else -> {}
                }
            },
            onOptionSelected = { selectedOption ->
                speechHelper.stopGracefully()
                currentConfirmation = null
                assistantState = AssistantState.PROCESSING
                brain.submitOptionSelection(selectedOption)
            },
            onDismiss = { finish() }
        )
    }
}
