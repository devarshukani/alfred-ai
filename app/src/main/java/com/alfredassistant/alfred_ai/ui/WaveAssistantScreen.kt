package com.alfredassistant.alfred_ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.BuildConfig

@Composable
fun WaveAssistantScreen(
    state: AssistantState,
    audioLevel: Float,
    confirmation: ConfirmationRequest?,
    onMicTap: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug overrides
    var debugState by remember { mutableStateOf<AssistantState?>(null) }
    var debugConfirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }

    val activeState = debugState ?: state
    val activeConfirmation = debugConfirmation ?: confirmation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (activeState == AssistantState.IDLE) onDismiss()
            }
    ) {
        // Main content at bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxSize()
        ) {
            ConfirmationBox(
                confirmation = activeConfirmation,
                onOptionSelected = { option ->
                    if (debugConfirmation != null) {
                        debugConfirmation = null
                        debugState = null
                    } else {
                        onOptionSelected(option)
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AlfredWaveBar(
                state = activeState,
                audioLevel = audioLevel,
                onClick = onMicTap
            )
        }

        // Debug buttons — top right, only in debug builds
        if (BuildConfig.DEBUG) {
            DebugPanel(
                onStateChange = { debugState = it },
                onShowConfirmation = {
                    debugState = AssistantState.AWAITING_CONFIRMATION
                    debugConfirmation = ConfirmationRequest(
                        prompt = "I found three contacts for \"mom\". Which one should I call?\n\n1. Mom — +91REDACTED\n2. Rehanatik Shaikh Mom — +918829209160\n3. Sukhmeet Bawa Mom — +91REDACTED",
                        options = listOf(
                            "Call Mom",
                            "Rehanatik Shaikh",
                            "Sukhmeet Bawa",
                            "Cancel"
                        ),
                        buttonStyles = listOf("primary", "primary", "primary", "cancel")
                    )
                },
                onReset = {
                    debugState = null
                    debugConfirmation = null
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp)
            )
        }
    }
}

@Composable
private fun DebugPanel(
    onStateChange: (AssistantState) -> Unit,
    onShowConfirmation: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        DebugChip("Reset", Color(0xFFFF6B6B)) { onReset() }
        DebugChip("Idle", Color(0xFF5B9BFF)) { onStateChange(AssistantState.IDLE) }
        DebugChip("Listen", Color(0xFF5CD07E)) { onStateChange(AssistantState.LISTENING) }
        DebugChip("Process", Color(0xFFA78BFA)) { onStateChange(AssistantState.PROCESSING) }
        DebugChip("Speak", Color(0xFFFFAB5E)) { onStateChange(AssistantState.SPEAKING) }
        DebugChip("Confirm", Color(0xFFFFD166)) { onShowConfirmation() }
    }
}

@Composable
private fun DebugChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
