package com.freetime.app.data.network

import android.util.Log
import com.freetime.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketEventListener(
    private val authToken: String,
    private val coroutineScope: CoroutineScope,
    private val onEventReceived: (WebSocketEvent) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) : WebSocketListener() {
    companion object {
        private const val TAG = "WebSocketListener"
        private const val RECONNECT_INTERVAL = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        fun getWebSocketUrl(): String {
            return try {
                BuildConfig.WEBSOCKET_URL
            } catch (e: Exception) {
                "wss://example.com:8080/ws"
            }
        }
    }

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var isIntentionallyClosed = false

    fun connect() {
        try {
            val client = ApiClient.getTrustAllOkHttpClient(
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 10,
                writeTimeoutSeconds = 10
            )

            val request = Request.Builder()
                .url("${getWebSocketUrl()}?token=$authToken")
                .addHeader("Authorization", "Bearer $authToken")
                .build()

            webSocket = client.newWebSocket(request, this)
            reconnectAttempts = 0
            isIntentionallyClosed = false

            Log.d(TAG, "WebSocket connecting...")
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            onError("Connection failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            isIntentionallyClosed = true
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    fun sendMessage(message: String) {
        try {
            webSocket?.send(message)
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            onError("Failed to send message: ${e.message}")
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket connected")
        reconnectAttempts = 0

        coroutineScope.launch(Dispatchers.Main) {
            onConnected()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            Log.d(TAG, "Message received: ${text.take(100)}")
            val json = JSONObject(text)

            val event = WebSocketEvent(
                id = json.optString("id"),
                type = json.optString("type"),
                timestamp = json.optString("timestamp"),
                data = json.optJSONObject("data")?.let { parseEventData(json.optString("type"), it) }
            )

            coroutineScope.launch(Dispatchers.Main) {
                onEventReceived(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message parse error: ${e.message}")
            onError("Failed to parse message: ${e.message}")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closing: $code - $reason")
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket closed: $code - $reason")

        coroutineScope.launch(Dispatchers.Main) {
            onDisconnected()
        }

        if (!isIntentionallyClosed && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Attempting to reconnect (attempt ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
            reconnectAttempts++

            coroutineScope.launch(Dispatchers.Default) {
                delay(RECONNECT_INTERVAL)
                connect()
            }
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket failure: ${t.message}")
        onError("WebSocket error: ${t.message}")

        // stop reconnecting after too many attempts
        if (!isIntentionallyClosed && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++

            coroutineScope.launch(Dispatchers.Default) {
                delay(RECONNECT_INTERVAL)
                connect()
            }
        }
    }

    // convert raw websocket events into typed payloads
    private fun parseEventData(eventType: String, data: JSONObject): Any? {
        return when (eventType) {
            "friend.request.received" -> FriendRequestEventData(
                requestId = data.optString("requestId"),
                senderId = data.optString("senderId"),
                senderUsername = data.optString("senderUsername"),
                createdAt = data.optString("createdAt")
            )

            "friend.request.accepted" -> FriendAcceptedEventData(
                userId = data.optString("userId"),
                username = data.optString("username"),
                acceptedAt = data.optString("acceptedAt")
            )

            "friend.request.rejected" -> FriendRejectedEventData(
                userId = data.optString("userId"),
                rejectedAt = data.optString("rejectedAt")
            )

            "group.vote.initiated" -> GroupVoteInitiatedEventData(
                voteId = data.optString("voteId"),
                groupId = data.optString("groupId"),
                totalMembers = data.optInt("totalMembers"),
                approvalThreshold = data.optInt("approvalThreshold"),
                expiresAt = data.optString("expiresAt")
            )

            "group.vote.cast" -> GroupVoteCastEventData(
                voteId = data.optString("voteId"),
                groupId = data.optString("groupId"),
                votedBy = data.optString("votedBy"),
                vote = data.optString("vote"),
                approvalCount = data.optJSONObject("currentStats")?.optInt("approvalCount") ?: 0,
                rejectionCount = data.optJSONObject("currentStats")?.optInt("rejectionCount") ?: 0
            )

            "group.deleted" -> GroupDeletedEventData(
                groupId = data.optString("groupId"),
                deletedAt = data.optString("deletedAt")
            )

            "channel.message.received" -> ChannelMessageEventData(
                messageId = data.optString("messageId"),
                channelId = data.optString("channelId"),
                senderId = data.optString("senderId"),
                senderUsername = data.optString("senderUsername"),
                content = data.optString("content"),
                createdAt = data.optString("createdAt")
            )

            "channel.message.deleted" -> ChannelMessageDeletedEventData(
                messageId = data.optString("messageId"),
                channelId = data.optString("channelId"),
                deletedBy = data.optString("deletedBy"),
                deletedAt = data.optString("deletedAt")
            )

            "channel.member.promoted" -> ChannelMemberPromotedEventData(
                channelId = data.optString("channelId"),
                memberId = data.optString("memberId"),
                memberUsername = data.optString("memberUsername"),
                promotedBy = data.optString("promotedBy")
            )

            "channel.member.demoted" -> ChannelMemberDemotedEventData(
                channelId = data.optString("channelId"),
                memberId = data.optString("memberId"),
                memberUsername = data.optString("memberUsername"),
                demotedBy = data.optString("demotedBy")
            )

            "media.download.requested" -> MediaDownloadRequestedEventData(
                requestId = data.optString("requestId"),
                mediaId = data.optString("mediaId"),
                requesterId = data.optString("requesterId"),
                requesterUsername = data.optString("requesterUsername"),
                reason = data.optString("reason")
            )

            "media.download.approved" -> MediaDownloadApprovedEventData(
                requestId = data.optString("requestId"),
                mediaId = data.optString("mediaId"),
                downloadUrl = data.optString("downloadUrl"),
                expiresAt = data.optString("expiresAt"),
                encrypted = data.optBoolean("encrypted", false),
                encryptionKey = data.optString("encryptionKey", null).takeIf { it?.isNotEmpty() == true },
                iv = data.optString("iv", null).takeIf { it?.isNotEmpty() == true },
                fileName = data.optString("fileName", null).takeIf { it?.isNotEmpty() == true },
                mimeType = data.optString("mimeType", null).takeIf { it?.isNotEmpty() == true }
            )

            "media.download.denied" -> MediaDownloadDeniedEventData(
                requestId = data.optString("requestId"),
                mediaId = data.optString("mediaId"),
                reason = data.optString("reason")
            )

            "user:profile-updated", "profileUpdated" -> UserProfileUpdatedEventData(
                userId = data.optString("userId"),
                username = data.optString("username"),
                displayName = data.optString("displayName"),
                bio = data.optString("bio"),
                status = data.optString("status"),
                profileImageUrl = data.optString("profileImageUrl"),
                avatar = data.optString("avatar"),
                pronouns = data.optString("pronouns"),
                privacyLevel = data.optString("privacyLevel"),
                lastUpdated = data.optString("lastUpdated")
            )

            "user.online" -> UserStatusEventData(
                userId = data.optString("userId"),
                username = data.optString("username"),
                status = "online"
            )

            "user.offline" -> UserStatusEventData(
                userId = data.optString("userId"),
                lastSeen = data.optString("lastSeen"),
                status = "offline"
            )

            else -> {
                Log.w(TAG, "Unknown event type: $eventType")
                null
            }
        }
    }
}

data class WebSocketEvent(
    val id: String,
    val type: String,
    val timestamp: String,
    val data: Any?
)

data class FriendRequestEventData(
    val requestId: String,
    val senderId: String,
    val senderUsername: String,
    val createdAt: String
)

data class FriendAcceptedEventData(
    val userId: String,
    val username: String,
    val acceptedAt: String
)

data class FriendRejectedEventData(
    val userId: String,
    val rejectedAt: String
)

data class GroupInvitePendingEventData(
    val inviteId: String,
    val groupId: String,
    val groupName: String,
    val groupIcon: String? = null,
    val inviterUsername: String,
    val inviterDisplayName: String = "",
    val status: String = "pending",
    val createdAt: String = ""
)

data class GroupVoteInitiatedEventData(
    val voteId: String,
    val groupId: String,
    val groupName: String = "",
    val totalMembers: Int,
    val approvalThreshold: Int,
    val expiresAt: String
)

data class GroupVoteCastEventData(
    val voteId: String,
    val groupId: String,
    val votedBy: String,
    val vote: String,
    val approvalCount: Int,
    val rejectionCount: Int
)

data class GroupDeletedEventData(
    val groupId: String,
    val deletedAt: String
)

data class ChannelMessageEventData(
    val messageId: String,
    val channelId: String,
    val senderId: String,
    val senderUsername: String,
    val content: String,
    val createdAt: String
)

data class ChannelMessageDeletedEventData(
    val messageId: String,
    val channelId: String,
    val deletedBy: String,
    val deletedAt: String
)

data class ChannelMemberPromotedEventData(
    val channelId: String,
    val memberId: String,
    val memberUsername: String,
    val promotedBy: String
)

data class ChannelMemberDemotedEventData(
    val channelId: String,
    val memberId: String,
    val memberUsername: String,
    val demotedBy: String
)

data class MediaDownloadRequestedEventData(
    val requestId: String,
    val mediaId: String,
    val requesterId: String,
    val requesterUsername: String,
    val reason: String
)

data class MediaDownloadApprovedEventData(
    val requestId: String,
    val mediaId: String,
    val downloadUrl: String,
    val expiresAt: String,
    val encrypted: Boolean = false,
    val encryptionKey: String? = null,
    val iv: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null
)

data class MediaDownloadDeniedEventData(
    val requestId: String,
    val mediaId: String,
    val reason: String
)

data class UserProfileUpdatedEventData(
    val userId: String,
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val status: String = "",
    val profileImageUrl: String = "",
    val avatar: String = "",
    val pronouns: String = "",
    val privacyLevel: String = "",
    val lastUpdated: String = ""
)

data class UserStatusEventData(
    val userId: String,
    val username: String? = null,
    val lastSeen: String? = null,
    val status: String
)
