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
import com.alfredassistant.alfred_ai.ui.OnboardingScreen
import com.alfredassistant.alfred_ai.ui.OverlayAssistantScreen
import com.alfredassistant.alfred_ai.ui.RichCard
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
        if (::speechHelper.isInitialized) speechHelper.stopGracefully()
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onStop() {
        super.onStop()
        if (::speechHelper.isInitialized) speechHelper.stopGracefully()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(60)
            window.attributes = window.attributes.also { it.blurBehindRadius = 60 }
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }

        ObjectBoxStore.init(this)
        brain = AlfredBrain(this)

        val grantedPermissions = mutableStateOf(getCurrentGrantedPermissions())
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            grantedPermissions.value = getCurrentGrantedPermissions()
            val micDenied = results.containsKey(Manifest.permission.RECORD_AUDIO)
                    && results[Manifest.permission.RECORD_AUDIO] == false
            if (micDenied && !isOnboardingComplete()) { /* let user retry */ }
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
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                    label = "onboarding"
                ) { done ->
                    if (!done) {
                        OnboardingScreen(
                            grantedPermissions = granted,
                            onRequestPermissions = { perms -> permissionLauncher.launch(perms.toTypedArray()) },
                            onFinish = {
                                prefs.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingDone = true
                                val remaining = getAllRequiredPermissions()
                                    .filter { ContextCompat.checkSelfPermission(this@OverlayAssistActivity, it) != PackageManager.PERMISSION_GRANTED }
                                if (remaining.isNotEmpty()) permissionLauncher.launch(remaining.toTypedArray())
                            }
                        )
                    } else {
                        AssistantContent(brain = brain)
                    }
                }
            }
        }
    }

    private fun isOnboardingComplete(): Boolean =
        getSharedPreferences("alfred_onboarding", Context.MODE_PRIVATE)
            .getBoolean("onboarding_complete", false)

    private fun getCurrentGrantedPermissions(): Set<String> {
        val all = onboardingSteps.flatMap { it.permissions }.toSet()
        return all.filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }.toSet()
    }

    private fun getAllRequiredPermissions(): List<String> {
        val base = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base.add(Manifest.permission.READ_MEDIA_IMAGES)
            base.add(Manifest.permission.READ_MEDIA_VIDEO)
            base.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            base.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return base
    }

    @Composable
    private fun AssistantContent(brain: AlfredBrain) {
        var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
        var currentRichCard by remember { mutableStateOf<RichCard?>(null) }
        var audioLevel by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            brain.onDisplayCard = { card ->
                runOnUiThread {
                    currentRichCard = card
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
                context = this@OverlayAssistActivity,
                onListeningStarted = { assistantState = AssistantState.LISTENING },
                onResult = { text ->
                    currentRichCard = null
                    assistantState = AssistantState.PROCESSING
                    scope.launch {
                        val response = brain.processInput(text)
                        if (brain.didRedirect) finish()
                        else speechHelper.speak(response)
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
                        } else if (currentRichCard != null) {
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
                    this@OverlayAssistActivity, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                android.os.Handler(mainLooper).postDelayed({ speechHelper.startListening() }, 300)
            }

            onDispose { speechHelper.shutdown() }
        }

        OverlayAssistantScreen(
            state = assistantState,
            audioLevel = audioLevel,
            richCard = currentRichCard,
            brain = brain,
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
                    currentRichCard = null
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
                    currentRichCard = null
                    assistantState = AssistantState.PROCESSING
                    brain.submitCardAction(actionId)
                }
            },
            onDismiss = { finish() }
        )
    }
}
