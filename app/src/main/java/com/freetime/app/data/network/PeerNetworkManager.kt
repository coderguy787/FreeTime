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

/**
 * Peer Network Manager
 * Handles peer-to-peer communication with master-server's peer network
 * - Peer discovery and registration
 * - Direct peer-to-peer data synchronization
 * - Distributed consensus and coordination
 */
class PeerNetworkManager(
    private val authToken: String,
    private val userId: String,
    private val coroutineScope: CoroutineScope,
    private val onPeerDiscovered: (PeerInfo) -> Unit = {},
    private val onPeerDataReceived: (PeerNetworkEvent) -> Unit = {},
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) : WebSocketListener() {

    companion object {
        private const val TAG = "PeerNetworkManager"
        private const val RECONNECT_INTERVAL = 5000L // 5 seconds
        private const val MAX_RECONNECT_ATTEMPTS = 10
        
        // Peer Network URL comes from BuildConfig (configured in build.gradle)
        fun getPeerNetworkUrl(): String {
            return try {
                BuildConfig.PEER_SERVER_URL
            } catch (e: Exception) {
                // Fallback if BuildConfig is not available
                "wss://example.com:9080"
            }
        }
    }

    private var peerWebSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var isIntentionallyClosed = false
    private val discoveredPeers = mutableMapOf<String, PeerInfo>()

    /**
     * Connect to Peer Network server
     */
    fun connect() {
        try {
            val client = ApiClient.getTrustAllOkHttpClient(
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 10,
                writeTimeoutSeconds = 10
            )

            val request = Request.Builder()
                .url("${getPeerNetworkUrl()}?token=$authToken&userId=$userId")
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("X-Peer-Id", userId)
                .build()

            peerWebSocket = client.newWebSocket(request, this)
            reconnectAttempts = 0
            isIntentionallyClosed = false

            Log.d(TAG, "Peer Network connecting to ${getPeerNetworkUrl()}...")
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            onError("Peer connection failed: ${e.message}")
        }
    }

    /**
     * Disconnect from Peer Network
     */
    fun disconnect() {
        try {
            isIntentionallyClosed = true
            peerWebSocket?.close(1000, "Client disconnect")
            peerWebSocket = null
            discoveredPeers.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    /**
     * Send data to peer network
     */
    fun sendPeerData(messageType: String, data: JSONObject) {
        try {
            val message = JSONObject().apply {
                put("type", messageType)
                put("senderId", userId)
                put("timestamp", System.currentTimeMillis())
                put("data", data)
            }
            peerWebSocket?.send(message.toString())
            Log.d(TAG, "Sent peer message: $messageType")
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            onError("Failed to send peer data: ${e.message}")
        }
    }

    /**
     * Request to sync data with specific peer
     */
    fun syncWithPeer(peerId: String, syncType: String) {
        try {
            val message = JSONObject().apply {
                put("type", "peer.sync.request")
                put("senderId", userId)
                put("targetPeerId", peerId)
                put("syncType", syncType)
                put("timestamp", System.currentTimeMillis())
            }
            peerWebSocket?.send(message.toString())
            Log.d(TAG, "Sync request sent to peer $peerId for $syncType")
        } catch (e: Exception) {
            Log.e(TAG, "Sync request error: ${e.message}")
            onError("Failed to sync with peer: ${e.message}")
        }
    }

    /**
     * Request peer list from network
     */
    fun requestPeerList() {
        try {
            val message = JSONObject().apply {
                put("type", "peers.list.request")
                put("senderId", userId)
                put("timestamp", System.currentTimeMillis())
            }
            peerWebSocket?.send(message.toString())
            Log.d(TAG, "Peer list request sent")
        } catch (e: Exception) {
            Log.e(TAG, "Peer list request error: ${e.message}")
            onError("Failed to request peer list: ${e.message}")
        }
    }

    /**
     * Get discovered peers
     */
    fun getDiscoveredPeers(): List<PeerInfo> = discoveredPeers.values.toList()

    /**
     * Get specific peer info
     */
    fun getPeerInfo(peerId: String): PeerInfo? = discoveredPeers[peerId]

    // ============== WebSocketListener Callbacks ==============

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Peer Network connected")
        reconnectAttempts = 0

        coroutineScope.launch(Dispatchers.Main) {
            onConnected()
            // Request peer list on connection
            requestPeerList()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            Log.d(TAG, "Peer message received: ${text.take(100)}")
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "peer.discovered" -> handlePeerDiscovered(json)
                "peer.disconnected" -> handlePeerDisconnected(json)
                "peer.sync.data" -> handlePeerSyncData(json)
                "peers.list" -> handlePeersList(json)
                else -> handleGenericPeerEvent(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message parse error: ${e.message}")
            onError("Failed to parse peer message: ${e.message}")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Peer Network closing: $code - $reason")
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Peer Network closed: $code - $reason")

        coroutineScope.launch(Dispatchers.Main) {
            onDisconnected()
        }

        // Auto-reconnect if not intentional
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
        Log.e(TAG, "Peer Network failure: ${t.message}")
        onError("Peer Network error: ${t.message}")

        // Auto-reconnect
        if (!isIntentionallyClosed && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            
            coroutineScope.launch(Dispatchers.Default) {
                delay(RECONNECT_INTERVAL)
                connect()
            }
        }
    }

    // ============== Event Handlers ==============

    private fun handlePeerDiscovered(json: JSONObject) {
        try {
            val peerId = json.optString("peerId")
            val peerInfo = PeerInfo(
                peerId = peerId,
                username = json.optString("username"),
                status = json.optString("status", "online"),
                lastSeen = json.optString("lastSeen"),
                dataHash = json.optString("dataHash", ""),
                version = json.optInt("version", 0)
            )
            
            discoveredPeers[peerId] = peerInfo
            Log.d(TAG, "Peer discovered: $peerId")
            
            coroutineScope.launch(Dispatchers.Main) {
                onPeerDiscovered(peerInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling peer discovered: ${e.message}")
        }
    }

    private fun handlePeerDisconnected(json: JSONObject) {
        try {
            val peerId = json.optString("peerId")
            discoveredPeers.remove(peerId)
            Log.d(TAG, "Peer disconnected: $peerId")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling peer disconnected: ${e.message}")
        }
    }

    private fun handlePeerSyncData(json: JSONObject) {
        try {
            val event = PeerNetworkEvent(
                type = "peer.sync.data",
                senderId = json.optString("senderId"),
                syncType = json.optString("syncType"),
                timestamp = json.optString("timestamp"),
                dataHash = json.optString("dataHash"),
                version = json.optInt("version"),
                data = json.optJSONObject("data")
            )
            
            coroutineScope.launch(Dispatchers.Main) {
                onPeerDataReceived(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync data: ${e.message}")
        }
    }

    private fun handlePeersList(json: JSONObject) {
        try {
            val peers = json.optJSONArray("peers")
            if (peers != null) {
                for (i in 0 until peers.length()) {
                    val peerJson = peers.getJSONObject(i)
                    val peerId = peerJson.optString("peerId")
                    
                    if (peerId != userId) { // Don't add self
                        val peerInfo = PeerInfo(
                            peerId = peerId,
                            username = peerJson.optString("username"),
                            status = peerJson.optString("status", "online"),
                            lastSeen = peerJson.optString("lastSeen"),
                            dataHash = peerJson.optString("dataHash", ""),
                            version = peerJson.optInt("version", 0)
                        )
                        discoveredPeers[peerId] = peerInfo
                        
                        coroutineScope.launch(Dispatchers.Main) {
                            onPeerDiscovered(peerInfo)
                        }
                    }
                }
                Log.d(TAG, "Received peer list with ${peers.length()} peers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling peers list: ${e.message}")
        }
    }

    private fun handleGenericPeerEvent(json: JSONObject) {
        try {
            val event = PeerNetworkEvent(
                type = json.optString("type"),
                senderId = json.optString("senderId"),
                syncType = json.optString("syncType", ""),
                timestamp = json.optString("timestamp"),
                dataHash = json.optString("dataHash", ""),
                version = json.optInt("version"),
                data = json.optJSONObject("data")
            )
            
            coroutineScope.launch(Dispatchers.Main) {
                onPeerDataReceived(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling generic event: ${e.message}")
        }
    }
}

// ============== Data Classes ==============

/**
 * Peer Network Event
 */
data class PeerNetworkEvent(
    val type: String,
    val senderId: String,
    val syncType: String = "",
    val timestamp: String,
    val dataHash: String = "",
    val version: Int = 0,
    val data: JSONObject? = null
)

/**
 * Peer Information
 */
data class PeerInfo(
    val peerId: String,
    val username: String,
    val status: String = "online",
    val lastSeen: String = "",
    val dataHash: String = "",
    val version: Int = 0
) {
    /**
     * Check if peer is online
     */
    fun isOnline(): Boolean = status == "online"
    
    /**
     * Check if peer data is in sync with local version
     */
    fun isInSync(localVersion: Int, localDataHash: String): Boolean {
        return version >= localVersion && dataHash == localDataHash
    }
}
