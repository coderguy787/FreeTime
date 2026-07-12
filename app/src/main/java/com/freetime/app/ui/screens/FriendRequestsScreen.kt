package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import kotlinx.coroutines.launch

// ✅ NEW: Helper function to get username color based on tags and role
fun getUsernameColorForFriendRequests(
    tags: List<String>,
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    role: String? = null
): Color {
    return when {
        // Priority 1: OWNER tag → Bright Magenta
        tags.contains("OWNER") -> Color(0xFFFF00FF)
        // Priority 2: VIP tag → Yellow
        tags.contains("VIP") -> Color(0xFFFFFF00)
        // Priority 3: BETA TESTER tag → Cyan
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
        // Priority 4: isAdmin or role=="admin" → Red
        isAdmin || role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)
        // Priority 5: isModerator or role=="moderator" → Orange
        isModerator || role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)
        // Default → White
        else -> CyberpunkTheme.White
    }
}

@Composable
fun FriendRequestsScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = FreeTimeApiService(context)
    val prefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    
    var pendingRequests by remember { mutableStateOf(listOf<com.freetime.app.api.FriendRequest>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var processedRequestIds by remember { mutableStateOf(setOf<String>()) }
    
    // Notification settings
    var notifyFriendRequests by remember { mutableStateOf(prefs.isNotifyFriendRequestsEnabled()) }
    var notifySound by remember { mutableStateOf(prefs.isNotifySoundEnabled()) }
    
    android.util.Log.d("FREETIME_REQUESTS", "FriendRequestsScreen: Loading pending requests")
    
    // Load pending friend requests from API
    LaunchedEffect(Unit) {
        // Dismiss in-app friend request notifications when opening this screen
        com.freetime.app.notifications.InAppNotificationStore.removeByType("friendRequest")
        
        isLoading = true
        try {
            val result = apiService.getPendingRequests()
            result.onSuccess { requests ->
                android.util.Log.d("FREETIME_REQUESTS", "FriendRequestsScreen: Loaded ${requests.size} requests")
                pendingRequests = requests
                errorMessage = ""
            }
            result.onFailure { error ->
                android.util.Log.e("FREETIME_REQUESTS", "FriendRequestsScreen: Error: ${error.message}")
                errorMessage = "Failed to load requests: ${error.message ?: "Unknown error"}"
                pendingRequests = emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_REQUESTS", "FriendRequestsScreen: Exception: ${e.message}")
            errorMessage = "Error: ${e.message ?: "Unknown error"}"
            pendingRequests = emptyList()
        } finally {
            isLoading = false
        }
    }

    // ✅ MUTUAL FRIEND AUTO-ACCEPT: Listen for auto-accepted requests and remove them from pending list
    DisposableEffect(Unit) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        val listener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onFriendRequestAutoAccepted(data: com.freetime.app.services.WebSocketManager.FriendRequestAutoAcceptedData) {
                android.util.Log.d("FREETIME_REQUESTS", "WebSocket: Friend request auto-accepted from ${data.userId}")
                
                // Remove the sender's request from pending list (they auto-accepted our pending request to them)
                // If User B's pending request to User A is in this list, and User A sent request to B,
                // then it auto-accepts and we should remove B's pending request to A from the list
                pendingRequests = pendingRequests.filter { it.senderId != data.userId }
                
                if (pendingRequests.isEmpty()) {
                    errorMessage = "✅ No more pending requests!"
                }
            }
            
            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {}
            override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {}
            override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {}
            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {}
            override fun onMessageRead(readData: com.freetime.app.services.WebSocketManager.ReadReceiptData) {}
            override fun onConversationAllRead(readData: com.freetime.app.services.WebSocketManager.ConversationReadData) {}
            override fun onIncomingCall(callData: com.freetime.app.services.WebSocketManager.IncomingCallData) {
                com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                    context, 
                    callData.callerUsername, 
                    callData.callerId, 
                    callData.callType,
                    callId = callData.callId,
                    callerAvatarUrl = callData.callerAvatar,
                    offerSdp = callData.sdpOffer
                )
            }
            override fun onCallAnswered(callData: com.freetime.app.services.WebSocketManager.CallAnsweredData) {}
            override fun onCallRejected(callData: com.freetime.app.services.WebSocketManager.CallRejectedData) {}
            override fun onCallEnded(callData: com.freetime.app.services.WebSocketManager.CallEndedData) {}
            override fun onIceCandidate(iceData: com.freetime.app.services.WebSocketManager.IceCandidateData) {}
            override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {}
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onConnectionEstablished() {}
            override fun onConnectionLost() {}
            override fun onError(error: String) {}
        }
        
        wsManager.addListener(listener)
        
        // Clean up listener when screen is dismissed
        onDispose {
            wsManager.removeListener(listener)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberpunkTheme.Black)
                    .border(
                        width = 0.5.dp,
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    "Friend Requests",
                    color = CyberpunkTheme.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                if (pendingRequests.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)),
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                pendingRequests.size.toString(),
                                color = CyberpunkTheme.CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Notification Settings Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Notification Settings",
                        color = CyberpunkTheme.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Friend Request Notifications Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                notifyFriendRequests = !notifyFriendRequests
                                prefs.setNotifyFriendRequests(notifyFriendRequests)
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Friend Requests",
                                color = CyberpunkTheme.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Get notified when someone sends a friend request",
                                color = CyberpunkTheme.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = notifyFriendRequests,
                            onCheckedChange = {
                                notifyFriendRequests = it
                                prefs.setNotifyFriendRequests(it)
                            },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberpunkTheme.CyberCyan,
                                checkedTrackColor = CyberpunkTheme.CyberCyan.copy(alpha = 0.3f)
                            )
                        )
                    }
                    
                    Divider(color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f))
                    
                    // Notification Sound Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                notifySound = !notifySound
                                prefs.setNotifySound(notifySound)
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Notification Sound",
                                color = CyberpunkTheme.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Play sound for notifications",
                                color = CyberpunkTheme.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = notifySound,
                            onCheckedChange = {
                                notifySound = it
                                prefs.setNotifySound(it)
                            },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberpunkTheme.CyberCyan,
                                checkedTrackColor = CyberpunkTheme.CyberCyan.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
            
            // Error message
            if (errorMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5544).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            tint = Color(0xFFff4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            errorMessage,
                            color = Color(0xFFff4444),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CyberpunkTheme.CyberCyan
                    )
                }
            } else if (pendingRequests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            null,
                            tint = CyberpunkTheme.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No pending requests",
                            color = CyberpunkTheme.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "When friends send you requests,\nthey'll appear here",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingRequests.filter { it.requestId !in processedRequestIds }) { request ->
                        FriendRequestItemSimple(
                            request = request,
                            onAccept = {
                                scope.launch {
                                    try {
                                        android.util.Log.d("FREETIME_REQUESTS", "Accepting friend request from ${request.senderUsername} (senderId: ${request.senderId})")
                                        val result = apiService.acceptFriendRequest(request.senderId)
                                        result.onSuccess { message ->
                                            processedRequestIds = processedRequestIds + request.requestId
                                            errorMessage = ""
                                            android.util.Log.d("FREETIME_REQUESTS", "Request accepted successfully: $message")
                                        }
                                        result.onFailure { error ->
                                            errorMessage = "Failed to accept: ${error.message ?: "Unknown error"}"
                                            android.util.Log.e("FREETIME_REQUESTS", "Failed to accept request: ${error.message}")
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message ?: "Unknown error"}"
                                        android.util.Log.e("FREETIME_REQUESTS", "Exception accepting request", e)
                                    }
                                }
                            },
                            onDecline = {
                                scope.launch {
                                    try {
                                        android.util.Log.d("FREETIME_REQUESTS", "Declining friend request from ${request.senderUsername} (senderId: ${request.senderId})")
                                        val result = apiService.declineFriendRequest(request.senderId)
                                        result.onSuccess { message ->
                                            processedRequestIds = processedRequestIds + request.requestId
                                            errorMessage = ""
                                            android.util.Log.d("FREETIME_REQUESTS", "Request declined successfully: $message")
                                        }
                                        result.onFailure { error ->
                                            errorMessage = "Failed to decline: ${error.message ?: "Unknown error"}"
                                            android.util.Log.e("FREETIME_REQUESTS", "Failed to decline request: ${error.message}")
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message ?: "Unknown error"}"
                                        android.util.Log.e("FREETIME_REQUESTS", "Exception declining request", e)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendRequestItemSimple(
    request: com.freetime.app.api.FriendRequest,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - User info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        request.senderUsername.firstOrNull()?.uppercase() ?: "?",
                        color = CyberpunkTheme.CyberCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Name and username
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        request.senderUsername,
                        color = getUsernameColorForFriendRequests(request.senderTags, request.senderIsAdmin, request.senderIsModerator, request.senderRole),  // ✅ NEW: Apply color
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "Wants to be friends",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            
            // Right side - Accept and Decline buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline button
                Button(
                    onClick = onDecline,
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B4040).copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        "Decline",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.White
                    )
                }
                
                // Accept button
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .height(36.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.PrimaryPurple
                    )
                ) {
                    Text(
                        "Accept",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberpunkTheme.White
                    )
                }
            }
        }
    }
}
