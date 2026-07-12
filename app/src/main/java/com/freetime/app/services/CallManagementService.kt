package com.freetime.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.freetime.app.MainActivity
import com.freetime.app.R
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.notifications.NotificationHelper
import com.freetime.app.webrtc.WebRTCManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.SessionDescription
import java.util.*

/**
 * Android Service for WebRTC Call Management.
 * Handles background call sessions, foreground service for microphone/camera access,
 * and unified call state management.
 */
class CallManagementService : Service() {

    private val binder = CallBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var apiService: FreeTimeApiService
    private var audioRouter: CallAudioRouter? = null
    
    private var webRtcManager: WebRTCManager? = null
    
    private val callSessions = mutableMapOf<String, CallSession>()
    
    inner class CallBinder : Binder() {
        fun getService(): CallManagementService = this@CallManagementService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 CallManagementService created")
        audioRouter = CallAudioRouter(this)
        apiService = FreeTimeApiService(this)
        
        // Ensure notification channels exist
        NotificationHelper.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallManagementService destroyed")
        cleanup()
    }

    // --- Core API ---

    fun initializeIncomingCallSession(
        callId: String,
        remoteUserId: String,
        remoteUsername: String,
        callType: CallType,
        offerSdp: String = ""  // ✅ CRITICAL: Accept offer from incoming call
    ) {
        if (callSessions.containsKey(callId)) return
        
        val session = CallSession(
            callId = callId,
            remoteUserId = remoteUserId,
            remoteUsername = remoteUsername,
            callType = callType,
            isInitiator = false,
            remoteSdpOffer = offerSdp  // ✅ CRITICAL: Store the offer
        )
        callSessions[callId] = session
        Log.d(TAG, "✅ Session initialized: $callId, offer available: ${offerSdp.isNotEmpty()}, offer length: ${offerSdp.length}")
    }

    fun startForegroundForCall(callId: String) {
        val session = callSessions[callId] ?: return
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("CHAT_ID", session.remoteUserId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.freetime_logo))
            .setColor(getColor(R.color.notification_color))
            .setContentTitle("Ongoing Call")
            .setContentText("In call with ${session.remoteUsername}")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    suspend fun initiateCall(
        remoteUserId: String,
        remoteUsername: String,
        callType: CallType
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val localCallId = UUID.randomUUID().toString()
            val session = CallSession(
                callId = localCallId,
                remoteUserId = remoteUserId,
                remoteUsername = remoteUsername,
                callType = callType,
                isInitiator = true
            )
            callSessions[localCallId] = session

            initWebRtc(localCallId, remoteUserId)
            webRtcManager?.createPeerConnection()
            
            if (callType == CallType.VIDEO) webRtcManager?.enableVideo(true)

            val offer = webRtcManager?.createOffer() ?: throw Exception("Offer creation failed")
            val offerJson = offer.toJson().toString()
            session.localSdpOffer = offerJson

            val result = apiService.initiateCall(remoteUserId, callType.name.lowercase(), offerJson)
            result.fold(
                onSuccess = { serverId ->
                    callSessions.remove(localCallId)
                    callSessions[serverId] = session.copy(callId = serverId)
                    Result.success(serverId)
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAndSendAnswer(callId: String): String? {
        return try {
            Log.d(TAG, "🎯 createAndSendAnswer START for callId=$callId")
            
            val session = callSessions[callId]
            if (session == null) {
                Log.e(TAG, "❌ Session not found for callId=$callId")
                return null
            }
            
            // Initialize WebRTC on the proper dispatcher (Main.immediate works with WebRTC callbacks)
            initWebRtc(callId, session.remoteUserId)
            
            Log.d(TAG, "🔧 Creating PeerConnection...")
            webRtcManager?.createPeerConnection()
            
            if (session.callType == CallType.VIDEO) {
                Log.d(TAG, "🎥 Enabling video for call")
                webRtcManager?.enableVideo(true)
            }

            // ✅ CRITICAL FIX: Set remote offer BEFORE creating answer
            if (session.remoteSdpOffer.isNotEmpty()) {
                Log.d(TAG, "📥 Setting remote SDP offer (length=${session.remoteSdpOffer.length})...")
                
                val offerSdp = if (session.remoteSdpOffer.startsWith("{")) {
                    try {
                        JSONObject(session.remoteSdpOffer).getString("sdp")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse offer JSON: ${e.message}")
                        session.remoteSdpOffer
                    }
                } else {
                    session.remoteSdpOffer
                }
                
                try {
                    webRtcManager?.setRemoteDescriptionSuspend(
                        SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                    )
                    Log.d(TAG, "✅ Remote offer set successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to set remote description: ${e.message}", e)
                    throw e
                }
            } else {
                Log.w(TAG, "⚠️ No offer SDP available for answer creation")
                return null
            }

            Log.d(TAG, "🎤 Creating answer SDP...")
            val answer = try {
                webRtcManager?.createAnswer()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create answer: ${e.message}", e)
                throw e
            }
            
            if (answer == null) {
                Log.e(TAG, "❌ createAnswer returned null")
                return null
            }
            
            val answerJson = answer.toJson().toString()
            session.remoteSdpAnswer = answerJson
            Log.d(TAG, "✅ Answer created successfully (length=${answerJson.length})")
            
            answerJson
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in createAndSendAnswer: ${e.message}", e)
            null
        }
    }

    suspend fun addRemoteIceCandidate(candidateSdp: String, sdpMLineIndex: Int, sdpMid: String?) {
        val candidate = org.webrtc.IceCandidate(sdpMid ?: "", sdpMLineIndex, candidateSdp)
        webRtcManager?.addIceCandidate(candidate)
    }

    fun endCall(callId: String) {
        serviceScope.launch {
            apiService.endCall(callId)
            cleanup()
            stopForegroundService()
            callSessions.remove(callId)
        }
    }

    fun rejectCall(callId: String) {
        serviceScope.launch {
            apiService.rejectCall(callId)
            cleanup()
            stopForegroundService()
            callSessions.remove(callId)
        }
    }

    fun setMuted(muted: Boolean) {
        // ✅ UNIFIED: Microphone control via WebRTC manager
        webRtcManager?.setMuted(muted)
        Log.d(TAG, if (muted) "Microphone muted" else "Microphone unmuted")
    }

    fun setSpeakerPhoneOn(enabled: Boolean) {
        // ✅ UNIFIED: Speakerphone control via audio router
        audioRouter?.setSpeakerphoneOn(enabled)
        Log.d(TAG, if (enabled) "Speakerphone enabled" else "Speakerphone disabled")
    }

    fun isSpeakerPhoneOn(): Boolean {
        // Note: Audio router doesn't expose speaker state getter
        // This would need to be tracked separately if needed
        return false
    }

    // --- Private Helpers ---

    private fun initWebRtc(callId: String, remoteUserId: String) {
        if (webRtcManager != null) return
        
        webRtcManager = WebRTCManager(
            this,
            onIceCandidate = { candidate ->
                // Prefer WebSocket signaling for ICE candidates (fast, avoids REST 404)
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val payload = JSONObject().apply {
                            put("callId", callId)
                            put("to", remoteUserId)
                            put("candidate", candidate.toJson())
                        }
                        com.freetime.app.services.WebSocketManager.getInstance().send("call:ice-candidate", payload)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to send ICE candidate via WebSocket: ${e.message}")
                        // Fallback: attempt REST send but don't crash if it fails
                        try { apiService.sendIceCandidate(callId, remoteUserId, candidate.toJson().toString()) } catch (_: Exception) {}
                    }
                }
            },
            onTrack = { /* Handle remote stream */ },
            onConnectionStateChanged = { state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    configureAudioForCall()
                    startForegroundForCall(callId)
                }
            }
        )
    }

    private fun configureAudioForCall() {
        // ✅ UNIFIED: Use CallAudioRouter for audio management
        Log.d(TAG, "🎧 Configuring audio for call using CallAudioRouter")
        // Enable speaker by default to ensure users can hear remote audio
        audioRouter?.initializeCallAudio(useSpeaker = true)
    }

    private fun cleanup() {
        // ✅ UNIFIED: Use CallAudioRouter for cleanup
        Log.d(TAG, "🧹 Cleaning up audio using CallAudioRouter")
        audioRouter?.cleanupCallAudio()
        webRtcManager?.close()
        webRtcManager = null
        serviceScope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val TAG = "CallMgmtService"
        private const val NOTIFICATION_ID = 8888
        
        fun start(context: Context) {
            val intent = Intent(context, CallManagementService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    data class CallSession(
        val callId: String,
        val remoteUserId: String,
        val remoteUsername: String,
        val callType: CallType,
        val isInitiator: Boolean = false,
        var localSdpOffer: String? = null,
        var remoteSdpOffer: String = "",  // ✅ CRITICAL: Store the offer from incoming call
        var remoteSdpAnswer: String? = null
    )

    enum class CallType { AUDIO, VIDEO }
}

fun SessionDescription.toJson(): JSONObject {
    val json = JSONObject()
    json.put("type", type.canonicalForm())
    json.put("sdp", description)
    return json
}

fun org.webrtc.IceCandidate.toJson(): JSONObject {
    val json = JSONObject()
    json.put("sdp", sdp)
    json.put("sdpMid", sdpMid)
    json.put("sdpMLineIndex", sdpMLineIndex)
    return json
}
