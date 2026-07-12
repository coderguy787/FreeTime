@file:Suppress("DEPRECATION")

package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.R
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.api.BadgeDetail

data class UserProfile(
    val id: String,
    val name: String,
    val username: String,
    val bio: String,
    val status: String = "Online",
    val lastSeen: String = "now",
    val avatarColor: Color = CyberpunkTheme.PrimaryPurple,
    val isVerified: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val sharedChats: Int = 5,
    val totalMessages: Int = 342,
    val commonGroups: Int = 3
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    profile: UserProfile = UserProfile(
        id = "1",
        name = "Alexandra Chen",
        username = "@alexchen",
        bio = "Designer | Developer | Coffee enthusiast ☕",
        status = "Online",
        lastSeen = "now",
        isVerified = true
    ),
    onNavigateBack: () -> Unit,
    onCall: () -> Unit,
    onVideo: () -> Unit,
    onMessage: () -> Unit
) {
    val context = LocalContext.current
    var isBlocked by remember { mutableStateOf(profile.isBlocked) }
    var isMuted by remember { mutableStateOf(profile.isMuted) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var userBadges by remember { mutableStateOf<List<BadgeDetail>>(emptyList()) }
    var isLoadingBadges by remember { mutableStateOf(true) }
    
    LaunchedEffect(profile.id) {
        isLoadingBadges = true
        val apiService = FreeTimeApiService(context)
        apiService.getUserBadges(profile.id).fold(
            onSuccess = { badges -> userBadges = badges },
            onFailure = { }
        )
        isLoadingBadges = false
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
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            null,
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyberpunkTheme.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            Icons.Default.MoreVert,
                            null,
                            tint = CyberpunkTheme.PrimaryPurple
                        )
                    }
                }
            }
            
            item {
                // Avatar Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            color = profile.avatarColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = profile.avatarColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    color = profile.avatarColor.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 3.dp,
                                    color = profile.avatarColor,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                profile.name.take(1),
                                color = profile.avatarColor,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Online indicator
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Color(0xFF00FF00),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 3.dp,
                                        color = CyberpunkTheme.Black,
                                        shape = CircleShape
                                    )
                                    .align(Alignment.BottomEnd)
                            )
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profile.name,
                                    color = CyberpunkTheme.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                if (profile.isVerified) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        null,
                                        tint = CyberpunkTheme.CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            // Badges Row - Simple Implementation
                            if (userBadges.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    userBadges.take(4).forEach { badge ->
                                        Text(badge.icon, fontSize = 18.sp)
                                    }
                                    if (userBadges.size > 4) {
                                        Text(
                                            "+${userBadges.size - 4}",
                                            color = CyberpunkTheme.LightGray,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                profile.username,
                                color = CyberpunkTheme.LightGray,
                                fontSize = 13.sp
                            )
                            
                            Text(
                                profile.bio,
                                color = CyberpunkTheme.LightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
            
            item {
                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            color = Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.Message,
                        label = "Message",
                        onClick = onMessage,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ActionButton(
                        icon = Icons.Default.Phone,
                        label = "Call",
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                        tint = Color(0xFF00FF00)
                    )
                    
                    ActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        onClick = onVideo,
                        modifier = Modifier.weight(1f),
                        tint = CyberpunkTheme.CyberCyan
                    )
                }
            }
            
            item {
                // Status Section
                InfoSection(title = "Status") {
                    StatusItem(
                        label = "Last seen",
                        value = profile.lastSeen,
                        icon = Icons.Default.Schedule
                    )
                    StatusItem(
                        label = "Status",
                        value = profile.status,
                        icon = Icons.Default.Circle,
                        iconTint = Color(0xFF00FF00)
                    )
                }
            }
            
            item {
                // Statistics Section
                InfoSection(title = "Statistics") {
                    StatisticItem(
                        label = "Messages",
                        value = profile.totalMessages.toString(),
                        icon = Icons.Default.Message
                    )
                    StatisticItem(
                        label = "Shared chats",
                        value = profile.sharedChats.toString(),
                        icon = Icons.Default.Group
                    )
                    StatisticItem(
                        label = "Common groups",
                        value = profile.commonGroups.toString(),
                        icon = Icons.Default.People
                    )
                }
            }
            
            item {
                // Settings Section
                SettingToggle(
                    title = "Mute notifications",
                    subtitle = "Disable notifications from this user",
                    isEnabled = isMuted,
                    onChange = { isMuted = it },
                    icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications
                )
            }
            
            item {
                SettingToggle(
                    title = "Block user",
                    subtitle = "Prevent this user from contacting you",
                    isEnabled = isBlocked,
                    onChange = { 
                        showBlockDialog = true
                    },
                    icon = Icons.Default.Block,
                    dangerTint = true
                )
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Show report dialog */ }
                        .background(Color(0xFF1a1f3a), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1f3a))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Report user",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFF4444)
                            )
                            Text(
                                "Report this user for abuse or harassment",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Icon(
                            Icons.Default.Flag,
                            null,
                            tint = Color(0xFFFF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            item {
                // Privacy Section
                ExpandableSection(
                    title = "Privacy Settings",
                    isExpanded = expandedSection == "privacy",
                    onToggle = {
                        expandedSection = if (expandedSection == "privacy") null else "privacy"
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyOption(
                            title = "See my online status",
                            enabled = true,
                            onToggle = {}
                        )
                        PrivacyOption(
                            title = "See when I'm typing",
                            enabled = false,
                            onToggle = {}
                        )
                        PrivacyOption(
                            title = "See my read receipts",
                            enabled = true,
                            onToggle = {}
                        )
                    }
                }
            }
            
            item {
                // Danger Zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            color = Color(0xFFFF1744).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFFF1744).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { /* Delete chat */ }
                        .padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = Color(0xFFFF1744),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Delete chat",
                            color = Color(0xFFFF1744),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CyberpunkTheme.PrimaryPurple
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = tint
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, tint)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            color = CyberpunkTheme.PrimaryPurple,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        content()
    }
}

@Composable
fun ColumnScope.StatusItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = CyberpunkTheme.CyberCyan
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(label, color = CyberpunkTheme.LightGray, fontSize = 13.sp)
        }
        Text(value, color = CyberpunkTheme.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ColumnScope.StatisticItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = Color(0xFF0F0F1E),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = CyberpunkTheme.LightGray, fontSize = 12.sp)
            Text(value, color = CyberpunkTheme.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dangerTint: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = if (dangerTint) Color(0xFFFF1744).copy(alpha = 0.3f) else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onChange(!isEnabled) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (dangerTint) Color(0xFFFF1744) else CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    title,
                    color = if (dangerTint) Color(0xFFFF1744) else CyberpunkTheme.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(subtitle, color = CyberpunkTheme.LightGray, fontSize = 12.sp)
            }
        }
        
        Switch(
            checked = isEnabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (dangerTint) Color(0xFFFF1744) else CyberpunkTheme.PrimaryPurple,
                checkedTrackColor = if (dangerTint) Color(0xFFFF1744).copy(alpha = 0.3f) else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = CyberpunkTheme.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = CyberpunkTheme.PrimaryPurple,
                modifier = Modifier.size(20.dp)
            )
        }
        
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
fun PrivacyOption(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = Color(0xFF0F0F1E),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggle(!enabled) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = CyberpunkTheme.White, fontSize = 13.sp)
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberpunkTheme.PrimaryPurple,
                checkedTrackColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
            )
        )
    }
}
