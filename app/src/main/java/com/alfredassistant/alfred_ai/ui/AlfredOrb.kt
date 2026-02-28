package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.alfredassistant.alfred_ai.ui.theme.*
import kotlin.math.sin

@Composable
fun AlfredOrb(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // --- Idle: slow breathing ---
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleBreath"
    )
    val idleGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleGlow"
    )

    // --- Listening: expanding pulse rings ---
    val listenPulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listenPulse1"
    )
    val listenPulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing,
                delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "listenPulse2"
    )
    val listenPulse3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing,
                delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "listenPulse3"
    )

    // --- Processing: rotating arc ---
    val processRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "processRotation"
    )
    val processArc by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "processArc"
    )

    // --- Speaking: rhythmic scale + glow ---
    val speakScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakScale"
    )
    val speakGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakGlow"
    )
    val speakRing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speakRing"
    )

    val coreColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> AlfredGold.copy(alpha = 0.8f)
            AssistantState.LISTENING -> AlfredRed
            AssistantState.PROCESSING -> AlfredAmber
            AssistantState.SPEAKING -> AlfredGold
        },
        animationSpec = tween(500),
        label = "coreColor"
    )

    Box(
        modifier = modifier
            .size(200.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.18f

            when (state) {
                AssistantState.IDLE -> {
                    // Outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                coreColor.copy(alpha = idleGlow),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 2.2f
                        ),
                        radius = baseRadius * 2.2f,
                        center = center
                    )
                    // Core orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlfredGoldLight,
                                coreColor,
                                AlfredGoldDim
                            ),
                            center = center,
                            radius = baseRadius * idleBreath
                        ),
                        radius = baseRadius * idleBreath,
                        center = center
                    )
                    // Thin ring
                    drawCircle(
                        color = AlfredGold.copy(alpha = 0.2f),
                        radius = baseRadius * 1.4f * idleBreath,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                AssistantState.LISTENING -> {
                    // Expanding pulse rings
                    listOf(listenPulse1, listenPulse2, listenPulse3).forEach { pulse ->
                        val ringRadius = baseRadius * (1f + pulse * 1.8f)
                        val ringAlpha = (1f - pulse) * 0.5f
                        drawCircle(
                            color = AlfredRed.copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = (2f - pulse * 1.5f).dp.toPx())
                        )
                    }
                    // Core — slightly larger, solid
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlfredRedGlow,
                                AlfredRed,
                                AlfredRed.copy(alpha = 0.7f)
                            ),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )
                }

                AssistantState.PROCESSING -> {
                    // Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlfredAmber.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 2f
                        ),
                        radius = baseRadius * 2f,
                        center = center
                    )
                    // Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AlfredAmber, AlfredGoldDim),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius * 0.9f,
                        center = center
                    )
                    // Rotating arc
                    rotate(processRotation, pivot = center) {
                        drawArc(
                            color = AlfredAmber,
                            startAngle = 0f,
                            sweepAngle = processArc,
                            useCenter = false,
                            topLeft = Offset(
                                center.x - baseRadius * 1.4f,
                                center.y - baseRadius * 1.4f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                baseRadius * 2.8f,
                                baseRadius * 2.8f
                            ),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                AssistantState.SPEAKING -> {
                    val currentRadius = baseRadius * speakScale

                    // Outer glow burst
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlfredGold.copy(alpha = speakGlow),
                                AlfredGold.copy(alpha = speakGlow * 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = currentRadius * 2.5f
                        ),
                        radius = currentRadius * 2.5f,
                        center = center
                    )
                    // Undulating ring
                    val ringPoints = 60
                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0..ringPoints) {
                        val angle = (i.toFloat() / ringPoints) * 2f * Math.PI.toFloat()
                        val wobble = 1f + sin((angle * 3f + speakRing).toDouble()).toFloat() * 0.08f
                        val r = currentRadius * 1.5f * wobble
                        val x = center.x + r * kotlin.math.cos(angle.toDouble()).toFloat()
                        val y = center.y + r * sin(angle.toDouble()).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = AlfredGold.copy(alpha = 0.4f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    // Core orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlfredGoldLight,
                                AlfredGold,
                                AlfredGoldDim
                            ),
                            center = center,
                            radius = currentRadius
                        ),
                        radius = currentRadius,
                        center = center
                    )
                }
            }
        }
    }
}
