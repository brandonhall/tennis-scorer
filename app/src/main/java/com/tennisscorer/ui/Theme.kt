package com.tennisscorer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Optic = Color(0xFFC9F24A)
val Ink = Color(0xFF0D0F0E)
val Surface1 = Color(0xFF16191A)
val Surface2 = Color(0xFF1E2422)
val OnDark = Color(0xFFECEFEA)
val Muted = Color(0xFF8A938B)
val OutlineDark = Color(0xFF2C322E)

private val DarkOptic = darkColorScheme(
    primary = Optic,
    onPrimary = Ink,
    secondary = Optic,
    onSecondary = Ink,
    background = Ink,
    onBackground = OnDark,
    surface = Surface1,
    onSurface = OnDark,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = OutlineDark,
    error = Color(0xFFE5746A),
)

@Composable
fun TennisScorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkOptic, content = content)
}
