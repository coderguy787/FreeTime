package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.R
import com.freetime.app.ui.components.CyberpunkTheme
import kotlinx.coroutines.launch

data class EnhancedMessage(
    val id: String,
    val content: String,
    val senderName: String,
    val senderAvatar: String? = null,
    val isSender: Boolean,
    val timestamp: String,
    val status: String = "read", // sending, sent, delivered, read
    val reactions: Map<String, Int> = emptyMap(), // emoji -> count
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    val replyTo: String? = null,
    val forwardedFrom: String? = null,
    val mediaType: String? = null, // "image", "video", "audio", "document"
    val mediaUrl: String? = null
)

@Composable
fun EnhancedChatScreen(
    chatName: String,
    chatAvatar: String? = null,
    onNavigateBack: () -> Unit,
    onCall: () -> Unit,
    onVideo: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var selectedMessage by remember { mutableStateOf<String?>(null) }
    var showMessageOptions by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<EnhancedMessage?>(null) }
    var pinnedMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var showPinnedBanner by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val messages = remember {
        listOf(
            EnhancedMessage(
                id = "1",
                content = "Hey! Check out these amazing new features! 🚀",
                senderName = "Alex",
                isSender = false,
                timestamp = "10:23 AM",
                reactions = mapOf("🚀" to 1, "❤️" to 1),
                mediaType = null
            ),
            EnhancedMessage(
                id = "2",
                content = "Just implemented message reactions, pinning, and forwarding!",
                senderName = "You",
                isSender = true,
                timestamp = "10:24 AM",
                status = "read"
            ),
            EnhancedMessage(
                id = "3",
                content = "This is incredible! The UI is so smooth 😍",
                senderName = "Alex",
                isSender = false,
                timestamp = "10:25 AM",
                reactions = mapOf("😂" to 2, "🔥" to 3),
                isEdited = true
            ),
            EnhancedMessage(
                id = "4",
                content = "Thanks! Built with Telegram-like features in mind 💙",
                senderName = "You",
                isSender = true,
                timestamp = "10:26 AM",
                status = "read",
                isPinned = true
            )
        )
    }
    
    val filteredMessages = if (isSearching && searchQuery.isNotEmpty()) {
        messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
    } else {
        messages
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Enhanced Chat Header
            EnhancedChatHeader(
                chatName = chatName,
                isOnline = true,
                onNavigateBack = onNavigateBack,
                onCall = onCall,
                onVideo = onVideo,
                onSearch = { isSearching = !isSearching },
                unreadCount = 3,
                isSearching = isSearching
            )
            
            // Search Bar (conditional)
            if (isSearching) {
                SearchMessageBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearching = false
                        searchQuery = ""
                    }
                )
            }
            
            // Pinned Message Banner
            if (showPinnedBanner && pinnedMessage == null && messages.any { it.isPinned }) {
                PinnedMessageBanner(
                    message = messages.first { it.isPinned },
                    onDismiss = { showPinnedBanner = false },
                    onClick = { /* Jump to pinned message */ }
                )
            }
            
            // Messages List
            Box(modifier = Modifier.weight(1f)) {
                if (filteredMessages.isEmpty() && isSearching) {
                    EmptySearchResult(query = searchQuery)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp),
                        reverseLayout = true
                    ) {
                        items(
                            filteredMessages.asReversed(),
                            key = { it.id }
                        ) { message ->
                            EnhancedMessageBubble(
                                message = message,
                                isSelected = selectedMessage == message.id,
                                onLongPress = {
                                    selectedMessage = message.id
                                    showMessageOptions = true
                                },
                                onReplyClick = { replyingTo = message },
                                onReaction = { emoji ->
                                    showEmojiPicker = false
                                    // Handle reaction
                                }
                            )
                        }
                    }
                }
            }
            
            // Reply Preview
            if (replyingTo != null) {
                ReplyPreview(
                    message = replyingTo!!,
                    onClear = { replyingTo = null }
                )
            }
            
            // Enhanced Input Area
            EnhancedChatInput(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        messageText = ""
                        replyingTo = null
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
                onAttachClick = { /* Show attachment menu */ },
                onEmojiClick = { showEmojiPicker = !showEmojiPicker },
                hasReply = replyingTo != null,
                onVoiceRecord = { /* Start voice recording */ }
            )
            
            // Emoji Picker (conditional)
            if (showEmojiPicker) {
                EmojiReactionPicker(
                    onEmojiSelected = { emoji ->
                        showEmojiPicker = false
                        // Add reaction to selected message
                    }
                )
            }
        }
        
        // Message Options Menu (Modal)
        if (showMessageOptions && selectedMessage != null) {
            MessageContextMenu(
                message = messages.find { it.id == selectedMessage },
                onDismiss = {
                    showMessageOptions = false
                    selectedMessage = null
                },
                onReply = {
                    replyingTo = messages.find { it.id == selectedMessage }
                    showMessageOptions = false
                    selectedMessage = null
                },
                onForward = {
                    // Show forward dialog
                    showMessageOptions = false
                    selectedMessage = null
                },
                onPin = {
                    pinnedMessage = messages.find { it.id == selectedMessage }
                    showPinnedBanner = true
                    showMessageOptions = false
                    selectedMessage = null
                },
                onEdit = {
                    messageText = messages.find { it.id == selectedMessage }?.content ?: ""
                    showMessageOptions = false
                    selectedMessage = null
                },
                onDelete = {
                    showMessageOptions = false
                    selectedMessage = null
                }
            )
        }
    }
}

@Composable
fun EnhancedChatHeader(
    chatName: String,
    isOnline: Boolean,
    unreadCount: Int,
    isSearching: Boolean,
    onNavigateBack: () -> Unit,
    onCall: () -> Unit,
    onVideo: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CyberpunkTheme.Black,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .border(
                width = 0.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF9D4EDD).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = Color(0xFF9D4EDD),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chatName.first().toString(),
                        color = Color(0xFF9D4EDD),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        chatName,
                        color = CyberpunkTheme.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isOnline) Color(0xFF00FF00) else Color(0xFF888888),
                                    shape = CircleShape
                                )
                        )
                        
                        Text(
                            if (isOnline) "Active now" else "Offline",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isSearching) 
                                CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                            else 
                                Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Phone,
                        null,
                        tint = Color(0xFF00FF00),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = onVideo,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // Unread badge
        if (unreadCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Badge(
                    containerColor = CyberpunkTheme.PrimaryPurple,
                    contentColor = CyberpunkTheme.Black,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text("$unreadCount unread", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EnhancedMessageBubble(
    message: EnhancedMessage,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onReplyClick: () -> Unit,
    onReaction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (isSelected) 0.8f else 1f),
        horizontalAlignment = if (message.isSender) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Forwarded/Reply indicator
        if (message.forwardedFrom != null || message.replyTo != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
                    .align(if (message.isSender) Alignment.End else Alignment.Start)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (message.forwardedFrom != null) Icons.Default.Forward else Icons.Default.Reply,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (message.forwardedFrom != null)
                            "Forwarded from ${message.forwardedFrom}"
                        else
                            "Reply to ${message.replyTo}",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = if (message.isSender)
                        CyberpunkTheme.PrimaryPurple.copy(alpha = 0.8f)
                    else
                        Color(0xFF2A2A4E),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (message.isSender)
                        CyberpunkTheme.CyberCyan.copy(alpha = 0.5f)
                    else
                        CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() }
                    )
                }
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Pinned indicator
                if (message.isPinned) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            null,
                            tint = Color(0xFFFFAA00),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Pinned",
                            color = Color(0xFFFFAA00),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    message.content,
                    color = if (message.isSender) CyberpunkTheme.Black else CyberpunkTheme.White,
                    fontSize = 14.sp
                )
                
                // Edited indicator
                if (message.isEdited) {
                    Text(
                        "edited",
                        color = if (message.isSender)
                            CyberpunkTheme.Black.copy(alpha = 0.6f)
                        else
                            CyberpunkTheme.LightGray,
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                
                // Reactions
                if (message.reactions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(message.reactions.toList()) { (emoji, count) ->
                            ReactionBubble(emoji = emoji, count = count)
                        }
                    }
                }
            }
        }
        
        // Timestamp with status
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .align(if (message.isSender) Alignment.End else Alignment.Start)
        ) {
            Text(
                message.timestamp,
                color = CyberpunkTheme.GhostGray,
                fontSize = 11.sp
            )
            
            if (message.isSender) {
                when (message.status) {
                    "sending" -> Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = CyberpunkTheme.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                    "sent" -> Icon(
                        Icons.Default.Done,
                        null,
                        tint = CyberpunkTheme.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                    "delivered" -> Icon(
                        Icons.Default.DoneAll,
                        null,
                        tint = CyberpunkTheme.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                    "read" -> Icon(
                        Icons.Default.DoneAll,
                        null,
                        tint = CyberpunkTheme.CyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    else -> Icon(
                        Icons.Default.Error,
                        null,
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionBubble(emoji: String, count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = CyberpunkTheme.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 0.5.dp,
                color = CyberpunkTheme.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { /* Add own reaction */ }
            .padding(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(emoji, fontSize = 12.sp)
            Text(count.toString(), fontSize = 10.sp, color = CyberpunkTheme.LightGray)
        }
    }
}

@Composable
fun PinnedMessageBanner(
    message: EnhancedMessage,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFFAA00).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.5.dp,
                color = Color(0xFFFFAA00).copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.PushPin,
                    null,
                    tint = Color(0xFFFFAA00),
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pinned message",
                        color = Color(0xFFFFAA00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        message.content.take(50) + if (message.content.length > 50) "..." else "",
                        color = CyberpunkTheme.White,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = Color(0xFFFFAA00),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ReplyPreview(message: EnhancedMessage, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberpunkTheme.Black)
            .borderBottom(1.5.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f))
            .padding(12.dp)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Replying to ${message.senderName}",
                    color = CyberpunkTheme.CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message.content.take(60) + if (message.content.length > 60) "..." else "",
                    color = CyberpunkTheme.White,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EnhancedChatInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onVoiceRecord: () -> Unit,
    hasReply: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CyberpunkTheme.Black,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .border(
                width = 0.5.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = if (hasReply) CyberpunkTheme.CyberCyan else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    null,
                    tint = CyberpunkTheme.PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            TextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 100.dp),
                placeholder = { Text("Message...", color = CyberpunkTheme.GhostGray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = CyberpunkTheme.White,
                    unfocusedTextColor = CyberpunkTheme.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = CyberpunkTheme.CyberCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() })
            )
            
            IconButton(onClick = onEmojiClick, modifier = Modifier.size(40.dp)) {
                Text("😊", fontSize = 20.sp)
            }
            
            AnimatedContent(
                targetState = messageText.isNotBlank(),
                transitionSpec = {
                    (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                }
            ) { hasText ->
                if (hasText) {
                    IconButton(
                        onClick = onSendClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = CyberpunkTheme.PrimaryPurple,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            null,
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onVoiceRecord,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            null,
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiReactionPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf(
        "👍", "❤️", "😂", "😍", "🔥", "😮", "😢", "🎉",
        "🙌", "✨", "👌", "💯", "🎊", "🚀", "💜", "🌟"
    )
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(emojis) { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        color = Color(0xFF0F0F1E),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onEmojiSelected(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun MessageContextMenu(
    message: EnhancedMessage?,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ContextMenuItem(Icons.Default.Reply, "Reply", onReply)
            ContextMenuItem(Icons.Default.Forward, "Forward", onForward)
            ContextMenuItem(Icons.Default.PushPin, "Pin", onPin)
            if (message?.isSender == true) {
                ContextMenuItem(Icons.Default.Edit, "Edit", onEdit)
            }
            ContextMenuItem(Icons.Default.Delete, "Delete", onDelete, tint = Color(0xFFFF1744))
        }
    }
}

@Composable
fun ContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = CyberpunkTheme.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}

@Composable
fun SearchMessageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberpunkTheme.Black)
            .padding(12.dp)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = CyberpunkTheme.CyberCyan.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            placeholder = {
                Text("Search messages...", color = CyberpunkTheme.GhostGray, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = CyberpunkTheme.CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = CyberpunkTheme.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedTextColor = CyberpunkTheme.White,
                unfocusedTextColor = CyberpunkTheme.White,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = CyberpunkTheme.CyberCyan,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun EmptySearchResult(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            null,
            tint = CyberpunkTheme.LightGray,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "No messages found",
            color = CyberpunkTheme.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "\"$query\" didn't match any messages",
            color = CyberpunkTheme.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// Helper extension
fun Modifier.borderBottom(width: Dp, color: Color): Modifier =
    this.drawBehind {
        drawLine(color = color, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = width.toPx())
    }
