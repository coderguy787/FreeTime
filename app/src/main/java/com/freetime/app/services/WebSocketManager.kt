package com.freetime.app.services

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
// ✅ OkHttp imports for Socket.IO SSL configuration
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * WebSocket Manager for Real-Time Communication
 * Uses the official Socket.IO client library.
 * Handles:
 * - Real-time messages
 * - Call signaling (initiation, answer, ICE, etc.)
 * - Connection lifecycle management
 */

// ✅ Connection state for UI display
enum class ConnectionState {
    DISCONNECTED,      // Not connected, not trying
    CONNECTING,        // Attempting to connect
    CONNECTED,         // Successfully connected
    RECONNECTING,      // Attempting to reconnect after disconnect
    FAILED             // Failed to connect after retries
}

class WebSocketManager private constructor() {

    companion object {
        private const val TAG = "SocketIOManager"

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }

    private var mSocket: Socket? = null
    private val listeners = CopyOnWriteArrayList<WebSocketListener>()
    
    // ✅ STORE USERID FOR SOCKET.IO ROOM JOINING
    private var currentUserId: String = ""
    
    // ✅ DUPLICATE MESSAGE DETECTION: Track recently processed message IDs to prevent
    // duplicates from dual Socket.IO paths (primary polling + secondary WebSocket).
    // Entries expire after 10 seconds.
    private val recentMessageIds = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    private fun isDuplicateMessage(messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val stale = now - 10_000L
        // Clean stale entries opportunistically
        if (recentMessageIds.size > 500) {
            recentMessageIds.entries.removeAll { it.value < stale }
        }
        val existing = recentMessageIds.putIfAbsent(messageId, now)
        return existing != null && existing > stale
    }
    
    // ✅ COROUTINE SCOPE for non-blocking timeout + proper cancellation
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    
    // ✅ TIMEOUT JOB: Can be cancelled if connection succeeds before timeout
    private var connectionTimeoutJob: Job? = null
    
    // ✅ OBSERVABLE CONNECTION STATE for UI
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // ✅ Get current connection state
    fun getConnectionState(): ConnectionState = _connectionState.value
    
    // ✅ Update connection state (internal use only)
    private fun setConnectionState(state: ConnectionState) {
        Log.d(TAG, "📊 Connection State: ${_connectionState.value} → $state")
        _connectionState.value = state
    }

    /**
     * WebSocket Event Listener Interface
     */
    interface WebSocketListener {
        fun onNewMessage(message: MessageData) {}
        fun onGroupMessage(message: GroupMessageData) {}
        fun onChannelMessage(message: ChannelMessageData) {}
        fun onUserTyping(typingData: TypingData) {}
        fun onMessageRead(readData: ReadReceiptData) {}
        fun onConversationAllRead(readData: ConversationReadData) {}
        fun onIncomingCall(callData: IncomingCallData) {}
        fun onCallAnswered(callData: CallAnsweredData) {}
        fun onCallRejected(callData: CallRejectedData) {}
        fun onCallEnded(callData: CallEndedData) {}
        fun onIceCandidate(iceData: IceCandidateData) {}
        fun onUserStatusChanged(statusData: UserStatusData) {}
        fun onReactionReceived(reactionData: ReactionData) {}
        fun onNotificationReceived(data: Map<String, Any>) {}  // ✅ NEW: Handle incoming notifications from Socket.IO
        fun onReactionRemoved(reactionData: ReactionData) {}
        // ✅ NEW: GROUP message reactions (visible to all group members)
        fun onGroupReactionReceived(reactionData: ReactionData) {}
        fun onGroupReactionRemoved(reactionData: ReactionData) {}
        fun onProfileUpdated(profileData: ProfileUpdatedData) {}
        
        // 🔧 NEW: Avatar Update Event (real-time profile picture sync)
        fun onAvatarUpdated(data: AvatarUpdatedData) {}
        
        // Friend System Events
        fun onFriendRequestReceived(data: FriendRequestEventData) {}
        fun onFriendRequestAccepted(data: FriendRequestEventData) {}
        fun onFriendRequestRejected(data: FriendRequestEventData) {}
        fun onFriendRequestCanceled(data: FriendRequestEventData) {}
        
        // Group & Channel Events
        fun onGroupMemberJoined(data: GroupMemberActionData) {}
        fun onGroupMemberLeft(data: GroupMemberActionData) {}
        fun onGroupMemberPromoted(data: GroupMemberActionData) {}
        fun onGroupMemberRemoved(data: GroupMemberActionData) {}
        fun onGroupUpdated(data: GroupUpdatedData) {}
        fun onGroupInviteReceived(data: GroupInviteData) {}
        // ✅ NEW: Group custom picture events
        fun onGroupPictureUpdated(data: GroupPictureUpdatedData) {}
        fun onGroupPictureRemoved(data: GroupPictureRemovedData) {}
        // ✅ NEW: Group name update event
        fun onGroupNameUpdated(data: GroupNameUpdatedData) {}
        
        // Group Voting Events
        fun onGroupVoteInitiated(data: GroupVoteInitiatedData) {}
        fun onGroupVoteCast(data: GroupVoteCastData) {}
        fun onGroupDeleted(data: GroupDeletedData) {}
        
        // Media Download Events
        fun onMediaDownloadRequested(data: MediaDownloadRequestData) {}
        fun onMediaDownloadApproved(data: MediaDownloadResponseData) {}
        fun onMediaDownloadDenied(data: MediaDownloadResponseData) {}
        
        // Status & Connection
        fun onUserOnline(data: UserPresenceData) {}
        fun onUserOffline(data: UserPresenceData) {}
        fun onConnectionEstablished() {}
        fun onConnectionLost() {}
        fun onError(error: String) {}
        // ✅ NEW: Handle session terminated (concurrent login prevention)
        fun onSessionTerminated(reason: String, newDeviceName: String, message: String) {}
        fun onChatHistoryDeleted(data: ChatHistoryDeletedData) {}
        fun onUserProfileUpdated(data: UserProfileUpdatedData) {}
        fun onNotificationReceived(data: InternalNotificationData) {}
        
        // Legacy/Compatibility
        fun onFriendRequestAutoAccepted(data: FriendRequestAutoAcceptedData) {}
        fun onFriendAdded(data: FriendAddedData) {}
        fun onFriendRemoved(data: FriendRemovedData) {}
    }

    // region Data Classes
    data class MessageData(
        val messageId: String,
        val senderId: String,
        val senderUsername: String,
        val senderDisplayName: String? = null,
        val recipientId: String,
        val content: String,
        val createdAt: Long,
        val media: String? = null,
        val mediaType: String? = null,
        val mediaName: String? = null,
        val senderAvatar: String? = null,
        val mediaShareMode: String? = null, // ✅ NEW: "public" (unencrypted) or "protected" (encrypted)
        val replyToMessageId: String? = null,
        val replyToUsername: String? = null,
        val replyToText: String? = null,
        val subject: String? = null  // ✅ NEW: Announcement subject
    )
    data class GroupMessageData(
        val messageId: String,
        val groupId: String,
        val senderId: String,
        val senderUsername: String,
        val content: String,
        val createdAt: Long,
        val senderAvatar: String? = null,  // ✅ NEW: Profile picture from backend
        val mediaShareMode: String? = null, // ✅ NEW: "public" (unencrypted) or "protected" (encrypted)
        val mediaId: String? = null,
        val mediaType: String? = null,
        val mediaName: String? = null,
        // ✅ REPLY SUPPORT: Fields for WhatsApp-style replies
        val replyToMessageId: String? = null,
        val replyToUsername: String? = null,
        val replyToText: String? = null
    )
    data class ChannelMessageData(val messageId: String, val channelId: String, val senderId: String, val senderUsername: String, val content: String, val createdAt: Long)
    data class TypingData(val userId: String, val username: String, val recipientId: String, val isTyping: Boolean, val timestamp: Long)
    data class ReadReceiptData(val messageId: String, val readBy: String, val username: String, val readAt: Long, val conversationId: String)
    data class ConversationReadData(val conversationId: String, val readBy: String, val username: String, val readAt: Long, val allMessages: Boolean, val messageCount: Int)
    data class IncomingCallData(val callId: String, val callerId: String, val callerUsername: String, val callType: String, val sdpOffer: String, val timestamp: Long, val callerAvatar: String? = null)
    data class CallAnsweredData(val callId: String, val answer: String, val answeredBy: String? = null, val answeredAt: Long = System.currentTimeMillis())
    data class CallRejectedData(val callId: String, val reason: String, val rejectedBy: String)
    data class CallEndedData(val callId: String, val endedBy: String? = null, val duration: Long? = null, val endedAt: Long = System.currentTimeMillis())
    data class IceCandidateData(val callId: String, val from: String, val candidate: String)
    data class UserStatusData(val userId: String, val isOnline: Boolean, val lastActivityAt: Long, val actualStatus: String, val lastSeen: String)
    data class ReactionData(val messageId: String, val emoji: String, val userId: String, val username: String, val groupId: String = "")  // ✅ Added groupId for group reactions
    data class ProfileUpdatedData(val userId: String, val field: String, val value: String, val timestamp: Long)
    
    // 🔧 NEW: Avatar Update Data Class
    data class AvatarUpdatedData(
        val userId: String,
        val username: String,
        val avatarUrl: String,
        val imageId: String,
        val fileName: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    // ✅ MUTUAL FRIEND AUTO-ACCEPT: Data for auto-accepted friend requests
    data class FriendRequestAutoAcceptedData(val userId: String, val username: String, val avatar: String = "", val friendshipId: String? = null)
    // ✅ REAL-TIME FRIEND LIST SYNC: Data for friend list updates
    data class FriendAddedData(val userId: String, val username: String, val avatar: String? = null, val status: String = "offline")
    data class FriendRemovedData(val removedFriendId: String)
    data class ChatHistoryDeletedData(val deletedBy: String, val recipientId: String)
    // ✅ GROUP MEMBER EVENTS: Data for member actions in groups
    data class GroupMemberActionData(
        val groupId: String,
        val userId: String,
        val username: String,
        val displayName: String = "",
        val avatar: String? = null,
        val action: String, // "joined", "left", "promoted", "removed"
        val actor: String? = null, // Who performed the action (for kick/promote)
        val timestamp: Long = System.currentTimeMillis()
    )
    data class UserProfileUpdatedData(
        val userId: String,
        val bio: String? = null,
        val status: String? = null,
        val privacyLevel: String? = null,
        val pronouns: String? = null,
        val avatar: String? = null,
        val profileImageUrl: String? = null,
        val lastUpdated: String? = null
    )
    
    // Friend System Events
    data class FriendRequestEventData(
        val requestId: String,
        val senderId: String,
        val senderName: String,
        val recipientId: String,
        val status: String,
        val timestamp: String,
        val senderAvatar: String? = null
    )
    
    // Group & Channel Events
    data class GroupUpdatedData(val groupId: String, val name: String? = null, val description: String? = null, val avatar: String? = null)
    data class GroupInviteData(val inviteId: String, val groupId: String, val groupName: String, val inviterId: String, val inviterName: String)
    
    // Group Voting Events
    data class GroupVoteInitiatedData(val voteId: String, val groupId: String, val title: String, val options: List<String>, val createdBy: String)
    data class GroupVoteCastData(val voteId: String, val userId: String, val optionIndex: Int)
    data class GroupDeletedData(val groupId: String, val deletedBy: String)
    
    // ✅ NEW: Group custom picture data
    data class GroupPictureUpdatedData(val groupId: String, val pictureUrl: String, val updatedBy: String, val updatedAt: String)
    data class GroupPictureRemovedData(val groupId: String, val removedBy: String, val removedAt: String)
    // ✅ NEW: Group name update data
    data class GroupNameUpdatedData(val groupId: String, val name: String, val updatedBy: String, val updatedAt: String)
    
    // Media Download Events
    data class MediaDownloadRequestData(val requestId: String, val mediaId: String, val requesterId: String, val requesterName: String)
    data class MediaDownloadResponseData(
        val requestId: String,
        val mediaId: String,
        val approved: Boolean,
        val message: String? = null,
        // ✅ NEW: Download details from approval
        val downloadUrl: String? = null,
        val encryptionKey: String? = null,
        val iv: String? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val size: Long? = null,
        val encrypted: Boolean = false,
        val expiresAt: Long? = null
    )
    
    // Status & Connection
    data class UserPresenceData(val userId: String, val username: String, val timestamp: String)
    data class InternalNotificationData(val title: String, val body: String, val data: JSONObject, val timestamp: Long)
    
    // ==================== DUAL-SERVICE SUPPORT ====================
    // mSocket = Primary connection (polling on 443, guaranteed)
    // mWebSocketSocket = Secondary connection (WebSocket on 8080, low-latency)
    // Both run simultaneously. Whichever connects first becomes primary signal receiver.
    private var mWebSocketSocket: Socket? = null
    private var isConnectedViaPrimary = false
    private var isConnectedViaWebSocket = false
    
    // ✅ OkHttp client for Socket.IO connections with proper SSL/TLS
    private val okHttpClient: OkHttpClient by lazy { createSocketIOHttpClient() }
    
    // ✅ EVENT QUEUE: Buffer events while socket is connecting
    private data class PendingEvent(val eventName: String, val data: JSONObject, val timestamp: Long = System.currentTimeMillis())
    private val pendingEvents = CopyOnWriteArrayList<PendingEvent>()
    
    // ✅ CONNECTION SYNCHRONIZATION: Wait for connection before sending
    private val connectionLock = java.util.concurrent.locks.ReentrantLock()
    private val connectionCondition = connectionLock.newCondition()
    
    // endregion

    fun connect(serverUrl: String, token: String, userId: String = "") {
        if (mSocket?.connected() == true) {
            Log.d(TAG, "Socket is already connected.")
            return
        }

        // ✅ STORE USER ID FOR ROOM JOINING AFTER CONNECTION
        currentUserId = userId

        // ✅ SET STATE: Mark that we're attempting to connect
        setConnectionState(ConnectionState.CONNECTING)

        try {
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "  🔗 SOCKET.IO DUAL-SERVICE CONNECTION ATTEMPT")
            Log.i(TAG, "  Primary URL: $serverUrl (polling on 443)")
            Log.i(TAG, "  Secondary URL: WebSocket on 8080")
            Log.i(TAG, "  Time: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(System.currentTimeMillis())}")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            
            // ✅ CRITICAL FIX: Configure global SSL context for Socket.IO
            // Socket.IO library doesn't allow direct client configuration,
            // so we set the system default SSL context which it will use
            Log.d(TAG, "📋 Configuring Global SSL Context:")
            Log.d(TAG, "   Context: Default JVM SSL (TLSv1.2)")

            Log.d(TAG, "   Trust: All certificates (self-signed support)")
            try {
                val trustAllCerts = arrayOf<TrustManager>(createTrustAllX509TrustManager())
                val defaultSslContext = SSLContext.getInstance("TLSv1.2")
                defaultSslContext.init(null, trustAllCerts, java.security.SecureRandom())
                SSLContext.setDefault(defaultSslContext)
                Log.d(TAG, "   ✅ Global SSL context configured successfully")
            } catch (e: Exception) {
                Log.e(TAG, "   ⚠️  Failed to set global SSL context: ${e.message}")
            }
            
            // ==================== PRIMARY SERVICE: Polling on Port 443 ====================
            // This is the reliable connection using HTTP polling
            
            // ✅ DEBUG: Log token status BEFORE creating options
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "🔴 CRITICAL: Token is NULL or EMPTY! Cannot authenticate Socket.IO connection")
                Log.e(TAG, "   This will cause 'xhr poll error' - user may not be logged in")
                setConnectionState(ConnectionState.FAILED)
                notifyListenerError("Not authenticated: token missing")
                return
            }
            Log.d(TAG, "✅ Token validation: Token present (${token.length} chars)")
            Log.d(TAG, "   Token starts with: ${token.take(20)}...")
            
            val primaryOpts = IO.Options().apply {
                reconnection = true
                reconnectionDelay = 1000  // Start retry at 1s
                reconnectionDelayMax = 10000  // Max retry delay 10s
                reconnectionAttempts = Int.MAX_VALUE
                
                // ✅ CRITICAL FIX: Attach our custom OkHttpClient to Socket.IO
                // This client trusts self-signed certificates and has correct timeouts
                callFactory = okHttpClient
                webSocketFactory = okHttpClient
                
                // ✅ AUTHENTICATION: Pass token in query parameters with proper URL encoding
                // Socket.IO polling needs token accessible via query string for initial handshake
                query = "token=${java.net.URLEncoder.encode(token, "UTF-8")}"
                // Also try auth object for WebSocket compatibility
                auth = mapOf("token" to token)
                // 🔧 OPTIMIZED TRANSPORTS: Allow both for better performance
                // We start with polling (reliable) and library upgrades to WebSocket
                transports = arrayOf("polling", "websocket")
                
                // 🔥 MASSIVE INCREASE: Socket.IO v2.1.1 polling hangs on 2nd request
                // Setting timeout VERY high to work around server-side long-polling holdopen
                timeout = 120000  // 120 seconds (was 20s) - Socket.IO long-polls for events
            }

            Log.d(TAG, "📋 Socket.IO Configuration:")
            Log.d(TAG, "   URL: $serverUrl")
            Log.d(TAG, "   Transport: polling (HTTPS long-polling)")
            Log.d(TAG, "   Purpose: Stable, guaranteed connection")
            Log.d(TAG, "   Timeout: 120s (increased for v2.1.1 polling compatibility)")
            Log.d(TAG, "   Auth: JWT token in query string (URL-encoded)")
            Log.d(TAG, "   Query: token=${java.net.URLEncoder.encode(token, "UTF-8").take(30)}...")
            Log.d(TAG, "   Note: WebSocket disabled, polling-only mode for stability")
            
            mSocket = IO.socket(serverUrl, primaryOpts)
            setupEventListeners()
            mSocket?.connect()
            Log.w(TAG, "⏳ Socket.IO Polling connection started on $serverUrl (120s timeout)...")
            
            // ✅ TIMEOUT HANDLER: Cancel if polling doesn't connect within 120 seconds
            // Socket.IO v2.1.1 long-polls every 25-30 seconds, so need long timeout
            connectionTimeoutJob?.cancel()
            
            connectionTimeoutJob = scope.launch {
                delay(120000)  // 120 seconds timeout for polling connection
                if (_connectionState.value == ConnectionState.CONNECTING) {
                    Log.w(TAG, "⏰ CONNECTION TIMEOUT: Polling didn't connect in 120s, will retry...")
                    setConnectionState(ConnectionState.RECONNECTING)
                }
            }

        } catch (e: URISyntaxException) {
            Log.e(TAG, "URISyntaxException: ${e.reason}", e)
            setConnectionState(ConnectionState.FAILED)
            notifyListenerError("Invalid WebSocket URL.")
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}", e)
            setConnectionState(ConnectionState.FAILED)
            notifyListenerError("An unexpected error occurred during connection.")
            Log.e(TAG, "Connection error: ${e.message}", e)
            notifyListenerError("An unexpected error occurred during connection.")
        }
    }

    private fun setupEventListeners() {
        // ✅ CRITICAL FIX: Remove existing listeners to prevent accumulation on reconnect
        mSocket?.off()  // Remove all listeners
        
        // ✅ Valid connection state listeners
        mSocket?.on(Socket.EVENT_CONNECT) {
            // ✅ CANCEL TIMEOUT: Connection succeeded, no need for timeout anymore
            connectionTimeoutJob?.cancel()
            
            // ✅ SET STATE: We're now connected
            setConnectionState(ConnectionState.CONNECTED)
            
            // ✅ SIGNAL CONNECTION EVENT: Wake up any threads waiting for connection
            connectionLock.lock()
            try {
                connectionCondition.signalAll()
                Log.d(TAG, "📢 Connection condition signaled to waiting threads")
            } finally {
                connectionLock.unlock()
            }
            
            // ✅ CRITICAL FIX: JOIN USER ROOM FOR INCOMING CALLS/BROADCASTS
            // Server broadcasts to 'user:{userId}' room, app must join to receive
            if (currentUserId.isNotEmpty()) {
                val joinData = mapOf("userId" to currentUserId)
                mSocket?.emit("join", joinData)
                Log.i(TAG, "✅ JOINED SOCKET.IO ROOM: user:$currentUserId")
            } else {
                Log.w(TAG, "⚠️  WARNING: userId is empty, cannot join user room for incoming calls")
            }
            
            // ✅ FLUSH PENDING EVENTS: Send any events that were queued while disconnected
            flushPendingEvents()
            
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "✅ SOCKET.IO CONNECTED")
            Log.i(TAG, "  Socket ID: ${mSocket?.id()}")
            Log.i(TAG, "  Status: Connected[true], Ready for events")
            Log.i(TAG, "  Pending events flushed: ${pendingEvents.size} remaining")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            logConnectionDiagnostics("SUCCESS", "Connection established")
            listeners.forEach { it.onConnectionEstablished() }
        }
        
        mSocket?.on(Socket.EVENT_DISCONNECT) { reason ->
            // ✅ SET STATE: Disconnected, but reconnection may be in progress
            setConnectionState(ConnectionState.RECONNECTING)
            
            Log.w(TAG, "═══════════════════════════════════════════════════════")
            Log.w(TAG, "❌ SOCKET.IO DISCONNECTED")
            Log.w(TAG, "  Reason: $reason")
            Log.w(TAG, "  Status: Reconnecting in 2-30s...")
            Log.w(TAG, "═══════════════════════════════════════════════════════")
            logConnectionDiagnostics("DISCONNECT", reason.toString())
            listeners.forEach { it.onConnectionLost() }
        }
        
        // ✅ CRITICAL FIX: Handle successful reconnection (not just initial CONNECT)
        // Socket.IO emits "reconnect" event when auto-reconnecting after a disconnect
        mSocket?.on("reconnect") {
            // ✅ SET STATE: Successfully reconnected after disconnect
            setConnectionState(ConnectionState.CONNECTED)
            
            // ✅ REJOIN USER ROOM AFTER RECONNECTION
            if (currentUserId.isNotEmpty()) {
                val joinData = mapOf("userId" to currentUserId)
                mSocket?.emit("join", joinData)
                Log.i(TAG, "✅ REJOINED SOCKET.IO ROOM AFTER RECONNECT: user:$currentUserId")
            }
            
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "✅ SOCKET.IO SUCCESSFULLY RECONNECTED")
            Log.i(TAG, "  Socket ID: ${mSocket?.id()}")
            Log.i(TAG, "  Status: Reconnection successful, Ready for events")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            logConnectionDiagnostics("RECONNECT", "Auto-reconnection successful")
            listeners.forEach { it.onConnectionEstablished() }
        }
        
        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.getOrNull(0)?.toString() ?: "Unknown connection error"
            
            // ✅ SET STATE: Connection error - will be retried (state = RECONNECTING)
            // Only set to FAILED after exhausting retries (which we don't track yet)
            setConnectionState(ConnectionState.RECONNECTING)
            
            Log.e(TAG, "═══════════════════════════════════════════════════════")
            Log.e(TAG, "⚠️  CONNECTION ERROR (Retrying...)")
            Log.e(TAG, "  Error Type: ${error.split(":").firstOrNull() ?: "Unknown"}")
            Log.e(TAG, "  Full Error: $error")
            
            // ✅ POLLING SPECIFIC DIAGNOSTICS
            if (error.contains("xhr", ignoreCase = true) || 
                error.contains("polling", ignoreCase = true) || 
                error.contains("http", ignoreCase = true)) {
                Log.e(TAG, "  Transport: XHR Long-Polling (HTTP)")
                Log.e(TAG, "  Diagnosis Steps:")
                Log.e(TAG, "    1. Check: Is server reachable? (curl http://server:port/socket.io/?EIO=4&transport=polling)")
                Log.e(TAG, "    2. Check: CORS configured? (Access-Control-Allow-Origin must include client domain)")
                Log.e(TAG, "    3. Check: Socket.IO middleware allowing polling? (transports: ['polling', 'websocket'])")
                Log.e(TAG, "    4. Check: Firewall/proxy blocking long-polling? (keep-alive required)")
                Log.e(TAG, "    5. Monitor: Server logs for /socket.io polling requests")
                logConnectionDiagnostics("POLLING_ERROR", "Polling failed: $error")
            } else if (error.contains("websocket", ignoreCase = true) || 
                       error.contains("ws://", ignoreCase = true) ||
                       error.contains("wss://", ignoreCase = true)) {
                Log.e(TAG, "  Transport: WebSocket (WSS)")
                Log.e(TAG, "  Status: WebSocket upgrade failed, will retry with polling...")
                logConnectionDiagnostics("WEBSOCKET_ERROR", "Will fallback to polling: $error")
            }
            Log.e(TAG, "═══════════════════════════════════════════════════════")
        }
        
        // ✅ Specific error listeners on transport layer
        mSocket?.on("error") { args ->
            val error = args.getOrNull(0)?.toString() ?: "Unknown socket error"
            Log.w(TAG, "⚠️  Socket-level error: $error")
        }

        // Custom event listeners
        mSocket?.on("incomingCall") { args -> handleEvent(args, ::handleIncomingCall) }
        mSocket?.on("callAnswered") { args -> handleEvent(args, ::handleCallAnswered) }
        mSocket?.on("callRejected") { args -> handleEvent(args, ::handleCallRejected) }
        mSocket?.on("callEnded") { args -> handleEvent(args, ::handleCallEnded) }
        mSocket?.on("iceCandidate") { args -> handleEvent(args, ::handleIceCandidate) }
        
        // Messaging
        mSocket?.on("newMessage") { args -> handleEvent(args, ::handleNewMessage) }
        mSocket?.on("group.message.received") { args -> handleEvent(args, ::handleGroupMessage) }
        mSocket?.on("channel.message.received") { args -> handleEvent(args, ::handleChannelMessage) }
        mSocket?.on("user_typing") { args -> handleEvent(args, ::handleUserTyping) }
        mSocket?.on("message:reaction:added") { args -> handleEvent(args, ::handleReactionAdded) }
        mSocket?.on("message:reaction:remove") { args -> handleEvent(args, ::handleReactionRemoved) }
        // ✅ GROUP REACTIONS: Real-time reaction updates for group messages visible to all members
        mSocket?.on("group:message:reaction:added") { args -> handleEvent(args, ::handleGroupReactionAdded) }
        mSocket?.on("group:message:reaction:removed") { args -> handleEvent(args, ::handleGroupReactionRemoved) }
        mSocket?.on("chat.history.deleted") { args -> handleEvent(args, ::handleChatHistoryDeleted) }
        mSocket?.on("chatHistoryDeleted") { args -> handleEvent(args, ::handleChatHistoryDeleted) }  // ✅ NEW: camelCase version
        mSocket?.on("profile_updated") { args -> handleEvent(args, ::handleProfileUpdated) }
        
        // 🔧 NEW: Avatar Update Events (real-time profile picture sync)
        mSocket?.on("avatar.updated") { args -> handleEvent(args, ::handleAvatarUpdated) }
        mSocket?.on("user.avatar.updated") { args -> handleEvent(args, ::handleAvatarUpdated) }
        
        // Friend System
        mSocket?.on("friend.request.received") { args -> handleEvent(args, ::handleFriendRequestReceived) }
        mSocket?.on("friend.request.accepted") { args -> handleEvent(args, ::handleFriendRequestAccepted) }
        mSocket?.on("friend.request.rejected") { args -> handleEvent(args, ::handleFriendRequestRejected) }
        mSocket?.on("friend.request.canceled") { args -> handleEvent(args, ::handleFriendRequestCanceled) }
        mSocket?.on("friend.added") { args -> handleEvent(args, ::handleFriendAdded) }
        mSocket?.on("friend.removed") { args -> handleEvent(args, ::handleFriendRemoved) }
        mSocket?.on("friend.request.auto_accepted") { args -> handleEvent(args, ::handleFriendRequestAutoAccepted) }
        
        // Group & Channel Management
        mSocket?.on("group.member.joined") { args -> handleEvent(args, ::handleGroupMemberJoined) }
        mSocket?.on("group.member.left") { args -> handleEvent(args, ::handleGroupMemberLeft) }
        mSocket?.on("groupMemberLeft") { args -> handleEvent(args, ::handleGroupMemberLeft) }  // ✅ NEW: camelCase version
        mSocket?.on("group.member.promoted") { args -> handleEvent(args, ::handleGroupMemberPromoted) }
        mSocket?.on("group.member.removed") { args -> handleEvent(args, ::handleGroupMemberRemoved) }
        mSocket?.on("group.updated") { args -> handleEvent(args, ::handleGroupUpdated) }
        mSocket?.on("group_invite") { args -> handleEvent(args, ::handleGroupInvite) }
        
        // Group Voting
        mSocket?.on("group.vote.initiated") { args -> handleEvent(args, ::handleGroupVoteInitiated) }
        mSocket?.on("group.vote.cast") { args -> handleEvent(args, ::handleGroupVoteCast) }
        mSocket?.on("group.deleted") { args -> handleEvent(args, ::handleGroupDeleted) }
        mSocket?.on("group.clearHistory.voteStarted") { args -> handleEvent(args, ::handleNotificationReceived) }
        mSocket?.on("group.clearHistory.voteUpdated") { args -> handleEvent(args, ::handleNotificationReceived) }
        mSocket?.on("group.clearHistory.passed") { args -> handleEvent(args, ::handleNotificationReceived) }
        mSocket?.on("group.clearHistory.failed") { args -> handleEvent(args, ::handleNotificationReceived) }
        mSocket?.on("groupHistoryDeleted") { args -> handleEvent(args, ::handleNotificationReceived) }
        
        // ✅ NEW: Group custom picture updates (real-time sync)
        mSocket?.on("group:pictureUpdated") { args -> handleEvent(args, ::handleGroupPictureUpdated) }
        mSocket?.on("group:pictureRemoved") { args -> handleEvent(args, ::handleGroupPictureRemoved) }
        // ✅ NEW: Group name updates (real-time sync)
        mSocket?.on("group:nameUpdated") { args -> handleEvent(args, ::handleGroupNameUpdated) }
        
        // Media Downloads
        mSocket?.on("media.download.requested") { args -> handleEvent(args, ::handleMediaDownloadRequested) }
        mSocket?.on("media.download.approved") { args -> handleEvent(args, ::handleMediaDownloadApproved) }
        mSocket?.on("media.download.denied") { args -> handleEvent(args, ::handleMediaDownloadDenied) }
        
        // Presence & Status
        mSocket?.on("userStatusChanged") { args -> handleEvent(args, ::handleUserStatusChanged) }
        mSocket?.on("user.online") { args -> handleEvent(args, ::handleUserOnline) }
        mSocket?.on("user.offline") { args -> handleEvent(args, ::handleUserOffline) }
        mSocket?.on("user:profile-updated") { args -> handleEvent(args, ::handleUserProfileUpdated) }
        mSocket?.on("notification:received") { args -> handleEvent(args, ::handleNotificationReceived) }
        
        // ✅ NEW: Handle session termination (concurrent login prevention)
        mSocket?.on("session:terminated") { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) {
                args[0] as JSONObject
            } else {
                JSONObject()
            }
            
            val reason = data.optString("reason", "unknown")
            val newDeviceName = data.optJSONObject("newDeviceInfo")?.optString("deviceName", "Unknown Device") ?: "Unknown Device"
            val message = data.optString("message", "Your session has been terminated")
            
            Log.w(TAG, "🔐 SESSION TERMINATED: $reason from $newDeviceName")
            
            // Handle termination
            listeners.forEach { 
                it.onSessionTerminated(reason, newDeviceName, message)
            }
        }
        
        // ✅ NEW: Start keepalive heartbeat to prevent connection drops
        startKeepaliveHeartbeat()
    }

    /**
     * ✅ NEW: Keepalive Heartbeat Mechanism
     * Sends periodic heartbeat to server to prevent connection timeout
     * Server ignores unknown events, but headers keep connection alive
     */
    private var keepaliveJob: Job? = null
    
    private fun startKeepaliveHeartbeat() {
        // Cancel any existing keepalive job
        keepaliveJob?.cancel()
        
        // Start new keepalive loop (send heartbeat every 30 seconds)
        keepaliveJob = scope.launch {
            try {
                while (true) {
                    delay(30000)  // 30 seconds between heartbeats
                    
                    // Only send if socket is connected
                    if (mSocket?.connected() == true) {
                        mSocket?.emit("keepalive", JSONObject().apply {
                            put("timestamp", System.currentTimeMillis())
                        })
                        Log.d(TAG, "💓 Keepalive heartbeat sent (keeps connection alive)")
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "🛑 Keepalive heartbeat cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Keepalive heartbeat error: ${e.message}")
            }
        }
    }
    
    private fun stopKeepaliveHeartbeat() {
        keepaliveJob?.cancel()
        keepaliveJob = null
        Log.d(TAG, "🛑 Keepalive heartbeat stopped")
    }

    private fun handleEvent(args: Array<Any>, handler: (JSONObject) -> Unit) {
        if (args.isNotEmpty() && args[0] is JSONObject) {
            try {
                handler(args[0] as JSONObject)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event: ${e.message}", e)
            }
        }
    }

    // ==================== SECONDARY SERVICE EVENT LISTENERS ====================
    // Setup event listeners for the secondary WebSocket-only connection (port 8080)
    private fun setupSecondaryEventListeners() {
        // ✅ Remove existing listeners to prevent accumulation
        mWebSocketSocket?.off()
        
        // ✅ PRIMARY EVENT: Connection successful on WebSocket
        mWebSocketSocket?.on(Socket.EVENT_CONNECT) {
            // ✅ Track that we have WebSocket connection
            isConnectedViaWebSocket = true
            
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "✅ SECONDARY SERVICE CONNECTED (WebSocket on Port 8080)")
            Log.i(TAG, "  Socket ID: ${mWebSocketSocket?.id()}")
            Log.i(TAG, "  Transport: WebSocket (WSS)")
            Log.i(TAG, "  Status: Low-latency service ready")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            
            // If this connects before the primary, declare overall connection success
            if (!isConnectedViaPrimary) {
                setConnectionState(ConnectionState.CONNECTED)
                Log.i(TAG, "   ⚡ WebSocket connected first! Using low-latency transport.")
                listeners.forEach { it.onConnectionEstablished() }
            } else {
                Log.i(TAG, "   ℹ️  WebSocket connected (primary already active). Now redundant connection available.")
            }
        }
        
        // ✅ Disconnect event
        mWebSocketSocket?.on(Socket.EVENT_DISCONNECT) { reason ->
            isConnectedViaWebSocket = false
            Log.w(TAG, "⚠️  SECONDARY: WebSocket disconnected: $reason")
            
            // If primary is still connected, we're okay
            if (isConnectedViaPrimary) {
                Log.i(TAG, "   ℹ️  PRIMARY is still active. Using polling connection.")
            } else {
                setConnectionState(ConnectionState.RECONNECTING)
            }
        }
        
        // ✅ Successful reconnection
        mWebSocketSocket?.on("reconnect") {
            isConnectedViaWebSocket = true
            Log.i(TAG, "✅ SECONDARY: WebSocket reconnected (port 8080)")
        }
        
        // ✅ Connection error
        mWebSocketSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.getOrNull(0)?.toString() ?: "Unknown WebSocket error"
            isConnectedViaWebSocket = false
            
            Log.w(TAG, "⚠️  SECONDARY: WebSocket connection error: $error")
            
            // If primary is connected, we have fallback
            if (isConnectedViaPrimary) {
                Log.i(TAG, "   ℹ️  PRIMARY (polling) is still active. Continuing with HTTP polling.")
            }
        }
        
        // ✅ Error event
        mWebSocketSocket?.on("error") { args ->
            val error = args.getOrNull(0)?.toString() ?: "Unknown error"
            Log.w(TAG, "⚠️  SECONDARY: WebSocket error: $error")
        }
        
        // ✅ Relay events from secondary to registered listeners
        // These events will arrive via the lower-latency WebSocket connection
        mWebSocketSocket?.on("incomingCall") { args -> handleEvent(args, ::handleIncomingCall) }
        mWebSocketSocket?.on("callAnswered") { args -> handleEvent(args, ::handleCallAnswered) }
        mWebSocketSocket?.on("callRejected") { args -> handleEvent(args, ::handleCallRejected) }
        mWebSocketSocket?.on("callEnded") { args -> handleEvent(args, ::handleCallEnded) }
        mWebSocketSocket?.on("iceCandidate") { args -> handleEvent(args, ::handleIceCandidate) }
        
        // Messaging
        mWebSocketSocket?.on("newMessage") { args -> handleEvent(args, ::handleNewMessage) }
        mWebSocketSocket?.on("group.message.received") { args -> handleEvent(args, ::handleGroupMessage) }
        mWebSocketSocket?.on("channel.message.received") { args -> handleEvent(args, ::handleChannelMessage) }
        mWebSocketSocket?.on("user_typing") { args -> handleEvent(args, ::handleUserTyping) }
        mWebSocketSocket?.on("message:reaction:added") { args -> handleEvent(args, ::handleReactionAdded) }
        mWebSocketSocket?.on("message:reaction:remove") { args -> handleEvent(args, ::handleReactionRemoved) }
        // ✅ GROUP REACTIONS: Real-time reaction updates for group messages visible to all members
        mWebSocketSocket?.on("group:message:reaction:added") { args -> handleEvent(args, ::handleGroupReactionAdded) }
        mWebSocketSocket?.on("group:message:reaction:removed") { args -> handleEvent(args, ::handleGroupReactionRemoved) }
        mWebSocketSocket?.on("chat.history.deleted") { args -> handleEvent(args, ::handleChatHistoryDeleted) }
        mWebSocketSocket?.on("chatHistoryDeleted") { args -> handleEvent(args, ::handleChatHistoryDeleted) }  // ✅ camelCase alias used by server
        mWebSocketSocket?.on("profile_updated") { args -> handleEvent(args, ::handleProfileUpdated) }
        
        // 🔧 NEW: Avatar Update Events (real-time profile picture sync)
        mWebSocketSocket?.on("avatar.updated") { args -> handleEvent(args, ::handleAvatarUpdated) }
        mWebSocketSocket?.on("user.avatar.updated") { args -> handleEvent(args, ::handleAvatarUpdated) }
        
        // Friend System
        mWebSocketSocket?.on("friend.request.received") { args -> handleEvent(args, ::handleFriendRequestReceived) }
        mWebSocketSocket?.on("friend.request.accepted") { args -> handleEvent(args, ::handleFriendRequestAccepted) }
        mWebSocketSocket?.on("friend.request.rejected") { args -> handleEvent(args, ::handleFriendRequestRejected) }
        mWebSocketSocket?.on("friend.request.canceled") { args -> handleEvent(args, ::handleFriendRequestCanceled) }
        mWebSocketSocket?.on("friend.added") { args -> handleEvent(args, ::handleFriendAdded) }
        mWebSocketSocket?.on("friend.removed") { args -> handleEvent(args, ::handleFriendRemoved) }
        mWebSocketSocket?.on("friend.request.auto_accepted") { args -> handleEvent(args, ::handleFriendRequestAutoAccepted) }
        
        // Group & Channel Management
        mWebSocketSocket?.on("group.member.joined") { args -> handleEvent(args, ::handleGroupMemberJoined) }
        mWebSocketSocket?.on("group.member.left") { args -> handleEvent(args, ::handleGroupMemberLeft) }
        mWebSocketSocket?.on("group.member.promoted") { args -> handleEvent(args, ::handleGroupMemberPromoted) }
        mWebSocketSocket?.on("group.member.removed") { args -> handleEvent(args, ::handleGroupMemberRemoved) }
        mWebSocketSocket?.on("group.updated") { args -> handleEvent(args, ::handleGroupUpdated) }
        mWebSocketSocket?.on("group_invite") { args -> handleEvent(args, ::handleGroupInvite) }
        
        // Group Voting
        mWebSocketSocket?.on("group.vote.initiated") { args -> handleEvent(args, ::handleGroupVoteInitiated) }
        mWebSocketSocket?.on("group.vote.cast") { args -> handleEvent(args, ::handleGroupVoteCast) }
        mWebSocketSocket?.on("group.deleted") { args -> handleEvent(args, ::handleGroupDeleted) }
        mWebSocketSocket?.on("group.clearHistory.voteStarted") { args -> handleEvent(args, ::handleNotificationReceived) }
        mWebSocketSocket?.on("group.clearHistory.voteUpdated") { args -> handleEvent(args, ::handleNotificationReceived) }
        mWebSocketSocket?.on("group.clearHistory.passed") { args -> handleEvent(args, ::handleNotificationReceived) }
        mWebSocketSocket?.on("group.clearHistory.failed") { args -> handleEvent(args, ::handleNotificationReceived) }
        mWebSocketSocket?.on("groupHistoryDeleted") { args -> handleEvent(args, ::handleNotificationReceived) }
        
        // Media Downloads
        mWebSocketSocket?.on("media.download.requested") { args -> handleEvent(args, ::handleMediaDownloadRequested) }
        mWebSocketSocket?.on("media.download.approved") { args -> handleEvent(args, ::handleMediaDownloadApproved) }
        mWebSocketSocket?.on("media.download.denied") { args -> handleEvent(args, ::handleMediaDownloadDenied) }
        
        // Presence & Status
        mWebSocketSocket?.on("userStatusChanged") { args -> handleEvent(args, ::handleUserStatusChanged) }
        mWebSocketSocket?.on("user.online") { args -> handleEvent(args, ::handleUserOnline) }
        mWebSocketSocket?.on("user.offline") { args -> handleEvent(args, ::handleUserOffline) }
        mWebSocketSocket?.on("user:profile-updated") { args -> handleEvent(args, ::handleUserProfileUpdated) }
    }

    /**
     * Logs comprehensive Socket.IO connection diagnostics for troubleshooting
     * Helps identify whether WebSocket is working or if polling is the fallback
     */
    private fun logConnectionDiagnostics(eventType: String, details: String) {
        try {
            val isConnected = mSocket?.connected() ?: false
            val timestamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
            val socketId = mSocket?.id() ?: "N/A"
            
            val diagnostics = """
                ═══════════════════════════════════════════════════════
                [Socket.IO Diagnostic - $timestamp] $eventType
                ───────────────────────────────────────────────────────
                Details: $details
                Connected: $isConnected
                Socket ID: $socketId
                Note: Check Logcat filter "SocketIO_Diagnostics" for transport info
                ═══════════════════════════════════════════════════════
            """.trimIndent()
            
            Log.i(TAG, diagnostics)
            
            // Also log to Android system logs for persistence
            android.util.Log.println(
                android.util.Log.INFO,
                "SocketIO_Diagnostics",
                "$eventType - Details: $details, Connected: $isConnected"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error logging diagnostics: ${e.message}")
        }
    }

    fun send(eventName: String, data: JSONObject) {
        if (mSocket?.connected() == true) {
            mSocket?.emit(eventName, data)
            Log.d(TAG, "✅ Emitted event '$eventName' (socket connected)")
        } else {
            // Queue the event to be sent once connected
            val pendingEvent = PendingEvent(eventName, data)
            pendingEvents.add(pendingEvent)
            Log.w(TAG, "⏳ Event '$eventName' queued (socket not connected yet). Queue size: ${pendingEvents.size}")
            
            // Try to wait for connection in background thread
            scope.launch(Dispatchers.Default) {
                if (waitForConnectionSync(5000)) {  // Wait up to 5 seconds
                    // Connection established, flush queued events
                    flushPendingEvents()
                } else {
                    Log.w(TAG, "⚠️  Connection timeout after 5s - event '$eventName' may not be sent immediately")
                }
            }
        }
    }
    
    /**
     * Wait for Socket.IO connection to be established (blocking call for event queue)
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if connected, false if timeout
     */
    private fun waitForConnectionSync(timeoutMs: Long): Boolean {
        // Quick check: if already connected, return immediately
        if (mSocket?.connected() == true) {
            return true
        }
        
        connectionLock.lock()
        try {
            // Double-check after acquiring lock
            if (mSocket?.connected() == true) {
                return true
            }
            
            // Wait for condition to be signaled OR timeout
            return connectionCondition.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) ||
                   mSocket?.connected() == true
        } catch (e: InterruptedException) {
            Log.w(TAG, "⚠️  Interrupted while waiting for connection: ${e.message}")
            return false
        } finally {
            connectionLock.unlock()
        }
    }
    
    /**
     * Flush all pending events once socket connects
     */
    private fun flushPendingEvents() {
        if (pendingEvents.isEmpty()) return
        
        Log.i(TAG, "🔄 Flushing ${pendingEvents.size} pending events...")
        val eventsToSend = pendingEvents.toList()
        pendingEvents.clear()
        
        for (event in eventsToSend) {
            if (mSocket?.connected() == true) {
                try {
                    mSocket?.emit(event.eventName, event.data)
                    Log.d(TAG, "✅ Flushed queued event '${event.eventName}'")
                } catch (e: Exception) {
                    Log.e(TAG, "Error flushing event '${event.eventName}': ${e.message}")
                }
            } else {
                Log.w(TAG, "Socket disconnected while flushing events, re-queuing...")
                pendingEvents.add(event)
            }
        }
    }

    fun disconnect() {
        // ✅ Stop keepalive heartbeat
        stopKeepaliveHeartbeat()
        
        // Disconnect both primary and secondary connections
        mSocket?.disconnect()
        mSocket?.off() // Remove all listeners from primary
        
        mWebSocketSocket?.disconnect()
        mWebSocketSocket?.off() // Remove all listeners from secondary
        
        isConnectedViaPrimary = false
        isConnectedViaWebSocket = false
        
        setConnectionState(ConnectionState.DISCONNECTED)  // ✅ SET STATE
        Log.i(TAG, "════════════════════════════════════════════")
        Log.i(TAG, "Both Socket.IO connections disconnected")
        Log.i(TAG, "  PRIMARY (polling): ✓ disconnected")
        Log.i(TAG, "  SECONDARY (WebSocket): ✓ disconnected")
        Log.i(TAG, "════════════════════════════════════════════")
    }

    fun addListener(listener: WebSocketListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: WebSocketListener) {
        listeners.remove(listener)
    }

    fun isConnected(): Boolean = mSocket?.connected() ?: false || mWebSocketSocket?.connected() ?: false

    /**
     * Returns detailed connection diagnostics for debugging and UI display
     * Use this to show users which transport is active and connection health
     */
    fun getConnectionDiagnostics(): Map<String, Any> {
        return try {
            mapOf(
                "isConnected" to (mSocket?.connected() ?: false),
                "socketId" to (mSocket?.id() ?: "N/A"),
                "availableTransports" to listOf("websocket", "polling"),
                "timeoutMs" to 60000,
                "reconnectionDelayMs" to 2000,
                "reconnectionDelayMaxMs" to 30000,
                "status" to when {
                    mSocket?.connected() == true -> "CONNECTED"
                    mSocket != null -> "CONNECTING"
                    else -> "DISCONNECTED"
                }
            )
        } catch (e: Exception) {
            mapOf(
                "error" to "Failed to get diagnostics: ${e.message}",
                "status" to "ERROR"
            )
        }
    }

    private fun notifyListenerError(error: String) {
        listeners.forEach { it.onError(error) }
    }

    // region Event Handlers
    private fun handleIncomingCall(data: JSONObject) {
        try {
            val callId = data.optString("callId", "")
            if (callId.isEmpty()) {
                Log.w(TAG, "Incoming call event missing callId — ignoring")
                return
            }
            // Extract raw SDP from offer — may be {type,sdp} object or a nested JSON string
            val sdp = extractSdp(data, "offer")
            val call = IncomingCallData(
                callId = callId,
                callerId = data.optString("callerId", ""),
                callerUsername = data.optString("callerUsername", data.optString("callerName", "Friend")),
                callType = data.optString("callType", "audio"),
                sdpOffer = sdp,
                timestamp = data.optLong("timestamp", System.currentTimeMillis()),
                callerAvatar = data.optString("callerAvatar", data.optString("caller_avatar", null))
            )
            listeners.forEach { it.onIncomingCall(call) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming call: ${e.message}", e)
        }
    }
    
    private fun handleCallAnswered(data: JSONObject) {
        try {
            val callId = data.optString("callId", "")
            if (callId.isEmpty()) {
                Log.w(TAG, "Call answered event missing callId — ignoring")
                return
            }
            // Extract raw SDP from answer — may be {type,sdp} object or a nested JSON string
            val sdp = extractSdp(data, "answer")
            val callData = CallAnsweredData(
                callId = callId,
                answer = sdp,
                answeredBy = data.optString("answeredBy", null),
                answeredAt = data.optLong("answeredAt", System.currentTimeMillis())
            )
            listeners.forEach { it.onCallAnswered(callData) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse call answered: ${e.message}", e)
        }
    }

    private fun handleCallRejected(data: JSONObject) {
        try {
            val callId = data.optString("callId", "")
            if (callId.isEmpty()) {
                Log.w(TAG, "Call rejected event missing callId — ignoring")
                return
            }
            val callData = CallRejectedData(
                callId = callId,
                reason = data.optString("reason", "declined"),
                rejectedBy = data.optString("rejectedBy", "")
            )
            listeners.forEach { it.onCallRejected(callData) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse call rejected: ${e.message}", e)
        }
    }

    private fun handleCallEnded(data: JSONObject) {
        try {
            val callId = data.optString("callId", "")
            if (callId.isEmpty()) {
                Log.w(TAG, "Call ended event missing callId — ignoring")
                return
            }
            val callData = CallEndedData(
                callId = callId,
                endedBy = data.optString("endedBy", null),
                duration = if (data.has("duration")) data.optLong("duration", 0L) else null,
                endedAt = data.optLong("endedAt", System.currentTimeMillis())
            )
            listeners.forEach { it.onCallEnded(callData) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse call ended: ${e.message}", e)
        }
    }

    private fun handleIceCandidate(data: JSONObject) {
        try {
            val candidateObj = data.optJSONObject("candidate")
            val candidateStr = candidateObj?.toString() ?: data.optString("candidate", "")
            if (candidateStr.isEmpty()) {
                Log.w(TAG, "Empty ICE candidate received — ignoring")
                return
            }
            val iceData = IceCandidateData(
                callId = data.getString("callId"),
                from = data.optString("from", ""),
                candidate = candidateStr
            )
            listeners.forEach { it.onIceCandidate(iceData) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate: ${e.message}")
        }
    }

    /**
     * Extract raw SDP string from a field that may be:
     * - A JSON object {type, sdp}
     * - A JSON string '{"type":"offer","sdp":"v=0..."}'
     * - A raw SDP string 'v=0...'
     */
    private fun extractSdp(data: JSONObject, field: String): String {
        // Try as JSON object first
        val obj = data.optJSONObject(field)
        if (obj != null) {
            val sdp = obj.optString("sdp", "")
            // Guard against double-serialized SDP (sdp field is itself a JSON string)
            if (sdp.startsWith("{")) {
                try {
                    val inner = JSONObject(sdp)
                    return inner.optString("sdp", sdp)
                } catch (_: Exception) { /* not JSON, fall through */ }
            }
            return sdp.ifEmpty { obj.toString() }
        }
        // Try as string (may be raw SDP or JSON string)
        val str = data.optString(field, "")
        if (str.startsWith("{")) {
            try {
                val parsed = JSONObject(str)
                return parsed.optString("sdp", str)
            } catch (_: Exception) { /* not JSON, use as-is */ }
        }
        return str
    }

    private fun handleNewMessage(data: JSONObject) {
        // Backend sends: _id or id, senderId, recipientId, content, timestamp, senderUsername
        // Fall back between field‑name variants so old and new server versions both work
        val messageId = data.optString("_id").takeIf { it.isNotEmpty() }
            ?: data.optString("id").takeIf { it.isNotEmpty() }
            ?: data.optString("messageId").takeIf { it.isNotEmpty() }
            ?: return  // no usable ID → discard
        
        // ✅ DEDUP: Drop if we've already processed this messageId within the last 10s
        if (isDuplicateMessage(messageId)) {
            Log.d(TAG, "⏭️ Dropped duplicate newMessage event: $messageId")
            return
        }
        val senderId = data.optString("senderId").takeIf { it.isNotEmpty() }
            ?: data.optString("from").takeIf { it.isNotEmpty() }
            ?: return
        val recipientId = data.optString("recipientId").takeIf { it.isNotEmpty() }
            ?: data.optString("to").takeIf { it.isNotEmpty() }
            ?: return
        val content = data.optString("content", "")
        val timestamp = if (data.has("timestamp")) data.getLong("timestamp") else System.currentTimeMillis()
        val message = MessageData(
            messageId = messageId,
            senderId = senderId,
            senderUsername = data.optString("senderUsername", data.optString("senderName", "Unknown")),
            senderDisplayName = data.optString("senderDisplayName", data.optString("displayName", "")).takeIf { it.isNotEmpty() },
            recipientId = recipientId,
            content = content,
            createdAt = timestamp,
            media = data.optString("media").takeIf { it.isNotEmpty() },
            mediaType = data.optString("mediaType").takeIf { it.isNotEmpty() && it != "null" },
            mediaName = data.optString("mediaName").takeIf { it.isNotEmpty() && it != "null" },
            senderAvatar = data.optString("senderAvatar").takeIf { it.isNotEmpty() && it != "null" },
            mediaShareMode = data.optString("mediaShareMode").takeIf { it.isNotEmpty() && it != "null" },
            replyToMessageId = data.optString("replyToMessageId").takeIf { it.isNotEmpty() && it != "null" },
            replyToUsername = data.optString("replyToUsername").takeIf { it.isNotEmpty() && it != "null" },
            replyToText = data.optString("replyToText").takeIf { it.isNotEmpty() && it != "null" },
            subject = data.optString("subject").takeIf { it.isNotEmpty() && it != "null" }
        )
        listeners.forEach { it.onNewMessage(message) }
    }

    private fun handleReactionAdded(data: JSONObject) {
        val reactionData = ReactionData(
            messageId = data.optString("messageId", ""),
            emoji = data.optString("emoji", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", "")
        )
        listeners.forEach { it.onReactionReceived(reactionData) }
    }

    private fun handleReactionRemoved(data: JSONObject) {
        val reactionData = ReactionData(
            messageId = data.optString("messageId", ""),
            emoji = data.optString("emoji", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", "")
        )
        listeners.forEach { it.onReactionRemoved(reactionData) }
    }

    // ✅ NEW: Handle GROUP message reactions (visible to all group members)
    private fun handleGroupReactionAdded(data: JSONObject) {
        Log.d(TAG, "📌 GROUP Reaction received: ${data.optString("emoji")} on message ${data.optString("messageId")}")
        val reactionData = ReactionData(
            messageId = data.optString("messageId", ""),
            emoji = data.optString("emoji", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            groupId = data.optString("groupId", "")  // Include groupId for group context
        )
        listeners.forEach { it.onGroupReactionReceived(reactionData) }
    }

    // ✅ NEW: Handle GROUP message reaction removal
    private fun handleGroupReactionRemoved(data: JSONObject) {
        Log.d(TAG, "📌 GROUP Reaction removed: ${data.optString("emoji")} from message ${data.optString("messageId")}")
        val reactionData = ReactionData(
            messageId = data.optString("messageId", ""),
            emoji = data.optString("emoji", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            groupId = data.optString("groupId", "")  // Include groupId for group context
        )
        listeners.forEach { it.onGroupReactionRemoved(reactionData) }
    }

    private fun handleProfileUpdated(data: JSONObject) {
        val profileData = ProfileUpdatedData(
            userId = data.optString("userId", ""),
            field = data.optString("field", ""),
            value = data.optString("value", ""),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onProfileUpdated(profileData) }
    }

    // 🔧 NEW: Handle Avatar Updated Events
    private fun handleAvatarUpdated(data: JSONObject) {
        val avatarData = AvatarUpdatedData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            avatarUrl = data.optString("avatarUrl", ""),
            imageId = data.optString("imageId", ""),
            fileName = data.optString("fileName", null),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        Log.d(TAG, "Avatar updated for user ${avatarData.userId}: ${avatarData.avatarUrl}")
        listeners.forEach { it.onAvatarUpdated(avatarData) }
    }

    private fun handleGroupMessage(data: JSONObject) {
        val messageId = data.optString("messageId", "").takeIf { it.isNotEmpty() } ?: return
        
        // ✅ DEDUP: Drop if we've already processed this messageId within the last 10s
        if (isDuplicateMessage(messageId)) {
            Log.d(TAG, "⏭️ Dropped duplicate group.message.received event: $messageId")
            return
        }
        
        val message = GroupMessageData(
            messageId = messageId,
            groupId = data.optString("groupId", ""),
            senderId = data.optString("senderId", ""),
            senderUsername = data.optString("senderUsername", ""),
            content = data.optString("content", ""),
            createdAt = data.optLong("createdAt", System.currentTimeMillis()),
            senderAvatar = data.optString("senderAvatar", null),
            replyToMessageId = data.optString("replyToMessageId").takeIf { it.isNotEmpty() && it != "null" },
            replyToUsername = data.optString("replyToUsername").takeIf { it.isNotEmpty() && it != "null" },
            replyToText = data.optString("replyToText").takeIf { it.isNotEmpty() && it != "null" },
            mediaId = data.optString("mediaId").takeIf { it.isNotEmpty() && it != "null" },
            mediaType = data.optString("mediaType").takeIf { it.isNotEmpty() && it != "null" },
            mediaName = data.optString("mediaName").takeIf { it.isNotEmpty() && it != "null" },
            mediaShareMode = data.optString("mediaShareMode").takeIf { it.isNotEmpty() && it != "null" }
        )
        listeners.forEach { it.onGroupMessage(message) }
    }

    private fun handleGroupMemberJoined(data: JSONObject) {
        val payload = GroupMemberActionData(
            groupId = data.optString("groupId", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            displayName = data.optString("displayName", ""),
            avatar = data.optString("avatar", null),
            action = "joined",
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onGroupMemberJoined(payload) }
    }

    private fun handleGroupMemberLeft(data: JSONObject) {
        val payload = GroupMemberActionData(
            groupId = data.optString("groupId", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            displayName = data.optString("displayName", ""),
            action = "left",
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onGroupMemberLeft(payload) }
    }

    private fun handleGroupMemberPromoted(data: JSONObject) {
        val payload = GroupMemberActionData(
            groupId = data.optString("groupId", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            action = "promoted",
            actor = data.optString("promotedBy", ""),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onGroupMemberPromoted(payload) }
    }

    private fun handleGroupMemberRemoved(data: JSONObject) {
        val payload = GroupMemberActionData(
            groupId = data.optString("groupId", ""),
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            action = "removed",
            actor = data.optString("actor", ""),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onGroupMemberRemoved(payload) }
    }

    private fun handleChatHistoryDeleted(data: JSONObject) {
        val payload = ChatHistoryDeletedData(
            deletedBy = data.optString("deletedBy", ""),
            recipientId = data.optString("recipientId", "")
        )
        listeners.forEach { it.onChatHistoryDeleted(payload) }
    }

    private fun handleUserProfileUpdated(data: JSONObject) {
        val payload = UserProfileUpdatedData(
            userId = data.optString("userId", ""),
            bio = data.optString("bio", null),
            status = data.optString("status", null),
            privacyLevel = data.optString("privacyLevel", null),
            pronouns = data.optString("pronouns", null),
            avatar = data.optString("avatar", null),
            profileImageUrl = data.optString("profileImageUrl", null),
            lastUpdated = data.optString("lastUpdated", null)
        )
        Log.d(TAG, "Profile updated: ${payload.userId}")
        listeners.forEach { it.onUserProfileUpdated(payload) }
    }

    private fun handleNotificationReceived(data: JSONObject) {
        try {
            val payload = InternalNotificationData(
                title = data.optString("title", "FreeTime"),
                body = data.optString("body", ""),
                data = data.optJSONObject("data") ?: JSONObject(),
                timestamp = data.optLong("timestamp", System.currentTimeMillis())
            )
            Log.d(TAG, "Internal notification received: ${payload.title}")
            listeners.forEach { it.onNotificationReceived(payload) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse internal notification: ${e.message}")
        }
    }

    private fun handleChannelMessage(data: JSONObject) {
        val message = ChannelMessageData(
            messageId = data.optString("messageId", ""),
            channelId = data.optString("channelId", ""),
            senderId = data.optString("senderId", ""),
            senderUsername = data.optString("senderUsername", ""),
            content = data.optString("content", ""),
            createdAt = data.optLong("createdAt", System.currentTimeMillis())
        )
        listeners.forEach { it.onChannelMessage(message) }
    }

    private fun handleUserTyping(data: JSONObject) {
        val typingData = TypingData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            recipientId = data.optString("recipientId", ""),
            isTyping = data.optBoolean("isTyping", false),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
        listeners.forEach { it.onUserTyping(typingData) }
    }

    private fun handleFriendRequestReceived(data: JSONObject) {
        val payload = FriendRequestEventData(
            requestId = data.optString("requestId", ""),
            senderId = data.optString("senderId", ""),
            senderName = data.optString("senderName", ""),
            recipientId = data.optString("recipientId", ""),
            status = data.optString("status", "pending"),
            timestamp = data.optString("timestamp", System.currentTimeMillis().toString()),
            senderAvatar = data.optString("senderAvatar", null)
        )
        Log.d(TAG, "Friend request received from ${payload.senderName}")
        listeners.forEach { it.onFriendRequestReceived(payload) }
    }

    private fun handleFriendRequestAccepted(data: JSONObject) {
        val payload = FriendRequestEventData(
            requestId = data.optString("requestId", ""),
            senderId = data.optString("senderId", ""),
            senderName = data.optString("senderName", ""),
            recipientId = data.optString("recipientId", ""),
            status = "accepted",
            timestamp = data.optString("timestamp", System.currentTimeMillis().toString())
        )
        listeners.forEach { it.onFriendRequestAccepted(payload) }
    }

    private fun handleFriendRequestRejected(data: JSONObject) {
        val payload = FriendRequestEventData(
            requestId = data.optString("requestId", ""),
            senderId = data.optString("senderId", ""),
            senderName = data.optString("senderName", ""),
            recipientId = data.optString("recipientId", ""),
            status = "rejected",
            timestamp = data.optString("timestamp", System.currentTimeMillis().toString())
        )
        listeners.forEach { it.onFriendRequestRejected(payload) }
    }

    private fun handleFriendRequestCanceled(data: JSONObject) {
        val payload = FriendRequestEventData(
            requestId = data.optString("requestId", ""),
            senderId = data.optString("senderId", ""),
            senderName = data.optString("senderName", ""),
            recipientId = data.optString("recipientId", ""),
            status = "canceled",
            timestamp = data.optString("timestamp", System.currentTimeMillis().toString())
        )
        listeners.forEach { it.onFriendRequestCanceled(payload) }
    }

    private fun handleGroupUpdated(data: JSONObject) {
        val payload = GroupUpdatedData(
            groupId = data.optString("groupId", ""),
            name = data.optString("name", null),
            description = data.optString("description", null),
            avatar = data.optString("avatar", null)
        )
        listeners.forEach { it.onGroupUpdated(payload) }
    }

    private fun handleGroupInvite(data: JSONObject) {
        val payload = GroupInviteData(
            inviteId = data.optString("inviteId", ""),
            groupId = data.optString("groupId", ""),
            groupName = data.optString("groupName", ""),
            inviterId = data.optString("inviterId", ""),
            inviterName = data.optString("inviterName", "")
        )
        listeners.forEach { it.onGroupInviteReceived(payload) }
    }

    private fun handleGroupVoteInitiated(data: JSONObject) {
        val optionsArray = data.optJSONArray("options")
        val optionsList = mutableListOf<String>()
        if (optionsArray != null) {
            for (i in 0 until optionsArray.length()) {
                optionsList.add(optionsArray.optString(i))
            }
        }
        val payload = GroupVoteInitiatedData(
            voteId = data.optString("voteId", ""),
            groupId = data.optString("groupId", ""),
            title = data.optString("title", ""),
            options = optionsList,
            createdBy = data.optString("createdBy", "")
        )
        listeners.forEach { it.onGroupVoteInitiated(payload) }
    }

    private fun handleGroupVoteCast(data: JSONObject) {
        val payload = GroupVoteCastData(
            voteId = data.optString("voteId", ""),
            userId = data.optString("userId", ""),
            optionIndex = data.optInt("optionIndex", -1)
        )
        listeners.forEach { it.onGroupVoteCast(payload) }
    }

    private fun handleGroupDeleted(data: JSONObject) {
        val payload = GroupDeletedData(
            groupId = data.optString("groupId", ""),
            deletedBy = data.optString("deletedBy", "")
        )
        listeners.forEach { it.onGroupDeleted(payload) }
    }

    // ✅ NEW: Handle group picture update
    private fun handleGroupPictureUpdated(data: JSONObject) {
        val payload = GroupPictureUpdatedData(
            groupId = data.optString("groupId", ""),
            pictureUrl = data.optString("pictureUrl", ""),
            updatedBy = data.optString("updatedBy", ""),
            updatedAt = data.optString("updatedAt", "")
        )
        Log.d(TAG, "Group picture updated: ${payload.groupId}")
        listeners.forEach { it.onGroupPictureUpdated(payload) }
    }

    // ✅ NEW: Handle group picture removal
    private fun handleGroupPictureRemoved(data: JSONObject) {
        val payload = GroupPictureRemovedData(
            groupId = data.optString("groupId", ""),
            removedBy = data.optString("removedBy", ""),
            removedAt = data.optString("removedAt", "")
        )
        Log.d(TAG, "Group picture removed: ${payload.groupId}")
        listeners.forEach { it.onGroupPictureRemoved(payload) }
    }

    // ✅ NEW: Handle group name update
    private fun handleGroupNameUpdated(data: JSONObject) {
        val payload = GroupNameUpdatedData(
            groupId = data.optString("groupId", ""),
            name = data.optString("name", ""),
            updatedBy = data.optString("updatedBy", ""),
            updatedAt = data.optString("updatedAt", "")
        )
        Log.d(TAG, "Group name updated: ${payload.groupId} -> ${payload.name}")
        listeners.forEach { it.onGroupNameUpdated(payload) }
    }

    private fun handleMediaDownloadRequested(data: JSONObject) {
        val payload = MediaDownloadRequestData(
            requestId = data.optString("requestId", ""),
            mediaId = data.optString("mediaId", ""),
            requesterId = data.optString("requesterId", ""),
            requesterName = data.optString("requesterName", data.optString("requesterUsername", "User"))
        )
        android.util.Log.d("FREETIME_WEBSOCKET", "🔔 handleMediaDownloadRequested: requestId=${payload.requestId}, mediaId=${payload.mediaId}, requester=${payload.requesterName}")
        listeners.forEach { 
            android.util.Log.d("FREETIME_WEBSOCKET", "  -> Notifying listener: ${it.javaClass.simpleName}")
            it.onMediaDownloadRequested(payload) 
        }
    }

    private fun handleMediaDownloadApproved(data: JSONObject) {
        val payload = MediaDownloadResponseData(
            requestId = data.optString("requestId", ""),
            mediaId = data.optString("mediaId", ""),
            approved = true,
            message = data.optString("message", null),
            // ✅ NEW: Extract download details from WebSocket event
            downloadUrl = data.optString("downloadUrl", null).takeIf { it?.isNotEmpty() == true },
            encryptionKey = data.optString("encryptionKey", null).takeIf { it?.isNotEmpty() == true },
            iv = data.optString("iv", null).takeIf { it?.isNotEmpty() == true },
            fileName = data.optString("fileName", null).takeIf { it?.isNotEmpty() == true },
            mimeType = data.optString("mimeType", null).takeIf { it?.isNotEmpty() == true },
            size = data.optLong("size", 0).takeIf { it > 0 },
            encrypted = data.optBoolean("encrypted", false),
            expiresAt = data.optLong("expiresAt", 0).takeIf { it > 0 }
        )
        Log.d(TAG, "✅ Media download approved: mediaId=${payload.mediaId}, url=${payload.downloadUrl != null}, encrypted=${payload.encrypted}")
        listeners.forEach { it.onMediaDownloadApproved(payload) }
    }

    private fun handleMediaDownloadDenied(data: JSONObject) {
        val payload = MediaDownloadResponseData(
            requestId = data.optString("requestId", ""),
            mediaId = data.optString("mediaId", ""),
            approved = false,
            message = data.optString("message", null)
        )
        listeners.forEach { it.onMediaDownloadDenied(payload) }
    }

    private fun handleUserOnline(data: JSONObject) {
        val payload = UserPresenceData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            timestamp = data.optString("onlineAt", System.currentTimeMillis().toString())
        )
        listeners.forEach { it.onUserOnline(payload) }
    }

    private fun handleUserOffline(data: JSONObject) {
        val payload = UserPresenceData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            timestamp = data.optString("offlineAt", System.currentTimeMillis().toString())
        )
        listeners.forEach { it.onUserOffline(payload) }
    }

    // ✅ MUTUAL FRIEND AUTO-ACCEPT: Handle auto-accepted friend request notification
    private fun handleFriendRequestAutoAccepted(data: JSONObject) {
        val payload = FriendRequestAutoAcceptedData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            avatar = data.optString("avatar", ""),
            friendshipId = data.optString("friendshipId", null)
        )
        Log.d(TAG, "Friend request auto-accepted from ${payload.username} (${payload.userId})")
        listeners.forEach { it.onFriendRequestAutoAccepted(payload) }
    }

    // ✅ REAL-TIME FRIEND LIST SYNC: Handle friend added event
    private fun handleFriendAdded(data: JSONObject) {
        val payload = FriendAddedData(
            userId = data.optString("userId", ""),
            username = data.optString("username", ""),
            avatar = data.optString("avatar", null),
            status = data.optString("status", "offline")
        )
        Log.d(TAG, "Friend added: ${payload.username} (${payload.userId})")
        listeners.forEach { it.onFriendAdded(payload) }
    }

    // ✅ REAL-TIME FRIEND LIST SYNC: Handle friend removed event
    private fun handleFriendRemoved(data: JSONObject) {
        val payload = FriendRemovedData(
            removedFriendId = data.optString("removedFriendId", "")
        )
        Log.d(TAG, "Friend removed: ${payload.removedFriendId}")
        listeners.forEach { it.onFriendRemoved(payload) }
    }

    // ✅ PRESENCE UPDATE: Handle user status change event
    private fun handleUserStatusChanged(data: JSONObject) {
        val payload = UserStatusData(
            userId = data.optString("userId", ""),
            isOnline = data.optBoolean("isOnline", false),
            lastActivityAt = if (data.has("lastActivityAt")) data.getLong("lastActivityAt") else System.currentTimeMillis(),
            actualStatus = data.optString("status", if (data.optBoolean("isOnline", false)) "online" else "offline"),
            lastSeen = data.optString("lastActivityAt", System.currentTimeMillis().toString())
        )
        Log.d(TAG, "User status changed: ${payload.userId} is ${if (payload.isOnline) "online" else "offline"}")
        listeners.forEach { it.onUserStatusChanged(payload) }
    }
    
    // endregion
    
    // ==================== SSL CERTIFICATE BYPASS (DEBUG MODE ONLY) ====================
    
    /**
     * Creates a trust manager that accepts all certificates
     * ⚠️  DEBUG ONLY - Never use in production
     */
    private fun createTrustAllX509TrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }
    
    /**
     * Creates an SSL socket factory that accepts all certificates
     * ⚠️  DEBUG ONLY - Never use in production
     */
    private fun createTrustAllSSLSocketFactory(): SSLSocketFactory {
        return try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf<TrustManager>(createTrustAllX509TrustManager()), java.security.SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create trustAll SSL factory: ${e.message}")
            throw e
        }
    }
    
    /**
     * ✅ CRITICAL FIX: Create OkHttp client with proper SSL/TLS configuration for Socket.IO
     * Socket.IO library uses OkHttp internally for both polling and WebSocket upgrades.
     * Without proper SSL configuration, WebSocket/HTTPS connections will fail with:
     *   - SSLHandshakeException
     *   - Certificate verification failed
     *   - PKIX path building failed
     */
    private fun createSocketIOHttpClient(): OkHttpClient {
        return try {
            // Create trust manager that accepts all certificates (for self-signed certs in dev)
            val trustAllCerts = arrayOf<TrustManager>(createTrustAllX509TrustManager())
            
            // Create SSL context with trust all certs
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // Create Socket Factory
            val sslSocketFactory = sslContext.socketFactory
            val trustManager = trustAllCerts[0] as X509TrustManager
            
            // Build OkHttp client with SSL + proper timeouts for Socket.IO
            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }  // Accept any hostname (self-signed certs)
                .connectTimeout(30, TimeUnit.SECONDS)  // TCP connection timeout
                .readTimeout(60, TimeUnit.SECONDS)     // Socket read timeout
                .writeTimeout(60, TimeUnit.SECONDS)    // Socket write timeout
                .callTimeout(120, TimeUnit.SECONDS)    // Total request timeout
                // HTTP/1.1 support for better stability in long-polling
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)        // Auto-retry on failure
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] Failed to create Socket.IO OkHttp client: ${e.message}", e)
            // Fallback to default OkHttp client (may not work for self-signed certs)
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
        }
    }
}
