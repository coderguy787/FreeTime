package com.freetime.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Advanced UI Components with Glassmorphism, Animations, and Micro-interactions
 * Features:
 * - Glassmorphism effects (frosted glass look)
 * - Shimmer loading animations
 * - Enhanced cards with depth
 * - Smooth micro-interactions
 * - Better visual hierarchy
 */

/**
 * Glassmorphic Card - Frosted glass effect with blur
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF0F0F0).copy(alpha = 0.1f), // Light grey instead of white
    borderColor: Color = Color(0xFFF0F0F0).copy(alpha = 0.2f),     // Light grey instead of white
    blurRadius: Float = 20f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .graphicsLayer(
                alpha = 0.99f,
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            )
    ) {
        content()
    }
}

/**
 * Shimmer Loading Effect - Elegant loading animation
 */
@Composable
fun ShimmerLoadingEffect(
    modifier: Modifier = Modifier,
    isLoading: Boolean = true,
    content: @Composable () -> Unit
) {
    val shimmerAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.graphicsLayer(alpha = shimmerAlpha)) {
        content()
    }
}

/**
 * Shimmer Skeleton Loader
 */
@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    width: Float = 1f,
    height: Float = 0.8f
) {
    val shimmerTranslate by animateFloatAsState(
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth(width)
            .height((height * 50).dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF2A2A3E),
                        Color(0xFF1A1A2E)
                    ),
                    start = androidx.compose.ui.geometry.Offset(
                        shimmerTranslate * 500f,
                        0f
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        shimmerTranslate * 500f + 500f,
                        0f
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
    )
}

/**
 * Enhanced Floating Action Button with Ripple and Glow
 */
@Composable
fun GlowingFAB(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = CyberpunkTheme.PrimaryPurple,
    isActive: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .drawBehind {
                // Glow effect
                drawCircle(
                    color = glowColor.copy(alpha = 0.3f),
                    radius = size.minDimension / 2 + 10.dp.toPx(),
                    alpha = 0.5f
                )
            }
            .clip(RoundedCornerShape(50))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, glowColor.copy(alpha = 0.8f)),
                    radius = 100f
                )
            )
            .clickable(enabled = isActive) { onClick() }
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(0xFFF0F0F0), // Light grey instead of white
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Elevated Card with Shadow and Hover Effect
 */
@Composable
fun ElevatedCard(
    modifier: Modifier = Modifier,
    isHovered: Boolean = false,
    shadowElevation: Float = 12f,
    content: @Composable () -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 20.dp else shadowElevation.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)),
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = elevation
    ) {
        content()
    }
}

/**
 * Animated Badge - Number badge with animation
 */
@Composable
fun AnimatedBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CyberpunkTheme.PrimaryPurple,
    textColor: Color = Color(0xFFF0F0F0) // Light grey instead of white
) {
    val scale by animateFloatAsState(
        targetValue = if (count > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)
    )

    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .background(backgroundColor, shape = RoundedCornerShape(50))
            .border(2.dp, Color.Black, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = textColor,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Pulse Animation Effect
 */
@Composable
fun PulseEffect(
    modifier: Modifier = Modifier,
    color: Color = CyberpunkTheme.CyberCyan,
    size: Float = 10f
) {
    val pulseSize by animateFloatAsState(
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    val pulseAlpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size((size + pulseSize).dp)
            .graphicsLayer(alpha = pulseAlpha * 0.3f + 0.3f)
            .background(
                color = color.copy(alpha = (pulseAlpha * 0.3f + 0.3f)),
                shape = RoundedCornerShape(50)
            )
    )
}

/**
 * Gradient Text
 */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = Brush.linearGradient(
        colors = listOf(CyberpunkTheme.PrimaryPurple, CyberpunkTheme.CyberCyan)
    ),
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold
) {
    Box(
        modifier = modifier.background(brush = brush)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = Color(0xFFF0F0F0) // Light grey instead of white
        )
    }
}

/**
 * Smooth Divider with Gradient
 */
@Composable
fun GradientDivider(
    modifier: Modifier = Modifier,
    startColor: Color = Color.Transparent,
    endColor: Color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
    height: Float = 1f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(startColor, endColor, startColor)
                )
            )
    )
}

/**
 * Animated Status Indicator
 */
@Composable
fun AnimatedStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Float = 12f
) {
    val pulseSize by animateFloatAsState(
        targetValue = if (isOnline) 0f else 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                color = if (isOnline) Color(0xFF00FF00) else Color(0xFF808080),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 2.dp,
                color = Color.Black,
                shape = RoundedCornerShape(50)
            )
    )
}

/**
 * Expandable Section with Smooth Animation
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandChanged(!isExpanded) }
                .background(Color(0xFF1A1A2E)),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = CyberpunkTheme.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CyberpunkTheme.PrimaryPurple,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F1E))
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Smooth Transition Card for Tab Content
 */
@Composable
fun TransitionCard(
    currentValue: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    AnimatedContent(
        targetState = currentValue,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + 
             slideInHorizontally { width -> if (targetState != initialState) -width else width })
                .togetherWith(
                    fadeOut(animationSpec = tween(300)) + 
                    slideOutHorizontally { width -> if (targetState != initialState) width else -width }
                )
        },
        modifier = modifier
    ) { value ->
        content(value)
    }
}

/**
 * Circular Progress Indicator with Gradient
 */
@Composable
fun GradientCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 4f,
    gradientColors: List<Color> = listOf(CyberpunkTheme.PrimaryPurple, CyberpunkTheme.CyberCyan)
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .drawBehind {
                val angle = progress * 360f
                val strokePx = strokeWidth
                val radiusSize = (size.width - strokePx * 2) / 2
                
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = gradientColors,
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2,
                            size.height / 2
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = angle,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(strokePx, strokePx),
                    size = androidx.compose.ui.geometry.Size(radiusSize * 2, radiusSize * 2)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${(progress * 100).toInt()}%",
            color = CyberpunkTheme.White,
            fontSize = 12.sp
        )
    }
}
