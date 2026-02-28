package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

@Composable
fun AssistantScreen(
    state: AssistantState,
    spokenText: String,
    responseText: String,
    onMicTap: () -> Unit,
    onSetDefaultAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        AssistantState.IDLE -> "Tap the mic or say something"
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Title
            Text(
                text = "Alfred",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Show what user said
            if (spokenText.isNotEmpty()) {
                Text(
                    text = "\"$spokenText\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Show response text
            if (responseText.isNotEmpty()) {
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mic button
            Button(
                onClick = onMicTap,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = micColor),
                contentPadding = PaddingValues(0.dp),
                enabled = state == AssistantState.IDLE || state == AssistantState.LISTENING
            ) {
                Text(
                    text = if (state == AssistantState.LISTENING) "⏹" else "🎤",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Set as default assistant button
            TextButton(onClick = onSetDefaultAssistant) {
                Text("Set as Default Assistant")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speaking animation at the bottom
            SpeakingAnimation(
                isAnimating = state == AssistantState.SPEAKING,
                barColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
