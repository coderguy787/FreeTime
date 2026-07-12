package com.freetime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import coil.compose.AsyncImage

/**
 * Enhanced user profile card with all features:
 * - Profile banner
 * - Avatar
 * - User info (name, username, bio)
 * - Badges
 * - Tags
 * - Status
 * - Action buttons (block, report, message)
 */
@Composable
fun EnhancedProfileCard(
    username: String,
    displayName: String,
    bio: String,
    avatarUrl: String?,
    bannerUrl: String?,
    badges: List<UserBadge> = emptyList(),
    tags: List<String> = emptyList(),
    status: String = "Available",
    isCurrentUser: Boolean = false,
    onBlockUser: (() -> Unit)? = null,
    onSendMessage: (() -> Unit)? = null,
    onAddFriend: (() -> Unit)? = null
) {
    var showBlockDialog by remember { mutableStateOf(false) }
    var isBlocking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(CyberpunkTheme.Black)
    ) {
        // Profile Banner
        if (!bannerUrl.isNullOrEmpty()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Profile Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(CyberpunkTheme.DarkGray),
                contentScale = ContentScale.Crop
            )
        } else {
            // Gradient banner fallback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.3f),
                                CyberpunkTheme.DarkBlack
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
            )
        }

        // Avatar and basic info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val apiService = remember { com.freetime.app.api.FreeTimeApiService(context) }
            val resolvedAvatarUrl = apiService.resolveAvatarUrl(avatarUrl)
            
            // Avatar (positioned over banner with overlap)
            AsyncImage(
                model = resolvedAvatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(CyberpunkTheme.DarkGray)
                    .border(3.dp, CyberpunkTheme.PrimaryMagenta, CircleShape),
                contentScale = ContentScale.Crop
            )

            // Display name and username
            Text(
                displayName,
                color = CyberpunkTheme.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "@$username",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 13.sp
                )

                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                "Available" -> Color(0xFF51CF66)
                                "Busy" -> Color(0xFFFFD93D)
                                "DND" -> Color(0xFFFF6B6B)
                                else -> Color(0xFF909090)
                            }
                        )
                )
                Text(
                    status,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 12.sp
                )
            }

            // Bio
            if (bio.isNotEmpty()) {
                Text(
                    bio,
                    color = CyberpunkTheme.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = CyberpunkTheme.DarkGray
        )

        // Badges section
        if (badges.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UserBadgesGrid(badges)
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = CyberpunkTheme.DarkGray
            )
        }

        // Tags section
        if (tags.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Interests & Skills",
                    color = CyberpunkTheme.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.take(8).forEach { tag ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                            color = Color(0xFF00FFFF).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "#$tag",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color(0xFF00FFFF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (tags.size > 8) {
                        Text(
                            "+${tags.size - 8} more",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = CyberpunkTheme.DarkGray
            )
        }

        // Action buttons
        if (!isCurrentUser) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Message button
                if (onSendMessage != null) {
                    ElevatedButton(
                        onClick = onSendMessage,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = CyberpunkTheme.PrimaryMagenta
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message", fontSize = 12.sp)
                    }
                }

                // Add friend button
                if (onAddFriend != null) {
                    ElevatedButton(
                        onClick = onAddFriend,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = CyberpunkTheme.PrimaryMagenta.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }

                // Block button
                if (onBlockUser != null) {
                    IconButton(
                        onClick = { showBlockDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = "Block User",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Block confirmation dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = {
                Text("Block User?", color = CyberpunkTheme.White)
            },
            text = {
                Text(
                    "You won't be able to see messages from @$username after blocking them.",
                    color = CyberpunkTheme.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isBlocking = true
                        onBlockUser?.invoke()
                        showBlockDialog = false
                        isBlocking = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B6B)
                    ),
                    enabled = !isBlocking
                ) {
                    if (isBlocking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 1.dp
                        )
                    } else {
                        Text("Block")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = { showBlockDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberpunkTheme.DarkGray
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = CyberpunkTheme.DarkBlack,
            tonalElevation = 8.dp
        )
    }
}
