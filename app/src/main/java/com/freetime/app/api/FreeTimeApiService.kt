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
import java.util.concurrent.TimeUnit // ✅ Ensure this import is present

/**
 * Data class for send message response
 */
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

/**
 * Data class for friend request response
 * Includes auto-accept detection for mutual friend requests
 */
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
    val interests: List<String> = emptyList(),  // NEW: User's interests for discovery
    val friendId: String = "",  // NEW: Track friend relationship ID for remove/block
    val isOnline: Boolean = false,  // REAL: Actual online status from server
    val actualOnlineStatus: String = "offline",  // REAL: 'online', 'offline', 'occupied'
    val lastSeen: String = "",  // For displaying "online", "offline", "2m ago", etc.
    val isAdmin: Boolean = false,  // ✅ NEW: FreeTime admin status for color coding
    val isModerator: Boolean = false,  // ✅ NEW: FreeTime moderator status for color coding
    val role: String? = null  // ✅ NEW: User's role field for color display
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
    val reactions: Map<String, List<String>> = emptyMap(),  // ✅ NEW: Emoji reactions mapping
    // ✅ REPLY SUPPORT: Fields for WhatsApp-style replies
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    // ✅ MEDIA REQUESTS: Track pending download requests for this message
    val pendingRequests: List<com.freetime.app.services.WebSocketManager.MediaDownloadRequestData> = emptyList(),
    // ✅ NEW: Two-tier media sharing - "public" (no encryption) or "protected" (encrypted with download requests)
    val mediaShareMode: String? = "protected",  // Default to protected for backward compatibility
    // ✅ NEW: Color-coding fields for display name/username color
    val senderDisplayName: String = "",  // Sender's display name
    val senderTags: List<String> = emptyList(),  // Sender's tags for color coding
    val senderIsAdmin: Boolean = false,  // Sender's admin status
    val senderIsModerator: Boolean = false,  // Sender's moderator status
    val senderRole: String? = null  // Sender's role field
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
    val adminIds: List<String> = emptyList(),  // ✅ FIX: Server also sends this field
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
    val mutedMembers: List<Any>? = null  // ✅ FIX: Support muted member tracking
)

// ✅ NEW: Expiring invitation link data class
data class ExpiringInviteLink(
    val inviteCode: String,
    val shareLink: String,
    val expiresAt: Long,
)

// Data class for group deletion votes
data class GroupDeletionVote(
    val voteId: String,
    val groupId: String,
    val initiatedByUserId: String,
    val initiatedByUsername: String,
    val votesFor: List<String>, // User IDs who voted for
    val votesAgainst: List<String>, // User IDs who voted against
    val createdAt: Long,
    val expiresAt: Long,
    val status: String, // "active", "cleared", "rejected"
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
    val icon: String = "",  // ✅ NEW: Icon emoji/text for display
    val color: String = "",  // ✅ NEW: Color hex for badge display
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

// Data class for Call initiation response
data class CallInitiationResponse(
    val callId: String,
    val success: Boolean,
    val message: String,
    val sdpOffer: String? = null, // SDP offer for the callee
    val calleeId: String? = null // ID of the callee
)

// Data class for Call approval response (backend perspective)
data class CallApprovalResponse(
    val callId: String,
    val success: Boolean,
    val message: String,
    val sdpAnswer: String? = null // SDP answer from the callee
)

// Data class for Call rejection response
data class CallRejectionResponse(
    val callId: String,
    val success: Boolean,
    val message: String
)

// Data class for retrieving call details
data class CallDetailsResponse(
    val callId: String,
    val callerId: String,
    val calleeId: String,
    val status: String, // "ringing", "active", "ended", "rejected", "missed"
    val callType: String, // "audio", "video"
    val sdpOffer: String? = null, // SDP offer if the call was initiated but not yet answered
    val sdpAnswer: String? = null, // SDP answer if the call was answered
    val startTime: Long? = null,
    val endTime: Long? = null,
    val createdAt: Long
)

// Data class for Call History Item
data class CallHistoryItem(
    val callId: String,
    val recipientName: String,
    val startTime: Long,
    val durationSeconds: Int,
    val status: String,
    val direction: String // "incoming", "outgoing"
)

// Data class for Friend Request
data class FriendRequest(
    val requestId: String,
    val senderId: String,
    val senderUsername: String,
    val createdAt: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val avatarUrl: String? = null,
    val senderTags: List<String> = emptyList(),  // ✅ NEW: For color display
    val senderRole: String? = null,  // ✅ NEW: For color display
    val senderIsAdmin: Boolean = false,  // ✅ NEW: For color display
    val senderIsModerator: Boolean = false  // ✅ NEW: For color display
)

// Data class for Media Download Request Info
data class MediaDownloadRequestInfo(
    val requestId: String,
    val requesterId: String? = null,
    val mediaId: String? = null,
    val requesterName: String,
    val reason: String,
    val requestedAt: String
)

// Data class for Media Download Approval
data class MediaDownloadApproval(
    val downloadLink: String,
    val mediaId: String,
    val fileName: String,
    val mimeType: String,
    val encrypted: Boolean,
    val encryptionKey: String? = null,
    val iv: String? = null
)

// Data class for Verify Token response (used by verifyToken method)
data class VerifyTokenResponse(
    val valid: Boolean,
    val userId: String,
    val username: String,
    val message: String
)

// Announcement system user ID - used for admin announcement DMs
const val ANNOUNCEMENT_USER_ID = "announcement_system"
const val ANNOUNCEMENT_USERNAME = "announcement"
const val ANNOUNCEMENT_DISPLAY_NAME = "📢 Announcement"

class FreeTimeApiService(private val context: Context) {

    // Use a single OkHttpClient instance for efficiency
    // ✅ Corrected TimeUnit usage and added explicit import
    // ✅ Reverted to by lazy initialization for SSLContext and TrustManagers
    private val sslContext: SSLContext by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, java.security.SecureRandom())
        sslContext
    }

    private val trustManagers: Array<TrustManager> by lazy {
        arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })
    }
    // ✅ Accessing trustAllCerts after trustManagers is initialized via lazy
    private val trustAllCerts = arrayOf<TrustManager>(trustManagers[0])

    // ✅ Simplified OkHttpClient configuration: Removed custom SSLSocketFactory and HostnameVerifier for now.
    // This might resolve the sslContext/trustAllCerts initialization errors.
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

    /**
     * ✅ NEW: Get current logged in user ID
     */
    fun getCurrentUserId(): String = prefs.getUserId() ?: ""

    /**
     * ✅ NEW: Resolve a potentially relative avatar URL to a full URL
     */
    fun resolveAvatarUrl(avatar: String?): String? {
        if (avatar.isNullOrEmpty()) return null
        if (avatar.startsWith("http")) return avatar
        
        // Remove leading slash if present
        val cleanAvatar = if (avatar.startsWith("/")) avatar.substring(1) else avatar
        return "${getBaseUrl()}/$cleanAvatar"
    }

    /**
     * Verify user token with server for session validation and Zero-Touch sync.
     * Refactored to return VerifyTokenResponse directly and handle errors.
     */
    // ✅ MODIFIED: Return type changed from Response<VerifyTokenResponse> to VerifyTokenResponse
    suspend fun verifyToken(token: String): VerifyTokenResponse {
        return withContext(Dispatchers.IO) {
            val url = "${getBaseUrl()}/api/auth/verify-token"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token) // Assuming token includes "Bearer "
                .get()
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                // Handle network errors
                return@withContext VerifyTokenResponse(valid = false, userId = "", username = "", message = "Network error: ${e.message}")
            }

            val responseBody = response.body?.string() ?: ""
            response.body?.close() // Close the response body to prevent leaks

            try {
                val json = JSONObject(responseBody)
                VerifyTokenResponse(
                    valid = json.optBoolean("valid", false),
                    userId = json.optString("userId", ""),
                    username = json.optString("username", ""),
                    message = json.optString("message", "")
                )
            } catch (e: Exception) {
                // Handle JSON parsing errors
                VerifyTokenResponse(valid = false, userId = "", username = "", message = "Error parsing server response: ${e.message}")
            }
        }
    }

    /**
     * ✅ NEW: Get pending notifications for background polling
     * @param userId Current user ID
     * @param since Optional timestamp to only get new notifications
     * @return Result containing list of pending messages and calls
     */
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

            val callsList = mutableListOf<PendingCall>()
            val callsArray = json.optJSONArray("calls") ?: JSONArray()
            for (i in 0 until callsArray.length()) {
                val call = callsArray.getJSONObject(i)
                callsList.add(PendingCall(
                    callerId = call.optString("callerId", ""),
                    callerName = call.optString("callerName", "Unknown"),
                    callerAvatar = if (call.isNull("callerAvatar")) null else call.optString("callerAvatar", null),
                    callType = call.optString("callType", "voice"),
                    timestamp = call.optLong("timestamp", 0)
                ))
            }

            Result.success(PendingNotificationsResponse(messagesList, callsList))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Report a user for misconduct
     * @param reportedUserId The ID of the user being reported
     * @param reason The reason for reporting (e.g., "spam", "harassment")
     * @param description Detailed description of the incident
     * @return Result containing the report ID on success
     */
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

    /**
     * Send a text message to a user
     */
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
                response.body?.close() // Close the response body

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

    /**
     * Upload media to chat
     * Returns a Pair of (mediaId, encryptionKey) or null on failure
     */
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

            // Determine if media is encrypted based on recipientId (private vs group)
            // For now, assume private messages are encrypted, group messages may be public or protected
            val isEncrypted = recipientId.startsWith("group:") == false // Simple heuristic: if not a group, assume encrypted

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
            response.body?.close() // Close the response body

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", "Media upload failed: HTTP ${response.code}")
                return@withContext null
            }

            val json = JSONObject(responseBody)

            if (json.optBoolean("success", false)) {
                val mediaId = json.optString("mediaId", "")
                val encryptionKey = if (isEncrypted) json.optString("encryptionKey", null) else null
                if (mediaId.isNotEmpty()) {
                    android.util.Log.d("API_SERVICE", "✅ Media uploaded successfully: ID=$mediaId, Encrypted=$isEncrypted, Key=${encryptionKey != null}")
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

    /**
     * Upload PUBLIC (unencrypted) media to a group chat
     * @param mediaData The media bytes
     * @param fileName Original filename
     * @param mimeType MIME type of the media
     * @param recipientId Group ID (prefixed with "group:")
     * @param token Authorization token
     */
    suspend fun uploadPublicMediaToChat(
        mediaData: ByteArray,
        fileName: String,
        mimeType: String,
        recipientId: String, // Should be "group:groupId"
        token: String,
        groupId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${getBaseUrl()}/api/media/upload"

            // Upload WITHOUT encryption for public media
            val mediaRequestBody = mediaData.toRequestBody(mimeType.toMediaType())
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("recipientId", recipientId)
                .addFormDataPart("encrypted", "false")  // ✅ Mark as unencrypted
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
            response.body?.close() // Close the response body

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", "Public media upload failed: HTTP ${response.code}")
                return@withContext null
            }

            val json = JSONObject(responseBody)

            if (json.optBoolean("success", false)) {
                val mediaId = json.optString("mediaId", "")
                if (mediaId.isNotEmpty()) {
                    android.util.Log.d("API_SERVICE", "✅ Public media uploaded successfully (unencrypted): $mediaId")
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

    // ✅ NEW: Answer call API method
    /**
     * Answer an incoming call
     * @param callId The ID of the call to answer
     * @param answerSdp The SDP answer from the client
     * @return Result indicating success or failure
     */
    suspend fun answerCall(callId: String, answerSdp: String): Result<CallApprovalResponse> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/answer"

            val requestBody = JSONObject().apply {
                put("sdpAnswer", answerSdp)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                val errorBody = try { JSONObject(responseBody).optString("error", responseBody) } catch (e: Exception) { responseBody }
                return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }

            // Parse the successful response into CallApprovalResponse
            val json = JSONObject(responseBody)
            val callApprovalResponse = CallApprovalResponse(
                callId = json.optString("callId", callId),
                success = json.optBoolean("success", true),
                message = json.optString("message", "Call answered successfully"),
                sdpAnswer = json.optString("sdpAnswer", null) // backend might send back the answer again or something else
            )
            Result.success(callApprovalResponse)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error answering call: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * Download media from server
     * @param mediaId Media ID to download
     * @param authHeader Authorization header (Bearer token)
     * @return Raw bytes of the media or null on failure
     */
    suspend fun downloadMedia(mediaId: String, authHeader: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = authHeader ?: ("Bearer " + (getAuthToken() ?: return@withContext null))
            val url = "${getBaseUrl()}/api/media/$mediaId/download"
            android.util.Log.d("API_SERVICE", "📥 Downloading media: mediaId=$mediaId, url=$url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()
            val responseBody = bytes?.let { String(it) } ?: ""

            if (!response.isSuccessful) {
                android.util.Log.e("API_SERVICE", "❌ Media download failed: HTTP ${response.code}, mediaId=$mediaId, body=$responseBody")
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

            // Successful - return raw bytes (already consumed)
            android.util.Log.d("API_SERVICE", "✅ Media downloaded: size=${bytes?.size ?: 0} bytes")
            bytes
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Exception downloading media: ${e.message}", e)
            null
        }
    }

    /**
     * ✅ NEW: Download and save PUBLIC (unencrypted) media to gallery with original format
     * @param mediaId The media ID
     * @param fileName Original file name with extension
     * @param mediaType Type of media (image/video)
     * @param token Authorization token
     */
    suspend fun downloadAndSavePublicMedia(
        mediaId: String,
        fileName: String,
        mediaType: String,
        token: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${getBaseUrl()}/api/media/$mediaId/download"
            android.util.Log.d("API_SERVICE", "📥 Downloading PUBLIC media: mediaId=$mediaId, fileName=$fileName, url=$url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                android.util.Log.e("API_SERVICE", "❌ Download failed: HTTP ${response.code}, error=$errorBody")
                response.body?.close()
                return@withContext Result.failure(Exception("Failed to download: HTTP ${response.code} - $errorBody"))
            }

            val isEncryptedHeader = response.header("X-Encrypted") == "true"
            val encryptionKeyHeader = response.header("X-Encryption-Key")
            onProgress?.invoke(0.1f)
            val mediaBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("No data received"))
            response.body?.close() // Close the response body
            onProgress?.invoke(0.5f)

            val finalBytes = if (isEncryptedHeader && !encryptionKeyHeader.isNullOrEmpty()) {
                android.util.Log.d("API_SERVICE", "🔓 Decrypting public media using header key: keyLength=${encryptionKeyHeader.length}")
                try {
                    MediaEncryption(context).decryptMedia(mediaBytes, encryptionKeyHeader) ?: mediaBytes
                } catch (e: Exception) {
                    android.util.Log.e("API_SERVICE", "❌ Decryption of public media failed: ${e.message}", e)
                    mediaBytes
                }
            } else {
                mediaBytes
            }

            // ✅ FIX: Save with original file extension preserved
            // Extract extension from fileName
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
                // Clean up if stream fails
                context.contentResolver.delete(uri, null, null)
                return@withContext Result.failure(Exception("Failed to open output stream"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error downloading public media: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Download and decrypt media that has been approved for download
     * @param approval Approval details including download link and encryption key
     * @return Result indicating success or failure
     */
    suspend fun downloadAndDecryptApprovedMedia(approval: MediaDownloadApproval, onProgress: ((Float) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            
            // 1. Download the encrypted media
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

            // 2. Decrypt if necessary
            val finalBytes = if (approval.encrypted && !approval.encryptionKey.isNullOrEmpty()) {
                MediaEncryption(context).decryptMedia(encryptedBytes, approval.encryptionKey)
            } else {
                encryptedBytes
            } ?: return@withContext Result.failure(Exception("Failed to decrypt media"))

            // 3. Save to gallery
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
            android.util.Log.e("API_SERVICE", "Error in downloadAndDecryptApprovedMedia: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Download and save PROTECTED (encrypted) media with original format using approval data
     * @param mediaId The media ID
     * @param fileName Original file name with extension
     * @param mediaType Type of media (image/video)
     * @param mediaKey Encryption key
     */
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
                android.util.Log.e("API_SERVICE", "❌ PROTECTED media download failed: HTTP ${response.code}, error=$errorBody")
                response.body?.close()
                return@withContext Result.failure(Exception("Failed to download: HTTP ${response.code} - $errorBody"))
            }

            onProgress?.invoke(0.1f)
            val encryptedBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("No data received"))
            response.body?.close()
            onProgress?.invoke(0.5f)
            
            // Step 2: Decrypt using MediaEncryption with the key from message
            val encryptor = MediaEncryption(context)
            android.util.Log.d("API_SERVICE", "🔐 Decrypting PROTECTED media: keyLength=${mediaKey.length}, encryptedSize=${encryptedBytes.size}")
            val decryptedBytes = try {
                encryptor.decryptMedia(encryptedBytes, mediaKey)
            } catch (decryptError: Exception) {
                android.util.Log.e("API_SERVICE", "❌ Decryption failed: ${decryptError.message}", decryptError)
                return@withContext Result.failure(Exception("Failed to decrypt media: ${decryptError.message}"))
            }
            
            android.util.Log.d("API_SERVICE", "✅ Media decrypted successfully: decryptedSize=${decryptedBytes.size}")
            
            // Step 3: Save to device gallery/storage using MediaStore
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
                            else -> "Downloads/FreeTime" // Save other types to Downloads
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
                        // Fallback for older versions if media type is not image/video
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
                // Clean up the created file entry if stream fails
                context.contentResolver.delete(uri, null, null)
                return@withContext Result.failure(Exception("Failed to open output stream"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error saving protected media: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * Request download access for a media item
     * @param mediaId The ID of the media to request access for
     * @return Result indicating success or failure
     */
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

    /**
     * Approve a media download request
     * @param requestId The ID of the download request to approve
     * @return Result indicating success or failure
     */
    suspend fun approveMediaDownloadRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/download-request/$requestId/approve"
            val requestBody = JSONObject().apply {
                // No specific body needed for approval, just the action
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

    /**
     * Deny a media download request
     * @param requestId The ID of the download request to deny
     * @return Result indicating success or failure
     */
    suspend fun denyMediaDownloadRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/media/download-request/$requestId/deny"
            val requestBody = JSONObject().apply {
                // No specific body needed for denial, just the action
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


    /**
     * Register device FCM token in FCMTokens collection
     */
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

    /**
     * Register FCM token in users collection for a specific user
     */
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

    /**
     * ✅ NEW: Initiate a WebRTC call with SDP offer
     * @param recipientId The user ID to call
     * @param callType "voice" or "video"
     * @param offer SDP offer string
     * @return Result with the server-assigned callId
     */
    suspend fun initiateCall(recipientId: String, callType: String, offer: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/initiate"
            
            val requestBody = JSONObject().apply {
                put("recipientId", recipientId)
                put("callType", callType)
                put("offer", offer)
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
            val callId = json.optString("callId", "")
            if (callId.isNotEmpty()) {
                Result.success(callId)
            } else {
                Result.failure(Exception("No callId returned from server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Send WebRTC ICE candidate to peer via server
     * @param callId The active call ID
     * @param recipientId The peer user ID
     * @param candidate ICE candidate JSON string
     * @return Result indicating success or failure
     */
    suspend fun sendIceCandidate(callId: String, recipientId: String, candidate: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/candidate"
            
            val requestBody = JSONObject().apply {
                put("recipientId", recipientId)
                put("candidate", candidate)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.body?.close()

            if (!response.isSuccessful) {
                // Log HTTP error for runtime debugging (no body available here)
                android.util.Log.e("FREETIME_API", "sendIceCandidate(): HTTP ${response.code} - ${response.message}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Initiate a simple call
     */
    suspend fun initiateSimpleCall(recipientId: String, callType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/initiate"
            
            val requestBody = JSONObject().apply {
                put("recipientId", recipientId)
                put("callType", callType)
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
            // Expecting { success: true, callId: "..." }
            val callId = json.optString("callId", "")
            if (callId.isNotEmpty()) {
                Result.success(callId)
            } else {
                Result.failure(Exception("No callId returned from server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * End a call
     */
    suspend fun endCall(callId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/end"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            Result.success("Call ended")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject a call
     */
    suspend fun rejectCall(callId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/reject"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody())
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.body?.close()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            Result.success("Call rejected")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Get call history from backend
     */
    suspend fun getCallHistory(limit: Int = 50): Result<List<CallHistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/history?limit=$limit"
            
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
            val history = mutableListOf<CallHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                history.add(CallHistoryItem(
                    callId = obj.optString("_id", obj.optString("callId", "")),
                    recipientName = obj.optString("recipientName", "Unknown"),
                    startTime = obj.optLong("startTime", 0),
                    durationSeconds = obj.optInt("durationSeconds", 0),
                    status = obj.optString("status", "completed"),
                    direction = obj.optString("direction", "outgoing")
                ))
            }
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Save call metrics to backend
     */
    suspend fun saveCallMetrics(callId: String, packetLoss: Float, latency: Int, jitter: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/metrics"
            
            val requestBody = JSONObject().apply {
                put("packetLoss", packetLoss)
                put("latency", latency)
                put("jitter", jitter)
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

    /**
     * ✅ NEW: Report a missed call
     */
    suspend fun reportMissedCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/missed"
            
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

    /**
     * ✅ NEW: Create a new group
     */
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
                // ✅ FIX: Check if group data exists in response even on error (handles 503 gracefully)
                // The group might be created but server returns 503 (server busy)
                val groupJson = if (json.has("group")) json.getJSONObject("group") else null
                
                // If group data is present, consider it success (server may have returned 503 after creation)
                if (groupJson != null && groupJson.length() > 0) {
                    val groupId = groupJson.optString("groupId", groupJson.optString("_id", groupJson.optString("id", "")))
                    if (groupId.isNotEmpty()) {
                        android.util.Log.w("API_SERVICE", "⚠️ Group created but HTTP ${response.code}: ${response.message}. Proceeding with group.")
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
            // Re-use logic from getGroupDetails or similar
            val groupJson = if (json.has("group")) json.getJSONObject("group") else json
            Result.success(parseGroupInternal(groupJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    /**
     * ✅ NEW: Connect WebSocket (bridge to WebSocketManager)
     */
    fun connectWebSocket() {
        val token = prefs.getToken() ?: return
        val serverUrl = getBaseUrl()
        val userId = getCurrentUserId()
        com.freetime.app.services.WebSocketManager.getInstance().connect(serverUrl, token, userId)
    }

    /**
     * ✅ NEW: Search messages in a chat
     */
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

    /**
     * Add ICE candidate for a call
     */
    suspend fun addCallCandidate(callId: String, candidate: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId/candidate"
            
            val requestBody = JSONObject().apply {
                put("candidate", candidate)
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

    /**
     * Get call details
     */
    suspend fun getCall(callId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId"
            
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

            Result.success(JSONObject(responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Get user status (online/offline)
     */
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

    /**
     * ✅ NEW: Get pending media download requests
     */
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

    /**
     * ✅ NEW: Send typing indicator
     */
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

    /**
     * ✅ NEW: Delete chat history with a user
     */
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

    /**
     * ✅ NEW: Upload profile banner from byte array
     */
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
            Result.success(json.optString("bannerUrl", ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Delete profile banner
     */
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

    /**
     * ✅ NEW: Delete profile image
     */
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

    /**
     * ✅ NEW: Set availability status
     */
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

    /**
     * ✅ NEW: Set status message
     */
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

    /**
     * ✅ NEW: Set language preference
     */
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

    /**
     * ✅ NEW: Set theme preference
     */
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

    /**
     * ✅ NEW: Update display name
     */
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

    /**
     * ✅ NEW: Update bio
     */
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

    /**
     * ✅ NEW: Fetch privacy settings from server
     */
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

    /**
     * ✅ NEW: Update privacy settings (generic with key-value map)
     */
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

    /**
     * ✅ NEW: Change password
     */
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

    /**
     * ✅ NEW: Delete account
     */
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

    /**
     * ✅ NEW: Delete chat history (alias for deleteChatHistoryWithUser)
     */
    suspend fun deleteChatHistory(recipientId: String): Result<Unit> = deleteChatHistoryWithUser(recipientId)

    /**
     * ✅ NEW: Send friend request (with response that includes autoAccepted field)
     */
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

    /**
     * ✅ NEW: Remove a friend
     */
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

    /**
     * ✅ NEW: Get current user profile
     */
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
            banner = json.optString("banner", null),
            status = json.optString("status", "Available"),
            pronouns = json.optString("pronouns", ""),
            tags = run {
                val arr = json.optJSONArray("tags") ?: JSONArray()
                (0 until arr.length()).map { arr.getString(it) }
            },
            badges = emptyList(), // Populate if needed
            role = json.optString("role", "User"),
            lastUsernameChangeAt = json.optString("lastUsernameChangeAt", null),
            lastDisplayNameChangeAt = json.optString("lastDisplayNameChangeAt", null),
            isCurrentUser = true
        )
    }

    /**
     * ✅ NEW: Upload profile image from raw byte array
     */
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

    /**
     * ✅ NEW: Upload profile image from content URI
     */
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

    /**
     * ✅ NEW: Upload profile image from base64 string
     */
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

    /**
     * ✅ NEW: Update username
     */
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

    /**
     * ✅ NEW: Update user profile (generic)
     */
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
            profilePicture = profilePicture
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

    /**
     * ✅ NEW: Get public user profile
     */
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

    /**
     * ✅ NEW: Logout
     */
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

    /**
     * ✅ NEW: Get friends
     */
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

            // Try multiple shapes: raw array, { friends: [...] }, { success: true, friends: [...] }, { data: { friends: [...] } }
            val jsonArray = try {
                JSONArray(responseBody)
            } catch (e: Exception) {
                try {
                    val root = JSONObject(responseBody)
                    // Common patterns
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
                // ✅ NEW: Extract tags array from JSON
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
                    tags = tagsList,  // ✅ NEW: Extract tags
                    isOnline = obj.optBoolean("isOnline", false),
                    lastSeen = obj.optString("lastSeen", ""),
                    isAdmin = obj.optBoolean("isAdmin", false),  // ✅ NEW: Extract isAdmin
                    isModerator = obj.optBoolean("isModerator", false),  // ✅ NEW: Extract isModerator
                    role = obj.optString("role", null)  // ✅ NEW: Extract role
                ))
            }
            // If friends list is empty, log for debugging to help identify contract mismatches
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

    /**
     * ✅ NEW: Get user groups
     */
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

    /**
     * ✅ NEW: Get pending calls via REST (fallback polling)
     */
    suspend fun getPendingCallsViaREST(): Result<List<Map<String, Any?>>> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/pending"
            
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
            val calls = mutableListOf<Map<String, Any?>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    map[key] = obj.get(key)
                }
                calls.add(map)
            }
            Result.success(calls)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ NEW: Get pending friend requests via REST (fallback polling)
     */
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

    /**
     * ✅ NEW: Test Socket.IO diagnostic
     */
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

    /**
     * ✅ NEW: Get user badges
     */
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

    /**
     * ✅ NEW: Add group reaction
     */
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

    /**
     * ✅ NEW: Remove group reaction
     */
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

    /**
     * Delete a call
     */
    suspend fun deleteCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/calls/$callId"
            
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

    /**
     * Delete an individual message
     */
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

    /**
     * Add reaction to a message
     */
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

    /**
     * Remove reaction from a message
     */
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


    /**
     * Search users
     */
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

    /**
     * Get pending friend requests
     */
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

            // ✅ FIX: Backend returns raw array [], not wrapped object
            val requestsArray = if (responseBody.trim().startsWith("[")) {
                JSONArray(responseBody)
            } else {
                val json = JSONObject(responseBody)
                json.optJSONArray("requests") ?: JSONArray()
            }
            val requests = mutableListOf<FriendRequest>()
            for (i in 0 until requestsArray.length()) {
                val requestJson = requestsArray.getJSONObject(i)
                
                // ✅ NEW: Extract color fields from request
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
                    senderTags = senderTagsList,  // ✅ NEW: Extract tags
                    senderRole = senderRoleVal,  // ✅ NEW: Extract role
                    senderIsAdmin = senderIsAdminVal,  // ✅ NEW: Extract admin status
                    senderIsModerator = senderIsModeratorVal  // ✅ NEW: Extract moderator status
                ))
            }
            Result.success(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept friend request
     */
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

    /**
     * Decline friend request
     */
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

    /**
     * Invite a user to a group
     */
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

    /**
     * Get active votes for a group
     */
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

    /**
     * Cast vote for clearing history
     */
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

    /**
     * Remove member from group
     */
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

    /**
     * Promote member to admin
     */
    suspend fun promoteGroupAdmin(groupId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/admins/$userId"
            val requestBody = "{}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put(requestBody)  // ✅ FIXED: Changed from POST to PUT for correct HTTP semantics
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

    /**
     * Demote admin to user
     */
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

    /**
     * Update group details
     */
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

    /**
     * Initiate a vote to clear history
     */
    suspend fun initiateClearHistoryVote(groupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAuthToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = "${getBaseUrl()}/api/groups/$groupId/clear-history-vote"
            // ✅ FIX: Add proper JSON content-type for POST request
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

    /**
     * Leave a group
     */
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

    /**
     * Delete a group
     */
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

    /**
     * Get friends not in a specific group
     */
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

    /**
     * Upload group picture
     */
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

    /**
     * Generate group invite code
     */
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

    /**
     * Revoke group invite code
     */
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

    /**
     * Cast vote for group deletion
     */
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

    /**
     * Join group by code
     */
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

    /**
     * Join group by invite link
     */
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


    // ✅ NEW: Group Invitation Management
    
    /**
     * Get pending group invitations for current user
     */
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

    /**
     * Accept group invitation
     */
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

    /**
     * Decline group invitation
     */
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

    // --- Group Chat API Calls ---

    /**
     * Get group details
     */
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

            android.util.Log.d("API_SERVICE", "✅ Loaded group '${group.name}' with ${members.size} members")
            Result.success(group)
        } catch (e: Exception) {
            android.util.Log.e("API_SERVICE", "Error loading group: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get group messages
     * @param groupId Group ID
     * @param limit Max number of messages to retrieve
     * @return List of GroupMessage or error
     */
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

                // ✅ REACTION PARSING: Parse reactions map from API response
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

                // ✅ REPLY SUPPORT: Extract and convert reply fields
                fun isValNull(v: String): String? = if (v.isEmpty() || v == "null") null else v
                val replyIdVal = msgJson.optString("replyToMessageId", "")
                val replyUserVal = msgJson.optString("replyToUsername", "")
                val replyTextVal = msgJson.optString("replyToText", "")

                // ✅ NEW: Extract media and mediaShareMode from response
                val respondMediaId = msgJson.optString("mediaId", "")
                val respondMediaType = msgJson.optString("mediaType", "")
                val respondMediaName = msgJson.optString("mediaName", "")
                val respondMediaShareMode = msgJson.optString("mediaShareMode", "protected")

                // ✅ NEW: Extract color-coding fields from server response (tags, role, admin/moderator status)
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
                    // ✅ REPLY FIELDS: Pass reply data using null coalescing
                    replyToMessageId = isValNull(replyIdVal),
                    replyToUsername = isValNull(replyUserVal),
                    replyToText = isValNull(replyTextVal),
                    // ✅ MEDIA FIELDS: Pass media data
                    mediaId = if (respondMediaId.isNotEmpty()) respondMediaId else null,
                    mediaType = if (respondMediaType.isNotEmpty()) respondMediaType else null,
                    mediaName = if (respondMediaName.isNotEmpty()) respondMediaName else null,
                    mediaShareMode = respondMediaShareMode,
                    // ✅ COLOR-CODING FIELDS: Pass tags and role data for username color display
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

    /**
     * Send a message to a group
     * @param groupId Group ID
     * @param content Message content
     * @return Group message response or error
     */
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
            
            // Response is now returned directly, not wrapped in "message" field
            val messageId = json.optString("messageId", json.optString("id", ""))
            val messageContent = json.optString("content", json.optString("message", content))
            val timestamp = json.optString("timestamp", json.optLong("timestamp", System.currentTimeMillis()).toString())

            // ✅ REACTION PARSING: Parse reactions map from API response (usually empty for new message)
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

            // ✅ REPLY SUPPORT: Extract reply fields
            fun isValNull(v: String): String? = if (v.isEmpty() || v == "null") null else v
            val replyToMsgIdVal = json.optString("replyToMessageId", "")
            val replyToUserVal = json.optString("replyToUsername", "")
            val replyToMsgVal = json.optString("replyToText", "")

            // ✅ NEW: Extract media and mediaShareMode from response
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
                // ✅ REPLY FIELDS: Pass reply data using null coalescing
                replyToMessageId = isValNull(replyToMsgIdVal),
                replyToUsername = isValNull(replyToUserVal),
                replyToText = isValNull(replyToMsgVal),
                // ✅ MEDIA FIELDS: Pass media data
                mediaId = if (respondMediaId.isNotEmpty()) respondMediaId else null,
                mediaType = if (respondMediaType.isNotEmpty()) respondMediaType else null,
                mediaName = if (respondMediaName.isNotEmpty()) respondMediaName else null,
                mediaShareMode = respondMediaShareMode
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Invite a user to a group
     * @param groupId Group ID
     * @param userId User ID to invite
    // ... (rest of the file) ...
 */
    /**
     * ✅ NEW: Generate an expiring invite link for a group
     * @param groupId The group ID
     * @param expiresIn Expiration time in milliseconds (0 for never)
     * @return Result containing the invite link details
     */
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

    // Placeholder for other API calls if needed.
}
