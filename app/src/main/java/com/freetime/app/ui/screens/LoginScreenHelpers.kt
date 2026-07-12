package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.freetime.app.ui.theme.PrimaryPurple
import com.freetime.app.ui.utils.*

/**
 * Helper Composable for Login Form Fields
 */
@Composable
fun LoginFormFields(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    isLoading: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingMedium(deviceSize))
    ) {
        // Username Field - Animated Entry
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300)
            ),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Username",
                        tint = PrimaryPurple
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Password Field - Animated Entry with Delay
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 100)
            ),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Password",
                        tint = PrimaryPurple
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onShowPasswordChange(!showPassword) },
                        enabled = !isLoading
                    ) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Password",
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Auto-login info: Always enabled for 30 days
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 200)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 200)
            ),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            androidx.compose.material3.Text(
                text = "✓ Auto-login enabled for 30 days",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color(0xFF00FF00).copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = responsiveSpacingSmall(deviceSize)),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun SignUpFormFields(
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    showConfirmPassword: Boolean,
    onShowConfirmPasswordChange: (Boolean) -> Unit,
    isLoading: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingSmall(deviceSize))
    ) {
        var animationDelay = 0
        
        // Username Field - Animated
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = animationDelay)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = animationDelay)
            )
        ) {
            animationDelay = 100
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Username",
                        tint = PrimaryPurple
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Email Field - Animated with delay
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 100)
            )
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = "Email",
                        tint = PrimaryPurple
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Display Name Field - Animated with delay
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 200)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 200)
            )
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display Name", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Display Name",
                        tint = PrimaryPurple
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Password Field - Animated with delay
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 300)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 300)
            )
        ) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Password",
                        tint = PrimaryPurple
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onShowPasswordChange(!showPassword) },
                        enabled = !isLoading
                    ) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Password",
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }

        // Confirm Password Field - Animated with delay
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 400)) + slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = tween(300, delayMillis = 400)
            )
        ) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm Password", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Confirm Password",
                        tint = PrimaryPurple
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onShowConfirmPasswordChange(!showConfirmPassword) },
                        enabled = !isLoading
                    ) {
                        Icon(
                            if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Password",
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = PrimaryPurple.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0A0E27),
                    unfocusedContainerColor = Color(0xFF0A0E27)
                )
            )
        }
    }
}


