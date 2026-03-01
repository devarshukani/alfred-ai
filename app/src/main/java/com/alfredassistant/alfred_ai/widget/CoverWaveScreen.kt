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
private const val GRAD_STEPS = 48

/**
 * Smooth Gaussian radial gradient brush.
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

/**
 * Full-screen animated wave composable for the Flex Window cover screen.
 * The wave animation fills the entire display (not just a bottom bar).
 * Confirmation dialogs overlay on top.
 */
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
        // Full-screen wave canvas
        FullScreenWaveCanvas(
            state = state,
            audioLevel = audioLevel,
            onClick = onMicTap,
            modifier = Modifier.fillMaxSize()
        )

        // Confirmation box centered on cover screen
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

    // 7 drift phases for organic blob movement across the full screen
    val drifts = (0 until 7).map { i ->
        inf.animateFloat(
            0f, 2f * PI.toFloat(),
            infiniteRepeatable(
                tween(3400 + i * 400, easing = LinearEasing),
                RepeatMode.Restart
            ),
            "d$i"
        ).value
    }

    // Breathing pulses
    val pulses = (0 until 7).map { i ->
        inf.animateFloat(
            0.55f, 1.0f,
            infiniteRepeatable(
                tween(1400 + i * 200, easing = EaseInOut),
                RepeatMode.Reverse
            ),
            "p$i"
        ).value
    }

    val tw = tween<Float>(TRANSITION_MS, easing = EaseInOut)
    val ctw = tween<Color>(TRANSITION_MS, easing = EaseInOut)

    // Glow radius — larger since we fill the whole screen
    val aGlowR by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.35f
        AssistantState.LISTENING -> 0.45f
        AssistantState.PROCESSING -> 0.40f
        AssistantState.SPEAKING -> 0.55f
        AssistantState.AWAITING_CONFIRMATION -> 0.38f
    }, tw, label = "aGR")

    // Drift amplitude
    val aDrift by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.08f
        AssistantState.LISTENING -> 0.15f
        AssistantState.PROCESSING -> 0.12f
        AssistantState.SPEAKING -> 0.22f
        AssistantState.AWAITING_CONFIRMATION -> 0.08f
    }, tw, label = "aDf")

    // Peak alpha
    val aGlowAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.40f
        AssistantState.LISTENING -> 0.60f
        AssistantState.PROCESSING -> 0.50f
        AssistantState.SPEAKING -> 0.80f
        AssistantState.AWAITING_CONFIRMATION -> 0.42f
    }, tw, label = "aGA")

    // Core brightness
    val aCoreAlpha by animateFloatAsState(when (state) {
        AssistantState.IDLE -> 0.20f
        AssistantState.LISTENING -> 0.45f
        AssistantState.PROCESSING -> 0.35f
        AssistantState.SPEAKING -> 0.65f
        AssistantState.AWAITING_CONFIRMATION -> 0.25f
    }, tw, label = "aCA")

    // 7 colors per state — spread across the full screen
    data class C7(val a: Color, val b: Color, val c: Color, val d: Color,
                  val e: Color, val f: Color, val g: Color)

    val tc = when (state) {
        AssistantState.IDLE -> C7(WaveBlue, WaveCyan, WavePurple, WaveBlue, WaveCyan, WavePurple, WaveBlue)
        AssistantState.LISTENING -> C7(WaveBlue, WaveCyan, WaveGreen, WaveYellow, WaveBlue, WaveGreen, WaveCyan)
        AssistantState.PROCESSING -> C7(WavePurple, WaveBlue, WavePink, WaveCyan, WavePurple, WaveBlue, WavePink)
        AssistantState.SPEAKING -> C7(WaveOrange, WaveYellow, WaveRed, WavePink, WaveOrange, WaveYellow, WaveRed)
        AssistantState.AWAITING_CONFIRMATION -> C7(WaveBlue, WaveCyan, AlfredGoldLight, WaveBlue, WaveCyan, AlfredGoldLight, WaveBlue)
    }
    val colors = listOf(
        animateColorAsState(tc.a, ctw, label = "c0").value,
        animateColorAsState(tc.b, ctw, label = "c1").value,
        animateColorAsState(tc.c, ctw, label = "c2").value,
        animateColorAsState(tc.d, ctw, label = "c3").value,
        animateColorAsState(tc.e, ctw, label = "c4").value,
        animateColorAsState(tc.f, ctw, label = "c5").value,
        animateColorAsState(tc.g, ctw, label = "c6").value,
    )

    // Blob positions spread across the full screen
    val blobPositions = listOf(
        0.20f to 0.15f, 0.75f to 0.25f, 0.40f to 0.50f,
        0.15f to 0.75f, 0.80f to 0.70f, 0.55f to 0.85f,
        0.50f to 0.35f
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

        // Draw 7 large radial glow blobs spread across the full screen
        for (i in 0 until 7) {
            val (bx, by) = blobPositions[i]
            val d = drifts[i]
            val p = pulses[i]
            val col = colors[i]

            val cx = bx * w + sin(d.toDouble()).toFloat() * w * aDrift
            val cy = by * h + cos(d.toDouble() * 0.7).toFloat() * h * aDrift
            val r = diag * aGlowR * p * audioBoost * 0.5f

            val alpha = (aGlowAlpha * p * audioAlphaBoost).coerceAtMost(0.85f)

            drawCircle(
                brush = smoothRadialBrush(
                    center = Offset(cx, cy),
                    radius = r * 2f,
                    peakColor = col.copy(alpha = alpha),
                    spread = 0.55f
                ),
                radius = r * 2f,
                center = Offset(cx, cy)
            )
        }

        // Central core glow
        val corePulse = pulses[0]
        val coreR = diag * 0.25f * corePulse * audioBoost
        val coreAlpha = (aCoreAlpha * corePulse * audioAlphaBoost).coerceAtMost(0.80f)
        val coreColor = lerp(Color.White, colors[0], 0.3f).copy(alpha = coreAlpha)

        drawCircle(
            brush = smoothRadialBrush(
                center = Offset(w * 0.5f, h * 0.5f),
                radius = coreR,
                peakColor = coreColor,
                spread = 0.45f
            ),
            radius = coreR,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}
