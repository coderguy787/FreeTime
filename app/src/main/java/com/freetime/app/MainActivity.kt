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
    internal val pendingNavIntent = androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingNavIntent.value = intent

        WindowCompat.setDecorFitsSystemWindows(window, false)

        configureApiClient()

        NotificationHelper.createNotificationChannels(this)

        com.freetime.app.notifications.InAppNotificationStore.init(this)

        registerFcmToken()

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

        com.freetime.app.services.MessageSyncWorker.schedulePeriodicSync(this)

        com.freetime.app.services.ServerStatusManager.start(this)
        com.freetime.app.services.OfflineMessageQueue.init(this)

        com.freetime.app.services.BackgroundPollingService.stopPolling(this)

        // block screenshots
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        this.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        lifecycleScope.launch {
            if (com.freetime.app.security.AntiTamperManager.isCompromised(this@MainActivity)) {
                com.freetime.app.security.AntiTamperManager.triggerBlockedScreen(this@MainActivity)
                return@launch
            }
        }
        lifecycleScope.launch {
            // periodic root check
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                try {
                    if (com.freetime.app.security.AntiTamperManager.isCompromised(this@MainActivity)) {
                        com.freetime.app.security.AntiTamperManager.triggerBlockedScreen(this@MainActivity)
                        break
                    }
                } catch (_: Exception) {}
            }
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

    private fun configureApiClient() {
        ApiClient.setBaseUrl(BuildConfig.API_BASE_URL)
        android.util.Log.d("MainActivity", "Using server: ${BuildConfig.API_BASE_URL}")
    }

    fun registerFcmToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (token.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                val apiService = com.freetime.app.api.FreeTimeApiService(this@MainActivity)

                                val result = apiService.registerDeviceFcmToken(token)
                                if (result.isSuccess) {
                                    android.util.Log.d("FCM", " Device FCM token registered in FCMTokens collection")
                                }

                                val prefs = com.freetime.app.data.local.SharedPreferencesHelper(this@MainActivity)
                                val userId = prefs.getUserId()
                                if (!userId.isNullOrEmpty()) {
                                    val userResult = apiService.registerFcmToken(userId, token)
                                    if (userResult.isSuccess) {
                                        android.util.Log.d("FCM", " FCM token registered in users collection for $userId")
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FCM", " FCM registration exception: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    android.util.Log.e("FCM", " Failed to get FCM token: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FCM", " Firebase not initialized: ${e.message}")
            android.util.Log.d("FCM", "Using WebSocket notifications instead")
        }
    }

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

    var isCheckingAuth by remember { mutableStateOf(true) }
    var initialAuthSucceeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        android.util.Log.d("AuthCheck", "Starting auth check...")

        if (prefs.isFirstRun()) {
            android.util.Log.d("AuthCheck", "First run or app update detected.")
            if (prefs.isRememberMeEnabled() && !prefs.isRememberMeTokenValid()) {
                android.util.Log.d("AuthCheck", "Remember Me token expired during update. Clearing auth data.")
                prefs.clearAllAuthenticationData()
            }
            prefs.markAppInitialized()
        }

        var isLoggedIn = prefs.isLoggedIn()

        if (!isLoggedIn && prefs.isRememberMeTokenValid()) {
            // auto-login with the saved token
            android.util.Log.d("AuthCheck", "No active session, but found valid Remember Me token. Restoring session.")
            val rememberMeToken = prefs.getRememberMeToken()
            val userId = prefs.getUserIdFromRememberMe()
            val username = prefs.getUsernameFromRememberMe()

            if (rememberMeToken != null && userId != null && username != null) {
                val deviceId = prefs.getDeviceId() ?: UUID.randomUUID().toString()
                prefs.saveAuthData(rememberMeToken, userId, deviceId, username)
                isLoggedIn = true
                android.util.Log.d("AuthCheck", "Session restored locally from Remember Me token.")
            } else {
                 android.util.Log.w("AuthCheck", "Remember Me token data was incomplete. Clearing.")
                 prefs.clearRememberMeToken()
            }
        }

        if (isLoggedIn) {
            val token = prefs.getToken()
            if (token != null) {
                try {
                    android.util.Log.d("AuthCheck", "Verifying session with server for Zero-Touch sync...")
                    val apiService = com.freetime.app.api.FreeTimeApiService(activity)
                    val response = apiService.verifyToken("Bearer $token")

                    if (response.valid) {
                        android.util.Log.d("AuthCheck", " Session verified and synchronized with server.")
                    } else if (response.message.contains("invalid token", ignoreCase = true)) {
                        android.util.Log.w("AuthCheck", " Session invalidated by server: ${response.message}")
                        if (prefs.isRememberMeTokenValid()) {
                            android.util.Log.d("AuthCheck", "Attempting recovery via Remember Me...")
                        } else {
                            prefs.clearAllAuthenticationData()
                            isLoggedIn = false
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AuthCheck", " Could not verify session with server (offline?): ${e.message}")
                }
            }
        }

        initialAuthSucceeded = isLoggedIn
        isCheckingAuth = false
        android.util.Log.d("AuthCheck", "Auth check finished. isLoggedIn: $initialAuthSucceeded")
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                com.freetime.app.services.UpdateGateManager.check(activity)
            } catch (e: Exception) {
                android.util.Log.w("UpdateGate", "Update check failed: ${e.message}")
            }
            kotlinx.coroutines.delay(60_000)
        }
    }

    if (isCheckingAuth) {
        SplashScreen()
    } else {
        val navController = rememberNavController()
        val apiService = remember { com.freetime.app.api.FreeTimeApiService(activity) }

        Box(modifier = Modifier.fillMaxSize()) {
            AppNavGraph(
                navController = navController,
                isLoggedIn = initialAuthSucceeded,
                onLoginSuccess = {
                    activity.registerFcmToken()
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

            val gateInfo = com.freetime.app.services.UpdateGateManager.gateInfo
            if (com.freetime.app.services.UpdateGateManager.showGate && gateInfo != null) {
                com.freetime.app.ui.screens.UpdateGateScreen(
                    info = gateInfo,
                    onDownload = {
                        val info = com.freetime.app.services.UpdateGateManager.gateInfo ?: return@UpdateGateScreen
                        com.freetime.app.services.UpdateGateManager.hide()
                        com.freetime.app.services.AppUpdateManager.downloadApk(activity, info) { id ->
                            if (id != -1L) {
                                com.freetime.app.services.AppUpdateManager.installApk(activity, id)
                            }
                        }
                    },
                    onSkip = {
                        com.freetime.app.services.UpdateGateManager.skip(activity)
                    }
                )
            }
        }

        var sessionTerminatedReason by remember { mutableStateOf<Pair<String, String>?>(null) }

        DisposableEffect(initialAuthSucceeded) {
            if (!initialAuthSucceeded) {
                return@DisposableEffect onDispose { }
            }

            val listener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
                override fun onSessionTerminated(reason: String, newDeviceName: String, message: String) {
                    android.util.Log.w("SessionTermination", " Session terminated: $reason from $newDeviceName")
                    sessionTerminatedReason = Pair(reason, newDeviceName)

                    val prefsHelper = com.freetime.app.data.local.SharedPreferencesHelper(activity)
                    prefsHelper.clearAuthData()
                    prefsHelper.clearRememberMeToken()

                    navController.navigate(com.freetime.app.navigation.Route.Login.path) {
                        popUpTo(com.freetime.app.navigation.Route.Home.path) { inclusive = true }
                    }
                }

                override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.senderId) {
                        com.freetime.app.notifications.NotificationHelper.showMessageNotification(
                            activity, message.senderUsername, message.content, message.senderId, message.senderAvatar
                        )
                    }
                }

                override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.groupId) {
                        com.freetime.app.notifications.NotificationHelper.showGroupMessageNotification(
                            activity, "Group", message.senderUsername, message.content, message.groupId, message.senderId, message.senderAvatar
                        )
                    }
                }

                override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {
                    if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId != message.channelId) {
                        com.freetime.app.notifications.NotificationHelper.showChannelMessageNotification(
                            activity, "Channel", message.senderUsername, message.content, message.channelId
                        )
                    }
                }

            }

            com.freetime.app.services.WebSocketManager.getInstance().addListener(listener)

            onDispose {
                com.freetime.app.services.WebSocketManager.getInstance().removeListener(listener)
            }
        }

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

        val pendingNavIntent by activity.pendingNavIntent
        LaunchedEffect(initialAuthSucceeded, pendingNavIntent) {
            if (!initialAuthSucceeded) return@LaunchedEffect
            val currentIntent = pendingNavIntent ?: return@LaunchedEffect

            val dataUri = currentIntent.data
            if (dataUri != null) {
                android.util.Log.d("MainActivity", "Handling deep link URI: $dataUri")
                when (dataUri.scheme) {
                    "freetime" -> {
                        if (dataUri.host == "group" && dataUri.path?.startsWith("/invite/") == true) {
                            val idOrCode = dataUri.lastPathSegment
                            if (!idOrCode.isNullOrEmpty()) {
                                android.util.Log.d("MainActivity", "Navigating to group via deep link: $idOrCode")
                                navController.navigate("join_group/$idOrCode") { launchSingleTop = true }
                                activity.pendingNavIntent.value = null
                                return@LaunchedEffect
                            }
                        }
                    }
                    "http", "https" -> {
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
            val navigateTo = currentIntent.getStringExtra("NAVIGATE_TO")

            if (!chatId.isNullOrEmpty()) {
                android.util.Log.d("MainActivity", "Navigating to chat: $chatId")
                navController.navigate(
                    com.freetime.app.navigation.Route.Chat.createRoute(chatId = chatId)
                ) {
                    launchSingleTop = true
                }
            } else if (!navigateTo.isNullOrEmpty()) {
                android.util.Log.d("MainActivity", "Deep link navigation: $navigateTo")
                when (navigateTo) {
                    "friendRequests" -> navController.navigate(com.freetime.app.navigation.Route.SearchFriends.path) { launchSingleTop = true }
                    "publicProfile" -> {
                        val profileUserId = currentIntent.getStringExtra("PROFILE_USER_ID")
                        if (!profileUserId.isNullOrEmpty()) {
                            navController.navigate(com.freetime.app.navigation.Route.UserProfile.createRoute(profileUserId)) { launchSingleTop = true }
                        } else {
                            navController.navigate(com.freetime.app.navigation.Route.Home.path) { launchSingleTop = true }
                        }
                    }
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
