package com.freetime.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.focus.onFocusChanged
import com.freetime.app.R
import com.freetime.app.ui.composables.MessageContextMenu
import com.freetime.app.ui.composables.GifPickerDialog
import com.freetime.app.ui.components.CyberpunkTheme
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.SendMessageRequest
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.model.CallState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.freetime.app.api.FreeTimeApiService
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract
import com.freetime.app.services.CallStateManager
import com.freetime.app.ui.components.*
import java.util.UUID
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.MediaStream
import org.json.JSONObject
import com.freetime.app.webrtc.WebRTCFactory
import com.freetime.app.webrtc.WebRTCManager
import com.freetime.app.webrtc.toJson
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

// ✅ NEW: Helper function to get username color based on tags and role
fun getUsernameColor(
    tags: List<String> = emptyList(),
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    role: String? = null  // ✅ NEW: Accept role parameter for flexibility
): Color {
    // ✅ CRITICAL FIX: Default to white if no tags/roles provided
    if (tags.isEmpty() && !isAdmin && !isModerator && role.isNullOrBlank()) {
        return CyberpunkTheme.White
    }
    
    return when {
        // Priority 1: OWNER tag → Bright Magenta
        tags.contains("OWNER") -> Color(0xFFFF00FF)  // Bright Magenta
        // Priority 2: VIP tag → Yellow
        tags.contains("VIP") -> Color(0xFFFFFF00)  // Bright Yellow
        // Priority 3: BETA TESTER tag → Cyan
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)  // Bright Cyan
        // Priority 4: isAdmin or role=admin → Red
        isAdmin || role == "admin" || role == "ADMIN" -> Color(0xFFFF0000)  // Bright Red
        // Priority 5: isModerator or role=moderator → Orange
        isModerator || role == "moderator" || role == "MODERATOR" -> Color(0xFFFF8C00)  // Bright Orange
        // Default → White
        else -> CyberpunkTheme.White
    }
}

data class Message(
    val id: String,
    val content: String,
    val senderName: String,
    val isSender: Boolean,
    val timestamp: String,
    val isRead: Boolean = true,
    val hasReaction: String? = null,
    val reactions: List<String> = emptyList(),
    val status: String = "sent", // "sending", "sent", "delivered", "failed"
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val senderId: String = "",
    val mediaId: String? = null,
    val mediaType: String? = null,  // ✅ NEW: Media type (image/video/document)
    val mediaName: String? = null,  // ✅ NEW: Original filename with extension
    val pendingRequests: List<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData> = emptyList(),
    val senderTags: List<String> = emptyList(),  // ✅ NEW: Tags for color coding
    val senderIsAdmin: Boolean = false,  // ✅ NEW: Admin status
    val senderIsModerator: Boolean = false,  // ✅ NEW: Moderator status
    val senderRole: String = "",  // ✅ NEW: Role for flexible color matching
    val subject: String? = null,  // ✅ NEW: Announcement subject
    val isAnnouncement: Boolean = false,  // ✅ NEW: Announcement flag
    val mediaShareMode: String? = null  // ✅ NEW: "public" or "protected"
)

fun parseReactions(json: String): List<String> {
    if (json.isEmpty()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun inferMediaShareMode(content: String): String? {
    val regex = """^\[Media: ([^|\]]+)(?:\|([^\]]*))?\]""".toRegex()
    val match = regex.find(content)
    return when {
        match == null -> null
        match.groupValues.getOrNull(2).isNullOrEmpty() -> "public"
        else -> "protected"
    }
}

// Helper function to build clickable text with URL detection
fun buildClickableText(text: String): AnnotatedString {
    val urlRegex = """(https?://[^\s]+|www\.[^\s]+)""".toRegex()
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        urlRegex.findAll(text).forEach { match ->
            // Add text before URL
            append(text.substring(lastIndex, match.range.first))
            
            // Add URL as clickable text
            val url = match.value
            val fullUrl = if (url.startsWith("www.")) "https://${url}" else url
            pushStringAnnotation(
                tag = "URL",
                annotation = fullUrl
            )
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF00D4FF),
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(url)
            }
            pop()
            
            lastIndex = match.range.last + 1
        }
        // Add remaining text after last URL
        append(text.substring(lastIndex))
    }
    return annotatedString
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernChatScreen(
    recipientId: String,
    chatName: String = "",
    acceptCallOnOpen: Boolean = false,
    pendingCallId: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onViewProfile: (userId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = ApiClient.getInstance()
    val prefs = SharedPreferencesHelper(context)
    val apiService = remember { FreeTimeApiService(context) }
    val currentUserId = prefs.getUserId() ?: ""
    val currentUsername = prefs.getUsername() ?: "You"
    
    // ✅ NEW: Initialize Database and Repository for local encrypted storage
    val database = remember { com.freetime.app.data.local.database.FreeTimeDatabase.getInstance(context) }
    val encryptionManager = remember { com.freetime.app.data.local.encryption.EncryptionManager(context) }
    val messageRepository = remember { com.freetime.app.data.repository.MessageRepository(database, encryptionManager, context) }
    
    var messageText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var showMascotTip by remember { mutableStateOf(true) }
    var isTyping by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    
    var isSendingMessage by remember { mutableStateOf(false) }
    var recipientName by remember { mutableStateOf(chatName) }
    var recipientIsOnline by remember { mutableStateOf(false) }
    var recipientExists by remember { mutableStateOf(true) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var deleteHistoryStatus by remember { mutableStateOf("") }
    var mediaDownloadRequests by remember { mutableStateOf(mapOf<String, String>()) }
    var showMediaRequestDialog by remember { mutableStateOf<String?>(null) }
    var visibleImageMediaIds by remember { mutableStateOf(setOf<String>()) }
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    
    // ✅ NEW: Message context menu state
    var showMessageContextMenu by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageText by remember { mutableStateOf("") }
    var selectedMessageIsOwn by remember { mutableStateOf(false) }

    var isFriend by remember { mutableStateOf(false) } // Track if recipient is a friend
    var recipientTags by remember { mutableStateOf(listOf<String>()) }  // ✅ NEW: Recipient's tags for color coding
    var recipientIsAdmin by remember { mutableStateOf(false) }  // ✅ NEW: Recipient's admin status
    var recipientIsModerator by remember { mutableStateOf(false) }  // ✅ NEW: Recipient's moderator status
    var recipientRole by remember { mutableStateOf("") }  // ✅ NEW: Recipient's role for flexible color matching
    val isAnnouncementChat = recipientId == com.freetime.app.api.ANNOUNCEMENT_USER_ID
    // Announcement auto-disappear after 3 minutes
    val announcementDeliveredAt = remember { mutableStateOf(mutableMapOf<String, Long>()) }
    val announcementNow = remember { mutableStateOf(System.currentTimeMillis()) }
    if (isAnnouncementChat) {
        LaunchedEffect(Unit) {
            while (true) {
                announcementNow.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
        LaunchedEffect(messages) {
            messages.forEach { msg ->
                if (!announcementDeliveredAt.value.containsKey(msg.id)) {
                    announcementDeliveredAt.value[msg.id] = System.currentTimeMillis()
                }
            }
        }
    }
    // ✅ DEBOUNCE: Last send time to prevent duplicate sends
    var lastSendTimeMs by remember { mutableStateOf(0L) }
    val SEND_DEBOUNCE_MS = 500L // Prevent double-taps within 500ms

    // ===== MESSAGE SEARCH =====
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Message>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf("") }

    // ===== TYPING INDICATOR =====
    var isRecipientTyping by remember { mutableStateOf(false) }
    var typingTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastTypingIndicatorSentMs by remember { mutableStateOf(0L) }
    val TYPING_INDICATOR_DEBOUNCE_MS = 1000L // Send at most once per second

    // ===== CONNECTION STATUS =====
    var wsConnected by remember { mutableStateOf(false) }
    
    // Monitor WebSocket connection status
    LaunchedEffect(Unit) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        while (true) {
            wsConnected = wsManager.isConnected()
            delay(1000) // Check every second
        }
    }

    // Check friendship status
    LaunchedEffect(recipientId) {
        try {
            val result = apiService.getFriends()
            result.onSuccess { friends ->
                isFriend = friends.any { it.userId == recipientId }
                android.util.Log.d("FREETIME_CHAT", "Friendship status for $recipientId: $isFriend")
            }.onFailure {
                android.util.Log.e("FREETIME_CHAT", "Failed to check friendship status: ${it.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_CHAT", "Error checking friendship: ${e.message}")
        }
    }

    // ✅ FIX: Update message sender names once recipient name is loaded (fixes "Friend" fallback)
    LaunchedEffect(recipientName) {
        if (recipientName.isNotEmpty() && recipientName != "User" && messages.isNotEmpty()) {
            // Check if any messages still have "Friend" as sender name that should be updated
            messages = messages.map { message ->
                if (!message.isSender && (message.senderName == "Friend" || message.senderName.isEmpty())) {
                    message.copy(senderName = recipientName)
                } else {
                    message
                }
            }
            android.util.Log.d("FREETIME_CHAT", "✅ Updated message sender names with recipient: $recipientName")
        }
    }

    // View-once timer logic (3 seconds)
    LaunchedEffect(visibleImageMediaIds) {
        if (visibleImageMediaIds.isNotEmpty()) {
            val recentlyAdded = visibleImageMediaIds.last()
            delay(3000)
            visibleImageMediaIds = visibleImageMediaIds - recentlyAdded
            android.util.Log.d("FREETIME_MEDIA", "View-once expired for: $recentlyAdded")
        }
    }
    
    // Function to handle media download request
    fun sendMediaDownloadRequests(mediaIds: List<String>) {
        scope.launch {
            try {
                // Request each media individually
                var successCount = 0
                var failureCount = 0
                
                for (mediaId in mediaIds) {
                    try {
                        val result = apiService.requestMediaDownload(mediaId)
                        result.onSuccess {
                            android.util.Log.d("FREETIME_MEDIA", "Download request sent for $mediaId")
                            mediaDownloadRequests = mediaDownloadRequests + (mediaId to "pending")
                            successCount++
                        }
                        result.onFailure { error ->
                            android.util.Log.e("FREETIME_MEDIA", "Failed: ${error.message}")
                            failureCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_MEDIA", "Exception: ${e.message}")
                        failureCount++
                    }
                }

                val msg = if (successCount > 0) "Sent $successCount request(s)" else "Failed to send requests"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                isMultiSelectMode = false
                selectedMessages = emptySet()
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_MEDIA", "Error: ${e.message}")
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // ✅ NEW: Download and save media file when approval received
    fun downloadAndSaveMediaFile(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData, originalFileName: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("FREETIME_MEDIA", "🎬 Starting auto-download of approved media: ${data.mediaId}")
                
                // Build the download URL
                val downloadUrl = if (data.downloadUrl?.startsWith("http") == true) {
                    data.downloadUrl
                } else if (!data.downloadUrl.isNullOrEmpty()) {
                    val baseUrl = apiService.getBaseUrl()
                    "${baseUrl.trimEnd('/')}${data.downloadUrl}"
                } else {
                    android.util.Log.e("FREETIME_MEDIA", "No download URL provided in approval")
                    return@launch
                }
                
                // Step 1: Download encrypted bytes
                val token = prefs.getToken() ?: return@launch
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
                return@launch
            }
                
                val encryptedBytes = response.body?.bytes() ?: return@launch
                android.util.Log.d("FREETIME_MEDIA", "Downloaded ${encryptedBytes.size} bytes")
                
                // Step 2: Decrypt if needed
                val decryptedBytes = if (data.encrypted && !data.encryptionKey.isNullOrEmpty() && data.encryptionKey != "client-side") {
                    try {
                        val encryptor = com.freetime.app.security.MediaEncryption(context)
                        encryptor.decryptMedia(encryptedBytes, data.encryptionKey)
                    } catch (decryptError: Exception) {
                        android.util.Log.e("FREETIME_MEDIA", "Decryption failed: ${decryptError.message}")
                        return@launch
                    }
                } else {
                    encryptedBytes
                }
                
                // Step 3: Determine media type
                val mimeType = data.mimeType ?: when {
                    data.fileName?.endsWith(".mp4") == true || data.fileName?.endsWith(".mov") == true || data.fileName?.endsWith(".avi") == true || data.fileName?.endsWith(".mkv") == true || data.fileName?.endsWith(".webm") == true || data.fileName?.endsWith(".flv") == true || data.fileName?.endsWith(".wmv") == true -> "video/mp4"
                    data.fileName?.endsWith(".png") == true -> "image/png"
                    data.fileName?.endsWith(".jpg") == true || data.fileName?.endsWith(".jpeg") == true -> "image/jpeg"
                    data.fileName?.endsWith(".gif") == true -> "image/gif"
                    data.fileName?.endsWith(".webp") == true -> "image/webp"
                    data.fileName?.endsWith(".bmp") == true -> "image/bmp"
                    data.fileName?.endsWith(".svg") == true -> "image/svg+xml"
                    data.fileName?.endsWith(".mp3") == true || data.fileName?.endsWith(".wav") == true || data.fileName?.endsWith(".flac") == true || data.fileName?.endsWith(".aac") == true || data.fileName?.endsWith(".ogg") == true || data.fileName?.endsWith(".wma") == true -> "audio/mpeg"
                    data.fileName?.endsWith(".pdf") == true -> "application/pdf"
                    data.fileName?.endsWith(".zip") == true || data.fileName?.endsWith(".rar") == true || data.fileName?.endsWith(".7z") == true || data.fileName?.endsWith(".tar") == true || data.fileName?.endsWith(".gz") == true -> "application/zip"
                    data.fileName?.endsWith(".doc") == true || data.fileName?.endsWith(".docx") == true -> "application/msword"
                    data.fileName?.endsWith(".xls") == true || data.fileName?.endsWith(".xlsx") == true -> "application/vnd.ms-excel"
                    data.fileName?.endsWith(".ppt") == true || data.fileName?.endsWith(".pptx") == true -> "application/vnd.ms-powerpoint"
                    data.fileName?.endsWith(".txt") == true -> "text/plain"
                    data.fileName?.endsWith(".csv") == true -> "text/csv"
                    data.fileName?.endsWith(".json") == true -> "application/json"
                    data.fileName?.endsWith(".xml") == true -> "application/xml"
                    data.fileName?.endsWith(".apk") == true -> "application/vnd.android.package-archive"
                    else -> "application/octet-stream"
                }
                
                val mediaType = when {
                    mimeType.startsWith("image") -> "image"
                    mimeType.startsWith("video") -> "video"
                    mimeType.startsWith("audio") -> "audio"
                    else -> "document"
                }
                
                // Step 4: Save to private app storage for automatic cleanup on uninstall
                var finalFileName = originalFileName ?: data.fileName ?: "media_${System.currentTimeMillis()}"
                // Strip any non-standard extension (e.g. .media) — use MIME type as source of truth
                val baseName = finalFileName.substringBeforeLast(".")
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: finalFileName.substringAfterLast(".", "").takeIf { it.length <= 4 && it.all { c -> c.isLetterOrDigit() } }
                finalFileName = if (!ext.isNullOrEmpty()) "$baseName.$ext" else finalFileName
                
                android.util.Log.d("FREETIME_MEDIA", "✅ Saving media to public storage: $finalFileName")
                val saved = if (android.os.Build.VERSION.SDK_INT >= 29) {
                    // Android 10+ — use MediaStore for public visibility
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        when (mediaType) {
                            "image" -> {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/FreeTime")
                            }
                            "video" -> {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MOVIES}/FreeTime")
                            }
                            else -> {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/FreeTime")
                            }
                        }
                    }
                    val collectionUri = when (mediaType) {
                        "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else -> android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    }
                    try {
                        val uri = context.contentResolver.insert(collectionUri, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(decryptedBytes) }
                            true
                        } else false
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_MEDIA", "MediaStore save failed: ${e.message}")
                        false
                    }
                } else {
                    // Android 9 and below — use public external storage
                    try {
                        val targetDir = when (mediaType) {
                            "image" -> android.os.Environment.DIRECTORY_PICTURES
                            "video" -> android.os.Environment.DIRECTORY_MOVIES
                            else -> android.os.Environment.DIRECTORY_DOWNLOADS
                        }
                        val mediaDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(targetDir), "FreeTime")
                        if (!mediaDir.exists()) mediaDir.mkdirs()
                        val file = java.io.File(mediaDir, finalFileName)
                        file.writeBytes(decryptedBytes)
                        // Notify media scanner
                        val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        intent.data = android.net.Uri.fromFile(file)
                        context.sendBroadcast(intent)
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_MEDIA", "Legacy save failed: ${e.message}")
                        false
                    }
                }
                if (saved) {
                    android.util.Log.d("FREETIME_MEDIA", "✅ Media saved to public storage: $finalFileName")
                    val displayType = when (mediaType) {
                        "image" -> "Image"
                        "video" -> "Video"
                        "audio" -> "Audio"
                        else -> "File"
                    }
                    val friendlyName = finalFileName.substringBeforeLast(".").take(30)
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "$displayType saved: $friendlyName ($mediaType)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.util.Log.e("FREETIME_MEDIA", "❌ Failed to save media to public storage, falling back to private")
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Media saved to app storage", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_MEDIA", "Error downloading media: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download media: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Media file picker state
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaPickerError by remember { mutableStateOf("") }
    var isProcessingMedia by remember { mutableStateOf(false) }
    
    // Coming soon popup state
    var showComingSoonPopup by remember { mutableStateOf(false) }
    
    // Voice call state - integrated into chat
    // Use CallStateManager for thread-safe state management across WebSocket/UI/timeout threads
    val callStateManager = remember { CallStateManager() }
    var callState by remember { mutableStateOf<CallState?>(null) }
    var callDurationSeconds by remember { mutableStateOf(0) }
    var callId by remember { mutableStateOf("") }
    var callErrorMessage by remember { mutableStateOf("") }
    var pendingIncomingSdpOffer by remember { mutableStateOf("") }
    var incomingCallType by remember { mutableStateOf("audio") }  // ✅ NEW: Track incoming call type for video
    var isIncomingCall by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    
    // Register UI listener for state changes in callStateManager
    LaunchedEffect(Unit) {
        callStateManager.addListener { newState ->
            callState = newState
        }
    }
    
    // RECORD_AUDIO runtime permission for calls and voice messages
    var pendingCallAfterPermission by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("FREETIME_CALL", "RECORD_AUDIO permission: ${if (isGranted) "GRANTED" else "DENIED"}")
        if (isGranted && pendingCallAfterPermission) {
            pendingCallAfterPermission = false
            // Permission granted, proceed with call - will be triggered by callState check below
            callStateManager.updateState(CallState.INITIATING)
        } else if (!isGranted) {
            pendingCallAfterPermission = false
            callErrorMessage = "Microphone permission is required for calls"
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    // UI State
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showMediaModeDialog by remember { mutableStateOf(false) }
    var pendingMediaShareMode by remember { mutableStateOf("protected") }
    
    // Ensure WebSocket is connected (handles notification entry path where home screen is skipped)
    LaunchedEffect(recipientId) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        if (!wsManager.isConnected()) {
            android.util.Log.d("FREETIME_CHAT", "🔗 WebSocket not connected, initiating connection...")
            scope.launch {
                try {
                    apiService.connectWebSocket()
                    android.util.Log.d("FREETIME_CHAT", "✅ WebSocket connected in chat screen")
                } catch (e: Exception) {
                    android.util.Log.w("FREETIME_CHAT", "⚠️ WebSocket connection attempted but failed: ${e.message}")
                }
            }
        }
    }
    
    // ===== DEBOUNCED SEARCH EFFECT =====
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            showSearchResults = false
            searchResults = emptyList()
            lastSearchQuery = ""
            return@LaunchedEffect
        }

        // Debounce: wait 500ms after user stops typing
        delay(500)
        
        if (searchQuery == lastSearchQuery) {
            return@LaunchedEffect // Same search, don't repeat
        }

        isSearching = true
        lastSearchQuery = searchQuery
        
        try {
            val result = apiService.searchMessages(recipientId, searchQuery, limit = 50)
            result.onSuccess { results ->
                // Convert API Message to UI Message
                searchResults = results.map { apiMsg ->
                    Message(
                        id = apiMsg._id,
                        content = apiMsg.content,
                        senderName = apiMsg.senderId,
                        isSender = apiMsg.senderId == currentUserId,
                        timestamp = apiMsg.timestamp.toString(),
                        isRead = true,
                        status = "sent",
                        mediaType = apiMsg.mediaType,
                        mediaName = apiMsg.mediaName,
                        senderTags = emptyList(),  // ✅ NEW: Default empty for search results
                        senderIsAdmin = false,  // ✅ NEW: Default false for search results
                        senderIsModerator = false  // ✅ NEW: Default false for search results
                    )
                }
                showSearchResults = true
                android.util.Log.d("FREETIME_CHAT", "Found ${results.size} messages matching: $searchQuery")
            }.onFailure { error ->
                android.util.Log.e("FREETIME_CHAT", "Search error: ${error.message}")
                showSearchResults = false
                searchResults = emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_CHAT", "Search exception: ${e.message}")
            showSearchResults = false
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    val listState = rememberLazyListState()

    val token = prefs.getToken() ?: ""

    // WebRTC Manager - recreate when chat ID changes
    val webRTCManager = remember(recipientId) {
        WebRTCManager(
            context = context,
            onIceCandidate = { candidate ->
                // Send ICE candidate via WebSocket
                if (callId.isNotEmpty() && currentUserId.isNotEmpty()) {
                    val iceCandidatePayload = JSONObject().apply {
                        put("callId", callId)
                        put("to", recipientId)
                        put("candidate", candidate.toJson())
                    }
                    com.freetime.app.services.WebSocketManager.getInstance().send("call:ice-candidate", iceCandidatePayload)
                }
            },
            onTrack = { mediaStream ->
                android.util.Log.d("FREETIME_WEBRTC", "Received remote media stream: ${mediaStream.id}")
            },
            onConnectionStateChanged = { state ->
                android.util.Log.d("FREETIME_WEBRTC", "Connection state: $state")
                when (state) {
                    org.webrtc.PeerConnection.PeerConnectionState.FAILED -> {
                        scope.launch(Dispatchers.Main) {
                            callErrorMessage = "Connection failed — check your network"
                            // Use CallStateManager for thread-safe state update
                            callStateManager.updateState(CallState.FAILED)
                        }
                    }
                    org.webrtc.PeerConnection.PeerConnectionState.DISCONNECTED -> {
                        scope.launch(Dispatchers.Main) {
                            // Use CallStateManager to safely check current state and transition
                            if (callStateManager.getState() == CallState.ACTIVE) {
                                callErrorMessage = "Call disconnected"
                                callStateManager.updateState(CallState.ENDED)
                                delay(2000)
                                callStateManager.clearState()
                                callId = ""
                                callDurationSeconds = 0
                                pendingIncomingSdpOffer = ""
                                isIncomingCall = false
                                isMuted = false
                                isSpeakerOn = false
                            }
                        }
                    }
                    org.webrtc.PeerConnection.PeerConnectionState.CONNECTED -> {
                        android.util.Log.d("FREETIME_WEBRTC", "Peer connection established!")
                    }
                    else -> {}
                }
            },
            onIceConnectionFailed = { iceState, canRetry ->
                android.util.Log.w("FREETIME_WEBRTC", "⚠️ ICE connection failed. State: $iceState, Can retry: $canRetry")
                scope.launch(Dispatchers.Main) {
                    if (!canRetry) {
                        callErrorMessage = "Network connection lost — unable to recover"
                        callStateManager.updateState(CallState.FAILED)
                    } else {
                        android.util.Log.d("FREETIME_WEBRTC", "🔄 Attempting to recover from connection failure...")
                        // Retry mechanism will be handled by WebRTCManager
                    }
                }
            }
        )
    }

    // Cleanup WebRTCManager when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                webRTCManager.close()
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Error closing WebRTC: ${e.message}")
            }
            try {
                com.freetime.app.notifications.NotificationHelper.cancelOngoingCallNotification(context)
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Error canceling notification: ${e.message}")
            }
        }
    }
    
    // ==================== CRITICAL FIX: WebSocket Listener for Real-Time Messages ====================
    // Create WebSocket listener object for this chat screen
    val webSocketListener = remember {
        object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                // Only add message if it's from the current recipient or to the current user
                android.util.Log.d("FREETIME_CHAT", "📨 WebSocket onNewMessage called: from=${message.senderId}, to=${message.recipientId}, content='${message.content}'")
                // Dispatch to Main thread so Compose state updates trigger recomposition correctly
                scope.launch(Dispatchers.Main) {
                    // Skip own messages (already added via HTTP response) to prevent duplicates
                    if (message.senderId == recipientId && message.recipientId == currentUserId) {
                        
                        // DEDUPLICATION: Check if message already exists
                        // 1) By message ID (primary)
                        // 2) By mediaId for media messages (optimistic + echo dedup)
                        // 3) By content + sender for network retry dedup
                        val dedupMediaId = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                            .find(message.content)?.groupValues?.get(1)
                        val isDuplicateById = messages.any { it.id == message.messageId }
                        val isDuplicateByMedia = dedupMediaId != null && messages.any { it.mediaId == dedupMediaId }
                        val isDuplicateByContent = !isDuplicateById && !isDuplicateByMedia &&
                            messages.any { it.content == message.content && it.isSender == (message.senderId == currentUserId) }
                        if (isDuplicateById || isDuplicateByMedia || isDuplicateByContent) {
                            android.util.Log.d("FREETIME_CHAT", "⏭️ Duplicate message received via WebSocket - ignoring: ${message.messageId} (id=$isDuplicateById, media=$isDuplicateByMedia, content=$isDuplicateByContent)")
                        } else {
                            android.util.Log.d("FREETIME_CHAT", "✅ Message matches current chat - adding to UI: ${message.content}")
                            
                            // ✅ NEW: Save incoming message to local encrypted storage
                            scope.launch(Dispatchers.IO) {
                                val encryptedContent = encryptionManager.encrypt(
                                    message.content, 
                                    "$recipientId:${message.senderId}"
                                )
                                val entity = com.freetime.app.data.local.database.MessageEntity(
                                    messageId = message.messageId,
                                    chatId = recipientId,
                                    senderId = message.senderId,
                                    contentEncrypted = encryptedContent,
                                    timestamp = message.createdAt,
                                    isRead = false,
                                    syncState = "synced"
                                )
                                database.messageDao().insertMessage(entity)
                            }
                            
                            // Automatic encrypted media download for incoming messages
                            // Format: [Media: mediaId|mediaKey] fileName
                            val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|([^\]]*))?\]""".toRegex()
                            val mediaMatch = mediaIdRegex.find(message.content)
                            val mediaId = mediaMatch?.groupValues?.get(1)
                            val mediaKey = mediaMatch?.groupValues?.get(2)
                            
                            if (mediaId != null && message.senderId != currentUserId) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        android.util.Log.d("FREETIME_MEDIA", "📥 Auto-downloading encrypted media: $mediaId (key present: ${!mediaKey.isNullOrEmpty()})")
                                        val token = SharedPreferencesHelper(context).getToken() ?: ""
                                        
                                        // We download the raw bytes from backend
                                        val mediaData = apiService.downloadMedia(mediaId, "Bearer $token")
                                        if (mediaData != null && !mediaKey.isNullOrEmpty()) {
                                            val fileName = message.content.substringAfter("] ").takeIf { it.isNotEmpty() } ?: "downloaded_media"
                                            val mimeType = context.contentResolver.getType(Uri.parse(mediaId)) ?: "application/octet-stream"
                                            
                                            // Media is already downloaded and saved in downloadAndSaveMediaFile
                                            android.util.Log.d("FREETIME_MEDIA", "✅ Media $mediaId downloaded and cached")

                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("FREETIME_MEDIA", "Failed to auto-download media: ${e.message}")
                                    }
                                }
                            }

                messages = listOf(Message(
                    id = message.messageId,
                    senderName = if (message.senderId == currentUserId) currentUsername else recipientName,
                    content = message.content,
                    timestamp = formatMessageTime(message.createdAt),
                    isSender = message.senderId == currentUserId,
                    isRead = false,
                    status = "delivered", // Received via WebSocket
                    replyToMessageId = message.replyToMessageId,
                    replyToUsername = message.replyToUsername,
                    replyToText = message.replyToText,
                    senderId = message.senderId,
                    mediaId = mediaId,
                    mediaType = message.mediaType,
                    mediaName = message.mediaName,
                    mediaShareMode = message.mediaShareMode ?: if (mediaKey.isNullOrEmpty()) "public" else "protected",
                    senderTags = if (message.senderId == currentUserId) emptyList() else recipientTags,
                    senderIsAdmin = if (message.senderId == currentUserId) false else recipientIsAdmin,
                    senderIsModerator = if (message.senderId == currentUserId) false else recipientIsModerator,
                    subject = message.subject,
                    isAnnouncement = message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID
                )) + messages
                            
                            // ✅ FIX: Trim messages if list grows too large (prevent memory issues)
                            if (messages.size > 500) {
                                messages = messages.take(500)
                            }
    }
}
}
            }
            
            override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                android.util.Log.d("FREETIME_CHAT", "📨 WebSocket onGroupMessage called (not used yet)")
            }

            override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {
                android.util.Log.d("FREETIME_CHAT", "📨 WebSocket onChannelMessage called (not used yet)")
            }

            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                android.util.Log.d("FREETIME_CHAT", "📥 Media download requested: ${data.mediaId} by ${data.requesterName}")
                scope.launch(Dispatchers.Main) {
                    var attached = false
                    messages = messages.map { msg ->
                        // Match by content regex OR by msg.mediaId
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)

                        if (!data.mediaId.isNullOrEmpty() && (msgMediaId == data.mediaId || msg.mediaId == data.mediaId)) {
                            attached = true
                            // Add request if not already there
                            if (msg.pendingRequests.none { it.requestId == data.requestId }) {
                                msg.copy(pendingRequests = msg.pendingRequests + data)
                            } else msg
                        } else msg
                    }

                    if (!attached) {
                        // Fallback: resolve via REST pending-requests and attach by resolved mediaId
                        val requestIdToResolve = data.requestId
                        val apiServiceLocal = apiService
                        val dataCopy = data
                        scope.launch(Dispatchers.IO) {
                            try {
                                val pending = apiServiceLocal?.getPendingMediaDownloadRequests()
                                val resolved = pending?.getOrNull()?.find { it.requestId == requestIdToResolve }
                                val resolvedMediaId = resolved?.mediaId
                                if (!resolvedMediaId.isNullOrEmpty()) {
                                    scope.launch(Dispatchers.Main) {
                                        messages = messages.map { msg ->
                                            val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                                            val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)
                                            if (msgMediaId == resolvedMediaId || msg.mediaId == resolvedMediaId) {
                                                if (msg.pendingRequests.none { it.requestId == requestIdToResolve }) {
                                                    msg.copy(pendingRequests = msg.pendingRequests + dataCopy.copy(mediaId = resolvedMediaId))
                                                } else msg
                                            } else msg
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("FREETIME_CHAT", "Failed to resolve pending request via REST: ${e.message}")
                            }
                        }
                    }
                }
            }

            override fun onMediaDownloadApproved(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData) {
                android.util.Log.d("FREETIME_CHAT", "✅ Media download approved: ${data.mediaId}, encrypted=${data.encrypted}, key=${!data.encryptionKey.isNullOrEmpty()}")
                scope.launch(Dispatchers.Main) {
                    // Update the status map
                    mediaDownloadRequests = mediaDownloadRequests + (data.mediaId to "approved")
                    
                    // Remove from pending requests in messages
                    messages = messages.map { msg ->
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)
                        
                        if (msgMediaId == data.mediaId || msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.mediaId == data.mediaId })
                        } else msg
                    }
                }
                
                    // AUTO-DOWNLOAD: Download file automatically when approval received
                    if (data.downloadUrl != null && data.downloadUrl.isNotEmpty()) {
                        android.util.Log.d("FREETIME_CHAT", "📥 Starting auto-download for mediaId: ${data.mediaId}")
                        scope.launch(Dispatchers.IO) {
                            try {
                                // Look up original filename from messages for correct extension
                                val origName = messages.find { msg ->
                                    val regex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                                    val mid = regex.find(msg.content)?.groupValues?.get(1)
                                    mid == data.mediaId || msg.mediaId == data.mediaId
                                }?.mediaName
                                downloadAndSaveMediaFile(data, origName)
                                android.util.Log.d("FREETIME_CHAT", "✅ Media file downloaded and saved to gallery")
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Media downloaded to gallery", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CHAT", "❌ Failed to download media: ${e.message}", e)
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Failed to download: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {
                // Only show typing indicator if it's from the current chat recipient
                if (typingData.userId == recipientId) {
                    scope.launch(Dispatchers.Main) {
                        android.util.Log.d("FREETIME_CHAT", "⌨️ WebSocket: ${typingData.username} is typing")
                        isRecipientTyping = true
                        
                        // Cancel previous timeout job
                        typingTimeoutJob?.cancel()
                        
                        // Set new timeout: hide typing indicator after 3 seconds of inactivity
                        typingTimeoutJob = scope.launch {
                            delay(3000) // 3 seconds
                            isRecipientTyping = false
                            android.util.Log.d("FREETIME_CHAT", "⌨️ Typing indicator timeout")
                        }
                    }
                }
            }
            override fun onMessageRead(readData: com.freetime.app.services.WebSocketManager.ReadReceiptData) {
                android.util.Log.d("FREETIME_CHAT", "✓ WebSocket onMessageRead called (not used yet)")
            }
            override fun onConversationAllRead(readData: com.freetime.app.services.WebSocketManager.ConversationReadData) {
                android.util.Log.d("FREETIME_CHAT", "✓✓ WebSocket onConversationAllRead called (not used yet)")
            }
            
            override fun onIncomingCall(callData: com.freetime.app.services.WebSocketManager.IncomingCallData) {
                android.util.Log.d("FREETIME_CALL", "🔔 WebSocket onIncomingCall called from ${callData.callerId}")
                
                // ✅ FIX: Check if we are already in a call BEFORE showing incoming call UI
                val factoryActive = WebRTCFactory.hasActiveCall()
                if (webRTCManager.isActive() || callStateManager.isCallActive() || factoryActive) {
                    android.util.Log.w("FREETIME_CALL", "Blocking incoming call from ${callData.callerId}: already in a call")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val rejectPayload = JSONObject().apply {
                                put("callId", callData.callId)
                                put("callerId", callData.callerId)
                                put("reason", "busy")
                            }
                            com.freetime.app.services.WebSocketManager.getInstance().send("call:reject", rejectPayload)
                        } catch (e: Exception) {}
                    }
                    return
                }

                if (callData.callerId == recipientId) {
                    scope.launch(Dispatchers.Main) {
                        android.util.Log.d("FREETIME_CALL", "✅ Call from current chat recipient: ${callData.callId}")
                        callId = callData.callId
                        isIncomingCall = true
                        incomingCallType = callData.callType  // ✅ NEW: Store call type for video support
                        // Store the SDP offer for later use when user accepts
                        pendingIncomingSdpOffer = callData.sdpOffer
                        // Show the ringing UI with accept/decline buttons — do NOT auto-answer
                        // Use CallStateManager for thread-safe state update from WebSocket listener
                        callStateManager.updateState(CallState.RINGING, callData.callId)
                        // Cancel the system notification since we're showing in-app UI
                        com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                        android.util.Log.d("FREETIME_CALL", "Showing incoming call UI for: $callId")
                    }
                } else {
                    // ✅ NEW: Show system notification for calls from other users while in a chat
                    android.util.Log.d("FREETIME_CALL", "🔔 Call from someone else (${callData.callerUsername}) - showing notification")
                    com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                        context,
                        callData.callerUsername,
                        callData.callerId,
                        callData.callType,
                        callId = callData.callId,
                        callerAvatarUrl = callData.callerAvatar,
                        offerSdp = callData.sdpOffer
                    )
                }
            }
            
            override fun onCallAnswered(callData: com.freetime.app.services.WebSocketManager.CallAnsweredData) {
                android.util.Log.d("FREETIME_CALL", "🔔 WebSocket onCallAnswered called: callId=${callData.callId}")
                // Use CallStateManager for thread-safe check and state transition
                if (callData.callId == callStateManager.getCallId() && (callStateManager.getState() == CallState.RINGING || callStateManager.getState() == CallState.INITIATING)) {
                    scope.launch(Dispatchers.Main) {
                        // Re-check state inside coroutine — it may have changed (e.g., timeout fired)
                        // Use CallStateManager for safe check
                        if (callStateManager.getState() != CallState.RINGING && callStateManager.getState() != CallState.INITIATING) {
                            android.util.Log.w("FREETIME_CALL", "⏭️ Call state changed before answer could be processed (now ${callStateManager.getState()}) - ignoring")
                            return@launch
                        }
                        android.util.Log.d("FREETIME_CALL", "✅ Call answered event matches current call - transitioning to ACTIVE")
                        callStateManager.updateState(CallState.ACTIVE)
                        callDurationSeconds = 0
                        // Set remote answer
                        val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, callData.answer)
                        webRTCManager.setRemoteDescription(remoteAnswer)
                    }
                } else {
                    android.util.Log.d("FREETIME_CALL", "⏭️ Call answer from different call - ignoring (current=$callId, received=${callData.callId})")
                }
            }

            override fun onCallRejected(callData: com.freetime.app.services.WebSocketManager.CallRejectedData) {
                android.util.Log.d("FREETIME_CALL", "🔔 WebSocket onCallRejected called: callId=${callData.callId}, reason=${callData.reason}")
                if (callData.callId == callStateManager.getCallId()) {
                    scope.launch(Dispatchers.Main) {
                        android.util.Log.d("FREETIME_CALL", "✅ Call rejected for current call - ending")
                        com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                        callStateManager.updateState(CallState.ENDED)
                        callErrorMessage = "Call rejected: ${callData.reason}"
                        webRTCManager.close()
                        delay(2000)
                        callStateManager.clearState()
                        callId = ""
                        pendingIncomingSdpOffer = ""
                        isIncomingCall = false
                    }
                }
            }
            
            override fun onCallEnded(callData: com.freetime.app.services.WebSocketManager.CallEndedData) {
                android.util.Log.d("FREETIME_CALL", "🔔 WebSocket onCallEnded called: callId=${callData.callId}")
                if (callData.callId == callStateManager.getCallId()) {
                    scope.launch(Dispatchers.Main) {
                        android.util.Log.d("FREETIME_CALL", "✅ Call ended for current call")
                        com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                        val duration = callDurationSeconds
                        callStateManager.updateState(CallState.ENDED)
                        webRTCManager.close()
                        // Show "Call Ended" briefly then dismiss
                        delay(2000)
                        callStateManager.clearState()
                        callDurationSeconds = 0
                        callId = ""
                        pendingIncomingSdpOffer = ""
                        isIncomingCall = false
                        isMuted = false
                        isSpeakerOn = false
                        // Show missed call notification if call was never active
                        if (duration == 0) {
                            com.freetime.app.notifications.NotificationHelper.showMissedCallNotification(
                                context, recipientName, recipientId
                            )
                        }
                    }
                }
            }

            override fun onNotificationReceived(data: com.freetime.app.services.WebSocketManager.InternalNotificationData) {
                android.util.Log.d("FREETIME_CHAT", "🔔 Internal notification received while in chat: ${data.title}")
                
                // If it's a call, handle it immediately
                val type = data.data.optString("type", "")
                if (type == "call" || type == "incoming_call") {
                    val callerId = data.data.optString("callerId", "")
                    val callerName = data.data.optString("callerName", data.title)
                    val callType = data.data.optString("callType", "audio")
                    val callIdFromNotif = data.data.optString("callId", "")
                    
                    if (callerId == recipientId && callIdFromNotif.isNotEmpty()) {
                        scope.launch(Dispatchers.Main) {
                            callId = callIdFromNotif
                            isIncomingCall = true
                            incomingCallType = callType
                            callStateManager.updateState(CallState.RINGING, callIdFromNotif)
                            com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                        }
                    } else if (callerId.isNotEmpty()) {
                        // Call from someone else: show notification
                        com.freetime.app.notifications.NotificationHelper.showIncomingCallNotification(
                            context, callerName, callerId, callType, callIdFromNotif
                        )
                    }
                }
            }
            
            override fun onIceCandidate(iceData: com.freetime.app.services.WebSocketManager.IceCandidateData) {
                android.util.Log.d("FREETIME_WEBRTC", "🔌 WebSocket onIceCandidate received for call: ${iceData.callId}")
                if (iceData.callId == callId) {
                    scope.launch(Dispatchers.Main) {
                        try {
                            // Convert JSON candidate to WebRTC IceCandidate
                            val jsonCandidate = JSONObject(iceData.candidate)
                            val sdpMid = jsonCandidate.optString("sdpMid", "")
                            val sdpMLineIndex = jsonCandidate.optInt("sdpMLineIndex", 0)
                            val candidateStr = jsonCandidate.optString("candidate", "")
                            if (candidateStr.isEmpty()) {
                                android.util.Log.w("FREETIME_WEBRTC", "Empty ICE candidate string — ignoring")
                                return@launch
                            }
                            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                            webRTCManager.addIceCandidate(iceCandidate)
                        } catch (e: Exception) {
                            android.util.Log.e("FREETIME_WEBRTC", "Failed to parse ICE candidate: ${e.message}")
                        }
                    }
                } else {
                    android.util.Log.d("FREETIME_WEBRTC", "ICE candidate for different call — ignoring (current=$callId, received=${iceData.callId})")
                }
            }
            override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {
                android.util.Log.d("FREETIME_CHAT", "👤 WebSocket onUserStatusChanged called (not used yet)")
            }
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                android.util.Log.d("FREETIME_CHAT", "👍 WebSocket onReactionReceived: ${reactionData.emoji} on ${reactionData.messageId}")
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            msg.copy(reactions = (msg.reactions + reactionData.emoji).distinct())
                        } else msg
                    }
                }
                // Persist to Room DB so reactions survive app restart
                scope.launch(Dispatchers.IO) {
                    val existing = database.messageDao().getMessageById(reactionData.messageId)
                    if (existing != null) {
                        val updatedReactions = (parseReactions(existing.reactions) + reactionData.emoji).distinct()
                        database.messageDao().updateMessage(existing.copy(reactions = com.google.gson.Gson().toJson(updatedReactions)))
                    }
                }
            }
            override fun onReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                android.util.Log.d("FREETIME_CHAT", "👎 WebSocket onReactionRemoved: ${reactionData.emoji} from ${reactionData.messageId}")
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            msg.copy(reactions = msg.reactions.filter { it != reactionData.emoji })
                        } else msg
                    }
                }
                // Persist to Room DB so removal survives app restart
                scope.launch(Dispatchers.IO) {
                    val existing = database.messageDao().getMessageById(reactionData.messageId)
                    if (existing != null) {
                        val updatedReactions = parseReactions(existing.reactions).filter { it != reactionData.emoji }
                        database.messageDao().updateMessage(existing.copy(reactions = com.google.gson.Gson().toJson(updatedReactions)))
                    }
                }
            }
            override fun onConnectionEstablished() {
                android.util.Log.d("FREETIME_CHAT", "✅✅ WebSocket onConnectionEstablished called - REAL-TIME READY")
            }
            override fun onConnectionLost() {
                android.util.Log.d("FREETIME_CHAT", "❌❌ WebSocket onConnectionLost called - REAL-TIME OFFLINE")
            }
            override fun onError(error: String) {
                android.util.Log.e("FREETIME_CHAT", "❌ WebSocket onError: $error")
            }
            override fun onChatHistoryDeleted(data: com.freetime.app.services.WebSocketManager.ChatHistoryDeletedData) {
                // Check if this event is for the current conversation
                val isCurrentConversation = (data.deletedBy == recipientId) ||
                    (data.recipientId == recipientId && data.deletedBy == currentUserId) ||
                    (data.recipientId == currentUserId && data.deletedBy == recipientId)
                if (isCurrentConversation) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            messageRepository.deleteAllMessagesForChat(recipientId)
                            android.util.Log.d("FREETIME_CHAT", "✅ Local DB cleared for chat with $recipientId")
                        } catch (e: Exception) {
                            android.util.Log.e("FREETIME_CHAT", "❌ Failed to clear local DB: ${e.message}")
                        }
                    }
                    scope.launch(Dispatchers.Main) {
                        messages = emptyList()
                        android.util.Log.d("FREETIME_CHAT", "🗑 Chat history cleared by ${data.deletedBy}")
                        onNavigateToHome()
                    }
                }
            }
        }
    }
    
    // Register WebSocket listener when screen opens, unregister when closes
    DisposableEffect(Unit) {
        android.util.Log.d("FREETIME_CHAT", "🔗 Registering WebSocket listener for real-time chat")
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        wsManager.addListener(webSocketListener)
        // Track active chat for notification suppression (Telegram-style)
        com.freetime.app.notifications.NotificationHelper.currentActiveChatId = recipientId
        // Dismiss any existing message notifications from this sender (Telegram-style)
        com.freetime.app.notifications.NotificationHelper.cancelMessageNotification(context, recipientId)
        // Dismiss in-app notifications for message type from this sender
        com.freetime.app.notifications.InAppNotificationStore.removeByTypeAndSender("message", recipientId)
        android.util.Log.d("FREETIME_CHAT", "✅ WebSocket listener registered + active chat set: $recipientId")
        onDispose {
            android.util.Log.d("FREETIME_CHAT", "🔌 Unregistering WebSocket listener")
            wsManager.removeListener(webSocketListener)
            com.freetime.app.notifications.NotificationHelper.currentActiveChatId = null
            android.util.Log.d("FREETIME_CHAT", "✅ WebSocket listener unregistered + active chat cleared")
        }
    }
    
    // Auto-accept incoming call when launched from IncomingCallActivity
    LaunchedEffect(acceptCallOnOpen, pendingCallId) {
        if (acceptCallOnOpen && pendingCallId.isNotEmpty()) {
            android.util.Log.d("FREETIME_CALL", "Auto-accept call from notification: callId=$pendingCallId")
            callId = pendingCallId
            isIncomingCall = true

            // Cancel the incoming call notification immediately
            com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)

            // 1. Wait briefly for WebSocket to deliver the SDP offer (fast path)
            var waitMs = 0
            while (pendingIncomingSdpOffer.isEmpty() && waitMs < 5000) {
                delay(500)
                waitMs += 500
            }

            // 2. If WebSocket didn't deliver the offer, fetch from REST API (slow path — app was killed/backgrounded)
            if (pendingIncomingSdpOffer.isEmpty()) {
                android.util.Log.d("FREETIME_CALL", "No SDP from WebSocket — fetching call details via REST API")
                try {
                    val callResult = withContext(Dispatchers.IO) { apiService.getCall(pendingCallId) }
                    callResult.onSuccess { callJson ->
                        // offer can be: JSON object {type,sdp}, JSON string '{"type":"offer","sdp":"v=0..."}', or raw SDP
                        val offerObj = callJson.optJSONObject("offer")
                        val sdp: String
                        if (offerObj != null) {
                            val rawSdp = offerObj.optString("sdp", "")
                            // Guard against double-serialized SDP
                            sdp = if (rawSdp.startsWith("{")) {
                                try { JSONObject(rawSdp).optString("sdp", rawSdp) } catch (_: Exception) { rawSdp }
                            } else rawSdp.ifEmpty { offerObj.toString() }
                        } else {
                            val offerStr = callJson.optString("offer", "")
                            sdp = if (offerStr.startsWith("{")) {
                                try {
                                    val parsed = JSONObject(offerStr)
                                    parsed.optString("sdp", offerStr)
                                } catch (_: Exception) { offerStr }
                            } else offerStr
                        }
                        val status = callJson.optString("status", "")
                        if (sdp.isNotEmpty() && status == "ringing") {
                            pendingIncomingSdpOffer = sdp
                            android.util.Log.d("FREETIME_CALL", "Got SDP offer from REST API (${sdp.length} chars)")
                        } else {
                            android.util.Log.w("FREETIME_CALL", "Call status=$status, no valid SDP offer")
                        }
                    }
                    callResult.onFailure { e ->
                        android.util.Log.e("FREETIME_CALL", "REST getCall failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_CALL", "REST getCall error: ${e.message}")
                }
            }

            // 3. Perform WebRTC handshake
            if (pendingIncomingSdpOffer.isNotEmpty()) {
                android.util.Log.d("FREETIME_CALL", "Got SDP offer — auto-accepting call")
                try {
                    // ===== CRITICAL: Prevent concurrent calls (ATOMIC via CallStateManager) =====
                    // Check both WebRTC and call state manager for active calls
                    // Use callStateManager.isCallActive() for thread-safe check of ACTIVE|RINGING|CONNECTING states
                    val factoryActive = WebRTCFactory.hasActiveCall()
                    if (webRTCManager.isActive() || callStateManager.isCallActive() || factoryActive) {
                        android.util.Log.w("FREETIME_CALL", "Blocked incoming call: existing call in progress (webRTC=${webRTCManager.isActive()}, factory=$factoryActive, callState=${callStateManager.getState()})")
                        
                        // ===== ATOMIC STATE UPDATE =====
                        // Update local state BEFORE async rejection to prevent concurrent accepts
                        callErrorMessage = "A call is already in progress. Cannot accept new call."
                        val stateUpdated = callStateManager.updateState(CallState.FAILED, callId)
                        android.util.Log.d("FREETIME_CALL", "Busy call state update: success=$stateUpdated")
                        
                        // Then send rejection (async) - might fail but state is already updated locally
                        scope.launch(Dispatchers.IO) {
                            try { 
                                val result = apiService.rejectCall(callId)
                                if (result.isFailure) {
                                    android.util.Log.e("FREETIME_CALL", "Failed to reject busy call: ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CALL", "Exception rejecting busy call", e)
                            }
                        }
                        val rejectPayload = JSONObject().apply {
                            put("callId", callId)
                            put("callerId", recipientId)
                            put("reason", "busy")
                        }
                        com.freetime.app.services.WebSocketManager.getInstance().send("call:reject", rejectPayload)
                        pendingIncomingSdpOffer = ""
                        return@LaunchedEffect
                    }
                    
                    android.util.Log.d("FREETIME_CALL", "🔄 Creating peer connection...")
                    try {
                        webRTCManager.createPeerConnection()
                        android.util.Log.d("FREETIME_CALL", "✅ Peer connection created")
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CALL", "❌ Failed to create peer connection: ${e.message}", e)
                        throw e
                    }
                    
                    android.util.Log.d("FREETIME_CALL", "🔄 Setting remote SDP offer (length=${pendingIncomingSdpOffer.length} chars)")
                    try {
                        val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, pendingIncomingSdpOffer)
                        webRTCManager.setRemoteDescription(remoteOffer)
                        android.util.Log.d("FREETIME_CALL", "✅ Remote SDP offer set successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CALL", "❌ Failed to set remote SDP: ${e.message}", e)
                        throw e
                    }
                    
                    android.util.Log.d("FREETIME_CALL", "🔄 Creating SDP answer...")
                    val answer = try {
                        webRTCManager.createAnswer()
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CALL", "❌ Failed to create answer: ${e.message}", e)
                        throw e
                    }
                    android.util.Log.d("FREETIME_CALL", "✅ SDP answer created (length=${answer.description.length} chars)")
                    
                    // Send answer via REST API (reliable — always works)
                    android.util.Log.d("FREETIME_CALL", "📤 Sending answer via REST API...")
                    withContext(Dispatchers.IO) {
                        try {
                            val result = apiService.answerCall(callId, answer.toJson().toString())
                            android.util.Log.d("FREETIME_CALL", "✅ Answer sent via REST API: $result")
                        } catch (e: Exception) {
                            android.util.Log.e("FREETIME_CALL", "❌ API answer failed: ${e.message}", e)
                        }
                    }
                    
                    // Also send via WebSocket (fast — if connected)
                    android.util.Log.d("FREETIME_CALL", "📤 Sending answer via WebSocket...")
                    try {
                        val answerPayload = JSONObject().apply {
                            put("callId", callId)
                            put("callerId", recipientId)
                            put("answer", answer.toJson())
                        }
                        com.freetime.app.services.WebSocketManager.getInstance().send("call:answer", answerPayload)
                        android.util.Log.d("FREETIME_CALL", "✅ Answer sent via WebSocket")
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CALL", "⚠️ WebSocket answer send failed: ${e.message}")
                    }
                    
                    android.util.Log.d("FREETIME_CALL", "🔄 Updating call state to ACTIVE...")
                    callStateManager.updateState(CallState.ACTIVE)
                    callDurationSeconds = 0
                    pendingIncomingSdpOffer = ""
                    android.util.Log.d("FREETIME_CALL", "✅ Auto-accepted call: $callId")
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_CALL", "Auto-accept failed: ${e.message}")
                    callErrorMessage = "Failed to accept call: ${e.message}"
                    callStateManager.updateState(CallState.FAILED)
                }
            } else {
                android.util.Log.w("FREETIME_CALL", "No SDP offer available — call may have ended")
                callErrorMessage = "Call no longer available"
                callStateManager.updateState(CallState.ENDED)
                delay(2000)
                callStateManager.clearState()
                callId = ""
                isIncomingCall = false
            }
        }
    }

    // ✅ FIX: Smart auto-scroll that only scrolls if user is already viewing the bottom
    // This prevents the chat from jumping when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            try {
                // Check if user is near the bottom (item 0 in a reverseLayout LazyColumn)
                // In reverseLayout, item 0 is the newest message at the bottom.
                // If user is at index 0 or close to it, they are at the bottom.
                val firstVisibleIndex = listState.firstVisibleItemIndex
                if (firstVisibleIndex < 3) {
                    listState.animateScrollToItem(0)
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Scroll error: ${e.message}")
            }
        }
    }
    
    // Scroll to bottom when keyboard opens (user starts typing)
    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(0)
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Keyboard scroll error: ${e.message}")
            }
        }
    }
    
    // Media file picker launcher - supports images and videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            isProcessingMedia = true
            // Take persistable permission to prevent ENOENT on delayed access
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            scope.launch {
                try {
                    val fileName = com.freetime.app.utils.FileUtils.getFileNameFromUri(context, uri) ?: "media_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileData = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: byteArrayOf()
                    
                    if (fileData.isEmpty()) {
                        mediaPickerError = "Failed to read file"
                        android.util.Log.e("FREETIME_MEDIA", "Empty file data for $fileName")
                    } else {
                        // CRITICAL FIX: Real upload to server instead of just local storage
                        try {
                            // Upload media to server and get server ID + encryption key
                            val uploadResult = apiService.uploadMediaToChat(
                                mediaData = fileData,
                                fileName = fileName,
                                mimeType = mimeType,
                                recipientId = recipientId,
                                token = token,
                                mediaShareMode = pendingMediaShareMode
                            )
                            
                            if (uploadResult != null) {
                                val (serverMediaId, mediaKey) = uploadResult
                                val isPublic = pendingMediaShareMode == "public"
                                val mediaContent = if (isPublic) {
                                    "[Media: $serverMediaId] $fileName"
                                } else {
                                    "[Media: $serverMediaId|${mediaKey ?: ""}] $fileName"
                                }
                                
                                // 3. Send message with server media ID reference (and encryption key for protected)
                                val sendRequest = SendMessageRequest(
                                    recipientId = recipientId,
                                    content = mediaContent
                                )
                                val response = apiClient.sendMessage(sendRequest, "Bearer $token")
                                if (response.isSuccessful) {
                                    android.util.Log.d("FREETIME_MEDIA", "Media uploaded and sent: $serverMediaId ($fileName, mode=$pendingMediaShareMode)")
                                    
                                    // Update UI with server media ID
                                    messages = listOf(Message(
                                        id = serverMediaId,
                                        senderName = currentUsername,
                                        content = mediaContent,
                                        timestamp = "Now",
                                        isSender = true,
                                        status = "sent",
                                        mediaId = serverMediaId,
                                        mediaType = if (mimeType.startsWith("video/")) "video" else "image",
                                        mediaName = fileName,
                                        senderTags = emptyList(),  // ✅ NEW: Current user - will use different styling
                                        senderIsAdmin = false,  // ✅ NEW: Default
                                        senderIsModerator = false,  // ✅ NEW: Default
                                        mediaShareMode = pendingMediaShareMode
                                    )) + messages
                                    // Persist to local DB immediately so it survives chat reopen
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val encryptedContent = encryptionManager.encrypt(
                                                mediaContent,
                                                "$recipientId:$currentUserId"
                                            )
                                            val entity = com.freetime.app.data.local.database.MessageEntity(
                                                messageId = serverMediaId,
                                                chatId = recipientId,
                                                senderId = currentUserId,
                                                contentEncrypted = encryptedContent,
                                                timestamp = System.currentTimeMillis(),
                                                isRead = true,
                                                syncState = "synced",
                                                mediaType = if (mimeType.startsWith("video/")) "video" else "image",
                                                mediaName = fileName
                                            )
                                            database.messageDao().insertMessage(entity)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FREETIME_MEDIA", "Failed to persist media message: ${e.message}")
                                        }
                                    }
                                    mediaPickerError = ""
                                } else {
                                    mediaPickerError = "Failed to send media message"
                                    android.util.Log.e("FREETIME_MEDIA", "API send error: ${response.code()}")
                                }
                            } else {
                                mediaPickerError = "Failed to upload media to server"
                            }
                        } catch (e: Exception) {
                            mediaPickerError = "Media upload error: ${e.message}"
                            android.util.Log.e("FREETIME_MEDIA", "Media upload exception", e)
                        }
                    }
                } catch (e: Exception) {
                    mediaPickerError = "Error: ${e.message}"
                    android.util.Log.e("FREETIME_MEDIA", "Media picker error: ${e.message}", e)
                } finally {
                    isProcessingMedia = false
                    selectedMediaUri = null
                }
            }
        }
    }
    
    android.util.Log.d("FREETIME_CHAT", "ModernChatScreen: Opened chat with $recipientId, name=$chatName")
    
    // CRITICAL FIX: Fetch recipient user data and online status
    LaunchedEffect(recipientId) {
        try {
            val response = apiService.getPublicUserProfile(recipientId)
            response.onSuccess { user ->
                // ✅ IMPROVED: Ensure we have a real name, not "User" fallback unless necessary
                // Check all possible name sources in priority order
                val displayName = user.displayName?.takeIf { it.isNotEmpty() && it != "null" }
                val username = user.username?.takeIf { it.isNotEmpty() && it != "null" }
                val loadedName = displayName ?: username ?: "User"
                
                recipientName = loadedName
                recipientTags = user.tags ?: emptyList()  // ✅ NEW: Extract tags for color coding
                recipientRole = user.role ?: ""  // ✅ NEW: Store role for flexible color matching
                recipientIsAdmin = user.role == "ADMIN" || user.role == "admin"  // ✅ NEW: Extract admin status from role
                recipientIsModerator = user.role == "MODERATOR" || user.role == "moderator"  // ✅ NEW: Extract moderator status from role
                recipientIsOnline = false  // Will be polled via getUserStatus
                recipientExists = true
                
                // 🔍 DEBUG: Log the profile data to verify what's being loaded
                android.util.Log.d("FREETIME_CHAT", "📋 Recipient profile loaded - displayName='${user.displayName}' username='${user.username}' -> Final Name: '$loadedName'")
                android.util.Log.d("FREETIME_CHAT", "✅ Recipient profile loaded - Name: $loadedName, Tags: $recipientTags, Role: ${user.role}, Admin: $recipientIsAdmin, Mod: $recipientIsModerator")
            }.onFailure { error ->
                recipientExists = false
                android.util.Log.e("FREETIME_CHAT", "❌ Failed to load recipient: ${error.message}")
            }
        } catch (e: Exception) {
            recipientExists = false
            android.util.Log.e("FREETIME_CHAT", "❌ Exception loading recipient: ${e.message}", e)
        }
    }

    // Auto-refresh recipient status every 10 seconds for real-time updates
    LaunchedEffect(recipientId) {
        while (true) {
            delay(10000)  // Poll every 10 seconds
            try {
                val statusResult = apiService.getUserStatus(recipientId)
                statusResult.onSuccess { statusMap ->
                    val isOnline = (statusMap["isOnline"] as? Boolean) ?: false
                    if (recipientIsOnline != isOnline) {
                        recipientIsOnline = isOnline
                        android.util.Log.d("FREETIME_CHAT", "Status updated: $recipientName is now ${if (isOnline) "online" else "offline"}")
                    }
                }.onFailure { error ->
                    android.util.Log.e("FREETIME_CHAT", "Failed to poll status: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Exception polling status: ${e.message}")
            }
        }
    }

    // ✅ FIX: Sync pending media download requests AFTER messages are loaded
    // Uses snapshotFlow to wait until messages list is non-empty before attaching pending requests
    LaunchedEffect(recipientId) {
        try {
            // Wait until messages are populated (Room DB loads quickly, typically < 500ms)
            var waited = 0
            while (messages.isEmpty() && waited < 5000) {
                delay(200)
                waited += 200
            }

            val apiServiceLocal = apiService
            withContext(Dispatchers.IO) {
                apiServiceLocal.getPendingMediaDownloadRequests()
            }.onSuccess { requests ->
                if (requests.isNotEmpty()) {
                    android.util.Log.d("FREETIME_CHAT", "📥 Found ${requests.size} pending media requests from API")
                    messages = messages.map { msg ->
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)

                        if (msgMediaId != null) {
                            val msgRequests = requests.filter { it.mediaId == msgMediaId }
                            if (msgRequests.isNotEmpty()) {
                                val mappedRequests = msgRequests.map { req ->
                                    com.freetime.app.services.WebSocketManager.MediaDownloadRequestData(
                                        requestId = req.requestId,
                                        mediaId = req.mediaId ?: "",
                                        requesterId = req.requesterId ?: "",
                                        requesterName = req.requesterName ?: "User"
                                    )
                                }
                                msg.copy(pendingRequests = mappedRequests)
                            } else msg
                        } else msg
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("FREETIME_CHAT", "Failed to sync pending requests: ${error.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_CHAT", "Error syncing pending requests: ${e.message}")
        }
    }
    
    // Load from Local Database (one-shot), then Sync from API
    LaunchedEffect(recipientId) {
        isLoadingMessages = true
        try {
            // 1. Load initial data from local database (one-shot, non-blocking)
            val localEntities = messageRepository.getMessagesForChat(recipientId).first()
            val existingReactions = messages.associate { it.id to it.reactions }
            messages = localEntities.map { entity ->
                val decryptedContent = messageRepository.decryptMessage(entity)
                Message(
                    id = entity.messageId,
                    senderName = if (entity.senderId == currentUserId) currentUsername else recipientName,
                    content = decryptedContent,
                    isSender = entity.senderId == currentUserId,
                    timestamp = formatMessageTime(entity.timestamp),
                    isRead = entity.isRead,
                    senderId = entity.senderId,
                    replyToMessageId = entity.replyToMessageId,
                    replyToUsername = entity.replyToUsername,
                    replyToText = entity.replyToText,
                    mediaType = entity.mediaType,
                    mediaName = entity.mediaName,
                    mediaShareMode = inferMediaShareMode(decryptedContent),
                    subject = null,
                    isAnnouncement = entity.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID,
                    reactions = existingReactions[entity.messageId] ?: parseReactions(entity.reactions)
                )
            }

            // 2. Sync from API (gets messages from server, including announcements)
            val apiMessages = messageRepository.fetchMessagesFromAPI(recipientId)
            apiMessages.forEach { entity ->
                database.messageDao().insertMessage(entity)
            }

            // 3. Fetch reactions from API for messages that have none in DB (pre-migration data)
            val token = SharedPreferencesHelper(context).getToken() ?: ""
            if (token.isNotEmpty()) {
                val pollResponse = apiClient.getMessages(recipientId, "Bearer $token")
                if (pollResponse.isSuccessful) {
                    pollResponse.body()?.let { body ->
                        val reactionMap = mutableMapOf<String, List<String>>()
                        for (resp in body) {
                            val id = resp._id
                            reactionMap[id] = resp.reactions.keys.toList()
                        }
                        messages = messages.map { msg ->
                            val apiReactions = reactionMap[msg.id]
                            if (apiReactions != null && apiReactions.isNotEmpty() && apiReactions != msg.reactions) {
                                msg.copy(reactions = apiReactions)
                            } else msg
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_CHAT", "Error syncing messages: ${e.message}")
        } finally {
            isLoadingMessages = false
        }
    }

    // Reactively listen to local DB changes (new messages via sync, WebSocket, etc.)
    LaunchedEffect(recipientId) {
        messageRepository.getMessagesForChat(recipientId).collect { localEntities ->
            val existingReactions = messages.associate { it.id to it.reactions }
            messages = localEntities.map { entity ->
                val decryptedContent = messageRepository.decryptMessage(entity)
                Message(
                    id = entity.messageId,
                    senderName = if (entity.senderId == currentUserId) currentUsername else recipientName,
                    content = decryptedContent,
                    isSender = entity.senderId == currentUserId,
                    timestamp = formatMessageTime(entity.timestamp),
                    isRead = entity.isRead,
                    senderId = entity.senderId,
                    replyToMessageId = entity.replyToMessageId,
                    replyToUsername = entity.replyToUsername,
                    replyToText = entity.replyToText,
                    mediaType = entity.mediaType,
                    mediaName = entity.mediaName,
                    mediaShareMode = inferMediaShareMode(decryptedContent),
                    subject = null,
                    isAnnouncement = entity.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID,
                    reactions = existingReactions[entity.messageId] ?: parseReactions(entity.reactions)
                )
            }
        }
    }

    // FALLBACK: Periodic message check every 5 seconds as fallback for real-time delivery
    // Uses delta sync: only fetches and appends NEW messages (not full reload)
    var lastPollTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(recipientId, wsConnected) {
        while (true) {
            delay(5000) // Check for new messages every 5 seconds
            
            if (wsConnected) {
                // If WebSocket is connected, we don't need to poll
                continue
            }
            
            try {
                val response = apiClient.getMessages(recipientId, "Bearer $token")
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        val newMessages = body.mapNotNull { msgResponse ->
                            try {
                                val msgId = msgResponse.id?.takeIf { it.isNotEmpty() } ?: msgResponse._id?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
                                
                                // 🔍 DEBUG: Log what we're receiving from API
                                if (msgResponse.senderId != currentUserId) {
                                    android.util.Log.d("FREETIME_CHAT", "📨 Poll: senderDisplayName='${msgResponse.senderDisplayName}' senderName='${msgResponse.senderName}'")
                                }
                                
                                // ✅ CRITICAL FIX: Use senderDisplayName from backend, check senderName as fallback
                                val senderDisplayName = if (msgResponse.senderId == currentUserId) {
                                    currentUsername
                                } else {
                                    val displayName = msgResponse.senderDisplayName?.takeIf { it.isNotEmpty() && it != "null" }
                                        ?: msgResponse.senderName?.takeIf { it.isNotEmpty() && it != "null" }
                                        ?: recipientName.takeIf { it.isNotEmpty() }
                                        ?: "User"
                                    displayName
                                }
                                
                                Message(
                                    id = msgId,
                                    senderName = senderDisplayName,
                                    content = msgResponse.content ?: "",
                                    timestamp = formatMessageTime(msgResponse.timestamp),
                                    isSender = msgResponse.senderId == currentUserId,
                                    isRead = msgResponse.read,
                                    status = "delivered",
                                    replyToMessageId = msgResponse.replyToMessageId,
                                    replyToUsername = msgResponse.replyToUsername,
                                    replyToText = msgResponse.replyToText,
                                    mediaType = msgResponse.mediaType,
                                    mediaName = msgResponse.mediaName,
                                    mediaShareMode = msgResponse.mediaShareMode,
                                    // ✅ CRITICAL FIX: Use color-coding fields from backend
                                    senderTags = if (msgResponse.senderId == currentUserId) emptyList() else (msgResponse.senderTags.takeIf { it.isNotEmpty() } ?: recipientTags),
                                    senderIsAdmin = if (msgResponse.senderId == currentUserId) false else (msgResponse.senderIsAdmin || recipientIsAdmin),
                                    senderIsModerator = if (msgResponse.senderId == currentUserId) false else (msgResponse.senderIsModerator || recipientIsModerator),
                                    subject = msgResponse.subject,
                                    isAnnouncement = msgResponse.isAdminAnnouncement,
                                    reactions = msgResponse.reactions.keys.toList()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CHAT", "Error parsing poll message: ${e.message}")
                                null
                            }
                        }
                        
                        // Add only new messages (delta sync by ID + content dedup)
                        val existingIds = messages.map { it.id }.toSet()
                        val existingContentPairs = messages.map { it.content to it.senderName }.toSet()
                        val onlyNew = newMessages.filter { msg ->
                            msg.id !in existingIds &&
                            (msg.content to msg.senderName) !in existingContentPairs
                        }
                        if (onlyNew.isNotEmpty()) {
                            android.util.Log.d("FREETIME_CHAT", "📨 Poll added ${onlyNew.size} new messages (dedup'd from ${newMessages.size})")
                            messages = onlyNew + messages
                            lastPollTimestamp = System.currentTimeMillis()
                        }

                        // Sync reactions for existing messages from API data
                        val reactionMap = newMessages.associate { it.id to it.reactions }
                        messages = messages.map { msg ->
                            val apiReactions = reactionMap[msg.id]
                            if (apiReactions != null && apiReactions != msg.reactions) {
                                msg.copy(reactions = apiReactions)
                            } else msg
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FREETIME_CHAT", "Fallback poll failed (non-blocking): ${e.message}")
            }
        }
    }
    
    var messageToReact by remember { mutableStateOf<Message?>(null) }
    
    // Reaction function
    fun addReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                // Update local UI immediately
                messages = messages.map { 
                    if (it.id == messageId) {
                        it.copy(reactions = (it.reactions + emoji).distinct())
                    } else it
                }
                
                // Send to server via REST API for persistence
                withContext(Dispatchers.IO) {
                    try {
                        apiService.addReaction(messageId, emoji)
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "REST reaction failed: ${e.message}")
                    }
                }
                
                // Also notify via Socket.IO for real-time delivery to other user
                val payload = JSONObject().apply {
                    put("messageId", messageId)
                    put("emoji", emoji)
                    put("recipientId", recipientId)
                }
                com.freetime.app.services.WebSocketManager.getInstance().send("message:reaction:add", payload)
                
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Error adding reaction: ${e.message}")
            }
        }
    }

    // Remove reaction function
    fun removeReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                // Update local UI immediately
                messages = messages.map { 
                    if (it.id == messageId) {
                        it.copy(reactions = it.reactions.filter { r -> r != emoji })
                    } else it
                }
                
                // Send to server via REST API for persistence
                withContext(Dispatchers.IO) {
                    try {
                        apiService.removeReaction(messageId, emoji)
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "REST reaction removal failed: ${e.message}")
                    }
                }
                
                // Notify via Socket.IO for real-time delivery to other user
                val payload = JSONObject().apply {
                    put("messageId", messageId)
                    put("emoji", emoji)
                    put("recipientId", recipientId)
                }
                com.freetime.app.services.WebSocketManager.getInstance().send("message:reaction:remove", payload)
                
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Error removing reaction: ${e.message}")
            }
        }
    }
    
    fun addOrUpdateMessage(newMsg: Message) {
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
            messages = listOf(newMsg) + messages
        }
    }
    
    fun replyToPrivateMessage(messageId: String) {
        val message = messages.find { it.id == messageId }
        if (message != null) {
            replyingToMessage = message
        }
    }
    
    // Send message function
    val sendMessage: () -> Unit = send@{
        // ✅ DEBOUNCE: Check if enough time has passed since last send
        val now = System.currentTimeMillis()
        if (now - lastSendTimeMs < SEND_DEBOUNCE_MS) {
            android.util.Log.w("FREETIME_CHAT", "Send throttled - last send was ${now - lastSendTimeMs}ms ago")
            return@send
        }
        lastSendTimeMs = now
        
        if (messageText.trim().isNotEmpty() && recipientExists && !isSendingMessage) {
            // CRITICAL FIX: Check if recipient exists before sending
            if (!recipientExists) {
                android.util.Log.e("FREETIME_CHAT", "Cannot send message: Recipient does not exist in database")
            } else {
                isSendingMessage = true
                // CRITICAL FIX: Copy message text and reply state before clearing to prevent race conditions
                val messageToSend = messageText.trim()
                val replyTo = replyingToMessage
                messageText = ""  // Clear input IMMEDIATELY to prevent duplicate sends
                replyingToMessage = null // Clear reply context
                
                // ✅ FIX: Add message limit to prevent memory bloat and crashes (keep last 500 messages)
                if (messages.size > 500) {
                    messages = messages.take(500)
                    android.util.Log.d("FREETIME_CHAT", "📊 Trimmed messages to 500 to prevent memory bloat")
                }
                
                scope.launch {
                    try {
                        // ✅ UPDATED: Save locally (encrypted) BEFORE sending to API
                        val localMessageId = messageRepository.sendMessage(
                            chatId = recipientId,
                            senderId = currentUserId,
                            content = messageToSend,
                            replyToMessageId = replyTo?.id,
                            replyToUsername = replyTo?.senderName,
                            replyToText = replyTo?.content
                        )
                        
                        // Add to UI immediately
                        val msgForUi = Message(
                            id = localMessageId,
                            senderName = currentUsername,
                            content = messageToSend,
                            isSender = true,
                            timestamp = formatMessageTime(System.currentTimeMillis()),
                            isRead = true,
                            status = "pending", // Initial status
                            replyToMessageId = replyTo?.id,
                            replyToUsername = replyTo?.senderName,
                            replyToText = replyTo?.content,
                            senderTags = emptyList(), 
                            senderIsAdmin = false,
                            senderIsModerator = false
                        )
                        messages = listOf(msgForUi) + messages
                        
                        try {
                            val sendRequest = SendMessageRequest(
                                recipientId = recipientId,
                                content = messageToSend,
                                replyToMessageId = replyTo?.id,
                                replyToUsername = replyTo?.senderName,
                                replyToText = replyTo?.content
                            )
                            val response = apiClient.sendMessage(sendRequest, "Bearer $token")
                            
                            if (response.isSuccessful) {
                                // ✅ SUCCESS: Update sync state to "synced"
                                messageRepository.updateSyncState(localMessageId, "synced")
                                // Parse server ID and update local message ID to prevent poll-duplicates
                                val serverMsgId = response.body()?.id?.takeIf { it.isNotEmpty() }
                                    ?: response.body()?._id?.takeIf { it.isNotEmpty() }
                                if (serverMsgId != null && serverMsgId != localMessageId) {
                                    messageRepository.updateMessageId(localMessageId, serverMsgId)
                                    messages = messages.map { if (it.id == localMessageId) it.copy(id = serverMsgId, status = "delivered") else it }
                                } else {
                                    messages = messages.map { if (it.id == localMessageId) it.copy(status = "delivered") else it }
                                }
                            } else {
                                messageRepository.updateSyncState(localMessageId, "failed")
                                messages = messages.map { if (it.id == localMessageId) it.copy(status = "failed") else it }
                                Toast.makeText(context, "Failed to send: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                                messageText = messageToSend
                                replyingToMessage = replyTo
                            }
                        } catch (e: Exception) {
                            messageRepository.updateSyncState(localMessageId, "failed")
                            messages = messages.map { if (it.id == localMessageId) it.copy(status = "failed") else it }
                            Toast.makeText(context, "Message will resend when connection improves", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "Fatal error sending message: ${e.message}")
                        Toast.makeText(context, "Message failed: ${e.message}", Toast.LENGTH_LONG).show()
                        messageText = messageToSend // Restore message
                    } finally {
                        isSendingMessage = false
                    }
                }
            }
        } else if (messageText.trim().isEmpty()) {
            android.util.Log.d("FREETIME_CHAT", "Empty message - ignoring send")
        } else if (isSendingMessage) {
            android.util.Log.d("FREETIME_CHAT", "Message already sending - ignoring duplicate send")
        }
    }
    
    // Check media download status
    val checkMediaStatus = { mediaId: String ->
        scope.launch {
            try {
                val result = apiService.getPendingMediaDownloadRequests()
                result.onSuccess { requests ->
                    val request = requests.find { it.mediaId == mediaId }
                    if (request != null) {
                        mediaDownloadRequests = mediaDownloadRequests + (mediaId to "pending")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Error checking media status: ${e.message}")
            }
        }
    }

    // Send typing indicator function with debouncing
    val sendTypingIndicator: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastTypingIndicatorSentMs >= TYPING_INDICATOR_DEBOUNCE_MS && recipientExists) {
            lastTypingIndicatorSentMs = now
            scope.launch(Dispatchers.IO) {
                try {
                    apiService.sendTypingIndicator(recipientId)
                    android.util.Log.d("FREETIME_CHAT", "✍️ Typing indicator sent to $recipientId")
                } catch (e: Exception) {
                    // Don't spam logs for typing indicator failures - it's non-critical
                    android.util.Log.v("FREETIME_CHAT", "Typing indicator send failed (non-critical): ${e.message}")
                }
            }
        }
    }
    
    // Voice call initiation function
    val initiateVoiceCall: () -> Unit = init@{
        // ✅ CRITICAL FIX: Prevent multiple concurrent calls - check if already in a call
        val currentState = callStateManager.getState()
        val isAlreadyInCall = currentState != null && 
            currentState != CallState.ENDED && 
            currentState != CallState.FAILED
        
        if (isAlreadyInCall) {
            android.util.Log.w("FREETIME_CALL", "⚠️ Cannot start new call: Already in call (state=$currentState)")
            Toast.makeText(context, "A call is already in progress. End it first.", Toast.LENGTH_SHORT).show()
            return@init // Exit early, don't start another call
        }
        
        if (recipientExists) {
            // Check RECORD_AUDIO permission first
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasAudioPermission) {
                android.util.Log.d("FREETIME_CALL", "Requesting RECORD_AUDIO permission before call")
                pendingCallAfterPermission = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                callStateManager.updateState(CallState.INITIATING)
            }
        } else {
            android.util.Log.e("FREETIME_CALL", "Cannot initiate call: Recipient does not exist in database")
            callErrorMessage = "Cannot call: Recipient user not found in database"
            callStateManager.updateState(CallState.FAILED)
        }
    }
    
    // Perform actual call initiation when callState becomes INITIATING
    LaunchedEffect(callState) {
        if (callState == CallState.INITIATING) {
            callErrorMessage = ""
            
            // ===== CRITICAL: Prevent concurrent calls WITH IMPROVED STATE DETECTION =====
            // ✅ FIX: Check multiple state sources and log all conditions
            val webRTCActive = webRTCManager.isActive()
            val factoryActiveManager = WebRTCFactory.getActiveManager()
            val factoryHasActive = WebRTCFactory.hasActiveCall()
            val managerActive = callStateManager.isCallActive()
            
            // We are only "genuinely busy" if there's another manager active OR our manager is already advanced
            // If factoryActiveManager is US, then it's not "another" manager
            val isAnotherManagerBusy = factoryHasActive && factoryActiveManager !== webRTCManager
            val isCurrentManagerAdvanced = webRTCActive || managerActive
            
            android.util.Log.d("FREETIME_CALL", "Call initiation check: anotherManagerBusy=$isAnotherManagerBusy, currentAdvanced=$isCurrentManagerAdvanced, currentState=$callState")
            
            if (isAnotherManagerBusy || isCurrentManagerAdvanced) {
                callErrorMessage = "A call is already in progress. End it before starting a new call. (Other:$isAnotherManagerBusy Adv:$isCurrentManagerAdvanced)"
                callStateManager.updateState(CallState.FAILED)
                return@LaunchedEffect
            }
            
            if (recipientId.isEmpty()) {
                callErrorMessage = "Cannot call: recipient not found"
                callStateManager.updateState(CallState.FAILED)
                return@LaunchedEffect
            }
            try {
                webRTCManager.createPeerConnection()
                val offer = webRTCManager.createOffer()

                // Use REST API to register call and get callId
                val offerJson = offer.toJson().toString()
                val result = apiService.initiateCall(recipientId, "audio", offerJson)
                result.fold(
                    onSuccess = { serverCallId ->
                        callId = serverCallId
                        callStateManager.updateState(CallState.RINGING, callId)
                        android.util.Log.d("FREETIME_CALL", "Voice call initiated with callId: $callId")

                        // Also signal via WebSocket for real-time delivery (with graceful fallback)
                        val offerPayload = JSONObject().apply {
                            put("callId", callId)
                            put("recipientId", recipientId)
                            put("callType", "audio")
                            put("offer", offer.toJson())
                        }
                        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
                        if (wsManager.isConnected()) {
                            wsManager.send("call:initiate", offerPayload)
                            android.util.Log.d("FREETIME_CALL", "✅ call:initiate emitted via WebSocket")
                        } else {
                            // REST API already succeeded, WebSocket is redundant for basic functionality
                            android.util.Log.w("FREETIME_CALL", "⚠️ WebSocket disconnected - call will use REST API only")
                        }
                    },
                    onFailure = { error ->
                        callErrorMessage = "Failed to initiate call: ${error.message}"
                        callStateManager.updateState(CallState.FAILED)
                        webRTCManager.close()
                        android.util.Log.e("FREETIME_CALL", callErrorMessage)
                    }
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Screen was closed, don't report as error (this is expected)
                android.util.Log.d("FREETIME_CALL", "Call initiation cancelled: user navigated away")
                throw e  // Re-throw to properly cancel the scope
            } catch (e: Exception) {
                callErrorMessage = "Error initiating call: ${e.message ?: "Unknown error"}"
                callStateManager.updateState(CallState.FAILED)
                android.util.Log.e("FREETIME_CALL", callErrorMessage)
            }
        }
    }
    
    // Handle call duration timer + ongoing call notification
    LaunchedEffect(callState) {
        if (callState == CallState.ACTIVE) {
            // Show ongoing call notification (Telegram-style)
            com.freetime.app.notifications.NotificationHelper.showOngoingCallNotification(
                context, recipientName.ifEmpty { chatName }, recipientId
            )
            // Use proper timer with snapshotFlow to monitor state changes
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(1000)
                // Check current state to ensure we stop when call ends
                if (callState != CallState.ACTIVE) {
                    break
                }
                callDurationSeconds++
                android.util.Log.d("FREETIME_CALL", "📞 Call duration: ${formatCallDuration(callDurationSeconds)}")
            }
        } else {
            // Cancel ongoing notification when call is not active
            com.freetime.app.notifications.NotificationHelper.cancelOngoingCallNotification(context)
        }
    }
    
    // CRITICAL FIX: Wait for actual WebSocket event instead of simulating
    // Listen for call answered event from WebSocket
    LaunchedEffect(callState, callId) {
        if (callState == CallState.RINGING && callId.isNotEmpty()) {
            // CRITICAL: WebSocket listener (registered above via webSocketListener object) will now
            // receive onCallAnswered events and update callState to ACTIVE
            // If no answer within 60 seconds, end the call
            android.util.Log.d("FREETIME_CALL", "⏳ Waiting for call: $callId (timeout in 60s, incoming=$isIncomingCall)")
            
            delay(60000)
            if (callState == CallState.RINGING) {
                if (isIncomingCall) {
                    // Recipient side: reject the call (missed)
                    // CRITICAL FIX: Properly handle rejection with error logging
                    scope.launch(Dispatchers.IO) {
                        try { 
                            val result = apiService.rejectCall(callId)
                            if (result.isFailure) {
                                android.util.Log.e("FREETIME_CALL", "Failed to reject missed call: ${result.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FREETIME_CALL", "Exception rejecting missed call", e)
                        }
                    }
                    val rejectPayload = JSONObject().apply {
                        put("callId", callId)
                        put("callerId", recipientId)
                        put("reason", "no_answer")
                    }
                    com.freetime.app.services.WebSocketManager.getInstance().send("call:reject", rejectPayload)
                    com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                    com.freetime.app.notifications.NotificationHelper.showMissedCallNotification(context, recipientName, recipientId)
                } else {
                    // Caller side: end the call (no answer)
                    // CRITICAL FIX: Properly handle end call with error logging
                    scope.launch(Dispatchers.IO) {
                        try { 
                            val result = apiService.endCall(callId)
                            if (result.isFailure) {
                                android.util.Log.e("FREETIME_CALL", "Failed to end call (timeout): ${result.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FREETIME_CALL", "Exception ending call after timeout", e)
                        }
                    }
                    val endCallPayload = JSONObject().apply {
                        put("callId", callId)
                        put("endingUserId", currentUserId)
                        put("recipientId", recipientId)
                    }
                    com.freetime.app.services.WebSocketManager.getInstance().send("call:end", endCallPayload)
                }
                callStateManager.updateState(CallState.ENDED)
                callErrorMessage = if (isIncomingCall) "Missed call" else "No answer (timeout after 60s)"
                android.util.Log.d("FREETIME_CALL", "📴 Call timeout after 60 seconds (incoming=$isIncomingCall)")
                webRTCManager.close()
                delay(2000)
                callStateManager.clearState()
                callId = ""
                callDurationSeconds = 0
                pendingIncomingSdpOffer = ""
                isIncomingCall = false
                isMuted = false
                isSpeakerOn = false
            }
        }
    }
    
    // When call is answered via WebSocket from backend
    // The callState is updated to ACTIVE by the webSocketListener object
    // See webSocketListener.onCallAnswered() above
    
    // Auto-hide image previews after 3 seconds
    LaunchedEffect(visibleImageMediaIds) {
        if (visibleImageMediaIds.isNotEmpty()) {
            delay(3000)
            visibleImageMediaIds = setOf()
            android.util.Log.d("FREETIME_MEDIA", "Auto-hid image previews after 3 seconds")
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }

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
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chat Header
            if (isAnnouncementChat) {
                AnnouncementChatHeader(
                    onNavigateBack = onNavigateBack
                )
            } else {
                ChatScreenHeader(
                    chatName = recipientName,
                    isOnline = recipientIsOnline,
                    onNavigateBack = onNavigateBack,
                    onCallClick = { showComingSoonPopup = true },
                    onVideoClick = { /* Video calls not yet available - WebRTC disabled */ },
                    onMoreClick = { showMenu = true },
                    onViewProfile = onViewProfile,
                    recipientId = recipientId,
                    isFriend = isFriend,
                    nameColor = getUsernameColor(recipientTags, recipientIsAdmin, recipientIsModerator, recipientRole),
                    callState = callState
                )
            }
            
            // Dropdown menu for options - Enhanced UI
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(
                        color = Color(0xFF1A1A2E),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = CyberpunkTheme.CyberCyan.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                DropdownMenuItem(
                    text = { 
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(20.dp))
                            Text("Remove User", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = {
                        showMenu = false
                        removeDialog = true
                    }
                )
                HorizontalDivider(color = CyberpunkTheme.CyberCyan.copy(alpha = 0.2f), thickness = 1.dp)
                DropdownMenuItem(
                    text = { 
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFF00FF), modifier = Modifier.size(20.dp))
                            Text("Report User", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = {
                        showMenu = false
                        reportDialog = true
                    }
                )
                HorizontalDivider(color = CyberpunkTheme.CyberCyan.copy(alpha = 0.2f), thickness = 1.dp)
                DropdownMenuItem(
                    text = { 
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFF00D4FF), modifier = Modifier.size(20.dp))
                            Text("Delete History", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = {
                        showMenu = false
                        showDeleteHistoryDialog = true
                    }
                )
            }


            // Block dialog
            // Remove dialog
            if (removeDialog) {
                AlertDialog(
                    onDismissRequest = { removeDialog = false },
                    title = { Text("Remove Friend") },
                    text = { Text("Are you sure you want to remove this user from your friends?") },
                    confirmButton = {
                        TextButton(onClick = {
                            removeDialog = false
                            scope.launch {
                                try {
                                    val result = apiService.removeFriend(recipientId)
                                    result.onSuccess {
                                        android.util.Log.d("FREETIME_CHAT", "Friend removed successfully")
                                        // Navigate to home page, not just back
                                        if (onNavigateToHome != null) {
                                            onNavigateToHome()
                                        } else {
                                            onNavigateBack()
                                        }
                                    }.onFailure {
                                        android.util.Log.e("FREETIME_CHAT", "Failed to remove friend: ${it.message}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("FREETIME_CHAT", "Error removing friend: ${e.message}")
                                }
                            }
                        }) { Text("Remove") }
                    },
                    dismissButton = {
                        TextButton(onClick = { removeDialog = false }) { Text("Cancel") }
                    }
                )
            }
            // Report dialog - using new ReportUserDialog composable
            if (reportDialog) {
                com.freetime.app.ui.composables.ReportUserDialog(
                    userId = recipientId,
                    userName = recipientName,
                    onDismiss = { reportDialog = false }
                )
            }
            
            // Coming soon popup for calls
            if (showComingSoonPopup) {
                AlertDialog(
                    onDismissRequest = { showComingSoonPopup = false },
                    title = { Text("Coming Soon!") },
                    text = { Text("Voice and video calls will be available in a future update.") },
                    confirmButton = {
                        TextButton(onClick = { showComingSoonPopup = false }) {
                            Text("OK")
                        }
                    }
                )
            }
            
            // Delete History dialog
            if (showDeleteHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteHistoryDialog = false },
                    title = { Text("Delete Chat History") },
                    text = {
                        if (deleteHistoryStatus.isBlank())
                            Text("Are you sure you want to delete all messages, images, videos, and files shared with this user? This will delete history for both users.")
                        else
                            Text(deleteHistoryStatus)
                    },
                    confirmButton = {
                        if (deleteHistoryStatus.isBlank()) {
                            TextButton(onClick = {
                                scope.launch {
                                    deleteHistoryStatus = "Deleting..."
                                    val api = FreeTimeApiService(context)
                                    val result = api.deleteChatHistoryWithUser(recipientId)
                                    result.onSuccess {
                                        showDeleteHistoryDialog = false
                                        deleteHistoryStatus = ""
                                        messages = emptyList()
                                        onNavigateToHome()
                                    }.onFailure {
                                        deleteHistoryStatus = it.message ?: "Failed to delete history."
                                    }
                                }
                            }) { Text("Delete") }
                        } else {
                            TextButton(onClick = {
                                showDeleteHistoryDialog = false
                                deleteHistoryStatus = ""
                            }) { Text("OK") }
                        }
                    },
                    dismissButton = {
                        if (deleteHistoryStatus.isBlank())
                            TextButton(onClick = { showDeleteHistoryDialog = false }) { Text("Cancel") }
                    }
                )
            }
            
            // Voice Call Overlay - WhatsApp style
            if (callState != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    ChatCallOverlay(
                        recipientName = recipientName.ifEmpty { chatName },
                        callState = callState ?: CallState.ENDED,
                        callDuration = callDurationSeconds,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        errorMessage = callErrorMessage,
                        onToggleMute = {
                            isMuted = !isMuted
                            webRTCManager.setMuted(isMuted)
                            android.util.Log.d("FREETIME_CALL", "Mute toggled: $isMuted")
                        },
                        onToggleSpeaker = {
                            isSpeakerOn = !isSpeakerOn
                            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            audioManager.isSpeakerphoneOn = isSpeakerOn
                            android.util.Log.d("FREETIME_CALL", "Speaker toggled: $isSpeakerOn")
                        },
                        onHangup = { 
                            scope.launch {
                                if (callId.isNotEmpty()) {
                                    // End call via REST API (reliable)
                                    try {
                                        apiService.endCall(callId)
                                    } catch (e: Exception) {
                                        android.util.Log.e("FREETIME_CALL", "API end call failed: ${e.message}")
                                    }
                                    // Also signal via WebSocket (fast)
                                    val endCallPayload = JSONObject().apply {
                                        put("callId", callId)
                                        put("endingUserId", currentUserId)
                                        put("recipientId", recipientId)
                                    }
                                    com.freetime.app.services.WebSocketManager.getInstance().send("call:end", endCallPayload)
                                }
                                webRTCManager.close()
                                callStateManager.clearState()
                                callDurationSeconds = 0
                                callId = ""
                                pendingIncomingSdpOffer = ""
                                isIncomingCall = false
                                isMuted = false
                                isSpeakerOn = false
                            }
                        },
                        onAccept = if (callState == CallState.RINGING && pendingIncomingSdpOffer.isNotEmpty()) {
                            {
                                // Guard: prevent double-tap — only proceed if still RINGING
                                if (callState == CallState.RINGING) {
                                    // ✅ NEW: Check for audio permission before accepting
                                    var hasPermission = true
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            android.widget.Toast.makeText(context, "Microphone permission required to answer call", android.widget.Toast.LENGTH_LONG).show()
                                            // Trigger permission request via activity
                                            (context as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
                                            hasPermission = false
                                        }
                                    }

                                    if (hasPermission) {
                                        // User manually accepted the incoming call — now do WebRTC handshake
                                        callStateManager.updateState(CallState.CONNECTING, callId)
                                        scope.launch {
                                            try {
                                                android.util.Log.d("FREETIME_CALL", "User accepted incoming call: $callId")
                                                // Cancel incoming call notification
                                                com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                                                webRTCManager.createPeerConnection()
                                                
                                                // Enable video if this is a video call
                                                if (incomingCallType == "video") {
                                                    webRTCManager.enableVideo(true)
                                                    android.util.Log.d("FREETIME_CALL", "📷 Video enabled for incoming video call")
                                                }
                                                
                                                val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, pendingIncomingSdpOffer)
                                                webRTCManager.setRemoteDescription(remoteOffer)

                                                val answer = webRTCManager.createAnswer()

                                                // Send via REST API (reliable)
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        apiService.answerCall(callId, answer.toJson().toString())
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("FREETIME_CALL", "API answer failed: ${e.message}")
                                                    }
                                                }

                                                // Also send via WebSocket (fast real-time)
                                                val answerPayload = JSONObject().apply {
                                                    put("callId", callId)
                                                    put("callerId", recipientId)
                                                    put("answer", answer.toJson())
                                                }
                                                com.freetime.app.services.WebSocketManager.getInstance().send("call:answer", answerPayload)
                                                callStateManager.updateState(CallState.ACTIVE, callId)
                                                callDurationSeconds = 0
                                                pendingIncomingSdpOffer = ""
                                                android.util.Log.d("FREETIME_CALL", "Answer sent for call: $callId")
                                            } catch (e: Exception) {
                                                android.util.Log.e("FREETIME_CALL", "Error accepting call: ${e.message}")
                                                callErrorMessage = "Failed to accept call: ${e.message}"
                                                callStateManager.updateState(CallState.FAILED)
                                            }
                                        }
                                    }
                                } // end guard: if (callState == RINGING)
                            }
                        } else null,
                        onReject = if (callState == CallState.RINGING) {
                            {
                                scope.launch {
                                    if (callId.isNotEmpty()) {
                                        try {
                                            apiService.rejectCall(callId)
                                        } catch (e: Exception) {
                                            android.util.Log.e("FREETIME_CALL", "API reject call failed: ${e.message}")
                                        }
                                        // Also signal via WebSocket
                                        val rejectPayload = JSONObject().apply {
                                            put("callId", callId)
                                            put("callerId", recipientId)
                                            put("reason", "declined")
                                        }
                                        com.freetime.app.services.WebSocketManager.getInstance().send("call:reject", rejectPayload)
                                    }
                                    // Cancel incoming call notification
                                    com.freetime.app.notifications.NotificationHelper.cancelCallNotification(context, recipientId)
                                    webRTCManager.close()
                                    callStateManager.clearState()
                                    callDurationSeconds = 0
                                    callId = ""
                                    pendingIncomingSdpOffer = ""
                                    isIncomingCall = false
                                }
                            }
                        } else null
                    )
                }
            }
            
            // CRITICAL FIX: Warning Banner - User not found or offline
            if (!recipientExists) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFD32F2F))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        "Warning",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Text(
                        "User not found in database. Cannot send messages or make calls.",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (!recipientIsOnline && !isAnnouncementChat) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFA726))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        "Info",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Text(
                        "User is currently offline. Messages will be delivered when they come online.",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
            
            // ===== SEARCH BAR =====
            if (showSearchResults || searchQuery.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF5F5F5),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            "Search",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Gray
                        )
                        
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages...", fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = TextStyle(fontSize = 14.sp)
                        )
                        
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { 
                                    searchQuery = ""
                                    showSearchResults = false
                                    searchResults = emptyList()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Clear search",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                        
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.Blue
                            )
                        } else if (showSearchResults && searchResults.isNotEmpty()) {
                            Text(
                                "${searchResults.size}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            
            // ===== TYPING INDICATOR =====
            AnimatedVisibility(
                visible = isRecipientTyping,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Color(0xFFF0F0F0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            "Typing",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Text(
                            "$recipientName is typing...",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                        // Animated dots
                        repeat(3) { i ->
                            Text(
                                "•",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .animateContentSize()
                                    .alpha(0.4f + 0.6f * ((System.currentTimeMillis() / 300L + i) % 3) / 2f)
                            )
                        }
                    }
                }
            }
            
            // Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Show search results or all messages
                // For announcement chats, only show the latest announcement
                val messagesToDisplay = if (showSearchResults && searchResults.isNotEmpty()) {
                    searchResults
                } else if (showSearchResults && searchQuery.isNotEmpty()) {
                    // Searching but no results
                    emptyList()
                } else if (isAnnouncementChat && messages.isNotEmpty()) {
                    val now = announcementNow.value
                    val threeMinAgo = now - 3 * 60 * 1000L
                    val activeAnnouncements = messages.filter { msg ->
                        val delivered = announcementDeliveredAt.value[msg.id] ?: 0L
                        delivered >= threeMinAgo
                    }
                    if (activeAnnouncements.isNotEmpty()) listOf(activeAnnouncements.first()) else emptyList()
                } else {
                    messages
                }
                
                if (messagesToDisplay.isEmpty() && (messages.isEmpty() || (showSearchResults && searchQuery.isNotEmpty()))) {
                    if (showSearchResults && searchQuery.isNotEmpty()) {
                        // No search results
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                "No results",
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No messages found matching \"$searchQuery\"",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        EmptyChatMessage(mascotVisible = showMascotTip)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        reverseLayout = true
                    ) {
                        items(
                            items = messagesToDisplay,
                            key = { it.id }  // ✅ FIX: Add key to prevent recomposition issues
                        ) { message ->
                            // Multi-select and Long-press logic
                            val isSelected = selectedMessages.contains(message.id)
                            
                            // Extract media ID to check if this is a media message
                            val msgMediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                            val isMsgMediaMessage = msgMediaIdRegex.find(message.content) != null
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                selectedMessages = if (isSelected) {
                                                    selectedMessages - message.id
                                                } else {
                                                    selectedMessages + message.id
                                                }
                                                if (selectedMessages.isEmpty()) isMultiSelectMode = false
                                            }
                                        },
                                        onLongClick = {
                                            if (isMultiSelectMode) {
                                                // In multi-select mode, toggle selection
                                                selectedMessages = if (isSelected) {
                                                    selectedMessages - message.id
                                                } else {
                                                    selectedMessages + message.id
                                                }
                                                if (selectedMessages.isEmpty()) isMultiSelectMode = false
                                            } else if (isMsgMediaMessage && !message.isSender && message.mediaShareMode == "protected") {
                                                // Long-press on received PROTECTED media: request download permission
                                                val mediaId = msgMediaIdRegex.find(message.content)?.groupValues?.get(1)
                                                if (mediaId != null) {
                                                    sendMediaDownloadRequests(listOf(mediaId))
                                                }
                                            } else {
                                                // Long-press on regular message: show context menu
                                                selectedMessageId = message.id
                                                selectedMessageText = message.content
                                                selectedMessageIsOwn = message.isSender
                                                showMessageContextMenu = true
                                            }
                                        }
                                    )
                                    .background(
                                        if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                            ) {
                                    MessageBubble(
                                        message = message,
                                        onReactionAdd = { emoji -> addReaction(message.id, emoji) },
                                        mediaDownloadRequests = mediaDownloadRequests,
                                        visibleImageMediaIds = visibleImageMediaIds,
                                        onShowImagePreview = { visibleImageMediaIds = visibleImageMediaIds + it },
                                        onRequestMediaDownload = { 
                                            // Auto-request for single tap if enabled or via multi-select
                                            val mediaIds = listOf(it)
                                            sendMediaDownloadRequests(mediaIds)
                                        },
                                        onApproveRequest = { requestId ->
                                            scope.launch {
                                                apiService.approveMediaDownloadRequest(requestId).onSuccess {
                                                    // Update UI immediately (WebSocket will also update it)
                                                    messages = messages.map { m ->
                                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                                    }
                                                }
                                            }
                                        },
                                        onDenyRequest = { requestId ->
                                            scope.launch {
                                                apiService.denyMediaDownloadRequest(requestId).onSuccess {
                                                    messages = messages.map { m ->
                                                        m.copy(pendingRequests = m.pendingRequests.filterNot { it.requestId == requestId })
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    
                                    // Reaction Overlay (if this message is the one being long-pressed)
                                    if (messageToReact?.id == message.id) {
                                        ReactionPicker(
                                            onReactionSelected = { emoji ->
                                                addReaction(message.id, emoji)
                                                messageToReact = null
                                            },
                                            onReplyClick = {
                                                replyingToMessage = message
                                                messageToReact = null
                                            },
                                            onDismiss = { messageToReact = null }
                                        )
                                    }
                                
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { 
                                            selectedMessages = if (it) selectedMessages + message.id else selectedMessages - message.id
                                            if (selectedMessages.isEmpty()) isMultiSelectMode = false
                                        },
                                        modifier = Modifier.align(Alignment.CenterStart),
                                        colors = CheckboxDefaults.colors(checkedColor = CyberpunkTheme.PrimaryPurple)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // MULTI-SELECT ACTION BAR
                if (isMultiSelectMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = CyberpunkTheme.DarkGray,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${selectedMessages.size} selected",
                                color = CyberpunkTheme.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { 
                                    isMultiSelectMode = false
                                    selectedMessages = emptySet()
                                }) {
                                    Text("Cancel", color = CyberpunkTheme.LightGray)
                                }
                                
                                Button(
                                    onClick = {
                                        // Extract media IDs from selected messages
                                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                                        val mediaIds = messages.filter { it.id in selectedMessages }
                                            .mapNotNull { mediaIdRegex.find(it.content)?.groupValues?.get(1) }
                                        
                                        if (mediaIds.isNotEmpty()) {
                                            sendMediaDownloadRequests(mediaIds)
                                        } else {
                                            Toast.makeText(context, "No media files selected", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Request Download", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            // Mascot Tip (Optional)
            if (showMascotTip && messages.size > 2) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { 100 }) + fadeOut()
                ) {
                    MascotChatTip(
                        onDismiss = { showMascotTip = false }
                    )
                }
            }
            
            // Input Area
            if (isAnnouncementChat) {
                ReadOnlyChatBanner()
            } else {
                ChatInputArea(
                        messageText = messageText,
                        onMessageChange = { messageText = it },
                        isTyping = isTyping,
                        replyTo = replyingToMessage,
                        onCancelReply = { replyingToMessage = null },
                        onSendClick = {
                            sendMessage()
                        },
                        onTyping = {
                            sendTypingIndicator()
                        },
                        onAttachClick = { 
                            if (!isProcessingMedia) {
                                showMediaModeDialog = true
                            }
                        },
                    onGifClick = {
                        if (!isProcessingMedia) {
                            showGifPicker = true
                        }
                    },
                    onEmojiClick = {
                        showEmojiPicker = !showEmojiPicker
                    },
                    onFocusChange = { focused -> isInputFocused = focused }
                )
        }
    }
    
    // Emoji Picker Dialog
    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text("Select Emoji", color = CyberpunkTheme.LightGray) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val emojiList = listOf(
                        "😊", "😂", "❤️", "😍", "😎", "😢", "😡", "😱", "👍", "👎",
                        "🙏", "🎉", "🔥", "💯", "✨", "⭐", "👋", "👏", "🙌", "💪",
                        "🎈", "🎊", "🎁", "🌟", "💖", "💝", "👀", "✌️", "🤔", "🤗"
                    )
                    
                    // Grid of emojis (5 per row) - LARGER FONTS FOR BETTER VISIBILITY
                    for (i in emojiList.indices step 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in 0 until 5) {
                                if (i + j < emojiList.size) {
                                    Button(
                                        onClick = {
                                            messageText = messageText + emojiList[i + j]
                                            showEmojiPicker = false
                                            android.util.Log.d("FREETIME_CHAT", "Emoji selected: ${emojiList[i + j]}")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f),
                                            contentColor = Color.Unspecified // EMOJI FIX: Do not tint emojis
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            emojiList[i + j],
                                            fontSize = 28.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEmojiPicker = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberpunkTheme.PrimaryPurple)
                ) {
                    Text("Close")
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = CyberpunkTheme.LightGray,
            titleContentColor = CyberpunkTheme.PrimaryPurple
        )
    }
    
    // ✅ NEW: Media sharing mode selection dialog
    if (showMediaModeDialog) {
        AlertDialog(
            onDismissRequest = { showMediaModeDialog = false },
            title = { Text("Share Media As:") },
            text = { 
                Text(
                    "Choose how to share this media:\n\n" +
                    "• PUBLIC: Viewable immediately by the recipient\n\n" +
                    "• PROTECTED: Encrypted, recipient must request download"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingMediaShareMode = "public"
                        showMediaModeDialog = false
                        mediaPickerLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA00))
                ) { 
                    Text("Public") 
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        pendingMediaShareMode = "protected"
                        showMediaModeDialog = false
                        mediaPickerLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA8800))
                ) { 
                    Text("Protected") 
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = CyberpunkTheme.LightGray,
            titleContentColor = CyberpunkTheme.PrimaryPurple
        )
    }

    // ✅ NEW: GIF Picker
    GifPickerDialog(
        visible = showGifPicker,
        onDismiss = { showGifPicker = false },
        onGifSelected = { gifUrl, _ ->
            showGifPicker = false
            scope.launch {
                isProcessingMedia = true
                try {
                    val gifBytes = withContext(Dispatchers.IO) {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder().url(gifUrl).build()
                        val response = client.newCall(request).execute()
                        response.body?.bytes() ?: byteArrayOf()
                    }
                    if (gifBytes.isNotEmpty()) {
                        val fileName = "gif_${System.currentTimeMillis()}.gif"
                        val uploadResult = apiService.uploadMediaToChat(
                            mediaData = gifBytes,
                            fileName = fileName,
                            mimeType = "image/gif",
                            recipientId = recipientId,
                            token = token,
                            mediaShareMode = "public"
                        )
                        if (uploadResult != null) {
                            val (serverMediaId, _) = uploadResult
                            val sendRequest = SendMessageRequest(
                                recipientId = recipientId,
                                content = "[Media: $serverMediaId] $fileName"
                            )
                            val response = apiClient.sendMessage(sendRequest, "Bearer $token")
                            if (response.isSuccessful) {
                                messages = listOf(Message(
                                    id = serverMediaId,
                                    senderName = currentUsername,
                                    content = "[Media: $serverMediaId] $fileName",
                                    timestamp = "Now",
                                    isSender = true,
                                    status = "sent",
                                    mediaId = serverMediaId,
                                    mediaType = "image",
                                    mediaName = fileName,
                                    senderTags = emptyList(),
                                    senderIsAdmin = false,
                                    senderIsModerator = false,
                                    mediaShareMode = "public"
                                )) + messages
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val encryptedContent = encryptionManager.encrypt(
                                            "[Media: $serverMediaId] $fileName",
                                            "$recipientId:$currentUserId"
                                        )
                                        val entity = com.freetime.app.data.local.database.MessageEntity(
                                            messageId = serverMediaId,
                                            chatId = recipientId,
                                            senderId = currentUserId,
                                            contentEncrypted = encryptedContent,
                                            timestamp = System.currentTimeMillis(),
                                            isRead = true,
                                            syncState = "synced",
                                            mediaType = "image",
                                            mediaName = fileName
                                        )
                                        database.messageDao().insertMessage(entity)
                                    } catch (e: Exception) {
                                        android.util.Log.e("FREETIME_MEDIA", "Failed to persist GIF: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FREETIME_MEDIA", "GIF upload error: ${e.message}")
                } finally {
                    isProcessingMedia = false
                }
            }
        }
    )

    // ✅ NEW: Message Context Menu
    val contextMessage = messages.find { it.id == selectedMessageId }
    val contextMediaId = contextMessage?.let { msg ->
        val regex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
        regex.find(msg.content)?.groupValues?.get(1) ?: msg.mediaId
    }
    val hasPublicMedia = contextMessage?.let { msg ->
        msg.mediaShareMode == "public"
    } ?: false
    if (showMessageContextMenu && selectedMessageId != null) {
        MessageContextMenu(
            messageId = selectedMessageId ?: "",
            messageText = selectedMessageText,
            isOwnMessage = selectedMessageIsOwn,
            showMenu = showMessageContextMenu,
            onDismiss = { showMessageContextMenu = false },
            onCopy = { 
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", selectedMessageText))
                showMessageContextMenu = false
            },
            onDelete = { },
            onReact = { emoji -> selectedMessageId?.let { addReaction(it, emoji) } },
            onReply = { selectedMessageId?.let { replyToPrivateMessage(it); showMessageContextMenu = false } },
            onEdit = { },
            currentReactions = emptyMap(),
            hasPublicMedia = hasPublicMedia,
            onDownload = {
                val mediaId = contextMediaId
                if (mediaId != null && mediaId.isNotEmpty()) {
                    scope.launch {
                        try {
                            val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                                override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                                override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                            })
                            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                            val client = OkHttpClient.Builder()
                                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                                .hostnameVerifier { _, _ -> true }
                                .build()
                            val downloadReq = Request.Builder()
                                .url("$baseUrl/api/media/$mediaId/download")
                                .addHeader("Authorization", "Bearer $token")
                                .get()
                                .build()
                            val response = client.newCall(downloadReq).execute()
                            if (response.isSuccessful) {
                                val bytes = response.body?.bytes() ?: return@launch
                                val fileName = contextMessage?.mediaName ?: "media_$mediaId"
                                val mimeType = response.body?.contentType()?.toString() ?: "application/octet-stream"
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/FreeTime")
                                    }
                                    val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                    if (uri != null) {
                                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                    }
                                } else {
                                    val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "FreeTime")
                                    if (!dir.exists()) dir.mkdirs()
                                    java.io.File(dir, fileName).writeBytes(bytes)
                                }
                                Toast.makeText(context, "Saved: $fileName", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showMessageContextMenu = false
                    }
                }
            }
        )
    }

}
}

@Composable
fun ReactionPicker(
    onReactionSelected: (String) -> Unit,
    onReplyClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val reactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
    
    androidx.compose.ui.window.Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            color = Color(0xFF1A1A2E),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reactions.forEach { emoji ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .clickable { onReactionSelected(emoji) }
                                .padding(4.dp),
                            fontSize = 24.sp
                        )
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                )
                
                TextButton(
                    onClick = onReplyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Reply, null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reply", color = CyberpunkTheme.CyberCyan)
                }
            }
        }
    }
}

@Composable
fun AnnouncementChatHeader(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF8C00), Color(0xFFFFAA00))
                )
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Image(
                    painter = painterResource(id = com.freetime.app.R.drawable.saying),
                    contentDescription = "Announcement Mascot",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                    contentScale = ContentScale.Crop
                )

                Column {
                    Text(
                        com.freetime.app.api.ANNOUNCEMENT_DISPLAY_NAME,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Admin",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ReadOnlyChatBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A2E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFFFFAA00),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "This is a read-only announcement. You cannot reply.",
                color = Color(0xFFFFAA00).copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ChatScreenHeader(
    chatName: String,
    isOnline: Boolean,
    onNavigateBack: () -> Unit,
    onCallClick: () -> Unit,
    onVideoClick: () -> Unit,
    onMoreClick: () -> Unit,
    onViewProfile: (userId: String) -> Unit = {},
    recipientId: String = "",
    isFriend: Boolean = false,
    nameColor: Color = Color.White,  // ✅ NEW: Color parameter with default white
    callState: CallState? = null  // ✅ NEW: Add callState to control button
) {
    android.util.Log.d("CHAT_HEADER", "Rendering header - Name: '$chatName', Color: ${nameColor}, Online: $isOnline, CallState: $callState")
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = CyberpunkTheme.PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = recipientId.isNotEmpty(),
                            onClick = { onViewProfile(recipientId) }
                        )
                ) {
                    // ✅ CRITICAL: Show displayName with color, use only "User" if truly empty
                    val displayNameToShow = chatName.takeIf { it.isNotEmpty() } ?: "User"
                    
                    // 🔍 DEBUG: Log what we're displaying in the header
                    androidx.compose.runtime.LaunchedEffect(displayNameToShow) {
                        android.util.Log.d("CHAT_HEADER", "📺 Displaying header name: '$displayNameToShow' (chatName='$chatName')")
                    }
                    
                    Text(
                        displayNameToShow,
                        color = nameColor,  // ✅ UPDATED: Apply color-coded name color
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                            if (isOnline) "Online" else "Offline",
                            color = CyberpunkTheme.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFriend) {
                    IconButton(
                        onClick = onCallClick,
                        enabled = (callState == null || callState == CallState.ENDED || callState == CallState.FAILED),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            null,
                            tint = if (callState == null || callState == CallState.ENDED || callState == CallState.FAILED) CyberpunkTheme.PrimaryPurple else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onMoreClick,
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
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onReactionAdd: (String) -> Unit,
    mediaDownloadRequests: Map<String, String> = emptyMap(),
    visibleImageMediaIds: Set<String> = emptySet(),
    onShowImagePreview: (String) -> Unit = {},
    onRequestMediaDownload: (String) -> Unit = {},
    onApproveRequest: (String) -> Unit = {},
    onDenyRequest: (String) -> Unit = {}
) {
    // Extract media ID if this is a media message
    val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
    val mediaMatch = mediaIdRegex.find(message.content)
    val contentMediaId = mediaMatch?.groupValues?.get(1)
    val mediaId = contentMediaId ?: message.mediaId
    val contentFileName = contentMediaId?.let {
        message.content.substringAfter("] ").takeIf { it.isNotEmpty() } ?: "Unknown"
    }
    val fileName = contentFileName ?: message.mediaName
    val isMediaMessage = mediaId != null
    val isImage = message.mediaType == "image" ||
                  fileName?.endsWith(".jpg", ignoreCase = true) == true || 
                  fileName?.endsWith(".png", ignoreCase = true) == true ||
                  fileName?.endsWith(".jpeg", ignoreCase = true) == true ||
                  fileName?.endsWith(".gif", ignoreCase = true) == true
    val isVideo = message.mediaType == "video" ||
                  fileName?.endsWith(".mp4", ignoreCase = true) == true ||
                  fileName?.endsWith(".mov", ignoreCase = true) == true
    val mediaApprovalStatus = if (mediaId != null) mediaDownloadRequests[mediaId] else null
    val mediaKey = mediaMatch?.groupValues?.getOrNull(2)
    val isPublicMedia = message.mediaShareMode == "public"
    
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    
    // Calculate responsive bubble width based on screen size
    // For small screens (< 380 dp), use 75% of screen width
    // For normal screens, use up to 280 dp
    val maxBubbleWidth = when {
        screenWidthDp < 380.dp -> screenWidthDp * 0.75f
        screenWidthDp < 600.dp -> (screenWidthDp * 0.75f).coerceAtMost(280.dp)
        else -> 280.dp
    }
    
    // Responsive padding based on screen size
    val bubblePadding = when {
        screenWidthDp < 380.dp -> 8.dp  // Smaller padding for tiny screens
        screenWidthDp < 600.dp -> 10.dp // Medium padding for phones
        else -> 12.dp                    // Regular padding for larger screens
    }
    
    // Responsive font sizes
    val messageFontSize = when {
        screenWidthDp < 380.dp -> 12.sp
        screenWidthDp < 600.dp -> 13.sp
        else -> 14.sp
    }
    
    val timestampFontSize = when {
        screenWidthDp < 380.dp -> 9.sp
        screenWidthDp < 600.dp -> 10.sp
        else -> 11.sp
    }
    
    val scaleAnimation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scaleAnimation, scaleY = scaleAnimation),
        horizontalAlignment = if (message.isSender) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
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
                .padding(bubblePadding)
                .widthIn(max = maxBubbleWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (!message.isSender) {
                // ✅ FIXED: Fallback to initial letter if no avatar URL
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = Color(0xFF9D4EDD).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF9D4EDD),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        message.senderName.firstOrNull()?.toString() ?: "?",
                        color = CyberpunkTheme.CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Show reply context if this message is replying to another
                if (!message.replyToMessageId.isNullOrEmpty() && 
                    message.replyToMessageId != "null" &&
                    (!message.replyToUsername.isNullOrEmpty() || !message.replyToText.isNullOrEmpty())) {
                    Surface(
                        color = if (message.isSender) Color.Black.copy(alpha = 0.25f) else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .fillMaxWidth()
                        ) {
                            // Left accent bar to distinguish reply
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        if (message.isSender) Color.Black.copy(alpha = 0.6f) 
                                        else CyberpunkTheme.CyberCyan,
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
                                        tint = if (message.isSender) Color.Black.copy(alpha = 0.7f) else CyberpunkTheme.CyberCyan
                                    )
                                    Text(
                                        text = message.replyToUsername ?: "Unknown",
                                        fontSize = 11.sp,
                                        color = if (message.isSender) Color.Black.copy(alpha = 0.8f) else CyberpunkTheme.CyberCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                val replyText = if (message.replyToText == "null" || message.replyToText.isNullOrEmpty()) "Media" else message.replyToText
                                Text(
                                    text = replyText,
                                    fontSize = 11.sp,
                                    color = if (message.isSender) Color.DarkGray else CyberpunkTheme.LightGray.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // ✅ NEW: Show sender name with color coding for recipient messages
                if (!message.isSender) {
                    Text(
                        text = message.senderName,
                        color = getUsernameColor(message.senderTags, message.senderIsAdmin, message.senderIsModerator, message.senderRole),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                when {
                    isMediaMessage && isVideo -> {
                        // CRITICAL FIX: Enable video playback status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎥 Video: $fileName",
                                color = if (message.isSender) CyberpunkTheme.Black else CyberpunkTheme.CyberCyan,
                                fontSize = messageFontSize,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isPublicMedia) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFFAA8800)
                                )
                            }
                        }
                        
                        when (mediaApprovalStatus) {
                            "approved" -> {
                                Button(
                                    onClick = { /* Open video player */ },
                                    modifier = Modifier.fillMaxWidth(0.8f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF51CF66)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play Video", fontSize = 11.sp)
                                }
                            }
                            "pending" -> {
                                Text("⏳ Waiting for approval...", color = Color(0xFFFFD700), fontSize = messageFontSize * 0.8f)
                            }
                            else -> {
                                if (!message.isSender && !isPublicMedia) {
                                    Button(
                                        onClick = { mediaId?.let { onRequestMediaDownload(it) } },
                                        modifier = Modifier.fillMaxWidth(0.8f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D4EDD)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(8.dp)
                                    ) {
                                        Text("🔓 Request to Watch", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    isMediaMessage && isImage -> {
                        if (isPublicMedia) {
                            val context = LocalContext.current
                            val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                            val token = SharedPreferencesHelper(context).getToken() ?: ""
                            var imageLoadError by remember(mediaId) { mutableStateOf<String?>(null) }
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("$baseUrl/api/media/$mediaId/download")
                                    .addHeader("Authorization", "Bearer $token")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = fileName,
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                                onError = { imageLoadError = "Load failed" }
                            )
                            if (imageLoadError != null) {
                                Text(
                                    "❌ ${imageLoadError}",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = messageFontSize * 0.8f,
                                )
                            }
                        } else {
                            // Protected media: show lock icon + existing approval flow
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🖼️ Image: $fileName",
                                    color = if (message.isSender) CyberpunkTheme.Black else CyberpunkTheme.CyberCyan,
                                    fontSize = messageFontSize,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFFAA8800)
                                )
                            }
                            
                            when (mediaApprovalStatus) {
                                "approved" -> {
                                    if (mediaId in visibleImageMediaIds) {
                                        // ✅ FIX: Load and display decrypted image preview
                                        var cachedImagePath by remember(mediaId) { mutableStateOf<String?>(null) }
                                        var isLoadingImage by remember(mediaId) { mutableStateOf(true) }
                                        var imageLoadError by remember(mediaId) { mutableStateOf<String?>(null) }
                                        val context = LocalContext.current
                                        
                                        LaunchedEffect(mediaId) {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val token = SharedPreferencesHelper(context).getToken() ?: return@withContext
                                                    val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                                                    val client = OkHttpClient()
                                                    val request = Request.Builder()
                                                        .url("$baseUrl/api/media/$mediaId/download")
                                                        .addHeader("Authorization", "Bearer $token")
                                                        .get()
                                                        .build()
                                                    
                                                    val response = client.newCall(request).execute()
                                                    
                                                    if (response.isSuccessful) {
                                                        val encryptedData = response.body?.bytes() ?: ByteArray(0)
                                                        
                                                        if (encryptedData.isNotEmpty()) {
                                                            val cacheDir = context.cacheDir
                                                            val ext = when {
                                                                fileName?.endsWith(".gif", ignoreCase = true) == true -> ".gif"
                                                                fileName?.endsWith(".png", ignoreCase = true) == true -> ".png"
                                                                fileName?.endsWith(".webp", ignoreCase = true) == true -> ".webp"
                                                                else -> ".jpg"
                                                            }
                                                            val cacheFile = File(cacheDir, "preview_$mediaId$ext")
                                                            cacheFile.writeBytes(encryptedData)
                                                            cachedImagePath = cacheFile.absolutePath
                                                            android.util.Log.d("IMAGE_PREVIEW", "Cached image at: ${cacheFile.absolutePath}")
                                                        }
                                                    } else {
                                                        imageLoadError = "Failed to load image: ${response.code}"
                                                    }
                                                } catch (e: Exception) {
                                                    imageLoadError = "Error loading image: ${e.message}"
                                                    android.util.Log.e("IMAGE_PREVIEW", "Image loading error", e)
                                                } finally {
                                                    isLoadingImage = false
                                                }
                                            }
                                        }
                                        
                                        when {
                                            isLoadingImage -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(30.dp),
                                                    color = Color(0xFF51CF66),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            cachedImagePath != null -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.8f)
                                                        .heightIn(max = 200.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF1a1a2e))
                                                ) {
                                                    AsyncImage(
                                                        model = cachedImagePath,
                                                        contentDescription = fileName,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 200.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            }
                                            imageLoadError != null -> {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("🔒", fontSize = 28.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("Protected", color = Color(0xFFAA8800), fontSize = messageFontSize * 0.8f, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.height(8.dp))
                                                    Button(
                                                        onClick = { mediaId?.let { onRequestMediaDownload(it) } },
                                                        modifier = Modifier.fillMaxWidth(0.8f).height(32.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D4EDD)),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(8.dp)
                                                    ) {
                                                        Text("🔓 Request Download", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Text(
                                            "📸 Preview active (auto-hides in 3 seconds)",
                                            color = Color(0xFF51CF66),
                                            fontSize = messageFontSize * 0.8f
                                        )
                                    } else {
                                        Text(
                                            "👁️ Preview expired",
                                            color = CyberpunkTheme.GhostGray,
                                            fontSize = messageFontSize * 0.8f
                                        )
                                    }
                                }
                                "pending" -> {
                                    Text(
                                        "⏳ Waiting for approval...",
                                        color = Color(0xFFFFD700),
                                        fontSize = messageFontSize * 0.8f
                                    )
                                }
                                "denied" -> {
                                    Text(
                                        "❌ Download denied",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = messageFontSize * 0.8f
                                    )
                                }
                                else -> {
                                    // No approval request yet
                                    if (!message.isSender) {
                                        Text(
                                            "Request download from owner",
                                            color = CyberpunkTheme.GhostGray,
                                            fontSize = messageFontSize * 0.8f
                                        )
                                        Button(
                                            onClick = { mediaId?.let { onRequestMediaDownload(it) } },
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .height(32.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF9D4EDD)
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(8.dp)
                                        ) {
                                            Text("🔓 Request Download", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    isMediaMessage -> {
                        // Generic file (audio, document, archive, etc.)
                        val fileIcon = when {
                            message.mediaType == "audio" || fileName?.endsWith(".mp3", ignoreCase = true) == true || fileName?.endsWith(".wav", ignoreCase = true) == true || fileName?.endsWith(".flac", ignoreCase = true) == true || fileName?.endsWith(".ogg", ignoreCase = true) == true -> "🎵"
                            fileName?.endsWith(".pdf", ignoreCase = true) == true -> "📄"
                            fileName?.endsWith(".zip", ignoreCase = true) == true || fileName?.endsWith(".rar", ignoreCase = true) == true || fileName?.endsWith(".7z", ignoreCase = true) == true -> "📦"
                            fileName?.endsWith(".apk", ignoreCase = true) == true -> "📱"
                            else -> "📁"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$fileIcon File: $fileName",
                                color = if (message.isSender) CyberpunkTheme.Black else CyberpunkTheme.CyberCyan,
                                fontSize = messageFontSize,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isPublicMedia) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFFAA8800)
                                )
                            }
                        }
                        when (mediaApprovalStatus) {
                            "approved" -> {
                                Button(
                                    onClick = { mediaId?.let { onRequestMediaDownload(it) } },
                                    modifier = Modifier.fillMaxWidth(0.8f).height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF51CF66)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download File", fontSize = 11.sp)
                                }
                            }
                            "pending" -> {
                                Text("⏳ Waiting for approval...", color = Color(0xFFFFD700), fontSize = messageFontSize * 0.8f)
                            }
                            else -> {
                                if (!message.isSender && !isPublicMedia) {
                                    Button(
                                        onClick = { mediaId?.let { onRequestMediaDownload(it) } },
                                        modifier = Modifier.fillMaxWidth(0.8f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D4EDD)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(8.dp)
                                    ) {
                                        Text("🔓 Request Download", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // Display text message with clickable links
                        val uriHandler = LocalUriHandler.current
                        val annotatedString = buildClickableText(message.content)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (message.isAnnouncement && !message.subject.isNullOrBlank()) {
                                Text(
                                    text = "📢 ${message.subject}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyberpunkTheme.CyberCyan,
                                    fontSize = 13.sp
                                )
                            }

                            ClickableText(
                                text = annotatedString,
                                onClick = { offset ->
                                    annotatedString.getStringAnnotations(
                                        tag = "URL",
                                        start = offset,
                                        end = offset
                                    ).firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                        android.util.Log.d("FREETIME_CHAT", "Opening URL: ${annotation.item}")
                                    }
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    color = if (message.isSender) CyberpunkTheme.Black else CyberpunkTheme.White,
                                    fontSize = messageFontSize
                                )
                            )
                        }
                    }
                }
                
                // ✅ NEW: Media download request approval UI (for sender)
                if (message.isSender && message.pendingRequests.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (message.reactions.isNotEmpty()) {
                        message.reactions.forEach { reaction ->
                            ReactionChip(reaction = reaction)
                        }
                    }
                }
            }
        }
        
        // Timestamp + Status Indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                message.timestamp,
                color = CyberpunkTheme.GhostGray,
                fontSize = timestampFontSize
            )
            
            // Status indicators (only for sender)
            if (message.isSender) {
                when (message.status) {
                    "sending" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.dp,
                            color = Color.Gray
                        )
                    }
                    "sent" -> {
                        Text(
                            "✓",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    "delivered" -> {
                        Text(
                            "✓✓",
                            color = Color(0xFF51CF66), // Green for delivered
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    "failed" -> {
                        Icon(
                            Icons.Default.Error,
                            "Failed",
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFFFF6B6B) // Red for failure
                        )
                    }
                }
            }
            
            // Read receipt (✓✓ with blue check)
            if (message.isSender && message.isRead) {
                Text(
                    "✓✓",
                    color = Color(0xFF2196F3), // Blue for read
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

    }
}

@Composable
fun ReactionChip(
    reaction: String
) {
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
            .padding(4.dp)
    ) {
        Text(
            reaction,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun MascotChatTip(
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = CyberpunkTheme.CyberCyan.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.half_hello),
                contentDescription = "Cody Tip",
                modifier = Modifier
                    .size(48.dp),
                contentScale = ContentScale.Fit
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    "Tip: Use reactions! 👍❤️😂",
                    color = CyberpunkTheme.CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                
                Text(
                    "Long-press messages to react",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 11.sp
                )
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    null,
                    tint = CyberpunkTheme.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(
    messageText: String,
    onMessageChange: (String) -> Unit,
    isTyping: Boolean,
    replyTo: Message? = null,
    onCancelReply: () -> Unit = {},
    onSendClick: () -> Unit,
    onTyping: (() -> Unit)? = null,
    onAttachClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onGifClick: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit) = {}
) {
    // Debounce typing indicator sends
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            onTyping?.invoke()
        }
    }
    
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    
    // Responsive input padding
    val inputPadding = when {
        screenWidthDp < 380.dp -> 8.dp
        screenWidthDp < 600.dp -> 10.dp
        else -> 12.dp
    }
    
    val inputFontSize = when {
        screenWidthDp < 380.dp -> 12.sp
        screenWidthDp < 600.dp -> 12.sp
        else -> 13.sp
    }
    
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
            .padding(inputPadding)
    ) {
        // Reply Preview
        if (replyTo != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF1A1A2E).copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(CyberpunkTheme.CyberCyan)
                    )
                    
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = replyTo.senderName,
                            fontSize = 11.sp,
                            color = CyberpunkTheme.CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = replyTo.content,
                            fontSize = 12.sp,
                            color = CyberpunkTheme.LightGray,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(
                        onClick = onCancelReply,
                        modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = CyberpunkTheme.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F1E))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Default.AttachFile, null, tint = Color.Gray)
            }
            
            if (onGifClick != null) {
                IconButton(onClick = onGifClick) {
                    Text("GIF", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                }
            }
            
            IconButton(onClick = onEmojiClick) {
                Icon(Icons.Default.EmojiEmotions, null, tint = Color.Gray)
            }
            
            TextField(
                value = messageText,
                onValueChange = { newValue ->
                    val filtered = newValue.replace("\n", "").replace("\r", "")
                    onMessageChange(filtered)
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState -> onFocusChange(focusState.isFocused) },
                placeholder = { Text("Type a message...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A2E),
                    unfocusedContainerColor = Color(0xFF1A1A2E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyberpunkTheme.CyberCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() })
            )
            
            IconButton(
                onClick = onSendClick,
                enabled = messageText.isNotBlank(),
            ) {
                    Icon(
                        Icons.Default.Send,
                        null,
                        tint = if (messageText.isNotBlank()) CyberpunkTheme.CyberCyan else Color.Gray
                    )
            }
        }
    }
}

@Composable
fun EmptyChatMessage(mascotVisible: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (mascotVisible) {
            Image(
                painter = painterResource(id = R.drawable.open_arms),
                contentDescription = "Start chat",
                modifier = Modifier
                    .size(140.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Start the conversation!",
            color = CyberpunkTheme.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Say hello to get things rolling",
            color = CyberpunkTheme.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// Helper function to format message timestamps
fun formatMessageTime(timestamp: Long): String {
    return try {
        val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        dateFormat.format(java.util.Date(timestamp))
    } catch (e: Exception) {
        "Now"
    }
}
// Format call duration
fun formatCallDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

// Helper function to extract filename from URI
fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    
    // Try to get filename from MediaStore/ContentResolver
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                // Try OpenableColumns.DISPLAY_NAME first
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("FREETIME_MEDIA", "Error getting filename from cursor: ${e.message}")
    }

    // Fallback to URI path
    if (fileName == null) {
        fileName = uri.path?.substringAfterLast('/')
    }

    // Ensure extension is present if we can determine it from MIME type
    if (fileName != null) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (extension != null && !fileName!!.endsWith(".$extension", ignoreCase = true)) {
                    // Check if it already has SOME extension, if not, add the one from MIME type
                    if (!fileName!!.contains(".")) {
                        fileName = "$fileName.$extension"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FREETIME_MEDIA", "Error ensuring extension: ${e.message}")
        }
    }
    
    return fileName
}

// In-chat call overlay - WhatsApp style voice call UI
@Composable
fun ChatCallOverlay(
    recipientName: String,
    callState: CallState,
    callDuration: Int,
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    errorMessage: String = "",
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onHangup: () -> Unit,
    onAccept: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1A2E))
            .border(
                width = 1.dp,
                color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Avatar / Status
        val safeName = recipientName.trim().ifEmpty { "Friend" }
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    color = CyberpunkTheme.PrimaryPurple.copy(alpha = 0.2f)
                )
                .border(
                    width = 2.dp,
                    color = CyberpunkTheme.CyberCyan,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                safeName.first().uppercaseChar().toString(),
                color = CyberpunkTheme.CyberCyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }
        // Recipient Name
        Text(
            safeName,
            color = CyberpunkTheme.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Call Status / Duration
        when (callState) {
            CallState.INITIATING -> {
                Text(
                    "Calling...",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 14.sp
                )
            }
            CallState.RINGING -> {
                Text(
                    if (onAccept != null) "Incoming Call..." else "Ringing...",
                    color = CyberpunkTheme.CyberCyan,
                    fontSize = 14.sp
                )
            }
            CallState.CONNECTING -> {
                Text(
                    "Connecting...",
                    color = CyberpunkTheme.CyberCyan,
                    fontSize = 14.sp
                )
            }
            CallState.ACTIVE -> {
                Text(
                    formatCallDuration(callDuration),
                    color = CyberpunkTheme.CyberCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            CallState.FAILED -> {
                Text(
                    "Call Failed",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp
                )
            }
            CallState.ENDED -> {
                Text(
                    "Call Ended",
                    color = CyberpunkTheme.LightGray,
                    fontSize = 14.sp
                )
            }
        }
        
        // ✅ ADD: Error message display
        if (errorMessage.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1A1A)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3A2A2A)
                ),
                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        
        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // During RINGING state with accept available (incoming call) show Accept/Decline
            if (callState == CallState.RINGING && onAccept != null) {
                // Decline button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onReject ?: onHangup,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            "Decline",
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Decline", color = CyberpunkTheme.LightGray, fontSize = 11.sp)
                }
                // Accept button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.Call,
                            "Accept",
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Accept", color = CyberpunkTheme.LightGray, fontSize = 11.sp)
                }
            } else {
                // Active / outgoing ringing / initiating / ended / failed states
                if (callState == CallState.ACTIVE) {
                    // Mute button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                            ),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                null,
                                tint = CyberpunkTheme.CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (isMuted) "Unmute" else "Mute", color = CyberpunkTheme.LightGray, fontSize = 10.sp)
                    }
                    
                    // Speaker button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeakerOn) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f)
                            ),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                null,
                                tint = CyberpunkTheme.CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Speaker", color = CyberpunkTheme.LightGray, fontSize = 10.sp)
                    }
                }

                // Hangup button - Always visible for non-incoming states
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onHangup,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            null,
                            tint = CyberpunkTheme.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("End", color = CyberpunkTheme.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}
