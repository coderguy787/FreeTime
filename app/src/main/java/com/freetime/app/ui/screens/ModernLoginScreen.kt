package com.freetime.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.freetime.app.R
import com.freetime.app.ui.components.CyberpunkTheme

/**
 * MODERN DISCORD/TELEGRAM-LIKE LOGIN SCREEN
 * Complete redesign with:
 * - Mascot (Cody) with animations
 * - Modern card-based layout
 * - Smooth transitions between login/signup
 * - Professional color scheme (Magenta + Black)
 * - Proper animations and interactions
 */

@Composable
fun ModernLoginScreen(
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var isLoginMode by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showMascot by remember { mutableStateOf(true) }
    
    // 2FA state
    var show2FAScreen by remember { mutableStateOf(false) }
    var twoFATempToken by remember { mutableStateOf("") }
    var twoFASetupRequired by remember { mutableStateOf(false) }
    var isSignUpFlow by remember { mutableStateOf(false) }
    
    // Mascot animation state
    val mascotScale by animateFloatAsState(
        targetValue = if (showMascot) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f)
    )
    
    val mascotAlpha by animateFloatAsState(
        targetValue = if (showMascot) 1f else 0.6f,
        animationSpec = tween(300)
    )
    
    // Show 2FA screen if required
    if (show2FAScreen) {
        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: Showing 2FA screen, isSignUpFlow=$isSignUpFlow, setupRequired=$twoFASetupRequired")
        if (isSignUpFlow) {
            // During registration, show the authenticator setup screen
            TwoFactorSetupDuringRegistrationScreen(
                tempToken = twoFATempToken,
                onSetupComplete = {
                    android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA setup complete during signup")
                    show2FAScreen = false
                    isSignUpFlow = false
                    onLoginSuccess()
                },
                onError = { error ->
                    android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA setup error: $error")
                    errorMessage = error
                    show2FAScreen = false
                    isSignUpFlow = false
                    showMascot = true
                    isLoading = false
                }
            )
        } else {
            // During login, show the verification screen
            TwoFactorLoginVerificationScreen(
                tempToken = twoFATempToken,
                setupRequired = twoFASetupRequired,
                onSuccess = {
                    android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA completed successfully")
                    show2FAScreen = false
                    onLoginSuccess()
                },
                onError = { error ->
                    android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA error: $error")
                    errorMessage = error
                    show2FAScreen = false
                    showMascot = true
                    isLoading = false
                }
            )
        }
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberpunkTheme.Black,
                        Color(0xFF0A0E27)
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // FreeTime Logo Section with Animation
            AnimatedVisibility(
                visible = showMascot,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { -100 }),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(mascotScale)
                        .alpha(mascotAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.freetime_logo
                        ),
                        contentDescription = "FreeTime Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            // Brand & Welcome Text
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FreeTime",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberpunkTheme.PrimaryPurple,
                    fontSize = 32.sp,
                    letterSpacing = 1.5.sp
                )
                
                Text(
                    text = if (isLoginMode) "Welcome Back!" else "Join Us!",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberpunkTheme.White,
                    fontSize = 18.sp
                )
                
                Text(
                    text = if (isLoginMode) 
                        "Connect with friends instantly" 
                    else 
                        "Start messaging securely now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 14.sp
                )
            }
            
            // Info Box: Auto-Login Feature (Only visible on login tab)
            if (isLoginMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF0F5F4F).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = CyberpunkTheme.CyberCyan.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "🔑",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "30-Day Auto-Login Enabled",
                                    color = CyberpunkTheme.CyberCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "You'll stay logged in for 30 days. Close and reopen the app—no password needed!",
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Main Auth Card
            AnimatedContent(
                targetState = isLoginMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + 
                     slideInHorizontally { width -> if (targetState) -width else width })
                        .togetherWith(
                            fadeOut(animationSpec = tween(300)) + 
                            slideOutHorizontally { width -> if (targetState) width else -width }
                        )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
            ) { loginMode ->
                if (loginMode) {
                    ModernLoginCard(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onErrorClear = { errorMessage = "" },
                        onLoginClick = { username, password, rememberMe, force ->
                            showMascot = false
                            isLoading = true
                            scope.launch {
                                // Call performLogin - auto-login is always enabled
                                performLogin(
                                    username = username,
                                    password = password,
                                    context = context,
                                    force = force,
                                    onSuccess = { 
                                        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: Login successful, calling onLoginSuccess")
                                        onLoginSuccess() 
                                    },
                                    on2FARequired = { tempToken, setupRequired ->
                                        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA required, transitioning to 2FA screen")
                                        isLoading = false
                                        twoFATempToken = tempToken
                                        twoFASetupRequired = setupRequired
                                        show2FAScreen = true
                                    },
                                    onError = { error ->
                                        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: Login error: $error")
                                        errorMessage = error
                                        isLoading = false
                                        showMascot = true
                                    },
                                    setLoading = { isLoading = it }
                                )
                            }
                        }
                    )
                } else {
                    ModernSignUpCard(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onErrorClear = { errorMessage = "" },
                        onSignUpClick = { username, email, displayName, password ->
                            // Handle validation errors from the card
                            if (username == "__ERROR__") {
                                errorMessage = email // email field carries the error message  
                                return@ModernSignUpCard
                            }
                            showMascot = false
                            isLoading = true
                            errorMessage = ""
                            scope.launch {
                                // Call existing performSignUp from LoginScreen.kt
                                performSignUp(
                                    username = username,
                                    email = email,
                                    displayName = displayName,
                                    password = password,
                                    context = context,
                                    on2FASetupRequired = { tempToken, userId ->
                                        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: 2FA setup required during signup")
                                        isLoading = false
                                        twoFATempToken = tempToken
                                        twoFASetupRequired = true
                                        isSignUpFlow = true
                                        show2FAScreen = true
                                    },
                                    onError = { error ->
                                        android.util.Log.d("FREETIME_LOGIN", "ModernLoginScreen: Signup error: $error")
                                        errorMessage = error
                                        isLoading = false
                                        showMascot = true
                                    },
                                    setLoading = { isLoading = it }
                                )
                            }
                        }
                    )
                }
            }
            
            // Toggle Login/SignUp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoginMode) 
                        "Don't have an account? " 
                    else 
                        "Already have an account? ",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 14.sp
                )
                
                Text(
                    text = if (isLoginMode) "Sign Up" else "Log In",
                    color = CyberpunkTheme.PrimaryPurple,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        errorMessage = ""
                        isLoginMode = !isLoginMode
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ModernLoginCard(
    isLoading: Boolean,
    errorMessage: String,
    onErrorClear: () -> Unit,
    onLoginClick: (username: String, password: String, rememberMe: Boolean, force: Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (errorMessage.isNotEmpty()) {
            ModernErrorBox(message = errorMessage, onDismiss = onErrorClear)
        }
        
        ModernTextInput(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            placeholder = "Enter your username",
            icon = Icons.Default.Person,
            enabled = !isLoading
        )
        
        ModernTextInput(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "Enter your password",
            icon = Icons.Default.Lock,
            isPassword = true,
            showPassword = showPassword,
            onShowPasswordToggle = { showPassword = !showPassword },
            enabled = !isLoading
        )
        

        
        ModernActionButton(
            text = "LOG IN",
            isLoading = isLoading,
            onClick = { onLoginClick(username, password, true, true) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModernSignUpCard(
    isLoading: Boolean,
    errorMessage: String,
    onErrorClear: () -> Unit,
    onSignUpClick: (username: String, email: String, displayName: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (errorMessage.isNotEmpty()) {
            ModernErrorBox(message = errorMessage, onDismiss = onErrorClear)
        }
        
        ModernTextInput(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            placeholder = "Choose a username",
            icon = Icons.Default.Person,
            enabled = !isLoading
        )
        
        ModernTextInput(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "your.email@example.com",
            icon = Icons.Default.Email,
            enabled = !isLoading
        )
        
        ModernTextInput(
            value = displayName,
            onValueChange = { displayName = it },
            label = "Display Name",
            placeholder = "How should we call you?",
            icon = Icons.Default.Person,
            enabled = !isLoading
        )
        
        ModernTextInput(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "Create a strong password",
            icon = Icons.Default.Lock,
            isPassword = true,
            showPassword = showPassword,
            onShowPasswordToggle = { showPassword = !showPassword },
            enabled = !isLoading
        )
        
        ModernTextInput(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm Password",
            placeholder = "Re-enter your password",
            icon = Icons.Default.Lock,
            isPassword = true,
            showPassword = showPassword,
            onShowPasswordToggle = { showPassword = !showPassword },
            enabled = !isLoading
        )
        
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it },
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberpunkTheme.PrimaryPurple,
                    uncheckedColor = CyberpunkTheme.DarkGray
                )
            )
            ClickableTermsText(context = context)
        }
        
        ModernActionButton(
            text = "CREATE ACCOUNT",
            isLoading = isLoading,
            onClick = { 
                val validationError = when {
                    username.isBlank() -> "Username is required"
                    username.length < 3 -> "Username must be at least 3 characters"
                    !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username: letters, numbers, underscores only"
                    email.isBlank() -> "Email is required"
                    !email.contains("@") || !email.contains(".") -> "Invalid email format"
                    displayName.isBlank() -> "Display name is required"
                    password.isBlank() -> "Password is required"
                    password.length < 8 -> "Password must be at least 8 characters"
                    !password.contains(Regex("[A-Za-z]")) -> "Password needs letters (a-z, A-Z)"
                    !password.contains(Regex("[0-9]")) -> "Password needs numbers (0-9)"
                    !password.contains(Regex("[@\$%^&*!]")) -> "Password needs special chars (@\$%^&*!)"
                    confirmPassword.isBlank() -> "Please confirm your password"
                    password != confirmPassword -> "Passwords don't match"
                    !acceptedTerms -> "You must accept the terms to continue"
                    else -> null
                }
                if (validationError != null) {
                    onErrorClear()
                    // Trigger error display through parent
                    onSignUpClick("__ERROR__", validationError, "", "")
                } else {
                    onSignUpClick(username.trim(), email.trim(), displayName.trim(), password)
                }
            },
            enabled = acceptedTerms,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModernTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onShowPasswordToggle: () -> Unit = {},
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            color = CyberpunkTheme.PrimaryPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = CyberpunkTheme.GhostGray) },
            leadingIcon = { Icon(icon, null, tint = CyberpunkTheme.PrimaryPurple) },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = onShowPasswordToggle, modifier = Modifier.size(20.dp)) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null,
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !showPassword) 
                PasswordVisualTransformation() else VisualTransformation.None,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(
                    width = 1.5.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.CyberCyan,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                unfocusedBorderColor = CyberpunkTheme.DarkGray,
                cursorColor = CyberpunkTheme.CyberCyan,
                focusedContainerColor = Color(0xFF0F0F1E),
                unfocusedContainerColor = Color(0xFF0F0F1E)
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun ModernActionButton(
    text: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(56.dp)
            .border(
                width = 2.dp,
                color = CyberpunkTheme.CyberCyan,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = CyberpunkTheme.PrimaryPurple,
            contentColor = CyberpunkTheme.White,
            disabledContainerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 12.dp,
            pressedElevation = 16.dp
        )
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CyberpunkTheme.White,
                    strokeWidth = 2.dp
                )
                Text(
                    "LOADING...",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 15.sp
                )
            }
        } else {
            Text(
                text,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun ModernErrorBox(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFF00FF).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = Color(0xFFFF00FF).copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFFFF00FF), fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = Color(0xFFFF00FF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
@Composable
fun ClickableTermsText(context: Context) {
    val termsUrl = "https://freetime-official.org/terms"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("I agree to ", color = CyberpunkTheme.LightGray, fontSize = 13.sp)
        Text(
            text = "Terms & Privacy",
            color = Color(0xFFFF69B4),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl))
                    context.startActivity(intent)
                }
        )
    }
}

/**
 * Perform login - Always saves 30-day auto-login token
 */
suspend fun performLogin(
    username: String,
    password: String,
    context: Context,
    force: Boolean = false,
    onSuccess: () -> Unit,
    on2FARequired: (tempToken: String, setupRequired: Boolean) -> Unit,
    onError: (error: String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    try {
        setLoading(true)

        // Check network connectivity first
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val hasInternet = networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasInternet) {
            android.util.Log.e("FREETIME_LOGIN", "No internet connectivity detected")
            setLoading(false)
            onError("No internet connection.\n\nCheck your Wi-Fi or mobile data and try again.")
            return
        }
        android.util.Log.d("FREETIME_LOGIN", "Network connectivity confirmed")

        // Get API instance
        val api = com.freetime.app.data.network.ApiClient.getInstance()
        val prefs = com.freetime.app.data.local.SharedPreferencesHelper(context)
        
        // ✅ NEW: Initialize SessionManager for device tracking
        val sessionManager = com.freetime.app.services.SessionManager(context)
        val deviceId = sessionManager.getDeviceId()
        val deviceInfo = sessionManager.getDeviceInfo()
        
        android.util.Log.d("FREETIME_LOGIN", "Device ID: $deviceId")
        android.util.Log.d("FREETIME_LOGIN", "Device: ${deviceInfo.optString("deviceName")}")
        
        // Log server URL for debugging
        val serverUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()
        android.util.Log.d("FREETIME_LOGIN", "Connecting to: $serverUrl")
        
        // Create login request ✅ NEW: Include device info
        val loginRequest = com.freetime.app.data.network.LoginRequest(
            username = username,
            password = password,
            deviceId = deviceId,
            force = force,
            deviceInfo = com.freetime.app.data.network.DeviceInfo(
                platform = deviceInfo.optString("platform"),
                deviceName = deviceInfo.optString("deviceName"),
                osVersion = deviceInfo.optString("osVersion"),
                appVersion = deviceInfo.optString("appVersion"),
                androidId = deviceInfo.optString("androidId"),
                buildId = deviceInfo.optString("buildId")
            )
        )
        
        // Perform login
        val response = try {
            api.login(loginRequest)
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("FREETIME_LOGIN", "DNS Resolution Failed: Cannot find server at $serverUrl")
            setLoading(false)
            onError("Cannot reach server - DNS resolution failed.\n\nCheck that:\n- Server address is correct\n- You have internet connection")
            return
        } catch (e: java.net.ConnectException) {
            android.util.Log.e("FREETIME_LOGIN", "Connection Refused: Server not responding at $serverUrl")
            setLoading(false)
            onError("Server is not responding.\n\nCheck that:\n- Server is running\n- Port is correct\n- Firewall allows connections")
            return
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("FREETIME_LOGIN", "Connection Timeout: Server took too long to respond")
            setLoading(false)
            onError("Server took too long to respond.\n\nThe server may be:\n- Overloaded\n- Offline\n- Very slow network")
            return
        } catch (e: javax.net.ssl.SSLException) {
            android.util.Log.e("FREETIME_LOGIN", "SSL Error: ${e.message}")
            setLoading(false)
            onError("SSL Certificate Error.\n\nThis is usually a server configuration issue.")
            return
        } catch (e: java.io.IOException) {
            android.util.Log.e("FREETIME_LOGIN", "Network Error: ${e.message}")
            setLoading(false)
            onError("Network error: ${e.message ?: "Connection failed"}")
            return
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_LOGIN", "Unexpected error: ${e.javaClass.simpleName}: ${e.message}")
            setLoading(false)
            onError("Unexpected error: ${e.message ?: "Unknown error"}")
            return
        }
        
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: "{}"
            android.util.Log.e("FREETIME_LOGIN", "Login failed (HTTP ${response.code()}): $errorBody")
            setLoading(false)
            
            val errorMsg = try {
                val json = org.json.JSONObject(errorBody)
                val rawError = json.optString("error", "")
                val errorCode = json.optString("code", "")
                if (errorCode == "CONCURRENT_LOGIN" || rawError == "CONCURRENT_LOGIN") "account already in use"
                else json.optString("message", "Login failed. Please check your credentials.")
            } catch (e: Exception) {
                "Login failed. Please check your credentials."
            }
            
            onError(errorMsg)
            return
        }
        
        val loginResponse = response.body() ?: run {
            setLoading(false)
            onError("Invalid server response")
            return
        }
        
        // Check if 2FA is required
        if (loginResponse.requiresTwoFactor) {
            android.util.Log.d("FREETIME_LOGIN", "2FA required")
            setLoading(false)
            on2FARequired(
                loginResponse.tempToken ?: "",
                loginResponse.setupRequired
            )
            return
        }
        
        // Store token
        val token = loginResponse.token ?: run {
            setLoading(false)
            onError("No token received from server")
            return
        }
        
        // Extract userId from response (try multiple fields)
        val userId = loginResponse.user?.userId 
            ?: loginResponse.userId 
            ?: loginResponse.user?._id 
            ?: username // Fallback to username as unique identifier
        
        // ✅ NEW: Save session info
        val sessionId = loginResponse.sessionId ?: ""
        sessionManager.saveSession(sessionId, deviceId, token)
        
        // ALWAYS save token with 30-day auto-login (mandatory for all users)
        prefs.saveRememberMeToken(token, userId, username)
        android.util.Log.d("FREETIME_LOGIN", "30-day auto-login token saved for user: $userId")
        android.util.Log.d("FREETIME_LOGIN", "  User will stay logged in for 30 days - no password required on app restart")
        
        // CRITICAL: Also save the token as the active session token (for immediate API calls)
        prefs.saveAuthData(
            token = token,
            userId = userId,
            username = username,
            deviceId = deviceId
        )
        android.util.Log.d("FREETIME_LOGIN", "Session token saved - API calls will now work")
        
        // Mark that this is a fresh login (for showing info dialog)
        prefs.saveBoolean("show_remember_me_info", true)
        
        setLoading(false)
        android.util.Log.d("FREETIME_LOGIN", "Login successful - auto-login enabled for 30 days")
        onSuccess()
        
    } catch (e: Exception) {
        android.util.Log.e("FREETIME_LOGIN", "Login exception: ${e.message}", e)
        setLoading(false)
        onError("Login error: ${e.message ?: "Unknown error"}")
    }
}