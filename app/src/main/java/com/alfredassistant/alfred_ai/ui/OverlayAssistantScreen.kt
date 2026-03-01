package com.alfredassistant.alfred_ai.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun OverlayAssistantScreen(
    state: AssistantState,
    audioLevel: Float,
    confirmation: ConfirmationRequest?,
    onMicTap: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    WaveAssistantScreen(
        state = state,
        audioLevel = audioLevel,
        confirmation = confirmation,
        onMicTap = onMicTap,
        onOptionSelected = onOptionSelected,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
