package com.freetime.app.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.freetime.app.data.network.WebSocketEventListener
import com.freetime.app.data.network.WebSocketEvent
import com.freetime.app.data.network.FriendRequestEventData
import com.freetime.app.data.network.GroupVoteInitiatedEventData
import com.freetime.app.data.network.ChannelMessageEventData
import com.freetime.app.data.network.MediaDownloadRequestedEventData
import com.freetime.app.data.network.UserProfileUpdatedEventData
import com.freetime.app.data.network.MediaDownloadApprovedEventData
import com.freetime.app.data.network.UserStatusEventData
import kotlinx.coroutines.CoroutineScope as KoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * WebSocket Event Handler Repository
 * Manages real-time event streams for:
 * - Friend request notifications
 * - Group voting updates
 * - Channel message updates
 * - Media download status
 * - User online/offline status
 */
class WebSocketEventRepository(
    private val authToken: String,
    private val coroutineScope: KoroutineScope
) {
    companion object {
        private const val TAG = "WebSocketEventRepo"
    }

    private var eventListener: WebSocketEventListener? = null

    // ============== FRIEND EVENTS ==============
    private val _friendRequestReceived = MutableLiveData<FriendRequestEventData>()
    val friendRequestReceived: LiveData<FriendRequestEventData> = _friendRequestReceived

    private val _friendRequestAccepted = MutableLiveData<String>() // UserId
    val friendRequestAccepted: LiveData<String> = _friendRequestAccepted

    private val _friendRequestRejected = MutableLiveData<String>() // UserId
    val friendRequestRejected: LiveData<String> = _friendRequestRejected

    private val _friendRequestCanceled = MutableLiveData<String>() // UserId
    val friendRequestCanceled: LiveData<String> = _friendRequestCanceled

    // ============== GROUP EVENTS ==============
    private val _groupCreated = MutableLiveData<Map<String, Any>>()
    val groupCreated: LiveData<Map<String, Any>> = _groupCreated

    private val _groupVoteInitiated = MutableLiveData<GroupVoteInitiatedEventData>()
    val groupVoteInitiated: LiveData<GroupVoteInitiatedEventData> = _groupVoteInitiated

    private val _groupVoteCast = MutableLiveData<Map<String, Any>>()
    val groupVoteCast: LiveData<Map<String, Any>> = _groupVoteCast
    val voteCast: LiveData<Map<String, Any>> = _groupVoteCast  // Alias for UIViewModels

    private val _groupDeleted = MutableLiveData<String>() // GroupId
    val groupDeleted: LiveData<String> = _groupDeleted

    // ============== CHANNEL EVENTS ==============
    private val _channelCreated = MutableLiveData<Map<String, Any>>()
    val channelCreated: LiveData<Map<String, Any>> = _channelCreated

    private val _channelMessageReceived = MutableLiveData<ChannelMessageEventData>()
    val channelMessageReceived: LiveData<ChannelMessageEventData> = _channelMessageReceived

    private val _channelMessageDeleted = MutableLiveData<Map<String, String>>()
    val channelMessageDeleted: LiveData<Map<String, String>> = _channelMessageDeleted

    private val _memberPromoted = MutableLiveData<Map<String, String>>()
    val memberPromoted: LiveData<Map<String, String>> = _memberPromoted

    private val _memberDemoted = MutableLiveData<Map<String, String>>()
    val memberDemoted: LiveData<Map<String, String>> = _memberDemoted

    // ============== MEDIA EVENTS ==============
    private val _mediaDownloadRequested = MutableLiveData<MediaDownloadRequestedEventData>()
    val mediaDownloadRequested: LiveData<MediaDownloadRequestedEventData> = _mediaDownloadRequested

    private val _mediaDownloadApproved = MutableLiveData<Map<String, String>>()
    val mediaDownloadApproved: LiveData<Map<String, String>> = _mediaDownloadApproved

    private val _mediaDownloadDenied = MutableLiveData<Map<String, String>>()
    val mediaDownloadDenied: LiveData<Map<String, String>> = _mediaDownloadDenied

    // ============== PROFILE EVENTS ==============
    private val _profileUpdated = MutableLiveData<UserProfileUpdatedEventData>()
    val profileUpdated: LiveData<UserProfileUpdatedEventData> = _profileUpdated

    // ============== STATUS EVENTS ==============
    private val _userOnline = MutableLiveData<String>() // UserId
    val userOnline: LiveData<String> = _userOnline

    private val _userOffline = MutableLiveData<String>() // UserId
    val userOffline: LiveData<String> = _userOffline

    // ============== CONNECTION EVENTS ==============
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _errorState = MutableLiveData<String>()
    val errorState: LiveData<String> = _errorState

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    /**
     * Connect to WebSocket and start listening for events
     */
    fun connect() {
        try {
            _connectionState.value = ConnectionState.CONNECTING

            eventListener = WebSocketEventListener(
                authToken = authToken,
                coroutineScope = coroutineScope,
                onEventReceived = { event -> handleEvent(event) },
                onConnected = { handleConnected() },
                onDisconnected = { handleDisconnected() },
                onError = { error -> handleError(error) }
            )

            eventListener?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            handleError("Connection failed: ${e.message}")
        }
    }

    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        try {
            eventListener?.disconnect()
            eventListener = null
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    /**
     * Handle incoming WebSocket event
     */
    private fun handleEvent(event: WebSocketEvent) {
        Log.d(TAG, "Event received: ${event.type}")

        when (event.type) {
            // Friend events
            "friend.request.received" -> {
                val data = event.data as? FriendRequestEventData
                if (data != null) {
                    _friendRequestReceived.value = data
                }
            }

            "friend.request.accepted" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    _friendRequestAccepted.value = data["userId"] as? String
                }
            }

            "friend.request.rejected" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    _friendRequestRejected.value = data["userId"] as? String
                }
            }

            // Group voting events
            "group_created" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _groupCreated.value = data as Map<String, Any>
                    Log.d(TAG, "✓ Group created event received: ${data["name"]}")
                }
            }

            "group.vote.initiated" -> {
                val data = event.data as? GroupVoteInitiatedEventData
                if (data != null) {
                    _groupVoteInitiated.value = data
                }
            }

            "group.vote.cast" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _groupVoteCast.value = data as Map<String, Any>
                }
            }

            "group.deleted" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    _groupDeleted.value = data["groupId"] as? String
                }
            }

            // Channel events
            "channel_created" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _channelCreated.value = data as Map<String, Any>
                    Log.d(TAG, "✓ Channel created event received: ${data["name"]}")
                }
            }

            "channel.message.received" -> {
                val data = event.data as? ChannelMessageEventData
                if (data != null) {
                    _channelMessageReceived.value = data
                }
            }

            "channel.message.deleted" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _channelMessageDeleted.value = data as Map<String, String>
                }
            }

            "channel.member.promoted" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _memberPromoted.value = data as Map<String, String>
                }
            }

            "channel.member.demoted" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _memberDemoted.value = data as Map<String, String>
                }
            }

            // Media events
            "media.download.requested" -> {
                val data = event.data as? MediaDownloadRequestedEventData
                if (data != null) {
                    _mediaDownloadRequested.value = data
                }
            }

            "media.download.approved" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _mediaDownloadApproved.value = data as Map<String, String>
                }
            }

            "media.download.denied" -> {
                val data = event.data as? Map<*, *>
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    _mediaDownloadDenied.value = data as Map<String, String>
                }
            }

            // Profile update events (when friends update their profile)
            "user:profile-updated", "profileUpdated" -> {
                val data = event.data as? UserProfileUpdatedEventData
                if (data != null) {
                    Log.d(TAG, "📍 Friend profile updated: ${data.userId} (displayName: ${data.displayName}, avatar: ${data.avatar})")
                    _profileUpdated.value = data
                }
            }

            // User status events
            "user.online" -> {
                val data = event.data as? UserStatusEventData
                if (data != null) {
                    _userOnline.value = data.userId
                }
            }

            "user.offline" -> {
                val data = event.data as? UserStatusEventData
                if (data != null) {
                    _userOffline.value = data.userId
                }
            }

            else -> {
                Log.w(TAG, "Unknown event type: ${event.type}")
            }
        }
    }

    private fun handleConnected() {
        Log.d(TAG, "WebSocket connected")
        _connectionState.value = ConnectionState.CONNECTED
    }

    private fun handleDisconnected() {
        Log.d(TAG, "WebSocket disconnected")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleError(error: String) {
        Log.e(TAG, "WebSocket error: $error")
        _connectionState.value = ConnectionState.ERROR
        _errorState.value = error
    }

    /**
     * Check if WebSocket is currently connected
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * Get current connection state
     */
    fun getConnectionState(): ConnectionState {
        return _connectionState.value ?: ConnectionState.DISCONNECTED
    }
}
