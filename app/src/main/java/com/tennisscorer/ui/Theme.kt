package com.tennisscorer.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** The one color Material's single `primary` can't cover: a readable accent for
 * small elements (server dot, serving score, pills) that differs per theme —
 * bright optic on dark, deeper court-green on light. */
data class TennisColors(val accent: Color)

private val Optic = Color(0xFFC9F24A)
private val OpticButtonLight = Color(0xFFBFE53A)
private val DeepGreen = Color(0xFF4C7A14)

val LocalTennisColors = staticCompositionLocalOf { TennisColors(accent = Optic) }

private val DarkScheme = darkColorScheme(
    primary = Optic,
    onPrimary = Color(0xFF0D0F0E),
    secondary = Optic,
    onSecondary = Color(0xFF0D0F0E),
    background = Color(0xFF0D0F0E),
    onBackground = Color(0xFFECEFEA),
    surface = Color(0xFF1E2422),
    onSurface = Color(0xFFECEFEA),
    surfaceVariant = Color(0xFF1E2422),
    onSurfaceVariant = Color(0xFF8A938B),
    outline = Color(0xFF2C322E),
    error = Color(0xFFE5746A),
)

private val LightScheme = lightColorScheme(
    primary = OpticButtonLight,
    onPrimary = Color(0xFF16231B),
    secondary = OpticButtonLight,
    onSecondary = Color(0xFF16231B),
    background = Color(0xFFEAEEDC),
    onBackground = Color(0xFF18271C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18271C),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF606B58),
    outline = Color(0xFFCBD4B7),
    error = Color(0xFFB3261E),
)

@Composable
fun TennisScorerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val tennis = TennisColors(accent = if (dark) Optic else DeepGreen)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalTennisColors provides tennis) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
