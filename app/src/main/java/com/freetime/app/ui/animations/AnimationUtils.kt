package com.freetime.app.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.freetime.app.ui.components.CyberpunkTheme

@Composable
fun FadeInAnimation(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    // trigger the enter animation on the first frame
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMillis,
                easing = EaseOut
            )
        ) + scaleIn(
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = delayMillis,
                easing = EaseOut
            ),
            initialScale = 0.9f
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SlideInFromBottom(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = delayMillis,
                easing = EaseOut
            ),
            initialOffsetY = { 100 }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = delayMillis
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun PulseAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale
        )
    ) {
        content()
    }
}

@Composable
fun ShimmerLoading(
    modifier: Modifier = Modifier,
    width: Int = 200,
    height: Int = 16,
    cornerRadius: Int = 8
) {
    val shimmerColors = listOf(
        CyberpunkTheme.DarkGray.copy(alpha = 0.6f),
        CyberpunkTheme.DarkGray.copy(alpha = 0.2f),
        CyberpunkTheme.DarkGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerPosition by transition.animateFloat(
        // shine sweep effect
        initialValue = -width.toFloat(),
        targetValue = width.toFloat() * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_position"
    )

    Box(
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = shimmerColors,
                    start = androidx.compose.ui.geometry.Offset(shimmerPosition, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmerPosition + 100f, 0f)
                ),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
    )
}

@Composable
fun FloatingUpAnimation(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    durationMillis: Int = 2000,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = delayMillis,
                easing = EaseOut
            ),
            initialOffsetY = { 50 }
        ) + fadeIn(animationSpec = tween(300, delayMillis)),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = EaseIn
            ),
            targetOffsetY = { -300 }
        ) + fadeOut(animationSpec = tween(durationMillis))
    ) {
        content()
    }
}

@Composable
fun BounceAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_y"
    )

    Box(modifier = modifier.graphicsLayer(translationY = offsetY)) {
        content()
    }
}

@Composable
fun WaveAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_rotation"
    )

    Box(
        modifier = modifier.graphicsLayer(
            rotationZ = rotation,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.8f, 0.2f)
        )
    ) {
        content()
    }
}

@Composable
fun GlowAnimation(
    modifier: Modifier = Modifier,
    color: Color = CyberpunkTheme.CyberCyan,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    color = color.copy(alpha = alpha),
                    shape = RoundedCornerShape(12.dp)
                )
        )
        content()
    }
}

@Composable
fun TabTransitionAnimation(
    selectedTab: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(500, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) +
                    scaleIn(
                        animationSpec = tween(500, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
                        initialScale = 0.92f
                    ) togetherWith
                    fadeOut(animationSpec = tween(300, easing = EaseIn)) +
                    scaleOut(
                        animationSpec = tween(300, easing = EaseIn),
                        targetScale = 0.92f
                    )
        },
        modifier = modifier,
        label = "tab_transition"
    ) {
        content()
    }
}

@Composable
fun <T> StaggeredListAnimation(
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int) -> Unit
) {
    Column(modifier = modifier) {
            items.forEachIndexed { index, item ->
            FadeInAnimation(
                delayMillis = index * 80,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemContent(item, index)
            }
        }
    }
}

@Composable
fun animateColorBetween(
    targetColor: Color,
    animationSpec: AnimationSpec<Color> = tween(500, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
): State<Color> {
    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = animationSpec,
        label = "color_animation"
    )
}

@Composable
fun ExpandableAnimation(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(
            animationSpec = tween(500, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))
        ) + fadeIn(animationSpec = tween(500)),
        exit = shrinkVertically(
            animationSpec = tween(400, easing = EaseIn)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SoftSlideInFromLeft(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMillis,
                easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
            ),
            initialOffsetX = { -100 }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMillis
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SoftSlideInFromRight(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMillis,
                easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
            ),
            initialOffsetX = { 100 }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = delayMillis
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SoftFadeTransition(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    durationMillis: Int = 800,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SmoothScaleAnimation(
    isPressed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 200f
        ),
        label = "smooth_scale"
    )

    Box(modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
        content()
    }
}

@Composable
fun SmoothElevationAnimation(
    isHovered: Boolean,
    baseElevation: Float = 4f,
    hoverElevation: Float = 12f
): State<Float> {
    return animateFloatAsState(
        targetValue = if (isHovered) hoverElevation else baseElevation,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 150f
        ),
        label = "smooth_elevation"
    )
}

@Composable
fun StaggeredFadeInAnimation(
    modifier: Modifier = Modifier,
    itemDelayMillis: Int = 100,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 800,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun SmoothRotationAnimation(
    targetRotation: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 200f
        ),
        label = "smooth_rotation"
    )

    Box(modifier = modifier.graphicsLayer(rotationZ = rotation)) {
        content()
    }
}

@Composable
fun BreathAnimation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                2000,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    Box(
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale
        )
    ) {
        content()
    }
}
