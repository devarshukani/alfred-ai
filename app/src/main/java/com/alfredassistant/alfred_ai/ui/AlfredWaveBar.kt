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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.alfredassistant.alfred_ai.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

private const val TRANSITION_MS = 600
private const val GRAD_STEPS = 32 // enough stops for zero banding

/**
 * Attempt to build a smooth Gaussian radial gradient.
 * f(t) = exp(-(t/spread)^2) — very wide, very soft.
 * [steps] color stops eliminate all banding.
 */
private fun smoothRadialBrush(
    center: Offset,
    radius: Float,
    peakColor: Color,
    spread: Float = 0.45f,
    steps: Int = GRAD_STEPS
): Brush {
    val stops = Array(steps) { i ->
        val t = i.toFloat() / (steps - 1)
        // Gaussian with configurable spread — lower spread = wider glow
        val falloff = exp(-(t / spread).pow(2)).toFloat()
        t to lerp(Color.Transparent, peakColor, falloff)
    }
    return Brush.radialGradient(colorStops = stops, center = center, radius = radius)
}

/**
 * Smooth horizontal Gaussian brush.
 */
private fun smoothHorizontalBrush(
    centerFraction: Float,
    peakColor: Color,
    spread: Float = 0.30f,
    steps: Int = GRAD_STEPS
): Brush {
    val stops = Array(steps) { i ->
        val t = i.toFloat() / (steps - 1)
        val dist = (t - centerFraction) / spread
        val falloff = exp(-(dist * dist)).toFloat()
        t to lerp(Color.Transparent, peakColor, falloff)
    }
    return Brush.horizontalGradient(colorStops = stops)
}

@Composable
fun AlfredWaveBar(
    state: AssistantState,
    audioLevel: Float,  // 0.0 (silent) to 1.0 (loud)
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "glow")

    // Smooth the audio level to avoid jitter
    val smoothAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(120, easing = EaseInOut),
        label = "sA"
    )

    // Drift for floating glows
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

    // Horizontal shift for gradient layers
    val hShift1 by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), "h1"
    )
    val hShift2 by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart), "h2"
    )
    val hShift3 by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4400, easing = LinearEasing), RepeatMode.Restart), "h3"
    )

    // Breathing
    val pulse1 by inf.animateFloat(
        0.55f, 1.0f, infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse), "p1"
    )
    val pulse2 by inf.animateFloat(
        0.60f, 1.0f, infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse), "p2"
    )
    val pulse3 by inf.animateFloat(
        0.50f, 1.0f, infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse), "p3"
    )
    val whitePulse by inf.animateFloat(
        0.80f, 1.0f, infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), "wp"
    )

    // ---- Smooth state transitions ----
    val tw = tween<Float>(TRANSITION_MS, easing = EaseInOut)
    val ctw = tween<Color>(TRANSITION_MS, easing = EaseInOut)

    val aBase by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.85f; AssistantState.LISTENING -> 0.95f
        AssistantState.PROCESSING -> 0.90f; AssistantState.SPEAKING -> 1.0f
        AssistantState.AWAITING_CONFIRMATION -> 0.87f
    }, tw, label = "aB")

    val aLayerAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.22f; AssistantState.LISTENING -> 0.45f
        AssistantState.PROCESSING -> 0.36f; AssistantState.SPEAKING -> 0.55f
        AssistantState.AWAITING_CONFIRMATION -> 0.28f
    }, tw, label = "aLA")

    val aReach by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.35f; AssistantState.LISTENING -> 0.55f
        AssistantState.PROCESSING -> 0.48f; AssistantState.SPEAKING -> 0.95f
        AssistantState.AWAITING_CONFIRMATION -> 0.42f
    }, tw, label = "aR")

    val aGlowR by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.15f; AssistantState.LISTENING -> 0.21f
        AssistantState.PROCESSING -> 0.18f; AssistantState.SPEAKING -> 0.34f
        AssistantState.AWAITING_CONFIRMATION -> 0.17f
    }, tw, label = "aGR")

    val aDrift by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.06f; AssistantState.LISTENING -> 0.11f
        AssistantState.PROCESSING -> 0.12f; AssistantState.SPEAKING -> 0.22f
        AssistantState.AWAITING_CONFIRMATION -> 0.05f
    }, tw, label = "aDf")

    val aGlowAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.20f; AssistantState.LISTENING -> 0.40f
        AssistantState.PROCESSING -> 0.30f; AssistantState.SPEAKING -> 0.65f
        AssistantState.AWAITING_CONFIRMATION -> 0.23f
    }, tw, label = "aGA")

    // Colors
    data class C5(val a: Color, val b: Color, val c: Color, val d: Color, val e: Color)
    val tc = when (state) {
        AssistantState.IDLE -> C5(WaveBlue, WaveCyan, WavePurple, WaveBlue, WaveCyan)
        AssistantState.LISTENING -> C5(WaveBlue, WaveCyan, WaveGreen, WaveYellow, WaveBlue)
        AssistantState.PROCESSING -> C5(WavePurple, WaveBlue, WavePink, WaveCyan, WavePurple)
        AssistantState.SPEAKING -> C5(WaveOrange, WaveYellow, WaveRed, WavePink, WaveOrange)
        AssistantState.AWAITING_CONFIRMATION -> C5(AlfredGold, AlfredAmber, AlfredGoldLight, WaveYellow, AlfredGold)
    }
    val c1 by animateColorAsState(tc.a, ctw, label = "c1")
    val c2 by animateColorAsState(tc.b, ctw, label = "c2")
    val c3 by animateColorAsState(tc.c, ctw, label = "c3")
    val c4 by animateColorAsState(tc.d, ctw, label = "c4")
    val c5 by animateColorAsState(tc.e, ctw, label = "c5")

    val colors = listOf(c1, c2, c3, c4, c5)
    val drifts = listOf(drift1, drift2, drift3, drift4, drift5)
    val hShifts = listOf(hShift1, hShift2, hShift3)
    val pulseList = listOf(pulse1, pulse2, pulse3, pulse1, pulse2)
    val glowXs = listOf(0.10f, 0.30f, 0.50f, 0.70f, 0.90f)

    // Audio reactivity multipliers
    val audioBoost = 1f + smoothAudio * 0.75f       // size boost
    val audioAlphaBoost = 1f + smoothAudio * 0.5f   // brightness boost
    val audioReachBoost = 1f + smoothAudio * 0.4f    // height boost

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        val w = size.width
        val h = size.height

        // ========== White base — sigmoid, starts lower ==========
        val bA = aBase * whitePulse
        val baseSteps = GRAD_STEPS
        val baseStops = Array(baseSteps) { i ->
            val t = i.toFloat() / (baseSteps - 1)
            // Sigmoid pushed down — mid at 0.7 so white only appears in bottom 30%
            val k = 12f
            val mid = 0.70f
            val sigmoid = (1.0 / (1.0 + exp((-k * (t - mid)).toDouble()))).toFloat()
            t to Color.White.copy(alpha = bA * sigmoid)
        }
        drawRect(
            brush = Brush.verticalGradient(colorStops = baseStops),
            size = size
        )

        // ========== Colored horizontal bands — confined to bottom half ==========
        for (i in 0 until 3) {
            val phase = hShifts[i] * 2f * PI.toFloat()
            val cx = 0.5f + sin(phase.toDouble()).toFloat() * 0.35f
            // Limit reach so bands stay in bottom portion
            val layerReach = (aReach * (0.5f + i * 0.08f) * audioReachBoost).coerceAtMost(0.85f)
            val fadeStart = (1f - layerReach).coerceAtLeast(0.15f)
            val alpha = aLayerAlpha * pulseList[i] * audioAlphaBoost
            val col = colors[i].copy(alpha = alpha.coerceAtMost(1f))

            drawRect(
                brush = smoothHorizontalBrush(
                    centerFraction = cx,
                    peakColor = col,
                    spread = 0.32f
                ),
                topLeft = Offset(0f, h * fadeStart),
                size = Size(w, h * layerReach)
            )
        }

        // ========== Floating radial glows — anchored to bottom, fading at top ==========
        for (i in 0 until 5) {
            val d = drifts[i]
            val col = colors[i]
            val p = pulseList[i]

            val xc = glowXs[i] * w + sin(d.toDouble()).toFloat() * w * aDrift
            // Push glows toward bottom, but allow them to rise during SPEAKING
            val yc = h * (0.75f - aReach * 0.15f) + cos(d.toDouble()).toFloat() * h * 0.08f
            val r = w * aGlowR * p * audioBoost
            val center = Offset(xc, yc)

            // Vertical fade: glow is full strength at bottom, fades toward top
            // yFade = how far down the center is (1.0 = bottom, 0.0 = top)
            // Glows near the top of canvas get reduced alpha
            val yFade = (yc / h).coerceIn(0f, 1f)
            val alpha = (aGlowAlpha * p * audioAlphaBoost * yFade).coerceAtMost(1f)

            drawCircle(
                brush = smoothRadialBrush(
                    center = center,
                    radius = r * 2.8f,
                    peakColor = col.copy(alpha = alpha),
                    spread = 0.45f
                ),
                radius = r * 2.8f,
                center = center
            )
        }

        // ========== Top fade mask — erases everything above bottom portion ==========
        // DstOut: Black areas erase content, Transparent areas keep content
        // This ensures seamless blend with the transparent overlay background
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,           // fully erase at top
                    0.30f to Color.Black,           // still fully erased
                    0.50f to Color.Black.copy(alpha = 0.7f),
                    0.65f to Color.Black.copy(alpha = 0.2f),
                    0.75f to Color.Transparent,     // fully visible from here down
                    1.0f to Color.Transparent
                )
            ),
            size = size,
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOut
        )
    }
}
