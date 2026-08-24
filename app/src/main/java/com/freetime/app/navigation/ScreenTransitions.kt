package com.freetime.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.freetime.app.ui.theme.DisplaySettings

// screen transition animations
private fun speedDuration(settings: DisplaySettings): Int {
    return (250 * settings.getAnimationSpeedMultiplier()).toInt()
}

fun telegramPushEnter(settings: DisplaySettings): EnterTransition {
    val duration = speedDuration(settings)
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = duration,
            easing = FastOutSlowInEasing
        ),
        initialOffsetX = { it }
    )
}

fun telegramPushExit(settings: DisplaySettings): ExitTransition {
    return ExitTransition.None
}

fun telegramPopEnter(settings: DisplaySettings): EnterTransition {
    val duration = speedDuration(settings)
    return slideInHorizontally(
        animationSpec = tween(
            durationMillis = duration,
            easing = FastOutSlowInEasing
        ),
        initialOffsetX = { -it / 3 }
    )
}

fun telegramPopExit(settings: DisplaySettings): ExitTransition {
    val duration = speedDuration(settings)
    return slideOutHorizontally(
        animationSpec = tween(
            durationMillis = duration,
            easing = FastOutSlowInEasing
        ),
        targetOffsetX = { it / 3 }
    )
}
