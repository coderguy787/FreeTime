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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.UserData
import com.freetime.app.api.FriendRequest
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.components.CyberpunkTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

// ✅ NEW: Helper function to get search result user color based on tags and role
fun getSearchResultUserColor(
    tags: List<String>,
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    role: String? = null
): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Magenta
        tags.contains("VIP") -> Color(0xFFFFFF00)  // Yellow
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Cyan
        isAdmin || role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)  // Red
        isModerator || role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)  // Orange
        else -> CyberpunkTheme.White
    }
}

@Composable
fun SearchFriendsScreen(
    onNavigateBack: () -> Unit,
    onFriendAdded: (String) -> Unit = {},
    onNavigateToFriendRequests: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = FreeTimeApiService(context)
    val prefs = SharedPreferencesHelper(context)
    
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<UserData>()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var sentRequests by remember { mutableStateOf(setOf<String>()) }
    var sentRequestIds by remember { mutableStateOf(mapOf<String, String>()) }
    var undoTimers by remember { mutableStateOf(mapOf<String, Int>()) }
    var loadingStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var pendingRequests by remember { mutableStateOf(listOf<FriendRequest>()) }
    var isLoadingRequests by remember { mutableStateOf(true) }
    var isRefreshingRequests by remember { mutableStateOf(false) }
    
    // Function to refresh pending requests
    val refreshPendingRequests = {
        scope.launch {
            try {
                isRefreshingRequests = true
                val result = apiService.getPendingRequests()
                result.onSuccess { requests ->
                    pendingRequests = requests
                    android.util.Log.d("FREETIME_SEARCH", "Refreshed pending requests: ${requests.size} loaded")
                }
                result.onFailure { error ->
                    android.util.Log.e("FREETIME_SEARCH", "Error refreshing requests: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_SEARCH", "Exception refreshing requests: ${e.message}")
            } finally {
                isRefreshingRequests = false
            }
        }
    }
    
    // ✅ REAL-TIME FRIEND LIST SYNC: WebSocket listener for friend list updates
    val friendListListener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
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
        override fun onFriendRequestRejected(data: com.freetime.app.services.WebSocketManager.FriendRequestEventData) {
            android.util.Log.d("FREETIME_SEARCH", "Friend request rejected for user ${data.senderId}")
            sentRequests = sentRequests - data.senderId
            sentRequestIds = sentRequestIds - data.senderId
            undoTimers = undoTimers - data.senderId
            errorMessage = "✋ Friend request rejected"
        }
        override fun onFriendRequestAutoAccepted(data: com.freetime.app.services.WebSocketManager.FriendRequestAutoAcceptedData) {}
        override fun onFriendAdded(data: com.freetime.app.services.WebSocketManager.FriendAddedData) {
            // Real-time friend list sync: friend was added
            android.util.Log.d("FREETIME_SEARCH", "👥 Friend added event: ${data.username} (${data.userId})")
            // Optionally refresh pending requests if visible
            if (selectedTab == 2) {
                refreshPendingRequests()
            }
        }
        override fun onFriendRemoved(data: com.freetime.app.services.WebSocketManager.FriendRemovedData) {
            // Real-time friend list sync: friend was removed
            android.util.Log.d("FREETIME_SEARCH", "👥 Friend removed event: ${data.removedFriendId}")
            // Optionally refresh pending requests if visible
            if (selectedTab == 2) {
                refreshPendingRequests()
            }
        }
        override fun onConnectionEstablished() {}
        override fun onConnectionLost() {}
        override fun onError(error: String) {}
        override fun onChatHistoryDeleted(data: com.freetime.app.services.WebSocketManager.ChatHistoryDeletedData) {}
    }
    
    DisposableEffect(Unit) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        wsManager.addListener(friendListListener)
        onDispose {
            wsManager.removeListener(friendListListener)
        }
    }
    
    // Rate limiting: 3 requests max, then 3 minute cooldown
    var lastRequestTimestamps by remember { mutableStateOf(listOf<Long>()) }
    val MAX_REQUESTS_PER_WINDOW = 3
    val COOLDOWN_DURATION_MS = 3 * 60 * 1000L // 3 minutes in milliseconds
    var cooldownTimeRemaining by remember { mutableStateOf(0L) }
    
    // Countdown timer for rate limiting
    LaunchedEffect(lastRequestTimestamps.size) {
        while (true) {
            val now = System.currentTimeMillis()
            // Remove requests older than 3 minutes
            val validRequests = lastRequestTimestamps.filter { timestamp ->
                (now - timestamp) < COOLDOWN_DURATION_MS
            }
            lastRequestTimestamps = validRequests
            
            if (validRequests.size >= MAX_REQUESTS_PER_WINDOW) {
                // Still in cooldown, calculate remaining time
                val oldestRequest = validRequests.first()
                val timeRemaining = (oldestRequest + COOLDOWN_DURATION_MS - now) / 1000
                if (timeRemaining > 0) {
                    cooldownTimeRemaining = timeRemaining
                }
            } else {
                cooldownTimeRemaining = 0
            }
            
            kotlinx.coroutines.delay(1000)
        }
    }
    
    android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Opened search friends")
    
    // Load pending requests on screen open
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val result = apiService.getPendingRequests()
                result.onSuccess { requests ->
                    pendingRequests = requests
                    android.util.Log.d("FREETIME_SEARCH", "Loaded ${requests.size} pending friend requests")
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_SEARCH", "Error loading pending requests: ${e.message}")
            } finally {
                isLoadingRequests = false
            }
        }
    }
    
    // Auto-refresh pending requests every 10 seconds
    var autoRefreshEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(selectedTab, autoRefreshEnabled) {
        while (autoRefreshEnabled) {
            delay(10000) // Refresh every 10 seconds
            if (selectedTab == 1) { // Only refresh when on Receive tab
                refreshPendingRequests()
            }
        }
    }
    
    // ✅ CACHE CLEARING: Auto-clear search results when switching away from Search tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) {  // 0 = Search tab, 1 = Receive tab
            searchResults = emptyList()
            searchQuery = ""
            errorMessage = ""
            android.util.Log.d("FREETIME_SEARCH", "Cleared search results when switching tabs")
        }
    }
    
    // ✅ CACHE EXPIRATION: Track when search results were loaded
    var lastSearchTime by remember { mutableStateOf(0L) }
    var searchResultsExpired by remember { mutableStateOf(false) }
    
    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty()) {
            lastSearchTime = System.currentTimeMillis()
            delay(24 * 60 * 60 * 1000)  // 24 hours
            searchResults = emptyList()
            searchResultsExpired = true
            android.util.Log.d("FREETIME_SEARCH", "Search results cache expired after 24 hours")
        }
    }
    
    // Search users function
    val performSearch = {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            errorMessage = ""
            scope.launch {
                try {
                    val result = apiService.searchUsers(trimmed)
                    result.onSuccess { users ->
                        android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Found ${users.size} users")
                        searchResults = users
                    }
                    result.onFailure { error ->
                        android.util.Log.e("FREETIME_SEARCH", "SearchFriendsScreen: Search error: ${error.message}")
                        errorMessage = "Search failed: ${error.message ?: "Unknown error"}"
                        searchResults = emptyList()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Search cancelled")
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_SEARCH", "SearchFriendsScreen: Exception: ${e.message}", e)
                    errorMessage = "Error: ${e.message ?: "Unknown error"}"
                    searchResults = emptyList()
                } finally {
                    isSearching = false
                }
            }
        } else {
            // ✅ CACHE CLEAR: When query is empty, clear results immediately
            searchResults = emptyList()
            errorMessage = ""
            sentRequests = emptySet()  // Also clear sent request tracking
        }
        Unit
    }
    
    // Send friend request function with rate limiting
    val sendFriendRequest = { userId: String ->
        val now = System.currentTimeMillis()
        
        // Remove timestamps older than 3 minutes
        val validRequests = lastRequestTimestamps.filter { timestamp ->
            (now - timestamp) < COOLDOWN_DURATION_MS
        }
        lastRequestTimestamps = validRequests
        
        android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Rate check - Current requests in window: ${validRequests.size}/$MAX_REQUESTS_PER_WINDOW")
        
        // Check if we've hit the limit (3 requests in last 3 minutes)
        if (validRequests.size >= MAX_REQUESTS_PER_WINDOW) {
            val oldestRequest = validRequests.first()
            val remainingTime = (oldestRequest + COOLDOWN_DURATION_MS - now) / 1000
            errorMessage = "⏸️ Rate limit active! You've sent 3 requests. Please wait ${remainingTime}s before sending more."
            android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: ⏸️ RATE LIMIT BLOCKED - 3 requests in window, ${remainingTime}s remaining")
        } else {
            // Add timestamp NOW (counts this attempt) before attempting the request
            lastRequestTimestamps = validRequests + now
            val currentCount = lastRequestTimestamps.filter { ts -> (now - ts) < COOLDOWN_DURATION_MS }.size
            
            android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: 📤 Sending request - Count will be: ${currentCount}/$MAX_REQUESTS_PER_WINDOW")
            
            // Rate limit check passed, send the request
            loadingStates = loadingStates + (userId to true)
            scope.launch {
                try {
                    val result = apiService.sendFriendRequest(searchResults.find { it.userId == userId }?.username ?: "")
                    result.onSuccess { response ->
                        android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: ✅ Friend request sent successfully to $userId")
                        
                        // ✅ MUTUAL FRIEND FIX: Check autoAccepted field directly instead of string parsing
                        if (response.autoAccepted) {
                            android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: 🎉 Mutual friend request - instant friendship! (${response.message})")
                            errorMessage = "🎉 ${response.message}"
                            
                            // ✅ REFRESH: Refresh pending requests to sync state
                            // This removes any pending requests from this user
                            try {
                                refreshPendingRequests()
                                android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Refreshed pending requests after mutual accept")
                            } catch (e: Exception) {
                                android.util.Log.w("FREETIME_SEARCH", "Failed to refresh pending requests: ${e.message}")
                            }
                            
                            onFriendAdded(userId)
                        } else {
                            sentRequests = sentRequests + userId
                            if (response.requestId != null) {
                                sentRequestIds = sentRequestIds + (userId to response.requestId)
                            }

                            // Start 5s undo countdown
                            scope.launch {
                                for (i in 5 downTo 1) {
                                    undoTimers = undoTimers + (userId to i)
                                    delay(1000)
                                }
                                undoTimers = undoTimers - userId
                            }

                            val recentCount = lastRequestTimestamps.filter { ts -> (System.currentTimeMillis() - ts) < COOLDOWN_DURATION_MS }.size
                            if (recentCount >= MAX_REQUESTS_PER_WINDOW) {
                                val oldestTs = lastRequestTimestamps.minOrNull() ?: now
                                val waitTime = (oldestTs + COOLDOWN_DURATION_MS - System.currentTimeMillis()) / 1000
                                errorMessage = "✅ Request sent! You've reached 3 requests. Wait ${waitTime}s before sending more."
                                android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: ⚠️ LIMIT REACHED - ${recentCount}/3 requests, ${waitTime}s cooldown")
                            } else {
                                errorMessage = "✅ Request sent! You can send ${MAX_REQUESTS_PER_WINDOW - recentCount} more requests."
                                android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: ✅ Request ${recentCount}/$MAX_REQUESTS_PER_WINDOW - Can send ${MAX_REQUESTS_PER_WINDOW - recentCount} more")
                            }
                            onFriendAdded(userId)
                        }
                    }
                    result.onFailure { error ->
                        android.util.Log.e("FREETIME_SEARCH", "SearchFriendsScreen: ❌ Request failed: ${error.message}")
                        // Keep the attempt in the count (rate limiting applies to attempts)
                        val recentCount = lastRequestTimestamps.filter { ts -> (System.currentTimeMillis() - ts) < COOLDOWN_DURATION_MS }.size
                        
                        // Provide better error messages
                        val userMessage = when {
                            error.message?.contains("already exists", ignoreCase = true) == true -> 
                                "❌ Request already pending with this user - check your pending requests"
                            error.message?.contains("Already friends", ignoreCase = true) == true -> 
                                "👥 You're already friends with this user!"
                            error.message?.contains("not found", ignoreCase = true) == true -> 
                                "❌ User not found - they may have deleted their account"
                            else -> 
                                "❌ ${error.message ?: "Failed to send request"}"
                        }
                        
                        errorMessage = "$userMessage (Attempt ${recentCount}/$MAX_REQUESTS_PER_WINDOW)"
                        android.util.Log.d("FREETIME_SEARCH", "SearchFriendsScreen: Failed attempt counted - ${recentCount}/$MAX_REQUESTS_PER_WINDOW in window")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_SEARCH", "SearchFriendsScreen: Exception: ${e.message}", e)
                    val recentCount = lastRequestTimestamps.filter { ts -> (System.currentTimeMillis() - ts) < COOLDOWN_DURATION_MS }.size
                    errorMessage = "❌ Error: ${e.message ?: "Unknown error"} (Attempt ${recentCount}/$MAX_REQUESTS_PER_WINDOW)"
                } finally {
                    loadingStates = loadingStates + (userId to false)
                }
            }
        }
        Unit
    }

    val cancelRequest = { userId: String ->
        val requestId = sentRequestIds[userId]
        if (requestId != null) {
            scope.launch {
                try {
                    val result = apiService.cancelFriendRequest(requestId)
                    result.onSuccess {
                        android.util.Log.d("FREETIME_SEARCH", "Cancelled request $requestId for user $userId")
                    }
                    result.onFailure { error ->
                        android.util.Log.e("FREETIME_SEARCH", "Server cancel failed for request $requestId: ${error.message}")
                        errorMessage = "⚠️ Could not cancel on server"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_SEARCH", "Exception cancelling request: ${e.message}")
                }
            }
        } else {
            android.util.Log.w("FREETIME_SEARCH", "No requestId for user $userId — skipping server cancel")
        }
        undoTimers = undoTimers - userId
        sentRequests = sentRequests - userId
        sentRequestIds = sentRequestIds - userId
        errorMessage = "↩️ Request cancelled"
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27))
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        "Add Friends",
                        color = CyberpunkTheme.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Refresh button (only visible on Receive tab)
                if (selectedTab == 1) {
                    IconButton(
                        onClick = { refreshPendingRequests() },
                        enabled = !isRefreshingRequests,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = if (isRefreshingRequests) CyberpunkTheme.LightGray else CyberpunkTheme.CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberpunkTheme.Black),
                containerColor = CyberpunkTheme.Black,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(CyberpunkTheme.CyberCyan)
                    )
                },
                divider = {
                    Divider(
                        thickness = 0.5.dp,
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.background(CyberpunkTheme.Black),
                    text = {
                        Text(
                            "Search",
                            color = if (selectedTab == 0) CyberpunkTheme.CyberCyan else CyberpunkTheme.LightGray,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.background(CyberpunkTheme.Black),
                    text = {
                        Text(
                            "Receive (${pendingRequests.size})",
                            color = if (selectedTab == 1) CyberpunkTheme.CyberCyan else CyberpunkTheme.LightGray,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> SearchContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    errorMessage = errorMessage,
                    sentRequests = sentRequests,
                    loadingStates = loadingStates,
                    undoTimers = undoTimers,
                    onPerformSearch = performSearch,
                    onSendFriendRequest = sendFriendRequest,
                    onCancelRequest = cancelRequest,
                    onClearSearch = { 
                        searchQuery = ""
                        val empty = listOf<UserData>()
                        searchResults = empty
                    }
                )
                1 -> ReceiveContent(
                    pendingRequests = pendingRequests,
                    isLoadingRequests = isLoadingRequests,
                    isRefreshingRequests = isRefreshingRequests,
                    apiService = apiService,
                    scope = scope,
                    onRequestsUpdated = { pendingRequests = it },
                    onRefresh = { refreshPendingRequests() },
                    onFriendAdded = onFriendAdded
                )
            }
        }
    }
}

@Composable
fun SearchContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<UserData>,
    isSearching: Boolean,
    errorMessage: String,
    sentRequests: Set<String>,
    loadingStates: Map<String, Boolean>,
    undoTimers: Map<String, Int> = emptyMap(),
    onPerformSearch: () -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onCancelRequest: (String) -> Unit = {},
    onClearSearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xFF1A1A2E), shape = RoundedCornerShape(12.dp))
                .border(
                    width = 1.5.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(20.dp)
            )
            
            TextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    onSearchQueryChange(newValue.replace("\n", "").replace("\r", ""))
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                placeholder = {
                    Text(
                        "Search by username...",
                        color = CyberpunkTheme.GhostGray,
                        fontSize = 13.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = CyberpunkTheme.White,
                    unfocusedTextColor = CyberpunkTheme.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = CyberpunkTheme.CyberCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )
            
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = CyberpunkTheme.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            IconButton(
                onClick = onPerformSearch,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = CyberpunkTheme.CyberCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Done,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Error Message
        if (errorMessage.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFff4444).copy(alpha = 0.2f)
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
        
        // Results List
        if (searchResults.isEmpty() && searchQuery.isNotEmpty() && !isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PersonOff,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No users found",
                    color = CyberpunkTheme.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Try another search term",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { user ->
                    SearchResultCard(
                        user = user,
                        isRequested = user.userId in sentRequests,
                        isLoading = loadingStates[user.userId] ?: false,
                        undoSeconds = undoTimers[user.userId],
                        onSendRequest = { onSendFriendRequest(user.userId) },
                        onCancelRequest = { onCancelRequest(user.userId) }
                    )
                }
            }
        } else if (!isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Explore,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Search for friends",
                    color = CyberpunkTheme.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Type a username to get started",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ReceiveContent(
    pendingRequests: List<FriendRequest>,
    isLoadingRequests: Boolean,
    isRefreshingRequests: Boolean,
    apiService: FreeTimeApiService,
    scope: CoroutineScope,
    onRequestsUpdated: (List<FriendRequest>) -> Unit,
    onRefresh: () -> Unit,
    onFriendAdded: (String) -> Unit
) {
    var selectedRequests by remember { mutableStateOf<Set<String>>(setOf()) }
    var isAcceptingAll by remember { mutableStateOf(false) }
    
    val toggleRequestSelection = { requestId: String ->
        selectedRequests = if (requestId in selectedRequests) {
            selectedRequests - requestId
        } else {
            selectedRequests + requestId
        }
    }
    
    val selectAll = {
        selectedRequests = pendingRequests.map { it.senderId }.toSet()
    }
    
    val deselectAll = {
        selectedRequests = setOf()
    }
    
    val acceptSelectedRequests: () -> Unit = {
        scope.launch {
            try {
                isAcceptingAll = true
                val requestsToAccept = pendingRequests.filter { it.senderId in selectedRequests }
                var successCount = 0
                
                requestsToAccept.forEach { request ->
                    try {
                        val result = apiService.acceptFriendRequest(request.senderId)
                        result.onSuccess {
                            successCount++
                            onFriendAdded(request.senderId)
                            android.util.Log.d("FREETIME_SEARCH", "Accepted request from ${request.senderUsername}")
                        }
                        result.onFailure { error ->
                            android.util.Log.e("FREETIME_SEARCH", "Failed to accept request from ${request.senderUsername}: ${error.message}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_SEARCH", "Exception accepting request from ${request.senderUsername}: ${e.message}")
                    }
                }
                
                // Refresh list and show result
                onRefresh()
                selectedRequests = setOf()
                android.util.Log.d("FREETIME_SEARCH", "Bulk accept complete: $successCount/${requestsToAccept.size} accepted")
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_SEARCH", "Error in bulk accept: ${e.message}")
            } finally {
                isAcceptingAll = false
            }
        }
    }
    
    if (isLoadingRequests && pendingRequests.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = CyberpunkTheme.CyberCyan,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loading requests...",
                color = CyberpunkTheme.White,
                fontSize = 14.sp
            )
        }
    } else if (pendingRequests.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.DoneAll,
                null,
                tint = CyberpunkTheme.LightGray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No pending requests",
                color = CyberpunkTheme.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "All caught up!",
                color = CyberpunkTheme.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Bulk action toolbar
            if (pendingRequests.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF0D0D1A),
                    border = BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedRequests.size}/${pendingRequests.size} selected",
                            color = CyberpunkTheme.CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedRequests.isEmpty()) {
                                OutlinedButton(
                                    onClick = selectAll,
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = CyberpunkTheme.PrimaryPurple
                                    ),
                                    border = BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Select All", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = acceptSelectedRequests,
                                    enabled = !isAcceptingAll,
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF00FF00).copy(alpha = 0.2f),
                                        contentColor = Color(0xFF00FF00)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAcceptingAll) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFF00FF00)
                                        )
                                    } else {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Accept (${
                                            selectedRequests.size
                                        })", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = deselectAll,
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = CyberpunkTheme.LightGray
                                    ),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clear", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingRequests, key = { it.senderId }) { request ->
                    FriendRequestCard(
                        request = request,
                        apiService = apiService,
                        scope = scope,
                        isSelected = request.senderId in selectedRequests,
                        onToggleSelection = { toggleRequestSelection(request.senderId) },
                        onRequestRemoved = {
                            onRequestsUpdated(pendingRequests.filter { it.senderId != request.senderId })
                            selectedRequests = selectedRequests - request.senderId
                            onFriendAdded(request.senderId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FriendRequestCard(
    request: FriendRequest,
    apiService: FreeTimeApiService,
    scope: CoroutineScope,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onRequestRemoved: () -> Unit
) {
    var isAccepting by remember { mutableStateOf(false) }
    var isDeclining by remember { mutableStateOf(false) }

    val userColor = getSearchResultUserColor(
        tags = request.senderTags,
        isAdmin = request.senderIsAdmin,
        isModerator = request.senderIsModerator,
        role = request.senderRole
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A1A3E) else Color(0xFF1A1A2E)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.5.dp,
            color = if (isSelected) CyberpunkTheme.CyberCyan else userColor.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelection() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyberpunkTheme.CyberCyan,
                            uncheckedColor = userColor
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(userColor.copy(alpha = 0.15f), shape = CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val resolvedAvatar = apiService.resolveAvatarUrl(request.avatarUrl)
                        if (!resolvedAvatar.isNullOrEmpty() && resolvedAvatar != "null") {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(resolvedAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${request.senderUsername} avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                request.senderUsername.firstOrNull()?.uppercase() ?: "?",
                                color = userColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            request.senderUsername,
                            color = userColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "Wants to be your friend",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 11.sp
                        )
                        if (request.senderTags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                request.senderTags.take(3).forEach { tag ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(userColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.Transparent
                                    ) {
                                        Text(
                                            "#$tag",
                                            color = userColor.copy(alpha = 0.7f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (request.senderTags.size > 3) {
                                    Text(
                                        "+${request.senderTags.size - 3}",
                                        color = CyberpunkTheme.LightGray,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isAccepting = true
                            scope.launch {
                                try {
                                    val result = apiService.acceptFriendRequest(request.senderId)
                                    result.onSuccess {
                                        android.util.Log.d("FREETIME_RECEIVE", "Accepted friend request from ${request.senderUsername}")
                                        onRequestRemoved()
                                    }
                                    result.onFailure { error ->
                                        android.util.Log.e("FREETIME_RECEIVE", "Failed to accept: ${error.message}")
                                        isAccepting = false
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("FREETIME_RECEIVE", "Exception: ${e.message}")
                                    isAccepting = false
                                }
                            }
                        },
                        enabled = !isAccepting && !isDeclining,
                        modifier = Modifier.height(36.dp).widthIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF00).copy(alpha = 0.7f),
                            disabledContainerColor = Color(0xFF00FF00).copy(alpha = 0.3f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAccepting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                                color = CyberpunkTheme.Black
                            )
                        } else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = CyberpunkTheme.Black)
                        }
                    }

                    Button(
                        onClick = {
                            isDeclining = true
                            scope.launch {
                                try {
                                    val result = apiService.declineFriendRequest(request.senderId)
                                    result.onSuccess {
                                        android.util.Log.d("FREETIME_RECEIVE", "Declined friend request from ${request.senderUsername}")
                                        onRequestRemoved()
                                    }
                                    result.onFailure { error ->
                                        android.util.Log.e("FREETIME_RECEIVE", "Failed to decline: ${error.message}")
                                        isDeclining = false
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("FREETIME_RECEIVE", "Exception: ${e.message}")
                                    isDeclining = false
                                }
                            }
                        },
                        enabled = !isAccepting && !isDeclining,
                        modifier = Modifier.height(36.dp).widthIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0000).copy(alpha = 0.6f),
                            disabledContainerColor = Color(0xFFFF0000).copy(alpha = 0.25f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isDeclining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                                color = CyberpunkTheme.White
                            )
                        } else {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = CyberpunkTheme.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    user: UserData,
    isRequested: Boolean,
    isLoading: Boolean,
    undoSeconds: Int? = null,
    onSendRequest: () -> Unit,
    onCancelRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Avatar + User Info + Action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User info
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), shape = CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val resolvedAvatarUrl = apiService.resolveAvatarUrl(user.avatar)
                        if (!resolvedAvatarUrl.isNullOrEmpty() && resolvedAvatarUrl != "null") {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(resolvedAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${user.username} avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                user.username.firstOrNull()?.uppercase() ?: "?",
                                color = CyberpunkTheme.CyberCyan,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // User details: name and username
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            user.name.ifEmpty { user.username },
                            color = getSearchResultUserColor(user.tags, user.isAdmin, user.isModerator, user.role),  // ✅ NEW: Apply color based on tags and role
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "@${user.username}",
                            color = getSearchResultUserColor(user.tags, user.isAdmin, user.isModerator, user.role),  // ✅ NEW: Apply color to username
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                
                // Action button
                Button(
                    onClick = if (undoSeconds != null) onCancelRequest else onSendRequest,
                    enabled = (!isRequested && !isLoading) || undoSeconds != null,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            undoSeconds != null -> Color(0xFF6B4040)
                            isRequested -> CyberpunkTheme.LightGray.copy(alpha = 0.3f)
                            else -> CyberpunkTheme.PrimaryPurple
                        },
                        disabledContainerColor = CyberpunkTheme.LightGray.copy(alpha = 0.2f)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyberpunkTheme.White,
                            strokeWidth = 2.dp
                        )
                    } else if (undoSeconds != null) {
                        Text(
                            "Cancel $undoSeconds",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.White
                        )
                    } else if (isRequested) {
                        Text(
                            "Requested",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.White
                        )
                    } else {
                        Text(
                            "Add",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.White
                        )
                    }
                }
            }
            
            // Bio (if exists)
            if (user.bio.isNotEmpty()) {
                Text(
                    user.bio,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 60.dp)
                )
            }
            
            // Tags display
            if (user.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    user.tags.take(5).forEach { tag ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f))
                                .border(
                                    width = 0.5.dp,
                                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.Transparent
                        ) {
                            Text(
                                text = "#$tag",
                                color = CyberpunkTheme.CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (user.tags.size > 5) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.Transparent
                        ) {
                            Text(
                                text = "+${user.tags.size - 5}",
                                color = CyberpunkTheme.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
