package com.alfredassistant.alfred_ai.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alfredassistant.alfred_ai.assistant.AlfredBrain

@Composable
fun OverlayAssistantScreen(
    state: AssistantState,
    audioLevel: Float,
    confirmation: ConfirmationRequest?,
    brain: AlfredBrain?,
    onMicTap: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    WaveAssistantScreen(
        state = state,
        audioLevel = audioLevel,
        confirmation = confirmation,
        brain = brain,
        onMicTap = onMicTap,
        onOptionSelected = onOptionSelected,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
