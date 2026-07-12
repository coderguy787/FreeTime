package com.freetime.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.freetime.app.R
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.HttpUrlConnectionClient
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.LoginRequest
import com.freetime.app.data.network.SignUpRequest
import com.freetime.app.ui.theme.PrimaryPurple
import com.freetime.app.ui.theme.DarkBlack
import com.freetime.app.ui.theme.SecondaryBlack
import com.freetime.app.ui.theme.AccentLightPurple
import org.json.JSONObject
import org.json.JSONException
import com.freetime.app.ui.utils.*
import com.freetime.app.utils.DeviceFingerprint
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.components.CyberpunkButton
import com.freetime.app.ui.components.CyberpunkCard
import com.freetime.app.ui.components.ButtonVariant
import com.freetime.app.ui.components.ButtonSize
import com.freetime.app.ui.components.DiscordStyleTextField
import com.freetime.app.ui.components.DiscordStyleButton
import com.freetime.app.ui.components.DiscordStyleCard
import com.freetime.app.ui.components.DiscordStyleDivider
import com.freetime.app.ui.components.DiscordStyleSectionHeader
import androidx.compose.material3.ExperimentalMaterial3Api



suspend fun performLoginViaHttpUrlConnection(
    username: String,
    password: String,
    context: android.content.Context,
    rememberMe: Boolean = false,
    onSuccess: () -> Unit,
    on2FARequired: (tempToken: String, setupRequired: Boolean) -> Unit,
    onError: (String) -> Unit
) {
    return try {
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Starting login request with rememberMe=$rememberMe")
        val loginRequest = mapOf(
            "username" to username,
            "password" to password,
            "rememberMe" to rememberMe
        )
        val jsonBody = Gson().toJson(loginRequest)
        
        val baseUrl = ApiClient.getBaseUrl()
        val loginUrl = baseUrl.trimEnd('/') + "/api/login"
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Login URL: $loginUrl")
        
        val responseJson = withContext(Dispatchers.IO) {
            android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Sending request with rememberMe=$rememberMe")
            RawSocketHttpClient.post(loginUrl, jsonBody)
        }
        
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Response received: ${responseJson.take(200)}")
        
        val jsonBodyStart = responseJson.indexOf("{")
        val jsonText = if (jsonBodyStart >= 0) responseJson.substring(jsonBodyStart) else responseJson
        val jsonObject = JSONObject(jsonText)
        
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Parsed JSON, checking requiresTwoFactor")
        val requiresTwoFactor = jsonObject.optBoolean("requiresTwoFactor", false) || jsonObject.optBoolean("twofaRequired", false)
        
        when {
            requiresTwoFactor -> {
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: 2FA required")
                val tempToken = jsonObject.optString("tempToken", "")
                val setupRequired = jsonObject.optBoolean("setupRequired", false)
                val prefs = SharedPreferencesHelper(context)
                prefs.saveTempToken(token = tempToken, setupRequired = setupRequired)
                on2FARequired(tempToken, setupRequired)
            }
            jsonObject.optBoolean("success", false) -> {
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Login successful")
                val token = jsonObject.optString("token", "")
                val userId = jsonObject.optJSONObject("user")?.optString("userId", "") ?: ""
                val prefs = SharedPreferencesHelper(context)
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Saving token: ${token.take(20)}..., userId: $userId")
                
                // Always save auth data for current session (needed for API calls)
                prefs.saveAuthData(
                    token = token,
                    userId = userId,
                    username = username,
                    deviceId = android.os.Build.ID
                )
                
                if (rememberMe) {
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: rememberMe=true, saving Remember Me token for 30-day persistence")
                    prefs.saveRememberMeToken(token = token, userId = userId, username = username)
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Remember Me token saved - user will auto-login for 30 days")
                } else {
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: rememberMe=false - session will end on app close/restart")
                }
                
                // 🔔 Register pending FCM token if it exists
                val pendingFcmToken = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                    .getString("pending_fcm_token", null)
                if (!pendingFcmToken.isNullOrEmpty()) {
                    android.util.Log.d("FREETIME_LOGIN", "📱 Registering pending FCM token after successful login: ${pendingFcmToken.take(20)}...")
                    try {
                        val fcmRegisterUrl = ApiClient.getBaseUrl().trimEnd('/') + "/api/notifications/register-token"
                        val fcmRegisterBody = mapOf(
                            "fcmToken" to pendingFcmToken
                        ).let { Gson().toJson(it) }
                        
                        withContext(Dispatchers.IO) {
                            RawSocketHttpClient.post(
                                fcmRegisterUrl,
                                fcmRegisterBody,
                                mapOf("Authorization" to "Bearer $token")
                            )
                        }
                        android.util.Log.d("FREETIME_LOGIN", "✅ Pending FCM token registered successfully")
                        // Clear the pending token
                        context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                            .edit().remove("pending_fcm_token").apply()
                    } catch (fcmErr: Exception) {
                        android.util.Log.w("FREETIME_LOGIN", "⚠️ Failed to register pending FCM token: ${fcmErr.message}")
                        // Don't fail login if FCM registration fails
                    }
                }
                
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Calling onSuccess")
                onSuccess()
            }
            else -> {
                val rawErrorMsg = jsonObject.optString("message", jsonObject.optString("error", "Unknown error"))
                val errorCode = jsonObject.optString("code", "")
                val errorMsg = if (errorCode == "CONCURRENT_LOGIN" || rawErrorMsg == "CONCURRENT_LOGIN" || jsonObject.optString("error") == "CONCURRENT_LOGIN") "account already in use" else rawErrorMsg
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Login failed with error: $errorMsg")
                onError(errorMsg)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("FREETIME_LOGIN", "performLoginViaHttpUrlConnection: Exception occurred", e)
        onError(e.message ?: "Connection failed")
        RawSocketHttpClient.resetCancellation()
        throw e
    }
}

suspend fun performLoginViaOkHttp(
    username: String,
    password: String,
    context: android.content.Context,
    rememberMe: Boolean = false,
    onSuccess: () -> Unit,
    on2FARequired: (tempToken: String, setupRequired: Boolean) -> Unit,
    onError: (String) -> Unit
) {
    return try {
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Starting login request with rememberMe=$rememberMe")
        val apiService = ApiClient.getInstance()
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: API service obtained")
        
        val loginRequest = LoginRequest(
            username = username, 
            password = password,
            deviceId = android.os.Build.ID,
            rememberMe = rememberMe
        )
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Sending login request for user: $username with rememberMe=$rememberMe")
        
        val response = apiService.login(loginRequest)
        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Response received, code: ${response.code()}, successful: ${response.isSuccessful}")
        
        if (response.isSuccessful && response.body() != null) {
            android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Response is successful, parsing body")
            val loginResponse = response.body()
            android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Response body: $loginResponse")
            
            if (loginResponse != null) {
                android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Checking 2FA requirement: ${loginResponse.requiresTwoFactor}")
                
                if (loginResponse.requiresTwoFactor && loginResponse.tempToken != null) {
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: 2FA required, saving temp token")
                    val prefs = SharedPreferencesHelper(context)
                    prefs.saveTempToken(token = loginResponse.tempToken, setupRequired = loginResponse.setupRequired)
                    on2FARequired(loginResponse.tempToken, loginResponse.setupRequired)
                } else if (loginResponse.success) {
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Login successful, setting up auth session")
                    val prefs = SharedPreferencesHelper(context)
                    val user = loginResponse.user
                    if (user != null) {
                        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: User data present: ${user.userId}")
                        
                        // Always save auth data for current session (needed for API calls)
                        prefs.saveAuthData(
                            token = loginResponse.token,
                            userId = user.userId,
                            username = user.username,
                            deviceId = android.os.Build.ID
                        )
                        
                        if (rememberMe) {
                            android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: rememberMe=true, saving Remember Me token for 30-day persistence")
                            prefs.saveRememberMeToken(
                                token = loginResponse.token,
                                userId = user.userId,
                                username = user.username
                            )
                            android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Remember Me token saved - user will auto-login for 30 days")
                        } else {
                            android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: rememberMe=false - session will end on app close/restart")
                        }
                        
                        // 🔔 Register pending FCM token if it exists
                        val pendingFcmToken = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                            .getString("pending_fcm_token", null)
                        if (!pendingFcmToken.isNullOrEmpty()) {
                            android.util.Log.d("FREETIME_LOGIN", "📱 Registering pending FCM token after successful login: ${pendingFcmToken.take(20)}...")
                            try {
                                val fcmRegisterUrl = ApiClient.getBaseUrl().trimEnd('/') + "/api/notifications/register-token"
                                android.util.Log.d("FREETIME_LOGIN", "📡 FCM registration URL: $fcmRegisterUrl")
                                val fcmRegisterBody = mapOf(
                                    "fcmToken" to pendingFcmToken
                                ).let { Gson().toJson(it) }
                                
                                var registrationSucceeded = false
                                var lastError: Exception? = null
                                
                                // Try with configured URL first
                                try {
                                    withContext(Dispatchers.IO) {
                                        RawSocketHttpClient.post(
                                            fcmRegisterUrl,
                                            fcmRegisterBody,
                                            mapOf("Authorization" to "Bearer ${loginResponse.token}")
                                        )
                                    }
                                    registrationSucceeded = true
                                    android.util.Log.d("FREETIME_LOGIN", "✅ Pending FCM token registered successfully via primary URL")
                                } catch (primaryErr: Exception) {
                                    lastError = primaryErr
                                    android.util.Log.w("FREETIME_LOGIN", "⚠️ Primary registration failed: ${primaryErr.message}, trying HTTP fallback...")
                                    
                                    // Try HTTP fallback if HTTPS fails (for development/self-signed certs)
                                    try {
                                        val httpUrl = fcmRegisterUrl.replace("https://", "http://").replace(":443/", ":80/")
                                        android.util.Log.d("FREETIME_LOGIN", "🔄 Retrying with HTTP fallback: $httpUrl")
                                        withContext(Dispatchers.IO) {
                                            RawSocketHttpClient.post(
                                                httpUrl,
                                                fcmRegisterBody,
                                                mapOf("Authorization" to "Bearer ${loginResponse.token}")
                                            )
                                        }
                                        registrationSucceeded = true
                                        android.util.Log.d("FREETIME_LOGIN", "✅ Pending FCM token registered successfully via HTTP fallback")
                                    } catch (httpErr: Exception) {
                                        lastError = httpErr
                                        android.util.Log.w("FREETIME_LOGIN", "⚠️ HTTP fallback also failed: ${httpErr.message}")
                                    }
                                }
                                
                                if (registrationSucceeded) {
                                    // Clear the pending token
                                    context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                                        .edit().remove("pending_fcm_token").apply()
                                } else {
                                    android.util.Log.e("FREETIME_LOGIN", "❌ FCM token registration failed on all attempts", lastError)
                                    // Don't fail login if FCM registration fails
                                }
                            } catch (fcmErr: Exception) {
                                android.util.Log.e("FREETIME_LOGIN", "❌ Unexpected error during FCM registration: ${fcmErr.message}", fcmErr)
                                // Don't fail login if FCM registration fails
                            }
                        } else {
                            android.util.Log.w("FREETIME_LOGIN", "⚠️ No pending FCM token found to register")
                        }
                        
                        android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Auth session created, calling onSuccess")
                        onSuccess()
                    } else {
                        android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: User data is null in response")
                        onError("User data missing in response")
                    }
                } else {
                    android.util.Log.d("FREETIME_LOGIN", "performLoginViaOkHttp: Login failed, success=false, message=${loginResponse.message}")
                    onError(loginResponse.message)
                }
            } else {
                android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: Response body is null")
                onError("Invalid response")
            }
        } else {
            android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: Response not successful or body is null. Code: ${response.code()}")
            val errorBody = response.errorBody()?.string()
            android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: Error body: $errorBody")
            try {
                val errorJson = JSONObject(errorBody ?: "{}")
                val rawErrorMsg = errorJson.optString("error", errorJson.optString("message", "Login failed"))
                val errorCode = errorJson.optString("code", "")
                val errorMsg = if (errorCode == "CONCURRENT_LOGIN" || rawErrorMsg == "CONCURRENT_LOGIN") "account already in use" else rawErrorMsg
                onError(errorMsg)
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: Failed to parse error body", e)
                onError("Login failed - HTTP ${response.code()}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("FREETIME_LOGIN", "performLoginViaOkHttp: Exception occurred", e)
        onError(e.message ?: "Connection failed")
    }
}

suspend fun performSignUp(
    username: String,
    email: String,
    displayName: String,
    password: String,
    context: android.content.Context,
    on2FASetupRequired: (tempToken: String, userId: String) -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)
    try {
        val deviceFingerprint = DeviceFingerprint.generateFingerprint(context)
        
        if (Build.VERSION.SDK_INT >= 34) {
            performSignUpViaRawSocket(
                username = username,
                email = email,
                displayName = displayName,
                password = password,
                context = context,
                deviceFingerprint = deviceFingerprint,
                on2FASetupRequired = on2FASetupRequired,
                onError = onError,
                setLoading = setLoading
            )
        } else {
            performSignUpViaOkHttp(
                username = username,
                email = email,
                displayName = displayName,
                password = password,
                context = context,
                deviceFingerprint = deviceFingerprint,
                on2FASetupRequired = on2FASetupRequired,
                onError = onError,
                setLoading = setLoading
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("SIGNUP_DEBUG", "[CRITICAL] performSignUp exception: ${e.message}", e)
        onError(e.message ?: "Signup failed. Please check your internet connection and try again.")
        setLoading(false)
    }
}

suspend fun performSignUpViaRawSocket(
    username: String,
    email: String,
    displayName: String,
    password: String,
    context: android.content.Context,
    deviceFingerprint: com.freetime.app.data.network.DeviceFingerprint,
    on2FASetupRequired: (tempToken: String, userId: String) -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    try {
        val signUpRequest = mapOf(
            "username" to username,
            "email" to email,
            "displayName" to displayName,
            "password" to password,
            "confirmPassword" to password,
            "deviceFingerprint" to mapOf(
                // Primary device identifiers for rate limiting
                "androidId" to deviceFingerprint.androidId,           // ← PRIMARY: Cannot change without factory reset
                "hardwareSerial" to deviceFingerprint.hardwareSerial, // ← SECONDARY: Cannot change without bootloader
                
                // Secondary device info
                "deviceId" to deviceFingerprint.deviceId,
                "deviceModel" to deviceFingerprint.deviceModel,
                "deviceBrand" to deviceFingerprint.deviceBrand,
                "deviceName" to deviceFingerprint.deviceName,
                "product" to deviceFingerprint.product,
                
                // Build and version info
                "osVersion" to deviceFingerprint.osVersion,
                "appVersion" to deviceFingerprint.appVersion,
                "buildFingerprint" to deviceFingerprint.buildFingerprint,
                
                "timestamp" to deviceFingerprint.timestamp
            )
        )
        val jsonBody = Gson().toJson(signUpRequest)
        
        val baseUrl = ApiClient.getBaseUrl()
        val signupUrl = baseUrl.trimEnd('/') + "/api/signup"
        
        android.util.Log.d("SIGNUP_DEBUG", "Signup URL: $signupUrl")
        
        val responseJson = withContext(Dispatchers.IO) {
            RawSocketHttpClient.post(signupUrl, jsonBody)
        }
        
        android.util.Log.d("SIGNUP_DEBUG", "Raw response (length=${responseJson.length}): ${responseJson.take(500)}")
        android.util.Log.d("SIGNUP_DEBUG", "Response bytes: ${responseJson.toByteArray().size}")
        
        // Handle empty response - server may be returning empty body or timeout occurred
        if (responseJson.trim().isEmpty()) {
            // Try OkHttp as fallback for empty response
            android.util.Log.w("SIGNUP_DEBUG", "[CRITICAL] Empty response from RawSocket (length=0), trying OkHttp fallback...")
            android.util.Log.w("SIGNUP_DEBUG", "Raw socket may have timed out or server connection failed")
            performSignUpViaOkHttp(
                username = username,
                email = email,
                displayName = displayName,
                password = password,
                context = context,
                deviceFingerprint = deviceFingerprint,
                on2FASetupRequired = on2FASetupRequired,
                onError = onError,
                setLoading = setLoading
            )
            return@performSignUpViaRawSocket
        }
        
        val jsonBodyStart = responseJson.indexOf("{")
        if (jsonBodyStart == -1) {
            // Response doesn't contain JSON - might be an error page
            android.util.Log.e("SIGNUP_DEBUG", "No JSON in response (length=${responseJson.length}): ${responseJson.take(200)}")
            onError("Server returned unexpected format. Please try again or contact support.")
            return@performSignUpViaRawSocket
        }
        
        val jsonText = responseJson.substring(jsonBodyStart)
        val jsonObject = JSONObject(jsonText)
        
        android.util.Log.d("SIGNUP_DEBUG", "Parsed JSON: success=${jsonObject.optBoolean("success")}, error=${jsonObject.optString("error")}")
        
        when {
            jsonObject.optBoolean("success", false) -> {
                val tempToken = jsonObject.optString("tempToken", "")
                val userId = jsonObject.optString("userId", "")
                
                if (tempToken.isEmpty()) {
                    onError("Server error: No activation token received")
                    return@performSignUpViaRawSocket
                }
                
                val prefs = SharedPreferencesHelper(context)
                prefs.saveTempToken(token = tempToken, setupRequired = true)
                on2FASetupRequired(tempToken, userId)
            }
            jsonObject.has("error") -> {
                val errorMsg = jsonObject.optString("error", "Registration failed")
                val debugReason = jsonObject.optString("debug_reason", "")
                val message = jsonObject.optString("message", "")
                android.util.Log.w("SIGNUP_DEBUG", "Signup rejected: error=$errorMsg, message=$message, debug_reason=$debugReason")
                // Show message field if available (more descriptive), otherwise error
                val displayError = if (message.isNotEmpty() && message != errorMsg) "$errorMsg: $message" else errorMsg
                onError(displayError)
            }
            else -> {
                onError("Unexpected server response. Please try again.")
            }
        }
    } catch (e: JSONException) {
        android.util.Log.e("SIGNUP_DEBUG", "JSON parsing error: ${e.message}")
        onError("Server response format error. Please try again.")
    } catch (e: Exception) {
        android.util.Log.e("SIGNUP_DEBUG", "Signup error: ${e.message}", e)
        onError(e.message ?: "Connection failed")
    } finally {
        setLoading(false)
    }
}

suspend fun performSignUpViaOkHttp(
    username: String,
    email: String,
    displayName: String,
    password: String,
    context: android.content.Context,
    deviceFingerprint: com.freetime.app.data.network.DeviceFingerprint,
    on2FASetupRequired: (tempToken: String, userId: String) -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    try {
        val apiService = ApiClient.getInstance()
        val request = SignUpRequest(
            username = username,
            email = email,
            displayName = displayName,
            password = password,
            confirmPassword = password,
            deviceFingerprint = deviceFingerprint
        )
        
        android.util.Log.d("SIGNUP_DEBUG", "Sending signup request via OkHttp for user: $email")
        val response = apiService.signup(request)
        
        android.util.Log.d("SIGNUP_DEBUG", "Signup response code: ${response.code()}")
        
        if (response.isSuccessful && response.body() != null) {
            val signUpResponse = response.body()
            if (signUpResponse != null) {
                android.util.Log.d("SIGNUP_DEBUG", "Success response: success=${signUpResponse.success}, hasToken=${!signUpResponse.tempToken.isNullOrEmpty()}")
                
                if (signUpResponse.success && signUpResponse.tempToken != null) {
                    val prefs = SharedPreferencesHelper(context)
                    prefs.saveTempToken(token = signUpResponse.tempToken, setupRequired = true)
                    on2FASetupRequired(signUpResponse.tempToken, signUpResponse.userId ?: "")
                } else {
                    val errorMsg = signUpResponse.message ?: signUpResponse.error ?: "Registration failed"
                    onError(errorMsg)
                }
            } else {
                onError("Empty response from server")
            }
        } else {
            // Handle error response
            val errorBody = response.errorBody()?.string()
            android.util.Log.d("SIGNUP_DEBUG", "Error response: ${response.code()}, body=$errorBody")
            
            try {
                val errorJson = JSONObject(errorBody ?: "{}")
                val errorMsg = errorJson.optString("error") ?: errorJson.optString("message") ?: "Registration failed (${response.code()})"
                onError(errorMsg)
            } catch (e: JSONException) {
                // Fallback error message if JSON parsing fails
                onError("Registration failed. Please try again. Error: ${response.code()}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SIGNUP_DEBUG", "Signup error: ${e.message}", e)
        onError(e.message ?: "Connection failed")
    } finally {
        setLoading(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showLogoutPolicyDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var signUpUsername by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpDisplayName by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showPassword2 by remember { mutableStateOf(false) }
    var showPassword3 by remember { mutableStateOf(false) }
    
    var show2FAScreen by remember { mutableStateOf(false) }
    var twoFATempToken by remember { mutableStateOf("") }
    var twoFASetupRequired by remember { mutableStateOf(false) }
    var showSignUpFlow by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    android.util.Log.d("FREETIME_LOGIN", "LoginScreen composable: show2FAScreen=$show2FAScreen, twoFATempToken length=${twoFATempToken.length}")

    if (show2FAScreen) {
        android.util.Log.d("FREETIME_LOGIN", "LoginScreen: Showing 2FA screen, setupRequired=$twoFASetupRequired")
        if (showSignUpFlow) {
            TwoFactorSetupDuringRegistrationScreen(
                tempToken = twoFATempToken,
                onSetupComplete = {
                    show2FAScreen = false
                    showSignUpFlow = false
                    isSignUpMode = false
                    onLoginSuccess()
                },
                onError = { error ->
                    errorMessage = error
                    show2FAScreen = false
                }
            )
        } else {
            android.util.Log.d("FREETIME_LOGIN", "LoginScreen: Showing TwoFactorLoginVerificationScreen")
            TwoFactorLoginVerificationScreen(
                tempToken = twoFATempToken,
                setupRequired = twoFASetupRequired,
                onSuccess = {
                    show2FAScreen = false
                    onLoginSuccess()
                },
                onError = { error ->
                    errorMessage = error
                    show2FAScreen = false
                }
            )
        }
    } else {
        android.util.Log.d("FREETIME_LOGIN", "LoginScreen: Showing login form")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberpunkTheme.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                // FreeTime Logo/Branding
                Image(
                    painter = painterResource(id = R.drawable.freetime_logo),
                    contentDescription = "FreeTime Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                // FreeTime Title
                Text(
                    "FREETIME",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        fontSize = 28.sp
                    ),
                    color = CyberpunkTheme.PrimaryPurple
                )

                Text(
                    "Secure Peer-to-Peer Network",
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.5.sp),
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(CyberpunkTheme.DarkBlack, RoundedCornerShape(8.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { isSignUpMode = false; errorMessage = "" },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isSignUpMode) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.DarkBlack.copy(alpha = 0.5f),
                            contentColor = if (!isSignUpMode) CyberpunkTheme.White else CyberpunkTheme.LightGray
                        ),
                        shape = RoundedCornerShape(6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            "LOG IN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Button(
                        onClick = { isSignUpMode = true; errorMessage = "" },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSignUpMode) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.DarkBlack.copy(alpha = 0.5f),
                            contentColor = if (isSignUpMode) CyberpunkTheme.White else CyberpunkTheme.LightGray
                        ),
                        shape = RoundedCornerShape(6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            "REGISTER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Error Message
                AnimatedVisibility(
                    visible = errorMessage.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberpunkTheme.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "⚠ $errorMessage",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberpunkTheme.PrimaryPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            
                            // ✅ NEW: Show force login option if account is in use
                            if (errorMessage.contains("account already in use", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            performLogin(
                                                username, password, context,
                                                force = true,
                                                onSuccess = { 
                                                    showLogoutPolicyDialog = true
                                                },
                                                on2FARequired = { tempToken, setupRequired ->
                                                    twoFATempToken = tempToken
                                                    twoFASetupRequired = setupRequired
                                                    show2FAScreen = true
                                                    showSignUpFlow = false
                                                },
                                                onError = { 
                                                    errorMessage = it 
                                                },
                                                setLoading = { isLoading = it }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("LOGOUT OTHER DEVICES & LOGIN", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Forms Section
                if (!isSignUpMode) {
                    ProfessionalLoginForm(
                        username = username,
                        onUsernameChange = { username = it; errorMessage = "" },
                        password = password,
                        onPasswordChange = { password = it; errorMessage = "" },
                        showPassword = showPassword,
                        onShowPasswordChange = { showPassword = it },

                    )

                    Button(
                        onClick = {
                            when {
                                username.isEmpty() -> errorMessage = "Username required"
                                password.isEmpty() -> errorMessage = "Password required"
                                else -> {
                                    android.util.Log.d("FREETIME_LOGIN", "Login button clicked: username=$username")
                                    scope.launch {
                                        performLogin(
                                            username, password, context,
                                            force = false, // Initial attempt not forced
                                            onSuccess = { 
                                                android.util.Log.d("FREETIME_LOGIN", "Login button: onSuccess called, showing logout policy dialog")
                                                showLogoutPolicyDialog = true
                                            },
                                            on2FARequired = { tempToken, setupRequired ->
                                                android.util.Log.d("FREETIME_LOGIN", "Login button: on2FARequired called with tempToken length=${tempToken.length}, setupRequired=$setupRequired")
                                                twoFATempToken = tempToken
                                                twoFASetupRequired = setupRequired
                                                show2FAScreen = true
                                                showSignUpFlow = false
                                                android.util.Log.d("FREETIME_LOGIN", "Login button: State updated, show2FAScreen=$show2FAScreen")
                                            },
                                            onError = { 
                                                android.util.Log.d("FREETIME_LOGIN", "Login button: onError called: $it")
                                                errorMessage = it 
                                            },
                                            setLoading = { isLoading = it }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple,
                            contentColor = CyberpunkTheme.White,
                            disabledContainerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                            disabledContentColor = CyberpunkTheme.White.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CyberpunkTheme.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "LOG IN",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    // Register Form
                    ProfessionalRegisterForm(
                        username = signUpUsername,
                        onUsernameChange = { signUpUsername = it; errorMessage = "" },
                        email = signUpEmail,
                        onEmailChange = { signUpEmail = it; errorMessage = "" },
                        displayName = signUpDisplayName,
                        onDisplayNameChange = { signUpDisplayName = it; errorMessage = "" },
                        password = signUpPassword,
                        onPasswordChange = { signUpPassword = it; errorMessage = "" },
                        confirmPassword = signUpConfirmPassword,
                        onConfirmPasswordChange = { signUpConfirmPassword = it; errorMessage = "" },
                        showPassword = showPassword2,
                        onShowPasswordChange = { showPassword2 = it },
                        showConfirmPassword = showPassword3,
                        onShowConfirmPasswordChange = { showPassword3 = it },
                        acceptedTerms = acceptedTerms,
                        onAcceptedTermsChange = { acceptedTerms = it }
                    )

                    Button(
                        onClick = {
                            errorMessage = when {
                                signUpUsername.isEmpty() -> "Username required"
                                signUpUsername.length < 3 -> "Username must be 3+ chars (letters, numbers, _)"
                                !signUpUsername.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username: letters, numbers, underscores only"
                                signUpEmail.isEmpty() -> "Email required"
                                !signUpEmail.contains("@") -> "Invalid email format"
                                // Display name is optional
                                signUpPassword.isEmpty() -> "Password required"
                                signUpPassword.length < 8 -> "Password must be 8+ characters"
                                !signUpPassword.contains(Regex("[A-Za-z]")) -> "Password needs letters (a-z, A-Z)"
                                !signUpPassword.contains(Regex("[0-9]")) -> "Password needs numbers (0-9)"
                                !signUpPassword.contains(Regex("[@\$%^&*!]")) -> "Password needs special chars (@#\$%^&*!)"
                                signUpConfirmPassword.isEmpty() -> "Confirm password required"
                                signUpPassword != signUpConfirmPassword -> "Passwords don't match"
                                !acceptedTerms -> "Accept terms to continue"
                                else -> ""
                            }
                            
                            if (errorMessage.isEmpty()) {
                                scope.launch {
                                    performSignUp(
                                        signUpUsername, signUpEmail, signUpDisplayName,
                                        signUpPassword, context,
                                        on2FASetupRequired = { tempToken, userId ->
                                            twoFATempToken = tempToken
                                            twoFASetupRequired = true
                                            show2FAScreen = true
                                            showSignUpFlow = true
                                        },
                                        onError = { errorMessage = it },
                                        setLoading = { isLoading = it }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple,
                            contentColor = CyberpunkTheme.Black,
                            disabledContainerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                            disabledContentColor = CyberpunkTheme.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CyberpunkTheme.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "CREATE ACCOUNT",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        
        // 30-Day Logout Policy Dialog
        if (showLogoutPolicyDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showLogoutPolicyDialog = false
                    onLoginSuccess()
                },
                title = {
                    Text(
                        "Session Security Policy",
                        color = CyberpunkTheme.CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Welcome to FreeTime!",
                            color = CyberpunkTheme.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        
                        Text(
                            "For security reasons, your login session will automatically expire after 30 days of inactivity.",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            "After 30 days, you will be required to log in again to verify your identity and ensure account security.",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            "This helps protect your account from unauthorized access.",
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            android.util.Log.d("FREETIME_LOGIN", "User acknowledged 30-day logout policy")
                            showLogoutPolicyDialog = false
                            onLoginSuccess()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberpunkTheme.PrimaryPurple,
                            contentColor = CyberpunkTheme.White
                        ),
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "I Understand",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                containerColor = CyberpunkTheme.DarkBlack,
                textContentColor = CyberpunkTheme.White,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun ProfessionalLoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberpunkTheme.DarkBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DiscordStyleTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "Username",
            placeholder = "Enter your username",
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Username", tint = CyberpunkTheme.PrimaryPurple) }
        )

        DiscordStyleTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            placeholder = "Enter your password",
            isPassword = true,
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password", tint = CyberpunkTheme.PrimaryPurple) }
        )
        
        // Security Warning
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = CyberpunkTheme.CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "For your safety: You will be automatically logged out after 30 days of inactivity and will need to log in again.",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}


@Composable
fun ProfessionalRegisterForm(
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
    acceptedTerms: Boolean,
    onAcceptedTermsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberpunkTheme.DarkBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Password strength indicator
        Text(
            "Password must be 8+ chars: letters, numbers, special (@#\$%)",
            fontSize = 10.sp,
            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username *") },
            placeholder = { Text("Choose a username") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Username", tint = CyberpunkTheme.PrimaryPurple) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.PrimaryPurple,
                unfocusedLabelColor = CyberpunkTheme.LightGray,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                cursorColor = CyberpunkTheme.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email *") },
            placeholder = { Text("Enter your email") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email", tint = CyberpunkTheme.PrimaryPurple) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.PrimaryPurple,
                unfocusedLabelColor = CyberpunkTheme.LightGray,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                cursorColor = CyberpunkTheme.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name (optional)") },
            placeholder = { Text("Your full name") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Name", tint = CyberpunkTheme.PrimaryPurple) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.PrimaryPurple,
                unfocusedLabelColor = CyberpunkTheme.LightGray,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                cursorColor = CyberpunkTheme.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password *") },
            placeholder = { Text("Create a password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password", tint = CyberpunkTheme.PrimaryPurple) },
            trailingIcon = {
                IconButton(onClick = { onShowPasswordChange(!showPassword) }) {
                    Icon(
                        if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = CyberpunkTheme.PrimaryPurple
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.PrimaryPurple,
                unfocusedLabelColor = CyberpunkTheme.LightGray,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                cursorColor = CyberpunkTheme.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password *") },
            placeholder = { Text("Confirm password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Confirm", tint = CyberpunkTheme.PrimaryPurple) },
            trailingIcon = {
                IconButton(onClick = { onShowConfirmPasswordChange(!showConfirmPassword) }) {
                    Icon(
                        if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle password visibility",
                        tint = CyberpunkTheme.PrimaryPurple
                    )
                }
            },
            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberpunkTheme.PrimaryPurple,
                unfocusedBorderColor = CyberpunkTheme.LightGray.copy(alpha = 0.3f),
                focusedLabelColor = CyberpunkTheme.PrimaryPurple,
                unfocusedLabelColor = CyberpunkTheme.LightGray,
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                cursorColor = CyberpunkTheme.PrimaryPurple
            ),
            shape = RoundedCornerShape(8.dp)
        )

        // Terms & Conditions Agreement Section
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = onAcceptedTermsChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberpunkTheme.PrimaryPurple,
                    uncheckedColor = CyberpunkTheme.LightGray
                )
            )
            
            Text(
                text = "Terms & Conditions",
                color = CyberpunkTheme.PrimaryPurple,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse("https://freetime-official.org/terms")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("SIGNUP_TERMS", "Failed: ${e.message}")
                        }
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}




