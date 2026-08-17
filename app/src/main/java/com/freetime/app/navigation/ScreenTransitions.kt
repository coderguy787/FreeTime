package com.freetime.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Telegram-like screen transitions.
 *
 * Push: the new screen slides in from the right while the current screen drifts
 * a third of the way to the left (parallax depth) and fades out.
 * Pop: the screen below slides back in from the left while the top screen
 * slides out to the right.
 *
 * All use a 1s smooth decelerating curve (fast start, gentle stop).
 */
private const val SCREEN_TRANSITION_MS = 1000

val AnimatedContentTransitionScope<NavBackStackEntry>.telegramPushEnter: EnterTransition
    get() = slideInHorizontally(
        animationSpec = tween(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { it }
    ) + fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing))

val AnimatedContentTransitionScope<NavBackStackEntry>.telegramPushExit: ExitTransition
    get() = slideOutHorizontally(
        animationSpec = tween(SCREEN_TRANSITION_MS, easing = LinearOutSlowInEasing),
        targetOffsetX = { -it / 3 }
    ) + fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS, easing = LinearOutSlowInEasing))

val AnimatedContentTransitionScope<NavBackStackEntry>.telegramPopEnter: EnterTransition
    get() = slideInHorizontally(
        animationSpec = tween(SCREEN_TRANSITION_MS, easing = LinearOutSlowInEasing),
        initialOffsetX = { -it / 3 }
    ) + fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS, easing = LinearOutSlowInEasing))

val AnimatedContentTransitionScope<NavBackStackEntry>.telegramPopExit: ExitTransition
    get() = slideOutHorizontally(
        animationSpec = tween(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { it }
    ) + fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing))
