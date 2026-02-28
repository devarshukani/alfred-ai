package com.alfredassistant.alfred_ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OverlayAssistantScreen(
    state: AssistantState,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (state == AssistantState.IDLE) onDismiss()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        AlfredOrb(
            state = state,
            onClick = onMicTap,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}
