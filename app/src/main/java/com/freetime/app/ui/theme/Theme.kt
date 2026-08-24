package com.freetime.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

val PrimaryMagenta = Color(0xFFFF00FF)
val SecondaryMagenta = Color(0xFFE91E63)
val Black = Color(0xFF000000)
val DarkBlack = Color(0xFF1A1A1A)
val White = Color(0xFFF0F0F0)
val LightGray = Color(0xFFF0F0F0)
val DarkGray = Color(0xFF303030)
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
val DarkBg = Color(0xFF000000)

object CyberpunkTheme {
    val PrimaryMagenta = Color(0xFFFF00FF)
    val SecondaryMagenta = Color(0xFFE91E63)
    val Black = Color(0xFF000000)
    val DarkBlack = Color(0xFF1A1A1A)
    val White = Color(0xFFF0F0F0)
    val LightGray = Color(0xFFF0F0F0)
    val MediumGray = Color(0xFF808080)
    val DarkGray = Color(0xFF303030)
    val GhostGray = Color(0xFF606060)
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
    val CyberCyan = Color(0xFFFF00FF)
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
    background = DeepBlack,
    onBackground = White,
    surface = DarkCard,
    onSurface = White,
    surfaceVariant = DarkGray,
    onSurfaceVariant = LightGray
)

@Composable
fun FreeTimeTheme(
    accentColor: Color = PrimaryMagenta,
    content: @Composable () -> Unit
) {
    // app theme, dark mode only
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
