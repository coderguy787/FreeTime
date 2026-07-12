package com.freetime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.components.CyberpunkTheme


data class NotificationSetting(val label: String, val description: String, val key: String, var isEnabled: Boolean = true)

@Composable
fun NotificationSettingsScreenEnhanced(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    val prefsHelper = remember { SharedPreferencesHelper(context) }
    
    var settings by remember {
        mutableStateOf(listOf(
            NotificationSetting("MESSAGE_ALERTS", "Get notified on new messages", "notify_messages", true),
            NotificationSetting("FRIEND_REQUESTS", "Friend request notifications", "notify_friend_requests", true),
            NotificationSetting("GROUP_UPDATES", "Group activity updates", "notify_group_updates", true),
            NotificationSetting("SOUND", "Play notification sound", "notify_sound", true),
            NotificationSetting("VIBRATION", "Vibration feedback", "notify_vibration", true)
        ))
    }
    
    // Load settings from SharedPreferences on first composition
    LaunchedEffect(Unit) {
        settings = settings.map { setting ->
            val savedValue = when (setting.key) {
                "notify_messages" -> prefsHelper.isNotifyMessagesEnabled()
                "notify_friend_requests" -> prefsHelper.isNotifyFriendRequestsEnabled()
                "notify_group_updates" -> prefsHelper.isNotifyGroupUpdatesEnabled()
                "notify_sound" -> prefsHelper.isNotifySoundEnabled()
                "notify_vibration" -> prefsHelper.isNotifyVibrationEnabled()
                else -> setting.isEnabled
            }
            setting.copy(isEnabled = savedValue)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(CyberpunkTheme.CyberBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f)),
                color = Color.Transparent) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(20.dp))
                    }
                    Text("> NOTIFICATION_SETTINGS <", style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                        color = CyberpunkTheme.PrimaryPurple)
                }
            }

            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                items(settings.size) { index ->
                    val setting = settings[index]
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CyberpunkTheme.DarkGray, RoundedCornerShape(8.dp)),
                        color = CyberpunkTheme.DarkGray.copy(alpha = 0.3f)) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = setting.label,
                                tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(setting.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CyberpunkTheme.PrimaryPurple)
                                Text(setting.description, style = MaterialTheme.typography.labelSmall,
                                    color = CyberpunkTheme.GhostGray, fontSize = 10.sp)
                            }
                            Surface(modifier = Modifier
                                .size(width = 48.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val newValue = !setting.isEnabled
                                    settings = settings.mapIndexed { i, s ->
                                        if (i == index) s.copy(isEnabled = newValue) else s
                                    }
                                    // Save to SharedPreferences immediately
                                    when (setting.key) {
                                        "notify_messages" -> prefsHelper.setNotifyMessages(newValue)
                                        "notify_friend_requests" -> prefsHelper.setNotifyFriendRequests(newValue)
                                        "notify_group_updates" -> prefsHelper.setNotifyGroupUpdates(newValue)
                                        "notify_sound" -> prefsHelper.setNotifySound(newValue)
                                        "notify_vibration" -> prefsHelper.setNotifyVibration(newValue)
                                    }
                                    android.util.Log.d("NotificationSettings", "💾 ${setting.key} saved: $newValue")
                                }
                                .border(1.dp, if (setting.isEnabled) CyberpunkTheme.CyberCyan else CyberpunkTheme.DarkGray,
                                    RoundedCornerShape(14.dp)),
                                color = if (setting.isEnabled) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f) else Color.Transparent) {
                                Box(modifier = Modifier.fillMaxSize(),
                                    contentAlignment = if (setting.isEnabled) Alignment.CenterEnd else Alignment.CenterStart) {
                                    Surface(modifier = Modifier
                                        .size(22.dp)
                                        .padding(2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (setting.isEnabled) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class BlockedUser(val id: String, val name: String, val blockedDate: String)

@Composable
fun BlockedUsersScreenEnhanced(onBackClick: () -> Unit = {}) {
    var blockedUsers by remember {
        mutableStateOf(listOf(
            BlockedUser("1", "SPAM_BOT_001", "1 week ago"),
            BlockedUser("2", "PHISHING_USER", "2 weeks ago"),
            BlockedUser("3", "HARASSER_ACCOUNT", "3 days ago")
        ))
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(CyberpunkTheme.CyberBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f)),
                color = Color.Transparent) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = CyberpunkTheme.PrimaryPurple, modifier = Modifier.size(20.dp))
                    }
                    Text("> BLOCKED_USERS <", style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                        color = CyberpunkTheme.PrimaryPurple, modifier = Modifier.weight(1f))
                    Text("${blockedUsers.size}", style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.PrimaryPurple)
                }
            }

            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)) {
                items(blockedUsers) { user ->
                    Surface(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        color = Color(0xFFFF6B6B).copy(alpha = 0.08f)) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonOff, contentDescription = "Blocked",
                                tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF6B6B))
                                Text("Blocked ${user.blockedDate}", style = MaterialTheme.typography.labelSmall,
                                    color = CyberpunkTheme.GhostGray, fontSize = 10.sp)
                            }
                            Surface(modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    blockedUsers = blockedUsers.filter { it.id != user.id }
                                }
                                .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(6.dp)) {
                                Text("UNBLOCK", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
    }
}


