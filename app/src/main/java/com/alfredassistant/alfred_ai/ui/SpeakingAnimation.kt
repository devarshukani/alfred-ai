package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun SpeakingAnimation(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFD4A843),
    glowColor: Color = Color(0xFFE8C96A),
    barCount: Int = 7
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    val phases = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600 + index * 80,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "bar_$index"
        )
    }

    val alpha by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(400),
        label = "alpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                val barWidth = size.width / (barCount * 2.8f)
                val spacing = barWidth * 1.8f
                val totalWidth = barCount * barWidth + (barCount - 1) * (spacing - barWidth)
                val startX = (size.width - totalWidth) / 2f

                for (i in 0 until barCount) {
                    val fraction = if (isAnimating) {
                        (sin(phases[i].value.toDouble()).toFloat() + 1f) / 2f
                    } else {
                        0.1f
                    }
                    val minHeight = size.height * 0.08f
                    val maxHeight = size.height * 0.95f
                    val barHeight = minHeight + (maxHeight - minHeight) * fraction

                    val x = startX + i * spacing
                    val y = (size.height - barHeight) / 2f

                    // Glow bar with gradient
                    val brush = Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = alpha * 0.6f * fraction),
                            barColor.copy(alpha = alpha),
                            glowColor.copy(alpha = alpha * 0.6f * fraction)
                        )
                    )

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}
