package com.freetime.app.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Smooth fade in animation for screen transitions
 */
@Composable
fun FadeInAnimation(
    content: @Composable () -> Unit,
    duration: Int = 500
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(duration))
    ) {
        content()
    }
}

/**
 * Slide in from bottom animation
 */
@Composable
fun SlideInFromBottomAnimation(
    content: @Composable () -> Unit,
    duration: Int = 600
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = duration,
                easing = EaseOut
            )
        ) + fadeIn(animationSpec = tween(duration))
    ) {
        content()
    }
}

/**
 * Scale animation for button presses
 */
@Composable
fun scaleOnPress(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return Modifier.clickable(
        interactionSource = interactionSource,
        indication = null
    ) { }
}

/**
 * Button press animation - scale down and back up
 */
@Composable
fun AnimatedButtonPress(
    isPressed: Boolean,
    duration: Int = 200,
    scaleStart: Float = 1f,
    scaleEnd: Float = 0.95f,
    content: @Composable () -> Unit
) {
    val scale = animateFloatAsState(
        targetValue = if (isPressed) scaleEnd else scaleStart,
        animationSpec = tween(duration, easing = EaseInOutCubic)
    )

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}

/**
 * Loading spinner with rotation animation
 */
@Composable
fun RotatingAnimation(
    duration: Int = 1000,
    content: @Composable (Modifier) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    content(Modifier.graphicsLayer { rotationZ = rotation.value })
}

/**
 * Pulse animation for attention-grabbing elements
 */
@Composable
fun PulseAnimation(
    duration: Int = 1500,
    scaleStart: Float = 1f,
    scaleEnd: Float = 1.1f,
    content: @Composable (Modifier) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale = infiniteTransition.animateFloat(
        initialValue = scaleStart,
        targetValue = scaleEnd,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 2, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    content(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
}

/**
 * Shake animation for errors
 */
@Composable
fun ShakeAnimation(
    duration: Int = 500,
    intensity: Float = 10f,
    content: @Composable (Modifier) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val offsetX = infiniteTransition.animateFloat(
        initialValue = -intensity,
        targetValue = intensity,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 4, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    content(Modifier.graphicsLayer { translationX = offsetX.value })
}

/**
 * Expand/collapse animation
 */
@Composable
fun ExpandableAnimation(
    isExpanded: Boolean,
    duration: Int = 400,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(duration, easing = EaseOut)
        ) + fadeIn(animationSpec = tween(duration)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(duration, easing = EaseIn)
        ) + fadeOut(animationSpec = tween(duration))
    ) {
        content()
    }
}

/**
 * Smooth color transition animation
 */
@Composable
fun ColorTransitionAnimation(
    targetColor: androidx.compose.ui.graphics.Color,
    duration: Int = 500,
    label: String = "colorTransition"
): androidx.compose.ui.graphics.Color {
    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(duration, easing = EaseInOutCubic),
        label = label
    ).value
}

/**
 * Slide animation for items in lists
 */
@Composable
fun SlideInAnimation(
    content: @Composable () -> Unit,
    duration: Int = 400,
    delay: Int = 0
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(
                durationMillis = duration,
                delayMillis = delay,
                easing = EaseOut
            )
        ) + fadeIn(animationSpec = tween(duration, delay))
    ) {
        content()
    }
}

/**
 * Bounce animation for emphasis
 */
@Composable
fun BounceAnimation(
    duration: Int = 600,
    content: @Composable (Modifier) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val offsetY = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 2, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    content(Modifier.graphicsLayer { translationY = offsetY.value })
}

/**
 * Staggered list item animation - items fade and slide in one by one
 * @param index the position of this item in the list (0-based)
 * @param staggerDelayMs delay between each item's animation start
 */
@Composable
fun StaggeredListItemAnimation(
    index: Int,
    staggerDelayMs: Int = 60,
    content: @Composable () -> Unit
) {
    val delay = index * staggerDelayMs
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = delay,
                easing = EaseOutCubic
            )
        ) + fadeIn(animationSpec = tween(300, delayMillis = delay))
    ) {
        content()
    }
}

/**
 * Vertical staggered list animation - items slide up from below
 */
@Composable
fun StaggeredVerticalItemAnimation(
    index: Int,
    staggerDelayMs: Int = 50,
    content: @Composable () -> Unit
) {
    val delay = index * staggerDelayMs
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = 350,
                delayMillis = delay,
                easing = EaseOutCubic
            )
        ) + fadeIn(animationSpec = tween(250, delayMillis = delay))
    ) {
        content()
    }
}

/**
 * Animated scale effect for button press micro-interaction
 * Wraps content in a Box with animated scale on press
 */
@Composable
fun ScaleOnPressContent(
    isPressed: Boolean,
    scaleTarget: Float = 0.96f,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleTarget else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "scaleOnPress"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

/**
 * Fade-slide screen transition helper
 * Usage: pass enter/exit to AnimatedContent or AnimatedNavHost
 */
val fadeSlideIn: EnterTransition = slideInHorizontally(
    initialOffsetX = { it / 3 },
    animationSpec = tween(350, easing = EaseOutCubic)
) + fadeIn(animationSpec = tween(300))

val fadeSlideOut: ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 4 },
    animationSpec = tween(250, easing = EaseInCubic)
) + fadeOut(animationSpec = tween(250))

val fadeSlideInFromBottom: EnterTransition = slideInVertically(
    initialOffsetY = { it },
    animationSpec = tween(350, easing = EaseOutCubic)
) + fadeIn(animationSpec = tween(300))

val fadeSlideOutToBottom: ExitTransition = slideOutVertically(
    targetOffsetY = { it },
    animationSpec = tween(250, easing = EaseInCubic)
) + fadeOut(animationSpec = tween(250))
