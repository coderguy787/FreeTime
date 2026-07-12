package com.freetime.app.ui.composables

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.theme.CyberpunkTheme
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*

/**
 * ✅ WhatsApp-style context menu for messages
 * Appears on long press with options: Reply, Copy, React, Delete, etc.
 */
@Composable
fun MessageContextMenu(
    messageId: String,
    @Suppress("UNUSED_PARAMETER") messageText: String,
    isOwnMessage: Boolean,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDelete: () -> Unit,
    onReact: (emoji: String) -> Unit,
    onReply: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onEdit: () -> Unit,
    @Suppress("UNUSED_PARAMETER") currentReactions: Map<String, List<String>> = emptyMap(),
    hasPublicMedia: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onDownload: () -> Unit = {}
) {
    @Suppress("UNUSED_VARIABLE")
    val context = LocalContext.current
    var showEmojiPicker by remember { mutableStateOf(false) }
    var isReacting by remember { mutableStateOf(false) }  // ✅ FIX: Track reaction state to prevent menu auto-close
    
    if (!showMenu) {
        android.util.Log.d("FREETIME_MESSAGE_MENU", "📋 Menu dismissed (showMenu=false)")
        return
    }
    
    // Emoji picker list (8-10 quick reactions)
    val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👌", "🎉")
    
    android.util.Log.d("FREETIME_MESSAGE_MENU", "📋 MessageContextMenu showing for messageId: $messageId, isOwnMessage: $isOwnMessage")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A2E),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ✅ Quick Emoji Reactions Row
                if (!showEmojiPicker) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = Color(0xFF2A2A3E),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        android.util.Log.d("FREETIME_MESSAGE_MENU", "👆 Emoji clicked: $emoji, isReacting: $isReacting")
                                        isReacting = true
                                        onReact(emoji)
                                        android.util.Log.d("FREETIME_MESSAGE_MENU", "✅ onReact callback completed for $emoji")
                                        // ✅ FIX: Don't close menu - let user react with multiple emojis like WhatsApp
                                        // Users can swipe/click elsewhere to close the menu
                                        // Delay reset to ensure any async operations complete
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            isReacting = false
                                            android.util.Log.d("FREETIME_MESSAGE_MENU", "✅ isReacting reset after emoji reaction")
                                        }, 100)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = Color(0xFF2A2A3E),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { showEmojiPicker = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = "More emojis",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = Color(0xFF2A2A3E),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                
                // ✅ Menu Actions
                // Copy
                MenuAction(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    onClick = {
                        onCopy()
                        onDismiss()
                    }
                )
                
                // Reply
                MenuAction(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    label = "Reply",
                    onClick = {
                        onReply()
                        onDismiss()
                    }
                )
                
                // Download (for public media)
                if (hasPublicMedia) {
                    MenuAction(
                        icon = Icons.Default.Download,
                        label = "Download",
                        onClick = {
                            onDownload()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * ✅ Full emoji picker for additional reactions
 */
@Composable
fun EmojiPickerDialog(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val emojis = listOf(
        "👍", "👎", "❤️", "🧡", "💛", "💚", "💙", "💜",
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😜", "🤪",
        "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
        "😐", "😑", "😶", "😏", "😒", "🙁", "😞", "😔",
        "😌", "😕", "😲", "😱", "😳", "🥺", "😦", "😧",
        "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
        "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
        "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹",
        "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹",
        "😻", "😼", "😽", "🙀", "😿", "😾", "🔥", "⭐",
        "✨", "💫", "💥", "⚡", "🎉", "🎊", "🎈", "🎁",
        "✌️", "🤞", "🤟", "🤘", "🤙", "👋", "🤚", "🖐️"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(16.dp)
            ),
        title = {
            Text("Choose an emoji", color = CyberpunkTheme.PrimaryPurple, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                repeat(emojis.size / 8) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(8) { col ->
                            val index = row * 8 + col
                            if (index < emojis.size) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            color = Color(0xFF2A2A3E),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onEmojiSelected(emojis[index])
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emojis[index], fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CyberpunkTheme.PrimaryPurple)
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}
