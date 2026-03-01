package com.alfredassistant.alfred_ai.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.alfredassistant.alfred_ai.assistant.AlfredBrain

@Composable
fun OverlayAssistantScreen(
    state: AssistantState,
    audioLevel: Float,
    richCard: RichCard?,
    brain: AlfredBrain?,
    onMicTap: () -> Unit,
    onCardAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    WaveAssistantScreen(
        state = state,
        audioLevel = audioLevel,
        richCard = richCard,
        brain = brain,
        onMicTap = onMicTap,
        onCardAction = onCardAction,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
