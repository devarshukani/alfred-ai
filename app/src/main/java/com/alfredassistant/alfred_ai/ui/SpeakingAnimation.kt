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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun SpeakingAnimation(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF6650a4),
    barCount: Int = 5
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    // Each bar gets its own animated phase offset for a wave effect
    val phases = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 800 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "bar_$index"
        )
    }

    // Animate alpha for fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (isAnimating) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val barWidth = size.width / (barCount * 2.5f)
                val spacing = barWidth * 1.5f
                val totalWidth = barCount * barWidth + (barCount - 1) * (spacing - barWidth)
                val startX = (size.width - totalWidth) / 2f

                for (i in 0 until barCount) {
                    val fraction = if (isAnimating) {
                        (sin(phases[i].value.toDouble()).toFloat() + 1f) / 2f
                    } else {
                        0.15f
                    }
                    val minHeight = size.height * 0.15f
                    val maxHeight = size.height * 0.9f
                    val barHeight = minHeight + (maxHeight - minHeight) * fraction

                    val x = startX + i * spacing
                    val y = (size.height - barHeight) / 2f

                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}
