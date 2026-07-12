package com.freetime.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freetime.app.services.CallManagementService
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.ui.components.CyberpunkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.ui.graphics.Color

/**
 * ViewModel for managing active call state and WebRTC integration
 * Handles UI state for active calls, connection status, and call controls
 */
class CallViewModel(
    private val context: Context,
    private val callManager: CallManagementService,
    private val apiService: FreeTimeApiService
) : ViewModel() {
    
    companion object {
        private const val TAG = "CallViewModel"
    }

    // Call state
    private val _callId = MutableStateFlow<String?>(null)
    val callId: StateFlow<String?> = _callId.asStateFlow()

    private val _remoteName = MutableStateFlow("")
    val remoteName: StateFlow<String> = _remoteName.asStateFlow()

    private val _remoteUserId = MutableStateFlow("")
    val remoteUserId: StateFlow<String> = _remoteUserId.asStateFlow()

    private val _callType = MutableStateFlow("audio") // audio or video
    val callType: StateFlow<String> = _callType.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _callDuration = MutableStateFlow(0L) // in seconds
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private val _connectionStatus = MutableStateFlow("connecting") // connecting, connected, disconnected, failed
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _connectionQuality = MutableStateFlow("good") // excellent, good, fair, poor
    val connectionQuality: StateFlow<String> = _connectionQuality.asStateFlow()

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var durationJob: kotlinx.coroutines.Job? = null

    /**
     * Start outgoing call
     */
    fun initiateCall(recipientId: String, recipientName: String, callType: String = "audio") {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initiating $callType call to $recipientName")
                _remoteName.value = recipientName
                _remoteUserId.value = recipientId
                _callType.value = callType
                _isVideoEnabled.value = (callType == "video")
                _connectionStatus.value = "connecting"
                _isCallActive.value = true

                val callTypeEnum = if (callType == "video") {
                    CallManagementService.CallType.VIDEO
                } else {
                    CallManagementService.CallType.AUDIO
                }

                val result = callManager.initiateCall(
                    remoteUserId = recipientId,
                    remoteUsername = recipientName,
                    callType = callTypeEnum
                )

                result.fold(
                    onSuccess = { serverCallId ->
                        _callId.value = serverCallId
                        _connectionStatus.value = "connected"
                        startDurationTimer()
                        Log.d(TAG, "✅ Call initiated: $serverCallId")
                    },
                    onFailure = { error ->
                        _connectionStatus.value = "failed"
                        _errorMessage.value = "Failed to initiate call: ${error.message}"
                        _isCallActive.value = false
                        Log.e(TAG, "❌ Call initiation failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _connectionStatus.value = "failed"
                _errorMessage.value = "Exception during call: ${e.message}"
                _isCallActive.value = false
                Log.e(TAG, "Exception: ${e.message}", e)
            }
        }
    }

    /**
     * Accept incoming call
     */
    fun acceptCall(callId: String, remoteUserId: String, remoteName: String, callType: String = "audio") {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Accepting call from $remoteName")
                _callId.value = callId
                _remoteUserId.value = remoteUserId
                _remoteName.value = remoteName
                _callType.value = callType
                _isVideoEnabled.value = (callType == "video")
                _connectionStatus.value = "connected"
                _isCallActive.value = true
                startDurationTimer()
                Log.d(TAG, "✅ Call accepted: $callId")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to accept call: ${e.message}"
                _isCallActive.value = false
                Log.e(TAG, "Exception accepting call: ${e.message}")
            }
        }
    }

    /**
     * Toggle mute state
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        Log.d(TAG, if (_isMuted.value) "🔇 Muted" else "🔊 Unmuted")
    }

    /**
     * Toggle speaker
     */
    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        Log.d(TAG, if (_isSpeakerOn.value) "🔊 Speaker on" else "📱 Speaker off")
    }

    /**
     * Toggle video (if available)
     */
    fun toggleVideo(enable: Boolean) {
        if (_callType.value == "video") {
            _isVideoEnabled.value = enable
            Log.d(TAG, if (enable) "📷 Video enabled" else "📷 Video disabled")
        }
    }

    /**
     * End call
     */
    fun endCall() {
        viewModelScope.launch {
            try {
                val currentCallId = _callId.value
                if (currentCallId != null) {
                    stopDurationTimer()
                    callManager.endCall(currentCallId)
                    _isCallActive.value = false
                    _connectionStatus.value = "disconnected"
                    clearCallState()
                    Log.d(TAG, "✅ Call ended: $currentCallId, duration: ${_callDuration.value}s")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Exception ending call: ${e.message}"
                _isCallActive.value = false
            }
        }
    }

    /**
     * Start call duration timer
     */
    private fun startDurationTimer() {
        stopDurationTimer()
        _callDuration.value = 0L
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDuration.value += 1
                
                // Update quality based on duration (in real implementation, based on actual metrics)
                _connectionQuality.value = when {
                    _callDuration.value < 5 -> "connecting"
                    _callDuration.value < 30 -> "excellent"
                    _callDuration.value < 300 -> "good"
                    else -> "fair"
                }
            }
        }
    }

    /**
     * Stop call duration timer
     */
    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    /**
     * Clear call state on disconnect
     */
    private fun clearCallState() {
        _callId.value = null
        _remoteName.value = ""
        _remoteUserId.value = ""
        _callType.value = "audio"
        _isVideoEnabled.value = false
        _isMuted.value = false
        _isSpeakerOn.value = true
        _callDuration.value = 0L
        _connectionStatus.value = "disconnected"
        // Keep error message for a moment so user can see it
    }

    /**
     * Get formatted call duration string (MM:SS)
     */
    fun getFormattedDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Get connection status color
     */
    fun getStatusColor(status: String): Color {
        return when (status) {
            "connecting" -> Color.Yellow
            "connected" -> CyberpunkTheme.PrimaryPurple
            "disconnected" -> Color.Gray
            "failed" -> Color.Red
            else -> Color.White
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDurationTimer()
    }
}
