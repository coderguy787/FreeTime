package com.freetime.app.ui.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle

/**
 * Responsive design utilities for adaptive UI across phones and tablets
 * Breakpoints: Phone < 600dp, Tablet >= 600dp
 */

// Device size classification
enum class DeviceSize {
    PHONE,      // < 600dp (phones)
    TABLET,     // >= 600dp (tablets, large phones)
    LARGE_TABLET // >= 800dp (large tablets)
}

@Composable
fun rememberDeviceSize(context: Context): DeviceSize {
    val density = LocalDensity.current
    val screenWidthDp = with(density) {
        context.resources.displayMetrics.widthPixels.toDp()
    }
    
    return when {
        screenWidthDp >= 800.dp -> DeviceSize.LARGE_TABLET
        screenWidthDp >= 600.dp -> DeviceSize.TABLET
        else -> DeviceSize.PHONE
    }
}

/**
 * Responsive padding based on device type
 */
@Composable
fun responsivePaddingSmall(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 8.dp
    DeviceSize.TABLET -> 12.dp
    DeviceSize.LARGE_TABLET -> 16.dp
}

@Composable
fun responsivePaddingMedium(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 16.dp
    DeviceSize.TABLET -> 20.dp
    DeviceSize.LARGE_TABLET -> 24.dp
}

@Composable
fun responsivePaddingLarge(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 24.dp
    DeviceSize.TABLET -> 32.dp
    DeviceSize.LARGE_TABLET -> 40.dp
}

/**
 * Responsive spacing between elements
 */
@Composable
fun responsiveSpacingSmall(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 4.dp
    DeviceSize.TABLET -> 6.dp
    DeviceSize.LARGE_TABLET -> 8.dp
}

@Composable
fun responsiveSpacingMedium(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 12.dp
    DeviceSize.TABLET -> 16.dp
    DeviceSize.LARGE_TABLET -> 20.dp
}

@Composable
fun responsiveSpacingLarge(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 20.dp
    DeviceSize.TABLET -> 28.dp
    DeviceSize.LARGE_TABLET -> 36.dp
}

@Composable
fun responsiveSpacingXLarge(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 32.dp
    DeviceSize.TABLET -> 40.dp
    DeviceSize.LARGE_TABLET -> 48.dp
}

/**
 * Responsive button heights
 */
@Composable
fun responsiveButtonHeight(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 44.dp
    DeviceSize.TABLET -> 48.dp
    DeviceSize.LARGE_TABLET -> 56.dp
}

/**
 * Responsive icon sizes
 */
@Composable
fun responsiveIconSmall(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 24.dp
    DeviceSize.TABLET -> 32.dp
    DeviceSize.LARGE_TABLET -> 40.dp
}

@Composable
fun responsiveIconMedium(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 40.dp
    DeviceSize.TABLET -> 56.dp
    DeviceSize.LARGE_TABLET -> 64.dp
}

@Composable
fun responsiveIconLarge(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 56.dp
    DeviceSize.TABLET -> 72.dp
    DeviceSize.LARGE_TABLET -> 96.dp
}

@Composable
fun responsiveIconXXLarge(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 80.dp
    DeviceSize.TABLET -> 100.dp
    DeviceSize.LARGE_TABLET -> 120.dp
}

/**
 * Responsive content width (for centering content on tablets)
 */
@Composable
fun responsiveContentWidth(deviceSize: DeviceSize): Float = when (deviceSize) {
    DeviceSize.PHONE -> 1f          // Full width on phones
    DeviceSize.TABLET -> 0.85f      // 85% on tablets
    DeviceSize.LARGE_TABLET -> 0.75f // 75% on large tablets
}

/**
 * Responsive text sizes
 */
@Composable
fun responsiveHeadlineLargeFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 32.sp
    DeviceSize.TABLET -> 36.sp
    DeviceSize.LARGE_TABLET -> 40.sp
}

@Composable
fun responsiveHeadlineMediumFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 28.sp
    DeviceSize.TABLET -> 32.sp
    DeviceSize.LARGE_TABLET -> 36.sp
}

@Composable
fun responsiveHeadlineSmallFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 24.sp
    DeviceSize.TABLET -> 28.sp
    DeviceSize.LARGE_TABLET -> 32.sp
}

@Composable
fun responsiveTitleLargeFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 22.sp
    DeviceSize.TABLET -> 24.sp
    DeviceSize.LARGE_TABLET -> 26.sp
}

@Composable
fun responsiveTitleMediumFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 16.sp
    DeviceSize.TABLET -> 18.sp
    DeviceSize.LARGE_TABLET -> 20.sp
}

@Composable
fun responsiveLabelLargeFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 14.sp
    DeviceSize.TABLET -> 16.sp
    DeviceSize.LARGE_TABLET -> 18.sp
}

@Composable
fun responsiveBodyMediumFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 14.sp
    DeviceSize.TABLET -> 16.sp
    DeviceSize.LARGE_TABLET -> 18.sp
}

@Composable
fun responsiveBodySmallFont(deviceSize: DeviceSize) = when (deviceSize) {
    DeviceSize.PHONE -> 12.sp
    DeviceSize.TABLET -> 14.sp
    DeviceSize.LARGE_TABLET -> 16.sp
}

/**
 * Responsive list item height
 */
@Composable
fun responsiveListItemHeight(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 64.dp
    DeviceSize.TABLET -> 72.dp
    DeviceSize.LARGE_TABLET -> 80.dp
}

/**
 * Responsive corners radius
 */
@Composable
fun responsiveCornerRadius(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 12.dp
    DeviceSize.TABLET -> 16.dp
    DeviceSize.LARGE_TABLET -> 20.dp
}

/**
 * Responsive card elevation
 */
@Composable
fun responsiveElevation(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 4.dp
    DeviceSize.TABLET -> 6.dp
    DeviceSize.LARGE_TABLET -> 8.dp
}

/**
 * Helper to make multi-column layouts responsive
 * Returns number of columns based on device size and content width
 */
@Composable
fun responsiveGridColumns(deviceSize: DeviceSize): Int = when (deviceSize) {
    DeviceSize.PHONE -> 1
    DeviceSize.TABLET -> 2
    DeviceSize.LARGE_TABLET -> 3
}

/**
 * Responsive modal/dialog width
 */
@Composable
fun responsiveDialogWidth(deviceSize: DeviceSize): Float = when (deviceSize) {
    DeviceSize.PHONE -> 0.95f
    DeviceSize.TABLET -> 0.7f
    DeviceSize.LARGE_TABLET -> 0.6f
}

/**
 * Quick helper - returns pair of (padding, spacing) for common use
 */
@Composable
fun responsiveLayout(deviceSize: DeviceSize): Pair<Dp, Dp> = when (deviceSize) {
    DeviceSize.PHONE -> Pair(24.dp, 12.dp)        // padding, spacing
    DeviceSize.TABLET -> Pair(32.dp, 16.dp)       // padding, spacing
    DeviceSize.LARGE_TABLET -> Pair(40.dp, 20.dp) // padding, spacing
}
