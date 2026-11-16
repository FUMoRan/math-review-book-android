package com.fumoran.mathreview.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0EA5E9),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F9),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD5D9E2),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA7FF),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF67E8F9),
    background = Color(0xFF0B0D10),
    surface = Color(0xFF121417),
    surfaceVariant = Color(0xFF1B1F24),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF2A3038),
    error = Color(0xFFF87171)
)

@Composable
fun MathReviewTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
