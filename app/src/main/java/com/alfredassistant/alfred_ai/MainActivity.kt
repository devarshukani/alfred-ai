package com.alfredassistant.alfred_ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alfredassistant.alfred_ai.assistant.AlfredBrain
import com.alfredassistant.alfred_ai.speech.SpeechHelper
import com.alfredassistant.alfred_ai.ui.AssistantScreen
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.theme.AlfredaiTheme

class MainActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If launched via assistant trigger, redirect to transparent overlay
        if (intent?.action == Intent.ACTION_ASSIST ||
            intent?.action == Intent.ACTION_VOICE_COMMAND ||
            intent?.action == "android.intent.action.SEARCH_LONG_PRESS"
        ) {
            val overlayIntent = Intent(this, OverlayAssistActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(overlayIntent)
            finish()
            return
        }

        enableEdgeToEdge()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
            }
        }

        // Request mic permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            AlfredaiTheme {
                var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
                var spokenText by remember { mutableStateOf("") }
                var responseText by remember { mutableStateOf("") }

                // Initialize speech helper
                DisposableEffect(Unit) {
                    speechHelper = SpeechHelper(
                        context = this@MainActivity,
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
                            runOnUiThread { assistantState = AssistantState.IDLE }
                        },
                        onError = { error ->
                            runOnUiThread {
                                responseText = error
                                assistantState = AssistantState.IDLE
                            }
                        }
                    )
                    speechHelper.init()

                    onDispose {
                        speechHelper.shutdown()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AssistantScreen(
                        state = assistantState,
                        spokenText = spokenText,
                        responseText = responseText,
                        onMicTap = {
                            if (assistantState == AssistantState.LISTENING) {
                                speechHelper.stopListening()
                            } else if (assistantState == AssistantState.IDLE) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    spokenText = ""
                                    responseText = ""
                                    speechHelper.startListening()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onSetDefaultAssistant = {
                            // Open system settings to let user pick default assistant
                            try {
                                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Open Settings > Apps > Default Apps > Digital Assistant",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
