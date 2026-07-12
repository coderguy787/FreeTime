package com.freetime.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

// FreeTime Brand Colors - Black, Magenta, and Light Grey ONLY
val PrimaryMagenta = Color(0xFFFF00FF)     // Magenta - Primary action
val SecondaryMagenta = Color(0xFFE91E63)   // Secondary magenta
val Black = Color(0xFF000000)               // Pure black background
val DarkBlack = Color(0xFF1A1A1A)          // Dark black for surfaces
val White = Color(0xFFF0F0F0)              // Light grey text (not pure white)
val LightGray = Color(0xFFF0F0F0)          // Light gray for secondary text
val DarkGray = Color(0xFF303030)           // Dark gray for borders
val ErrorRed = Color(0xFFFF00FF)           // Use magenta for error states
val WarningOrange = Color(0xFFFF00FF)      // Use magenta for warnings
val SuccessGreen = Color(0xFFFF00FF)       // Use magenta for success

// Compatibility aliases for existing code
val PrimaryPurple = PrimaryMagenta
val SecondaryBlack = Black
val AccentLightPurple = SecondaryMagenta
val DeepPurple = DarkBlack
val DeepBlack = Color(0xFF000000)          // Very deep black for backgrounds
val DarkCard = Color(0xFF1A1A1A)           // Dark card/surface color
val CyberBlue = Color(0xFFFF00FF)          // Magenta accent
val CyberPurple = Color(0xFFFF00FF)        // Magenta accent
val DarkBg = Color(0xFF000000)             // Dark background

// CyberpunkTheme object for easy access to colors throughout the app
object CyberpunkTheme {
    val PrimaryMagenta = Color(0xFFFF00FF)
    val SecondaryMagenta = Color(0xFFE91E63)
    val Black = Color(0xFF000000)
    val DarkBlack = Color(0xFF1A1A1A)
    val White = Color(0xFFF0F0F0)           // Light grey (not pure white)
    val LightGray = Color(0xFFF0F0F0)
    val MediumGray = Color(0xFF808080)       // Medium gray
    val DarkGray = Color(0xFF303030)
    val GhostGray = Color(0xFF606060)        // Ghost gray
    val ErrorRed = Color(0xFFFF00FF)
    val WarningOrange = Color(0xFFFF00FF)
    val SuccessGreen = Color(0xFFFF00FF)
    val PrimaryPurple = PrimaryMagenta
    val SecondaryBlack = Black
    val AccentLightPurple = SecondaryMagenta
    val DeepPurple = DarkBlack
    val DeepBlack = Color(0xFF000000)
    val DarkCard = Color(0xFF1A1A1A)
    val CyberBlue = Color(0xFFFF00FF)
    val CyberPurple = Color(0xFFFF00FF)
    val CyberCyan = Color(0xFFFF00FF)        // Alias to Magenta to follow brand
    val CyberBlack = Color(0xFF000000)
    val DarkBg = Color(0xFF000000)
}

private val LightColors = lightColorScheme(
    primary = PrimaryMagenta,
    onPrimary = Black,
    primaryContainer = PrimaryMagenta,
    onPrimaryContainer = Black,
    secondary = Black,
    onSecondary = LightGray,
    secondaryContainer = DarkGray,
    onSecondaryContainer = LightGray,
    tertiary = SecondaryMagenta,
    onTertiary = Black,
    tertiaryContainer = SecondaryMagenta,
    onTertiaryContainer = Black,
    error = PrimaryMagenta,
    onError = Black,
    errorContainer = PrimaryMagenta,
    onErrorContainer = Black,
    background = LightGray,
    onBackground = Black,
    surface = LightGray,
    onSurface = Black,
    surfaceVariant = LightGray,
    onSurfaceVariant = DarkGray
)

private val DarkColors = darkColorScheme(
    primary = PrimaryMagenta,
    onPrimary = White,
    primaryContainer = PrimaryMagenta,
    onPrimaryContainer = White,
    secondary = DarkBlack,
    onSecondary = White,
    secondaryContainer = DarkBlack,
    onSecondaryContainer = White,
    tertiary = SecondaryMagenta,
    onTertiary = White,
    tertiaryContainer = SecondaryMagenta,
    onTertiaryContainer = White,
    error = PrimaryMagenta,
    onError = White,
    errorContainer = PrimaryMagenta,
    onErrorContainer = White,
    // Core dark theme colors - Black and Magenta only
    background = DeepBlack,           // Pure black background
    onBackground = White,
    surface = DarkCard,               // Dark card surface
    onSurface = White,
    surfaceVariant = DarkGray,
    onSurfaceVariant = LightGray
)

@Composable
fun FreeTimeTheme(
    accentColor: Color = PrimaryMagenta,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColors.copy(
        primary = accentColor,
        onPrimary = White,
        primaryContainer = accentColor,
        onPrimaryContainer = White,
        tertiary = accentColor,
        onTertiary = White,
        tertiaryContainer = accentColor,
        onTertiaryContainer = White,
        error = accentColor,
        onError = White,
        errorContainer = accentColor,
        onErrorContainer = White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FreeTimeTypography,
        shapes = FreeTimeShapes,
        content = content
    )
}
