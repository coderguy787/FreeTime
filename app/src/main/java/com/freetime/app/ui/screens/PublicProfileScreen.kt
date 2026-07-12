package com.freetime.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.BadgeDetail
import com.freetime.app.api.UserProfile
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.data.local.SharedPreferencesHelper
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

// ✅ NEW: Helper function to get profile name color based on tags and role
fun getProfileNameColor(
    tags: List<String>,
    role: String? = null
): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Magenta
        tags.contains("VIP") -> Color(0xFFFFFF00)  // Yellow
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Cyan
        role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)  // Red
        role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)  // Orange
        else -> CyberpunkTheme.White
    }
}
@Composable
fun PublicProfileScreen(
    userId: String,
    onBackClick: () -> Unit = {},
    onSendFriendRequest: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val apiService = remember { FreeTimeApiService(context) }
    val scope = rememberCoroutineScope()
    val prefs = SharedPreferencesHelper(context)
    
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var pronouns by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf(listOf<String>()) }  // NEW: Tags display
    var role by remember { mutableStateOf<String?>(null) }  // ✅ NEW: Store role for color display
    var badges by remember { mutableStateOf(listOf<BadgeDetail>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCurrentUser by remember { mutableStateOf(false) }
    var friendRequestSent by remember { mutableStateOf(false) }
    var isAlreadyFriend by remember { mutableStateOf(false) }  // NEW: Track existing friendship
    var showRemoveDialog by remember { mutableStateOf(false) }  // NEW: Confirm remove friend
    var isProcessingAction by remember { mutableStateOf(false) }  // NEW: Loading state for actions
    
    // Load profile and check friendship status on mount
    LaunchedEffect(userId) {
        scope.launch {
            try {
                val result = apiService.getPublicUserProfile(userId)
                result.onSuccess { profile ->
                    displayName = profile.displayName
                    username = profile.username
                    bio = profile.bio
                    pronouns = profile.pronouns
                    avatar = profile.avatar
                    tags = profile.tags ?: emptyList()  // NEW: Load tags
                    role = profile.role  // ✅ NEW: Load role
                    badges = profile.badges
                    isCurrentUser = profile.isCurrentUser
                }.onFailure {
                    displayName = "User Not Found"
                }
                
                // NEW: Check if already friends
                if (!isCurrentUser) {
                    try {
                        val token = prefs.getToken() ?: ""
                        val friendsResult = apiService.getFriends("Bearer $token")
                        friendsResult.onSuccess { friendsList ->
                            isAlreadyFriend = friendsList.any { it.userId == userId }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PublicProfileScreen", "Error checking friendship: ${e.message}")
                    }
                }
                
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }
    
    // NEW: Function to remove friend
    val removeFriend: () -> Unit = {
        isProcessingAction = true
        scope.launch {
            try {
                val token = prefs.getToken() ?: ""
                // Remove friend by user ID
                val deleteResponse = apiService.removeFriend(userId, "Bearer $token")
                if (deleteResponse.isSuccess) {
                    android.util.Log.d("PublicProfileScreen", "Friend removed successfully")
                    isAlreadyFriend = false
                    // Also delete chat history
                    try {
                        val chatDeleteResponse = apiService.deleteChatHistory(userId)
                        android.util.Log.d("PublicProfileScreen", "Chat history deleted")
                    } catch (e: Exception) {
                        android.util.Log.e("PublicProfileScreen", "Error deleting chat: ${e.message}")
                    }
                    showRemoveDialog = false
                } else {
                    android.util.Log.e("PublicProfileScreen", "Failed to remove friend: ${deleteResponse.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PublicProfileScreen", "Error removing friend: ${e.message}")
            } finally {
                isProcessingAction = false
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.DarkBlack)
    ) {
        // Header with background gradient
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                            CyberpunkTheme.DarkBlack
                        ),
                        endY = 300f
                    )
                )
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberpunkTheme.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    "Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CyberpunkTheme.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (!isCurrentUser) {
                    IconButton(onClick = { /* menu */ }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }
            
            if (!isLoading) {
                // Avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp)
                ) {
                    val resolvedAvatarUrl = apiService.resolveAvatarUrl(avatar)
                    
                    if (!resolvedAvatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(resolvedAvatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(2.dp, CyberpunkTheme.PrimaryPurple, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    color = CyberpunkTheme.PrimaryPurple,
                                    shape = CircleShape
                                )
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (displayName.ifEmpty { "U" }).firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberpunkTheme.White
                            )
                        }
                    }
                }
                
                // Display Name
                Text(
                    displayName.ifEmpty { "User" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = getProfileNameColor(tags, role),  // ✅ NEW: Apply color based on tags and role
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                // Username
                Text(
                    "@${username.ifEmpty { "unknown" }}",
                    fontSize = 14.sp,
                    color = CyberpunkTheme.LightGray,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
                
                // Pronouns/Tags
                if (pronouns.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = CyberpunkTheme.DarkGray
                    ) {
                        Text(
                            pronouns,
                            fontSize = 12.sp,
                            color = CyberpunkTheme.PrimaryPurple,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                
                // NEW: Display tags
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp)
                            .wrapContentWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.take(5).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    "#$tag",
                                    fontSize = 11.sp,
                                    color = CyberpunkTheme.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Content below header
        if (!isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bio
                if (bio.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = CyberpunkTheme.DarkGray
                    ) {
                        Text(
                            bio,
                            fontSize = 14.sp,
                            color = CyberpunkTheme.White,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // Badges
                if (badges.isNotEmpty()) {
                    Column {
                        Text(
                            "Badges",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberpunkTheme.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = CyberpunkTheme.DarkGray
                        ) {
                            BadgesGrid(badges = badges)
                        }
                    }
                }
                
                // Friend Request/Friend Management Buttons
                if (!isCurrentUser) {
                    when {
                        isAlreadyFriend -> {
                            // NEW: Show Remove and Block buttons if already friends
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Remove Friend Button
                                ElevatedButton(
                                    onClick = { showRemoveDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    enabled = !isProcessingAction,
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = Color(0xFFFF6B6B)
                                    )
                                ) {
                                    Icon(Icons.Filled.PersonRemove, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        friendRequestSent -> {
                            // Request already sent
                            ElevatedButton(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = false,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    disabledContainerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Request Sent", fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            // Send Friend Request Button
                            ElevatedButton(
                                onClick = {
                                    friendRequestSent = true
                                    onSendFriendRequest(userId)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = CyberpunkTheme.PrimaryPurple
                                )
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Friend Request", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // NEW: Remove Friend Confirmation Dialog
                if (showRemoveDialog) {
                    AlertDialog(
                        onDismissRequest = { showRemoveDialog = false },
                        title = { Text("Remove Friend") },
                        text = { Text("Are you sure you want to remove $displayName as a friend? Your chat history will also be deleted.") },
                        dismissButton = {
                            TextButton(onClick = { showRemoveDialog = false }, enabled = !isProcessingAction) {
                                Text("Cancel")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = removeFriend, enabled = !isProcessingAction) {
                                Text("Remove", color = Color(0xFFFF6B6B))
                            }
                        }
                    )
                }
            }
        }
        
        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CyberpunkTheme.PrimaryPurple
            )
        }
    }
}

/**
 * Badges Grid Component
 */
@Composable
fun BadgesGrid(badges: List<BadgeDetail>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        var rows = (badges.size + 3) / 4  // 4 badges per row
        
        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (rowIndex < rows - 1) 12.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) { colIndex ->
                    val badgeIndex = rowIndex * 4 + colIndex
                    if (badgeIndex < badges.size) {
                        BadgeItem(badges[badgeIndex])
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Individual Badge Display
 */
@Composable
fun BadgeItem(badge: BadgeDetail) {
    // Parse hex color or use theme color
    val badgeColor = try {
        Color(android.graphics.Color.parseColor(badge.color))
    } catch (e: Exception) {
        CyberpunkTheme.PrimaryPurple
    }
    
    Surface(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = badgeColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, badgeColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                badge.icon,
                fontSize = 24.sp
            )
            Text(
                badge.name.take(3),
                fontSize = 8.sp,
                color = CyberpunkTheme.White,
                maxLines = 1
            )
        }
    }
}
