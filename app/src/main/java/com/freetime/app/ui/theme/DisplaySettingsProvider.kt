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

/**
 * Display Settings Data Class containing all user customization preferences
 */
data class DisplaySettings(
    val fontSizeIndex: Int = 1,        // 0=Small, 1=Medium, 2=Large
    val animationSpeedIndex: Int = 1,  // 0=Slow, 1=Normal, 2=Fast
    val accentColorIndex: Int = 0,     // 0=Purple, 1=Cyan, 2=Magenta
    val compactModeEnabled: Boolean = false,
    val languageIndex: Int = 0         // 0=English, 1=Spanish, 2=French, 3=German
) {
    // Font size conversion
    fun getFontSize(): TextUnit = when (fontSizeIndex) {
        0 -> 10.sp  // Small
        2 -> 18.sp  // Large
        else -> 14.sp  // Medium (default)
    }
    
    // Base font size multiplier for scale
    fun getFontSizeMultiplier(): Float = when (fontSizeIndex) {
        0 -> 0.85f  // Small
        2 -> 1.25f  // Large
        else -> 1.0f  // Medium
    }
    
    // Animation speed multiplier
    fun getAnimationSpeedMultiplier(): Float = when (animationSpeedIndex) {
        0 -> 1.5f  // Slow
        2 -> 0.5f  // Fast
        else -> 1.0f  // Normal
    }
    
    // Accent color conversion
    fun getAccentColor(): Color = when (accentColorIndex) {
        1 -> Color(0xFF00FFFF)  // Cyan (#00FFFF)
        2 -> Color(0xFFFF00FF)  // Magenta (#FF00FF)
        else -> Color(0xFF9D4EDD)  // Purple (default - #9D4EDD)
    }
    
    // Compact mode padding multiplier (0.7 = 30% less padding)
    fun getCompactModePaddingMultiplier(): Float = if (compactModeEnabled) 0.7f else 1.0f
    
    // Language code
    fun getLanguageCode(): String = when (languageIndex) {
        1 -> "es"  // Español
        2 -> "fr"  // Français
        3 -> "de"  // Deutsch
        else -> "en"  // English
    }
}

// CompositionLocal for passing display settings throughout the app
val LocalDisplaySettings = compositionLocalOf { DisplaySettings() }

/**
 * Load display settings from SharedPreferences - recomposes when settings change
 * NOTE: This function always loads fresh from SharedPreferences instead of caching with remember()
 * to ensure changes to display settings are immediately reflected in the UI
 */
@Composable
fun rememberDisplaySettings(): DisplaySettings {
    val context = LocalContext.current
    val prefs = remember { SharedPreferencesHelper(context) }
    
    // Always fresh load from SharedPreferences - don't cache with remember
    // This ensures any changes to settings immediately reflect in the UI
    return DisplaySettings(
        fontSizeIndex = prefs.getFontSizeIndex(),
        animationSpeedIndex = prefs.getAnimationSpeedIndex(),
        accentColorIndex = prefs.getAccentColorIndex(),
        compactModeEnabled = prefs.isCompactModeEnabled(),
        languageIndex = prefs.getLanguageIndex()
    )
}

/**
 * Get the current display settings from CompositionLocal
 */
@Composable
fun currentDisplaySettings(): DisplaySettings {
    return LocalDisplaySettings.current
}
