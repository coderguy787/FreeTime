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
import androidx.compose.foundation.verticalScroll
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

// public profile view
fun getProfileNameColor(
    tags: List<String>,
    role: String? = null
): Color {
    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)
        tags.contains("VIP") -> Color(0xFFFFFF00)
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
        role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)
        role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)
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
    var banner by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var role by remember { mutableStateOf<String?>(null) }
    var badges by remember { mutableStateOf(listOf<BadgeDetail>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCurrentUser by remember { mutableStateOf(false) }
    var friendRequestSent by remember { mutableStateOf(false) }
    var isAlreadyFriend by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var isProcessingAction by remember { mutableStateOf(false) }

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
                    banner = profile.banner
                    tags = profile.tags ?: emptyList()
                    role = profile.role
                    badges = profile.badges
                    isCurrentUser = profile.isCurrentUser
                }.onFailure {
                    displayName = "User Not Found"
                }

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

    val removeFriend: () -> Unit = {
        isProcessingAction = true
        scope.launch {
            try {
                val token = prefs.getToken() ?: ""
                val deleteResponse = apiService.removeFriend(userId, "Bearer $token")
                if (deleteResponse.isSuccess) {
                    android.util.Log.d("PublicProfileScreen", "Friend removed successfully")
                    isAlreadyFriend = false
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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

                Spacer(modifier = Modifier.width(40.dp))
            }

            if (!isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val bannerColor = when {
                        tags.contains("OWNER") -> Color(0xFFFF00FF)
                        tags.contains("VIP") -> Color(0xFFFFFF00)
                        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
                        role?.uppercase() == "ADMIN" -> Color(0xFFFF0000)
                        role?.uppercase() == "MODERATOR" -> Color(0xFFFF8C00)
                        else -> CyberpunkTheme.PrimaryPurple
                    }

                    val resolvedBannerUrl = if (!banner.isNullOrEmpty()) {
                        apiService.resolveAvatarUrl(banner)
                    } else null

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        if (!resolvedBannerUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(resolvedBannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.4f)
                                            )
                                        )
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                bannerColor.copy(alpha = 0.7f),
                                                bannerColor.copy(alpha = 0.15f)
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(y = (-50).dp)
                            .padding(start = 12.dp)
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
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, CyberpunkTheme.DarkBlack, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(
                                        color = bannerColor,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .border(4.dp, CyberpunkTheme.DarkBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (displayName.ifEmpty { "U" }).firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberpunkTheme.White
                                )
                            }
                        }

                        if (isAlreadyFriend || isCurrentUser) {
                            val statusColor = Color(0xFF00FF88)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp),
                                shape = CircleShape,
                                color = CyberpunkTheme.DarkBlack
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(4.dp)
                                        .background(statusColor, CircleShape)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.offset(y = (-36).dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                displayName.ifEmpty { "User" },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = getProfileNameColor(tags, role)
                            )
                            if (pronouns.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyberpunkTheme.DarkGray
                                ) {
                                    Text(
                                        pronouns,
                                        fontSize = 11.sp,
                                        color = CyberpunkTheme.LightGray,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            "@${username.ifEmpty { "unknown" }}",
                            fontSize = 14.sp,
                            color = CyberpunkTheme.LightGray,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tags.take(5).forEach { tag ->
                                    val tagColor = when (tag.uppercase()) {
                                        "OWNER" -> Color(0xFFFF00FF)
                                        "VIP" -> Color(0xFFFFFF00)
                                        "BETA TESTER" -> Color(0xFF00FFFF)
                                        else -> CyberpunkTheme.PrimaryPurple
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = tagColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            "#$tag",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tagColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = CyberpunkTheme.DarkGray, thickness = 2.dp)

                    if (bio.isNotEmpty()) {
                        Column {
                            Text(
                                "ABOUT ME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberpunkTheme.LightGray,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    bio,
                                    fontSize = 14.sp,
                                    color = CyberpunkTheme.White,
                                    modifier = Modifier.padding(14.dp),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    if (badges.isNotEmpty()) {
                        Column {
                            Text(
                                "BADGES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberpunkTheme.LightGray,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = CyberpunkTheme.DarkGray.copy(alpha = 0.5f)
                            ) {
                                BadgesGrid(badges = badges)
                            }
                        }
                    }

                    if (!isCurrentUser) {
                        when {
                            isAlreadyFriend -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showRemoveDialog = true },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isProcessingAction,
                                        border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                                    ) {
                                        Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Remove Friend", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            friendRequestSent -> {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50),
                                        disabledContainerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Request Sent", fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        friendRequestSent = true
                                        onSendFriendRequest(userId)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send Friend Request", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

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

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CyberpunkTheme.PrimaryPurple
            )
        }
    }
}

@Composable
fun BadgesGrid(badges: List<BadgeDetail>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        var rows = (badges.size + 3) / 4

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

@Composable
fun BadgeItem(badge: BadgeDetail) {
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
