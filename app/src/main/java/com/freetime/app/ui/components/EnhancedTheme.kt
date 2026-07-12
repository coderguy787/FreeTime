package com.freetime.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enhanced Cyberpunk UI Components - Discord-like styling
 */

// Custom TextField with vibrant Cyberpunk styling
@Composable
fun DiscordStyleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var showPassword by remember { mutableStateOf(!isPassword) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                label,
                color = CyberpunkTheme.PrimaryPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(
                    width = 2.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ),
            placeholder = { 
                Text(
                    placeholder, 
                    color = CyberpunkTheme.GhostGray,
                    fontSize = 14.sp
                ) 
            },
            leadingIcon = leadingIcon,
            trailingIcon = if (isPassword) {
                {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.CyberCyan,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                unfocusedBorderColor = CyberpunkTheme.DarkGray,
                cursorColor = CyberpunkTheme.CyberCyan,
                focusedContainerColor = CyberpunkTheme.DarkBlack,
                unfocusedContainerColor = CyberpunkTheme.DarkBlack
            ),
            visualTransformation = if (isPassword && !showPassword) 
                PasswordVisualTransformation() 
            else 
                VisualTransformation.None,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        )
    }
}

// Enhanced Button with gradient and glow effect
@Composable
fun DiscordStyleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    text: String,
    icon: (@Composable () -> Unit)? = null,
    isPrimary: Boolean = true
) {
    val isPressed by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (isPrimary) CyberpunkTheme.CyberCyan else CyberpunkTheme.PrimaryPurple,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.DarkGray,
            contentColor = CyberpunkTheme.White,
            disabledContainerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.5f),
            disabledContentColor = CyberpunkTheme.White.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            disabledElevation = 0.dp
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                    color = CyberpunkTheme.White,
                    strokeWidth = 2.dp
                )
                Text(
                    "Loading...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            } else {
                icon?.invoke()
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// Card with gradient border - Discord style
@Composable
fun DiscordStyleCard(
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = if (isPrimary) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f) 
                       else CyberpunkTheme.DarkGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = CyberpunkTheme.DarkBlack,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

// Section header with accent line - Discord style
@Composable
fun DiscordStyleSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(
                    color = CyberpunkTheme.PrimaryPurple,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = CyberpunkTheme.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// Status badge with animation
@Composable
fun StatusBadge(
    text: String,
    status: BadgeStatus = BadgeStatus.ONLINE,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = when (status) {
            BadgeStatus.ONLINE -> Color(0xFF43B581)
            BadgeStatus.IDLE -> Color(0xFFFAA61A)
            BadgeStatus.OFFLINE -> CyberpunkTheme.GhostGray
            BadgeStatus.STREAMING -> Color(0xFF593695)
        },
        animationSpec = tween(800)
    )
    
    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = color,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

enum class BadgeStatus {
    ONLINE, IDLE, OFFLINE, STREAMING
}

// Animated divider - Discord style
@Composable
fun DiscordStyleDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    )
}
