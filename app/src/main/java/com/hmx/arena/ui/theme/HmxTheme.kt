package com.hmx.arena.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HmxLightColors = lightColorScheme(
    primary = Color(0xFF0A0A0B),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0A0A0B),
    surface = Color.White,
    onSurface = Color(0xFF0A0A0B),
)

@Composable
fun HmxArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HmxLightColors, content = content)
}
