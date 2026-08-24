package com.freetime.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.freetime.app.data.local.SharedPreferencesHelper

data class DisplaySettings(
    val fontSizeIndex: Int = 1,
    val animationSpeedIndex: Int = 1,
    val accentColorIndex: Int = 0,
    val languageIndex: Int = 0
) {
    fun getFontSize(): TextUnit = when (fontSizeIndex) {
        0 -> 10.sp
        2 -> 18.sp
        else -> 14.sp
    }

    fun getFontSizeMultiplier(): Float = when (fontSizeIndex) {
        0 -> 0.85f
        2 -> 1.25f
        else -> 1.0f
    }

    fun getAnimationSpeedMultiplier(): Float = when (animationSpeedIndex) {
        0 -> 1.5f
        2 -> 0.5f
        else -> 1.0f
    }

    fun getAccentColor(): Color = when (accentColorIndex) {
        1 -> Color(0xFF00FFFF)
        2 -> Color(0xFFFF00FF)
        else -> Color(0xFF9D4EDD)
    }

    fun getLanguageCode(): String = when (languageIndex) {
        1 -> "es"
        2 -> "fr"
        3 -> "de"
        else -> "en"
    }
}

val LocalDisplaySettings = compositionLocalOf { DisplaySettings() }

@Composable
fun rememberDisplaySettings(): DisplaySettings {
    val context = LocalContext.current
    // display settings (font scale, animations)
    val prefs = remember { SharedPreferencesHelper(context) }

    return DisplaySettings(
        fontSizeIndex = prefs.getFontSizeIndex(),
        animationSpeedIndex = prefs.getAnimationSpeedIndex(),
        accentColorIndex = prefs.getAccentColorIndex(),
        languageIndex = prefs.getLanguageIndex()
    )
}

@Composable
fun currentDisplaySettings(): DisplaySettings {
    return LocalDisplaySettings.current
}
