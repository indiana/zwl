package com.example.zwl.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF2E7D32)
val GreenSecondary = Color(0xFF1B5E20)
val GreenBackground = Color(0xFF0C190D)
val GreenSurface = Color(0xFF162D18)
val GreenText = Color(0xFFE8F5E9)

val YellowPrimary = Color(0xFFFBC02D)
val YellowSecondary = Color(0xFFF57F17)
val YellowBackground = Color(0xFF131313)
val YellowSurface = Color(0xFF1A1A1A)
val YellowText = Color(0xFFFFFDE7)

private val InZoneColorScheme = darkColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    background = GreenBackground,
    surface = GreenSurface,
    onPrimary = Color.White,
    onBackground = GreenText,
    onSurface = GreenText
)

private val OutZoneColorScheme = darkColorScheme(
    primary = YellowPrimary,
    secondary = YellowSecondary,
    background = YellowBackground,
    surface = YellowSurface,
    onPrimary = Color.Black,
    onBackground = YellowText,
    onSurface = YellowText
)

@Composable
fun ZwlTheme(
    isInZone: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (isInZone) InZoneColorScheme else OutZoneColorScheme
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
