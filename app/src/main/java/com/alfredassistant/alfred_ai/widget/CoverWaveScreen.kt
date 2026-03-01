package com.alfredassistant.alfred_ai.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.alfredassistant.alfred_ai.ui.AssistantState
import com.alfredassistant.alfred_ai.ui.ConfirmationBox
import com.alfredassistant.alfred_ai.ui.ConfirmationRequest
import com.alfredassistant.alfred_ai.ui.theme.*
import kotlin.math.*

private const val TRANSITION_MS = 600
private const val GRAD_STEPS = 56

private fun smoothRadialBrush(
    center: Offset,
    radius: Float,
    peakColor: Color,
    spread: Float = 0.55f,
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
fun CoverWaveScreen(
    state: AssistantState,
    audioLevel: Float,
    confirmation: ConfirmationRequest?,
    onMicTap: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (state == AssistantState.IDLE) onDismiss()
            }
    ) {
        FullScreenWaveCanvas(
            state = state,
            audioLevel = audioLevel,
            onClick = onMicTap,
            modifier = Modifier.fillMaxSize()
        )

        ConfirmationBox(
            confirmation = confirmation,
            onOptionSelected = onOptionSelected,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun FullScreenWaveCanvas(
    state: AssistantState,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "coverGlow")

    val smoothAudio by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(120, easing = EaseInOut),
        label = "sA"
    )

    // 5 large blobs — fewer but much bigger for a cleaner look
    val drifts = (0 until 5).map { i ->
        inf.animateFloat(
            -1f, 1f,
            infiniteRepeatable(
                tween(5000 + i * 700, easing = EaseInOut),
                RepeatMode.Reverse
            ),
            "d$i"
        ).value
    }

    val pulses = (0 until 5).map { i ->
        inf.animateFloat(
            0.65f, 1.0f,
            infiniteRepeatable(
                tween(2000 + i * 300, easing = EaseInOut),
                RepeatMode.Reverse
            ),
            "p$i"
        ).value
    }

    val tw = tween<Float>(TRANSITION_MS, easing = EaseInOut)
    val ctw = tween<Color>(TRANSITION_MS, easing = EaseInOut)

    val aGlowR by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 1.2f
        AssistantState.LISTENING -> 1.6f
        AssistantState.PROCESSING -> 1.4f
        AssistantState.SPEAKING -> 1.9f
        AssistantState.AWAITING_CONFIRMATION -> 1.3f
    }, tw, label = "aGR")

    val aDrift by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.10f
        AssistantState.LISTENING -> 0.18f
        AssistantState.PROCESSING -> 0.14f
        AssistantState.SPEAKING -> 0.25f
        AssistantState.AWAITING_CONFIRMATION -> 0.10f
    }, tw, label = "aDf")

    val aGlowAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.50f
        AssistantState.LISTENING -> 0.70f
        AssistantState.PROCESSING -> 0.60f
        AssistantState.SPEAKING -> 0.90f
        AssistantState.AWAITING_CONFIRMATION -> 0.52f
    }, tw, label = "aGA")

    val aCoreAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.25f
        AssistantState.LISTENING -> 0.50f
        AssistantState.PROCESSING -> 0.40f
        AssistantState.SPEAKING -> 0.70f
        AssistantState.AWAITING_CONFIRMATION -> 0.30f
    }, tw, label = "aCA")

    data class C5(val a: Color, val b: Color, val c: Color, val d: Color, val e: Color)

    val tc = when (state) {
        AssistantState.IDLE -> C5(WaveBlue, WaveCyan, WavePurple, WaveBlue, WaveCyan)
        AssistantState.LISTENING -> C5(WaveBlue, WaveCyan, WaveGreen, WaveYellow, WaveBlue)
        AssistantState.PROCESSING -> C5(WavePurple, WaveBlue, WavePink, WaveCyan, WavePurple)
        AssistantState.SPEAKING -> C5(WaveOrange, WaveYellow, WaveRed, WavePink, WaveOrange)
        AssistantState.AWAITING_CONFIRMATION -> C5(WaveBlue, WaveCyan, AlfredGoldLight, WaveBlue, WaveCyan)
    }
    val colors = listOf(
        animateColorAsState(tc.a, ctw, label = "c0").value,
        animateColorAsState(tc.b, ctw, label = "c1").value,
        animateColorAsState(tc.c, ctw, label = "c2").value,
        animateColorAsState(tc.d, ctw, label = "c3").value,
        animateColorAsState(tc.e, ctw, label = "c4").value,
    )

    // 5 blobs spread wide — anchored at edges and center for full coverage
    val blobPositions = listOf(
        0.15f to 0.20f,
        0.85f to 0.25f,
        0.50f to 0.50f,
        0.20f to 0.80f,
        0.80f to 0.75f,
    )

    val audioBoost = 1f + smoothAudio * 0.5f
    val audioAlphaBoost = 1f + smoothAudio * 0.35f

    Canvas(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        val w = size.width
        val h = size.height
        val diag = hypot(w, h)

        for (i in 0 until 5) {
            val (bx, by) = blobPositions[i]
            val p = pulses[i]
            val col = colors[i]

            val cx = bx * w + drifts[i] * w * aDrift
            val cy = by * h + drifts[(i + 2) % 5] * h * aDrift
            val r = diag * aGlowR * p * audioBoost * 0.5f

            val alpha = (aGlowAlpha * p * audioAlphaBoost).coerceAtMost(0.90f)

            drawCircle(
                brush = smoothRadialBrush(
                    center = Offset(cx, cy),
                    radius = r,
                    peakColor = col.copy(alpha = alpha),
                    spread = 0.60f
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        // Large soft core glow at center
        val corePulse = pulses[2]
        val coreR = diag * 0.8f * corePulse * audioBoost
        val coreAlpha = (aCoreAlpha * corePulse * audioAlphaBoost).coerceAtMost(0.80f)
        val coreColor = lerp(Color.White, colors[0], 0.25f).copy(alpha = coreAlpha)

        drawCircle(
            brush = smoothRadialBrush(
                center = Offset(w * 0.5f, h * 0.5f),
                radius = coreR,
                peakColor = coreColor,
                spread = 0.50f
            ),
            radius = coreR,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Secondary outer halo for extra depth
        val haloR = diag * 1.1f * pulses[0] * audioBoost
        val haloColor = lerp(Color.White, colors[1], 0.4f)
            .copy(alpha = (coreAlpha * 0.3f).coerceAtMost(0.35f))

        drawCircle(
            brush = smoothRadialBrush(
                center = Offset(w * 0.5f, h * 0.5f),
                radius = haloR,
                peakColor = haloColor,
                spread = 0.65f
            ),
            radius = haloR,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}
