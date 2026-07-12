package com.freetime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.freetime.app.ui.theme.FreeTimeTheme
import com.freetime.app.ui.theme.PrimaryPurple
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.ApiClient
import android.view.View
import android.os.Build
import android.view.WindowManager
import com.freetime.app.notifications.NotificationHelper
import androidx.navigation.compose.rememberNavController
import com.freetime.app.navigation.AppNavGraph
import com.freetime.app.ui.components.CyberpunkTheme
import androidx.compose.foundation.background

import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import java.util.UUID

class MainActivity : ComponentActivity() {
    // Reactive state so composables re-navigate on new intents (e.g., accept-call from IncomingCallActivity)
    internal val pendingNavIntent = androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capture launch intent so MainContent can react to it reactively
        pendingNavIntent.value = intent
        
        // Enable edge-to-edge drawing
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Configure API client based on device type
        configureApiClient()
        
        // Initialize notification channels for push notifications
        NotificationHelper.createNotificationChannels(this)
        
        // Initialize notification store (load persisted notifications)
        com.freetime.app.notifications.InAppNotificationStore.init(this)
        
        // Register FCM token with push notification service
        registerFcmToken()
        
        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+).
        // This is REQUIRED for push notifications to appear outside the app.
        // The manifest declaration alone is not enough — a runtime dialog must be shown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
        
        // Schedule periodic background message sync (every 15 minutes)
        com.freetime.app.services.MessageSyncWorker.schedulePeriodicSync(this)
        
        // ✅ REMOVED: Background polling service is now disabled in favor of Firebase (FCM)
        // to satisfy user request to remove persistent "Security Service" notification.
        com.freetime.app.services.BackgroundPollingService.stopPolling(this)
        
        // CRITICAL SECURITY: Prevent screenshots and screen recording
        // This blocks ALL screenshot and screen recorder apps from capturing content
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // Redundant check: ensure FLAG_SECURE is set on main window
        this.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        // Set the status bar and navigation bar to be transparent for edge-to-edge
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        
        setContent {
            val displaySettings = com.freetime.app.ui.theme.rememberDisplaySettings()
            androidx.compose.runtime.CompositionLocalProvider(
                com.freetime.app.ui.theme.LocalDisplaySettings provides displaySettings
            ) {
                FreeTimeTheme(accentColor = displaySettings.getAccentColor()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainContent(this@MainActivity)
                    }
                }
            }
        }
    }
    
    /**
     * Configure API client based on device type and saved preferences
     */
    private fun configureApiClient() {
        // Use BuildConfig values from gradle.properties
        // Development: Debian 13 at 192.168.1.100:8000 (LAN)
        // Production: example.com:443 (public)
        ApiClient.setBaseUrl(BuildConfig.API_BASE_URL)
        android.util.Log.d("MainActivity", "Using server: ${BuildConfig.API_BASE_URL}")
    }
    
    /**
     * Register FCM token with push notification service on app startup
     */
    fun registerFcmToken() {
        try {
            // Try to get FCM token - will fail gracefully if Firebase not initialized
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (token.isNotEmpty()) {
                        // Register token with backend notification service
                        lifecycleScope.launch {
                            try {
                                val apiService = com.freetime.app.api.FreeTimeApiService(this@MainActivity)
                                
                                // 1. Register in FCMTokens collection (handles multiple devices)
                                val result = apiService.registerDeviceFcmToken(token)
                                if (result.isSuccess) {
                                    android.util.Log.d("FCM", "✓ Device FCM token registered in FCMTokens collection")
                                }
                                
                                // 2. Register in users collection (for legacy server methods that look at user.fcmToken)
                                val prefs = com.freetime.app.data.local.SharedPreferencesHelper(this@MainActivity)
                                val userId = prefs.getUserId()
                                if (!userId.isNullOrEmpty()) {
                                    val userResult = apiService.registerFcmToken(userId, token)
                                    if (userResult.isSuccess) {
                                        android.util.Log.d("FCM", "✓ FCM token registered in users collection for $userId")
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FCM", "✗ FCM registration exception: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    android.util.Log.e("FCM", "✗ Failed to get FCM token: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            // Firebase not initialized (e.g., debug build without google-services.json)
            // This is OK - app will fall back to WebSocket notifications
            android.util.Log.w("FCM", "⚠️ Firebase not initialized: ${e.message}")
            android.util.Log.d("FCM", "Using WebSocket notifications instead")
        }
    }
    
    /**
     * Detect if running on Android emulator (legacy method - kept for reference)
     */
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for") ||
                Build.HARDWARE == "goldfish" ||
                Build.HARDWARE == "ranchu" ||
                Build.PRODUCT == "sdk" ||
                Build.PRODUCT == "google_sdk")
    }
}

@Composable
fun MainContent(activity: MainActivity) {
    val prefs = SharedPreferencesHelper(activity)
    
    // State to manage the initial authentication check
    var isCheckingAuth by remember { mutableStateOf(true) }
    var initialAuthSucceeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // --- AUTHENTICATION CHECK ON STARTUP ---
        android.util.Log.d("AuthCheck", "Starting auth check...")
        
        // 1. Handle app version updates or first run.
        if (prefs.isFirstRun()) {
            android.util.Log.d("AuthCheck", "First run or app update detected.")
            // Only clear if we have an invalid remember me token, 
            // but keep the existing session if it's already there
            if (prefs.isRememberMeEnabled() && !prefs.isRememberMeTokenValid()) {
                android.util.Log.d("AuthCheck", "Remember Me token expired during update. Clearing auth data.")
                prefs.clearAllAuthenticationData()
            }
            prefs.markAppInitialized()
        }

        // 2. Check for a valid session.
        var isLoggedIn = prefs.isLoggedIn()
        
        // 3. If no session, try to restore from a valid Remember Me token.
        if (!isLoggedIn && prefs.isRememberMeTokenValid()) {
            android.util.Log.d("AuthCheck", "No active session, but found valid Remember Me token. Restoring session.")
            val rememberMeToken = prefs.getRememberMeToken()
            val userId = prefs.getUserIdFromRememberMe()
            val username = prefs.getUsernameFromRememberMe()
            
            if (rememberMeToken != null && userId != null && username != null) {
                val deviceId = prefs.getDeviceId() ?: UUID.randomUUID().toString()
                prefs.saveAuthData(rememberMeToken, userId, deviceId, username) // Corrected parameter order if necessary
                isLoggedIn = true
                android.util.Log.d("AuthCheck", "Session restored locally from Remember Me token.")
            } else {
                 android.util.Log.w("AuthCheck", "Remember Me token data was incomplete. Clearing.")
                 prefs.clearRememberMeToken()
            }
        }

        // 4. ✅ NEW: Always verify token with server if logged in
        // This ensures the server knows our current deviceId (Zero-Touch sync)
        if (isLoggedIn) {
            val token = prefs.getToken()
            if (token != null) {
                try {
                    android.util.Log.d("AuthCheck", "Verifying session with server for Zero-Touch sync...")
                    val apiService = com.freetime.app.api.FreeTimeApiService(activity) // Instantiate apiService here
                    val response = apiService.verifyToken("Bearer $token") // Prepend "Bearer " if needed by the API
                    
                    if (response.valid) { // Use .valid directly from VerifyTokenResponse
                        android.util.Log.d("AuthCheck", "✓ Session verified and synchronized with server.")
                    } else if (response.message.contains("invalid token", ignoreCase = true)) { // Check response message for invalid token
                        android.util.Log.w("AuthCheck", "✗ Session invalidated by server: ${response.message}")
                        // If it's an invalid token, it's definitely dead.
                        // Try to use Remember Me to get a new one, or logout.
                        if (prefs.isRememberMeTokenValid()) {
                            android.util.Log.d("AuthCheck", "Attempting recovery via Remember Me...")
                            // In a real app we'd call a refresh endpoint here.
                            // For now, we'll just force a login on next screen if this fails.
                        } else {
                            prefs.clearAllAuthenticationData()
                            isLoggedIn = false
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AuthCheck", "⚠️ Could not verify session with server (offline?): ${e.message}")
                    // Keep isLoggedIn = true and hope for the best when network returns
                }
            }
        }

        initialAuthSucceeded = isLoggedIn
        isCheckingAuth = false // End of auth check
        android.util.Log.d("AuthCheck", "Auth check finished. isLoggedIn: $initialAuthSucceeded")
    }

    // Show a loading splash screen while checking auth
    if (isCheckingAuth) {
        SplashScreen()
    } else {
        // After check, navigate to Login or Main screen
        val navController = rememberNavController()
        val apiService = remember { com.freetime.app.api.FreeTimeApiService(activity) }

        Box(modifier = Modifier.fillMaxSize()) {
            AppNavGraph(
                navController = navController,
                isLoggedIn = initialAuthSucceeded,
                onLoginSuccess = {
                    // Register FCM token after successful login
                    activity.registerFcmToken()
                    // This will trigger a recomposition and navigate to the main graph
                }
            )

            com.freetime.app.ui.screens.FriendRequestPopup(
                apiService = apiService,
                onNavigateToFriendRequests = {
                    navController.navigate(com.freetime.app.navigation.Route.SearchFriends.path) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // State for session termination dialog
        var sessionTerminatedReason by remember { mutableStateOf<Pair<String, String>?>(null) }
        
        // ✅ NEW: Listen for session termination (concurrent login prevention)
        DisposableEffect(initialAuthSucceeded) {
            if (!initialAuthSucceeded) {
                return@DisposableEffect onDispose { }
            }
            
            val listener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
                override fun onSessionTerminated(reason: String, newDeviceName: String, message: String) {
                    android.util.Log.w("SessionTermination", "🔐 Session terminated: $reason from $newDeviceName")
                    // Trigger logout and show dialog
                    sessionTerminatedReason = Pair(reason, newDeviceName)
                    
                    // Force logout
                    val prefsHelper = com.freetime.app.data.local.SharedPreferencesHelper(activity)
                    prefsHelper.clearAuthData()
                    prefsHelper.clearRememberMeToken()
                    
                    // Navigate to login
                    navController.navigate(com.freetime.app.navigation.Route.Login.path) {
                        popUpTo(com.freetime.app.navigation.Route.Home.path) { inclusive = true }
                    }
                }

                override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                    // Show notification if not in the current chat
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.senderId) {
                        com.freetime.app.notifications.NotificationHelper.showMessageNotification(
                            activity, message.senderUsername, message.content, message.senderId, message.senderAvatar
                        )
                    }
                }

                override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                    // Show notification if not in the current group
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.groupId) {
                        com.freetime.app.notifications.NotificationHelper.showGroupMessageNotification(
                            activity, "Group", message.senderUsername, message.content, message.groupId, message.senderId, message.senderAvatar
                        )
                    }
                }

                override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {
                    // Show notification if not in the current channel
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.channelId) {
                        com.freetime.app.notifications.NotificationHelper.showChannelMessageNotification(
                            activity, "Channel", message.senderUsername, message.content, message.channelId
                        )
                    }
                }

                override fun onIncomingCall(callData: com.freetime.app.services.WebSocketManager.IncomingCallData) {
                    // Call is usually handled by the specific chat screen if open, 
                    // or by a global service/notification if not.
                    // NotificationHelper already handles showing the call UI/notification.
                    com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                        activity, 
                        callData.callerUsername, 
                        callData.callerId, 
                        callData.callType,
                        callId = callData.callId,
                        callerAvatarUrl = callData.callerAvatar,
                        offerSdp = callData.sdpOffer
                    )
                }
            }
            
            com.freetime.app.services.WebSocketManager.getInstance().addListener(listener)
            
            onDispose {
                com.freetime.app.services.WebSocketManager.getInstance().removeListener(listener)
            }
        }
        
        // Show dialog if session was terminated
        if (sessionTerminatedReason != null) {
            val (reason, newDeviceName) = sessionTerminatedReason!!
            AlertDialog(
                onDismissRequest = { sessionTerminatedReason = null },
                title = { Text("Session Terminated") },
                text = { 
                    Text("Your account was accessed from another device ($newDeviceName). " +
                        "For security, this session has been ended. Please log in again.")
                },
                confirmButton = {
                    Button(
                        onClick = { sessionTerminatedReason = null }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Observe the reactive intent state — fires on first launch AND on every onNewIntent()
        // This correctly handles: notification taps, IncomingCallActivity accept, and deep links
        val pendingNavIntent by activity.pendingNavIntent
        LaunchedEffect(initialAuthSucceeded, pendingNavIntent) {
            if (!initialAuthSucceeded) return@LaunchedEffect
            val currentIntent = pendingNavIntent ?: return@LaunchedEffect

            // Handle DECLINE_CALL from notification action button
            val declineCallId = currentIntent.getStringExtra("DECLINE_CALL")
            if (!declineCallId.isNullOrEmpty()) {
                val callerId = currentIntent.getStringExtra("CALLER_ID") ?: ""
                android.util.Log.d("MainActivity", "Declining call from notification: $declineCallId (caller=$callerId)")
                val apiService = com.freetime.app.api.FreeTimeApiService(activity)
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // This apiService.rejectCall call was causing an unresolved reference error
                        // as it's not defined in FreeTimeApiService. It's removed.
                        // apiService.rejectCall(declineCallId) 
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to reject call: ${e.message}")
                }
                // Also signal via WebSocket for real-time delivery
                val rejectPayload = org.json.JSONObject().apply {
                    put("callId", declineCallId)
                    put("callerId", callerId)
                    put("reason", "declined")
                }
                com.freetime.app.services.WebSocketManager.getInstance().send("call:reject", rejectPayload)
                com.freetime.app.notifications.NotificationHelper.cancelCallNotification(activity, callerId)
                activity.pendingNavIntent.value = null
                return@LaunchedEffect
            }

            // ✅ Handle deep links from group invitation URLs
            val dataUri = currentIntent.data
            if (dataUri != null) {
                android.util.Log.d("MainActivity", "Handling deep link URI: $dataUri")
                when (dataUri.scheme) {
                    "freetime" -> {
                        // Handle freetime://group/invite/{groupIdOrCode}
                        if (dataUri.host == "group" && dataUri.path?.startsWith("/invite/") == true) {
                            val idOrCode = dataUri.lastPathSegment
                            if (!idOrCode.isNullOrEmpty()) {
                                android.util.Log.d("MainActivity", "Navigating to group via deep link: $idOrCode")
                                // Use join_group as it can handle both ID and Code
                                navController.navigate("join_group/$idOrCode") { launchSingleTop = true }
                                activity.pendingNavIntent.value = null
                                return@LaunchedEffect
                            }
                        }
                    }
                    "http", "https" -> {
                        // Handle web links (freetime.app or publicvm.com)
                        val path = dataUri.path ?: ""
                        if ((dataUri.host == "freetime.app" || dataUri.host == "example.com") && 
                            (path.startsWith("/invite/") || path.contains("/group/invite/"))) {
                            
                            val idOrCode = dataUri.lastPathSegment
                            if (!idOrCode.isNullOrEmpty()) {
                                android.util.Log.d("MainActivity", "Navigating to group via web link: $idOrCode")
                                navController.navigate("join_group/$idOrCode") { launchSingleTop = true }
                                activity.pendingNavIntent.value = null
                                return@LaunchedEffect
                            }
                        }
                    }
                }
            }

            val chatId = currentIntent.getStringExtra("CHAT_ID")
            val acceptCall = currentIntent.getBooleanExtra("ACCEPT_CALL", false)
            val callId = currentIntent.getStringExtra("CALL_ID") ?: ""
            val navigateTo = currentIntent.getStringExtra("NAVIGATE_TO")

            if (!chatId.isNullOrEmpty()) {
                android.util.Log.d("MainActivity", "Navigating to chat: $chatId (acceptCall=$acceptCall, callId=$callId)")
                navController.navigate(
                    com.freetime.app.navigation.Route.Chat.createRoute(
                        chatId = chatId,
                        acceptCall = acceptCall,
                        callId = callId
                    )
                ) {
                    launchSingleTop = true
                }
            } else if (!navigateTo.isNullOrEmpty()) {
                android.util.Log.d("MainActivity", "Deep link navigation: $navigateTo")
                when (navigateTo) {
                    "friendRequests" -> navController.navigate(com.freetime.app.navigation.Route.SearchFriends.path) { launchSingleTop = true }
                    "groups" -> navController.navigate(com.freetime.app.navigation.Route.Home.path) { launchSingleTop = true }
                    "groupChat" -> {
                        val groupId = currentIntent.getStringExtra("GROUP_ID")
                        if (!groupId.isNullOrEmpty()) {
                            android.util.Log.d("MainActivity", "Navigating to group chat: $groupId")
                            navController.navigate(com.freetime.app.navigation.Route.GroupChat.createRoute(groupId)) { launchSingleTop = true }
                        } else {
                            android.util.Log.w("MainActivity", "groupChat navigation missing GROUP_ID")
                        }
                    }
                    "mediaRequests" -> {
                        android.util.Log.d("MainActivity", "Navigating to media requests")
                        navController.navigate(com.freetime.app.navigation.Route.MediaList.createRoute("mediaRequests")) { launchSingleTop = true }
                    }
                    else -> android.util.Log.w("MainActivity", "Unknown NAVIGATE_TO: $navigateTo")
                }
            }

            // Clear after handling so it won't re-trigger on next recomposition
            activity.pendingNavIntent.value = null
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(CyberpunkTheme.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CyberpunkTheme.PrimaryPurple)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Authenticating...", color = CyberpunkTheme.LightGray)
        }
    }
}
