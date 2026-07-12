package com.freetime.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.documentfile.provider.DocumentFile
import com.freetime.app.ui.theme.CyberpunkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freetime.app.data.network.ApiClient
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.ui.animations.StaggeredVerticalItemAnimation
import com.freetime.app.ui.composables.MessageContextMenu
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.freetime.app.ui.composables.DownloadProgressBar
import com.freetime.app.ui.composables.GifPickerDialog
import java.io.File
import java.io.FileOutputStream

data class ChatMessage(
    val id: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val isFromCurrentUser: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> list of usernames
    val mediaId: String? = null, // Reference to encrypted media
    val mediaType: String? = null, // "image" or "video"
    val mediaName: String? = null,
    val senderAvatar: String? = null, // Profile picture URL from backend
    val mediaShareMode: String? = null, // ✅ NEW: "public" (unencrypted) or "protected" (encrypted)
    // ✅ REPLY SUPPORT: Fields for WhatsApp-style replies
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    // ✅ MEDIA REQUESTS: Track pending download requests for this message
    val pendingRequests: List<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData> = emptyList()
)

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ
}

fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}

fun downloadGif(gifUrl: String): File? {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(gifUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: return null
        val ext = if (gifUrl.contains(".gif")) "gif" else "mp4"
        val tempFile = File.createTempFile("gif_", ".$ext")
        FileOutputStream(tempFile).use { output ->
            output.write(body.bytes())
        }
        tempFile
    } catch (e: Exception) {
        Log.e("GIF_DOWNLOAD", "Failed to download GIF", e)
        null
    }
}

// ✅ NEW: Composable for rendering clickable links in message text
@Composable
fun ClickableMessageText(
    text: String,
    style: androidx.compose.material3.Typography = MaterialTheme.typography,
    textColor: Color = CyberpunkTheme.PrimaryPurple,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    // More robust URL regex - matches http(s), ftp, and www URLs
    val urlPattern = """(?:https?|ftp)://[^\s]+|www\.[^\s]+""".toRegex()
    
    // Build annotated string with URL annotations at correct positions
    val annotatedString = buildAnnotatedString {
        var lastEndIndex = 0
        
        urlPattern.findAll(text).forEach { urlMatch ->
            // Add non-URL text before this match
            if (urlMatch.range.first > lastEndIndex) {
                append(text.substring(lastEndIndex, urlMatch.range.first))
            }
            
            val urlValue = urlMatch.value
            val annotationStart = length
            
            // Add URL with annotation
            pushStringAnnotation(tag = "URL", annotation = urlValue)
            withStyle(style = SpanStyle(
                color = CyberpunkTheme.CyberCyan,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            )) {
                append(urlValue)
            }
            pop()
            
            lastEndIndex = urlMatch.range.last + 1
        }
        
        // Add any remaining text after last URL
        if (lastEndIndex < text.length) {
            append(text.substring(lastEndIndex))
        }
    }
    
    val layoutResult = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    // Use Text with pointerInput to handle both tap (for URLs) and long press (for menu)
    androidx.compose.material3.Text(
        text = annotatedString,
        style = style.bodySmall.copy(color = textColor),
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        val urlAnnotations = annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        )
                        
                        val urlAnnotation = urlAnnotations.firstOrNull()
                        if (urlAnnotation != null) {
                            val url = urlAnnotation.item
                            val fullUrl = when {
                                url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://") -> url
                                url.startsWith("www.") -> "https://$url"
                                else -> "https://$url"
                            }
                            
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                context.startActivity(intent)
                                android.util.Log.d("CLICKABLE_LINK", "✅ Opening URL: $fullUrl")
                            } catch (e: Exception) {
                                android.util.Log.e("CLICKABLE_LINK", "❌ Failed to open URL: ${e.message}", e)
                                android.widget.Toast.makeText(context, "Cannot open link: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onLongPress = {
                    onLongPress()
                }
            )
        }
    )
}

@Composable
fun ChatScreenEnhanced(
    context: Context,
    chatId: String = "CYBER_CHAT_001",
    chatName: String = "CYBER_USER_1",
    recipientId: String = chatId,
    onBackClick: () -> Unit = {}
) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var newMessageText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showVideoCallDialog by remember { mutableStateOf(false) }
    var showAudioCallDialog by remember { mutableStateOf(false) }
    var reactionTarget by remember { mutableStateOf<String?>(null) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showMediaModeDialog by remember { mutableStateOf(false) }
    var pendingMediaShareMode by remember { mutableStateOf<String?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }
    
    // ✅ NEW: Message context menu state
    var showMessageContextMenu by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageText by remember { mutableStateOf("") }
    var selectedMessageIsOwn by remember { mutableStateOf(false) }
    
    // ✅ NEW: Reply state
    var replyingToMessageId by remember { mutableStateOf<String?>(null) }
    var replyingToUsername by remember { mutableStateOf("") }
    var replyingToText by remember { mutableStateOf("") }
    
    var uploadProgress by remember { mutableStateOf(0f) }
    var mediaDownloadRequests by remember { mutableStateOf(mapOf<String, String>()) } // mediaId -> status
    var mediaDownloadApprovals by remember { mutableStateOf(mapOf<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData>()) }
    // ✅ NEW: Reload trigger to force message refresh when screen is reopened
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val apiService = ApiClient.getInstance()
    val freeTimeApiService = remember { FreeTimeApiService(context) }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when input field is focused (keyboard opens)
    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (_: Exception) {}
        }
    }
    
    // Get current user info
    val prefs = SharedPreferencesHelper(context)
    val currentUserId = prefs.getUserId() ?: ""
    val currentUsername = prefs.getUsername() ?: "YOU"
    val token = prefs.getToken() ?: ""
    
    // Helper function to format timestamps
    fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    // ✅ NEW: Helper function to map MIME types to file extensions
    fun getFileExtensionFromMimeType(mimeType: String): String {
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) return ".$extension"
        
        return when (mimeType.lowercase()) {
            // Fallbacks for common types MimeTypeMap might miss
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "video/mp4" -> ".mp4"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> ""
        }
    }

    // ✅ NEW: Helper function to ensure filename has correct extension
    fun ensureCorrectFileExtension(originalFileName: String, mimeType: String): String {
        // If filename already has an extension, preserve it
        if (originalFileName.contains('.')) {
            android.util.Log.d("FREETIME_MEDIA", "📝 Preserving original extension: $originalFileName")
            return originalFileName
        }
        
        // No extension - add one based on MIME type
        val extension = getFileExtensionFromMimeType(mimeType)
        if (extension.isEmpty()) return originalFileName
        
        val fileName = originalFileName + extension
        android.util.Log.d("FREETIME_MEDIA", "📝 File extension mapping: $originalFileName → $fileName (mimeType: $mimeType)")
        return fileName
    }

    // Download and save media file function
    fun downloadAndSaveMediaFile(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
        try {
            android.util.Log.d("FREETIME_MEDIA", "🎬 Starting auto-download of approved media: ${data.mediaId}")
            
            val downloadUrl = if (data.downloadUrl?.startsWith("http") == true) {
                data.downloadUrl
            } else if (!data.downloadUrl.isNullOrEmpty()) {
                "${freeTimeApiService.getBaseUrl().trimEnd('/')}${data.downloadUrl}"
            } else {
                android.util.Log.e("FREETIME_MEDIA", "No download URL provided in approval")
                return
            }
            
            val request = okhttp3.Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            // Create OkHttpClient with SSL bypass (same as FreeTimeApiService)
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            val client = okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("FREETIME_MEDIA", "Download failed: ${response.code}")
                return
            }
            
            val encryptedBytes = response.body?.bytes() ?: return
            
            val decryptedBytes = if (data.encrypted && !data.encryptionKey.isNullOrEmpty()) {
                try {
                    val encryptor = com.freetime.app.security.MediaEncryption(context)
                    encryptor.decryptMedia(encryptedBytes, data.encryptionKey)
                } catch (decryptError: Exception) {
                    android.util.Log.e("FREETIME_MEDIA", "Decryption failed: ${decryptError.message}")
                    return
                }
            } else {
                encryptedBytes
            }
            
            val mimeType = data.mimeType ?: "application/octet-stream"
            val mediaType = when {
                mimeType.startsWith("image") -> "image"
                mimeType.startsWith("video") -> "video"
                mimeType.startsWith("audio") -> "audio"
                else -> "document"
            }
            
            // ✅ FIXED: Ensure filename has correct extension based on MIME type
            val originalFileName = data.fileName ?: "media_${System.currentTimeMillis()}"
            val fileName = ensureCorrectFileExtension(originalFileName, mimeType)
            
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        when (mediaType) {
                            "video" -> android.os.Environment.DIRECTORY_MOVIES
                            "image" -> android.os.Environment.DIRECTORY_PICTURES
                            else -> android.os.Environment.DIRECTORY_DOWNLOADS
                        }
                    )
                }
            }
            
            val mediaUri = when (mediaType) {
                "video" -> android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                "image" -> android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> {
                    // API 29+ uses MediaStore.Downloads, older versions use MediaStore.Files
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                }
            }
            
            val uri = context.contentResolver.insert(mediaUri, contentValues)
                ?: run {
                    android.util.Log.e("FREETIME_MEDIA", "Failed to create media file entry")
                    return
                }
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(decryptedBytes)
                outputStream.flush()
            } ?: run {
                android.util.Log.e("FREETIME_MEDIA", "Failed to open output stream")
                return
            }
            
            android.util.Log.d("FREETIME_MEDIA", "✅ Media saved to gallery: $fileName")
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "📥 Downloaded: $fileName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_MEDIA", "Error downloading media: ${e.message}", e)
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Failed to download: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Media picker launcher for images and videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        isUploadingMedia = true
                        uploadProgress = 0f
                        
                        // Get file info from URI
                        val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "media_${System.currentTimeMillis()}"
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val mediaType = when {
                            mimeType.startsWith("image/") -> "image"
                            mimeType.startsWith("video/") -> "video"
                            else -> "document"
                        }

                        // Read file data
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val fileData = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()

                        if (fileData.isNotEmpty()) {
                            // Create multipart body for media upload
                            val requestBody = fileData.toRequestBody(mimeType.toMediaType())
                            val multipartBody = okhttp3.MultipartBody.Part.createFormData("media", fileName, requestBody)
                            
                            val response = apiService.uploadMediaToChat(
                                recipientId = recipientId,
                                media = multipartBody,
                                token = "Bearer $token"
                            )
                            
                            if (response.isSuccessful && response.body()?.success == true) {
                                val mediaId = response.body()?.mediaId ?: ""
                                if (mediaId.isNotEmpty()) {
                                    // Add media message to chat (private messages always use protected/encrypted mode)
                                    val mediaLabel = when (mediaType) {
                                        "image" -> "image: $fileName"
                                        "video" -> "video: $fileName"
                                        else -> "file: $fileName"
                                    }
                                    val effectiveShareMode = pendingMediaShareMode ?: "protected"
                                    messages = messages + ChatMessage(
                                        id = (messages.size + 1).toString(),
                                        senderName = currentUsername,
                                        content = mediaLabel,
                                        timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                                        isFromCurrentUser = true,
                                        status = MessageStatus.SENT,
                                        mediaId = mediaId,
                                        mediaType = mediaType,
                                        mediaName = fileName,
                                        mediaShareMode = effectiveShareMode
                                    )
                                    errorMessage = "✓ $mediaType sent ${if (effectiveShareMode == "public") "as public" else "securely"}"
                                    pendingMediaShareMode = null
                                } else {
                                    errorMessage = "Media uploaded but no ID returned"
                                }
                            } else {
                                errorMessage = "Failed to upload media: ${response.body()?.error ?: response.message()}"
                            }
                        } else {
                            errorMessage = "Failed to read selected file"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        android.util.Log.e("FREETIME_CHAT", "Media upload error: ${e.message}", e)
                    } finally {
                        isUploadingMedia = false
                        uploadProgress = 0f
                    }
                }
            }
        }
    )

    fun addOrUpdateMessage(newMsg: ChatMessage) {
        val index = messages.indexOfFirst { it.id == newMsg.id }
        if (index != -1) {
            val existing = messages[index]
            messages = messages.toMutableList().apply {
                this[index] = newMsg.copy(
                    reactions = if (newMsg.reactions.isEmpty()) existing.reactions else newMsg.reactions,
                    replyToMessageId = newMsg.replyToMessageId?.takeIf { it != "null" } ?: existing.replyToMessageId,
                    replyToUsername = newMsg.replyToUsername?.takeIf { it != "null" } ?: existing.replyToUsername,
                    replyToText = newMsg.replyToText?.takeIf { it != "null" } ?: existing.replyToText
                )
            }
        } else {
            messages = messages + newMsg
        }
    }

    // WebSocket for real-time messages
    DisposableEffect(recipientId) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        
        // ✅ NEW: Reload messages when screen is opened/reopened
        // This ensures messages are fetched fresh if the screen was recreated
        reloadTrigger++
        val listener = object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                // Check if this message is for the current conversation
                // Skip own messages (already added via HTTP response) to prevent duplicates
                if (message.senderId == recipientId && message.recipientId == currentUserId) {
                    // Update UI on main thread
                    scope.launch(Dispatchers.Main) {
                        // Parse mediaId from content as fallback (format: [Media: uuid] filename)
                        var resolvedMediaId = message.media
                        var resolvedMediaType = message.mediaType
                        var resolvedMediaName = message.mediaName
                        if (resolvedMediaId == null) {
                            val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                            val match = mediaIdRegex.find(message.content)
                            if (match != null) {
                                resolvedMediaId = match.groupValues[1]
                                resolvedMediaName = message.content.substringAfter("] ").takeIf { it.isNotEmpty() }
                                resolvedMediaType = if (message.content.contains("video", ignoreCase = true)) "video" else "image"
                            }
                        }
                        val newChatMsg = ChatMessage(
                            id = message.messageId,
                            senderName = if (message.senderId == currentUserId) currentUsername else (message.senderDisplayName?.takeIf { it.isNotEmpty() } ?: message.senderUsername.takeIf { it.isNotEmpty() } ?: chatName),
                            content = message.content,
                            timestamp = formatTimestamp(message.createdAt),
                            isFromCurrentUser = message.senderId == currentUserId,
                            status = MessageStatus.DELIVERED,
                            senderAvatar = message.senderAvatar,
                            mediaId = resolvedMediaId,
                            mediaType = resolvedMediaType,
                            mediaName = resolvedMediaName,
                            mediaShareMode = message.mediaShareMode ?: "protected", // ✅ Default to protected for backward compat
                            replyToMessageId = message.replyToMessageId,
                            replyToUsername = message.replyToUsername,
                            replyToText = message.replyToText
                        )
                        addOrUpdateMessage(newChatMsg)
                        
                        // Scroll to bottom (only if user is near bottom)
                        try {
                            if (messages.isNotEmpty() && !listState.isScrollInProgress) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {
                if (typingData.userId == recipientId) {
                    // Handle typing indicator if needed
                }
            }
            
            // ✅ NEW: Handle reactions in private chats
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            val currentReactions = msg.reactions.toMutableMap()
                            val reactorsForEmoji = currentReactions.getOrDefault(reactionData.emoji, emptyList()).toMutableList()
                            if (!reactorsForEmoji.contains(reactionData.userId)) {
                                reactorsForEmoji.add(reactionData.userId)
                            }
                            currentReactions[reactionData.emoji] = reactorsForEmoji
                            msg.copy(reactions = currentReactions)
                        } else msg
                    }
                }
            }
            
            // ✅ NEW: Handle reaction removal in private chats
            override fun onReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            val currentReactions = msg.reactions.toMutableMap()
                            val reactorsForEmoji = currentReactions.getOrDefault(reactionData.emoji, emptyList()).toMutableList()
                            reactorsForEmoji.remove(reactionData.userId)
                            if (reactorsForEmoji.isEmpty()) {
                                currentReactions.remove(reactionData.emoji)
                            } else {
                                currentReactions[reactionData.emoji] = reactorsForEmoji
                            }
                            msg.copy(reactions = currentReactions)
                        } else msg
                    }
                }
            }
            
            // ✅ NEW: Handle avatar updates in private chats
            override fun onAvatarUpdated(data: com.freetime.app.services.WebSocketManager.AvatarUpdatedData) {
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (!msg.isFromCurrentUser) {
                            msg.copy(senderAvatar = data.avatarUrl)
                        } else msg
                    }
                }
            }
            
            // ✅ NEW: Handle notifications in private chats
            override fun onNotificationReceived(data: com.freetime.app.services.WebSocketManager.InternalNotificationData) {
                scope.launch(Dispatchers.Main) {
                    errorMessage = "📢 ${data.body}"
                    Log.d("CHAT", "Notification: ${data.body}")
                }
            }
            
            override fun onChatHistoryDeleted(data: com.freetime.app.services.WebSocketManager.ChatHistoryDeletedData) {
                if (data.recipientId == currentUserId || data.recipientId == recipientId) {
                    scope.launch(Dispatchers.Main) {
                        messages = emptyList()
                    }
                }
            }

            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests + data)
                        } else msg
                    }
                }
            }

            override fun onMediaDownloadApproved(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                scope.launch(Dispatchers.Main) {
                    mediaDownloadRequests = mediaDownloadRequests + (data.mediaId to "approved")
                    messages = messages.map { msg ->
                        if (msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.requestId == data.requestId })
                        } else msg
                    }
                }
                // Keep the approval payload so the UI can manually re-trigger download if needed
                mediaDownloadApprovals = mediaDownloadApprovals + (data.mediaId to data)
                
                // AUTO-DOWNLOAD: Download file automatically when approval received (no nested scopes)
                if (data.downloadUrl != null && data.downloadUrl.isNotEmpty()) {
                    android.util.Log.d("FREETIME_CHAT", "📥 Starting auto-download for mediaId: ${data.mediaId}")
                    // Direct scope launch without try-catch to avoid nesting issues
                    scope.launch(Dispatchers.IO) {
                        downloadAndSaveMediaFile(data)
                    }
                }
            }

            override fun onMediaDownloadDenied(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                scope.launch(Dispatchers.Main) {
                    mediaDownloadRequests = mediaDownloadRequests + (data.mediaId to "denied")
                    messages = messages.map { msg ->
                        if (msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.requestId == data.requestId })
                        } else msg
                    }
                }
            }
        }
        
        wsManager.addListener(listener)
        onDispose {
            wsManager.removeListener(listener)
        }
    }

    // Fetch pending media download requests from backend
    LaunchedEffect(recipientId, reloadTrigger) {
        try {
            val result = freeTimeApiService.getPendingMediaDownloadRequests()
            result.onSuccess { pendingListResult ->
                // Update messages with pending requests
                val requestsMap = mutableMapOf<String, MutableList<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData>>()
                for (request in pendingListResult) {
                    if (!request.mediaId.isNullOrEmpty()) {
                        val list = requestsMap.getOrPut(request.mediaId!!) { mutableListOf() }
                        list.add(com.freetime.app.services.WebSocketManager.MediaDownloadRequestData(
                            requestId = request.requestId,
                            mediaId = request.mediaId,
                            requesterId = request.requesterId ?: "",
                            requesterName = request.requesterName
                        ))
                    }
                }
                
                // Update messages with their pending requests
                messages = messages.map { msg ->
                    msg.copy(pendingRequests = requestsMap[msg.mediaId] ?: emptyList())
                }
                
                android.util.Log.d("FREETIME_CHAT", "✅ Fetched pending media requests: ${requestsMap.size} media with requests")
            }.onFailure { error ->
                android.util.Log.w("FREETIME_CHAT", "Failed to fetch pending requests: ${error.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("FREETIME_CHAT", "Exception fetching pending requests: ${e.message}")
        }
    }
    
    // Fetch messages from API on load
    LaunchedEffect(recipientId, reloadTrigger) {
        isLoadingMessages = true
        android.util.Log.d("FREETIME_CHAT", "📥 Fetching messages for recipientId=$recipientId, reloadTrigger=$reloadTrigger")
        try {
            val response = apiService.getMessages(recipientId, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val fetchedList = response.body() ?: emptyList()
                android.util.Log.d("FREETIME_CHAT", "✅ Fetched ${fetchedList.size} messages from backend")
                messages = fetchedList.mapNotNull { msgResponse ->
                    try {
                        ChatMessage(
                            id = msgResponse._id,
                            senderName = if (msgResponse.senderId == currentUserId) currentUsername else (msgResponse.senderName?.takeIf { it.isNotEmpty() } ?: (msgResponse.senderDisplayName?.takeIf { it.isNotEmpty() } ?: chatName)),
                            
                            content = msgResponse.content ?: "",
                            timestamp = formatTimestamp(msgResponse.timestamp),
                            isFromCurrentUser = msgResponse.senderId == currentUserId,
                            status = if (msgResponse.read) MessageStatus.READ else MessageStatus.DELIVERED,
                            reactions = msgResponse.reactions,
                            senderAvatar = msgResponse.senderAvatar,
                            mediaId = msgResponse.mediaId,
                            mediaType = msgResponse.mediaType,
                            mediaName = msgResponse.mediaName,
                            mediaShareMode = msgResponse.mediaShareMode ?: "protected", // ✅ Default to protected for backward compat
                            replyToMessageId = msgResponse.replyToMessageId,
                            replyToUsername = msgResponse.replyToUsername,
                            replyToText = msgResponse.replyToText
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                // Safely try to scroll to bottom
                try {
                    if (messages.isNotEmpty()) {
                        listState.scrollToItem(messages.lastIndex)
                    }
                } catch (e: Exception) {
                    // Safe to ignore - scroll failed or was interrupted
                }
                
                errorMessage = ""
            } else {
                errorMessage = "Failed to load messages"
            }
        } catch (e: CancellationException) {
            // Screen was closed, this is normal
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Connection failed"
        } finally {
            isLoadingMessages = false
        }
    }

    val lastSendTimeMs = remember { mutableStateOf(0L) }
    
    fun sendMessage() {
        if (newMessageText.isBlank() || token.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastSendTimeMs.value < 1200) {
            android.util.Log.d("FREETIME_CHAT", "⏱️ Debounce: message send throttled")
            return
        }
        lastSendTimeMs.value = now
        
        val replyToId = replyingToMessageId  // Capture current reply before clearing
        scope.launch {
            isSendingMessage = true
            try {
                android.util.Log.d("FREETIME_CHAT", "📤 Sending message to $recipientId: ${newMessageText.take(50)}...")
                val msgRequest = com.freetime.app.data.network.SendMessageRequest(
                    recipientId = recipientId,
                    content = newMessageText,
                    replyToMessageId = replyToId,
                    replyToUsername = replyingToUsername.takeIf { it.isNotEmpty() },
                    replyToText = replyingToText.takeIf { it.isNotEmpty() }
                )
                val response = apiService.sendMessage(msgRequest, "Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    val msgResponse = response.body() ?: return@launch
                    try {
                        android.util.Log.d("FREETIME_CHAT", "✅ Message sent successfully, ID: ${msgResponse._id}")
                        val newChatMsg = ChatMessage(
                            id = msgResponse._id,
                            senderName = currentUsername,
                            content = newMessageText,
                            timestamp = formatTimestamp(System.currentTimeMillis()),
                            isFromCurrentUser = true,
                            status = MessageStatus.SENT,
                            // ✅ CRITICAL FIX: Include reply data when sending message
                            replyToMessageId = msgResponse.replyToMessageId ?: replyToId,
                            replyToUsername = msgResponse.replyToUsername?.takeIf { it.isNotEmpty() } ?: replyingToUsername,
                            replyToText = msgResponse.replyToText?.takeIf { it.isNotEmpty() } ?: replyingToText
                        )
                        addOrUpdateMessage(newChatMsg)
                        newMessageText = ""
                        replyingToMessageId = null
                        replyingToUsername = ""
                        replyingToText = ""
                        errorMessage = ""
                        
                        // Try to scroll to bottom safely, but don't crash if interrupted
                        try {
                            if (messages.isNotEmpty() && !listState.isScrollInProgress) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        } catch (e: Exception) {
                            // Scroll was interrupted, likely due to screen closing
                            // This is normal and safe to ignore
                        }
                    } catch (e: Exception) {
                        // State mutation failed, but message was sent
                        newMessageText = ""
                        errorMessage = "Failed to update message display"
                    }
                } else {
                    android.util.Log.e("FREETIME_CHAT", "❌ Failed to send message: ${response.code()}")
                    errorMessage = "Failed to send message"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Screen was closed or coroutine was cancelled, this is normal
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Send failed"
            } finally {
                isSendingMessage = false
            }
        }
    }

    fun deleteMessageHistory() {
        scope.launch {
            isDeleting = true
            try {
                val deleteRequest = com.freetime.app.data.network.DeleteHistoryRequestDto(
                    targetUserId = recipientId,
                    chatId = "all",
                    deletionType = "one_side"
                )
                val response = apiService.deleteHistoryWithUser(recipientId, deleteRequest, "Bearer $token")
                
                if (response.isSuccessful) {
                    try {
                        messages = emptyList()
                        errorMessage = "Chat history cleared successfully"
                        showDeleteConfirm = false
                    } catch (e: Exception) {
                        errorMessage = "Failed to clear history"
                    }
                } else {
                    errorMessage = "Failed to delete chat history"
                }
            } catch (e: CancellationException) {
                // Screen was closed, this is normal
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Delete failed"
            } finally {
                isDeleting = false
            }
        }
    }
    
    // ✅ NEW: Copy message text to clipboard
    fun copyMessageToClipboard() {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Message", selectedMessageText))
        android.widget.Toast.makeText(context, "Message copied!", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    // ✅ NEW: Delete individual message
    fun deleteMessage(messageId: String) {
        scope.launch {
            freeTimeApiService.deleteMessage(messageId, "Bearer $token").onSuccess {
                messages = messages.filterNot { it.id == messageId }
                android.widget.Toast.makeText(context, "Message deleted", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                android.widget.Toast.makeText(context, "Failed to delete: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // ✅ NEW: Add reaction to message with toggle (remove if already reacted)
    fun toggleReaction(messageId: String, emoji: String) {
        scope.launch {
            android.util.Log.d("FREETIME_CHAT_REACTION", "🔄 Starting reaction toggle for emoji: $emoji")
            val message = messages.find { it.id == messageId } ?: return@launch
            val currentUsersReacted = message.reactions[emoji] ?: emptyList()
            
            // Check if user already reacted with this emoji
            if (currentUsersReacted.contains(currentUserId)) {
                // Remove reaction
                android.util.Log.d("FREETIME_CHAT_REACTION", "➖ Removing reaction $emoji for user $currentUsername")
                freeTimeApiService.removeReaction(messageId, emoji).onSuccess {
                    android.util.Log.d("FREETIME_CHAT_REACTION", "✅ Reaction removed successfully")
                    val newReactions = message.reactions.toMutableMap()
                    val updatedUsers = newReactions[emoji]?.filterNot { it == currentUserId } ?: emptyList()
                    if (updatedUsers.isEmpty()) {
                        newReactions.remove(emoji)
                    } else {
                        newReactions[emoji] = updatedUsers
                    }
                    messages = messages.map { 
                        if (it.id == messageId) it.copy(reactions = newReactions) else it 
                    }
                }.onFailure { e ->
                    android.util.Log.e("FREETIME_CHAT_REACTION", "❌ Failed to remove reaction: ${e.message}")
                    android.widget.Toast.makeText(context, "Failed to remove reaction", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                // Add reaction
                android.util.Log.d("FREETIME_CHAT_REACTION", "➕ Adding reaction $emoji for user $currentUsername")
                freeTimeApiService.addReaction(messageId, emoji).onSuccess {
                    android.util.Log.d("FREETIME_CHAT_REACTION", "✅ Reaction added successfully")
                    val newReactions = message.reactions.toMutableMap()
                    val updatedUsers = (newReactions[emoji] ?: emptyList()).toMutableList()
                    updatedUsers.add(currentUserId)
                    newReactions[emoji] = updatedUsers
                    messages = messages.map { 
                        if (it.id == messageId) it.copy(reactions = newReactions) else it 
                    }
                }.onFailure { e ->
                    android.util.Log.e("FREETIME_CHAT_REACTION", "❌ Failed to add reaction: ${e.message}")
                    android.widget.Toast.makeText(context, "Failed to add reaction", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            android.util.Log.d("FREETIME_CHAT_REACTION", "✅ toggleReaction coroutine finished")
        }
    }
    
    // ✅ NEW: Reply to message
    fun replyToMessage(messageId: String) {
        val message = messages.find { it.id == messageId }
        if (message != null) {
            replyingToMessageId = messageId
            replyingToUsername = if (message.isFromCurrentUser) currentUsername else (message.senderName.takeIf { it.isNotEmpty() } ?: "User")
            replyingToText = message.content.take(50)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberpunkTheme.DarkBlack)
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .imePadding()) {
            // ===== CYBERPUNK HEADER =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyberpunkTheme.PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.6f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                chatName.take(1),
                                color = CyberpunkTheme.PrimaryPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            chatName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = CyberpunkTheme.PrimaryPurple
                        )
                        // Status based on API
                        Text(
                            "● Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00FF00)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        /*
                        IconButton(
                            onClick = { showAudioCallDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Audio Call",
                                tint = CyberpunkTheme.CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        */
                        
                        /*
                        IconButton(
                            onClick = { showVideoCallDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Videocam,
                                contentDescription = "Video Call",
                                tint = CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        */
                        
                        Box(modifier = Modifier.size(32.dp)) {
                            IconButton(
                                onClick = { showMoreMenu = !showMoreMenu },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat History") },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DeleteOutline, "Clear History")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Profile") },
                                    onClick = { showMoreMenu = false },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Person, "Profile")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ===== DELETE CONFIRMATION DIALOG =====
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Chat History?") },
                    text = {
                        Text(
                            "This will delete all messages, files, and documents between you and this user. This action cannot be undone.",
                            color = Color.White
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                deleteMessageHistory()
                            }
                        ) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false }
                        ) {
                            Text("Cancel", color = CyberpunkTheme.PrimaryPurple)
                        }
                    },
                    containerColor = CyberpunkTheme.DarkGray
                )
            }

            // ===== MESSAGES LIST =====
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = false
            ) {
                itemsIndexed(messages) { index, message ->
                    StaggeredVerticalItemAnimation(index = index, staggerDelayMs = 30) {
                    if (message.isFromCurrentUser) {
                        UserMessageBubble(
                            message = message,
                            onLongPress = { 
                                // ✅ Long-press handler
                                android.util.Log.d("FREETIME_CHAT_LONGPRESS", "📌 Long-press on own message: ${message.id}")
                                selectedMessageId = message.id
                                selectedMessageText = message.content
                                selectedMessageIsOwn = true
                                showMessageContextMenu = true
                                android.util.Log.d("FREETIME_CHAT_LONGPRESS", "✅ Menu should show, showMessageContextMenu=$showMessageContextMenu")
                            },
                            currentUsername = currentUsername,
                            currentUserId = currentUserId,
                            onToggleReaction = { messageId, emoji -> toggleReaction(messageId, emoji) },
                            onApproveRequest = { requestId ->
                                scope.launch {
                                    freeTimeApiService.approveMediaDownloadRequest(requestId)
                                    // Local update will happen via WebSocket or we can optimistically update
                                    messages = messages.map { m ->
                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                    }
                                }
                            },
                            onDenyRequest = { requestId ->
                                scope.launch {
                                    freeTimeApiService.denyMediaDownloadRequest(requestId)
                                    messages = messages.map { m ->
                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                    }
                                }
                            },
                            mediaDownloadRequests = mediaDownloadRequests,
                            mediaDownloadApprovals = mediaDownloadApprovals,
                            downloadAndSaveMediaFile = ::downloadAndSaveMediaFile,
                            onRequestDownload = { mediaId ->
                                scope.launch {
                                    freeTimeApiService.requestMediaDownload(mediaId).onSuccess {
                                        android.widget.Toast.makeText(context, "Download request sent!", android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "Failed: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            freeTimeApiService = freeTimeApiService, // ✅ Pass API service
                            token = token // ✅ Pass auth token
                        )
                    } else {
                        OtherMessageBubble(
                            message = message,
                            onLongPress = { 
                                // ✅ Long-press handler
                                android.util.Log.d("FREETIME_CHAT_LONGPRESS", "📌 Long-press on other's message: ${message.id}")
                                selectedMessageId = message.id
                                selectedMessageText = message.content
                                selectedMessageIsOwn = false
                                showMessageContextMenu = true
                                android.util.Log.d("FREETIME_CHAT_LONGPRESS", "✅ Menu should show, showMessageContextMenu=$showMessageContextMenu")
                            },
                            currentUsername = currentUsername,
                            currentUserId = currentUserId,
                            onToggleReaction = { messageId, emoji -> toggleReaction(messageId, emoji) },
                            onRequestDownload = { mediaId ->
                                scope.launch {
                                    freeTimeApiService.requestMediaDownload(mediaId).onSuccess {
                                        android.widget.Toast.makeText(context, "Download request sent!", android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, "Failed: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            mediaDownloadRequests = mediaDownloadRequests,
                            mediaDownloadApprovals = mediaDownloadApprovals,
                            downloadAndSaveMediaFile = ::downloadAndSaveMediaFile,
                            freeTimeApiService = freeTimeApiService,
                            token = token
                        )
                    }
                    }
                }
            }
            
            // ===== TYPING INDICATOR PLACEHOLDER =====
            if (newMessageText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "[typing...]",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.LightGray,
                        fontSize = 10.sp
                    )
                }
            }

            // ===== MESSAGE INPUT AREA =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                color = CyberpunkTheme.DarkGray.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment button
                        IconButton(
                            onClick = {
                                showMediaModeDialog = true
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = !isUploadingMedia
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = "Attach file (images, videos, documents, etc.)",
                                tint = if (isUploadingMedia) CyberpunkTheme.LightGray else CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // GIF button
                        IconButton(
                            onClick = { showGifPicker = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                "GIF",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (showGifPicker) Color(0xFF9B59B6) else CyberpunkTheme.PrimaryPurple
                            )
                        }
                        
                        // Emoji button
                        IconButton(
                            onClick = { showEmojiPicker = !showEmojiPicker },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.EmojiEmotions,
                                contentDescription = "Emoji",
                                tint = if (showEmojiPicker) Color(0xFFFFCB00) else CyberpunkTheme.PrimaryPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        /*
                        // Voice message button
                        IconButton(
                            onClick = { 
                                // Start voice recording
                                val voiceService = com.freetime.app.services.VoiceRecordingService(context)
                                if (voiceService.hasMicrophonePermission()) {
                                    scope.launch {
                                        try {
                                            val voiceMessage = voiceService.startRecording()
                                            voiceMessage?.let {
                                                android.util.Log.d("FREETIME_CHAT", "✓ Voice recording started")
                                                // Note: Voice recording UI not yet integrated - recording service active
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("FREETIME_CHAT", "✗ Failed to start voice recording: ${e.message}")
                                            errorMessage = "Failed to start voice recording: ${e.message}"
                                        }
                                    }
                                } else {
                                    errorMessage = "Microphone permission required for voice messages"
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Voice Message",
                                tint = CyberpunkTheme.CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        */

                        // WhatsApp-style reply preview for private chat
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (replyingToMessageId != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f))
                                        .border(1.5.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Reply to: $replyingToUsername",
                                                fontSize = 12.sp,
                                                color = CyberpunkTheme.PrimaryPurple,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = replyingToText.take(50) + if (replyingToText.length > 50) "..." else "",
                                                fontSize = 13.sp,
                                                color = CyberpunkTheme.CyberCyan,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                replyingToMessageId = null
                                                replyingToUsername = ""
                                                replyingToText = ""
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel reply",
                                                tint = CyberpunkTheme.PrimaryPurple
                                            )
                                        }
        }
    }
}

                            // Message input field
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                color = CyberpunkTheme.DarkBlack.copy(alpha = 0.6f)
                            ) {
                                TextField(
                                    value = newMessageText,
                                    onValueChange = { newMessageText = it },
                                    placeholder = {
                                        Text(
                                            "[MESSAGE_HERE]",
                                            color = CyberpunkTheme.LightGray,
                                            fontSize = 13.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp)
                                        .onFocusChanged { focusState ->
                                            isInputFocused = focusState.isFocused
                                        },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = CyberpunkTheme.PrimaryPurple
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    singleLine = false,
                                    maxLines = 3
                                )
                            }
                        }

                        // Send button
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (newMessageText.isNotEmpty() && !isSendingMessage) {
                                        // Trigger API send only (no optimistic update)
                                        sendMessage()
                                    }
                                }
                                .border(1.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(6.dp)),
                            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Send,
                                    contentDescription = "Send",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    // Emoji picker
                    if (showEmojiPicker) {
                        EmojiPickerRow(
                            onEmojiSelected = { emoji ->
                                newMessageText += emoji
                            }
                        )
                    }
                }
            }
        }
        
        // ✅ NEW: Message context menu overlay
        if (showMessageContextMenu && selectedMessageId != null) {
            val selectedMsg = messages.find { it.id == selectedMessageId }
            val hasPublicMedia = selectedMsg?.mediaId != null && selectedMsg?.mediaShareMode == "public"
            MessageContextMenu(
                messageId = selectedMessageId ?: "",
                messageText = selectedMessageText,
                isOwnMessage = selectedMessageIsOwn,
                showMenu = showMessageContextMenu,
                onDismiss = { showMessageContextMenu = false },
                onCopy = { copyMessageToClipboard() },
                onDelete = { },
                onReact = { emoji -> selectedMessageId?.let { toggleReaction(it, emoji) } },
                onReply = { selectedMessageId?.let { replyToMessage(it) } },
                onEdit = { /* TODO: Implement edit feature */ },
                currentReactions = messages.find { it.id == selectedMessageId }?.reactions ?: emptyMap(),
                hasPublicMedia = hasPublicMedia,
                onDownload = {
                    selectedMsg?.mediaId?.let { mediaId ->
                        scope.launch(Dispatchers.IO) {
                            freeTimeApiService?.downloadAndSavePublicMedia(mediaId, selectedMsg.mediaName ?: "media", selectedMsg.mediaType ?: "image", token)
                        }
                    }
                }
            )
        }

        // Media mode selection dialog (private chats)
        if (showMediaModeDialog) {
            AlertDialog(
                onDismissRequest = { showMediaModeDialog = false },
                title = { Text("Select Media Sharing Mode", color = CyberpunkTheme.PrimaryPurple) },
                text = {
                    Text(
                        "PUBLIC = Shared with the recipient, viewable immediately like WhatsApp\n\nPROTECTED = Sent with download requests (encrypted)",
                        color = CyberpunkTheme.LightGray,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingMediaShareMode = "public"
                            showMediaModeDialog = false
                            mediaPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                    ) { Text("Public", color = Color.White) }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            pendingMediaShareMode = "protected"
                            showMediaModeDialog = false
                            mediaPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12))
                    ) { Text("Protected", color = Color.White) }
                },
                containerColor = Color(0xFF1A1A2E)
            )
        }

        // GIF Picker
        GifPickerDialog(
            visible = showGifPicker,
            onDismiss = { showGifPicker = false },
            onGifSelected = { gifUrl, previewUrl ->
                scope.launch {
                    isUploadingMedia = true
                    uploadProgress = 0f
                    try {
                        val gifFile: java.io.File? = withContext(Dispatchers.IO) {
                            downloadGif(gifUrl)
                        }
                        if (gifFile != null) {
                            val fileData = withContext(Dispatchers.IO) {
                                gifFile.readBytes()
                            }
                            val fileName = "gif_${System.currentTimeMillis()}.gif"

                            val requestBody = fileData.toRequestBody("image/gif".toMediaType())
                            val multipartBody = okhttp3.MultipartBody.Part.createFormData("media", fileName, requestBody)

                            val response = apiService.uploadMediaToChat(
                                recipientId = recipientId,
                                media = multipartBody,
                                token = "Bearer $token"
                            )

                            if (response.isSuccessful && response.body()?.success == true) {
                                val mediaId = response.body()?.mediaId ?: ""
                                if (mediaId.isNotEmpty()) {
                                    val gifShareMode = pendingMediaShareMode ?: "public"
                                    messages = messages + ChatMessage(
                                        id = (messages.size + 1).toString(),
                                        senderName = currentUsername,
                                        content = "image: $fileName",
                                        timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                                        isFromCurrentUser = true,
                                        status = MessageStatus.SENT,
                                        mediaId = mediaId,
                                        mediaType = "image",
                                        mediaName = fileName,
                                        mediaShareMode = gifShareMode
                                    )
                                    // Send a message via the API so the other user receives the GIF
                                    val mediaMsgRequest = com.freetime.app.data.network.SendMessageRequest(
                                        recipientId = recipientId,
                                        content = "[Media: $mediaId] $fileName",
                                        replyToMessageId = null,
                                        replyToUsername = null,
                                        replyToText = null
                                    )
                                    apiService.sendMessage(mediaMsgRequest, "Bearer $token")
                                    errorMessage = "✓ GIF sent"
                                    pendingMediaShareMode = null
                                } else {
                                    errorMessage = "GIF uploaded but no ID returned"
                                }
                            } else {
                                errorMessage = "Failed to upload GIF: ${response.body()?.error ?: response.message()}"
                            }
                            gifFile.delete()
                        } else {
                            Toast.makeText(context, "Failed to download GIF", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("GIF_SEND", "Error sending GIF", e)
                        Toast.makeText(context, "Failed to send GIF: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingMedia = false
                        uploadProgress = 0f
                    }
                }
            }
        )
    }
    
    /*
    // Audio Call Dialog
    if (showAudioCallDialog) {
        AlertDialog(
            onDismissRequest = { showAudioCallDialog = false },
            title = { Text("Start Audio Call") },
            text = {
                Text("Start an audio call with $chatName?\n\nThe call will be initiated and they will receive a notification.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAudioCallDialog = false
                        scope.launch {
                            try {
                                isSendingMessage = true
                            // Use real WebRTC service for call initiation
                                val callService = com.freetime.app.services.CallManagementService(context)
                                val result = callService.initiateCall(recipientId, chatName, com.freetime.app.services.CallManagementService.CallType.AUDIO)
                                isSendingMessage = false
                                
                                result.fold(
                                    onSuccess = { callId ->
                                        android.util.Log.d("FREETIME_CHAT", "✓ Audio call initiated with $chatName - Call ID: $callId")
                                        errorMessage = "Audio call initiated with $chatName! 📞 Call ID: $callId"
                                        // Note: CallScreen navigation not yet implemented - call ID returned for future use
                                    },
                                    onFailure = { error ->
                                        android.util.Log.e("FREETIME_CHAT", "✗ Failed to initiate audio call: ${error.message}")
                                        errorMessage = "Failed to start audio call: ${error.message}"
                                    }
                                )
                            } catch (e: Exception) {
                                isSendingMessage = false
                                android.util.Log.e("FREETIME_CHAT", "✗ Exception during call initiation: ${e.message}")
                                errorMessage = "Error: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Start Call", color = CyberpunkTheme.CyberCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAudioCallDialog = false }) {
                    Text("Cancel", color = CyberpunkTheme.PrimaryPurple)
                }
            },
            containerColor = CyberpunkTheme.DarkGray
        )
    }
    */
    
    /*
    // Video Call Dialog
    if (showVideoCallDialog) {
        AlertDialog(
            onDismissRequest = { showVideoCallDialog = false },
            title = { Text("Start Video Call") },
            text = {
                Text("Start a video call with $chatName?\n\nThe call will be initiated and they will receive a notification.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVideoCallDialog = false
                        scope.launch {
                            try {
                                isSendingMessage = true
                                // Use real WebRTC service for call initiation
                                val callService = com.freetime.app.services.CallManagementService(context)
                                val result = callService.initiateCall(recipientId, chatName, com.freetime.app.services.CallManagementService.CallType.VIDEO)
                                isSendingMessage = false
                                
                                result.fold(
                                    onSuccess = { callId ->
                                        android.util.Log.d("FREETIME_CHAT", "✓ Video call initiated with $chatName - Call ID: $callId")
                                        errorMessage = "Video call initiated with $chatName! 📹 Call ID: $callId"
                                        // Note: CallScreen navigation not yet implemented - call ID returned for future use
                                    },
                                    onFailure = { error ->
                                        android.util.Log.e("FREETIME_CHAT", "✗ Failed to initiate video call: ${error.message}")
                                        errorMessage = "Failed to start video call: ${error.message}"
                                    }
                                )
                            } catch (e: Exception) {
                                isSendingMessage = false
                                android.util.Log.e("FREETIME_CHAT", "✗ Exception during call initiation: ${e.message}")
                                errorMessage = "Error: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Start Call", color = CyberpunkTheme.CyberCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoCallDialog = false }) {
                    Text("Cancel", color = CyberpunkTheme.PrimaryPurple)
                }
            },
            containerColor = CyberpunkTheme.DarkGray
        )
    }
    */
}

@Composable
fun EmojiPickerRow(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf(
        "�", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "��", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
        "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
        "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
        "🥵", "🥶", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕",
        "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨",
        "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩",
        "😫", "🥱", "😤", "😡", "😠", "🤬", "�", "👿", "💀", "☠️",
        "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "❤️", "🧡",
        "💛", "�", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
        "💞", "💓", "💗", "💖", "💘", "💝", "��", "👎", "👌", "✌️",
        "🤞", "🤟", "🤘", "🤙", "�", "�", "�", "�", "☝️", "✋",
        "🤚", "�️", "🖖", "👋", "�", "💪", "🙏", "🎉", "🎊", "🎈",
        "🔥", "⭐", "✨", "💫", "☀️", "🌙", "🌈", "⚡", "🌟", "�"
    )
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberpunkTheme.Black.copy(alpha = 0.8f))
            .padding(8.dp)
            .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(emojis) { emoji ->
            Button(
                onClick = { onEmojiSelected(emoji) },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                    contentColor = Color.Unspecified
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(emoji, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun AvatarImage(
    avatarUrl: String?,
    senderName: String,
    size: Dp = 32.dp
) {
    val context = LocalContext.current
    val apiService = remember { com.freetime.app.api.FreeTimeApiService(context) }
    val resolvedUrl = apiService.resolveAvatarUrl(avatarUrl)
    
    if (!resolvedUrl.isNullOrEmpty()) {
        // Try to load the actual avatar image from URL
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolvedUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$senderName's avatar",
            modifier = Modifier
                .size(width = size, height = size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = {
                // Image failed to load, will show fallback from placeholder
                android.util.Log.w("FREETIME_AVATAR", "Failed to load avatar: $resolvedUrl")
            }
        )
    } else {
        // Fallback: Show placeholder with initials
        Surface(
            modifier = Modifier.size(width = size, height = size),
            shape = CircleShape,
            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    senderName.take(1).uppercase(),
                    color = CyberpunkTheme.PrimaryPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun UserMessageBubble(
    message: ChatMessage,
    onLongPress: () -> Unit = {},
    currentUsername: String = "",
    currentUserId: String = "",
    onToggleReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onApproveRequest: (requestId: String) -> Unit = {},
    onDenyRequest: (requestId: String) -> Unit = {},
    mediaDownloadRequests: Map<String, String> = emptyMap(),
    mediaDownloadApprovals: Map<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData> = emptyMap(),
    downloadAndSaveMediaFile: (com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) -> Unit = {},
    onRequestDownload: (String) -> Unit = {},
    freeTimeApiService: FreeTimeApiService? = null, // ✅ NEW: API service for direct downloads
    token: String = "" // ✅ NEW: Auth token for downloads
) {
    var isPressed by remember { mutableStateOf(false) }
    val localScope = rememberCoroutineScope()
    val context = LocalContext.current
    var downloadProgress by remember { mutableStateOf(0f) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                .border(1.5.dp, CyberpunkTheme.PrimaryPurple, RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isPressed = true
                            onLongPress()
                        }
                    )
                },
            color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show media if present
                if (message.mediaId != null) {
                    val mediaTypeLabel = message.mediaType ?: "file"
                    val isImage = message.mediaType == "image"
                    val isVideo = message.mediaType == "video"
                    val isGif = message.mediaName?.lowercase()?.endsWith(".gif") == true
                    val status = mediaDownloadRequests[message.mediaId]
                    val approvalData = mediaDownloadApprovals[message.mediaId]
                    val isPublicMedia = message.mediaShareMode == "public"
                    val iconVector = when {
                        isImage || isGif -> Icons.Filled.Image
                        isVideo -> Icons.Filled.Videocam
                        else -> Icons.Filled.AttachFile
                    }
                    val mediaDisplayName = if (message.mediaName.isNullOrEmpty() || message.mediaName == "null") null else message.mediaName
                    val fileLabel = when {
                        isImage -> "image: ${mediaDisplayName ?: "image"}"
                        isVideo -> "video: ${mediaDisplayName ?: "video"}"
                        else -> "file: ${mediaDisplayName ?: "file"}"
                    }
                    // ✅ Inline image preview for public media
                    if (isPublicMedia && (isImage || isGif)) {
                        val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()
                        val imageUrl = "$baseUrl/api/media/${message.mediaId}/download"
                        val imageRequest = coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .crossfade(true)
                            .size(800)
                            .build()
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = message.mediaName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberpunkTheme.DarkBlack.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!isPublicMedia && status != "approved") {
                                Icon(Icons.Filled.Lock, contentDescription = "Protected", tint = CyberpunkTheme.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                            } else {
                                Icon(iconVector, contentDescription = mediaTypeLabel, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                fileLabel,
                                color = CyberpunkTheme.PrimaryPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isPublicMedia) {
                                Text(
                                    "📤 Public - long-press to download",
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    when {
                                        status == "approved" -> "Approved - tap to download"
                                        else -> "Protected media — tap to request"
                                    },
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 9.sp
                                )
                            }
                            if (downloadProgress > 0f && downloadProgress < 1f) {
                                DownloadProgressBar(
                                    progress = downloadProgress,
                                    fileName = mediaDisplayName ?: "Downloading...",
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!isPublicMedia) {
                            Button(onClick = {
                                if (status == "approved" && approvalData != null) {
                                    // ✅ PROTECTED: Download encrypted media with key
                                    localScope.launch(Dispatchers.IO) {
                                        try {
                                            if (approvalData.encrypted && approvalData.encryptionKey != null) {
                                                android.util.Log.d("FREETIME_CHAT", "📥 Downloading PROTECTED media: ${message.mediaId}")
                                                
                                                // ✅ FIXED: Use filename from approval if message doesn't have it
                                                var fileNameToUse = message.mediaName 
                                                    ?: approvalData.fileName 
                                                    ?: "media_${System.currentTimeMillis()}"
                                                
                                                // ✅ FIX: Ensure filename has correct extension
                                                if (!fileNameToUse.contains('.')) {
                                                    val extension = when (message.mediaType?.lowercase()) {
                                                        "video" -> ".mp4"
                                                        "image" -> ".jpg"
                                                        else -> ".bin"
                                                    }
                                                    fileNameToUse = "$fileNameToUse$extension"
                                                }
                                                
                                                downloadProgress = 0.1f
                                                val result = freeTimeApiService?.downloadMediaFile(
                                                    message.mediaId!!,
                                                    fileNameToUse,
                                                    message.mediaType ?: "image",
                                                    approvalData.encryptionKey!!,
                                                    onProgress = { p -> downloadProgress = p }
                                                )
                                                result?.onSuccess {
                                                    downloadProgress = 1f
                                                    android.util.Log.d("FREETIME_CHAT", "✅ Protected media saved")
                                                    Toast.makeText(context, "📥 Downloaded: $fileNameToUse", Toast.LENGTH_SHORT).show()
                                                }?.onFailure {
                                                    downloadProgress = 0f
                                                    android.util.Log.e("FREETIME_CHAT", "Failed to save protected media: ${it.message}")
                                                    Toast.makeText(context, "❌ Download failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("FREETIME_CHAT", "Download error: ${e.message}", e)
                                        }
                                    }
                                } else {
                                    message.mediaId?.let { onRequestDownload(it) }
                                }
                            }) {
                                Text(when {
                                    status == "approved" -> "Download"
                                    else -> "Request"
                                })
                            }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ✅ FIXED: Show reply context if this message is replying to another
                // Show if ANY reply field is present (not requiring all fields from server)
                if ((!message.replyToMessageId.isNullOrEmpty() && message.replyToMessageId != "null") ||
                    (!message.replyToUsername.isNullOrEmpty() && message.replyToUsername != "null") ||
                    (!message.replyToText.isNullOrEmpty() && message.replyToText != "null")) {
                    // ✅ FIXED: Use defaults if fields are missing
                    val replyUsername = if (message.replyToUsername.isNullOrEmpty() || message.replyToUsername == "null") "Unknown" else message.replyToUsername
                    val replyTextContent = if (message.replyToText == "null" || message.replyToText.isNullOrEmpty()) "(Message)" else message.replyToText
                    
                    Surface(
                        color = CyberpunkTheme.DarkBlack.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .fillMaxWidth()
                        ) {
                            // Left accent bar
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        CyberpunkTheme.CyberCyan,
                                        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Reply,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = CyberpunkTheme.CyberCyan
                                    )
                                    Text(
                                        text = replyUsername,
                                        fontSize = 11.sp,
                                        color = CyberpunkTheme.CyberCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = replyTextContent.take(100),
                                    fontSize = 11.sp,
                                    color = CyberpunkTheme.LightGray.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                if (message.mediaId == null) {
                    ClickableMessageText(
                        text = message.content,
                        textColor = CyberpunkTheme.PrimaryPurple,
                        onLongPress = onLongPress
                    )
                }

                // ✅ NEW: Show pending download requests for media
                if (message.pendingRequests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Download Requests:",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberpunkTheme.CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                            message.pendingRequests.forEach { request ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        request.requesterName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(
                                            onClick = { onApproveRequest(request.requestId) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onDenyRequest(request.requestId) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberpunkTheme.LightGray,
                        fontSize = 9.sp
                    )

                    when (message.status) {
                        MessageStatus.SENDING -> {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "Sending",
                                tint = CyberpunkTheme.LightGray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        MessageStatus.SENT -> {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Sent",
                                tint = CyberpunkTheme.LightGray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        MessageStatus.DELIVERED -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(-4.dp)) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Check",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Check",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        MessageStatus.READ -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(-4.dp)) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Check",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Check",
                                    tint = CyberpunkTheme.PrimaryPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Reactions - Clickable to toggle/remove
        if (message.reactions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(message.reactions.size) { index ->
                    val (emoji, users) = message.reactions.toList()[index]
                    val isCurrentUserReacted = users.contains(currentUserId)
                    
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // Click to toggle: if user already reacted, remove it; otherwise add it
                                onToggleReaction(message.id, emoji)
                            },
                        color = if (isCurrentUserReacted) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f) else CyberpunkTheme.Black.copy(alpha = 0.7f),
                        border = if (isCurrentUserReacted) BorderStroke(1.5.dp, CyberpunkTheme.PrimaryPurple) else BorderStroke(1.dp, CyberpunkTheme.CyberCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp, 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 12.sp)
                            Text(users.size.toString(), fontSize = 10.sp, color = CyberpunkTheme.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OtherMessageBubble(
    message: ChatMessage,
    onLongPress: () -> Unit = {},
    currentUsername: String = "",
    currentUserId: String = "",
    onToggleReaction: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onRequestDownload: (String) -> Unit = {},
    mediaDownloadRequests: Map<String, String> = emptyMap(),
    mediaDownloadApprovals: Map<String, com.freetime.app.services.WebSocketManager.MediaDownloadResponseData> = emptyMap(),
    downloadAndSaveMediaFile: (com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) -> Unit = {},
    freeTimeApiService: FreeTimeApiService? = null, // ✅ NEW: API service for direct downloads
    token: String = "" // ✅ NEW: Auth token for downloads
) {
    var isPressed by remember { mutableStateOf(false) }
    val localScope = rememberCoroutineScope()
    val mediaApprovalStatus = if (message.mediaId != null) mediaDownloadRequests[message.mediaId] else null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // Display avatar - either actual image or placeholder with initials
        AvatarImage(
            avatarUrl = message.senderAvatar,
            senderName = message.senderName,
            size = 32.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp))
                .border(1.5.dp, CyberpunkTheme.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isPressed = true
                            onLongPress()
                        }
                    )
                },
            color = CyberpunkTheme.DarkGray.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberpunkTheme.PrimaryPurple,
                    fontSize = 10.sp
                )

                // Show media if present
                if (message.mediaId != null) {
                    val context = LocalContext.current
                    val mediaTypeLabel = message.mediaType ?: "file"
                    val isImage = message.mediaType == "image"
                    val isVideo = message.mediaType == "video"
                    val isGif = message.mediaName?.lowercase()?.endsWith(".gif") == true
                    val iconVector = when {
                        isVideo -> Icons.Filled.Videocam
                        isImage || isGif -> Icons.Filled.Image
                        else -> Icons.Filled.AttachFile
                    }
                    val mediaDisplayName = if (message.mediaName.isNullOrEmpty() || message.mediaName == "null") null else message.mediaName
                    val fileLabel = when {
                        isImage -> "image: ${mediaDisplayName ?: "image"}"
                        isVideo -> "video: ${mediaDisplayName ?: "video"}"
                        else -> "file: ${mediaDisplayName ?: "file"}"
                    }
                    val isPublicMedia = message.mediaShareMode == "public"
                    // ✅ Inline image preview for public media (Other's messages)
                    if (isPublicMedia && (isImage || isGif)) {
                        val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()
                        val imageUrl = "$baseUrl/api/media/${message.mediaId}/download"
                        val imageRequest = coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .crossfade(true)
                            .size(800)
                            .build()
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = message.mediaName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (isPublicMedia) {
                        Text(
                            "📤 Public - long-press to download",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 9.sp
                        )
                    } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberpunkTheme.DarkBlack.copy(alpha = 0.7f))
                            .clickable {
                                // Request media download permission from sender
                                message.mediaId?.let { onRequestDownload(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaApprovalStatus == "approved") {
                            val approvalData = mediaDownloadApprovals[message.mediaId]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(iconVector, contentDescription = mediaTypeLabel, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    fileLabel,
                                    color = CyberpunkTheme.PrimaryPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Approved - tap to download", color = CyberpunkTheme.LightGray, fontSize = 9.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    if (approvalData != null) {
                                        localScope.launch(Dispatchers.IO) {
                                            try {
                                                downloadAndSaveMediaFile(approvalData)
                                            } catch (e: Exception) {
                                                android.util.Log.e("FREETIME_CHAT", "Manual download failed: ${e.message}")
                                            }
                                        }
                                    }
                                }) { Text("Download") }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    iconVector,
                                    contentDescription = mediaTypeLabel,
                                    tint = when (mediaApprovalStatus) {
                                        "approved" -> Color.Green
                                        "pending" -> Color.Yellow
                                        "denied" -> Color.Red
                                        else -> CyberpunkTheme.CyberCyan
                                    },
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    fileLabel,
                                    color = CyberpunkTheme.PrimaryPurple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    when (mediaApprovalStatus) {
                                        "approved" -> if (isVideo) "Ready to view" else "Ready to download"
                                        "pending" -> "⏳ Waiting for approval..."
                                        "denied" -> "❌ Download denied"
                                        else -> "Unlock to view $mediaTypeLabel"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberpunkTheme.LightGray,
                                    fontSize = 10.sp,
                                    maxLines = 2
                                )
                                if (mediaApprovalStatus == null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tap to download",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberpunkTheme.CyberCyan,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        }
                    }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ✅ FIXED: Show reply context if this message is replying to another
                // Show if ANY reply field is present (not requiring all fields from server)
                if ((!message.replyToMessageId.isNullOrEmpty() && message.replyToMessageId != "null") ||
                    (!message.replyToUsername.isNullOrEmpty() && message.replyToUsername != "null") ||
                    (!message.replyToText.isNullOrEmpty() && message.replyToText != "null")) {
                    // ✅ FIXED: Use defaults if fields are missing
                    val replyUsername = if (message.replyToUsername.isNullOrEmpty() || message.replyToUsername == "null") "Unknown" else message.replyToUsername
                    val replyTextContent = if (message.replyToText == "null" || message.replyToText.isNullOrEmpty()) "(Message)" else message.replyToText
                    
                    Surface(
                        color = CyberpunkTheme.DarkBlack.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .fillMaxWidth()
                        ) {
                            // Left accent bar
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        CyberpunkTheme.PrimaryPurple,
                                        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Reply,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = CyberpunkTheme.PrimaryPurple
                                    )
                                    Text(
                                        text = replyUsername,
                                        fontSize = 11.sp,
                                        color = CyberpunkTheme.PrimaryPurple,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = replyTextContent.take(100),
                                    fontSize = 11.sp,
                                    color = CyberpunkTheme.LightGray.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                if (message.mediaId == null) {
                    ClickableMessageText(
                        text = message.content,
                        textColor = CyberpunkTheme.LightGray,
                        onLongPress = onLongPress
                    )
                }

                Text(
                    message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberpunkTheme.LightGray,
                    fontSize = 9.sp
                )
            }
        }
        
        // Reactions - Clickable to toggle/remove
        if (message.reactions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(message.reactions.size) { index ->
                    val (emoji, users) = message.reactions.toList()[index]
                    val isCurrentUserReacted = users.contains(currentUserId)
                    
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // Click to toggle: if user already reacted, remove it; otherwise add it
                                onToggleReaction(message.id, emoji)
                            },
                        color = if (isCurrentUserReacted) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f) else CyberpunkTheme.Black.copy(alpha = 0.7f),
                        border = if (isCurrentUserReacted) BorderStroke(1.5.dp, CyberpunkTheme.PrimaryPurple) else BorderStroke(1.dp, CyberpunkTheme.CyberCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp, 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 12.sp)
                            Text(users.size.toString(), fontSize = 10.sp, color = CyberpunkTheme.LightGray)
                        }
                    }
                }
            }
        }
    }
}


