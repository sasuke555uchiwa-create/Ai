package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SasukeLightViolet,
    onPrimary = Color.White,
    primaryContainer = SasukeDarkSurfaceVariant,
    onPrimaryContainer = SasukeLightViolet,
    secondary = SasukeNeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = SasukeDarkSurfaceVariant,
    onSecondaryContainer = SasukeNeonCyan,
    tertiary = SasukeIndigoAccent,
    background = SasukeDarkBg,
    onBackground = SasukeDarkTextPrimary,
    surface = SasukeDarkSurface,
    onSurface = SasukeDarkTextPrimary,
    surfaceVariant = SasukeDarkSurfaceVariant,
    onSurfaceVariant = SasukeDarkTextSecondary,
    outline = SasukeDarkCardBorder,
    error = SasukeRedError
)

private val LightColorScheme = lightColorScheme(
    primary = SasukeElectricViolet,
    onPrimary = Color.White,
    primaryContainer = SasukeLightSurfaceVariant,
    onPrimaryContainer = SasukeElectricViolet,
    secondary = SasukeCobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = SasukeLightSurfaceVariant,
    onSecondaryContainer = SasukeCobaltBlue,
    tertiary = SasukeIndigoAccent,
    background = SasukeLightBg,
    onBackground = SasukeLightTextPrimary,
    surface = SasukeLightSurface,
    onSurface = SasukeLightTextPrimary,
    surfaceVariant = SasukeLightSurfaceVariant,
    onSurfaceVariant = SasukeLightTextSecondary,
    outline = SasukeLightCardBorder,
    error = SasukeRedError
)

@Composable
fun SasukeAiTheme(
    darkTheme: Boolean = true, // Dark theme by default as requested
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
