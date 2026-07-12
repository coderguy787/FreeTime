package com.freetime.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.models.*
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.theme.PrimaryMagenta
import com.freetime.app.ui.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * 2FA Login Verification Screen
 * Handles TOTP code entry during login when 2FA is required
 */
@Composable
fun TwoFactorLoginVerificationScreen(
    tempToken: String,
    setupRequired: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    if (setupRequired) {
        // User needs to setup 2FA first (first login after account creation)
        TwoFactorSetupDuringRegistrationScreen(
            tempToken = tempToken,
            onSetupComplete = onSuccess,
            onError = onError
        )
    } else {
        // User already has 2FA setup, just verify the code
        val deviceSize = rememberDeviceSize(context)
        
        var totpCode by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var attemptsRemaining by remember { mutableStateOf(5) }
        var timeRemaining by remember { mutableStateOf(150) } // 2 minutes 30 seconds
        var isLocked by remember { mutableStateOf(false) }
        var useBackupCode by remember { mutableStateOf(false) }

        // Countdown timer - expires after 2:30 and redirects to login
        LaunchedEffect(Unit) {
            while (timeRemaining > 0 && !isLocked) {
                delay(1000)
                timeRemaining--
                
                if (timeRemaining == 0) {
                    isLocked = true
                    errorMessage = "Verification window expired. Please try logging in again."
                    // Clear temp token
                    val prefs = SharedPreferencesHelper(context)
                    prefs.clearTempToken()
                    
                    // Auto-redirect to login after 2 seconds
                    delay(2000)
                    onError("Session expired")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0E27), Color(0xFF1a1f3a))
                    )
                )
                .padding(responsiveSpacingLarge(deviceSize)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(responsiveSpacingLarge(deviceSize))
        ) {
            // Show either TOTP or Backup Code screen based on selection
            if (useBackupCode) {
                TwoFactorBackupCodeVerificationScreenContent(
                    tempToken = tempToken,
                    deviceSize = deviceSize,
                    onSuccess = onSuccess,
                    onError = onError,
                    onBackToTotp = { useBackupCode = false }
                )
            } else {
                TwoFactorTotpVerificationScreenContent(
                    tempToken = tempToken,
                    deviceSize = deviceSize,
                    totpCode = totpCode,
                    onTotpChange = { totpCode = it },
                    isLoading = isLoading,
                    setLoading = { isLoading = it },
                    errorMessage = errorMessage,
                    setErrorMessage = { errorMessage = it },
                    attemptsRemaining = attemptsRemaining,
                    setAttemptsRemaining = { attemptsRemaining = it },
                    timeRemaining = timeRemaining,
                    isLocked = isLocked,
                    onSuccess = onSuccess,
                    onError = onError,
                    onUseBackupCode = { useBackupCode = true },
                    scope = scope,
                    context = context
                )
            }
        }
    }
}

/**
 * TOTP Code Entry Screen Content
 */
@Composable
fun TwoFactorTotpVerificationScreenContent(
    tempToken: String,
    deviceSize: DeviceSize,
    totpCode: String,
    onTotpChange: (String) -> Unit,
    isLoading: Boolean,
    setLoading: (Boolean) -> Unit,
    errorMessage: String,
    setErrorMessage: (String) -> Unit,
    attemptsRemaining: Int,
    setAttemptsRemaining: (Int) -> Unit,
    timeRemaining: Int,
    isLocked: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onUseBackupCode: () -> Unit,
    scope: CoroutineScope,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingMedium(deviceSize))
    ) {
        Spacer(modifier = Modifier.height(responsiveSpacingLarge(deviceSize)))

    // Header
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingSmall(deviceSize))
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = "2FA",
            tint = PrimaryMagenta,
            modifier = Modifier.size(responsiveIconLarge(deviceSize))
        )
        
        Text(
            "Two-Factor Authentication",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = PrimaryMagenta,
            textAlign = TextAlign.Center
        )
        
        Text(
            "Enter your 6-digit authenticator code",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Error Message
    AnimatedVisibility(
        visible = errorMessage.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            color = Color(0xFF7F1D1D)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "⚠️ $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold
                )
                
                if (attemptsRemaining > 0 && attemptsRemaining < 5) {
                    Text(
                        "Attempts remaining: $attemptsRemaining/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB74D)
                    )
                }
            }
        }
    }

    // TOTP Code Input
    OutlinedTextField(
        value = totpCode,
        onValueChange = { if (it.length <= 6) onTotpChange(it) },
        label = { Text("6-Digit Code", color = Color.Gray) },
        placeholder = { Text("000000", color = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp)),
        leadingIcon = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Code",
                tint = PrimaryMagenta
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = !isLoading && !isLocked,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            textAlign = TextAlign.Center,
            letterSpacing = 6.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryMagenta,
            unfocusedBorderColor = PrimaryMagenta.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF0A0E27),
            unfocusedContainerColor = Color(0xFF0A0E27),
            cursorColor = PrimaryMagenta
        )
    )

    Spacer(modifier = Modifier.height(responsiveSpacingLarge(deviceSize)))

    // Timer Display
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(responsiveCornerRadius(deviceSize))),
        color = Color(0xFF1a1f3a)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(responsiveSpacingMedium(deviceSize)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Time remaining:",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Text(
                "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (timeRemaining < 60) Color(0xFFFFB74D) else PrimaryMagenta
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }

        Spacer(modifier = Modifier.weight(1f))

        // Verify Button
        Button(
        onClick = {
            when {
                totpCode.isEmpty() -> setErrorMessage("Please enter the code")
                totpCode.length != 6 -> setErrorMessage("Code must be 6 digits")
                isLocked -> setErrorMessage("Verification window has expired")
                else -> {
                    scope.launch {
                        performLoginTwoFactorVerification(
                            tempToken = tempToken,
                            totpCode = totpCode,
                            context = context,
                            force = false, // Initial attempt not forced
                            onSuccess = {
                                Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            },
                            onError = { error ->
                                setErrorMessage(error)
                                onTotpChange("")
                                
                                // Update attempts remaining
                                if (error.contains("attempt")) {
                                    setAttemptsRemaining(when {
                                        error.contains("4") -> 4
                                        error.contains("3") -> 3
                                        error.contains("2") -> 2
                                        error.contains("1") -> 1
                                        else -> 0
                                    })
                                }
                            },
                            setLoading = setLoading
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(responsiveButtonHeight(deviceSize)),
        enabled = !isLoading && totpCode.length == 6 && !isLocked,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryMagenta,
            contentColor = Color.White,
            disabledContainerColor = PrimaryMagenta.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(responsiveCornerRadius(deviceSize)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text("Verify", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    // Backup Code Option
        TextButton(
            onClick = onUseBackupCode,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked
        ) {
            Text(
                "Don't have your phone? Use a backup code",
                color = PrimaryMagenta,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Backup Code Entry Screen Content
 */
@Composable
fun TwoFactorBackupCodeVerificationScreenContent(
    tempToken: String,
    deviceSize: DeviceSize,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onBackToTotp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var backupCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingMedium(deviceSize))
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Icon(
            Icons.Filled.Key,
            contentDescription = "Backup Code",
            tint = PrimaryMagenta,
            modifier = Modifier.size(64.dp)
        )
        
        Text(
            "Use Backup Code",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PrimaryMagenta
        )
        
        Text(
            "Enter one of your backup codes",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = backupCode,
            onValueChange = { backupCode = it },
            label = { Text("Backup Code", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryMagenta,
                unfocusedBorderColor = PrimaryMagenta.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0A0E27),
                unfocusedContainerColor = Color(0xFF0A0E27)
            )
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                color = Color(0xFF7F1D1D)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⚠️ $errorMessage",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // ✅ NEW: Show force login option if account is in use
                    if (errorMessage.contains("account already in use", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    performBackupCodeVerification(
                                        tempToken = tempToken,
                                        backupCode = backupCode,
                                        context = context,
                                        force = true,
                                        onSuccess = {
                                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                            onSuccess()
                                        },
                                        onError = { error ->
                                            errorMessage = error
                                            backupCode = ""
                                        },
                                        setLoading = { isLoading = it }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))
                        ) {
                            Text("LOGOUT OTHER DEVICES & VERIFY", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                when {
                    backupCode.isEmpty() -> errorMessage = "Please enter your backup code"
                    else -> {
                        scope.launch {
                            performBackupCodeVerification(
                                tempToken = tempToken,
                                backupCode = backupCode,
                                context = context,
                                force = false, // Initial attempt not forced
                                onSuccess = {
                                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                    onSuccess()
                                },
                                onError = { error ->
                                    errorMessage = error
                                    backupCode = ""
                                },
                                setLoading = { isLoading = it }
                            )
                        }
                    }
                }
            },
            enabled = backupCode.isNotEmpty() && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Verify Backup Code", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Back to TOTP Button
        TextButton(
            onClick = onBackToTotp,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(
                "← Back to Authenticator Code",
                color = PrimaryMagenta,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
suspend fun performLoginTwoFactorVerification(
    tempToken: String,
    totpCode: String,
    context: android.content.Context,
    force: Boolean = false,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    try {
        val apiService = ApiClient.getInstance()
        
        val response = apiService.verifyLoginTotp(
            token = "Bearer $tempToken",
            request = VerifyLoginTotpRequest(totpCode = totpCode, force = force)
        )
        
        if (response.isSuccessful && response.body() != null) {
            val verifyResponse = response.body()!!
            
            if (verifyResponse.success && verifyResponse.token != null) {
                // Save token and user info
                val prefs = SharedPreferencesHelper(context)
                val user = verifyResponse.user
                if (user != null) {
                    prefs.saveAuthData(
                        token = verifyResponse.token,
                        userId = user.id.ifEmpty { user.userId },
                        username = user.username,
                        deviceId = android.os.Build.ID
                    )
                }
                // Clear temp token
                prefs.clearTempToken()
                onSuccess()
            } else {
                onError(verifyResponse.error ?: "Verification failed")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            try {
                val errorJson = org.json.JSONObject(errorBody ?: "{}")
                val rawErrorMsg = errorJson.optString("error", "Verification failed")
                val errorCode = errorJson.optString("code", "")
                val errorMsg = if (errorCode == "CONCURRENT_LOGIN" || rawErrorMsg == "CONCURRENT_LOGIN") "account already in use" else rawErrorMsg
                val remaining = errorJson.optInt("attemptsRemaining", 0)
                val message = if (remaining > 0) {
                    "$errorMsg (Attempt ${6 - remaining}/5)"
                } else {
                    if (rawErrorMsg == "CONCURRENT_LOGIN") "account already in use" 
                    else "Too many failed attempts. Please try again later."
                }
                onError(message)
            } catch (e: Exception) {
                onError("Verification failed: ${response.code()}")
            }
        }
    } catch (e: Exception) {
        onError(e.message ?: "Connection error")
    } finally {
        setLoading(false)
    }
}



/**
 * 2FA Backup Code Entry Screen (for when user doesn't have authenticator)
 * This is now integrated into the main screen via TwoFactorBackupCodeVerificationScreenContent
 */

/**
 * Verify backup code (uses same endpoint as TOTP but with backup code)
 */
suspend fun performBackupCodeVerification(
    tempToken: String,
    backupCode: String,
    context: android.content.Context,
    force: Boolean = false,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    try {
        val apiService = ApiClient.getInstance()
        
        // Use verifyLoginTotp endpoint but with backup code instead of TOTP
        val response = apiService.verifyLoginTotp(
            token = "Bearer $tempToken",
            request = VerifyLoginTotpRequest(totpCode = backupCode, force = force)
        )
        
        if (response.isSuccessful && response.body() != null) {
            val verifyResponse = response.body()!!
            
            if (verifyResponse.success && verifyResponse.token != null) {
                val prefs = SharedPreferencesHelper(context)
                val user = verifyResponse.user
                if (user != null) {
                    prefs.saveAuthData(
                        token = verifyResponse.token,
                        userId = user.id.ifEmpty { user.userId },
                        username = user.username,
                        deviceId = android.os.Build.ID
                    )
                }
                prefs.clearTempToken()
                onSuccess()
            } else {
                onError(verifyResponse.error ?: "Backup code invalid")
            }
        } else {
            val errorBody = response.errorBody()?.string()
            try {
                val errorJson = org.json.JSONObject(errorBody ?: "{}")
                val rawErrorMsg = errorJson.optString("error", "Backup code verification failed")
                val errorCode = errorJson.optString("code", "")
                if (errorCode == "CONCURRENT_LOGIN" || rawErrorMsg == "CONCURRENT_LOGIN") {
                    onError("account already in use")
                } else {
                    onError(rawErrorMsg)
                }
            } catch (e: Exception) {
                onError("Backup code verification failed: ${response.code()}")
            }
        }
    } catch (e: Exception) {
        onError(e.message ?: "Connection error")
    } finally {
        setLoading(false)
    }
}


