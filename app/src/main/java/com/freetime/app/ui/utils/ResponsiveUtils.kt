package com.freetime.app.ui.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle

enum class DeviceSize {
    PHONE,
    TABLET,
    LARGE_TABLET
}

@Composable
fun rememberDeviceSize(context: Context): DeviceSize {
    val density = LocalDensity.current
    val screenWidthDp = with(density) {
        context.resources.displayMetrics.widthPixels.toDp()
    }

    // screen size helpers
    return when {
        screenWidthDp >= 800.dp -> DeviceSize.LARGE_TABLET
        screenWidthDp >= 600.dp -> DeviceSize.TABLET
        else -> DeviceSize.PHONE
    }
}

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

@Composable
fun responsiveButtonHeight(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 44.dp
    DeviceSize.TABLET -> 48.dp
    DeviceSize.LARGE_TABLET -> 56.dp
}

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

@Composable
fun responsiveContentWidth(deviceSize: DeviceSize): Float = when (deviceSize) {
    DeviceSize.PHONE -> 1f
    DeviceSize.TABLET -> 0.85f
    DeviceSize.LARGE_TABLET -> 0.75f
}

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

@Composable
fun responsiveListItemHeight(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 64.dp
    DeviceSize.TABLET -> 72.dp
    DeviceSize.LARGE_TABLET -> 80.dp
}

@Composable
fun responsiveCornerRadius(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 12.dp
    DeviceSize.TABLET -> 16.dp
    DeviceSize.LARGE_TABLET -> 20.dp
}

@Composable
fun responsiveElevation(deviceSize: DeviceSize): Dp = when (deviceSize) {
    DeviceSize.PHONE -> 4.dp
    DeviceSize.TABLET -> 6.dp
    DeviceSize.LARGE_TABLET -> 8.dp
}

@Composable
fun responsiveGridColumns(deviceSize: DeviceSize): Int = when (deviceSize) {
    DeviceSize.PHONE -> 1
    DeviceSize.TABLET -> 2
    DeviceSize.LARGE_TABLET -> 3
}

@Composable
fun responsiveDialogWidth(deviceSize: DeviceSize): Float = when (deviceSize) {
    DeviceSize.PHONE -> 0.95f
    DeviceSize.TABLET -> 0.7f
    DeviceSize.LARGE_TABLET -> 0.6f
}

@Composable
fun responsiveLayout(deviceSize: DeviceSize): Pair<Dp, Dp> = when (deviceSize) {
    DeviceSize.PHONE -> Pair(24.dp, 12.dp)
    DeviceSize.TABLET -> Pair(32.dp, 16.dp)
    DeviceSize.LARGE_TABLET -> Pair(40.dp, 20.dp)
}
