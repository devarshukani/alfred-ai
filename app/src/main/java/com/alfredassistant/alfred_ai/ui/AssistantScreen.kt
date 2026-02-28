package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*

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
        AssistantState.IDLE -> "AWAITING COMMAND"
        AssistantState.LISTENING -> "LISTENING"
        AssistantState.PROCESSING -> "PROCESSING"
        AssistantState.SPEAKING -> "RESPONDING"
    }

    val micColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.LISTENING -> AlfredRed
            AssistantState.PROCESSING -> AlfredAmber
            AssistantState.SPEAKING -> AlfredGold
            else -> AlfredGold
        },
        animationSpec = tween(400),
        label = "micColor"
    )

    // Pulsing ring animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlfredBlack, AlfredDarkGray, AlfredBlack)
                )
            )
    ) {
        // Subtle top decorative line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AlfredGold.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Title
            Text(
                text = "ALFRED",
                style = MaterialTheme.typography.headlineLarge.copy(
                    letterSpacing = 12.sp,
                    fontWeight = FontWeight.Thin
                ),
                color = AlfredGold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Decorative divider under title
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AlfredGold.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = AlfredTextDim
            )

            Spacer(modifier = Modifier.weight(1f))

            // Spoken text
            if (spokenText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AlfredCharcoal.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .border(
                            width = 0.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AlfredGold.copy(alpha = 0.3f),
                                    AlfredGold.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Text(
                        text = "\"$spokenText\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AlfredTextPrimary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Response text
            if (responseText.isNotEmpty()) {
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlfredGoldLight.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mic button with pulse ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Pulse rings when listening
                if (state == AssistantState.LISTENING) {
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val radius = size.minDimension / 2f * pulseScale
                        drawCircle(
                            color = AlfredRed.copy(alpha = pulseAlpha),
                            radius = radius,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Outer ring
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    micColor.copy(alpha = 0.6f),
                                    micColor.copy(alpha = 0.1f),
                                    micColor.copy(alpha = 0.6f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onMicTap,
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = micColor.copy(alpha = 0.3f),
                                spotColor = micColor.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlfredCharcoal,
                            disabledContainerColor = AlfredSlate
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = state == AssistantState.IDLE || state == AssistantState.LISTENING
                    ) {
                        Text(
                            text = if (state == AssistantState.LISTENING) "⏹" else "🎤",
                            fontSize = 30.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Set as default assistant
            TextButton(onClick = onSetDefaultAssistant) {
                Text(
                    text = "SET AS DEFAULT ASSISTANT",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlfredGoldDim
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speaking animation
            SpeakingAnimation(
                isAnimating = state == AssistantState.SPEAKING,
                barColor = AlfredGold,
                glowColor = AlfredGoldLight
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
