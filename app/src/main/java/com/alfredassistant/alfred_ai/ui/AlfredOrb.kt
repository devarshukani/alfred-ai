package com.alfredassistant.alfred_ai.ui

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.alfredassistant.alfred_ai.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AlfredOrb(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // === IDLE: gentle breathing ===
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = EaseInOut), RepeatMode.Reverse
        ), label = "idleBreath"
    )
    val idleGlow by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = EaseInOut), RepeatMode.Reverse
        ), label = "idleGlow"
    )

    // === LISTENING: staggered ripple rings ===
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing), RepeatMode.Restart
        ), label = "ripple1"
    )
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing, delayMillis = 600),
            RepeatMode.Restart
        ), label = "ripple2"
    )
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing, delayMillis = 1200),
            RepeatMode.Restart
        ), label = "ripple3"
    )
    val listenPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = EaseInOut), RepeatMode.Reverse
        ), label = "listenPulse"
    )

    // === PROCESSING: dual counter-rotating arcs ===
    val procRot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = LinearEasing), RepeatMode.Restart
        ), label = "procRot1"
    )
    val procRot2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing), RepeatMode.Restart
        ), label = "procRot2"
    )
    val procArc by infiniteTransition.animateFloat(
        initialValue = 50f, targetValue = 200f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = EaseInOut), RepeatMode.Reverse
        ), label = "procArc"
    )

    // === SPEAKING: multi-wave rings ===
    val speakPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing), RepeatMode.Restart
        ), label = "speakPhase"
    )
    val speakPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(2200, easing = LinearEasing), RepeatMode.Restart
        ), label = "speakPhase2"
    )
    val speakScale by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(350, easing = EaseInOut), RepeatMode.Reverse
        ), label = "speakScale"
    )
    val speakGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            tween(350, easing = EaseInOut), RepeatMode.Reverse
        ), label = "speakGlow"
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
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val baseR = size.minDimension * 0.19f

            // Core orb gradient — always gold, always solid
            val coreGradient = Brush.radialGradient(
                colors = listOf(
                    AlfredGoldLight,
                    AlfredGold,
                    AlfredGoldDim.copy(alpha = 0.9f)
                ),
                center = center
            )

            when (state) {
                AssistantState.IDLE -> {
                    val r = baseR * idleBreath
                    // Soft outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(AlfredGold.copy(alpha = idleGlow), Color.Transparent),
                            center = center, radius = r * 2.2f
                        ),
                        radius = r * 2.2f, center = center
                    )
                    // Thin outer ring
                    drawCircle(
                        color = AlfredGold.copy(alpha = 0.15f),
                        radius = r * 1.5f, center = center,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    // Core
                    drawCircle(brush = coreGradient, radius = r, center = center)
                }

                AssistantState.LISTENING -> {
                    val r = baseR * listenPulse
                    // Ripple rings expanding outward
                    listOf(ripple1, ripple2, ripple3).forEach { progress ->
                        val ringR = r * (1.2f + progress * 1.6f)
                        val alpha = (1f - progress) * 0.45f
                        val width = (2.5f - progress * 2f).coerceAtLeast(0.3f)
                        drawCircle(
                            color = AlfredGold.copy(alpha = alpha),
                            radius = ringR, center = center,
                            style = Stroke(width = width.dp.toPx())
                        )
                    }
                    // Core
                    drawCircle(brush = coreGradient, radius = r, center = center)
                }

                AssistantState.PROCESSING -> {
                    val r = baseR * 0.95f
                    // Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(AlfredGold.copy(alpha = 0.15f), Color.Transparent),
                            center = center, radius = r * 2.2f
                        ),
                        radius = r * 2.2f, center = center
                    )
                    // Core
                    drawCircle(brush = coreGradient, radius = r, center = center)
                    // Outer rotating arc
                    val arcR = r * 1.5f
                    val arcRect = Size(arcR * 2, arcR * 2)
                    val arcTopLeft = Offset(cx - arcR, cy - arcR)
                    rotate(procRot1, pivot = center) {
                        drawArc(
                            color = AlfredGold.copy(alpha = 0.7f),
                            startAngle = 0f, sweepAngle = procArc,
                            useCenter = false,
                            topLeft = arcTopLeft, size = arcRect,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    // Inner counter-rotating arc
                    val arcR2 = r * 1.25f
                    val arcRect2 = Size(arcR2 * 2, arcR2 * 2)
                    val arcTopLeft2 = Offset(cx - arcR2, cy - arcR2)
                    rotate(procRot2, pivot = center) {
                        drawArc(
                            color = AlfredGoldLight.copy(alpha = 0.5f),
                            startAngle = 0f, sweepAngle = procArc * 0.6f,
                            useCenter = false,
                            topLeft = arcTopLeft2, size = arcRect2,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                AssistantState.SPEAKING -> {
                    val r = baseR * speakScale
                    // Outer glow burst
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                AlfredGold.copy(alpha = speakGlow),
                                AlfredGold.copy(alpha = speakGlow * 0.2f),
                                Color.Transparent
                            ),
                            center = center, radius = r * 2.8f
                        ),
                        radius = r * 2.8f, center = center
                    )
                    // Wave ring 1 — fast, tight wobble
                    drawWaveRing(
                        cx = cx, cy = cy,
                        radius = r * 1.45f,
                        phase = speakPhase,
                        waves = 5, amplitude = 0.12f,
                        color = AlfredGold.copy(alpha = 0.5f),
                        strokeWidth = 1.8f
                    )
                    // Wave ring 2 — slower, wider wobble
                    drawWaveRing(
                        cx = cx, cy = cy,
                        radius = r * 1.85f,
                        phase = speakPhase2,
                        waves = 4, amplitude = 0.1f,
                        color = AlfredGoldLight.copy(alpha = 0.3f),
                        strokeWidth = 1.2f
                    )
                    // Wave ring 3 — outermost, subtle
                    drawWaveRing(
                        cx = cx, cy = cy,
                        radius = r * 2.2f,
                        phase = -speakPhase * 0.7f,
                        waves = 6, amplitude = 0.06f,
                        color = AlfredGold.copy(alpha = 0.15f),
                        strokeWidth = 0.8f
                    )
                    // Core
                    drawCircle(brush = coreGradient, radius = r, center = center)
                }

                AssistantState.AWAITING_CONFIRMATION -> {
                    val r = baseR * idleBreath
                    // Gentle pulsing glow — waiting state
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(AlfredAmber.copy(alpha = idleGlow * 1.5f), Color.Transparent),
                            center = center, radius = r * 2.5f
                        ),
                        radius = r * 2.5f, center = center
                    )
                    // Dashed-style double ring
                    drawCircle(
                        color = AlfredAmber.copy(alpha = 0.3f),
                        radius = r * 1.6f, center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = AlfredGold.copy(alpha = 0.2f),
                        radius = r * 1.35f, center = center,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    // Core
                    drawCircle(brush = coreGradient, radius = r, center = center)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveRing(
    cx: Float, cy: Float,
    radius: Float, phase: Float,
    waves: Int, amplitude: Float,
    color: Color, strokeWidth: Float
) {
    val points = 80
    val path = Path()
    for (i in 0..points) {
        val angle = (i.toFloat() / points) * 2f * Math.PI.toFloat()
        val wobble = 1f + sin((angle * waves + phase).toDouble()).toFloat() * amplitude
        val r = radius * wobble
        val x = cx + r * cos(angle.toDouble()).toFloat()
        val y = cy + r * sin(angle.toDouble()).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth.dp.toPx()))
}
