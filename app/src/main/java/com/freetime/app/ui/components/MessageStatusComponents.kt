package com.freetime.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.theme.CyberpunkTheme

/**
 * Typing Indicator Component
 * Shows animated "User is typing..." message
 */
@Composable
fun TypingIndicator(
    userName: String,
    modifier: Modifier = Modifier
) {
    var animationState by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            animationState = (animationState + 1) % 3
            kotlinx.coroutines.delay(400)
        }
    }
    
    Row(
        modifier = modifier
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$userName ",
            fontSize = 13.sp,
            color = Color.Gray
        )
        
        // Animated dots
        repeat(3) { index ->
            AnimatedVisibility(
                visible = index <= animationState,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "●",
                    fontSize = 10.sp,
                    color = CyberpunkTheme.SecondaryMagenta.copy(alpha = 0.7f)
                )
            }
        }
        
        Text(
            text = " is typing",
            fontSize = 13.sp,
            color = Color.Gray
        )
    }
}

/**
 * Message Status Indicator
 * Shows ✓ (sent), ✓✓ (delivered), ✓✓ (read)
 */
@Composable
fun MessageStatusIndicator(
    isRead: Boolean = false,
    isDelivered: Boolean = true,
    isSent: Boolean = true,
    modifier: Modifier = Modifier,
    size: Float = 14f
) {
    if (!isSent) {
        // Message not sent (may be pending or failed)
        Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "Message pending",
            modifier = modifier.size((size * 0.8f).dp),
            tint = Color.Gray
        )
    } else if (isRead) {
        // Message read - double checkmark in cyan
        Row(modifier = modifier) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Message read",
                modifier = Modifier.size(size.dp),
                tint = CyberpunkTheme.SecondaryMagenta
            )
        }
    } else if (isDelivered) {
        // Message delivered - double checkmark in gray
        Row(modifier = modifier) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Message delivered",
                modifier = Modifier.size(size.dp),
                tint = Color.Gray
            )
        }
    } else {
        // Single checkmark for sent
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = "Message sent",
            modifier = modifier.size(size.dp),
            tint = Color.Gray
        )
    }
}

/**
 * Read Receipt Info
 * Shows who read the message and when
 */
@Composable
fun ReadReceiptInfo(
    readBy: String = "",
    readAt: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (readBy.isNotEmpty() && readAt > 0) {
        val readTime = formatTimestamp(readAt)
        Text(
            text = "Read by $readBy at $readTime",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = modifier.padding(start = 8.dp, top = 4.dp)
        )
    }
}

/**
 * Format timestamp to readable format
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val now = java.util.Date()
    val diffSeconds = (now.time - date.time) / 1000
    
    return when {
        diffSeconds < 60 -> "now"
        diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
        diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            sdf.format(date)
        }
    }
}

/**
 * Delivery Status Badge
 * Shows small badge with delivery status
 */
@Composable
fun DeliveryStatusBadge(
    status: String, // "sent", "delivered", "read"
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status.lowercase()) {
        "read" -> Triple(CyberpunkTheme.SecondaryMagenta.copy(alpha = 0.2f), CyberpunkTheme.SecondaryMagenta, "✓✓")
        "delivered" -> Triple(Color.Gray.copy(alpha = 0.1f), Color.Gray, "✓✓")
        else -> Triple(Color.Transparent, Color.LightGray, "✓")
    }
    
    Surface(
        modifier = modifier
            .size(width = 32.dp, height = 20.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                color = textColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
