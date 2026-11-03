package com.wc.cwtrainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B1220)
val Surface1 = Color(0xFF121C2E)
val Surface2 = Color(0xFF1B2A44)
val Line = Color(0xFF263A5C)
val Amber = Color(0xFFFFB454)
val Green = Color(0xFF3DDC97)
val Cyan = Color(0xFF5DD3F3)
val TextMain = Color(0xFFE6EDF7)
val TextDim = Color(0xFF93A7C4)
val Red = Color(0xFFFF6B6B)

private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Green,
    onSecondary = Color(0xFF00160C),
    background = Bg,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    error = Red
)

@Composable
fun WcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
