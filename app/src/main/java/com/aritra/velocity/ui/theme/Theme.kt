package com.aritra.velocity.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aritra.velocity.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF65D9C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF075048),
    onPrimaryContainer = Color(0xFF8EF5E3),
    secondary = Color(0xFFB5CCFF),
    tertiary = Color(0xFFFFB4A8),
    background = Color(0xFF0A0F14),
    surface = Color(0xFF10171E),
    surfaceVariant = Color(0xFF1B242C),
    outline = Color(0xFF87939B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E2),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF315DA8),
    tertiary = Color(0xFF9C4238),
    background = Color(0xFFF7FAFC),
    surface = Color(0xFFF7FAFC),
    surfaceVariant = Color(0xFFE0E8EC),
    outline = Color(0xFF6F797E)
)

@Composable
fun VelocityTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
