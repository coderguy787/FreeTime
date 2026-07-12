package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.notifications.InAppNotification
import com.freetime.app.notifications.InAppNotificationStore
import com.freetime.app.ui.components.CyberpunkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FriendRequestPopup(
    apiService: FreeTimeApiService,
    onNavigateToFriendRequests: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val shownIds = remember { mutableStateMapOf<String, Boolean>() }
    val initialIdSet = remember { InAppNotificationStore.notifications.map { it.id }.toSet() }
    var dismissCounter by remember { mutableIntStateOf(0) }

    var currentPopup by remember { mutableStateOf<InAppNotification?>(null) }

    LaunchedEffect(InAppNotificationStore.notifications.size, dismissCounter) {
        val latest = InAppNotificationStore.notifications.firstOrNull { n ->
            n.type == "friendRequest" && n.id !in shownIds && n.id !in initialIdSet
        }
        if (latest != null) {
            currentPopup = latest
            delay(8000)
            if (currentPopup?.id == latest.id) {
                shownIds[latest.id] = true
                currentPopup = null
                dismissCounter++
            }
        }
    }

    AnimatedVisibility(
        visible = currentPopup != null,
        enter = slideInVertically { -it } + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically { -it } + fadeOut(animationSpec = tween(300)),
        modifier = Modifier
    ) {
        currentPopup?.let { notif ->
            FriendRequestPopupCard(
                notification = notif,
                onAccept = {
                    shownIds[notif.id] = true
                    currentPopup = null
                    dismissCounter++
                    scope.launch {
                        apiService.acceptFriendRequest(notif.senderId)
                        InAppNotificationStore.removeByTypeAndSender("friendRequest", notif.senderId)
                    }
                },
                onDecline = {
                    shownIds[notif.id] = true
                    currentPopup = null
                    dismissCounter++
                    scope.launch {
                        apiService.declineFriendRequest(notif.senderId)
                        InAppNotificationStore.removeByTypeAndSender("friendRequest", notif.senderId)
                    }
                },
                onNavigate = onNavigateToFriendRequests
            )
        }
    }
}

@Composable
private fun FriendRequestPopupCard(
    notification: InAppNotification,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onNavigate: () -> Unit
) {
    var isAccepting by remember { mutableStateOf(false) }
    var isDeclining by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            ),
        color = Color(0xFF0D0D1A),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.25f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            notification.title.firstOrNull()?.uppercase() ?: "?",
                            color = CyberpunkTheme.CyberCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            notification.title,
                            color = CyberpunkTheme.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            notification.description,
                            color = CyberpunkTheme.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            isAccepting = true
                            onAccept()
                        },
                        enabled = !isAccepting && !isDeclining,
                        modifier = Modifier.height(32.dp).widthIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF00).copy(alpha = 0.7f),
                            disabledContainerColor = Color(0xFF00FF00).copy(alpha = 0.3f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAccepting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = CyberpunkTheme.Black
                            )
                        } else {
                            Icon(Icons.Default.Check, "Accept", modifier = Modifier.size(14.dp), tint = CyberpunkTheme.Black)
                        }
                    }

                    Button(
                        onClick = {
                            isDeclining = true
                            onDecline()
                        },
                        enabled = !isAccepting && !isDeclining,
                        modifier = Modifier.height(32.dp).widthIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0000).copy(alpha = 0.6f),
                            disabledContainerColor = Color(0xFFFF0000).copy(alpha = 0.25f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isDeclining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = CyberpunkTheme.White
                            )
                        } else {
                            Icon(Icons.Default.Close, "Decline", modifier = Modifier.size(14.dp), tint = CyberpunkTheme.White)
                        }
                    }
                }
            }

            Text(
                "Tap to view all requests",
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onNavigate() }
            )
        }
    }
}
