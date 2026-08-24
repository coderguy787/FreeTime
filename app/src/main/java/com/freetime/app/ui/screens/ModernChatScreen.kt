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
import com.freetime.app.ui.animations.scaleOnPressEffect
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.SendMessageRequest
import com.freetime.app.data.local.SharedPreferencesHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
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
import com.freetime.app.services.ServerStatusManager
import com.freetime.app.services.OfflineMessageQueue
import com.freetime.app.ui.components.*
import java.util.UUID
import org.json.JSONObject
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

fun getUsernameColor(
    tags: List<String> = emptyList(),
    isAdmin: Boolean = false,
    isModerator: Boolean = false,
    role: String? = null
): Color {
    if (tags.isEmpty() && !isAdmin && !isModerator && role.isNullOrBlank()) {
        return CyberpunkTheme.White
    }

    return when {
        tags.contains("OWNER") -> Color(0xFFFF00FF)
        tags.contains("VIP") -> Color(0xFFFFFF00)
        tags.contains("BETA TESTER") -> Color(0xFF00FFFF)
        isAdmin || role == "admin" || role == "ADMIN" -> Color(0xFFFF0000)
        isModerator || role == "moderator" || role == "MODERATOR" -> Color(0xFFFF8C00)
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
    val status: String = "sent",
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val senderId: String = "",
    val mediaId: String? = null,
    val mediaType: String? = null,
    val mediaName: String? = null,
    val pendingRequests: List<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData> = emptyList(),
    val senderTags: List<String> = emptyList(),
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false,
    val senderRole: String = "",
    val subject: String? = null,
    val isAnnouncement: Boolean = false,
    val mediaShareMode: String? = null,
    val senderAvatar: String? = null
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

private fun resolvePrivateAvatarUrl(url: String?): String? {
    if (url.isNullOrEmpty() || url == "null" || url == "undefined") return null
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "${com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')}$url"
        else -> "${com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')}/$url"
    }
}

private val MEDIA_ID_REGEX = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
private val URL_REGEX = """(https?://[^\s]+|www\.[^\s]+)""".toRegex()

fun buildClickableText(text: String): AnnotatedString {
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        URL_REGEX.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))

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
        append(text.substring(lastIndex))
    }
    return annotatedString
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernChatScreen(
    recipientId: String,
    chatName: String = "",
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

    var isServerDown by remember { mutableStateOf(ServerStatusManager.isDown()) }
    LaunchedEffect(Unit) {
        ServerStatusManager.isServerDown.collect { down ->
            isServerDown = down
        }
    }

    var isSendingMessage by remember { mutableStateOf(false) }
    var recipientName by remember { mutableStateOf(chatName) }
    var recipientIsOnline by remember { mutableStateOf(false) }
    var recipientLastSeen by remember { mutableStateOf<String?>(null) }
    var recipientIsMuted by remember { mutableStateOf(false) }
    var currentChatBgPath by remember { mutableStateOf<String?>(null) }
    var recipientExists by remember { mutableStateOf(true) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var deleteHistoryStatus by remember { mutableStateOf("") }
    var mediaDownloadRequests by remember { mutableStateOf(mapOf<String, String>()) }
    var showMediaRequestDialog by remember { mutableStateOf<String?>(null) }
    var visibleImageMediaIds by remember { mutableStateOf(setOf<String>()) }
    var selectedMessages by remember { mutableStateOf(setOf<String>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    var showMessageContextMenu by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageText by remember { mutableStateOf("") }
    var selectedMessageIsOwn by remember { mutableStateOf(false) }

    var showForwardDialog by remember { mutableStateOf(false) }
    var friendsList by remember { mutableStateOf(listOf<com.freetime.app.api.UserData>()) }
    var isForwarding by remember { mutableStateOf(false) }

    var isFriend by remember { mutableStateOf(false) }
    var recipientTags by remember { mutableStateOf(listOf<String>()) }
    var recipientIsAdmin by remember { mutableStateOf(false) }
    var recipientIsModerator by remember { mutableStateOf(false) }
    var recipientRole by remember { mutableStateOf("") }
    var recipientAvatar by remember { mutableStateOf<String?>(null) }
    val isAnnouncementChat = recipientId == com.freetime.app.api.ANNOUNCEMENT_USER_ID
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
    var lastSendTimeMs by remember { mutableStateOf(0L) }
    val SEND_DEBOUNCE_MS = 500L

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Message>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf("") }

    var isRecipientTyping by remember { mutableStateOf(false) }
    var typingTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastTypingIndicatorSentMs by remember { mutableStateOf(0L) }
    val TYPING_INDICATOR_DEBOUNCE_MS = 1000L

    var wsConnected by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler {
        if (isInputFocused) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        wsManager.connectionState.collect { state ->
            wsConnected = state == com.freetime.app.services.ConnectionState.CONNECTED
        }
    }

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

    LaunchedEffect(recipientName) {
        if (recipientName.isNotEmpty() && recipientName != "User" && messages.isNotEmpty()) {
            messages = messages.map { message ->
                if (!message.isSender && (message.senderName == "Friend" || message.senderName.isEmpty())) {
                    message.copy(senderName = recipientName)
                } else {
                    message
                }
            }
            android.util.Log.d("FREETIME_CHAT", " Updated message sender names with recipient: $recipientName")
        }
    }

    // view-once images disappear after being opened
    LaunchedEffect(visibleImageMediaIds) {
        if (visibleImageMediaIds.isNotEmpty()) {
            val recentlyAdded = visibleImageMediaIds.last()
            delay(3000)
            visibleImageMediaIds = visibleImageMediaIds - recentlyAdded
            android.util.Log.d("FREETIME_MEDIA", "View-once expired for: $recentlyAdded")
        }
    }

    fun sendMediaDownloadRequests(mediaIds: List<String>) {
        scope.launch {
            try {
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

    fun downloadAndSaveMediaFile(data: com.freetime.app.services.WebSocketManager.MediaDownloadResponseData, originalFileName: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("FREETIME_MEDIA", " Starting auto-download of approved media: ${data.mediaId}")

                val downloadUrl = if (data.downloadUrl?.startsWith("http") == true) {
                    data.downloadUrl
                } else if (!data.downloadUrl.isNullOrEmpty()) {
                    val baseUrl = apiService.getBaseUrl()
                    "${baseUrl.trimEnd('/')}${data.downloadUrl}"
                } else {
                    android.util.Log.e("FREETIME_MEDIA", "No download URL provided in approval")
                    return@launch
                }

                val token = prefs.getToken() ?: return@launch
                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

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

                var finalFileName = originalFileName ?: data.fileName ?: "media_${System.currentTimeMillis()}"
                val baseName = finalFileName.substringBeforeLast(".")
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: finalFileName.substringAfterLast(".", "").takeIf { it.length <= 4 && it.all { c -> c.isLetterOrDigit() } }
                finalFileName = if (!ext.isNullOrEmpty()) "$baseName.$ext" else finalFileName

                android.util.Log.d("FREETIME_MEDIA", " Saving media to public storage: $finalFileName")
                val saved = if (android.os.Build.VERSION.SDK_INT >= 29) {
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
                    android.util.Log.d("FREETIME_MEDIA", " Media saved to public storage: $finalFileName")
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
                    android.util.Log.e("FREETIME_MEDIA", " Failed to save media to public storage, falling back to private")
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Media saved to app storage", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_MEDIA", "Error downloading media: ${e.message}", e)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download media: ${e.message ?: "Unknown error. Please try again."}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaPickerError by remember { mutableStateOf("") }
    var isProcessingMedia by remember { mutableStateOf(false) }

    
    val chatBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bgDir = java.io.File(context.getExternalFilesDir(null), "chat_backgrounds")
                if (!bgDir.exists()) bgDir.mkdirs()
                val destFile = java.io.File(bgDir, "bg_${recipientId}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                prefs.setChatBackgroundForUser(recipientId, destFile.absolutePath)
                currentChatBgPath = destFile.absolutePath
                Toast.makeText(context, "Chat background set!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to set background: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showMediaModeDialog by remember { mutableStateOf(false) }
    var pendingMediaShareMode by remember { mutableStateOf("protected") }

    LaunchedEffect(recipientId) {
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        if (!wsManager.isConnected()) {
            android.util.Log.d("FREETIME_CHAT", " WebSocket not connected, initiating connection...")
            scope.launch {
                try {
                    apiService.connectWebSocket()
                    android.util.Log.d("FREETIME_CHAT", " WebSocket connected in chat screen")
                } catch (e: Exception) {
                    android.util.Log.w("FREETIME_CHAT", " WebSocket connection attempted but failed: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            showSearchResults = false
            searchResults = emptyList()
            lastSearchQuery = ""
            return@LaunchedEffect
        }

        delay(500)

        if (searchQuery == lastSearchQuery) {
            return@LaunchedEffect
        }

        isSearching = true
        lastSearchQuery = searchQuery

        try {
            val result = apiService.searchMessages(recipientId, searchQuery, limit = 50)
            result.onSuccess { results ->
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
                        senderTags = emptyList(),
                        senderIsAdmin = false,
                        senderIsModerator = false
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

    

    val webSocketListener = remember {
        object : com.freetime.app.services.WebSocketManager.WebSocketListener {
            override fun onNewMessage(message: com.freetime.app.services.WebSocketManager.MessageData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onNewMessage called: from=${message.senderId}, to=${message.recipientId}, content='${message.content}'")
                scope.launch(Dispatchers.Main) {
                    if (message.senderId == recipientId && message.recipientId == currentUserId) {
                        val dedupMediaId = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                            .find(message.content)?.groupValues?.get(1)
                        val isDuplicateById = messages.any { it.id == message.messageId }
                        val isDuplicateByMedia = dedupMediaId != null && messages.any { it.mediaId == dedupMediaId }
                        val isDuplicateByContent = !isDuplicateById && !isDuplicateByMedia &&
                            messages.any { it.content == message.content && it.isSender == (message.senderId == currentUserId) }
                        if (isDuplicateById || isDuplicateByMedia || isDuplicateByContent) {
                            android.util.Log.d("FREETIME_CHAT", " Duplicate message received via WebSocket - ignoring: ${message.messageId} (id=$isDuplicateById, media=$isDuplicateByMedia, content=$isDuplicateByContent)")
                        } else {
                            android.util.Log.d("FREETIME_CHAT", " Message matches current chat - adding to UI: ${message.content}")

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

                            val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|([^\]]*))?\]""".toRegex()
                            val mediaMatch = mediaIdRegex.find(message.content)
                            val mediaId = mediaMatch?.groupValues?.get(1)
                            val mediaKey = mediaMatch?.groupValues?.get(2)

                            if (mediaId != null && message.senderId != currentUserId) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        android.util.Log.d("FREETIME_MEDIA", " Auto-downloading encrypted media: $mediaId (key present: ${!mediaKey.isNullOrEmpty()})")
                                        val token = SharedPreferencesHelper(context).getToken() ?: ""

                                        val mediaData = apiService.downloadMedia(mediaId, "Bearer $token")
                                        if (mediaData != null && !mediaKey.isNullOrEmpty()) {
                                            val fileName = message.content.substringAfter("] ").takeIf { it.isNotEmpty() } ?: "downloaded_media"
                                            val mimeType = context.contentResolver.getType(Uri.parse(mediaId)) ?: "application/octet-stream"

                                            android.util.Log.d("FREETIME_MEDIA", " Media $mediaId downloaded and cached")

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
                    status = "delivered",
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
                    isAnnouncement = message.senderId == com.freetime.app.api.ANNOUNCEMENT_USER_ID,
                    senderAvatar = message.senderAvatar
                )) + messages

                            if (messages.size > 500) {
                                messages = messages.take(500)
                            }
    }
}
}
            }

            override fun onGroupMessage(message: com.freetime.app.services.WebSocketManager.GroupMessageData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onGroupMessage called (not used yet)")
            }

            override fun onChannelMessage(message: com.freetime.app.services.WebSocketManager.ChannelMessageData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onChannelMessage called (not used yet)")
            }

            override fun onMediaDownloadRequested(data: com.freetime.app.services.WebSocketManager.MediaDownloadRequestData) {
                android.util.Log.d("FREETIME_CHAT", " Media download requested: ${data.mediaId} by ${data.requesterName}")
                scope.launch(Dispatchers.Main) {
                    var attached = false
                    messages = messages.map { msg ->
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)

                        if (!data.mediaId.isNullOrEmpty() && (msgMediaId == data.mediaId || msg.mediaId == data.mediaId)) {
                            attached = true
                            if (msg.pendingRequests.none { it.requestId == data.requestId }) {
                                msg.copy(pendingRequests = msg.pendingRequests + data)
                            } else msg
                        } else msg
                    }

                    if (!attached) {
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
                android.util.Log.d("FREETIME_CHAT", " Media download approved: ${data.mediaId}, encrypted=${data.encrypted}, key=${!data.encryptionKey.isNullOrEmpty()}")
                scope.launch(Dispatchers.Main) {
                    mediaDownloadRequests = mediaDownloadRequests + (data.mediaId to "approved")

                    messages = messages.map { msg ->
                        val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                        val msgMediaId = mediaIdRegex.find(msg.content)?.groupValues?.get(1)

                        if (msgMediaId == data.mediaId || msg.mediaId == data.mediaId) {
                            msg.copy(pendingRequests = msg.pendingRequests.filterNot { it.mediaId == data.mediaId })
                        } else msg
                    }
                }

                    if (data.downloadUrl != null && data.downloadUrl.isNotEmpty()) {
                        android.util.Log.d("FREETIME_CHAT", " Starting auto-download for mediaId: ${data.mediaId}")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val origName = messages.find { msg ->
                                    val regex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                                    val mid = regex.find(msg.content)?.groupValues?.get(1)
                                    mid == data.mediaId || msg.mediaId == data.mediaId
                                }?.mediaName
                                downloadAndSaveMediaFile(data, origName)
                                android.util.Log.d("FREETIME_CHAT", " Media file downloaded and saved to gallery")
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Media downloaded to gallery", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CHAT", " Failed to download media: ${e.message}", e)
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Failed to download: ${e.message ?: "Connection error"}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
            override fun onUserTyping(typingData: com.freetime.app.services.WebSocketManager.TypingData) {
                if (typingData.userId == recipientId) {
                    scope.launch(Dispatchers.Main) {
                        android.util.Log.d("FREETIME_CHAT", " WebSocket: ${typingData.username} is typing")
                        isRecipientTyping = true

                        typingTimeoutJob?.cancel()

                        typingTimeoutJob = scope.launch {
                            delay(3000)
                            isRecipientTyping = false
                            android.util.Log.d("FREETIME_CHAT", " Typing indicator timeout")
                        }
                    }
                }
            }
            override fun onMessageRead(readData: com.freetime.app.services.WebSocketManager.ReadReceiptData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onMessageRead called (not used yet)")
            }
            override fun onConversationAllRead(readData: com.freetime.app.services.WebSocketManager.ConversationReadData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onConversationAllRead called (not used yet)")
            }

            
            
            
            
            override fun onNotificationReceived(data: com.freetime.app.services.WebSocketManager.InternalNotificationData) {
                android.util.Log.d("FREETIME_CHAT", " Internal notification received while in chat: ${data.title}")

            }

                        override fun onUserStatusChanged(statusData: com.freetime.app.services.WebSocketManager.UserStatusData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onUserStatusChanged called (not used yet)")
            }
            override fun onReactionReceived(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onReactionReceived: ${reactionData.emoji} on ${reactionData.messageId}")
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            msg.copy(reactions = (msg.reactions + reactionData.emoji).distinct())
                        } else msg
                    }
                }
                scope.launch(Dispatchers.IO) {
                    val existing = database.messageDao().getMessageById(reactionData.messageId)
                    if (existing != null) {
                        val updatedReactions = (parseReactions(existing.reactions) + reactionData.emoji).distinct()
                        database.messageDao().updateMessage(existing.copy(reactions = com.google.gson.Gson().toJson(updatedReactions)))
                    }
                }
            }
            override fun onReactionRemoved(reactionData: com.freetime.app.services.WebSocketManager.ReactionData) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onReactionRemoved: ${reactionData.emoji} from ${reactionData.messageId}")
                scope.launch(Dispatchers.Main) {
                    messages = messages.map { msg ->
                        if (msg.id == reactionData.messageId) {
                            msg.copy(reactions = msg.reactions.filter { it != reactionData.emoji })
                        } else msg
                    }
                }
                scope.launch(Dispatchers.IO) {
                    val existing = database.messageDao().getMessageById(reactionData.messageId)
                    if (existing != null) {
                        val updatedReactions = parseReactions(existing.reactions).filter { it != reactionData.emoji }
                        database.messageDao().updateMessage(existing.copy(reactions = com.google.gson.Gson().toJson(updatedReactions)))
                    }
                }
            }
            override fun onMessageDeleted(messageId: String) {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onMessageDeleted: $messageId")
                scope.launch(Dispatchers.Main) {
                    messages = messages.filterNot { it.id == messageId }
                }
                scope.launch(Dispatchers.IO) {
                    database.messageDao().deleteMessageById(messageId)
                }
            }
            override fun onConnectionEstablished() {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onConnectionEstablished called - REAL-TIME READY")
            }
            override fun onConnectionLost() {
                android.util.Log.d("FREETIME_CHAT", " WebSocket onConnectionLost called - REAL-TIME OFFLINE")
            }
            override fun onError(error: String) {
                android.util.Log.e("FREETIME_CHAT", " WebSocket onError: $error")
            }
            override fun onChatHistoryDeleted(data: com.freetime.app.services.WebSocketManager.ChatHistoryDeletedData) {
                val isCurrentConversation = (data.deletedBy == recipientId) ||
                    (data.recipientId == recipientId && data.deletedBy == currentUserId) ||
                    (data.recipientId == currentUserId && data.deletedBy == recipientId)
                if (isCurrentConversation) {
                    scope.launch(Dispatchers.Main) {
                        messages = emptyList()
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            try {
                                messageRepository.deleteAllMessagesForChat(recipientId)
                                android.util.Log.d("FREETIME_CHAT", " Local DB cleared for chat with $recipientId")
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CHAT", " Failed to clear local DB: ${e.message}")
                            }
                        }
                        android.util.Log.d("FREETIME_CHAT", " Chat history cleared by ${data.deletedBy}")
                        onNavigateToHome()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        android.util.Log.d("FREETIME_CHAT", " Registering WebSocket listener for real-time chat")
        val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
        wsManager.addListener(webSocketListener)
        com.freetime.app.notifications.NotificationHelper.currentActiveChatId = recipientId
        com.freetime.app.notifications.NotificationHelper.cancelMessageNotification(context, recipientId)
        com.freetime.app.notifications.InAppNotificationStore.removeByTypeAndSender("message", recipientId)
        android.util.Log.d("FREETIME_CHAT", " WebSocket listener registered + active chat set: $recipientId")
        onDispose {
            android.util.Log.d("FREETIME_CHAT", " Unregistering WebSocket listener")
            wsManager.removeListener(webSocketListener)
            com.freetime.app.notifications.NotificationHelper.currentActiveChatId = null
            android.util.Log.d("FREETIME_CHAT", " WebSocket listener unregistered + active chat cleared")
        }
    }

    
    // auto-scroll only when already at the bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            try {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                if (firstVisibleIndex < 3) {
                    listState.animateScrollToItem(0)
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Scroll error: ${e.message}")
            }
        }
    }

    LaunchedEffect(isInputFocused) {
        if (isInputFocused && messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(0)
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Keyboard scroll error: ${e.message}")
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            isProcessingMedia = true
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
                    } else if (fileData.size > 500 * 1024 * 1024) {
                        mediaPickerError = "File exceeds 500 MB limit"
                        isProcessingMedia = false
                        Toast.makeText(context, "File too large (max 500 MB)", Toast.LENGTH_SHORT).show()
                        android.util.Log.w("FREETIME_MEDIA", "File rejected: ${fileData.size} bytes > 500MB")
                    } else {
                        try {
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

                                val sendRequest = SendMessageRequest(
                                    recipientId = recipientId,
                                    content = mediaContent
                                )
                                val response = apiClient.sendMessage(sendRequest, "Bearer $token")
                                if (response.isSuccessful) {
                                    android.util.Log.d("FREETIME_MEDIA", "Media uploaded and sent: $serverMediaId ($fileName, mode=$pendingMediaShareMode)")

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
                                        senderTags = emptyList(),
                                        senderIsAdmin = false,
                                        senderIsModerator = false,
                                        mediaShareMode = pendingMediaShareMode
                                    )) + messages
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

    LaunchedEffect(recipientId) {
        try {
            val response = apiService.getPublicUserProfile(recipientId)
            response.onSuccess { user ->
                val displayName = user.displayName?.takeIf { it.isNotEmpty() && it != "null" }
                val username = user.username?.takeIf { it.isNotEmpty() && it != "null" }
                val loadedName = displayName ?: username ?: "User"

                recipientName = loadedName
                recipientTags = user.tags ?: emptyList()
                recipientRole = user.role ?: ""
                recipientIsAdmin = user.role == "ADMIN" || user.role == "admin"
                recipientIsModerator = user.role == "MODERATOR" || user.role == "moderator"
                recipientAvatar = user.avatar
                recipientIsOnline = false
                recipientIsMuted = prefs.isUserMuted(recipientId)
                currentChatBgPath = prefs.getChatBackgroundForUser(recipientId)
                recipientExists = true

                android.util.Log.d("FREETIME_CHAT", " Recipient profile loaded - displayName='${user.displayName}' username='${user.username}' -> Final Name: '$loadedName'")
                android.util.Log.d("FREETIME_CHAT", " Recipient profile loaded - Name: $loadedName, Tags: $recipientTags, Role: ${user.role}, Admin: $recipientIsAdmin, Mod: $recipientIsModerator")
            }.onFailure { error ->
                recipientExists = false
                android.util.Log.e("FREETIME_CHAT", " Failed to load recipient: ${error.message}")
            }
        } catch (e: Exception) {
            recipientExists = false
            android.util.Log.e("FREETIME_CHAT", " Exception loading recipient: ${e.message}", e)
        }
    }

    LaunchedEffect(recipientId) {
        while (true) {
            delay(10000)
            try {
                val statusResult = apiService.getUserStatus(recipientId)
                statusResult.onSuccess { statusMap ->
                    val isOnline = (statusMap["isOnline"] as? Boolean) ?: false
                    val lastSeen = statusMap["lastSeen"] as? String
                    if (recipientIsOnline != isOnline) {
                        recipientIsOnline = isOnline
                        android.util.Log.d("FREETIME_CHAT", "Status updated: $recipientName is now ${if (isOnline) "online" else "offline"}")
                    }
                    if (lastSeen != null) {
                        recipientLastSeen = lastSeen
                    }
                }.onFailure { error ->
                    android.util.Log.e("FREETIME_CHAT", "Failed to poll status: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("FREETIME_CHAT", "Exception polling status: ${e.message}")
            }
        }
    }

    LaunchedEffect(recipientId) {
        try {
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
                    android.util.Log.d("FREETIME_CHAT", " Found ${requests.size} pending media requests from API")
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

    LaunchedEffect(recipientId) {
        isLoadingMessages = true
        try {
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

            val apiMessages = messageRepository.fetchMessagesFromAPI(recipientId)
            apiMessages.forEach { entity ->
                database.messageDao().insertMessage(entity)
            }

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

    var lastPollTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // fallback polling while the websocket is down
    LaunchedEffect(recipientId, wsConnected) {
        while (true) {
            delay(5000)

            if (wsConnected) {
                continue
            }

            try {
                val response = apiClient.getMessages(recipientId, "Bearer $token")
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        val newMessages = body.mapNotNull { msgResponse ->
                            try {
                                val msgId = msgResponse.id?.takeIf { it.isNotEmpty() } ?: msgResponse._id?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()

                                if (msgResponse.senderId != currentUserId) {
                                    android.util.Log.d("FREETIME_CHAT", " Poll: senderDisplayName='${msgResponse.senderDisplayName}' senderName='${msgResponse.senderName}'")
                                }

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
                                    senderTags = if (msgResponse.senderId == currentUserId) emptyList() else (msgResponse.senderTags.takeIf { it.isNotEmpty() } ?: recipientTags),
                                    senderIsAdmin = if (msgResponse.senderId == currentUserId) false else (msgResponse.senderIsAdmin || recipientIsAdmin),
                                    senderIsModerator = if (msgResponse.senderId == currentUserId) false else (msgResponse.senderIsModerator || recipientIsModerator),
                                    subject = msgResponse.subject,
                                    isAnnouncement = msgResponse.isAdminAnnouncement,
                                    senderAvatar = msgResponse.senderAvatar,
                                    reactions = msgResponse.reactions.keys.toList()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("FREETIME_CHAT", "Error parsing poll message: ${e.message}")
                                null
                            }
                        }

                        val existingIds = messages.map { it.id }.toSet()
                        val existingContentPairs = messages.map { it.content to it.senderName }.toSet()
                        val onlyNew = newMessages.filter { msg ->
                            msg.id !in existingIds &&
                            (msg.content to msg.senderName) !in existingContentPairs
                        }
                        if (onlyNew.isNotEmpty()) {
                            android.util.Log.d("FREETIME_CHAT", " Poll added ${onlyNew.size} new messages (dedup'd from ${newMessages.size})")
                            messages = onlyNew + messages
                            lastPollTimestamp = System.currentTimeMillis()
                        }

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

    fun addReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                messages = messages.map {
                    if (it.id == messageId) {
                        it.copy(reactions = (it.reactions + emoji).distinct())
                    } else it
                }

                withContext(Dispatchers.IO) {
                    try {
                        apiService.addReaction(messageId, emoji)
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "REST reaction failed: ${e.message}")
                    }
                }

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

    fun removeReaction(messageId: String, emoji: String) {
        scope.launch {
            try {
                messages = messages.map {
                    if (it.id == messageId) {
                        it.copy(reactions = it.reactions.filter { r -> r != emoji })
                    } else it
                }

                withContext(Dispatchers.IO) {
                    try {
                        apiService.removeReaction(messageId, emoji)
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "REST reaction removal failed: ${e.message}")
                    }
                }

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

    val sendMessage: () -> Unit = send@{
        val now = System.currentTimeMillis()
        if (now - lastSendTimeMs < SEND_DEBOUNCE_MS) {
            android.util.Log.w("FREETIME_CHAT", "Send throttled - last send was ${now - lastSendTimeMs}ms ago")
            return@send
        }
        lastSendTimeMs = now

        if (!isFriend) {
            android.util.Log.w("FREETIME_CHAT", "Cannot send message: not friends with $recipientId")
            return@send
        }

        if (messageText.trim().isNotEmpty() && recipientExists && !isSendingMessage) {
            if (!recipientExists) {
                android.util.Log.e("FREETIME_CHAT", "Cannot send message: Recipient does not exist in database")
            } else {
                isSendingMessage = true
                val messageToSend = messageText.trim()
                val replyTo = replyingToMessage
                messageText = ""
                replyingToMessage = null

                if (messages.size > 500) {
                    messages = messages.take(500)
                    android.util.Log.d("FREETIME_CHAT", " Trimmed messages to 500 to prevent memory bloat")
                }

                scope.launch {
                    try {
                        val localMessageId = messageRepository.sendMessage(
                            chatId = recipientId,
                            senderId = currentUserId,
                            content = messageToSend,
                            replyToMessageId = replyTo?.id,
                            replyToUsername = replyTo?.senderName,
                            replyToText = replyTo?.content
                        )

                        val msgForUi = Message(
                            id = localMessageId,
                            senderName = currentUsername,
                            content = messageToSend,
                            isSender = true,
                            timestamp = formatMessageTime(System.currentTimeMillis()),
                            isRead = true,
                            status = "pending",
                            replyToMessageId = replyTo?.id,
                            replyToUsername = replyTo?.senderName,
                            replyToText = replyTo?.content,
                            senderTags = emptyList(),
                            senderIsAdmin = false,
                            senderIsModerator = false
                        )
                        if (messages.none { it.id == localMessageId }) {
                            messages = listOf(msgForUi) + messages
                        }

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
                                withContext(kotlinx.coroutines.NonCancellable) {
                                    messageRepository.updateSyncState(localMessageId, "synced")
                                    val serverMsgId = response.body()?.id?.takeIf { it.isNotEmpty() }
                                        ?: response.body()?._id?.takeIf { it.isNotEmpty() }
                                    if (serverMsgId != null && serverMsgId != localMessageId) {
                                        messageRepository.updateMessageId(localMessageId, serverMsgId)
                                    }
                                }
                                val serverMsgId = response.body()?.id?.takeIf { it.isNotEmpty() }
                                    ?: response.body()?._id?.takeIf { it.isNotEmpty() }
                                if (serverMsgId != null && serverMsgId != localMessageId) {
                                    messages = messages.map { if (it.id == localMessageId) it.copy(id = serverMsgId, status = "delivered") else it }
                                } else {
                                    messages = messages.map { if (it.id == localMessageId) it.copy(status = "delivered") else it }
                                }
                            } else {
                                withContext(kotlinx.coroutines.NonCancellable) {
                                    messageRepository.updateSyncState(localMessageId, "failed")
                                }
                                messages = messages.map { if (it.id == localMessageId) it.copy(status = "failed") else it }
                                Toast.makeText(context, "Failed to send: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                                messageText = messageToSend
                                replyingToMessage = replyTo
                            }
                        } catch (e: Exception) {
                            withContext(kotlinx.coroutines.NonCancellable) {
                                messageRepository.updateSyncState(localMessageId, "failed")
                            }
                            messages = messages.map { if (it.id == localMessageId) it.copy(status = "failed") else it }
                            scope.launch(Dispatchers.IO) {
                                OfflineMessageQueue.enqueue(
                                    chatId = recipientId,
                                    content = messageToSend,
                                    recipientId = recipientId
                                )
                            }
                            Toast.makeText(context, "Queued — will send when online", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FREETIME_CHAT", "Fatal error sending message: ${e.message}")
                        Toast.makeText(context, "Message failed: ${e.message}", Toast.LENGTH_LONG).show()
                        messageText = messageToSend
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

    val sendTypingIndicator: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastTypingIndicatorSentMs >= TYPING_INDICATOR_DEBOUNCE_MS && recipientExists) {
            lastTypingIndicatorSentMs = now
            scope.launch(Dispatchers.IO) {
                try {
                    apiService.sendTypingIndicator(recipientId)
                    android.util.Log.d("FREETIME_CHAT", " Typing indicator sent to $recipientId")
                } catch (e: Exception) {
                    android.util.Log.v("FREETIME_CHAT", "Typing indicator send failed (non-critical): ${e.message}")
                }
            }
        }
    }

    
    
    
    
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

    val chatBgPath = remember(recipientId) { prefs.getChatBackgroundForUser(recipientId) }

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
        if (chatBgPath != null) {
            val bgFile = java.io.File(chatBgPath)
            if (bgFile.exists()) {
                AsyncImage(
                    model = bgFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
        ) {
            if (isAnnouncementChat) {
                AnnouncementChatHeader(
                    onNavigateBack = onNavigateBack
                )
            } else {
                ChatScreenHeader(
                    chatName = recipientName,
                    isOnline = recipientIsOnline,
                    lastSeen = recipientLastSeen,
                    onNavigateBack = onNavigateBack,
                    onMoreClick = { showMenu = true },
                    onViewProfile = onViewProfile,
                    recipientId = recipientId,
                    isFriend = isFriend,
                    nameColor = getUsernameColor(recipientTags, recipientIsAdmin, recipientIsModerator, recipientRole)
                )
            }

            val isServerDown by ServerStatusManager.isServerDown.collectAsState()
            if (isServerDown) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF3D2B00).copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "Offline — messages queued",
                        color = Color(0xFFFFAA00),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

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
                            Icon(
                                if (recipientIsMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (recipientIsMuted) Color(0xFF00FF88) else Color(0xFFFF6B6B),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                if (recipientIsMuted) "Unmute User" else "Mute User",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        recipientIsMuted = !recipientIsMuted
                        if (recipientIsMuted) prefs.muteUser(recipientId)
                        else prefs.unmuteUser(recipientId)
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
                            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(20.dp))
                            Text("Set Chat Background", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = {
                        showMenu = false
                        chatBgPickerLauncher.launch("image/*")
                    }
                )
                if (currentChatBgPath != null) {
                    HorizontalDivider(color = CyberpunkTheme.CyberCyan.copy(alpha = 0.2f), thickness = 1.dp)
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                                Text("Remove Chat Background", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        },
                        onClick = {
                            showMenu = false
                            try { java.io.File(currentChatBgPath!!).delete() } catch (_: Exception) {}
                            prefs.clearChatBackgroundForUser(recipientId)
                            currentChatBgPath = null
                        }
                    )
                }
                HorizontalDivider(color = CyberpunkTheme.CyberCyan.copy(alpha = 0.2f), thickness = 1.dp)
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
            if (reportDialog) {
                com.freetime.app.ui.composables.ReportUserDialog(
                    userId = recipientId,
                    userName = recipientName,
                    onDismiss = { reportDialog = false }
                )
            }

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
                                modifier = Modifier.size(24.dp).scaleOnPressEffect()
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val messagesToDisplay = if (showSearchResults && searchResults.isNotEmpty()) {
                    searchResults
                } else if (showSearchResults && searchQuery.isNotEmpty()) {
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
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        reverseLayout = true
                    ) {
                        items(
                            items = messagesToDisplay,
                            key = { it.id }
                        ) { message ->
                            val isSelected = selectedMessages.contains(message.id)

                            val isMsgMediaMessage = MEDIA_ID_REGEX.find(message.content) != null

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
                                                selectedMessages = if (isSelected) {
                                                    selectedMessages - message.id
                                                } else {
                                                    selectedMessages + message.id
                                                }
                                                if (selectedMessages.isEmpty()) isMultiSelectMode = false
                                            } else if (isMsgMediaMessage && !message.isSender && message.mediaShareMode == "protected") {
                                                val mediaId = MEDIA_ID_REGEX.find(message.content)?.groupValues?.get(1)
                                                if (mediaId != null) {
                                                    sendMediaDownloadRequests(listOf(mediaId))
                                                }
                                            } else {
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
                                            val mediaIds = listOf(it)
                                            sendMediaDownloadRequests(mediaIds)
                                        },
                                        onApproveRequest = { requestId ->
                                            scope.launch {
                                                apiService.approveMediaDownloadRequest(requestId).onSuccess {
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
                                        },
                                        recipientAvatar = recipientAvatar
                                    )

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

                if (isMultiSelectMode) {
                    val selectedOwnMessages = messages.filter {
                        it.id in selectedMessages && it.isSender
                    }
                    val allSelectedAreOwn = selectedOwnMessages.size == selectedMessages.size

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = CyberpunkTheme.DarkGray,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(onClick = {
                                    isMultiSelectMode = false
                                    selectedMessages = emptySet()
                                }) {
                                    Icon(Icons.Default.Close, "Close", tint = CyberpunkTheme.White, modifier = Modifier.size(22.dp))
                                }
                                Text(
                                    "${selectedMessages.size}",
                                    color = CyberpunkTheme.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            val result = apiService.getFriends()
                                            result.onSuccess { friends ->
                                                friendsList = friends
                                                showForwardDialog = true
                                            }.onFailure {
                                                Toast.makeText(context, "Failed to load friends", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to load friends", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Forward, "Forward", tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(22.dp))
                                }

                                IconButton(onClick = {
                                    val mediaIdRegex = """^\[Media: ([^|\]]+)(?:\|[^\]]*)?\]""".toRegex()
                                    val mediaIds = messages.filter { it.id in selectedMessages }
                                        .mapNotNull { mediaIdRegex.find(it.content)?.groupValues?.get(1) }
                                    if (mediaIds.isNotEmpty()) {
                                        sendMediaDownloadRequests(mediaIds)
                                    } else {
                                        Toast.makeText(context, "No media files selected", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Download", tint = CyberpunkTheme.CyberCyan, modifier = Modifier.size(22.dp))
                                }

                                if (allSelectedAreOwn) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            var deleted = 0
                                            for (msgId in selectedMessages) {
                                                apiService.deleteMessage(msgId).onSuccess {
                                                    messages = messages.filterNot { it.id == msgId }
                                                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                                                        database.messageDao().deleteMessageById(msgId)
                                                    }
                                                    deleted++
                                                }
                                            }
                                            if (deleted > 0) {
                                                Toast.makeText(context, "$deleted message(s) deleted", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                            }
                                            isMultiSelectMode = false
                                            selectedMessages = emptySet()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

            if (isAnnouncementChat) {
                ReadOnlyChatBanner()
            } else if (!isFriend && !isAnnouncementChat) {
                NotFriendsChatBanner()
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
                            if (isServerDown) {
                                Toast.makeText(context, "Media is unavailable while servers are offline", Toast.LENGTH_SHORT).show()
                            } else if (!isProcessingMedia) {
                                showMediaModeDialog = true
                            }
                        },
                    onGifClick = {
                        if (isServerDown) {
                            Toast.makeText(context, "GIFs are unavailable while servers are offline", Toast.LENGTH_SHORT).show()
                        } else if (!isProcessingMedia) {
                            showGifPicker = true
                        }
                    },
                    onEmojiClick = {
                        showEmojiPicker = !showEmojiPicker
                    },
                    onFocusChange = { focused -> isInputFocused = focused },
                    offlineMode = isServerDown
                )
        }
    }

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
                        "", "", "", "", "", "", "", "", "", "",
                        "", "", "", "", "", "", "", "", "", "",
                        "", "", "", "", "", "", "", "", "", ""
                    )

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
                                            contentColor = Color.Unspecified
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

    if (showForwardDialog) {
        val selectedMessagesContent = messages
            .filter { it.id in selectedMessages }
            .sortedByDescending { it.timestamp }
        AlertDialog(
            onDismissRequest = { showForwardDialog = false },
            title = {
                Text(
                    "Forward ${selectedMessagesContent.size} message(s) to:",
                    color = CyberpunkTheme.LightGray,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (friendsList.isEmpty()) {
                        Text(
                            "No friends found. Add friends first.",
                            color = CyberpunkTheme.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        friendsList.forEach { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isForwarding) return@clickable
                                        isForwarding = true
                                        scope.launch {
                                            val token = prefs.getToken() ?: ""
                                            var successCount = 0
                                            selectedMessagesContent.forEach { msg ->
                                                try {
                                                    val fwdContent = " Forwarded from ${msg.senderName}:\n\n${msg.content}"
                                                    val request = SendMessageRequest(
                                                        recipientId = friend.userId,
                                                        content = fwdContent
                                                    )
                                                    val response = apiClient.sendMessage(request, "Bearer $token")
                                                    if (response.isSuccessful) successCount++
                                                } catch (_: Exception) {}
                                            }
                                            isForwarding = false
                                            showForwardDialog = false
                                            isMultiSelectMode = false
                                            selectedMessages = emptySet()
                                            val toastMsg = if (successCount > 0)
                                                "Forwarded $successCount message(s) to ${friend.name.ifEmpty { friend.username }}"
                                            else
                                                "Failed to forward messages"
                                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = apiService.resolveAvatarUrl(friend.avatar),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        friend.name.ifEmpty { friend.username },
                                        color = CyberpunkTheme.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                    if (friend.name.isNotEmpty() && friend.username.isNotEmpty()) {
                                        Text(
                                            "@${friend.username}",
                                            color = CyberpunkTheme.LightGray.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                if (isForwarding) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = CyberpunkTheme.CyberCyan,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showForwardDialog = false }) {
                    Text("Cancel", color = CyberpunkTheme.CyberCyan)
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = CyberpunkTheme.LightGray,
            titleContentColor = CyberpunkTheme.PrimaryPurple
        )
    }

    GifPickerDialog(
        visible = showGifPicker,
        onDismiss = { showGifPicker = false },
        onGifSelected = { gifUrl, _ ->
            showGifPicker = false
            if (ServerStatusManager.isDown()) {
                Toast.makeText(context, "GIFs are unavailable while servers are offline", Toast.LENGTH_SHORT).show()
                return@GifPickerDialog
            }
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
            onDelete = {
                selectedMessageId?.let { msgId ->
                    scope.launch {
                        apiService.deleteMessage(msgId).onSuccess {
                            messages = messages.filterNot { it.id == msgId }
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                database.messageDao().deleteMessageById(msgId)
                            }
                            Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                        }.onFailure { e ->
                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onReact = { emoji -> selectedMessageId?.let { addReaction(it, emoji) } },
            onReply = { selectedMessageId?.let { replyToPrivateMessage(it); showMessageContextMenu = false } },
            onEdit = { },
            onSelect = {
                selectedMessageId?.let { msgId ->
                    isMultiSelectMode = true
                    selectedMessages = setOf(msgId)
                }
            },
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
    val reactions = listOf("", "", "", "", "", "")

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
                    modifier = Modifier.size(40.dp).scaleOnPressEffect()
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
fun NotFriendsChatBanner() {
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
                Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "You must be friends to message this user.",
                color = Color(0xFFFF6B6B).copy(alpha = 0.8f),
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
    lastSeen: String? = null,
    onNavigateBack: () -> Unit,
    onMoreClick: () -> Unit,
    onViewProfile: (userId: String) -> Unit = {},
    recipientId: String = "",
    isFriend: Boolean = false,
    nameColor: Color = Color.White,
) {
    val statusText = if (isOnline) "online" else formatLastSeen(lastSeen)
    android.util.Log.d("CHAT_HEADER", "Rendering header - Name: '$chatName', Color: ${nameColor}, Online: $isOnline")
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
                    val displayNameToShow = chatName.takeIf { it.isNotEmpty() } ?: "User"

                    androidx.compose.runtime.LaunchedEffect(displayNameToShow) {
                        android.util.Log.d("CHAT_HEADER", " Displaying header name: '$displayNameToShow' (chatName='$chatName')")
                    }

                    Text(
                        displayNameToShow,
                        color = nameColor,
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
                            statusText,
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
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(40.dp).scaleOnPressEffect()
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
    onDenyRequest: (String) -> Unit = {},
    recipientAvatar: String? = null
) {
    val mediaMatch = MEDIA_ID_REGEX.find(message.content)
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

    val maxBubbleWidth = when {
        screenWidthDp < 380.dp -> screenWidthDp * 0.75f
        screenWidthDp < 600.dp -> (screenWidthDp * 0.75f).coerceAtMost(280.dp)
        else -> 280.dp
    }

    val bubblePadding = when {
        screenWidthDp < 380.dp -> 8.dp
        screenWidthDp < 600.dp -> 10.dp
        else -> 12.dp
    }

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
                    val avatarUrl = resolvePrivateAvatarUrl(message.senderAvatar ?: recipientAvatar)
                    if (!avatarUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = message.senderName,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = " Video: $fileName",
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
                                    onClick = { },
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
                                Text(" Waiting for approval...", color = Color(0xFFFFD700), fontSize = messageFontSize * 0.8f)
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
                                        Text(" Request to Watch", fontSize = 11.sp)
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
                                    .size(800)
                                    .build(),
                                contentDescription = fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                                onError = { state ->
                                    imageLoadError = "Load failed: ${state.result.throwable?.message ?: "Check your connection"}"
                                }
                            )
                            if (imageLoadError != null) {
                                Text(
                                    " ${imageLoadError}",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = messageFontSize * 0.8f,
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = " Image: $fileName",
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
                                                        .fillMaxWidth()
                                                        .heightIn(max = 300.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF1a1a2e))
                                                ) {
                                                    AsyncImage(
                                                        model = cachedImagePath,
                                                        contentDescription = fileName,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 300.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            }
                                            imageLoadError != null -> {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("", fontSize = 28.sp)
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
                                                        Text(" Request Download", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            " Preview active (auto-hides in 3 seconds)",
                                            color = Color(0xFF51CF66),
                                            fontSize = messageFontSize * 0.8f
                                        )
                                    } else {
                                        Text(
                                            " Preview expired",
                                            color = CyberpunkTheme.GhostGray,
                                            fontSize = messageFontSize * 0.8f
                                        )
                                    }
                                }
                                "pending" -> {
                                    Text(
                                        " Waiting for approval...",
                                        color = Color(0xFFFFD700),
                                        fontSize = messageFontSize * 0.8f
                                    )
                                }
                                "denied" -> {
                                    Text(
                                        " Download denied",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = messageFontSize * 0.8f
                                    )
                                }
                                else -> {
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
                                            Text(" Request Download", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    isMediaMessage -> {
                        val fileIcon = when {
                            message.mediaType == "audio" || fileName?.endsWith(".mp3", ignoreCase = true) == true || fileName?.endsWith(".wav", ignoreCase = true) == true || fileName?.endsWith(".flac", ignoreCase = true) == true || fileName?.endsWith(".ogg", ignoreCase = true) == true -> ""
                            fileName?.endsWith(".pdf", ignoreCase = true) == true -> ""
                            fileName?.endsWith(".zip", ignoreCase = true) == true || fileName?.endsWith(".rar", ignoreCase = true) == true || fileName?.endsWith(".7z", ignoreCase = true) == true -> ""
                            fileName?.endsWith(".apk", ignoreCase = true) == true -> ""
                            else -> ""
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
                                Text(" Waiting for approval...", color = Color(0xFFFFD700), fontSize = messageFontSize * 0.8f)
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
                                        Text(" Request Download", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val uriHandler = LocalUriHandler.current
                        val annotatedString = buildClickableText(message.content)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (message.isAnnouncement && !message.subject.isNullOrBlank()) {
                                Text(
                                    text = " ${message.subject}",
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
                            "",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    "delivered" -> {
                        Text(
                            "",
                            color = Color(0xFF51CF66),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    "failed" -> {
                        Icon(
                            Icons.Default.Error,
                            "Failed",
                            modifier = Modifier.size(10.dp),
                            tint = Color(0xFFFF6B6B)
                        )
                    }
                }
            }

            if (message.isSender && message.isRead) {
                Text(
                    "",
                    color = Color(0xFF2196F3),
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
                    "Tip: Use reactions! ",
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
    onFocusChange: ((Boolean) -> Unit) = {},
    offlineMode: Boolean = false
) {
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            onTyping?.invoke()
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

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
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (!offlineMode) onAttachClick()
                },
                enabled = !offlineMode,
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    null,
                    tint = if (offlineMode) Color.Gray.copy(alpha = 0.4f) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onGifClick != null) {
                IconButton(
                    onClick = {
                        if (!offlineMode) onGifClick()
                    },
                    enabled = !offlineMode,
                    modifier = Modifier.size(32.dp).scaleOnPressEffect()
                ) {
                    Text(
                        "GIF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (offlineMode) Color.Gray.copy(alpha = 0.4f) else Color.Gray
                    )
                }
            }

            IconButton(
                onClick = onEmojiClick,
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                Icon(Icons.Default.EmojiEmotions, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
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
                placeholder = { Text("Message", color = Color.Gray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A2E),
                    unfocusedContainerColor = Color(0xFF1A1A2E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyberpunkTheme.CyberCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                textStyle = TextStyle(fontSize = 13.sp)
            )

            IconButton(
                onClick = onSendClick,
                enabled = messageText.isNotBlank(),
                modifier = Modifier.size(32.dp).scaleOnPressEffect()
            ) {
                Icon(
                    Icons.Default.Send,
                    null,
                    tint = if (messageText.isNotBlank()) CyberpunkTheme.CyberCyan else Color.Gray,
                    modifier = Modifier.size(18.dp)
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

fun formatMessageTime(timestamp: Long): String {
    return try {
        val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        dateFormat.format(java.util.Date(timestamp))
    } catch (e: Exception) {
        "Now"
    }
}

fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null

    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("FREETIME_MEDIA", "Error getting filename from cursor: ${e.message}")
    }

    if (fileName == null) {
        fileName = uri.path?.substringAfterLast('/')
    }

    if (fileName != null) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (extension != null && !fileName!!.endsWith(".$extension", ignoreCase = true)) {
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

