package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = CyberBlue,
    onSecondary = Color(0xFF002B73),
    secondaryContainer = Color(0xFF003FA6),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = CyberGreen,
    onTertiary = Color(0xFF003915),
    error = CyberCritical,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = CyberDarkBackground,
    onBackground = Color(0xFFE2E8F0),
    surface = CyberDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CyberDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = CyberLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3E7FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = CyberLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = CyberLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = CyberLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = CyberLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun ScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

// Backward-compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = ScanTheme(darkTheme = darkTheme, content = content)
