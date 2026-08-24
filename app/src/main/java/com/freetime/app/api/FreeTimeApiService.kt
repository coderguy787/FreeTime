package com.freetime.app.api

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.*
import com.freetime.app.security.MediaEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.ConnectionPool
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import com.freetime.app.BuildConfig
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import java.util.concurrent.TimeUnit

data class SendMessageResponse(
    val _id: String,
    val content: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: String,
    val messageType: String = "text",
    val voiceUrl: String = "",
    val voiceDuration: Long = 0L,
    val mediaType: String? = null,
    val mediaName: String? = null,
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null
)

data class SendMessageRequest(
    val recipientId: String,
    val content: String,
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null
)

data class FriendRequestResponse(
    val success: Boolean,
    val message: String,
    val autoAccepted: Boolean = false,
    val friendshipId: String? = null,
    val requestId: String? = null
)

data class Channel(
    val channelId: String,
    val name: String,
    val description: String = "",
    val creatorId: String,
    val creatorUsername: String,
    val avatar: String? = null,
    val isPrivate: Boolean = false,
    val admins: List<String> = emptyList(),
    val createdAt: String,
    val memberCount: Int = 0,
    val messageCount: Int = 0
)

data class UserData(
    val userId: String,
    val username: String,
    val name: String,
    val email: String,
    val avatar: String? = null,
    val bio: String = "",
    val status: String = "Available",
    val privacyLevel: String = "public",
    val tags: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val friendId: String = "",
    val isOnline: Boolean = false,
    val actualOnlineStatus: String = "offline",
    val lastSeen: String = "",
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
    val role: String? = null
)

data class GroupMember(
    val userId: String,
    val username: String,
    val displayName: String = "",
    val avatar: String? = null,
    val role: String = "USER",
    val tags: List<String> = emptyList(),
    val displayedStatus: String = "offline",
    val isAdmin: Boolean = false,
    val joinedAt: String = "",
    val isSystemAdmin: Boolean = false,
    val isSystemModerator: Boolean = false
)

data class GroupMessage(
    val messageId: String,
    val groupId: String,
    val senderId: String,
    val senderUsername: String,
    val senderAvatar: String? = null,
    val message: String,
    val timestamp: String,
    val mediaId: String? = null,
    val mediaType: String? = null,
    val mediaName: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val pendingRequests: List<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData> = emptyList(),
    val mediaShareMode: String? = "protected",
    val senderDisplayName: String = "",
    val senderTags: List<String> = emptyList(),
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false,
    val senderRole: String? = null
)

data class Group(
    val groupId: String,
    val name: String,
    val description: String = "",
    val creatorId: String,
    val creatorUsername: String,
    val avatar: String? = null,
    val members: List<GroupMember> = emptyList(),
    val admins: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val createdAt: String,
    val memberCount: Int = 0,
    val messageCount: Int = 0,
    val inviteLink: String? = null,
    val inviteCode: String? = null,
    val webInviteLink: String? = null,
    val webInviteCode: String? = null,
    val profilePictureUrl: String? = null,
    val profilePictureUpdatedAt: String? = null,
    val isPrivate: Boolean = false,
    val isActive: Boolean = true,
    val mutedMembers: List<Any>? = null
)

data class ExpiringInviteLink(
    val inviteCode: String,
    val shareLink: String,
    val expiresAt: Long,
)

data class GroupDeletionVote(
    val voteId: String,
    val groupId: String,
    val initiatedByUserId: String,
    val initiatedByUsername: String,
    val votesFor: List<String>,
    val votesAgainst: List<String>,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
    val voteType: String = "deletion",
    val approvingVotes: Int = 0,
    val rejectingVotes: Int = 0,
    val totalMembers: Int = 0,
    val approvalThreshold: Int = 0,
    val approvalPercentage: Float = 0.0f,
    val hasUserVoted: Boolean = false
)

data class BadgeDetail(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String? = null,
    val icon: String = "",
    val color: String = "",
    val category: String = "achievement",
    val earnedAt: String = ""
)

data class OnlineStatus(
    val userId: String,
    val isOnline: Boolean,
    val actualStatus: String = "offline",
    val lastSeen: String? = null,
    val lastSeenTimestamp: Long = 0
)

data class UserProfile(
    val userId: String,
    val username: String,
    val displayName: String,
    val email: String = "",
    val bio: String = "",
    val avatar: String? = null,
    val banner: String? = null,
    val status: String = "Available",
    val pronouns: String = "",
    val tags: List<String> = emptyList(),
    val badges: List<BadgeDetail> = emptyList(),
    val role: String? = null,
    val lastUsernameChangeAt: String? = null,
    val lastDisplayNameChangeAt: String? = null,
    val isCurrentUser: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val sharedChats: Int = 0,
    val totalMessages: Int = 0,
    val commonGroups: Int = 0
)

data class FriendRequest(
    val requestId: String,
    val senderId: String,
    val senderUsername: String,
    val createdAt: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val avatarUrl: String? = null,
    val senderTags: List<String> = emptyList(),
    val senderRole: String? = null,
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false
)

data class MediaDownloadRequestInfo(
    val requestId: String,
    val requesterId: String? = null,
    val mediaId: String? = null,
    val requesterName: String,
    val reason: String,
    val requestedAt: String
)

data class MediaDownloadApproval(
    val downloadLink: String,
    val mediaId: String,
    val fileName: String,
    val mimeType: String,
    val encrypted: Boolean,
    val encryptionKey: String? = null,
    val iv: String? = null
)

data class VerifyTokenResponse(
    val valid: Boolean,
    val userId: String,
    val username: String,
    val message: String
)

const val ANNOUNCEMENT_USER_ID = "announcement_system"
const val ANNOUNCEMENT_USERNAME = "announcement"
const val ANNOUNCEMENT_DISPLAY_NAME = " Announcement"

class FreeTimeApiService(private val context: Context) {
    private val sslContext: SSLContext by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, java.security.SecureRandom())
        sslContext
    }

    // self-signed cert on the server, skip ssl verification
    private val trustManagers: Array<TrustManager> by lazy {
        arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })
    }
    private val trustAllCerts = arrayOf<TrustManager>(trustManagers[0])

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 5, timeUnit = TimeUnit.MINUTES))
        .sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().build()
            chain.proceed(request)
        }
        .build()

    private val prefs = SharedPreferencesHelper(context)

    private fun getAuthToken(): String? = prefs.getToken()

    fun getBaseUrl(): String = BuildConfig.API_BASE_URL.trimEnd('/')

    fun getCurrentUserId(): String = prefs.getUserId() ?: ""

    fun resolveAvatarUrl(avatar: String?): String? {
        if (avatar.isNullOrEmpty()) return null
        if (avatar.startsWith("http")) return avatar

        val cleanAvatar = if (avatar.startsWith("/")) avatar.substring(1) else avatar
        return "${getBaseUrl()}/$cleanAvatar"
    }

    suspend fun verifyToken(token: String): VerifyTokenResponse {
        return withContext(Dispatchers.IO) {
            val url = "${getBaseUrl()}/api/auth/verify-token"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                return@withContext VerifyTokenResponse(valid = false, userId = "", username = "", message = "Network error: ${e.message}")
            }

            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            try {
                val json = JSONObject(responseBody)
                VerifyTokenResponse(
                    valid = json.optBoolean("valid", false),
                    userId = json.optString("userId", ""),
                    username = json.optString("username", ""),
                    message = json.optString("message", "")
                )
            } catch (e: Exception) {
                VerifyTokenResponse(valid = false, userId = "", username = "", message = "Error parsing server response: ${e.message}")
            }
        }
    }

    suspend fun getPendingNotifications(userId: String, since: String = ""): Result<PendingNotificationsResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/notifications/pending?userId=$userId&since=$since"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)

            val messagesList = mutableListOf<PendingMessage>()
            val messagesArray = json.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until messagesArray.length()) {
                val msg = messagesArray.getJSONObject(i)
                messagesList.add(PendingMessage(
                    chatId = msg.optString("chatId", ""),
                    senderId = msg.optString("senderId", ""),
                    senderName = msg.optString("senderName", "Unknown"),
                    senderAvatar = if (msg.isNull("senderAvatar")) null else msg.optString("senderAvatar", null),
                    content = msg.optString("content", ""),
                    timestamp = msg.optLong("timestamp", 0)
                ))
            }


            Result.success(PendingNotificationsResponse(messagesList))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(reportedUserId: String, reason: String, description: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$reportedUserId/report"

            val requestBody = JSONObject().apply {
                put("reportedUserId", reportedUserId)
                put("reason", reason)
                put("description", description)
                put("timestamp", System.currentTimeMillis())
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val reportId = json.optString("reportId", json.optString("id", UUID.randomUUID().toString()))
            Result.success(reportId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(request: SendMessageRequest, token: String): Result<SendMessageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${getBaseUrl()}/api/messages"
                val jsonBody = JSONObject().apply {
                    put("recipientId", request.recipientId)
                    put("content", request.content)
                    if (request.replyToMessageId != null) put("replyToMessageId", request.replyToMessageId)
                    if (request.replyToUsername != null) put("replyToUsername", request.replyToUsername)
                    if (request.replyToText != null) put("replyToText", request.replyToText)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", token)
                    .post(jsonBody)
                    .build()

                val response = client.newCall(req).execute()
                val responseBody = response.body?.string() ?: ""
                response.body?.close()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }

                val json = JSONObject(responseBody)
                val sendMessageResponse = SendMessageResponse(
                    _id = json.optString("_id", json.optString("id", "")),
                    content = json.optString("content", request.content),
                    senderId = json.optString("senderId", prefs.getUserIdFromRememberMe() ?: ""),
                    recipientId = json.optString("recipientId", request.recipientId),
                    timestamp = json.optString("timestamp", json.optLong("timestamp", System.currentTimeMillis()).toString()),
                    messageType = json.optString("messageType", "text"),
                    replyToMessageId = json.optString("replyToMessageId").takeIf { it.isNotEmpty() && it != "null" } ?: request.replyToMessageId,
                    replyToUsername = json.optString("replyToUsername").takeIf { it.isNotEmpty() && it != "null" } ?: request.replyToUsername,
                    replyToText = json.optString("replyToText").takeIf { it.isNotEmpty() && it != "null" } ?: request.replyToText
                )
                Result.success(sendMessageResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadMediaToChat(
        mediaData: ByteArray,
        fileName: String,
        mimeType: String,
        recipientId: String,
        token: String,
        groupId: String? = null,
        mediaShareMode: String? = null
    ): Pair<String, String?>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${getBaseUrl()}/api/media/upload"

            val isEncrypted = recipientId.startsWith("group:") == false

            val mediaRequestBody = mediaData.toRequestBody(mimeType.toMediaType())
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("recipientId", recipientId)
                .addFormDataPart("encrypted", isEncrypted.toString())
                .addFormDataPart("media", fileName, mediaRequestBody)

            if (!groupId.isNullOrEmpty()) {
                requestBodyBuilder.addFormDataPart("groupId", groupId)
            }
            if (!mediaShareMode.isNullOrEmpty()) {
                requestBodyBuilder.addFormDataPart("mediaShareMode", mediaShareMode)
            }

            val requestBody = requestBodyBuilder.build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", "Media upload failed: HTTP ${response.code}")
                return@withContext null
            }

            val json = JSONObject(responseBody)

            if (json.optBoolean("success", false)) {
                val mediaId = json.optString("mediaId", "")
                val encryptionKey = if (isEncrypted) json.optString("encryptionKey", null) else null
                if (mediaId.isNotEmpty()) {
                    android.util.Log.d("API_SERVICE", " Media uploaded successfully: ID=$mediaId, Encrypted=$isEncrypted, Key=${encryptionKey != null}")
                    Pair(mediaId, encryptionKey)
                } else {
                    null
                }
            } else {
                android.util.Log.e("API_SERVICE", "Media upload error: ${json.optString("error", "Unknown")}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Media upload exception: ${e.message}", e)
            null
        }
    }

    suspend fun uploadPublicMediaToChat(
        mediaData: ByteArray,
        fileName: String,
        mimeType: String,
        recipientId: String,
        token: String,
        groupId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${getBaseUrl()}/api/media/upload"

            val mediaRequestBody = mediaData.toRequestBody(mimeType.toMediaType())
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("recipientId", recipientId)
                .addFormDataPart("encrypted", "false")
                .addFormDataPart("mediaShareMode", "public")
                .addFormDataPart("media", fileName, mediaRequestBody)

            if (!groupId.isNullOrEmpty()) {
                requestBodyBuilder.addFormDataPart("groupId", groupId)
            }

            val requestBody = requestBodyBuilder.build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", "Public media upload failed: HTTP ${response.code}")
                return@withContext null
            }

            val json = JSONObject(responseBody)

            if (json.optBoolean("success", false)) {
                val mediaId = json.optString("mediaId", "")
                if (mediaId.isNotEmpty()) {
                    android.util.Log.d("API_SERVICE", " Public media uploaded successfully (unencrypted): $mediaId")
                    mediaId
                } else {
                    null
                }
            } else {
                android.util.Log.e("API_SERVICE", "Public media upload error: ${json.optString("error", "Unknown")}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Public media upload exception: ${e.message}", e)
            null
        }
    }


    suspend fun downloadMedia(mediaId: String, authHeader: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext null))
            val url = "${getBaseUrl()}/api/media/$mediaId/download"
            android.util.Log.d("API_SERVICE", " Downloading media: mediaId=$mediaId, url=$url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()
            val responseBody = bytes?.let { String(it) } ?: ""

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", " Media download failed: HTTP ${response.code}, mediaId=$mediaId, body=$responseBody")
                try {
                    if (prefs.getBoolean("dev_save_api_responses", false)) {
                        prefs.saveString("last_api_friends_response", responseBody)
                        prefs.saveLong("last_api_friends_response_ts", System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    android.util.Log.w("API_SERVICE", "Failed to persist raw response: ${e.message}")
                }
                return@withContext null
            }

            android.util.Log.d("API_SERVICE", " Media downloaded: size=${bytes?.size ?: 0} bytes")
            bytes
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Exception downloading media: ${e.message}", e)
            null
        }
    }

    suspend fun downloadAndSavePublicMedia(
        mediaId: String,
        fileName: String,
        mediaType: String,
        token: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${getBaseUrl()}/api/media/$mediaId/download"
            android.util.Log.d("API_SERVICE", " Downloading PUBLIC media: mediaId=$mediaId, fileName=$fileName, url=$url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                android.util.Log.e("API_SERVICE", " Download failed: HTTP ${response.code}, error=$errorBody")
                response.body?.close()
                return@withContext Result.failure(Exception("Failed to download: HTTP ${response.code} - $errorBody"))
            }

            val isEncryptedHeader = response.header("X-Encrypted") == "true"
            val encryptionKeyHeader = response.header("X-Encryption-Key")
            onProgress?.invoke(0.1f)
            val mediaBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("No data received"))
            response.body?.close()
            onProgress?.invoke(0.5f)

            val finalBytes = if (isEncryptedHeader && !encryptionKeyHeader.isNullOrEmpty()) {
                android.util.Log.d("API_SERVICE", " Decrypting public media using header key: keyLength=${encryptionKeyHeader.length}")
                try {
                    MediaEncryption(context).decryptMedia(mediaBytes, encryptionKeyHeader) ?: mediaBytes
                } catch (e: Exception) {
                    android.util.Log.e("API_SERVICE", " Decryption of public media failed: ${e.message}", e)
                    mediaBytes
                }
            } else {
                mediaBytes
            }

            val extension = fileName.substringAfterLast('.', "")
            val mimeType = when {
                mediaType.lowercase() == "video" -> "video/mp4"
                extension.lowercase() == "png" -> "image/png"
                extension.lowercase() == "gif" -> "image/gif"
                extension.lowercase() == "webp" -> "image/webp"
                extension.lowercase() == "pdf" -> "application/pdf"
                extension.lowercase() == "txt" -> "text/plain"
                extension.lowercase() == "mp3" -> "audio/mpeg"
                extension.lowercase() == "mp4" -> "video/mp4"
                else -> if (mediaType.lowercase() == "image") "image/jpeg" else if (mediaType.lowercase() == "audio") "audio/mpeg" else "application/octet-stream"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val relativePath = when (mediaType.lowercase()) {
                        "video" -> "Movies/FreeTime"
                        "image" -> "Pictures/FreeTime"
                        "audio" -> "Music/FreeTime"
                        else -> "Download/FreeTime"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }

            val mediaUri = when (mediaType.lowercase()) {
                "video" -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                "image" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                "audio" -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                }
            }

            val uri = context.contentResolver.insert(mediaUri, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create media file"))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(finalBytes)
                outputStream.flush()
            } ?: run {
                context.contentResolver.delete(uri, null, null)
                return@withContext Result.failure(Exception("Failed to open output stream"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error downloading public media: ${e.message}", e)
            val message = when {
                e is java.net.SocketTimeoutException -> "Download timed out. Check your connection."
                e is java.io.FileNotFoundException -> "Media file not found on server."
                e.message?.contains("SSL") == true || e.message?.contains("certificate") == true -> "Secure connection failed."
                e.message?.contains("resolve") == true || e.message?.contains("UnknownHost") == true -> "Cannot reach server. Check your internet."
                e.message != null -> "Download failed: ${e.message}"
                else -> "Download failed. Please try again."
            }
            Result.failure(Exception(message, e))
        }
    }

    suspend fun downloadAndDecryptApprovedMedia(approval: MediaDownloadApproval, onProgress: ((Float) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val request = Request.Builder()
                .url(approval.downloadLink)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download media: HTTP ${response.code}"))
            }

            onProgress?.invoke(0.1f)
            val encryptedBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("No data received"))
            response.body?.close()
            onProgress?.invoke(0.5f)

            // media is encrypted, key comes from the approval record
            val finalBytes = if (approval.encrypted && !approval.encryptionKey.isNullOrEmpty()) {
                MediaEncryption(context).decryptMedia(encryptedBytes, approval.encryptionKey)
            } else {
                encryptedBytes
            } ?: return@withContext Result.failure(Exception("Failed to decrypt media"))

            val mediaType = if (approval.mimeType.startsWith("video")) "video" else "image"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, approval.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, approval.mimeType)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        if (mediaType == "video") "Movies/FreeTime" else "Pictures/FreeTime")
                }
            }

            val mediaUri = if (mediaType == "video") {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val uri = context.contentResolver.insert(mediaUri, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create media file in gallery"))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(finalBytes)
                outputStream.flush()
            } ?: run {
                context.contentResolver.delete(uri, null, null)
                return@withContext Result.failure(Exception("Failed to open output stream for gallery"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error in downloadAndDecryptApprovedMedia: ${e.message}", e)
            val message = when {
                e is java.net.SocketTimeoutException -> "Download timed out. Check your connection."
                e.message?.contains("decrypt", ignoreCase = true) == true -> "Failed to decrypt media."
                e.message != null -> "Download failed: ${e.message}"
                else -> "Download failed. Please try again."
            }
            Result.failure(Exception(message, e))
        }
    }

    suspend fun downloadMediaFile(
        mediaId: String,
        fileName: String,
        mediaType: String,
        mediaKey: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/$mediaId/download"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                android.util.Log.e("API_SERVICE", " PROTECTED media download failed: HTTP ${response.code}, error=$errorBody")
                response.body?.close()
                return@withContext Result.failure(Exception("Failed to download: HTTP ${response.code} - $errorBody"))
            }

            onProgress?.invoke(0.1f)
            val encryptedBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("No data received"))
            response.body?.close()
            onProgress?.invoke(0.5f)

            val encryptor = MediaEncryption(context)
            android.util.Log.d("API_SERVICE", " Decrypting PROTECTED media: keyLength=${mediaKey.length}, encryptedSize=${encryptedBytes.size}")
            val decryptedBytes = try {
                encryptor.decryptMedia(encryptedBytes, mediaKey)
            } catch (decryptError: Exception) {
                android.util.Log.e("API_SERVICE", " Decryption failed: ${decryptError.message}", decryptError)
                return@withContext Result.failure(Exception("Failed to decrypt media: ${decryptError.message}"))
            }

            android.util.Log.d("API_SERVICE", " Media decrypted successfully: decryptedSize=${decryptedBytes.size}")

            val mimeTypeMap = android.webkit.MimeTypeMap.getSingleton()
            var extension = fileName.substringAfterLast('.', "")
            var finalFileName = fileName

            val mimeType = if (extension.isNotEmpty()) {
                mimeTypeMap.getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
            } else {
                val detectedMime = when (mediaType.lowercase()) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    else -> "application/octet-stream"
                }
                val ext = mimeTypeMap.getExtensionFromMimeType(detectedMime)
                if (ext != null) {
                    finalFileName = "$fileName.$ext"
                    extension = ext
                }
                detectedMime
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        when (mediaType.lowercase()) {
                            "video" -> "Movies/FreeTime"
                            "image" -> "Pictures/FreeTime"
                            else -> "Downloads/FreeTime"
                        }
                    )
                }
            }

            val mediaUri = when (mediaType.lowercase()) {
                "video" -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                "image" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                }
            }

            val uri = context.contentResolver.insert(mediaUri, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create media file entry"))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(decryptedBytes)
                outputStream.flush()
            } ?: run {
                android.util.Log.e("API_SERVICE", "Failed to open output stream for URI: $uri")
                context.contentResolver.delete(uri, null, null)
                return@withContext Result.failure(Exception("Failed to open output stream"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error saving protected media: ${e.message}", e)
            val message = when {
                e is java.net.SocketTimeoutException -> "Download timed out. Check your connection."
                e.message?.contains("decrypt", ignoreCase = true) == true -> "Failed to decrypt media."
                e.message != null -> "Download failed: ${e.message}"
                else -> "Download failed. Please try again."
            }
            Result.failure(Exception(message, e))
        }
    }

    suspend fun requestMediaDownload(mediaId: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/$mediaId/download-request"
            val requestBody = JSONObject().apply {
                put("reason", "User requires access to protected media")
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to request download: HTTP ${response.code}"))
            }
            val json = JSONObject(responseBody)
            val requestId = json.optString("requestId", json.optString("id", ""))
            val status = json.optString("status", "pending")
            Result.success(requestId to status)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error requesting media download: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun approveMediaDownloadRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/download-request/$requestId/approve"
            val requestBody = JSONObject().apply {
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to approve request: HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error approving media download request: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun denyMediaDownloadRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/download-request/$requestId/deny"
            val requestBody = JSONObject().apply {
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to deny request: HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error denying media download request: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerDeviceFcmToken(fcmToken: String): Result<FcmTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/fcm-token"

            val requestBody = JSONObject().apply {
                put("fcmToken", fcmToken)
                put("deviceId", prefs.getDeviceId() ?: "")
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            Result.success(FcmTokenResponse(
                success = json.optBoolean("success", true),
                message = json.optString("message", "")
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerFcmToken(userId: String, fcmToken: String): Result<FcmTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/fcm-token"

            val requestBody = JSONObject().apply {
                put("fcmToken", fcmToken)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            Result.success(FcmTokenResponse(
                success = json.optBoolean("success", true),
                message = json.optString("message", "")
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }









    suspend fun createGroup(name: String, description: String, memberIds: List<String>, isPrivate: Boolean = false): Result<Group> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups"

            val requestBody = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("isPrivate", isPrivate)
                val members = JSONArray()
                memberIds.forEach { members.put(it) }
                put("members", members)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            try {
                val json = JSONObject(responseBody)
                val groupJson = if (json.has("group")) json.getJSONObject("group") else null

                if (groupJson != null && groupJson.length() > 0) {
                    val groupId = groupJson.optString("groupId", groupJson.optString("_id", groupJson.optString("id", "")))
                    if (groupId.isNotEmpty()) {
                        android.util.Log.w("API_SERVICE", " Group created but HTTP ${response.code}: ${response.message}. Proceeding with group.")
                        return@withContext Result.success(parseGroupInternal(groupJson))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("API_SERVICE", "Could not parse response JSON: ${e.message}")
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val groupJson = if (json.has("group")) json.getJSONObject("group") else json
            Result.success(parseGroupInternal(groupJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // members can arrive as objects or plain ids, handle both
    private fun parseGroupInternal(groupJson: JSONObject): Group {
        val membersArray = groupJson.optJSONArray("members") ?: JSONArray()
        val members = mutableListOf<GroupMember>()
        for (i in 0 until membersArray.length()) {
            val memberJson = membersArray.optJSONObject(i)
            if (memberJson != null) {
                val memberRole = memberJson.optString("role", "USER")
                members.add(GroupMember(
                    userId = memberJson.optString("userId", memberJson.optString("id", "")),
                    username = memberJson.optString("username", ""),
                    displayName = memberJson.optString("displayName", memberJson.optString("username", "")),
                    avatar = memberJson.optString("avatar", null),
                    role = memberRole,
                    tags = emptyList(),
                    displayedStatus = "offline",
                    isAdmin = memberJson.optBoolean("isAdmin", false) || memberRole.equals("admin", ignoreCase = true),
                    joinedAt = memberJson.optString("joinedAt", ""),
                    isSystemAdmin = memberJson.optBoolean("isSystemAdmin", false),
                    isSystemModerator = memberJson.optBoolean("isSystemModerator", false)
                ))
            } else {
                val userId = membersArray.optString(i, "")
                if (userId.isNotEmpty()) {
                    members.add(GroupMember(
                        userId = userId,
                        username = "Member",
                        displayName = "Member",
                        avatar = null,
                        role = "USER",
                        tags = emptyList(),
                        displayedStatus = "offline",
                        isAdmin = false,
                        joinedAt = ""
                    ))
                }
            }
        }

        return Group(
            groupId = groupJson.optString("groupId", groupJson.optString("_id", groupJson.optString("id", ""))),
            name = groupJson.optString("name", ""),
            description = groupJson.optString("description", ""),
            creatorId = groupJson.optString("creatorId", ""),
            creatorUsername = groupJson.optString("creatorUsername", ""),
            avatar = groupJson.optString("avatar", null),
            members = members,
            admins = run {
                val adminsArray = groupJson.optJSONArray("admins") ?: JSONArray()
                (0 until adminsArray.length()).map { adminsArray.optString(it, "") }
            },
            adminIds = run {
                val adminIdsArray = groupJson.optJSONArray("adminIds") ?: JSONArray()
                (0 until adminIdsArray.length()).map { adminIdsArray.optString(it, "") }
            },
            createdAt = groupJson.optString("createdAt", ""),
            memberCount = groupJson.optInt("memberCount", members.size),
            messageCount = 0,
            isPrivate = groupJson.optBoolean("isPrivate", false)
        )
    }

    fun connectWebSocket() {
        val token = prefs.getToken() ?: return
        val serverUrl = getBaseUrl()
        val userId = getCurrentUserId()
        com.freetime.app.services.WebSocketManager.getInstance().connect(serverUrl, token, userId)
    }

    suspend fun searchMessages(recipientId: String, query: String, limit: Int = 50): Result<List<SendMessageResponse>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/messages/search?recipientId=$recipientId&query=$query&limit=$limit"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonArray = JSONArray(responseBody)
            val results = mutableListOf<SendMessageResponse>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                results.add(SendMessageResponse(
                    _id = obj.optString("_id", ""),
                    content = obj.optString("content", ""),
                    senderId = obj.optString("senderId", ""),
                    recipientId = obj.optString("recipientId", ""),
                    timestamp = obj.optString("timestamp", ""),
                    messageType = obj.optString("messageType", "text"),
                    voiceUrl = obj.optString("voiceUrl", ""),
                    voiceDuration = obj.optLong("voiceDuration", 0L),
                    mediaType = obj.optString("mediaType", null),
                    mediaName = obj.optString("mediaName", null)
                ))
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun getUserStatus(userId: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/status"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val result = mutableMapOf<String, Any?>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                result[key] = json.get(key)
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingMediaDownloadRequests(): Result<List<MediaDownloadRequestInfo>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/download-requests/pending"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonArray = when {
                responseBody.trim().startsWith("{") -> JSONObject(responseBody).optJSONArray("requests") ?: JSONArray()
                responseBody.trim().startsWith("[") -> JSONArray(responseBody)
                else -> JSONArray()
            }

            val requests = mutableListOf<MediaDownloadRequestInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                requests.add(MediaDownloadRequestInfo(
                    requestId = obj.optString("_id", obj.optString("requestId", "")),
                    requesterId = obj.optString("requesterId", obj.optString("requester", null)),
                    mediaId = obj.optString("mediaId", null),
                    requesterName = obj.optString("requesterName", "Unknown"),
                    reason = obj.optString("reason", ""),
                    requestedAt = obj.optString("requestedAt", "")
                ))
            }
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTypingIndicator(recipientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/chat/$recipientId/typing"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChatHistoryWithUser(recipientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/chat/$recipientId/delete-history"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileBanner(imageBytes: ByteArray, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile-banner"
            val encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val requestBody = JSONObject().apply {
                put("image", encodedImage)
                put("mimeType", mimeType)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val bannerUrl = json.optString("bannerUrl", "")
                .ifEmpty { json.optJSONObject("profileBanner")?.optString("bannerUrl", "") ?: "" }
            Result.success(bannerUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBanner(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile-banner"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfileImage(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile-image"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAvailabilityStatus(status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile"

            val requestBody = JSONObject().apply {
                put("status", status)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setStatusMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/status-message"

            val requestBody = JSONObject().apply {
                put("message", message)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setLanguagePreference(language: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/preferences/language"

            val requestBody = JSONObject().apply {
                put("language", language)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setThemePreference(theme: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/preferences/theme"

            val requestBody = JSONObject().apply {
                put("theme", theme)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/displayName"

            val requestBody = JSONObject().apply {
                put("displayName", displayName)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else {
                val serverMsg = try { JSONObject(responseBody).optString("error", "") } catch (e: Exception) { "" }
                val detail = if (serverMsg.isNotEmpty()) "HTTP ${response.code}: $serverMsg" else "HTTP ${response.code}"
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBio(bio: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/bio"

            val requestBody = JSONObject().apply {
                put("bio", bio)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else {
                val serverMsg = try { JSONObject(responseBody).optString("error", "") } catch (e: Exception) { "" }
                val detail = if (serverMsg.isNotEmpty()) "HTTP ${response.code}: $serverMsg" else "HTTP ${response.code}"
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrivacySettings(userId: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/privacy-settings"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val settings = if (json.has("privacySettings")) json.getJSONObject("privacySettings") else JSONObject()
            val result = mutableMapOf<String, String>()
            val keysIter: Iterator<*> = settings.keys()
            while (keysIter.hasNext()) {
                val key = keysIter.next() as String
                result[key] = settings.optString(key, "friends")
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(userId: String, settings: Map<String, Any>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/privacy-settings"

            val requestBody = JSONObject(settings).toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String, totpCode: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/change-password"

            val requestBody = JSONObject().apply {
                put("oldPassword", oldPassword)
                put("newPassword", newPassword)
                if (totpCode.isNotEmpty()) put("totpCode", totpCode)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/delete-account"

            val requestBody = JSONObject().apply {
                if (reason.isNotEmpty()) put("reason", reason)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChatHistory(recipientId: String): Result<Unit> = deleteChatHistoryWithUser(recipientId)

    suspend fun sendFriendRequest(username: String, authHeader: String? = null): Result<FriendRequestResponse> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/friends/request/username"
            val requestBody = JSONObject().apply {
                put("username", username)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val result = FriendRequestResponse(
                success = json.optBoolean("success", false),
                message = json.optString("message", ""),
                autoAccepted = json.optBoolean("autoAccepted", false),
                friendshipId = json.optString("friendshipId", null),
                requestId = json.optString("requestId", null)
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelFriendRequest(requestId: String, authHeader: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/friends/requests/$requestId/cancel"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .post("".toRequestBody(null))
                .build()
            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendId: String, authHeader: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rawToken = authHeader ?: getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val authorizationHeader = if (rawToken.startsWith("Bearer ")) rawToken else "Bearer $rawToken"
            val url = "${getBaseUrl()}/api/friends/$friendId"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", authorizationHeader)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = getCurrentUserId()
            if (userId.isEmpty()) {
                return@withContext Result.failure(Exception("User ID not found"))
            }
            val url = "${getBaseUrl()}/api/users/$userId/public-profile"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val profileJson = if (json.has("user")) json.getJSONObject("user") else json

            Result.success(parseUserProfileInternal(profileJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseUserProfileInternal(json: JSONObject): UserProfile {
        return UserProfile(
            userId = json.optString("userId", json.optString("_id", "")),
            username = json.optString("username", ""),
            displayName = json.optString("displayName").takeIf { it.isNotEmpty() } ?: json.optString("username", ""),
            email = json.optString("email", ""),
            bio = json.optString("bio", ""),
            avatar = json.optString("avatar", null),
            banner = json.optString("banner", null) ?: json.optString("bannerUrl", null),
            status = json.optString("status", "Available"),
            pronouns = json.optString("pronouns", ""),
            tags = run {
                val arr = json.optJSONArray("tags") ?: JSONArray()
                (0 until arr.length()).map { arr.getString(it) }
            },
            badges = emptyList(),
            role = json.optString("role", "User"),
            lastUsernameChangeAt = json.optString("lastUsernameChangeAt", null),
            lastDisplayNameChangeAt = json.optString("lastDisplayNameChangeAt", null),
            isCurrentUser = true
        )
    }

    suspend fun uploadProfileImage(imageBytes: ByteArray, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile-image"
            val encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val requestBody = JSONObject().apply {
                put("image", encodedImage)
                put("mimeType", mimeType)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                android.util.Log.w("API_SERVICE", "Profile upload returned HTTP ${response.code}, trying to verify...")
                val profileResult = getCurrentUserProfile()
                profileResult.onSuccess { profile ->
                    if (!profile.avatar.isNullOrEmpty()) {
                        val resolvedUrl = resolveAvatarUrl(profile.avatar)
                        if (resolvedUrl != null) {
                            return@withContext Result.success(resolvedUrl)
                        }
                    }
                }
                return@withContext Result.failure(Exception("Failed to update the photo!"))
            }

            val json = JSONObject(responseBody)
            val imageUrl = json.optString("imageUrl", "")
            if (imageUrl.isNotEmpty()) {
                Result.success(imageUrl)
            } else {
                val profileResult = getCurrentUserProfile()
                profileResult.onSuccess { profile ->
                    if (!profile.avatar.isNullOrEmpty()) {
                        val resolvedUrl = resolveAvatarUrl(profile.avatar)
                        if (resolvedUrl != null) {
                            return@withContext Result.success(resolvedUrl)
                        }
                    }
                }
                Result.failure(Exception("Failed to update the photo!"))
            }
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Profile upload exception: ${e.message}", e)
            val profileResult = getCurrentUserProfile()
            profileResult.onSuccess { profile ->
                if (!profile.avatar.isNullOrEmpty()) {
                    val resolvedUrl = resolveAvatarUrl(profile.avatar)
                    if (resolvedUrl != null) {
                        return@withContext Result.success(resolvedUrl)
                    }
                }
            }
            Result.failure(Exception("Failed to update the photo!"))
        }
    }

    suspend fun uploadProfileImage(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Unable to open image URI"))
            val imageBytes = inputStream.readBytes()
            inputStream.close()
            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            uploadProfileImage(imageBytes, mimeType)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(imageData: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile-image"

            val requestBody = JSONObject().apply {
                put("image", imageData)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            Result.success(json.optString("imageUrl", ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUsername(username: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val prefs = SharedPreferencesHelper(context)
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/username"

            val requestBody = JSONObject().apply {
                put("username", username)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else {
                val serverMsg = try { JSONObject(responseBody).optString("error", "") } catch (e: Exception) { "" }
                val detail = if (serverMsg.isNotEmpty()) "HTTP ${response.code}: $serverMsg" else "HTTP ${response.code}"
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        status: String? = null,
        interests: List<String>? = null,
        pronouns: String? = null,
        profilePicture: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val request = com.freetime.app.data.network.UpdateUserProfileRequest(
            username = username,
            displayName = displayName,
            bio = bio,
            status = status,
            tags = interests,
            profilePicture = profilePicture,
            pronouns = pronouns
        )
        return@withContext updateUserProfile(request)
    }

    suspend fun updateUserProfile(request: com.freetime.app.data.network.UpdateUserProfileRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User ID not found"))
            val url = "${getBaseUrl()}/api/users/$userId/profile"

            val requestBody = JSONObject().apply {
                put("displayName", request.displayName)
                put("bio", request.bio)
                put("status", request.status)
                if (request.username != null) put("username", request.username)
                if (request.tags != null) put("tags", JSONArray(request.tags))
                if (request.profilePicture != null) put("profilePicture", request.profilePicture)
                if (request.pronouns != null) put("pronouns", request.pronouns)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPublicUserProfile(userId: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/public-profile"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val profileJson = when {
                json.has("user") -> json.getJSONObject("user")
                json.has("profile") -> json.getJSONObject("profile")
                else -> json
            }

            Result.success(parseUserProfileInternal(profileJson).copy(isCurrentUser = false))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/logout"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            prefs.clearAllAuthenticationData()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriends(authHeader: String? = null): Result<List<UserData>> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader?.removePrefix("Bearer ") ?: getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/friends"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonArray = try {
                JSONArray(responseBody)
            } catch (e: Exception) {
                try {
                    val root = JSONObject(responseBody)
                    root.optJSONArray("friends")
                        ?: root.optJSONObject("data")?.optJSONArray("friends")
                        ?: root.optJSONArray("users")
                        ?: root.optJSONObject("result")?.optJSONArray("friends")
                        ?: JSONArray()
                } catch (e2: Exception) {
                    JSONArray()
                }
            }
            val friends = mutableListOf<UserData>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val tagsList = mutableListOf<String>()
                obj.optJSONArray("tags")?.let { tagsArray ->
                    for (j in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.optString(j, ""))
                    }
                }

                friends.add(UserData(
                    userId = obj.optString("userId", obj.optString("_id", "")),
                    username = obj.optString("username", ""),
                    name = obj.optString("name", obj.optString("displayName", "")),
                    email = obj.optString("email", ""),
                    avatar = obj.optString("avatar", obj.optString("avatarUrl", obj.optString("profilePictureUrl", ""))),
                    bio = obj.optString("bio", ""),
                    tags = tagsList,
                    isOnline = obj.optBoolean("isOnline", false),
                    lastSeen = obj.optString("lastSeen", ""),
                    isAdmin = obj.optBoolean("isAdmin", false),
                    isModerator = obj.optBoolean("isModerator", false),
                    role = obj.optString("role", null)
                ))
            }
            if (friends.isEmpty()) {
                android.util.Log.w("FREETIME_API", "getFriends(): parsed empty list from $url; raw response: $responseBody")
                try {
                    if (prefs.getBoolean("dev_save_api_responses", false)) {
                        prefs.saveString("last_api_friends_response", responseBody)
                        prefs.saveLong("last_api_friends_response_ts", System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FREETIME_API", "Failed to persist empty friends response: ${e.message}")
                }
            }
            Result.success(friends)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserGroups(): Result<List<Group>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonArray = try {
                JSONArray(responseBody)
            } catch (e: Exception) {
                JSONObject(responseBody).optJSONArray("groups") ?: JSONArray()
            }
            val groups = mutableListOf<Group>()
            for (i in 0 until jsonArray.length()) {
                groups.add(parseGroupInternal(jsonArray.getJSONObject(i)))
            }
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun getPendingFriendRequestsViaREST(): Result<List<FriendRequest>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/friends/requests/pending"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonArray = JSONArray(responseBody)
            val requests = mutableListOf<FriendRequest>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                requests.add(FriendRequest(
                    requestId = obj.optString("requestId", obj.optString("id", obj.optString("_id", ""))),
                    senderId = obj.optString("senderId", obj.optString("userId", "")),
                    senderUsername = obj.optString("senderUsername", obj.optString("senderName", obj.optString("username", obj.optString("displayName", "")))),
                    timestamp = obj.optLong("timestamp", obj.optLong("createdAt", System.currentTimeMillis())),
                    avatarUrl = obj.optString("avatarUrl", obj.optString("avatar", null))
                ))
            }
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testSocketIODiagnostic(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/socket.io/test"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val map = mutableMapOf<String, Any?>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() as String
                map[key] = json.get(key)
            }
            Result.success(map)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserBadges(userId: String): Result<List<BadgeDetail>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/users/$userId/badges"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val jsonArray = JSONArray(responseBody)
            val badges = mutableListOf<BadgeDetail>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                badges.add(BadgeDetail(
                    id = obj.optString("_id", ""),
                    name = obj.optString("name", ""),
                    description = obj.optString("description", ""),
                    iconUrl = obj.optString("iconUrl", null),
                    category = obj.optString("category", "achievement"),
                    earnedAt = obj.optString("earnedAt", "")
                ))
            }
            Result.success(badges)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addGroupReaction(groupId: String, messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/messages/$messageId/reactions"

            val requestBody = JSONObject().apply {
                put("emoji", emoji)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeGroupReaction(groupId: String, messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/messages/$messageId/reactions/$emoji"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun deleteMessage(messageId: String, authHeader: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/messages/$messageId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroupMessage(groupId: String, messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/messages/$messageId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReaction(messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/messages/$messageId/reactions"
            val requestBody = JSONObject().apply {
                put("emoji", emoji)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeReaction(messageId: String, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/messages/$messageId/reactions/$emoji"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String, authHeader: String? = null): Result<List<UserData>> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/users/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val usersArray = json.optJSONArray("users") ?: JSONArray()
            val users = mutableListOf<UserData>()
            for (i in 0 until usersArray.length()) {
                val userJson = usersArray.getJSONObject(i)
                users.add(UserData(
                    userId = userJson.optString("userId", userJson.optString("_id", "")),
                    username = userJson.optString("username", ""),
                    name = userJson.optString("displayName", userJson.optString("username", "")),
                    email = userJson.optString("email", ""),
                    avatar = userJson.optString("avatar", null),
                    status = userJson.optString("status", "Available"),
                    isOnline = userJson.optBoolean("isOnline", false)
                ))
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingRequests(authHeader: String? = null): Result<List<FriendRequest>> = withContext(Dispatchers.IO) {
        try {
            val rawToken = authHeader ?: getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val token = if (rawToken.startsWith("Bearer ")) rawToken else "Bearer $rawToken"
            val url = "${getBaseUrl()}/api/friends/requests/pending"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val requestsArray = if (responseBody.trim().startsWith("[")) {
                JSONArray(responseBody)
            } else {
                val json = JSONObject(responseBody)
                json.optJSONArray("requests") ?: JSONArray()
            }
            val requests = mutableListOf<FriendRequest>()
            for (i in 0 until requestsArray.length()) {
                val requestJson = requestsArray.getJSONObject(i)

                val tagsArray = requestJson.optJSONArray("senderTags")
                val senderTagsList = if (tagsArray != null) {
                    (0 until tagsArray.length()).map { tagsArray.getString(it) }
                } else emptyList()
                val senderRoleVal = requestJson.optString("senderRole", null)
                val senderIsAdminVal = requestJson.optBoolean("senderIsAdmin", false)
                val senderIsModeratorVal = requestJson.optBoolean("senderIsModerator", false)

                requests.add(FriendRequest(
                    requestId = requestJson.optString("requestId", requestJson.optString("id", "")),
                    senderId = requestJson.optString("senderId", requestJson.optString("userId", requestJson.optString("id", ""))),
                    senderUsername = requestJson.optString("senderUsername", requestJson.optString("senderName", requestJson.optString("username", requestJson.optString("displayName", "")))),
                    timestamp = requestJson.optLong("timestamp", requestJson.optLong("createdAt", System.currentTimeMillis())),
                    avatarUrl = requestJson.optString("avatarUrl", requestJson.optString("avatar", null)),
                    senderTags = senderTagsList,
                    senderRole = senderRoleVal,
                    senderIsAdmin = senderIsAdminVal,
                    senderIsModerator = senderIsModeratorVal
                ))
            }
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(senderId: String, authHeader: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/friends/requests/$senderId/accept"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun declineFriendRequest(senderId: String, authHeader: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))))
            val url = "${getBaseUrl()}/api/friends/requests/$senderId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inviteToGroup(groupId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/invite"
            val requestBody = JSONObject().apply {
                put("userId", userId)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inviteToGroup(groupId: String, userIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/invite"
            val requestBody = JSONObject().apply {
                val userIdsArray = JSONArray()
                userIds.forEach { userIdsArray.put(it) }
                put("userIds", userIdsArray)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveGroupVotes(groupId: String): Result<List<GroupDeletionVote>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/votes/active"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val votesArray = json.optJSONArray("votes") ?: JSONArray()
            val votes = mutableListOf<GroupDeletionVote>()
            for (i in 0 until votesArray.length()) {
                val voteJson = votesArray.getJSONObject(i)
                votes.add(GroupDeletionVote(
                    voteId = voteJson.optString("voteId", voteJson.optString("id", "")),
                    groupId = voteJson.optString("groupId", groupId),
                    initiatedByUserId = voteJson.optString("initiatedByUserId", voteJson.optString("initiatedBy", "")),
                    initiatedByUsername = voteJson.optString("initiatedByUsername", voteJson.optString("initiatedByUsername", voteJson.optString("initiatedBy", ""))),
                    votesFor = (0 until (voteJson.optJSONArray("votesFor")?.length() ?: 0)).mapNotNull { idx -> voteJson.optJSONArray("votesFor")?.optString(idx, null) }.filter { it.isNotBlank() },
                    votesAgainst = (0 until (voteJson.optJSONArray("votesAgainst")?.length() ?: 0)).mapNotNull { idx -> voteJson.optJSONArray("votesAgainst")?.optString(idx, null) }.filter { it.isNotBlank() },
                    createdAt = voteJson.optLong("createdAt", System.currentTimeMillis()),
                    expiresAt = voteJson.optLong("expiresAt", System.currentTimeMillis()),
                    status = voteJson.optString("status", "active"),
                    voteType = voteJson.optString("voteType", "deletion"),
                    approvingVotes = voteJson.optInt("approvingVotes", voteJson.optInt("votesForCount", 0)),
                    rejectingVotes = voteJson.optInt("rejectingVotes", voteJson.optInt("votesAgainstCount", 0)),
                    totalMembers = voteJson.optInt("totalMembers", voteJson.optInt("memberCount", 0)),
                    approvalThreshold = voteJson.optInt("approvalThreshold", 0),
                    approvalPercentage = voteJson.optDouble("approvalPercentage", voteJson.optDouble("approvalRatio", 0.0)).toFloat(),
                    hasUserVoted = voteJson.optBoolean("hasUserVoted", false)
                ))
            }
            Result.success(votes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun castClearHistoryVote(groupId: String, voteId: String, approve: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/clear-history-vote/$voteId/vote"
            val voteValue = if (approve) "yes" else "no"
            val requestBody = JSONObject().apply {
                put("vote", voteValue)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(groupId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/members/$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun promoteGroupAdmin(groupId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/admins/$userId"
            val requestBody = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun demoteGroupAdmin(groupId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/admins/$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroupDetails(groupId: String, name: String, description: String, isPrivate: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId"
            val requestBody = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("isPrivate", isPrivate)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateClearHistoryVote(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/clear-history-vote"
            val requestBody = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/leave"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriendsNotInGroup(groupId: String): Result<List<UserData>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/friends?notInGroup=$groupId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val friendsArray = json.optJSONArray("friends") ?: JSONArray()
            val friends = mutableListOf<UserData>()
            for (i in 0 until friendsArray.length()) {
                val friendJson = friendsArray.getJSONObject(i)
                friends.add(UserData(
                    userId = friendJson.optString("userId", ""),
                    username = friendJson.optString("username", ""),
                    name = friendJson.optString("displayName", friendJson.optString("username", "")),
                    email = friendJson.optString("email", ""),
                    avatar = friendJson.optString("avatar", null),
                    status = friendJson.optString("status", "Available"),
                    isOnline = friendJson.optBoolean("isOnline", false)
                ))
            }
            Result.success(friends)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadGroupPicture(groupId: String, imageData: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/picture"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("picture", "group_picture.jpg", imageData.toRequestBody("image/jpeg".toMediaType()))
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateGroupInviteCode(groupId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/invite-code"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            Result.success(json.optString("inviteCode", ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revokeGroupInviteCode(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/invite-code"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun castGroupDeletionVote(groupId: String, voteId: String, approve: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/deletion-vote/$voteId/vote"
            val requestBody = JSONObject().apply {
                put("approve", approve)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroupByCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/join/$code"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val groupId = try {
                JSONObject(responseBody).optString("groupId", JSONObject(responseBody).optString("id", ""))
            } catch (e: Exception) {
                ""
            }
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroupByInvite(inviteId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/invite/$inviteId/join"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val groupId = try {
                JSONObject(responseBody).optString("groupId", JSONObject(responseBody).optString("id", ""))
            } catch (e: Exception) {
                ""
            }
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingGroupInvitations(): Result<List<GroupInvitation>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/group-invitations"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val invitationsArray = json.optJSONArray("invitations") ?: JSONArray()
            val invitations = mutableListOf<GroupInvitation>()

            for (i in 0 until invitationsArray.length()) {
                val inviteJson = invitationsArray.getJSONObject(i)
                invitations.add(GroupInvitation(
                    inviteId = inviteJson.optString("inviteId", ""),
                    groupId = inviteJson.optString("groupId", ""),
                    groupName = inviteJson.optString("groupName", ""),
                    groupIcon = inviteJson.optString("groupIcon", null),
                    inviterUsername = inviteJson.optString("inviterUsername", ""),
                    inviterDisplayName = inviteJson.optString("inviterDisplayName", ""),
                    status = inviteJson.optString("status", "pending"),
                    createdAt = inviteJson.optString("createdAt", "")
                ))
            }
            Result.success(invitations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptGroupInvitation(inviteId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/group-invitations/$inviteId/accept"
            val requestBody = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val groupId = json.optJSONObject("group")?.optString("id", "") ?: ""
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun declineGroupInvitation(inviteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/group-invitations/$inviteId/decline"
            val requestBody = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class GroupInvitation(
        val inviteId: String,
        val groupId: String,
        val groupName: String,
        val groupIcon: String?,
        val inviterUsername: String,
        val inviterDisplayName: String,
        val status: String,
        val createdAt: String
    )

    suspend fun getGroupDetails(groupId: String): Result<Group> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = "${getBaseUrl()}/api/groups/$groupId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)

            val groupJson = json.optJSONObject("group")
            if (groupJson == null) {
                android.util.Log.e("API_SERVICE", "Error loading group: Response missing 'group' field")
                return@withContext Result.failure(Exception("Invalid response: missing group data"))
            }
            val membersArray = groupJson.optJSONArray("members") ?: JSONArray()

            val members = mutableListOf<GroupMember>()
            for (i in 0 until membersArray.length()) {
                val memberJson = membersArray.optJSONObject(i) ?: continue
                val memberRole = memberJson.optString("role", "USER")
                members.add(GroupMember(
                    userId = memberJson.optString("userId", ""),
                    username = memberJson.optString("username", ""),
                    displayName = memberJson.optString("displayName", memberJson.optString("username", "")),
                    avatar = memberJson.optString("avatar", null),
                    role = memberRole,
                    tags = run {
                        val tagsArray = memberJson.optJSONArray("tags") ?: JSONArray()
                        (0 until tagsArray.length()).map { tagsArray.optString(it, "") }
                    },
                    displayedStatus = memberJson.optString("displayedStatus", "offline"),
                    isAdmin = memberJson.optBoolean("isAdmin", false) || memberRole.equals("admin", ignoreCase = true),
                    joinedAt = memberJson.optString("joinedAt", ""),
                    isSystemAdmin = memberJson.optBoolean("isSystemAdmin", false),
                    isSystemModerator = memberJson.optBoolean("isSystemModerator", false)
                ))
            }

            val group = Group(
                groupId = groupJson.optString("groupId", groupJson.optString("id", "")),
                name = groupJson.optString("name", ""),
                description = groupJson.optString("description", ""),
                creatorId = groupJson.optString("creatorId", ""),
                creatorUsername = groupJson.optString("creatorUsername", ""),
                avatar = groupJson.optString("avatar", null),
                members = members,
                admins = run {
                    val adminsArray = groupJson.optJSONArray("admins") ?: JSONArray()
                    (0 until adminsArray.length()).map { adminsArray.optString(it, "") }
                },
                adminIds = run {
                    val adminIdsArray = groupJson.optJSONArray("adminIds") ?: JSONArray()
                    (0 until adminIdsArray.length()).map { adminIdsArray.optString(it, "") }
                },
                createdAt = groupJson.optString("createdAt", ""),
                memberCount = groupJson.optInt("memberCount", members.size),
                messageCount = groupJson.optInt("messageCount", 0),
                inviteLink = groupJson.optString("inviteLink", null),
                inviteCode = groupJson.optString("inviteCode", null),
                webInviteLink = groupJson.optString("webInviteLink", null),
                webInviteCode = groupJson.optString("webInviteCode", null),
                profilePictureUrl = groupJson.optString("profilePictureUrl", null),
                profilePictureUpdatedAt = groupJson.optString("profilePictureUpdatedAt", null),
                isPrivate = groupJson.optBoolean("isPrivate", false)
            )

            android.util.Log.d("API_SERVICE", " Loaded group '${group.name}' with ${members.size} members")
            Result.success(group)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error loading group: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getGroupMessages(groupId: String, limit: Int = 50): Result<List<GroupMessage>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = "${getBaseUrl()}/api/groups/$groupId/messages?limit=$limit"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val messagesArray = json.optJSONArray("messages") ?: JSONArray()

            val messages = mutableListOf<GroupMessage>()
            for (i in 0 until messagesArray.length()) {
                val msgJson = messagesArray.getJSONObject(i)
                val msgId = msgJson.optString("messageId", msgJson.optString("id", ""))

                val reactionsJson = msgJson.optJSONObject("reactions")
                val reactionsMap = mutableMapOf<String, List<String>>()
                if (reactionsJson != null) {
                    val keys = reactionsJson.keys()
                    while (keys.hasNext()) {
                        val emoji = keys.next() as String
                        val usersArray = reactionsJson.optJSONArray(emoji)
                        if (usersArray != null) {
                            val userList = mutableListOf<String>()
                            for (j in 0 until usersArray.length()) {
                                userList.add(usersArray.getString(j))
                            }
                            reactionsMap[emoji] = userList
                        }
                    }
                }

                fun isValNull(v: String): String? = if (v.isEmpty() || v == "null") null else v
                val replyIdVal = msgJson.optString("replyToMessageId", "")
                val replyUserVal = msgJson.optString("replyToUsername", "")
                val replyTextVal = msgJson.optString("replyToText", "")

                val respondMediaId = msgJson.optString("mediaId", "")
                val respondMediaType = msgJson.optString("mediaType", "")
                val respondMediaName = msgJson.optString("mediaName", "")
                val respondMediaShareMode = msgJson.optString("mediaShareMode", "protected")

                val tagsArray = msgJson.optJSONArray("senderTags")
                val senderTagsList = if (tagsArray != null) {
                    (0 until tagsArray.length()).map { tagsArray.getString(it) }
                } else {
                    emptyList()
                }
                val senderIsAdminVal = msgJson.optBoolean("senderIsAdmin", false)
                val senderIsModeratorVal = msgJson.optBoolean("senderIsModerator", false)
                val senderRoleVal = msgJson.optString("senderRole", null)
                val senderDisplayNameVal = msgJson.optString("senderDisplayName", msgJson.optString("displayName", ""))

                messages.add(GroupMessage(
                    messageId = msgId,
                    groupId = msgJson.optString("groupId", ""),
                    senderId = msgJson.optString("senderId", ""),
                    senderUsername = msgJson.optString("senderUsername", ""),
                    senderAvatar = msgJson.optString("senderAvatar", ""),
                    message = msgJson.optString("content", msgJson.optString("message", "")),
                    timestamp = msgJson.optString("createdAt", msgJson.optString("timestamp", "")),
                    reactions = reactionsMap,
                    replyToMessageId = isValNull(replyIdVal),
                    replyToUsername = isValNull(replyUserVal),
                    replyToText = isValNull(replyTextVal),
                    mediaId = if (respondMediaId.isNotEmpty()) respondMediaId else null,
                    mediaType = if (respondMediaType.isNotEmpty()) respondMediaType else null,
                    mediaName = if (respondMediaName.isNotEmpty()) respondMediaName else null,
                    mediaShareMode = respondMediaShareMode,
                    senderDisplayName = senderDisplayNameVal,
                    senderTags = senderTagsList,
                    senderIsAdmin = senderIsAdminVal,
                    senderIsModerator = senderIsModeratorVal,
                    senderRole = senderRoleVal
                ))
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(groupId: String, content: String, replyToId: String? = null, mediaShareMode: String? = null): Result<GroupMessage> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = "${getBaseUrl()}/api/groups/$groupId/messages"
            val requestBody = JSONObject().apply {
                put("content", content)
                if (!replyToId.isNullOrEmpty()) {
                    put("replyToMessageId", replyToId)
                }
                if (!mediaShareMode.isNullOrEmpty()) {
                    put("mediaShareMode", mediaShareMode)
                }
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val json = JSONObject(responseBody)

            val messageId = json.optString("messageId", json.optString("id", ""))
            val messageContent = json.optString("content", json.optString("message", content))
            val timestamp = json.optString("timestamp", json.optLong("timestamp", System.currentTimeMillis()).toString())

            val reactionsJson = json.optJSONObject("reactions")
            val reactionsMap = mutableMapOf<String, List<String>>()
            if (reactionsJson != null) {
                val keys = reactionsJson.keys()
                while (keys.hasNext()) {
                    val emoji = keys.next() as String
                    val usersArray = reactionsJson.optJSONArray(emoji)
                    if (usersArray != null) {
                        val userList = mutableListOf<String>()
                        for (j in 0 until usersArray.length()) {
                            userList.add(usersArray.getString(j))
                        }
                        reactionsMap[emoji] = userList
                    }
                }
            }

            fun isValNull(v: String): String? = if (v.isEmpty() || v == "null") null else v
            val replyToMsgIdVal = json.optString("replyToMessageId", "")
            val replyToUserVal = json.optString("replyToUsername", "")
            val replyToMsgVal = json.optString("replyToText", "")

            val respondMediaId = json.optString("mediaId", "")
            val respondMediaType = json.optString("mediaType", "")
            val respondMediaName = json.optString("mediaName", "")
            val respondMediaShareMode = json.optString("mediaShareMode", mediaShareMode ?: "protected")

            Result.success(GroupMessage(
                messageId = messageId,
                groupId = json.optString("groupId", groupId),
                senderId = json.optString("senderId", ""),
                senderUsername = json.optString("senderUsername", ""),
                senderAvatar = json.optString("senderAvatar", ""),
                message = messageContent,
                timestamp = timestamp,
                reactions = reactionsMap,
                replyToMessageId = isValNull(replyToMsgIdVal),
                replyToUsername = isValNull(replyToUserVal),
                replyToText = isValNull(replyToMsgVal),
                mediaId = if (respondMediaId.isNotEmpty()) respondMediaId else null,
                mediaType = if (respondMediaType.isNotEmpty()) respondMediaType else null,
                mediaName = if (respondMediaName.isNotEmpty()) respondMediaName else null,
                mediaShareMode = respondMediaShareMode
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun generateExpiringInviteLink(groupId: String, expiresIn: Long): Result<ExpiringInviteLink> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/invite-link"

            val requestBody = JSONObject().apply {
                put("expiresIn", expiresIn)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            Result.success(ExpiringInviteLink(
                inviteCode = json.optString("inviteCode", ""),
                shareLink = json.optString("shareLink", ""),
                expiresAt = json.optLong("expiresAt", 0)
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
