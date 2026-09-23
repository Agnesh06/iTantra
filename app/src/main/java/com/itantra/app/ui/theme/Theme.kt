package com.itantra.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepSpaceBlack = Color(0xFF0D1117)
val SlateSurface = Color(0xFF161B22)
val CardSurface = Color(0xFF21262D)
val AccentCyan = Color(0xFF58A6FF)
val AccentEmerald = Color(0xFF2EA043)
val AlertRed = Color(0xFFDA3633)
val AlertAmber = Color(0xFFD29922)
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)
val ConfidenceHighlight = Color(0x66D29922) // Translucent amber overlay for <0.75 confidence

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color.Black,
    secondary = AccentEmerald,
    onSecondary = Color.Black,
    error = AlertRed,
    onError = Color.White,
    background = DeepSpaceBlack,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary
)

@Composable
fun iTantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
