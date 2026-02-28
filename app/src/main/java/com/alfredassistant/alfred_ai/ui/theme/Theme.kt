package com.alfredassistant.alfred_ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AlfredDarkScheme = darkColorScheme(
    primary = AlfredGold,
    onPrimary = AlfredBlack,
    primaryContainer = AlfredGoldDim,
    onPrimaryContainer = AlfredGoldLight,
    secondary = AlfredAmber,
    onSecondary = AlfredBlack,
    background = AlfredBlack,
    onBackground = AlfredTextPrimary,
    surface = AlfredDarkGray,
    onSurface = AlfredTextPrimary,
    surfaceVariant = AlfredCharcoal,
    onSurfaceVariant = AlfredTextSecondary,
    outline = AlfredSlate
)

@Composable
fun AlfredaiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlfredDarkScheme,
        typography = Typography,
        content = content
    )
}
