package com.oneglobal.billboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF080808)
val InkRaised = Color(0xFF141414)
val Paper = Color(0xFFF2F0E8)
val Muted = Color(0xFF8E8E91)
val Acid = Color(0xFFD7FF00)
val Cobalt = Color(0xFF315CFF)
val Orange = Color(0xFFFF4E2B)
val Magenta = Color(0xFFFF3BBE)
val Ice = Color(0xFF72E7FF)

private val oneColors = darkColorScheme(
    primary = Acid,
    secondary = Cobalt,
    tertiary = Orange,
    background = Ink,
    surface = InkRaised,
    onPrimary = Ink,
    onSecondary = Paper,
    onBackground = Paper,
    onSurface = Paper,
)

@Composable
fun OneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = oneColors,
        content = content,
    )
}
