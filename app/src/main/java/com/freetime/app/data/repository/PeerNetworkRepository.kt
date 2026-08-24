package com.freetime.app.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.freetime.app.data.network.PeerNetworkManager
import com.freetime.app.data.network.PeerNetworkEvent
import com.freetime.app.data.network.PeerInfo
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class PeerNetworkRepository(
    private val authToken: String,
    private val userId: String,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PeerNetworkRepository"
    }

    private var peerManager: PeerNetworkManager? = null

    private val _peersDiscovered = MutableLiveData<List<PeerInfo>>()
    val peersDiscovered: LiveData<List<PeerInfo>> = _peersDiscovered

    private val _newPeerDiscovered = MutableLiveData<PeerInfo>()
    val newPeerDiscovered: LiveData<PeerInfo> = _newPeerDiscovered

    private val _peerDataReceived = MutableLiveData<PeerNetworkEvent>()
    val peerDataReceived: LiveData<PeerNetworkEvent> = _peerDataReceived

    private val _syncCompleted = MutableLiveData<String>()
    val syncCompleted: LiveData<String> = _syncCompleted

    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _errorState = MutableLiveData<String>()
    val errorState: LiveData<String> = _errorState

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    fun connect() {
        try {
            _connectionState.value = ConnectionState.CONNECTING

            // exposes peer connection state to screens
            peerManager = PeerNetworkManager(
                authToken = authToken,
                userId = userId,
                coroutineScope = coroutineScope,
                onPeerDiscovered = { peerInfo -> handlePeerDiscovered(peerInfo) },
                onPeerDataReceived = { event -> handlePeerDataReceived(event) },
                onConnected = { handleConnected() },
                onDisconnected = { handleDisconnected() },
                onError = { error -> handleError(error) }
            )

            peerManager?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            handleError("Peer connection failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            peerManager?.disconnect()
            peerManager = null
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    fun sendPeerData(messageType: String, data: JSONObject) {
        try {
            peerManager?.sendPeerData(messageType, data)
            Log.d(TAG, "Sent peer data: $messageType")
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            handleError("Failed to send peer data: ${e.message}")
        }
    }

    fun syncWithPeer(peerId: String, syncType: String) {
        try {
            peerManager?.syncWithPeer(peerId, syncType)
            Log.d(TAG, "Sync initiated with peer $peerId for $syncType")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            handleError("Failed to sync with peer: ${e.message}")
        }
    }

    fun refreshPeerList() {
        try {
            peerManager?.requestPeerList()
            Log.d(TAG, "Peer list refresh requested")
        } catch (e: Exception) {
            Log.e(TAG, "Refresh error: ${e.message}")
            handleError("Failed to refresh peer list: ${e.message}")
        }
    }

    fun getDiscoveredPeers(): List<PeerInfo> {
        return peerManager?.getDiscoveredPeers() ?: emptyList()
    }

    fun getPeerInfo(peerId: String): PeerInfo? {
        return peerManager?.getPeerInfo(peerId)
    }

    fun isPeerOnline(peerId: String): Boolean {
        return peerManager?.getPeerInfo(peerId)?.isOnline() ?: false
    }

    fun isPeerInSync(peerId: String, localVersion: Int, localDataHash: String): Boolean {
        val peerInfo = peerManager?.getPeerInfo(peerId) ?: return false
        return peerInfo.isInSync(localVersion, localDataHash)
    }

    private fun handlePeerDiscovered(peerInfo: PeerInfo) {
        Log.d(TAG, "Peer discovered: ${peerInfo.peerId}")
        _newPeerDiscovered.value = peerInfo

        val currentPeers = getDiscoveredPeers()
        _peersDiscovered.value = currentPeers
    }

    private fun handlePeerDataReceived(event: PeerNetworkEvent) {
        Log.d(TAG, "Peer data received: ${event.type} from ${event.senderId}")
        _peerDataReceived.value = event

        when (event.type) {
            "peer.sync.data" -> {
                _syncCompleted.value = event.senderId
            }
        }
    }

    private fun handleConnected() {
        Log.d(TAG, "Peer Network connected")
        _connectionState.value = ConnectionState.CONNECTED
        _errorState.value = null
    }

    private fun handleDisconnected() {
        Log.d(TAG, "Peer Network disconnected")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleError(error: String) {
        Log.e(TAG, "Error: $error")
        _connectionState.value = ConnectionState.ERROR
        _errorState.value = error
    }
}
