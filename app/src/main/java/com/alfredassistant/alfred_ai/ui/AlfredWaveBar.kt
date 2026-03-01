package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.alfredassistant.alfred_ai.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

private const val TRANSITION_MS = 600
// 64 stops = overkill-smooth on 8-bit panels, kills all banding
private const val GRAD_STEPS = 64

/**
 * Gaussian radial gradient with dithering-friendly stop count.
 * Uses a wide spread so the falloff is buttery smooth.
 */
private fun smoothRadialBrush(
    center: Offset,
    radius: Float,
    peakColor: Color,
    spread: Float = 0.50f,
    steps: Int = GRAD_STEPS
): Brush {
    val stops = Array(steps) { i ->
        val t = i.toFloat() / (steps - 1)
        val falloff = exp(-(t / spread).pow(2)).toFloat()
        t to lerp(Color.Transparent, peakColor, falloff)
    }
    return Brush.radialGradient(colorStops = stops, center = center, radius = radius)
}

@Composable
fun AlfredWaveBar(
    state: AssistantState,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "glow")

    // Smooth audio to avoid jitter
    val smoothAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(120, easing = EaseInOut),
        label = "sA"
    )

    // Slow drifts for organic movement
    val drift1 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart), "d1"
    )
    val drift2 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), "d2"
    )
    val drift3 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), "d3"
    )
    val drift4 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), "d4"
    )
    val drift5 by inf.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(4100, easing = LinearEasing), RepeatMode.Restart), "d5"
    )

    // Breathing pulses
    val pulse1 by inf.animateFloat(
        0.55f, 1.0f, infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse), "p1"
    )
    val pulse2 by inf.animateFloat(
        0.60f, 1.0f, infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse), "p2"
    )
    val pulse3 by inf.animateFloat(
        0.50f, 1.0f, infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse), "p3"
    )

    // ---- Smooth state transitions ----
    val tw = tween<Float>(TRANSITION_MS, easing = EaseInOut)
    val ctw = tween<Color>(TRANSITION_MS, easing = EaseInOut)

    // How far up the glow reaches (fraction of canvas height from bottom)
    val aReach by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.30f
        AssistantState.LISTENING -> 0.50f
        AssistantState.PROCESSING -> 0.42f
        AssistantState.SPEAKING -> 0.70f
        AssistantState.AWAITING_CONFIRMATION -> 0.38f
    }, tw, label = "aR")

    // Glow radius multiplier
    val aGlowR by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.28f
        AssistantState.LISTENING -> 0.38f
        AssistantState.PROCESSING -> 0.32f
        AssistantState.SPEAKING -> 0.50f
        AssistantState.AWAITING_CONFIRMATION -> 0.30f
    }, tw, label = "aGR")

    // Drift amplitude
    val aDrift by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.04f
        AssistantState.LISTENING -> 0.09f
        AssistantState.PROCESSING -> 0.10f
        AssistantState.SPEAKING -> 0.18f
        AssistantState.AWAITING_CONFIRMATION -> 0.04f
    }, tw, label = "aDf")

    // Peak alpha for glow blobs
    val aGlowAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.35f
        AssistantState.LISTENING -> 0.55f
        AssistantState.PROCESSING -> 0.45f
        AssistantState.SPEAKING -> 0.75f
        AssistantState.AWAITING_CONFIRMATION -> 0.38f
    }, tw, label = "aGA")

    // Core brightness (the bright white-ish center at the very bottom)
    val aCoreAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.25f
        AssistantState.LISTENING -> 0.50f
        AssistantState.PROCESSING -> 0.40f
        AssistantState.SPEAKING -> 0.70f
        AssistantState.AWAITING_CONFIRMATION -> 0.30f
    }, tw, label = "aCA")

    // Colors per state
    data class C5(val a: Color, val b: Color, val c: Color, val d: Color, val e: Color)
    val tc = when (state) {
        AssistantState.IDLE -> C5(WaveBlue, WaveCyan, WavePurple, WaveBlue, WaveCyan)
        AssistantState.LISTENING -> C5(WaveBlue, WaveCyan, WaveGreen, WaveYellow, WaveBlue)
        AssistantState.PROCESSING -> C5(WavePurple, WaveBlue, WavePink, WaveCyan, WavePurple)
        AssistantState.SPEAKING -> C5(WaveOrange, WaveYellow, WaveRed, WavePink, WaveOrange)
        AssistantState.AWAITING_CONFIRMATION -> C5(WaveBlue, WaveCyan, AlfredGoldLight, WaveBlue, WaveCyan)
    }
    val c1 by animateColorAsState(tc.a, ctw, label = "c1")
    val c2 by animateColorAsState(tc.b, ctw, label = "c2")
    val c3 by animateColorAsState(tc.c, ctw, label = "c3")
    val c4 by animateColorAsState(tc.d, ctw, label = "c4")
    val c5 by animateColorAsState(tc.e, ctw, label = "c5")

    val colors = listOf(c1, c2, c3, c4, c5)
    val drifts = listOf(drift1, drift2, drift3, drift4, drift5)
    val pulseList = listOf(pulse1, pulse2, pulse3, pulse1, pulse2)
    // Spread glows across the width — concentrated toward center
    val glowXs = listOf(0.15f, 0.32f, 0.50f, 0.68f, 0.85f)

    // Audio reactivity
    val audioBoost = 1f + smoothAudio * 0.6f
    val audioAlphaBoost = 1f + smoothAudio * 0.4f
    val audioReachBoost = 1f + smoothAudio * 0.3f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)  // compact wave bar
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        val w = size.width
        val h = size.height

        // ========== Layer 1: Soft colored radial glows ==========
        // Each glow is a large, soft radial gradient anchored near the bottom.
        // No masks, no blendModes — just additive-style overlapping translucent circles.
        for (i in 0 until 5) {
            val d = drifts[i]
            val col = colors[i]
            val p = pulseList[i]

            val xc = glowXs[i] * w + sin(d.toDouble()).toFloat() * w * aDrift
            // Anchor glows below canvas bottom so only the top portion of the radial is visible
            // This creates the "rising from below" look
            val rise = aReach * audioReachBoost * 0.25f
            val yc = h * (1.05f - rise) + cos(d.toDouble()).toFloat() * h * 0.05f
            val r = w * aGlowR * p * audioBoost

            val alpha = (aGlowAlpha * p * audioAlphaBoost).coerceAtMost(0.85f)

            drawCircle(
                brush = smoothRadialBrush(
                    center = Offset(xc, yc),
                    radius = r * 2.5f,
                    peakColor = col.copy(alpha = alpha),
                    spread = 0.55f
                ),
                radius = r * 2.5f,
                center = Offset(xc, yc)
            )
        }

        // ========== Layer 2: Bright core glow at bottom center ==========
        // This is the concentrated white-blue hotspot that sells the "light source" feel.
        // Wide radius, centered at the very bottom edge.
        val coreY = h * 1.05f  // slightly below canvas
        val coreR = w * 0.55f * audioBoost
        val coreAlpha = (aCoreAlpha * pulse1 * audioAlphaBoost).coerceAtMost(0.90f)

        // White-ish core — picks up a tint from the primary state color
        val coreColor = lerp(Color.White, c1, 0.3f).copy(alpha = coreAlpha)
        drawCircle(
            brush = smoothRadialBrush(
                center = Offset(w * 0.5f, coreY),
                radius = coreR,
                peakColor = coreColor,
                spread = 0.40f
            ),
            radius = coreR,
            center = Offset(w * 0.5f, coreY)
        )

        // Secondary, wider, dimmer core for extra softness
        val outerCoreColor = lerp(Color.White, c2, 0.5f).copy(alpha = coreAlpha * 0.4f)
        drawCircle(
            brush = smoothRadialBrush(
                center = Offset(w * 0.5f, coreY),
                radius = coreR * 1.6f,
                peakColor = outerCoreColor,
                spread = 0.60f
            ),
            radius = coreR * 1.6f,
            center = Offset(w * 0.5f, coreY)
        )
    }
}
