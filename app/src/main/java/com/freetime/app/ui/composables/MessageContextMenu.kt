package com.freetime.app.ui.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.theme.CyberpunkTheme
import androidx.compose.material.icons.automirrored.filled.Reply

@Composable
fun MessageContextMenu(
    messageId: String,
    messageText: String,
    isOwnMessage: Boolean,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onReact: (emoji: String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit = {},
    currentReactions: Map<String, List<String>> = emptyMap(),
    hasPublicMedia: Boolean = false,
    onDownload: () -> Unit = {}
) {
    var showEmojiPicker by remember { mutableStateOf(false) }

    if (!showMenu) return

    val quickEmojis = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25", "\uD83D\uDC4C", "\uD83C\uDF89")

    AnimatedVisibility(
        visible = showMenu,
        enter = fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.92f),
        exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    // context menu for messages
                    .clickable {  },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E30),
                shadowElevation = 16.dp,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (messageText.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2A2A40)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(CyberpunkTheme.CyberCyan)
                                )
                                Text(
                                    text = messageText,
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                    ) {
                        items(quickEmojis) { emoji ->
                            Surface(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { onReact(emoji) },
                                shape = CircleShape,
                                color = Color(0xFF2A2A40)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                        item {
                            Surface(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { showEmojiPicker = true },
                                shape = CircleShape,
                                color = Color(0xFF2A2A40)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "More emojis",
                                        tint = CyberpunkTheme.PrimaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFF2A2A40),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        WhatsAppMenuAction(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            label = "Reply",
                            iconTint = CyberpunkTheme.CyberCyan
                        ) {
                            onReply(); onDismiss()
                        }

                        WhatsAppMenuAction(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy"
                        ) {
                            onCopy(); onDismiss()
                        }

                        if (hasPublicMedia) {
                            WhatsAppMenuAction(
                                icon = Icons.Default.Download,
                                label = "Download",
                                iconTint = Color(0xFF00FF88)
                            ) {
                                onDownload(); onDismiss()
                            }
                        }

                        WhatsAppMenuAction(
                            icon = Icons.Default.CheckCircleOutline,
                            label = "Select",
                            iconTint = Color(0xFF00C9FF)
                        ) {
                            onSelect(); onDismiss()
                        }

                        if (isOwnMessage) {
                            HorizontalDivider(
                                color = Color(0xFF2A2A40),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                            WhatsAppMenuAction(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                iconTint = Color(0xFFFF4444),
                                textColor = Color(0xFFFF4444)
                            ) {
                                onDelete(); onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onEmojiSelected = { emoji -> onReact(emoji) },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

@Composable
fun WhatsAppMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color = CyberpunkTheme.LightGray,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmojiPickerDialog(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val emojis = listOf(
        "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDD25", "\uD83D\uDCAB",
        "\uD83C\uDF1F", "\u2B50", "\uD83C\uDF08", "\uD83C\uDF39", "\uD83C\uDF89", "\uD83D\uDCAA",
        "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE0E", "\uD83E\uDD29", "\uD83E\uDD14", "\uD83D\uDE0A",
        "\uD83D\uDE02", "\uD83D\uDE2D", "\uD83D\uDE21", "\uD83D\uDE31", "\uD83D\uDE08", "\uD83D\uDC7F",
        "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4B", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83D\uDCAA"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(20.dp)),
        title = {
            Text("React", color = CyberpunkTheme.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                emojis.chunked(6).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { emoji ->
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                        onDismiss()
                                    },
                                shape = CircleShape,
                                color = Color(0xFF2A2A40)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CyberpunkTheme.CyberCyan)
            }
        },
        containerColor = Color(0xFF1E1E30),
        titleContentColor = CyberpunkTheme.White,
        textContentColor = CyberpunkTheme.White
    )
}
