package com.freetime.app.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onEmojiClick: (() -> Unit)? = null,
    onMediaClick: (() -> Unit)? = null,
    onGifClick: (() -> Unit)? = null,
    isSending: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181A24))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onEmojiClick != null) {
            IconButton(onClick = onEmojiClick) {
                Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = Color.Cyan)
            }
        }
        if (onMediaClick != null) {
            IconButton(onClick = onMediaClick) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach Media", tint = Color.Cyan)
            }
        }
        if (onGifClick != null) {
            IconButton(onClick = onGifClick) {
                androidx.compose.material3.Text(
                    "GIF",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Cyan
                )
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            placeholder = { Text("Type a message...", fontSize = 14.sp) },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            ),
            singleLine = true
        )
        IconButton(
            onClick = onSendMessage,
            enabled = !isSending && value.isNotBlank()
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send", tint = if (!isSending && value.isNotBlank()) Color.Cyan else Color.Gray)
        }
    }
}
