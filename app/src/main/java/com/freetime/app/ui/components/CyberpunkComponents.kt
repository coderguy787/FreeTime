package com.freetime.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern Design System for FreeTime Android App
 * Color Palette: Discord/WhatsApp/Telegram inspired - Clean, modern look
 * Style: Minimal, Flat, with smooth animations
 */

// ===== COLOR PALETTE - BLACK, MAGENTA, WHITE ONLY =====
object CyberpunkTheme {
    // Primary colors - Black and Magenta only
    val Black = Color(0xFF000000)           // Pure black background
    val CyberBlack = Color(0xFF1A1A1A)     // Dark black
    val DarkBlack = Color(0xFF1A1A1A)      // Dark black (alias for CyberBlack)
    val DarkerGray = Color(0xFF121212)     // Even darker gray for backgrounds
    val DarkGray = Color(0xFF303030)       // Dark gray for borders
    val MediumGray = Color(0xFF505050)     // Medium gray
    val LightGray = Color(0xFFF0F0F0)      // Light gray for secondary text
    val GhostGray = Color(0xFF808080)      // Disabled text

    // Text colors
    val TextPrimary = Color(0xFFF0F0F0)    // Main text (light gray/white)
    val TextSecondary = Color(0xFFB0B0B0)  // Secondary text (medium gray)
    val TextGhost = Color(0xFF808080)      // Ghost/disabled text (alias for GhostGray)
    
    // Magenta accents - All use magenta
    val PrimaryPurple = Color(0xFFFF00FF)  // Magenta - Primary action
    val PrimaryMagenta = Color(0xFFFF00FF) // Magenta - Primary action
    val DeepPurple = Color(0xFFE91E63)     // Secondary magenta
    val LightPurple = Color(0xFFFF66FF)    // Light magenta
    
    // Accent colors
    val CyberCyan = Color(0xFF00FFFF)      // Bright cyan accent
    val AquaCyan = Color(0xFF70FFFF)       // Light cyan
    val DarkCyan = Color(0xFF00A0A0)       // Dark cyan

    val White = Color(0xFFF5F5F5)          // Clean off-white
    val ErrorRed = Color(0xFFFF3B3B)       // True red for errors
    val SuccessGreen = Color(0xFF32CD32)   // Lime green for success
    val WarningOrange = Color(0xFFFF8C00)  // Dark orange for warnings
    val SecondaryBlack = Color(0xFF121212) // Slightly lighter black
    }
// ===== CYBERPUNK BUTTON =====
@Composable
fun CyberpunkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    text: String,
    icon: (@Composable () -> Unit)? = null,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    size: ButtonSize = ButtonSize.MEDIUM
) {
    val pulseAnimation = rememberInfiniteTransition()
    val pulse by pulseAnimation.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val (containerColor, contentColor, borderColor) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(CyberpunkTheme.DarkGray, CyberpunkTheme.CyberCyan, CyberpunkTheme.PrimaryPurple)
        ButtonVariant.SECONDARY -> Triple(CyberpunkTheme.Black, CyberpunkTheme.PrimaryPurple, CyberpunkTheme.GhostGray)
        ButtonVariant.ACCENT -> Triple(CyberpunkTheme.DarkGray, CyberpunkTheme.CyberCyan, CyberpunkTheme.CyberCyan)
    }
    
    val (height, padding, fontSize) = when (size) {
        ButtonSize.SMALL -> Triple(32.dp, 8.dp, 12.sp)
        ButtonSize.MEDIUM -> Triple(48.dp, 12.dp, 14.sp)
        ButtonSize.LARGE -> Triple(56.dp, 16.dp, 16.sp)
    }
    
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(height)
            .border(
                width = 1.5.dp,
                color = if (enabled) borderColor else CyberpunkTheme.GhostGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .graphicsLayer {
                if (!enabled) alpha = 0.6f
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(4.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = padding, vertical = 4.dp)
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 1.5.dp
                )
                Text(
                    "LOADING...",
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.invoke()
                Text(
                    text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

enum class ButtonVariant {
    PRIMARY, SECONDARY, ACCENT
}

enum class ButtonSize {
    SMALL, MEDIUM, LARGE
}

// ===== CYBERPUNK CARD =====
@Composable
fun CyberpunkCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(CyberpunkTheme.DarkGray)
            .border(1.5.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

// ===== CYBERPUNK TOGGLE SWITCH =====
@Composable
fun CyberpunkToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true
) {
    val animatedOffset by animateFloatAsState(
        targetValue = if (checked) 24f else 4f,
        animationSpec = tween(durationMillis = 300)
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .background(
                    if (checked) CyberpunkTheme.CyberCyan.copy(alpha = 0.2f)
                    else CyberpunkTheme.GhostGray.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    1.dp,
                    if (checked) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset.dp)
                    .size(20.dp)
                    .background(
                        if (checked) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
        
        if (label.isNotEmpty()) {
            Text(
                label,
                color = CyberpunkTheme.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ===== CYBERPUNK CHECKBOX =====
@Composable
fun CyberpunkCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (checked) CyberpunkTheme.CyberCyan else CyberpunkTheme.Black,
                    shape = RoundedCornerShape(2.dp)
                )
                .border(
                    1.5.dp,
                    if (checked) CyberpunkTheme.CyberCyan else CyberpunkTheme.PrimaryPurple,
                    RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = CyberpunkTheme.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        if (label.isNotEmpty()) {
            Text(
                label,
                color = CyberpunkTheme.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ===== CYBERPUNK TEXT INPUT =====
@Composable
fun CyberpunkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    icon: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    error: String = ""
) {
    var showPassword by remember { mutableStateOf(!isPassword) }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                color = CyberpunkTheme.CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            placeholder = { Text(placeholder, color = CyberpunkTheme.GhostGray.copy(alpha = 0.5f)) },
            leadingIcon = icon,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = CyberpunkTheme.CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.CyberCyan,
                unfocusedBorderColor = CyberpunkTheme.GhostGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.CyberCyan,
                unfocusedLabelColor = CyberpunkTheme.GhostGray,
                cursorColor = CyberpunkTheme.CyberCyan,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                disabledBorderColor = CyberpunkTheme.GhostGray.copy(alpha = 0.2f),
                disabledTextColor = CyberpunkTheme.GhostGray
            ),
            shape = RoundedCornerShape(2.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            visualTransformation = if (isPassword && !showPassword) 
                androidx.compose.ui.text.input.PasswordVisualTransformation() 
            else 
                androidx.compose.ui.text.input.VisualTransformation.None
        )
        
        if (error.isNotEmpty()) {
            Text(
                "⚠ $error",
                color = CyberpunkTheme.ErrorRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ===== CYBERPUNK BADGE =====
@Composable
fun CyberpunkBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CyberpunkTheme.CyberCyan,
    backgroundColor: Color = CyberpunkTheme.DarkGray
) {
    Box(
        modifier = modifier
            .background(backgroundColor, shape = RoundedCornerShape(12.dp))
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ===== CYBERPUNK DIVIDER =====
@Composable
fun CyberpunkDivider(
    modifier: Modifier = Modifier,
    color: Color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color,
                        Color.Transparent
                    )
                )
            )
    )
}

// ===== CYBERPUNK STATUS INDICATOR =====
@Composable
fun CyberpunkStatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val pulseAnimation = rememberInfiniteTransition()
    val pulseSize by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier
            .size(12.dp)
            .graphicsLayer {
                if (isOnline) scaleX = pulseSize
                if (isOnline) scaleY = pulseSize
            }
            .background(
                if (isOnline) CyberpunkTheme.SuccessGreen else CyberpunkTheme.GhostGray,
                shape = CircleShape
            )
            .border(
                2.dp,
                if (isOnline) CyberpunkTheme.SuccessGreen else CyberpunkTheme.GhostGray,
                CircleShape
            )
    )
}

// ===== CYBERPUNK MESSAGE BUBBLE =====
@Composable
fun CyberpunkMessageBubble(
    message: String,
    isOwn: Boolean = true,
    timestamp: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.75f)
                .background(
                    if (isOwn) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                    else CyberpunkTheme.DarkGray,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (isOwn) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.CyberCyan.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            Text(
                message,
                color = CyberpunkTheme.White,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            if (timestamp.isNotEmpty()) {
                Text(
                    timestamp,
                    color = CyberpunkTheme.GhostGray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ===== CYBERPUNK LIST ITEM =====
@Composable
fun CyberpunkListItem(
    title: String,
    subtitle: String = "",
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.GhostGray.copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon()
            }
        }
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = CyberpunkTheme.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = CyberpunkTheme.GhostGray,
                    fontSize = 12.sp
                )
            }
        }
        
        if (trailing != null) {
            trailing()
        }
    }
}
