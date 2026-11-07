package com.inoagent.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF07090C)
val Panel = Color(0xFF12161B)
val PanelSoft = Color(0xFF181D23)
val Mint = Color(0xFF5CF2B3)
val MintDark = Color(0xFF163D31)
val Lavender = Color(0xFFB79BFF)
val TextPrimary = Color(0xFFF3F5F6)
val TextMuted = Color(0xFF8D969E)

private val colors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF052015),
    secondary = Lavender,
    background = Night,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelSoft,
    onSurfaceVariant = TextMuted,
    error = Color(0xFFFF8A8A),
)

@Composable
fun InoagentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

