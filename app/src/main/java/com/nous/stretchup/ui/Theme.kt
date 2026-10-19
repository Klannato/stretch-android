package com.nous.stretchup.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF7F3EC)
val Ink = Color(0xFF1F2A26)
val Pine = Color(0xFF1F6F5C)
val Clay = Color(0xFFC97B4A)
val Muted = Color(0xFF6E7A74)
val CardSurface = Color(0xFFFFFFFF)
val Hairline = Color(0xFFE3DDD2)

private val scheme = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    secondary = Clay,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Muted,
    outline = Hairline
)

private val type = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp)
)

@Composable
fun StretchTheme(content: @Composable () -> Unit) {
    // The palette is deliberately warm and low-light: this app is used at 7am and 11pm.
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
