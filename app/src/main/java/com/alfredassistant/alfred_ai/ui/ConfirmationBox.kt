package com.alfredassistant.alfred_ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfredassistant.alfred_ai.ui.theme.*

private val CardBg = Color(0xFF1C1D28)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val PillShape = RoundedCornerShape(50)

@Composable
fun ConfirmationBox(
    confirmation: ConfirmationRequest?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = confirmation != null,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier
    ) {
        confirmation?.let { req ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .border(0.5.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = req.prompt,
                    color = AlfredTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                req.options.forEachIndexed { index, option ->
                    val style = req.buttonStyles.getOrElse(index) { "primary" }
                    when (style) {
                        "primary" -> PrimaryPill(option) { onOptionSelected(option) }
                        "secondary" -> SecondaryPill(option) { onOptionSelected(option) }
                        "cancel" -> CancelText(option) { onOptionSelected(option) }
                        else -> PrimaryPill(option) { onOptionSelected(option) }
                    }
                    if (index < req.options.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(0.5.dp, Color.White.copy(alpha = 0.20f), PillShape)
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SecondaryPill(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CancelText(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 20.dp)
    ) {
        Text(
            text = label,
            color = AlfredTextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
