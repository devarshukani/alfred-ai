package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverlayAssistantScreen(
    state: AssistantState,
    spokenText: String,
    responseText: String,
    onMicTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        AssistantState.IDLE -> "Tap to speak"
        AssistantState.LISTENING -> "Listening..."
        AssistantState.PROCESSING -> "Thinking..."
        AssistantState.SPEAKING -> "Speaking..."
    }

    val micColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.LISTENING -> Color(0xFFE53935)
            AssistantState.PROCESSING -> Color(0xFFFFA726)
            AssistantState.SPEAKING -> Color(0xFF6650a4)
            else -> Color(0xFF6650a4)
        },
        animationSpec = tween(300),
        label = "micColor"
    )

    // Full screen transparent — tap outside to dismiss
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
        // Bottom card with the controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume clicks so they don't dismiss */ }
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Text bubble for spoken/response text
            if (spokenText.isNotEmpty() || responseText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (spokenText.isNotEmpty()) {
                            Text(
                                text = "\"$spokenText\"",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (responseText.isNotEmpty()) {
                            if (spokenText.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = responseText,
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status label
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mic button — center circle
            Button(
                onClick = onMicTap,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = micColor),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = when (state) {
                        AssistantState.LISTENING -> "⏹"
                        AssistantState.SPEAKING -> "🔊"
                        AssistantState.PROCESSING -> "⏳"
                        else -> "🎤"
                    },
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Speaking animation
            SpeakingAnimation(
                isAnimating = state == AssistantState.SPEAKING,
                barColor = Color.White
            )
        }
    }
}
