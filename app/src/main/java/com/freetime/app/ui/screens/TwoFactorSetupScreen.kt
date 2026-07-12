package com.freetime.app.ui.screens

import android.widget.Toast
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freetime.app.data.network.ApiClient
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.Base64
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.models.*
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.theme.PrimaryMagenta
import com.freetime.app.ui.theme.PrimaryMagenta
import com.freetime.app.ui.utils.*
import kotlinx.coroutines.launch

/**
 * 2FA Setup During Registration Screen
 * Displays QR code, manual secret, and backup codes
 */
@Composable
fun TwoFactorSetupDuringRegistrationScreen(
    tempToken: String,
    onSetupComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    val scope = rememberCoroutineScope()
    
    var step by remember { mutableStateOf(1) } // 1: QR Code, 2: Verify, 3: Backup Codes
    var qrCodeUrl by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }
    var backupCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var totpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) } // Start as loading
    var errorMessage by remember { mutableStateOf("") }
    var hasFetchError by remember { mutableStateOf(false) }

    // Step 1: Fetch QR Code
    LaunchedEffect(Unit) {
        scope.launch {
            fetchSetupAuthenticator(
                tempToken = tempToken,
                onSuccess = { response ->
                    qrCodeUrl = response.qrCode
                    secret = response.secret
                    backupCodes = response.backupCodes
                    isLoading = false
                    hasFetchError = false
                },
                onError = { error ->
                    errorMessage = error
                    isLoading = false
                    hasFetchError = true
                    onError(error)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E27), Color(0xFF1a1f3a))
                )
            )
    ) {
        // Show error screen if fetch failed
        if (hasFetchError) {
            ErrorRetryScreen(
                errorMessage = errorMessage,
                onRetry = {
                    isLoading = true
                    hasFetchError = false
                    scope.launch {
                        fetchSetupAuthenticator(
                            tempToken = tempToken,
                            onSuccess = { response ->
                                qrCodeUrl = response.qrCode
                                secret = response.secret
                                backupCodes = response.backupCodes
                                isLoading = false
                                hasFetchError = false
                            },
                            onError = { error ->
                                errorMessage = error
                                isLoading = false
                                hasFetchError = true
                                onError(error)
                            }
                        )
                    }
                }
            )
        } else {
            when (step) {
                1 -> {
                    TwoFactorQRCodeStep(
                        qrCodeUrl = qrCodeUrl,
                        secret = secret,
                        onNext = { step = 2 },
                        isLoading = isLoading
                    )
                }
                2 -> {
                    TwoFactorVerifySetupStep(
                        totpCode = totpCode,
                        onTotpChange = { totpCode = it },
                        errorMessage = errorMessage,
                        isLoading = isLoading,
                        onVerify = {
                            scope.launch {
                                verifyAuthenticatorCode(
                                    tempToken = tempToken,
                                    totpCode = totpCode,
                                    context = context,
                                    onSuccess = { step = 3 },
                                    onError = { errorMessage = it },
                                    setLoading = { isLoading = it }
                                )
                            }
                        }
                    )
                }
                3 -> {
                    TwoFactorBackupCodesStep(
                        backupCodes = backupCodes,
                        onComplete = {
                            // Clear temp token after successful 2FA setup
                            val prefs = SharedPreferencesHelper(context)
                            prefs.clearTempToken()
                            onSetupComplete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TwoFactorQRCodeStep(
    qrCodeUrl: String?,
    secret: String?,
    onNext: () -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    
    // Calculate responsive values
    val contentPadding = responsivePaddingLarge(deviceSize)
    val spacing = responsiveSpacingMedium(deviceSize)
    val qrCodeSize = if (deviceSize == DeviceSize.PHONE) {
        minOf(280.dp, 300.dp)
    } else if (deviceSize == DeviceSize.TABLET) {
        minOf(400.dp, 400.dp)
    } else {
        minOf(450.dp, 450.dp)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Spacer(modifier = Modifier.height(responsiveSpacingLarge(deviceSize)))
        
        Text(
            "Scan QR Code",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PrimaryMagenta
        )
        
        Text(
            "Open your authenticator app and scan this QR code",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        if (isLoading) {
            Column(
                modifier = Modifier
                    .size(qrCodeSize)
                    .background(Color(0xFF1a1f3a), RoundedCornerShape(15.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = PrimaryMagenta,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
            }
        } else if (qrCodeUrl != null && qrCodeUrl.isNotEmpty()) {
            // Decode and render QR code from base64 data URI
            val bitmap = remember(qrCodeUrl) {
                decodeBase64ToQRBitmap(qrCodeUrl)
            }
            
            val borderShape = RoundedCornerShape(15.dp)
            
            Surface(
                modifier = Modifier
                    .size(qrCodeSize)
                    .clip(borderShape),
                color = Color.White
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("QR code unavailable", color = Color.Red)
                    }
                }
            }
        }

        // Manual Entry Fallback
        if (secret != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1a1f3a), RoundedCornerShape(12.dp))
                    .padding(responsivePaddingMedium(deviceSize)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(responsiveSpacingSmall(deviceSize))
            ) {
                Text(
                    "Can't scan? Enter manually:",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    secret,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = PrimaryMagenta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0E27), RoundedCornerShape(8.dp))
                        .padding(responsivePaddingMedium(deviceSize)),
                    textAlign = TextAlign.Center,
                    fontSize = responsiveBodySmallFont(deviceSize)
                )
                
                // Copy button
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Secret", secret)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(responsiveButtonHeight(deviceSize)),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copy Secret", fontSize = responsiveBodySmallFont(deviceSize), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            enabled = !isLoading && qrCodeUrl != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(responsiveButtonHeight(deviceSize)),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("I've Scanned the Code", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun TwoFactorVerifySetupStep(
    totpCode: String,
    onTotpChange: (String) -> Unit,
    errorMessage: String,
    isLoading: Boolean,
    onVerify: () -> Unit
) {
    val context = LocalContext.current
    val deviceSize = rememberDeviceSize(context)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(responsivePaddingLarge(deviceSize)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingLarge(deviceSize))
    ) {
        Spacer(modifier = Modifier.height(responsiveSpacingLarge(deviceSize)))
        
        Icon(
            Icons.Filled.Lock,
            contentDescription = "Verify",
            tint = PrimaryMagenta,
            modifier = Modifier.size(responsiveIconLarge(deviceSize))
        )
        
        Text(
            "Verify Your Code",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PrimaryMagenta
        )
        
        Text(
            "Enter the 6-digit code from your authenticator app",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        OutlinedTextField(
            value = totpCode,
            onValueChange = { if (it.length <= 6) onTotpChange(it) },
            label = { Text("6-Digit Code", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.6f)
                .height(if (deviceSize == DeviceSize.PHONE) 64.dp else 72.dp)
                .clip(RoundedCornerShape(12.dp)),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 6.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryMagenta,
                unfocusedBorderColor = PrimaryMagenta.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0A0E27),
                unfocusedContainerColor = Color(0xFF0A0E27),
                cursorColor = PrimaryMagenta
            )
        )

        AnimatedVisibility(
            visible = errorMessage.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.6f)
                    .clip(RoundedCornerShape(10.dp)),
                color = Color(0xFF7F1D1D)
            ) {
                Text(
                    "⚠️ $errorMessage",
                    modifier = Modifier.padding(responsivePaddingMedium(deviceSize)),
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onVerify,
            enabled = totpCode.length == 6 && !isLoading,
            modifier = Modifier
                .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.6f)
                .height(responsiveButtonHeight(deviceSize)),
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
                Text("Verify Code", fontWeight = FontWeight.Bold, fontSize = responsiveBodySmallFont(deviceSize))
            }
        }
    }
}

@Composable
fun TwoFactorBackupCodesStep(
    backupCodes: List<String>,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var codesCopied by remember { mutableStateOf(false) }
    val deviceSize = rememberDeviceSize(context)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(responsivePaddingLarge(deviceSize)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(responsiveSpacingMedium(deviceSize))
    ) {
        Spacer(modifier = Modifier.height(responsiveSpacingLarge(deviceSize)))
        
        Icon(
            Icons.Filled.Save,
            contentDescription = "Backup",
            tint = PrimaryMagenta,
            modifier = Modifier.size(responsiveIconLarge(deviceSize))
        )
        
        Text(
            "Save Your Backup Codes",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PrimaryMagenta,
            textAlign = TextAlign.Center
        )

        Text(
            "⚠️ Store these codes in a safe place\nEach code can be used once if you lose your authenticator",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB74D),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.8f)
                .clip(RoundedCornerShape(12.dp)),
            color = Color(0xFF0A0E27)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(responsivePaddingMedium(deviceSize)),
                verticalArrangement = Arrangement.spacedBy(responsiveSpacingSmall(deviceSize))
            ) {
                backupCodes.forEachIndexed { index, code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF1a1f3a),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(responsivePaddingMedium(deviceSize)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = PrimaryMagenta,
                            fontSize = responsiveBodySmallFont(deviceSize)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Backup Codes", backupCodes.joinToString("\n"))
                clipboard.setPrimaryClip(clip)
                codesCopied = true
                Toast.makeText(context, "Codes copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.8f)
                .height(responsiveButtonHeight(deviceSize)),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (codesCopied) "✓ Codes Copied" else "Copy All Codes", fontWeight = FontWeight.Bold, fontSize = responsiveBodySmallFont(deviceSize))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth(if (deviceSize == DeviceSize.PHONE) 1f else 0.8f)
                .height(responsiveButtonHeight(deviceSize)),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue to App", fontWeight = FontWeight.Bold, fontSize = responsiveBodySmallFont(deviceSize))
        }
    }
}

// Helper Functions for 2FA API Calls

suspend fun fetchSetupAuthenticator(
    tempToken: String,
    onSuccess: (SetupAuthenticatorResponse) -> Unit,
    onError: (String) -> Unit
) {
    try {
        // Use BuildConfig to get the server URL (dynamically configured)
        // This ensures global connectivity using HTTPS for example.com
        val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL
        val setupUrl = "$baseUrl/api/setup-authenticator"
        val jsonBody = "{\"setupMode\":\"setup\"}"
        
        android.util.Log.d("TwoFactorSetup", "Fetching authenticator setup from: $setupUrl")
        
        val responseBody = RawSocketHttpClient.post(setupUrl, jsonBody, mapOf(
            "Authorization" to "Bearer $tempToken",
            "Content-Type" to "application/json"
        ))
        
        android.util.Log.d("TwoFactorSetup", "Setup response received: $responseBody")
        
        // Parse the JSON response
        val gson = com.google.gson.Gson()
        val setupResponse = gson.fromJson(responseBody, SetupAuthenticatorResponse::class.java)
        
        android.util.Log.d("TwoFactorSetup", "Parsed response - Success: ${setupResponse.success}, QR: ${setupResponse.qrCode?.take(50)}, Secret: ${setupResponse.secret}")
        
        if (setupResponse.success) {
            onSuccess(setupResponse)
        } else {
            onError(setupResponse.error ?: "Failed to generate QR code")
        }
    } catch (e: Exception) {
        android.util.Log.e("TwoFactorSetup", "Setup error: ${e.message}", e)
        onError(e.message ?: "Connection error")
    }
}

suspend fun verifyAuthenticatorCode(
    tempToken: String,
    totpCode: String,
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    try {
        // Use BuildConfig to get the server URL (dynamically configured)
        // This ensures global connectivity using HTTPS for example.com
        val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL
        val verifyUrl = "$baseUrl/api/verify-authenticator"
        val jsonBody = "{\"totpCode\":\"$totpCode\",\"rememberMe\":false}"
        
        android.util.Log.d("TwoFactorSetup", "Verifying TOTP code at: $verifyUrl")
        
        val responseBody = RawSocketHttpClient.post(verifyUrl, jsonBody, mapOf(
            "Authorization" to "Bearer $tempToken",
            "Content-Type" to "application/json"
        ))
        
        android.util.Log.d("TwoFactorSetup", "Verify response received")
        
        // Parse the JSON response
        val gson = com.google.gson.Gson()
        val verifyResponse = gson.fromJson(responseBody, VerifyAuthenticatorResponse::class.java)
        
        if (verifyResponse.success && verifyResponse.token != null) {
            // Save the actual JWT token
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
            onSuccess()
        } else {
            onError(verifyResponse.error ?: "Code verification failed")
        }
    } catch (e: Exception) {
        android.util.Log.e("TwoFactorSetup", "Verify error: ${e.message}", e)
        onError(e.message ?: "Connection error")
    } finally {
        setLoading(false)
    }
}

// Error Screen with Retry Option
@Composable
fun ErrorRetryScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = "Error",
            tint = Color.Red,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Setup Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Retry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/**
 * Decodes a base64 encoded QR code image from data URI format
 * Handles data URIs like: data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
 */
fun decodeBase64ToQRBitmap(dataUri: String): Bitmap? {
    return try {
        // Remove the data URI prefix if present
        val base64String = if (dataUri.contains(",")) {
            dataUri.substringAfter(",")
        } else {
            dataUri
        }
        
        // Decode base64 to byte array
        val decodedBytes = Base64.getDecoder().decode(base64String)
        
        // Decode byte array to Bitmap
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        android.util.Log.e("QRCodeDecoder", "Failed to decode QR code: ${e.message}")
        null
    }
}


