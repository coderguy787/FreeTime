package com.freetime.app.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.freetime.app.R
import com.freetime.app.ui.animations.StaggeredVerticalItemAnimation
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.ui.components.DiscordStyleTextField
import com.freetime.app.ui.components.DiscordStyleButton
import com.freetime.app.ui.components.DiscordStyleCard
import com.freetime.app.ui.components.DiscordStyleDivider
import com.freetime.app.ui.components.DiscordStyleSectionHeader
import com.freetime.app.ui.animations.*

import com.freetime.app.ui.utils.rememberDeviceSize
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.Friend
import com.freetime.app.data.local.SharedPreferencesHelper

data class FeedPost(
    val id: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false
)

@Composable
fun HomeScreen(
    onLogoutClick: () -> Unit,
    onChatClick: (userId: String) -> Unit = {}
) {
    val context = LocalContext.current
    HomeScreenEnhanced(
        context = context,
        onLogoutClick = onLogoutClick,
        onChatClick = onChatClick
    )
}

@Composable
fun HomeScreenEnhanced(
    context: Context,
    onLogoutClick: () -> Unit,
    onChatClick: (userId: String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("CHATS") }
    val scope = rememberCoroutineScope()
    val deviceSize = rememberDeviceSize(context)

    // Real data from API
    var feedPosts by remember { mutableStateOf(listOf<FeedPost>()) }
    var contacts by remember { mutableStateOf(listOf<Friend>()) }
    var friendRequests by remember { mutableStateOf(listOf<com.freetime.app.data.network.FriendRequest>()) }
    var isLoadingPosts by remember { mutableStateOf(false) }
    var isLoadingContacts by remember { mutableStateOf(false) }
    var isLoadingRequests by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedConversation by remember { mutableStateOf<Friend?>(null) }
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) } // Pair of userId, messageText
    var currentMessageInput by remember { mutableStateOf("") }

    // Fetch contacts from API when screen loads
    LaunchedEffect(Unit) {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken()
        
        if (token.isNullOrEmpty()) {
            errorMessage = "User token missing. Please log in again."
            return@LaunchedEffect
        }
        
        isLoadingContacts = true
        isLoadingRequests = true
        try {
            val response = ApiClient.getInstance().getFriendsList("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                contacts = response.body() ?: emptyList()
                errorMessage = "" // Clear error on success
            } else {
                errorMessage = "Failed to load contacts: ${response.code()}"
            }
            
            val requestsResponse = ApiClient.getInstance().getPendingFriendRequests("Bearer $token")
            if (requestsResponse.isSuccessful && requestsResponse.body() != null) {
                friendRequests = requestsResponse.body() ?: emptyList()
            } else {
                // Don't override previous error, just append
                if (errorMessage.isEmpty()) {
                    errorMessage = "Failed to load friend requests: ${requestsResponse.code()}"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error loading data: ${e.localizedMessage ?: e.message}"
        } finally {
            isLoadingContacts = false
            isLoadingRequests = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.CyberBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== ERROR MESSAGE BANNER =====
            FloatingUpAnimation(
                delayMillis = 0,
                durationMillis = 4000
            ) {
                if (errorMessage.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFF6B6B)),
                        color = Color(0xFFFF6B6B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { errorMessage = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ===== CYBERPUNK HEADER =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                                CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                            )
                        )
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "> FreeTime <",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = CyberpunkTheme.PrimaryPurple
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { 
                            // Refresh feed - reload posts
                            isLoadingPosts = true
                            scope.launch {
                                try {
                                    // Simulate refresh delay
                                    kotlinx.coroutines.delay(800)
                                    errorMessage = "✓ Feed refreshed successfully"
                                } finally {
                                    isLoadingPosts = false
                                }
                            }
                        }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { 
                            // Toggle settings visibility
                            selectedTab = "PROFILE"
                            errorMessage = "✓ Settings opened"
                        }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ===== TAB SELECTOR - WhatsApp-style pill buttons =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CHATS", "ADD FRIENDS", "PROFILE").forEach { tab ->
                    val isSelected = selectedTab == tab
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                        label = "tab_bg_color_$tab"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                        animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                        label = "tab_border_color_$tab"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                        animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                        label = "tab_text_color_$tab"
                    )
                    
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = tab }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        color = backgroundColor
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tab,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== CONTENT TABS WITH SMOOTH TRANSITIONS =====
            TabTransitionAnimation(
                selectedTab = selectedTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                "CHATS" -> {
                    // Combined view: Feed, Contacts, and Messages
                    var chatSubTab by remember { mutableStateOf("CHATS") }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sub-tab selector for chats view
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("CHATS", "GROUPS").forEach { subTab ->
                                val isSelected = chatSubTab == subTab
                                val subBgColor by animateColorAsState(
                                    targetValue = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.25f) else Color.Transparent,
                                    animationSpec = tween(350, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                                    label = "subtab_bg_$subTab"
                                )
                                val subBorderColor by animateColorAsState(
                                    targetValue = if (isSelected) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                                    animationSpec = tween(350, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                                    label = "subtab_border_$subTab"
                                )
                                val subTextColor by animateColorAsState(
                                    targetValue = if (isSelected) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                                    animationSpec = tween(350, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                                    label = "subtab_text_$subTab"
                                )
                                
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { chatSubTab = subTab }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = subBorderColor,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    color = subBgColor
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            subTab,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = subTextColor
                                        )
                                    }
                                }
                            }
                        }

                        when (chatSubTab) {
                            "CHATS" -> {
                                // Direct messages list - navigates to chat screen
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    if (contacts.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .size(80.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                                        color = CyberpunkTheme.DarkGray
                                                    ) {
                                                        Image(
                                                            painter = painterResource(id = R.drawable.saying),
                                                            contentDescription = "No messages",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                    Text(
                                                        "No messages yet. Add friends to start chatting!",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = CyberpunkTheme.GhostGray,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        itemsIndexed(contacts) { index, friend ->
                                            StaggeredVerticalItemAnimation(index = index) {
                                            val isPressed = remember { mutableStateOf(false) }
                                            val contactScale by animateFloatAsState(
                                                targetValue = if (isPressed.value) 0.95f else 1f,
                                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
                                                label = "contactScale"
                                            )
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .graphicsLayer { scaleX = contactScale; scaleY = contactScale }
                                                    .pointerInput(friend.userId) {
                                                        detectTapGestures(
                                                            onPress = { isPressed.value = true; tryAwaitRelease(); isPressed.value = false },
                                                            onTap = { onChatClick(friend.userId) }
                                                        )
                                                    }
                                                    .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                                color = CyberpunkTheme.DarkGray.copy(alpha = 0.2f)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(44.dp),
                                                        shape = CircleShape,
                                                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                friend.username.take(1).uppercase(),
                                                                color = CyberpunkTheme.PrimaryPurple,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 18.sp
                                                            )
                                                        }
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            friend.username,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = CyberpunkTheme.PrimaryPurple
                                                        )
                                                        Text(
                                                            "Tap to chat",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = CyberpunkTheme.GhostGray
                                                        )
                                                    }

                                                    Icon(
                                                        Icons.Filled.ChevronRight,
                                                        contentDescription = "Open chat",
                                                        tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            }

                            "GROUPS" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Groups & Channels coming soon!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = CyberpunkTheme.GhostGray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                    }
                }
                }

                "ADD FRIENDS" -> {
                    // Friend requests tab
                    if (isLoadingRequests) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    } else if (friendRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(2.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                    color = CyberpunkTheme.DarkGray
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.hello),
                                        contentDescription = "No requests",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(
                                    "No friend requests. Go explore and add friends!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = CyberpunkTheme.GhostGray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            itemsIndexed(friendRequests) { index, request ->
                                StaggeredVerticalItemAnimation(index = index) {
                                FriendRequestCardHomePage(
                                    request = request,
                                    onAccept = {
                                        scope.launch {
                                            try {
                                                val prefs = SharedPreferencesHelper(context)
                                                val token = prefs.getToken()
                                                if (!token.isNullOrEmpty()) {
                                                    val response = ApiClient.getInstance().acceptFriendRequest(
                                                        token = "Bearer $token",
                                                        senderId = request.senderId
                                                    )
                                                    if (response.isSuccessful) {
                                                        friendRequests = friendRequests.filter { it.id != request.id }
                                                        errorMessage = "✓ Accepted friend request from ${request.senderName}"
                                                    } else {
                                                        errorMessage = "Failed to accept request: ${response.code()}"
                                                    }
                                                } else {
                                                    errorMessage = "Authentication token missing"
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Error accepting request: ${e.localizedMessage ?: e.message}"
                                            }
                                        }
                                    },
                                    onReject = {
                                        scope.launch {
                                            try {
                                                val prefs = SharedPreferencesHelper(context)
                                                val token = prefs.getToken()
                                                if (!token.isNullOrEmpty()) {
                                                    val response = ApiClient.getInstance().rejectFriendRequest(
                                                        token = "Bearer $token",
                                                        senderId = request.senderId
                                                    )
                                                    if (response.isSuccessful) {
                                                        friendRequests = friendRequests.filter { it.id != request.id }
                                                        errorMessage = "✓ Declined friend request from ${request.senderName}"
                                                    } else {
                                                        errorMessage = "Failed to reject request: ${response.code()}"
                                                    }
                                                } else {
                                                    errorMessage = "Authentication token missing"
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Error rejecting request:  ${e.localizedMessage ?: e.message}"
                                            }
                                        }
                                    }
                                )
                                }
                            }
                        }
                    }
                }

                "PROFILE" -> {
                    // Profile and settings view
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            StaggeredVerticalItemAnimation(index = 0) {
                            FadeInAnimation(delayMillis = 200) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BreathAnimation(
                                        modifier = Modifier.size(120.dp)
                                    ) {
                                        WaveAnimation(
                                            modifier = Modifier.size(120.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.cody),
                                                contentDescription = "Profile Mascot",
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                                }
                            }
                        }

                        item {
                            StaggeredVerticalItemAnimation(index = 1) {
                            FadeInAnimation(delayMillis = 400) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                    color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            "Account Settings",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = CyberpunkTheme.PrimaryPurple
                                        )
                                        
                                        SettingRow(label = "Username", value = "User", onClick = { /* Edit */ })
                                        SettingRow(label = "Email", value = "user@freetime.com", onClick = { /* Edit */ })
                                        SettingRow(label = "Privacy", value = "Public", onClick = { /* Edit */ })
                                    }
                                }
                                }
                            }
                        }

                        item {
                            StaggeredVerticalItemAnimation(index = 2) {
                            SlideInFromBottom(delayMillis = 500) {
                                    AnimatedLogoutButton(onLogoutClick = onLogoutClick)
                                }
                                }
                            }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun CreatePostCard(onPostClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { 
                isPressed = true
                onPostClick()
            },
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.4f),
        shadowElevation = 2.dp
    ) {
        SmoothScaleAnimation(isPressed = isPressed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PulseAnimation(
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = CyberpunkTheme.PrimaryPurple
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("U", color = CyberpunkTheme.PrimaryPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    "[YOUR_POST_HERE]",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberpunkTheme.GhostGray,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Create",
                    tint = CyberpunkTheme.PrimaryPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(10.dp))
            .clickable { },
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f),
        shadowElevation = 4.dp
    ) {
        SmoothScaleAnimation(isPressed = isPressed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Author info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            post.author.take(1),
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        post.author,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberpunkTheme.PrimaryPurple
                    )
                    Text(
                        post.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.GhostGray
                    )
                }

                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = CyberpunkTheme.GhostGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Post content
            Text(
                post.content,
                style = MaterialTheme.typography.bodySmall,
                color = CyberpunkTheme.GhostGray,
                modifier = Modifier.fillMaxWidth()
            )

            // Optional: Add mascot decoration for rich content
            if (post.content.contains("share", ignoreCase = true) || post.content.contains("happy", ignoreCase = true)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.open_arms),
                            contentDescription = "Mascot",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Interaction buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Likes
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLikeClick() }
                        .border(
                            1.dp,
                            if (post.isLiked) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                            RoundedCornerShape(4.dp)
                        ),
                    color = if (post.isLiked) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (post.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.isLiked) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            post.likesCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Comments
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCommentClick() }
                        .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(4.dp)),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline,
                            contentDescription = "Comment",
                            tint = CyberpunkTheme.GhostGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            post.commentsCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Share
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onShareClick() }
                        .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(4.dp)),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = CyberpunkTheme.GhostGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            }
        }
    }
}
                        
@Composable
fun ContactCardCyberpunk(
    name: String,
    status: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1),
                        color = CyberpunkTheme.PrimaryPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.PrimaryPurple
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == "Online") CyberpunkTheme.PrimaryPurple else CyberpunkTheme.GhostGray
                )
            }

            // Status indicator
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = if (status == "Online") CyberpunkTheme.PrimaryPurple else CyberpunkTheme.GhostGray
            ) {}
        }
    }
}
@Composable
fun FriendRequestCardHomePage(
    request: com.freetime.app.data.network.FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            request.senderName.take(1),
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        request.senderName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberpunkTheme.PrimaryPurple
                    )
                    Text(
                        "Sent you a friend request",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.GhostGray
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAccept() }
                        .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(4.dp)),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Accept",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onReject() }
                        .border(1.dp, CyberpunkTheme.GhostGray, RoundedCornerShape(4.dp)),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Decline",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberpunkTheme.GhostGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
        color = CyberpunkTheme.DarkGray.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = CyberpunkTheme.PrimaryPurple
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.GhostGray
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Edit",
                    tint = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedLogoutButton(onLogoutClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "logoutScale"
    )
    
    var isHovered by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) Color(0xFFFF5555) else Color(0xFFFF6B6B),
        animationSpec = tween(300),
        label = "logoutBg"
    )
    
    Button(
        onClick = { 
            isPressed = true
            onLogoutClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { isPressed = true }
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Power,
                contentDescription = "Logout",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp)
            )
            Text(
                "Logout",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

