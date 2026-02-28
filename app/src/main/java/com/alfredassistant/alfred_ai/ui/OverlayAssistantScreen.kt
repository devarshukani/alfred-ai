package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*

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

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Full screen — tap outside to dismiss
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume clicks */ }
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Text bubble
            if (spokenText.isNotEmpty() || responseText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AlfredBlack.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(horizontal = 36.dp, vertical = 8.dp)
                        .border(
                            width = 0.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AlfredGold.copy(alpha = 0.4f),
                                    AlfredGold.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (spokenText.isNotEmpty()) {
                            Text(
                                text = "\"$spokenText\"",
                                color = AlfredTextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (responseText.isNotEmpty()) {
                            if (spokenText.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = responseText,
                                color = AlfredGoldLight,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status
            Text(
                text = statusText,
                color = AlfredTextDim,
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mic button with pulse
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                if (state == AssistantState.LISTENING) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val radius = size.minDimension / 2f * pulseScale
                        drawCircle(
                            color = AlfredRed.copy(alpha = pulseAlpha),
                            radius = radius,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // Outer ring
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    micColor.copy(alpha = 0.7f),
                                    micColor.copy(alpha = 0.1f),
                                    micColor.copy(alpha = 0.7f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onMicTap,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = micColor.copy(alpha = 0.4f),
                                spotColor = micColor.copy(alpha = 0.6f)
                            )
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlfredCharcoal.copy(alpha = 0.95f)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = when (state) {
                                AssistantState.LISTENING -> "⏹"
                                AssistantState.SPEAKING -> "🔊"
                                AssistantState.PROCESSING -> "⏳"
                                else -> "🎤"
                            },
                            fontSize = 24.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speaking animation
            SpeakingAnimation(
                isAnimating = state == AssistantState.SPEAKING,
                barColor = AlfredGold,
                glowColor = AlfredGoldLight
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
