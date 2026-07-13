package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.R
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.network.ApiClient  // ✅ FIX: Import for getBaseUrl()
import com.freetime.app.api.UserData
import com.freetime.app.api.FriendRequest
import com.freetime.app.api.Group
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.services.WebSocketManager
import com.freetime.app.services.BackgroundPollingService
import com.freetime.app.services.ConnectionState
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freetime.app.notifications.InAppNotificationStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.freetime.app.utils.GroupRefreshManager  // ✅ NEW: Import global refresh manager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ChatItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val isUnread: Boolean = false,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
    val isOnline: Boolean = false,
    val avatarColor: Color = Color.Gray,
    val avatarUrl: String? = null,
    val tags: List<String> = emptyList(),
    val role: String = "",  // ✅ NEW: User role (admin, moderator, etc.)
    val isAdmin: Boolean = false,  // ✅ NEW: Admin status
    val isModerator: Boolean = false,  // ✅ NEW: Moderator status
    val lastMessageTimestamp: Long = 0L  // ✅ NEW: Epoch millis for time-based filtering
)

/**
 * MODERN DISCORD-LIKE HOME SCREEN
 * Features:
 * - Chat list similar to Discord
 * - Mascot character showing contextual messages
 * - Smooth animations and transitions
 * - Online status indicators
 * - Typing indicators
 * - Unread message badges
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ModernHomeScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearchFriends: () -> Unit,
    onNavigateToFriendRequests: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToCreateChannel: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = SharedPreferencesHelper(context)
    var showApiResponseDialog by remember { mutableStateOf(false) }
    val savedFriendsApiResponse = remember { mutableStateOf(prefs.getString("last_api_friends_response", "")) }
    val apiService = FreeTimeApiService(context)
    
    var selectedChat by remember { mutableStateOf<String?>(null) }
    var mascotShowMessage by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf(listOf<ChatItem>()) }
    var isLoadingChats by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf("") }
    var pendingFriendRequestCount by remember { mutableStateOf(0) }
    val notifiedRequestIds = remember { mutableSetOf<String>() }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var incomingMessages by remember { mutableStateOf(listOf<ChatItem>()) }
    var userGroups by remember { mutableStateOf(listOf<Group>()) }
    var isLoadingGroups by remember { mutableStateOf(false) }
    var groupsRefreshKey by remember { mutableStateOf(0) }  // ✅ FIX: Trigger to reload groups
    
    // In-app update
    var updateInfo by remember { mutableStateOf<com.freetime.app.data.network.VersionInfoResponse?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf(-1L) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updatePrefs = remember { com.freetime.app.data.local.SharedPreferencesHelper(context) }
    
    LaunchedEffect(Unit) {
        val info = com.freetime.app.services.AppUpdateManager.checkForUpdate(context)
        if (info != null && com.freetime.app.services.AppUpdateManager.isUpdateAvailable(info)) {
            if (info.latestVersionCode > prefs.getSkippedVersion()) {
                updateInfo = info
            }
        }
    }
    
    // ✅ OBSERVE CONNECTION STATE from Socket.IO manager
    val wsManager = remember { WebSocketManager.getInstance() }
    var connectionState by remember { mutableStateOf(wsManager.getConnectionState()) }
    
    // Observe connection state changes
    LaunchedEffect(wsManager) {
        wsManager.connectionState.collect { newState ->
            connectionState = newState
        }
    }
    
    // Track initialization to prevent excessive recomposition logging
    val initCalledRef = remember { mutableStateOf(false) }
    if (!initCalledRef.value) {
        initCalledRef.value = true
        android.util.Log.d("FREETIME_HOME", "ModernHomeScreen: Initializing home screen")
    }
    
    // Request POST_NOTIFICATIONS permission on Android 13+ (required for notifications)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("FREETIME_HOME", "POST_NOTIFICATIONS permission: ${if (isGranted) "GRANTED" else "DENIED"}")
    }
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showApiResponseDialog) {
        AlertDialog(
            onDismissRequest = { showApiResponseDialog = false },
            title = { Text("Raw Friends API Response") },
            text = {
                val body = prefs.getString("last_api_friends_response", "(no saved response)") ?: "(no saved response)"
                Column {
                    Text(body, color = CyberpunkTheme.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Saved: ${prefs.getLong("last_api_friends_response_ts", 0)}", color = CyberpunkTheme.GhostGray, fontSize = 10.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showApiResponseDialog = false }) { Text("Close") }
            },
            containerColor = Color(0xFF0D0D1A)
        )
    }

    // Connect WebSocket for real-time notifications + register FCM token
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                apiService.connectWebSocket()
                android.util.Log.d("FREETIME_HOME", "✓ WebSocket connected for real-time notifications")
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_HOME", "WebSocket connection failed: ${e.message}")
            }
        }
        
        // ✅ NEW: Start background polling service for offline notifications (0-1 min delay)
        try {
            BackgroundPollingService.startPolling(context)
            android.util.Log.d("FREETIME_HOME", "✅ Background polling started (max 1 min offline notification delay, NO Firebase needed!)")
        } catch (e: Exception) {
            android.util.Log.w("FREETIME_HOME", "⚠️ Could not start background polling: ${e.message}")
        }
    }

    // Global WebSocket listener for notifications (fires when user is on home screen, not in chat)
    val globalNotificationListener = remember {
        object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                android.util.Log.d("FREETIME_HOME", "📨 New message notification from ${message.senderId}")
                
                // Check if message notifications are enabled
                if (!prefs.isNotifyMessagesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Message notifications disabled - skipping")
                    // Still update unread count, just skip notification
                } else {
                    val senderChat = chats.find { it.id == message.senderId }
                    val senderName = senderChat?.name ?: message.senderUsername
                    com.freetime.app.notifications.NotificationHelper.showMessageNotification(
                        context, senderName, message.content, message.senderId
                    )
                    
                    // Add to in-app notification center
                    com.freetime.app.notifications.InAppNotificationStore.addNotification(
                        com.freetime.app.notifications.InAppNotification(
                            type = "message",
                            title = senderName,
                            description = message.content,
                            senderId = message.senderId
                        )
                    )
                }

                // Update unread count in chat list
                val senderExists = chats.any { it.id == message.senderId }
                if (!senderExists) {
                    val displayName = if (message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID) {
                        com.freetime.app.api.ANNOUNCEMENT_DISPLAY_NAME
                    } else {
                        message.senderDisplayName ?: message.senderUsername
                    }
                    val announcementItem = ChatItem(
                        id = message.senderId,
                        name = displayName,
                        lastMessage = message.content,
                        timestamp = "Now",
                        isUnread = true,
                        unreadCount = 1,
                        isOnline = message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID,
                        avatarColor = Color(0xFFFFAA00),
                        avatarUrl = null,
                        tags = if (message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID) listOf("ADMIN", "ANNOUNCEMENT") else emptyList(),
                        role = if (message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID) "ADMIN" else "",
                        isAdmin = message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID,
                        isModerator = false,
                        lastMessageTimestamp = message.createdAt
                    )
                    chats = listOf(announcementItem) + chats
                } else {
                    chats = chats.map { chat ->
                        if (chat.id == message.senderId) {
                            chat.copy(
                                isUnread = true,
                                unreadCount = chat.unreadCount + 1,
                                lastMessage = message.content,
                                timestamp = "Now",
                                lastMessageTimestamp = message.createdAt
                            )
                        } else chat
                    }
                }
            }
            override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                android.util.Log.d("FREETIME_HOME", "📨 Group message from ${message.senderUsername} in group ${message.groupId}")
                
                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }
                
                // Skip notification if user is currently viewing this group (Telegram-style)
                if (com.freetime.app.notifications.NotificationHelper.currentActiveChatId == message.groupId) {
                    android.util.Log.d("FREETIME_HOME", "⏭️ Skipping notification - user viewing this group")
                    return
                }
                
                // Get group name from chats list
                val groupChat = chats.find { it.id == message.groupId }
                val groupName = groupChat?.name ?: "Group"
                
                // Show notification
                com.freetime.app.notifications.NotificationHelper.showGroupMessageNotification(
                    context, groupName, message.senderUsername, message.content, message.groupId, message.senderId
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMessage",
                        title = "${message.senderUsername} in $groupName",
                        description = message.content,
                        senderId = message.senderId
                    )
                )
                
                // Update group chat in list with latest message
                chats = chats.map { chat ->
                    if (chat.id == message.groupId) {
                        chat.copy(
                            isUnread = true,
                            unreadCount = chat.unreadCount + 1,
                            lastMessage = "${message.senderUsername}: ${message.content}",
                            timestamp = "Now"
                        )
                    } else chat
                }
            }
            
            // ✅ NEW: Handle group member joined events
            override fun onGroupMemberJoined(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) {
                android.util.Log.d("FREETIME_HOME", "👤 ${data.username} joined group ${data.groupId}")
                
                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }
                
                val groupChat = chats.find { it.id == data.groupId }
                val groupName = groupChat?.name ?: "Group"
                
                // Show notification
                com.freetime.app.notifications.NotificationHelper.showGroupMemberActionNotification(
                    context, groupName, data.username, "joined", data.groupId
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMemberJoined",
                        title = groupName,
                        description = "${data.username} joined",
                        senderId = data.userId
                    )
                )
            }
            
            // ✅ NEW: Handle group member left events
            override fun onGroupMemberLeft(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) {
                android.util.Log.d("FREETIME_HOME", "👤 ${data.username} left group ${data.groupId}")
                
                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }
                
                val groupChat = chats.find { it.id == data.groupId }
                val groupName = groupChat?.name ?: "Group"
                
                // Show notification
                com.freetime.app.notifications.NotificationHelper.showGroupMemberActionNotification(
                    context, groupName, data.username, "left", data.groupId
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMemberLeft",
                        title = groupName,
                        description = "${data.username} left",
                        senderId = data.userId
                    )
                )
            }
            
            // ✅ NEW: Handle group invitation received events
            override fun onGroupInviteReceived(data: com.freetime.app.services.WebSocketManager.GroupInviteData) {
                android.util.Log.d("FREETIME_HOME", "📨 Group invite: ${data.inviterName} invited you to ${data.groupName}")

                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }

                // Show system push notification
                com.freetime.app.notifications.NotificationHelper.showGroupInviteNotification(
                    context, data.groupName, data.inviterName, data.groupId
                )

                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupInvite",
                        title = data.groupName,
                        description = "${data.inviterName} invited you to join",
                        senderId = data.groupId,
                        inviteId = data.inviteId
                    )
                )
            }

            // ✅ NEW: Handle group member promoted events
            override fun onGroupMemberPromoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) {
                android.util.Log.d("FREETIME_HOME", "⭐ ${data.username} promoted to admin in group ${data.groupId}")
                
                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }
                
                val groupChat = chats.find { it.id == data.groupId }
                val groupName = groupChat?.name ?: "Group"
                
                // Show notification
                com.freetime.app.notifications.NotificationHelper.showGroupMemberActionNotification(
                    context, groupName, data.username, "promoted", data.groupId
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMemberPromoted",
                        title = groupName,
                        description = "${data.username} is now an admin",
                        senderId = data.userId
                    )
                )
            }
            
            // ✅ NEW: Handle group member removed events
            override fun onGroupMemberRemoved(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) {
                android.util.Log.d("FREETIME_HOME", "🚫 ${data.username} removed from group ${data.groupId}")
                
                // Check if group notifications are enabled
                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    android.util.Log.d("FREETIME_HOME", "⏸️ Group notifications disabled - skipping")
                    return
                }
                
                val groupChat = chats.find { it.id == data.groupId }
                val groupName = groupChat?.name ?: "Group"
                
                // Show notification
                com.freetime.app.notifications.NotificationHelper.showGroupMemberActionNotification(
                    context, groupName, data.username, "removed", data.groupId
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMemberRemoved",
                        title = groupName,
                        description = "${data.username} was removed",
                        senderId = data.userId
                    )
                )
            }
            
            override fun onGroupMemberDemoted(data: com.freetime.app.services.WebSocketManager.GroupMemberActionData) {
                android.util.Log.d("FREETIME_HOME", "⬇️ ${data.username} demoted in group ${data.groupId}")
                if (!prefs.isNotifyGroupUpdatesEnabled()) return
                val groupChat = chats.find { it.id == data.groupId }
                val groupName = groupChat?.name ?: "Group"
                com.freetime.app.notifications.NotificationHelper.showGroupMemberActionNotification(
                    context, groupName, data.username, "demoted", data.groupId
                )
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "groupMemberDemoted",
                        title = groupName,
                        description = "${data.username} is no longer an admin",
                        senderId = data.userId
                    )
                )
            }

            override fun onGroupHistoryCleared(data: com.freetime.app.services.WebSocketManager.GroupHistoryClearedData) {}
            
            override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {}
            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {}
            override fun onMessageRead(readData: com.freetime.app.services.WebSocketManager.ReadReceiptData) {}
            override fun onConversationAllRead(readData: com.freetime.app.services.WebSocketManager.ConversationReadData) {}
            override fun onIncomingCall(callData: com.freetime.app.services.WebSocketManager.IncomingCallData) {
                android.util.Log.d("FREETIME_HOME", "📞 Incoming call from ${callData.callerUsername}")
                com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                    context, 
                    callData.callerUsername, 
                    callData.callerId, 
                    callData.callType,
                    callId = callData.callId,
                    callerAvatarUrl = callData.callerAvatar,
                    offerSdp = callData.sdpOffer
                )
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "call",
                        title = callData.callerUsername,
                        description = "Incoming ${callData.callType} call",
                        senderId = callData.callerId
                    )
                )
            }
            override fun onCallAnswered(callData: com.freetime.app.services.WebSocketManager.CallAnsweredData) {}
            override fun onCallRejected(callData: com.freetime.app.services.WebSocketManager.CallRejectedData) {}
            override fun onCallEnded(callData: com.freetime.app.services.WebSocketManager.CallEndedData) {}
            override fun onIceCandidate(iceData: com.freetime.app.services.WebSocketManager.IceCandidateData) {}
            override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {}
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {}
            override fun onFriendRequestAutoAccepted(data: com.freetime.app.services.WebSocketManager.FriendRequestAutoAcceptedData) {
                // Refresh pending friend request count when mutual friend request auto-accepts
                android.util.Log.d("FREETIME_HOME", "🤝 Friend request auto-accepted from ${data.userId}, refreshing badge")
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "friendAccepted",
                        title = data.username,
                        description = "${data.username} accepted your request!",
                        senderId = data.userId
                    )
                )

                scope.launch {
                    try {
                        val result = apiService.getPendingRequests()
                        result.onSuccess { requests ->
                            pendingFriendRequestCount = requests.size
                            android.util.Log.d("FREETIME_HOME", "✓ Badge updated: ${requests.size} pending requests")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_HOME", "Error refreshing pending requests: ${e.message}")
                    }
                }
            }
            override fun onFriendAdded(data: com.freetime.app.services.WebSocketManager.FriendAddedData) {
                // Real-time friend list sync - friend was added
                android.util.Log.d("FREETIME_HOME", "👥 New friend added: ${data.username} (${data.userId})")
                // The actual friend list will be synced when SearchFriendsScreen receives this event
            }
            override fun onFriendRemoved(data: com.freetime.app.services.WebSocketManager.FriendRemovedData) {
                // Real-time friend list sync - friend was removed
                android.util.Log.d("FREETIME_HOME", "👥 Friend removed: ${data.removedFriendId}")
                // The actual friend list will be synced when SearchFriendsScreen receives this event
            }
            override fun onUserProfileUpdated(data: com.freetime.app.services.WebSocketManager.UserProfileUpdatedData) {
                android.util.Log.d("FREETIME_HOME", "👤 Profile updated for friend: ${data.userId}")
                // Update the chat list in real-time
                chats = chats.map { chat ->
                    if (chat.id == data.userId) {
                        chat.copy(
                            avatarUrl = data.avatar ?: data.profileImageUrl ?: chat.avatarUrl,
                            // If status is provided, update timestamp if online
                            timestamp = if (data.status != null && chat.isOnline) "Online - ${data.status}" else chat.timestamp
                        )
                    } else chat
                }
            }

            override fun onNotificationReceived(data: com.freetime.app.services.WebSocketManager.InternalNotificationData) {
                android.util.Log.d("FREETIME_HOME", "🔔 Internal notification received: ${data.title}")
                
                // 1. Show system notification (fallback for FCM)
                com.freetime.app.notifications.NotificationHelper.showInternalNotification(context, data)
                
                // 2. Add to in-app notification center
                val senderId = data.data.optString("senderId", "")
                val type = data.data.optString("type", "notification")
                
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = type,
                        title = data.title,
                        description = data.body,
                        senderId = senderId
                    )
                )
                
                // 3. Special handling for calls received via WebSocket fallback
                if (type == "call" || type == "incoming_call") {
                    val callerName = data.data.optString("callerName", data.title)
                    val callerId = data.data.optString("callerId", senderId)
                    val callType = data.data.optString("callType", "audio")
                    val callId = data.data.optString("callId", "")
                    
                    if (callerId.isNotEmpty()) {
                        com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                            context, 
                            callerName, 
                            callerId, 
                            callType, 
                            callId = callId,
                            offerSdp = data.data.optString("offer", data.data.optString("sdp", ""))
                        )
                    }
                }
            }
            
            // ✅ CRITICAL FIX: Handle incoming friend requests
            override fun onFriendRequestReceived(data: com.freetime.app.services.WebSocketManager.FriendRequestEventData) {
                android.util.Log.d("FREETIME_HOME", "🤝 Friend request received from ${data.senderName}")
                
                // Check if friend request notifications are enabled
                if (prefs.isNotifyFriendRequestsEnabled()) {
                    com.freetime.app.notifications.NotificationHelper.showFriendRequestNotification(
                        context, data.senderName, data.senderId, data.requestId
                    )
                }
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "friendRequest",
                        title = data.senderName,
                        description = "wants to be your friend",
                        senderId = data.senderId
                    )
                )
                
                // Update pending request count badge
                scope.launch {
                    try {
                        val result = apiService.getPendingRequests()
                        result.onSuccess { requests ->
                            pendingFriendRequestCount = requests.size
                            android.util.Log.d("FREETIME_HOME", "✓ Pending requests badge updated: ${requests.size}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_HOME", "Error refreshing pending requests: ${e.message}")
                    }
                }
            }
            
            // ✅ CRITICAL FIX: Handle friend request acceptance (non-auto)
            override fun onFriendRequestAccepted(data: com.freetime.app.services.WebSocketManager.FriendRequestEventData) {
                android.util.Log.d("FREETIME_HOME", "🤝 Friend request accepted by ${data.senderName}")
                
                // Check if friend request notifications are enabled
                if (prefs.isNotifyFriendRequestsEnabled()) {
                    com.freetime.app.notifications.NotificationHelper.showFriendRequestAcceptedNotification(
                        context, data.senderName, data.senderId
                    )
                }
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "friendAccepted",
                        title = data.senderName,
                        description = "accepted your friend request!",
                        senderId = data.senderId
                    )
                )
            }
            
            // ✅ CRITICAL FIX: Handle friend request rejection
            override fun onFriendRequestRejected(data: com.freetime.app.services.WebSocketManager.FriendRequestEventData) {
                android.util.Log.d("FREETIME_HOME", "❌ Friend request rejected by ${data.senderName}")
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "friendRejected",
                        title = data.senderName,
                        description = "declined your friend request",
                        senderId = data.senderId
                    )
                )
            }
            
            // ✅ CRITICAL FIX: Handle friend request cancellation
            override fun onFriendRequestCanceled(data: com.freetime.app.services.WebSocketManager.FriendRequestEventData) {
                android.util.Log.d("FREETIME_HOME", "🚫 Friend request canceled by ${data.senderName}")
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "friendCanceled",
                        title = data.senderName,
                        description = "canceled their friend request",
                        senderId = data.senderId
                    )
                )
                
                // Update pending request count badge
                scope.launch {
                    try {
                        val result = apiService.getPendingRequests()
                        result.onSuccess { requests ->
                            pendingFriendRequestCount = requests.size
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_HOME", "Error refreshing pending requests: ${e.message}")
                    }
                }
            }
            
            // ✅ CRITICAL FIX: Handle media download requests
            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                android.util.Log.d("FREETIME_HOME", "🔔 RECEIVED onMediaDownloadRequested: mediaId=${data.mediaId}, requester=${data.requesterName}, requestId=${data.requestId}")
                
                // Add to in-app notification center
                com.freetime.app.notifications.InAppNotificationStore.addNotification(
                    com.freetime.app.notifications.InAppNotification(
                        type = "downloadRequest",
                        title = data.requesterName,
                        description = "requested to download your media",
                        senderId = data.requesterId
                    )
                )
            }
            
            override fun onConnectionEstablished() {
                android.util.Log.d("FREETIME_HOME", "✓ WebSocket connected for notifications")
            }
            override fun onConnectionLost() {
                android.util.Log.d("FREETIME_HOME", "❌ WebSocket disconnected")
            }
            override fun onError(error: String) {
                android.util.Log.e("FREETIME_HOME", "WebSocket error: $error")
            }
        }
    }

    // ✅ CRITICAL FIX: Register listener BEFORE connecting to prevent race condition
    // where connection completes and events fire before listener is attached
    LaunchedEffect(globalNotificationListener) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        // Add listener first, THEN connect
        wsManager.addListener(globalNotificationListener)
        android.util.Log.d("FREETIME_HOME", "✅ WebSocket listener registered BEFORE connection")
    }
    
    DisposableEffect(Unit) {
        onDispose {
            val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
            wsManager.removeListener(globalNotificationListener)
            android.util.Log.d("FREETIME_HOME", "WebSocket listener removed")
        }
    }

    // Fetch friend request count
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = apiService.getPendingRequests()
                result.onSuccess { requests ->
                    pendingFriendRequestCount = requests.size
                    android.util.Log.d("FREETIME_HOME", "✓ Pending friend requests: ${requests.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_HOME", "Error fetching pending requests: ${e.message}")
            }
        }
    }

    // ✅ NEW: Watch global refresh manager
    val globalRefreshKey = GroupRefreshManager.refreshTrigger.value
    
    // Load user groups
    LaunchedEffect(groupsRefreshKey, globalRefreshKey) {  // ✅ FIX: Reload when manual refresh OR global refresh triggered
        isLoadingGroups = true
        try {
            // 🔄 Log refresh event
            if (globalRefreshKey > 0) {
                android.util.Log.d("FREETIME_HOME", "🔄 Refresh triggered by global signal (key=$globalRefreshKey), clearing old groups...")
                userGroups = emptyList()  // ✅ IMPROVED: Clear groups immediately to show loading state
            }
            
            val result = apiService.getUserGroups()
            result.onSuccess { groups ->
                // ✅ CRITICAL FIX: Construct picture URLs for groups that don't have them
                val enrichedGroups = groups.map { group ->
                    if (group.profilePictureUrl.isNullOrEmpty() || group.profilePictureUrl == "undefined") {
                        // Construct the picture URL if missing
                        group.copy(
                            profilePictureUrl = "${ApiClient.getBaseUrl().trimEnd('/')}/api/groups/${group.groupId}/picture"
                        )
                    } else {
                        group
                    }
                }
                
                // ✅ IMPROVED: Log group changes
                android.util.Log.d("FREETIME_HOME", "✓ Loaded ${enrichedGroups.size} groups (was ${userGroups.size})")
                val oldGroupIds = userGroups.map { it.groupId }.toSet()
                val newGroupIds = enrichedGroups.map { it.groupId }.toSet()
                val removedGroups = oldGroupIds - newGroupIds
                val addedGroups = newGroupIds - oldGroupIds
                
                if (removedGroups.isNotEmpty()) {
                    android.util.Log.d("FREETIME_HOME", "  ❌ Removed: ${removedGroups.size} group(s)")
                    removedGroups.forEach { groupId ->
                        android.util.Log.d("FREETIME_HOME", "    - Removed: $groupId")
                    }
                }
                if (addedGroups.isNotEmpty()) {
                    android.util.Log.d("FREETIME_HOME", "  ✅ Added: ${addedGroups.size} group(s)")
                }
                
                userGroups = enrichedGroups
                
                enrichedGroups.forEach { group ->
                    android.util.Log.d("FREETIME_HOME", "  📁 Group: ${group.name}")
                    android.util.Log.d("FREETIME_HOME", "    - PFP URL: ${group.profilePictureUrl}")
                    android.util.Log.d("FREETIME_HOME", "    - Avatar: ${group.avatar ?: "NULL"}")
                    android.util.Log.d("FREETIME_HOME", "    - InviteCode: ${group.inviteCode ?: "NULL"}")
                    android.util.Log.d("FREETIME_HOME", "    - InviteLink: ${group.inviteLink ?: "NULL"}")
                }
            }.onFailure { error ->
                android.util.Log.e("FREETIME_HOME", "❌ Error loading groups: ${error.message}")
                // ✅ NEW: Auto-logout on 401 Unauthorized
                if (error.message?.contains("401") == true) {
                    android.util.Log.e("FREETIME_HOME", "401 Unauthorized - clearing auth data")
                    prefs.clearAllAuthenticationData()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_HOME", "❌ Exception loading groups: ${e.message}", e)
        } finally {
            isLoadingGroups = false
        }
    }

    LaunchedEffect(Unit) {
        isLoadingChats = true
        try {
            // ✅ OFFLINE-FIRST: Load cached friends immediately
            val cachedFriendsJson = prefs.getCachedFriends()
            if (!cachedFriendsJson.isNullOrEmpty()) {
                try {
                    val cachedFriends = org.json.JSONArray(cachedFriendsJson)
                    val friendsList = mutableListOf<UserData>()
                    for (i in 0 until cachedFriends.length()) {
                        val obj = cachedFriends.getJSONObject(i)
                        friendsList.add(UserData(
                            userId = obj.optString("userId", ""),
                            username = obj.optString("username", ""),
                            name = obj.optString("name", ""),
                            email = obj.optString("email", ""),
                            avatar = obj.optString("avatar", ""),
                            bio = obj.optString("bio", ""),
                            isOnline = obj.optBoolean("isOnline", false),
                            lastSeen = obj.optString("lastSeen", null),
                            tags = obj.optJSONArray("tags")?.let { arr ->
                                val tagList: MutableList<String> = mutableListOf()
                                for (j in 0 until arr.length()) tagList.add(arr.getString(j))
                                tagList.toList() as List<String>
                            } ?: emptyList<String>(),
                            role = obj.optString("role", null),  // ✅ NEW: Extract role from cache
                            isAdmin = obj.optBoolean("isAdmin", false),  // ✅ NEW: Extract isAdmin from cache
                            isModerator = obj.optBoolean("isModerator", false)  // ✅ NEW: Extract isModerator from cache
                        ))
                    }
                    
                    chats = friendsList.mapIndexed { index, friend ->
                        ChatItem(
                            id = friend.userId,
                            name = friend.name.ifEmpty { friend.username },
                            lastMessage = "Tap to start chatting...",
                            timestamp = if (friend.isOnline) "Online now" else formatLastSeen(friend.lastSeen),
                            isUnread = false,
                            unreadCount = 0,
                            isTyping = false,
                            isOnline = friend.isOnline,
                            avatarColor = listOf(
                                Color(0xFFFF1493), Color(0xFF00FFFF), Color(0xFF9D4EDD),
                                Color(0xFF3A86FF), Color(0xFFFF7A00), Color(0xFF00FF00)
                            )[index % 6],
                            avatarUrl = apiService.resolveAvatarUrl(friend.avatar),
                            tags = friend.tags,
                            role = friend.role ?: "",  // ✅ NEW: Add role
                            isAdmin = friend.isAdmin,  // ✅ NEW: Add isAdmin
                            isModerator = friend.isModerator  // ✅ NEW: Add isModerator
                        )
                    }
                    android.util.Log.d("FREETIME_HOME", "📦 Loaded ${friendsList.size} friends from cache")
                } catch (e: Exception) {
                    android.util.Log.w("FREETIME_HOME", "Failed to parse cached friends: ${e.message}")
                }
            }
            
            // ✅ ATTEMPT API LOAD: Try to get fresh friends (even if cache was loaded)
            val result = apiService.getFriends()
            result.onSuccess { friends ->
                android.util.Log.d("FREETIME_HOME", "🔄 Updated with ${friends.size} friends from API")
                
                // ✅ CACHE: Save fresh friends for offline access
                try {
                    val friendsArray = org.json.JSONArray()
                    friends.forEach { friend ->
                        val obj = org.json.JSONObject()
                        obj.put("userId", friend.userId)
                        obj.put("username", friend.username)
                        obj.put("name", friend.name)
                        obj.put("email", friend.email)
                        obj.put("avatar", friend.avatar)
                        obj.put("bio", friend.bio)
                        obj.put("isOnline", friend.isOnline)
                        obj.put("lastSeen", friend.lastSeen ?: "")
                        val tagsArr = org.json.JSONArray(friend.tags)
                        obj.put("tags", tagsArr)
                        obj.put("role", friend.role ?: "")  // ✅ NEW: Cache role
                        obj.put("isAdmin", friend.isAdmin)  // ✅ NEW: Cache isAdmin
                        obj.put("isModerator", friend.isModerator)  // ✅ NEW: Cache isModerator
                        friendsArray.put(obj)
                    }
                    prefs.saveFriendsCache(friendsArray.toString())
                } catch (e: Exception) {
                    android.util.Log.w("FREETIME_HOME", "Failed to cache friends: ${e.message}")
                }
                
                chats = friends.mapIndexed { index, friend ->
                    ChatItem(
                        id = friend.userId,
                        name = friend.name.ifEmpty { friend.username },
                        lastMessage = "Tap to start chatting...",
                        timestamp = if (friend.isOnline) "Online now" else formatLastSeen(friend.lastSeen),
                        isUnread = false,
                        unreadCount = 0,
                        isTyping = false,
                        isOnline = friend.isOnline,  // USE REAL ONLINE STATUS
                        avatarColor = listOf(
                            Color(0xFFFF1493), Color(0xFF00FFFF), Color(0xFF9D4EDD),
                            Color(0xFF3A86FF), Color(0xFFFF7A00), Color(0xFF00FF00)
                        )[index % 6],
                        avatarUrl = apiService.resolveAvatarUrl(friend.avatar),
                        tags = friend.tags,
                        role = friend.role ?: "",  // ✅ NEW: Add role
                        isAdmin = friend.isAdmin,  // ✅ NEW: Add isAdmin
                        isModerator = friend.isModerator  // ✅ NEW: Add isModerator
                    )
                }
                loadingError = ""
            }
            result.onFailure { error ->
                android.util.Log.e("FREETIME_HOME", "⚠️  Failed to load friends from API: ${error.message}")
                
                // ✅ NEW: Auto-logout on 401 Unauthorized
                if (error.message?.contains("401") == true) {
                    android.util.Log.w("FREETIME_HOME", "🔐 Session expired (401). Navigating to login.")
                    prefs.clearAllAuthenticationData()
                    onNavigateToSettings() // Or dedicated logout function
                }
                
                // Don't clear chats - if cache was loaded, keep showing cached data
                val devSaveEnabled = prefs.getBoolean("dev_save_api_responses", false)
                if (chats.isEmpty()) {
                    loadingError = "Failed to load friends (${error.message ?: "unknown"})"
                    if (devSaveEnabled) loadingError += ". Tap 'Details' to view raw response."
                } else {
                    loadingError = "Using cached friends (reconnecting...)"
                }
                // Refresh in-memory saved response
                savedFriendsApiResponse.value = prefs.getString("last_api_friends_response", "")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.d("FREETIME_HOME", "ModernHomeScreen: Loading cancelled - screen left")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_HOME", "ModernHomeScreen: Exception: ${e.message}", e)
            loadingError = "Error: ${e.message ?: "Unknown error"}"
            // Keep cached chats if loading failed
            if (chats.isEmpty()) {
                chats = emptyList()
            }
        } finally {
            isLoadingChats = false
        }
        
        // Poll online status every 5 seconds
        while (true) {
            delay(5000)
            try {
                val result = apiService.getFriends()
                result.onSuccess { friends ->
                    android.util.Log.d("FREETIME_HOME", "📡 Online status poll: ${friends.filter { it.isOnline }.size}/${friends.size} online")
                    chats = chats.mapNotNull { chat ->
                        val updatedFriend = friends.find { it.userId == chat.id }
                        if (updatedFriend != null) {
                            chat.copy(
                                isOnline = updatedFriend.isOnline,
                                timestamp = if (updatedFriend.isOnline) "Online now" else (updatedFriend.lastSeen ?: "Offline")
                            )
                        } else {
                            chat
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_HOME", "Online status poll error: ${e.message}")
            }
        }
    }

    // ✅ FALLBACK POLLING: Periodically check for pending calls and requests via REST API
    // This ensures calls and friend requests are NEVER missed even if Socket.IO polling fails
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)  // Check every 15 seconds (calls must notify within this window)
            try {
                // Check for pending incoming calls
                val callsResult = apiService.getPendingCallsViaREST()
                callsResult.onSuccess { pendingCalls ->
                    if (pendingCalls.isNotEmpty()) {
                        android.util.Log.w("FREETIME_HOME", "🚨 FALLBACK POLL: Found ${pendingCalls.size} pending calls via REST!")
                        // Immediately notify via listener (if Socket.IO didn't deliver)
                        pendingCalls.forEach { callData ->
                            globalNotificationListener?.onIncomingCall(
                                WebSocketManager.IncomingCallData(
                                    callId = callData["callId"] as? String ?: "",
                                    callerId = callData["callerId"] as? String ?: "",
                                    callerUsername = callData["callerName"] as? String ?: "Unknown",
                                    callType = callData["callType"] as? String ?: "audio",
                                    sdpOffer = "",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                
                // Check for pending friend requests
                val requestsResult = apiService.getPendingFriendRequestsViaREST()
                requestsResult.onSuccess { pendingRequests ->
                    if (pendingRequests.isNotEmpty()) {
                        pendingRequests.forEach { request ->
                            // Deduplicate: skip if we already notified for this requestId
                            if (request.requestId.isNotEmpty() && notifiedRequestIds.contains(request.requestId)) return@forEach
                            if (request.requestId.isNotEmpty()) notifiedRequestIds.add(request.requestId)
                            val userName = request.senderUsername.ifEmpty { "Someone" }
                            android.util.Log.d("FREETIME_HOME", "REST Fallback: New friend request from $userName")
                            // Show system push notification
                            com.freetime.app.notifications.NotificationHelper.showFriendRequestNotification(
                                context = context,
                                senderName = userName,
                                senderId = request.senderId,
                                requestId = request.requestId
                            )
                            // Add in-app notification
                            com.freetime.app.notifications.InAppNotificationStore.addNotification(
                                com.freetime.app.notifications.InAppNotification(
                                    type = "friendRequest",
                                    title = userName,
                                    description = "Sent you a friend request",
                                    senderId = request.senderId
                                )
                            )
                        }
                        // Update badge count
                        pendingFriendRequestCount = pendingRequests.size
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("FREETIME_HOME", "Fallback polling error (non-critical): ${e.message}")
            }
        }
    }

    // ✅ SOCKET.IO DIAGNOSTIC CHECK: Monitor connectivity every 30 seconds
    // Helps identify transport issues and connection problems
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)  // Check every 30 seconds
            try {
                val diagResult = apiService.testSocketIODiagnostic()
                diagResult.onSuccess { diag ->
                    val connected = diag["clientConnected"] as? String ?: "unknown"
                    val transport = diag["currentTransport"] as? String ?: "none"
                    val socketId = diag["socketId"] as? String ?: "N/A"
                    android.util.Log.d("FREETIME_DIAG", "🔍 Socket.IO Status: Connected=$connected, Transport=$transport, SocketId=$socketId")
                    
                    if (connected == "false" || transport == "none") {
                        android.util.Log.w("FREETIME_DIAG", "⚠️ Socket.IO NOT CONNECTED! Relying on REST API fallback polling...")
                    } else {
                        android.util.Log.d("FREETIME_DIAG", "✅ Socket.IO connected via $transport")
                    }
                }
                diagResult.onFailure { error ->
                    android.util.Log.w("FREETIME_DIAG", "⚠️ Diagnostic check failed: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.d("FREETIME_DIAG", "Diagnostic error (non-critical): ${e.message}")
            }
        }
    }
    
    val CHAT_PERSIST_MS = 2 * 60 * 1000L // 2 minutes
    val filteredChats = chats.filter { chat ->
        val passesSearch = chat.name.contains(searchQuery, ignoreCase = true) ||
            chat.lastMessage.contains(searchQuery, ignoreCase = true)
        val isVisible = if (chat.id == com.freetime.app.api.ANNOUNCEMENT_USER_ID) {
            true  // Always visible, never auto-hide
        } else if (chat.lastMessageTimestamp > 0L) {
            System.currentTimeMillis() - chat.lastMessageTimestamp < CHAT_PERSIST_MS
        } else true
        passesSearch && isVisible
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
            )
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Top Bar
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "Messages",
                                style = MaterialTheme.typography.headlineSmall,
                                color = CyberpunkTheme.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp
                            )
                            Text(
                                "${filteredChats.size} conversations",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberpunkTheme.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        
                        // Update available icon
                        if (updateInfo != null) {
                            Box {
                                IconButton(
                                    onClick = { showUpdateDialog = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF00FFFF),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            null,
                                            tint = Color(0xFF00FFFF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Three-dot overflow menu
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            val unreadNotifCount = InAppNotificationStore.notifications.count { !it.isRead }

                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    null,
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                // Notifications
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Notifications")
                                            if (unreadNotifCount > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFFFF00FF)
                                                ) {
                                                    Text(
                                                        if (unreadNotifCount > 9) "9+" else unreadNotifCount.toString(),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showNotificationCenter = !showNotificationCenter
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Notifications, null, tint = Color(0xFF00FFFF))
                                    }
                                )

                                // Friend Requests / Search Friends
                                val friendIconTint = if (pendingFriendRequestCount > 0) CyberpunkTheme.CyberCyan else CyberpunkTheme.PrimaryPurple
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Find Friends")
                                            if (pendingFriendRequestCount > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFFFF00FF)
                                                ) {
                                                    Text(
                                                        pendingFriendRequestCount.toString(),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        if (pendingFriendRequestCount > 0) {
                                            onNavigateToFriendRequests()
                                        } else {
                                            onNavigateToSearchFriends()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PersonAdd, null, tint = friendIconTint)
                                    }
                                )

                                // Refresh Groups
                                DropdownMenuItem(
                                    text = { Text("Refresh Groups") },
                                    onClick = {
                                        showMenu = false
                                        GroupRefreshManager.triggerRefresh()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            null,
                                            tint = if (isLoadingGroups) Color(0xFF00FFFF) else CyberpunkTheme.PrimaryPurple
                                        )
                                    }
                                )

                                // Settings
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showMenu = false
                                        onNavigateToSettings()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, null, tint = CyberpunkTheme.PrimaryPurple)
                                    }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = CyberpunkTheme.Black,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .border(
                        width = 0.5.dp,
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = CyberpunkTheme.White
                )
            )
            
            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // Notification Center Overlay
            AnimatedVisibility(
                visible = showNotificationCenter,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                NotificationCenter(
                    pendingFriendRequestCount = pendingFriendRequestCount,
                    apiService = apiService,
                    prefs = prefs,
                    onDismiss = { showNotificationCenter = false },
                    onNavigateToChat = onNavigateToChat
                )
            }
            
            val filteredGroups = userGroups.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Chat List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (filteredChats.isEmpty() && filteredGroups.isEmpty()) {
                        EmptyChatList(searchQuery = searchQuery)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // Groups Section
                            if (filteredGroups.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "GROUPS",
                                        color = CyberpunkTheme.PrimaryPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredGroups) { group ->
                                    GroupListItem(
                                        group = group,
                                        onClick = {
                                            onNavigateToChat("group:${group.groupId}")
                                        }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "DIRECT MESSAGES",
                                        color = CyberpunkTheme.CyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                            
                            items(filteredChats) { chat ->
                                ChatListItem(
                                    chat = chat,
                                    isSelected = selectedChat == chat.id,
                                    onClick = {
                                        selectedChat = chat.id
                                        onNavigateToChat(chat.id)
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Sidebar with Mascot
                MascotSidebar(
                    mascotShowMessage = mascotShowMessage,
                    unreadCount = filteredChats.count { it.isOnline }.toString(),
                    onDismiss = { mascotShowMessage = false }
                )
            }
        }
    }

    // FAB for new group/channel
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ...existing code...
        // Place FAB at bottom right
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = CyberpunkTheme.PrimaryPurple
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Group/Channel", tint = Color.White)
        }

        // Dialog to choose group or channel
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("What would you like to create?", color = CyberpunkTheme.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showCreateDialog = false
                                android.util.Log.d("FREETIME_HOME", "✓ Navigate to Create Group")
                                onNavigateToCreateGroup()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = CyberpunkTheme.PrimaryPurple
                            )
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New Group", color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                disabledContainerColor = CyberpunkTheme.DarkGray.copy(alpha = 0.5f),
                                disabledContentColor = CyberpunkTheme.GhostGray
                            )
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New Channel — Coming Soon")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = CyberpunkTheme.PrimaryPurple)
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }

        // In-app update dialog
        if (showUpdateDialog && updateInfo != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Text("Update Available", color = CyberpunkTheme.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Version ${updateInfo!!.latestVersion} is now available.",
                            color = CyberpunkTheme.LightGray
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            updateInfo!!.releaseNotes,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 13.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        isDownloading = true
                        val info = updateInfo ?: return@TextButton
                        com.freetime.app.services.AppUpdateManager.downloadApk(context, info) { id ->
                            isDownloading = false
                            com.freetime.app.services.AppUpdateManager.installApk(context, id)
                        }
                    }) {
                        Text("Download", color = Color(0xFF00FFFF))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            updateInfo?.let { updatePrefs.setSkippedVersion(it.latestVersionCode) }
                            updateInfo = null
                        }) {
                            Text("Skip this version", color = CyberpunkTheme.GhostGray)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Later", color = CyberpunkTheme.PrimaryPurple)
                        }
                    }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(52.dp)
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        placeholder = {
            Text(
                "Search conversations...",
                color = CyberpunkTheme.GhostGray,
                fontSize = 13.sp,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                null,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = CyberpunkTheme.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedTextColor = CyberpunkTheme.White,
            unfocusedTextColor = CyberpunkTheme.White,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = CyberpunkTheme.CyberCyan,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            lineHeight = 16.sp
        ),
        singleLine = true,
        maxLines = 1
    )
}

@Composable
fun NotificationCenter(
    pendingFriendRequestCount: Int,
    apiService: FreeTimeApiService,
    prefs: SharedPreferencesHelper,
    onDismiss: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    var pendingRequests by remember { mutableStateOf(listOf<FriendRequest>()) }
    val scope = rememberCoroutineScope()
    val inAppNotifications = InAppNotificationStore.notifications
    
    LaunchedEffect(Unit) {
        scope.launch {
            val result = apiService.getPendingRequests()
            result.onSuccess { requests ->
                pendingRequests = requests
            }
        }
        InAppNotificationStore.markAllRead()
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        color = Color(0xFF0D0D1A),
        contentColor = CyberpunkTheme.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Notifications",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CyberpunkTheme.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (inAppNotifications.isNotEmpty()) {
                        IconButton(onClick = { InAppNotificationStore.clearAll() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.DeleteSweep, null, tint = CyberpunkTheme.LightGray)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = CyberpunkTheme.LightGray)
                    }
                }
            }
            
            Divider(color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f), thickness = 0.5.dp)
            
            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                // Friend Requests Section
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            "Friend Requests (${pendingRequests.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FFFF),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    items(pendingRequests.size, key = { pendingRequests[it].senderId }) { index ->
                        val request = pendingRequests[index]
                        FriendRequestActionCard(
                            username = request.senderUsername,
                            time = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(request.createdAt)),
                            onAccept = {
                                scope.launch {
                                    apiService.acceptFriendRequest(request.senderId).onSuccess {
                                        pendingRequests = pendingRequests.filter { r -> r.senderId != request.senderId }
                                    }
                                }
                            },
                            onDecline = {
                                scope.launch {
                                    apiService.declineFriendRequest(request.senderId).onSuccess {
                                        pendingRequests = pendingRequests.filter { r -> r.senderId != request.senderId }
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Real-time notifications from FCM
                if (inAppNotifications.isNotEmpty()) {
                    item {
                        Text(
                            "Recent Activity (${inAppNotifications.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    items(inAppNotifications.size, key = { inAppNotifications[it].id }) { index ->
                        val notif = inAppNotifications[index]
                        val timeAgo = getTimeAgo(notif.time)

                        when (notif.type) {
                            // Friend requests from FCM: show Accept / Decline inline
                            "friendRequest" -> {
                                if (notif.senderId.isNotEmpty()) {
                                    FriendRequestActionCard(
                                        username = notif.title,
                                        time = timeAgo,
                                        onAccept = {
                                            scope.launch {
                                                apiService.acceptFriendRequest(notif.senderId).onSuccess {
                                                    InAppNotificationStore.removeById(notif.id)
                                                }
                                            }
                                        },
                                        onDecline = {
                                            scope.launch {
                                                apiService.declineFriendRequest(notif.senderId).onSuccess {
                                                    InAppNotificationStore.removeById(notif.id)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    NotificationItem(
                                        icon = Icons.Default.PersonAdd,
                                        title = notif.title,
                                        description = notif.description,
                                        time = timeAgo,
                                        color = Color(0xFF00FFFF),
                                        badge = "Friend Request"
                                    )
                                }
                            }

                            // Messages: show notification + per-user Mute toggle
                            "message", "channelMessage" -> {
                                val isMuted = remember(notif.senderId) {
                                    mutableStateOf(prefs.isUserMuted(notif.senderId))
                                }
                                Column {
                                    NotificationItem(
                                        icon = Icons.Default.Chat,
                                        title = notif.title,
                                        description = notif.description,
                                        time = timeAgo,
                                        color = Color(0xFF00FF00),
                                        badge = if (notif.type == "channelMessage") "Channel" else "Message"
                                    )
                                    if (notif.senderId.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(end = 4.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            val muteColor = if (isMuted.value) Color(0xFFFF4444) else CyberpunkTheme.LightGray
                                            TextButton(
                                                onClick = {
                                                    if (isMuted.value) prefs.unmuteUser(notif.senderId)
                                                    else prefs.muteUser(notif.senderId)
                                                    isMuted.value = !isMuted.value
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isMuted.value) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                    contentDescription = if (isMuted.value) "Unmute" else "Mute",
                                                    tint = muteColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    if (isMuted.value) "Unmute" else "Mute",
                                                    fontSize = 10.sp,
                                                    color = muteColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Group invitations: show Join / Decline inline
                            "groupInvite" -> {
                                if (notif.inviteId.isNotEmpty()) {
                                    GroupInviteActionCard(
                                        groupName = notif.title,
                                        description = notif.description,
                                        time = timeAgo,
                                        onJoin = {
                                            scope.launch {
                                                apiService.acceptGroupInvitation(notif.inviteId).onSuccess {
                                                    InAppNotificationStore.removeById(notif.id)
                                                }
                                            }
                                        },
                                        onDecline = {
                                            scope.launch {
                                                apiService.declineGroupInvitation(notif.inviteId).onSuccess {
                                                    InAppNotificationStore.removeById(notif.id)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    NotificationItem(
                                        icon = Icons.Default.Group,
                                        title = notif.title,
                                        description = notif.description,
                                        time = timeAgo,
                                        color = Color(0xFFFFAA00),
                                        badge = "Group Invite"
                                    )
                                }
                            }

                            // Everything else: standard row with appropriate icon, clickable to chat
                            else -> {
                                val (icon, color, badge) = when (notif.type) {
                                    "call"       -> Triple(Icons.Default.Phone, Color(0xFF3A86FF), "Call")
                                    "missedCall" -> Triple(Icons.Default.PhoneMissed, Color(0xFFFF4444), "Missed Call")
                                    "friendAccepted" -> Triple(Icons.Default.CheckCircle, Color(0xFF00FF00), "Accepted")
                                    "groupVote", "group_vote", "group_invite" -> Triple(Icons.Default.Group, Color(0xFFFFAA00), "Group")
                                    "mediaDownloadRequest", "media_req" -> Triple(Icons.Default.Download, Color(0xFFFFAA00), "Download Req.")
                                    "mediaApproved" -> Triple(Icons.Default.CheckCircle, Color(0xFF00FF00), "Approved")
                                    "mediaDenied"  -> Triple(Icons.Default.Block, Color(0xFFFF4444), "Denied")
                                    else -> Triple(Icons.Default.Notifications, CyberpunkTheme.PrimaryPurple, "Notification")
                                }
                                
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (notif.senderId.isNotEmpty()) {
                                                onNavigateToChat(notif.senderId)
                                                onDismiss()
                                            }
                                        },
                                    color = Color.Transparent
                                ) {
                                    NotificationItem(
                                        icon = icon,
                                        title = notif.title,
                                        description = notif.description,
                                        time = timeAgo,
                                        color = color,
                                        badge = badge
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Empty State
                if (pendingRequests.isEmpty() && inAppNotifications.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    null,
                                    tint = CyberpunkTheme.DarkGray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No new notifications",
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

@Composable
fun NotificationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    time: String,
    color: Color,
    badge: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CyberpunkTheme.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    color = CyberpunkTheme.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.2f)),
                    color = Color.Transparent
                ) {
                    Text(
                        badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Text(
                    time,
                    fontSize = 9.sp,
                    color = CyberpunkTheme.DarkGray
                )
            }
        }
    }
}

/**
 * Actionable notification card for pending friend requests.
 * Shows username + Accept / Decline buttons; replaces itself with a spinner while the API call is in flight.
 */
@Composable
fun FriendRequestActionCard(
    username: String,
    time: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var isActing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF00FFFF).copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        color = Color(0xFF00FFFF).copy(alpha = 0.07f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Top row: icon + name + badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PersonAdd, null,
                    tint = Color(0xFF00FFFF),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        username,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = CyberpunkTheme.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Sent you a friend request",
                        fontSize = 11.sp, color = CyberpunkTheme.LightGray
                    )
                }
                Text(
                    time,
                    fontSize = 9.sp, color = CyberpunkTheme.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row: spinner while acting, buttons otherwise
            if (isActing) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00FFFF), strokeWidth = 2.dp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = { isActing = true; onDecline() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF4444).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
                    ) {
                        Text("DECLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { isActing = true; onAccept() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FFFF).copy(alpha = 0.25f),
                            contentColor = Color(0xFF00FFFF)
                        )
                    ) {
                        Text("ACCEPT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Actionable notification card for pending group invitations.
 * Shows group name + inviter + Join / Decline buttons.
 */
@Composable
fun GroupInviteActionCard(
    groupName: String,
    description: String,
    time: String,
    onJoin: () -> Unit,
    onDecline: () -> Unit
) {
    var isActing by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFFFAA00).copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        color = Color(0xFFFFAA00).copy(alpha = 0.07f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group, null,
                    tint = Color(0xFFFFAA00),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        groupName,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = CyberpunkTheme.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        description,
                        fontSize = 11.sp, color = CyberpunkTheme.LightGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    time,
                    fontSize = 9.sp, color = CyberpunkTheme.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isActing) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFFAA00), strokeWidth = 2.dp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = { isActing = true; onDecline() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF4444).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
                    ) {
                        Text("DECLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { isActing = true; onJoin() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFAA00).copy(alpha = 0.25f),
                            contentColor = Color(0xFFFFAA00)
                        )
                    ) {
                        Text("JOIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ✅ NEW: Helper function for color-coding usernames based on tags/role
fun getUsernameColor(
    tags: List<String> = emptyList(),
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    role: String = ""
): Color {
    // Default to white if no tags/roles
    if (tags.isEmpty() && !isAdmin && !isModerator && role.isBlank()) {
        return CyberpunkTheme.White
    }
    
    return when {
        // Priority 1: OWNER tag → Bright Magenta
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Bright Magenta
        // Priority 2: VIP tag → Yellow
        tags.contains("VIP") -> Color(0xFFFFFF00)  // Bright Yellow
        // Priority 3: BETA TESTER tag → Cyan
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Bright Cyan
        // Priority 4: isAdmin or role=admin → Red
        isAdmin || role.equals("admin", ignoreCase = true) -> Color(0xFFFF0000)  // Bright Red
        // Priority 5: isModerator or role=moderator → Orange
        isModerator || role.equals("moderator", ignoreCase = true) -> Color(0xFFFF8C00)  // Bright Orange
        // Default → White
        else -> CyberpunkTheme.White
    }
}

@Composable
fun ChatListItem(
    chat: ChatItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = 300f)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                            CyberpunkTheme.CyberCyan.copy(alpha = 0.2f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF0F0F1E)
                        )
                    )
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) 
                    CyberpunkTheme.CyberCyan 
                else 
                    CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with positioned status indicator outside
            Box(modifier = Modifier.size(56.dp)) {
                if (chat.id == com.freetime.app.api.ANNOUNCEMENT_USER_ID) {
                    Image(
                        painter = painterResource(id = com.freetime.app.R.drawable.saying),
                        contentDescription = "Announcement Mascot",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFFAA00), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (!chat.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(chat.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${chat.name}'s avatar",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, chat.avatarColor, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = chat.avatarColor.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = chat.avatarColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (chat.name.firstOrNull() ?: '?').toString().uppercase(),
                            color = chat.avatarColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                
                // Online Indicator - LARGER and MORE VISIBLE (18.dp instead of 14.dp)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = if (chat.isOnline) Color(0xFF00FF00) else Color(0xFF888888),
                            shape = CircleShape
                        )
                        .border(
                            width = 3.dp,
                            color = CyberpunkTheme.Black,
                            shape = CircleShape
                        )
                        .align(Alignment.BottomEnd)
                        .offset(x = (-3.dp), y = (-3.dp))
                )
            }
            
            // Message content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            chat.name,
                            color = getUsernameColor(chat.tags, chat.isAdmin, chat.isModerator, chat.role),  // ✅ NEW: Apply color coding
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            chat.timestamp,
                            color = CyberpunkTheme.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (chat.isTyping) {
                                TypingIndicator()
                            } else {
                                Text(
                                    chat.lastMessage,
                                    color = if (chat.isUnread) CyberpunkTheme.White else CyberpunkTheme.LightGray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (chat.isUnread) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    
                    // Tags display
                    if (chat.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chat.tags.take(3).forEach { tag ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f))
                                        .border(
                                            width = 0.5.dp,
                                            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.Transparent
                                ) {
                                    Text(
                                        text = "#$tag",
                                        color = CyberpunkTheme.CyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (chat.tags.size > 3) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.Transparent
                                ) {
                                    Text(
                                        text = "+${chat.tags.size - 3}",
                                        color = CyberpunkTheme.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Unread badge
                if (chat.unreadCount > 0) {
                    Badge(
                        modifier = Modifier.size(24.dp),
                        containerColor = CyberpunkTheme.PrimaryPurple,
                        contentColor = CyberpunkTheme.Black
                    ) {
                        Text(
                            if (chat.unreadCount > 99) "∞" else chat.unreadCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val scale by animateFloatAsState(
                targetValue = if (i % 2 == 0) 1f else 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(
                        color = CyberpunkTheme.CyberCyan,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun MascotSidebar(
    mascotShowMessage: Boolean,
    unreadCount: String,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = mascotShowMessage,
        enter = slideInHorizontally(initialOffsetX = { 250 }),
        exit = slideOutHorizontally(targetOffsetX = { 250 }),
        modifier = Modifier
            .widthIn(min = 200.dp, max = 320.dp)
            .fillMaxHeight()
            .padding(start = 12.dp, end = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF0F0F1E),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .align(Alignment.End)
            ) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Mascot Image - Fixed and enlarged with proper padding
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F0F1E)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.saying),
                    contentDescription = "Cody - Helper Mascot",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Message box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "You have",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
                
                Text(
                    unreadCount,
                    color = CyberpunkTheme.CyberCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
                
                Text(
                    "friends online! 🎉",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun GroupListItem(
    group: Group,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.CyberCyan.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12122A)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ FIX: Group avatar with error handling - show PFP if available, fallback to Group icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = CyberpunkTheme.CyberCyan.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = CyberpunkTheme.CyberCyan.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // ✅ FIX: Log what we're trying to display
                android.util.Log.d("FREETIME_HOME_GROUP_AVATAR", "📁 ${group.name}: profilePictureUrl=${group.profilePictureUrl ?: "NULL"}, avatar=${group.avatar ?: "NULL"}")
                
                // Try both profilePictureUrl and avatar fields
                val pictureUrl = if (!group.profilePictureUrl.isNullOrEmpty()) {
                    group.profilePictureUrl
                } else if (!group.avatar.isNullOrEmpty()) {
                    group.avatar
                } else {
                    // ✅ FIX: Construct endpoint URL if neither field is populated
                    "/api/groups/${group.groupId}/picture"
                }
                
                if (!pictureUrl.isNullOrEmpty() && pictureUrl != "/api/groups/${group.groupId}/picture") {
                    var imageLoaded by remember { mutableStateOf(false) }
                    var imageError by remember { mutableStateOf(false) }
                    
                    // ✅ FIX: Ensure URL is absolute
                    val baseUrl = ApiClient.getBaseUrl().trimEnd('/')
                    val absoluteUrl = if (pictureUrl.startsWith("http")) {
                        pictureUrl
                    } else if (pictureUrl.startsWith("/")) {
                        "$baseUrl$pictureUrl"
                    } else {
                        "$baseUrl/$pictureUrl"
                    }
                    
                    // ✅ FIX: Add cache busting with timestamp
                    val imageUrl = if (!group.profilePictureUpdatedAt.isNullOrEmpty()) {
                        "$absoluteUrl?v=${group.profilePictureUpdatedAt?.hashCode() ?: System.currentTimeMillis()}"
                    } else {
                        "$absoluteUrl?v=${System.currentTimeMillis()}"
                    }
                    
                    android.util.Log.d("FREETIME_HOME_GROUP_AVATAR", "📁 ${group.name}: Loading image from $imageUrl")
                    
                    if (!imageError && imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = group.name,
                            contentScale = ContentScale.Crop,
                            onSuccess = { 
                                imageLoaded = true
                                android.util.Log.d("FREETIME_HOME_GROUP_AVATAR", "✅ ${group.name}: Image loaded successfully")
                            },
                            onError = { 
                                imageError = true
                                android.util.Log.w("FREETIME_HOME_GROUP_AVATAR", "❌ ${group.name}: Failed to load image from $imageUrl")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Fallback icon if image failed to load
                    if (imageError || !imageLoaded) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = CyberpunkTheme.CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    group.name,
                    color = CyberpunkTheme.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.description.isNotEmpty()) {
                    Text(
                        group.description,
                        color = CyberpunkTheme.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${group.memberCount} members",
                    color = CyberpunkTheme.CyberCyan.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CyberpunkTheme.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun EmptyChatList(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.open_arms),
            contentDescription = "No chats",
            modifier = Modifier
                .size(120.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            if (searchQuery.isEmpty()) "No conversations yet" else "No results found",
            color = CyberpunkTheme.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
        
        Text(
            if (searchQuery.isEmpty())
                "Start a new conversation to get going!"
            else
                "Try a different search term",
            color = CyberpunkTheme.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * Format a lastSeen ISO timestamp into a human-readable relative time string
 * e.g. "Just now", "5m ago", "2h ago", "3 days ago", "Jun 15"
 */
fun formatLastSeen(lastSeen: String?): String {
    if (lastSeen.isNullOrEmpty() || lastSeen == "Offline") return "Offline"
    return try {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        )
        formats.forEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        
        var date: Date? = null
        for (fmt in formats) {
            try {
                date = fmt.parse(lastSeen)
                if (date != null) break
            } catch (_: Exception) {}
        }
        
        if (date == null) return lastSeen
        
        val now = System.currentTimeMillis()
        val diffMs = now - date.time
        if (diffMs < 0) return "Just now"
        
        val diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        
        when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.US)
                sdf.format(date)
            }
        }
    } catch (e: Exception) {
        lastSeen
    }
}
