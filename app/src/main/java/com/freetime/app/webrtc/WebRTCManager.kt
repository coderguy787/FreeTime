package com.freetime.app.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.freetime.app.services.CallAudioRouter
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Singleton WebRTC Factory to manage shared WebRTC resources.
 * Ensures proper resource management and prevents JNI crashes.
 */
object WebRTCFactory {
    private const val TAG = "WebRTCFactory"
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var isInitialized = false
    private var activeManager: WebRTCManager? = null

    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // Initialize EGL base
            eglBase = EglBase.create()

            // Initialize PeerConnectionFactory with proper options
            val options = PeerConnectionFactory.Options().apply {
                // Disable network monitoring to prevent threading issues
                disableNetworkMonitor = true
            }

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
                .setOptions(options)
                .createPeerConnectionFactory()

            isInitialized = true
            Log.d(TAG, "WebRTC Factory initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC Factory: ${e.message}")
            throw e
        }
    }

    fun getPeerConnectionFactory(): PeerConnectionFactory {
        if (!isInitialized) {
            throw IllegalStateException("WebRTCFactory not initialized. Call initialize() first.")
        }
        return peerConnectionFactory!!
    }

    fun getEglBase(): EglBase {
        if (!isInitialized) {
            throw IllegalStateException("WebRTCFactory not initialized. Call initialize() first.")
        }
        return eglBase!!
    }

    fun setActiveManager(manager: WebRTCManager?) {
        // ✅ FIX: Prevent circular reference - check if already the same manager
        if (activeManager === manager) {
            return  // Already set to this manager, no need to close and re-set
        }
        
        // ✅ FIX: Set to null FIRST before trying to close old manager (prevent re-entry)
        val oldManager = activeManager
        activeManager = manager
        
        // Close previous manager if different
        if (oldManager != null && oldManager != manager) {
            Log.d(TAG, "Closing previous WebRTC manager to prevent conflicts")
            try {
                oldManager.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing previous manager: ${e.message}")
            }
        }
    }

    fun getActiveManager(): WebRTCManager? = activeManager

    fun hasActiveCall(): Boolean = activeManager != null && activeManager!!.isActive()

    fun dispose() {
        try {
            activeManager?.close()
            activeManager = null
            peerConnectionFactory?.dispose()
            eglBase?.release()
            peerConnectionFactory = null
            eglBase = null
            isInitialized = false
            Log.d(TAG, "WebRTC Factory disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRTC Factory: ${e.message}")
        }
    }
}

/**
 * WebRTC Manager to encapsulate all WebRTC related logic.
 * Handles PeerConnection setup, SDP offer/answer creation, and ICE candidate management.
 */
class WebRTCManager(
    private val context: Context,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onTrack: (MediaStream) -> Unit,
    private val onConnectionStateChanged: ((PeerConnectionState) -> Unit)? = null,
    private val onIceConnectionFailed: ((IceConnectionState?, Boolean) -> Unit)? = null
) {

    companion object {
        private const val TAG = "WebRTCManager"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val ICE_CANDIDATE_DEBOUNCE_MS = 50L // Debounce ICE candidates
    }

    private val audioRouter = CallAudioRouter(context)

    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: org.webrtc.AudioTrack? = null
    private var localAudioSource: AudioSource? = null  // ✅ FIX: Store audio source for proper cleanup
    private var localVideoTrack: org.webrtc.VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isReleased = false
    val isReleasedPublic: Boolean get() = isReleased
    private val iceServers = listOf(
        IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    // ICE candidate debouncing
    private var lastIceCandidateSentTime = 0L
    private var iceCandidateBuffer = mutableListOf<IceCandidate>()
    private var iceCandidateScope: CoroutineScope? = null
    private var isClosing = false  // ✅ FIX: Prevent re-entry during close()

    init {
        // Initialize WebRTC factory if not already done
        WebRTCFactory.initialize(context)
        // Register this manager as active
        WebRTCFactory.setActiveManager(this)
        // Initialize coroutine scope for async ICE candidate handling
        iceCandidateScope = CoroutineScope(Dispatchers.IO)
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "ICE candidate generated: ${it.sdp}")
                // Debounce and batch ICE candidates to prevent flooding
                debounceAndSendIceCandidate(it)
            }
        }

        override fun onAddStream(stream: MediaStream?) {
            stream?.let {
                for (audioTrack in it.audioTracks) {
                    audioTrack.setEnabled(true)
                }
                Log.d(TAG, "🎵 onAddStream: received stream with ${it.audioTracks.size} audio and ${it.videoTracks.size} video tracks")
                onTrack(it)
            }
        }
        
        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(TAG, "🎵 ========== onTrack CALLBACK FIRED ==========")
            try {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "  📊 Transceiver: ${transceiver?.hashCode()}")
                Log.d(TAG, "  📊 Receiver: ${transceiver?.receiver?.hashCode()}")
                Log.d(TAG, "  📊 Track: ${track?.hashCode()}")
                Log.d(TAG, "  📊 Track kind: ${track?.kind()}")
                Log.d(TAG, "  📊 Track ID: ${track?.id()}")
                Log.d(TAG, "  📊 Track enabled: ${track?.enabled()}")
                
                // ✅ CRITICAL FIX: Route remote audio track properly using CallAudioRouter
                track?.let {
                    Log.d(TAG, "  🔄 Configuring track...")
                    it.setEnabled(true)
                    Log.d(TAG, "  ✅ Track enabled (enabled=${it.enabled()})")
                    
                    when (it.kind()) {
                        "audio" -> {
                            Log.d(TAG, "  🔊 ========== REMOTE AUDIO TRACK DETECTED ==========")
                            val audioTrack = it as? org.webrtc.AudioTrack
                            if (audioTrack == null) {
                                Log.e(TAG, "  ❌ Failed to cast to AudioTrack (type: ${it.javaClass.simpleName})")
                            } else {
                                Log.d(TAG, "  📊 AudioTrack cast successful")
                                Log.d(TAG, "  🎵 Routing to CallAudioRouter (useSpeaker=true)...")
                                // ✅ UNIFIED: Use CallAudioRouter to properly connect audio output
                                // Default to speaker = true to ensure audio is audible on most devices/emulators
                                try {
                                    audioRouter.routeRemoteAudio(audioTrack, useSpeaker = true)
                                    Log.d(TAG, "  ✅ Audio routed successfully")
                                } catch (e: Exception) {
                                    Log.e(TAG, "  ❌ Failed to route audio: ${e.message}", e)
                                }
                            }
                            // Notify with the track (PeerConnection manages MediaStream internally)
                            Log.d(TAG, "  ✅ Remote audio track routed and enabled")
                        }
                        "video" -> {
                            Log.d(TAG, "  📹 Remote video track received (not yet supported)")
                        }
                        else -> {
                            Log.d(TAG, "  ❓ Unknown track kind: ${it.kind()}")
                        }
                    }
                } ?: run {
                    Log.e(TAG, "  ❌ Track is null!")
                }
                Log.d(TAG, "🎵 ========== onTrack CALLBACK COMPLETE ==========")
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ Exception in onTrack: ${e.message}", e)
                e.printStackTrace()
            }
        }

        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onIceConnectionChange(newState: IceConnectionState?) {
            if (newState == IceConnectionState.DISCONNECTED || newState == IceConnectionState.FAILED) {
                onIceConnectionFailed?.invoke(newState, true)
            }
        }

        override fun onConnectionChange(newState: PeerConnectionState?) {
            newState?.let { onConnectionStateChanged?.invoke(it) }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: IceGatheringState?) {}
        override fun onSignalingChange(newState: SignalingState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
    }

    /**
     * Debounce and batch ICE candidates to prevent rapid-fire sending
     * This prevents blocking the signaling thread and reduces network overhead
     */
    private fun debounceAndSendIceCandidate(candidate: IceCandidate) {
        synchronized(iceCandidateBuffer) {
            iceCandidateBuffer.add(candidate)
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastIceCandidateSentTime >= ICE_CANDIDATE_DEBOUNCE_MS) {
                // Enough time has passed, send immediately
                sendBufferedIceCandidates()
                lastIceCandidateSentTime = currentTime
            } else {
                // Schedule async send after debounce period
                iceCandidateScope?.launch {
                    kotlinx.coroutines.delay(ICE_CANDIDATE_DEBOUNCE_MS)
                    synchronized(iceCandidateBuffer) {
                        if (iceCandidateBuffer.isNotEmpty()) {
                            sendBufferedIceCandidates()
                        }
                    }
                }
            }
        }
    }

    private fun sendBufferedIceCandidates() {
        if (iceCandidateBuffer.isEmpty()) return
        
        val candidates = iceCandidateBuffer.toList()
        iceCandidateBuffer.clear()
        
        // Send asynchronously on IO thread to not block signaling thread
        iceCandidateScope?.launch(Dispatchers.IO) {
            for (candidate in candidates) {
                try {
                    // Wrap in try-catch to prevent one failure from affecting others
                    onIceCandidate(candidate)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending ICE candidate: ${e.message}")
                }
            }
        }
    }

    fun createPeerConnection() {
        try {
            // Check for RECORD_AUDIO permission before attempting to use it
            val hasAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            Log.d(TAG, "createPeerConnection - hasAudioPermission: $hasAudioPermission")
            
            if (!hasAudioPermission) {
                val errorMsg = "RECORD_AUDIO permission not granted - cannot create PeerConnection"
                Log.e(TAG, errorMsg)
                throw SecurityException(errorMsg)
            }
            
            setupAudioForCall()
            val factory = WebRTCFactory.getPeerConnectionFactory()
            peerConnection = factory.createPeerConnection(iceServers, peerConnectionObserver)
            
            if (peerConnection == null) {
                throw IllegalStateException("Failed to create PeerConnection from factory")
            }
            
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            }
            
            val audioSource = factory.createAudioSource(audioConstraints)
            localAudioSource = audioSource  // ✅ FIX: Store for cleanup
            val audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
            localAudioTrack = audioTrack
            audioTrack.setEnabled(true)
            
            // ✅ FIXED: Use empty stream ID list for proper WebRTC negotiation (not "ARDAMS")
            peerConnection?.addTrack(audioTrack, emptyList())
            Log.d(TAG, "✅ Local audio track added to PeerConnection with ID: $AUDIO_TRACK_ID")
            
            pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
            pendingIceCandidates.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating peer connection: ${e.message}", e)
        }
    }
    
    fun isActive(): Boolean {
        return peerConnection != null && !isReleased
    }

    fun enableVideo(enabled: Boolean) {
        if (enabled) {
            if (localVideoTrack == null) {
                try {
                    val factory = WebRTCFactory.getPeerConnectionFactory()
                    videoCapturer = createVideoCapturer()
                    if (videoCapturer != null && videoSource == null) {
                        videoSource = factory.createVideoSource(false)
                        videoCapturer?.initialize(SurfaceTextureHelper.create("VideoCapturerThread", WebRTCFactory.getEglBase().eglBaseContext), context, videoSource?.capturerObserver)
                        videoCapturer?.startCapture(1280, 720, 30)
                        
                        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
                        localVideoTrack?.setEnabled(true)
                        // ✅ FIXED: Use empty stream ID list for proper WebRTC negotiation (not "ARDAMS")
                        peerConnection?.addTrack(localVideoTrack, emptyList())
                        Log.d(TAG, "✅ Video track added with ID: $VIDEO_TRACK_ID")
                    } else if (videoCapturer == null) {
                        Log.w(TAG, "⚠️ No camera device available for video capture")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error enabling video: ${e.message}", e)
                    videoCapturer = null
                    videoSource = null
                    localVideoTrack = null
                }
            } else {
                localVideoTrack?.setEnabled(true)
                videoCapturer?.startCapture(1280, 720, 30)
            }
        } else {
            localVideoTrack?.setEnabled(false)
            videoCapturer?.stopCapture()
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            var capturer: VideoCapturer? = null
            
            // Try front camera first
            for (deviceName in deviceNames) {
                if (capturer == null && enumerator.isFrontFacing(deviceName)) {
                    capturer = enumerator.createCapturer(deviceName, null)
                    if (capturer != null) {
                        Log.d(TAG, "✅ Front camera capturer created")
                        break
                    }
                }
            }
            
            // Fallback to back camera if front not found
            if (capturer == null) {
                for (deviceName in deviceNames) {
                    if (enumerator.isBackFacing(deviceName)) {
                        capturer = enumerator.createCapturer(deviceName, null)
                        if (capturer != null) {
                            Log.d(TAG, "✅ Back camera capturer created")
                            break
                        }
                    }
                }
            }
            
            if (capturer == null) {
                Log.w(TAG, "⚠️ No camera device available")
            }
            capturer
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating video capturer: ${e.message}", e)
            null
        }
    }
    
    private fun setupAudioForCall() {
        try {
            // ✅ UNIFIED: Use CallAudioRouter for proper audio management
            Log.d(TAG, "🎧 Setting up audio for call using CallAudioRouter")
            audioRouter.initializeCallAudio(useSpeaker = false)  // Use earpiece for calls
            Log.d(TAG, "✅ Audio setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio: ${e.message}", e)
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }
    
    fun setSpeakerPhoneOn(enabled: Boolean) {
        // ✅ UNIFIED: Speakerphone control via audio router
        audioRouter.setSpeakerphoneOn(enabled)
    }
    
    fun isSpeakerPhoneOn(): Boolean {
        // Note: Would need separate tracking in CallAudioRouter if needed
        return false
    }

    suspend fun createOffer(): SessionDescription {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        val offer = peerConnection?.createOfferSdp(sdpConstraints)
            ?: throw IllegalStateException("Failed to create SDP offer")
        peerConnection?.setLocalDescriptionSuspend(offer)
        return offer
    }

    suspend fun createAnswer(): SessionDescription {
        Log.d(TAG, "🔄 Creating SDP answer (OfferToReceiveAudio=true, OfferToReceiveVideo=true)")
        try {
            val sdpConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            Log.d(TAG, "📋 SDP constraints set: audio=true, video=true")
            
            val answer = peerConnection?.createAnswerSdp(sdpConstraints)
                ?: throw IllegalStateException("Failed to create SDP answer - peerConnection is null or returned null")
            
            Log.d(TAG, "✅ SDP answer created (length=${answer.description.length} chars)")
            Log.d(TAG, "🔄 Setting local description...")
            
            peerConnection?.setLocalDescriptionSuspend(answer)
            Log.d(TAG, "✅ Local description set successfully")
            
            return answer
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create answer: ${e.message}", e)
            throw e
        }
    }

    suspend fun setRemoteDescriptionSuspend(sdp: SessionDescription) {
        Log.d(TAG, "🔄 Setting remote SDP (type=${sdp.type}, length=${sdp.description.length} chars)")
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                if (peerConnection == null) {
                    Log.e(TAG, "❌ Cannot set remote description: peerConnection is null")
                    continuation.resumeWithException(RuntimeException("peerConnection is null"))
                    return@suspendCancellableCoroutine
                }
                
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "✅ Remote description set successfully")
                        Log.d(TAG, "🎤 Enabling local audio track")
                        try {
                            localAudioTrack?.setEnabled(true)
                            Log.d(TAG, "✅ Local audio track enabled")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to enable local audio track: ${e.message}")
                        }
                        continuation.resume(Unit)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "❌ Failed to set remote description: $error")
                        continuation.resumeWithException(RuntimeException(error))
                    }
                }, sdp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception setting remote description: ${e.message}", e)
            throw e
        }
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() { localAudioTrack?.setEnabled(true) }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (peerConnection != null) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.add(candidate)
        }
    }

    fun close() {
        // ✅ FIX: Prevent recursive calls to close()
        Log.d(TAG, "🛑 ========== CLOSING WebRTC CONNECTION ==========")
        Log.d(TAG, "  📊 isClosing=$isClosing, isReleased=$isReleased")
        
        if (isClosing || isReleased) {
            Log.w(TAG, "  ⚠️ Already closing or released - skipping close()")
            return
        }
        isClosing = true
        
        try {
            // ✅ UNIFIED: Use CallAudioRouter for cleanup
            Log.d(TAG, "  🧹 Cleaning up audio using CallAudioRouter...")
            audioRouter.cleanupCallAudio()
            Log.d(TAG, "  ✅ Audio cleanup complete")
            
            // ✅ FIX: Stop and dispose audio source to release microphone
            Log.d(TAG, "  🎤 Disabling and disposing audio tracks...")
            try {
                localAudioTrack?.setEnabled(false)
                Log.d(TAG, "    ✅ Local audio track disabled")
            } catch (e: Exception) {
                Log.e(TAG, "    ❌ Failed to disable audio track: ${e.message}")
            }
            
            try {
                localAudioTrack?.dispose()
                Log.d(TAG, "    ✅ Local audio track disposed")
            } catch (e: Exception) {
                Log.e(TAG, "    ❌ Failed to dispose audio track: ${e.message}")
            }
            
            try {
                localAudioSource?.dispose()  // ✅ FIX: Dispose audio source to release microphone
                Log.d(TAG, "    ✅ Local audio source disposed (microphone released)")
            } catch (e: Exception) {
                Log.e(TAG, "    ❌ Failed to dispose audio source: ${e.message}")
            }
            
            Log.d(TAG, "  📹 Disabling and disposing video tracks...")
            try {
                localVideoTrack?.dispose()
                Log.d(TAG, "    ✅ Local video track disposed")
                videoCapturer?.stopCapture()
                Log.d(TAG, "    ✅ Video capture stopped")
                videoCapturer?.dispose()
                Log.d(TAG, "    ✅ Video capturer disposed")
                videoSource?.dispose()
                Log.d(TAG, "    ✅ Video source disposed")
            } catch (e: Exception) {
                Log.e(TAG, "    ❌ Failed to dispose video: ${e.message}")
            }
            
            Log.d(TAG, "  🔌 Closing peer connection...")
            try {
                peerConnection?.close()
                Log.d(TAG, "    ✅ Peer connection closed")
            } catch (e: Exception) {
                Log.e(TAG, "    ❌ Failed to close peer connection: ${e.message}")
            }
            
            // Cleanup coroutine scope
            Log.d(TAG, "  🧹 Cleaning up ICE candidates buffer...")
            synchronized(iceCandidateBuffer) {
                iceCandidateBuffer.clear()
            }
            iceCandidateScope = null
            
            // ✅ FIX: Unregister from factory only if we're the active manager (prevent circular calls)
            try {
                if (WebRTCFactory.getActiveManager() === this) {
                    WebRTCFactory.setActiveManager(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering from factory: ${e.message}")
            }
            // Note: Don't dispose shared WebRTCFactory resources here
            isReleased = true
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTCManager: ${e.message}")
        }
    }

suspend fun PeerConnection.createOfferSdp(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    continuation.resume(sdp)
                } else {
                    continuation.resumeWithException(RuntimeException("Offer creation returned null SDP"))
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                continuation.resumeWithException(RuntimeException("Offer creation failed: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

suspend fun PeerConnection.createAnswerSdp(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    continuation.resume(sdp)
                } else {
                    continuation.resumeWithException(RuntimeException("Answer creation returned null SDP"))
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                continuation.resumeWithException(RuntimeException("Answer creation failed: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription?) {
    if (sdp == null) return
    suspendCancellableCoroutine { continuation ->
        setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() { 
                continuation.resume(Unit) 
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                continuation.resumeWithException(RuntimeException("Failed to set local description: $error"))
            }
        }, sdp)
    }
}
}

fun SessionDescription.toJson(): JSONObject {
    val json = JSONObject()
    json.put("type", type.canonicalForm())
    json.put("sdp", description)
    return json
}

fun IceCandidate.toJson(): JSONObject {
    val json = JSONObject()
    json.put("sdp", sdp)
    json.put("sdpMid", sdpMid)
    json.put("sdpMLineIndex", sdpMLineIndex)
    return json
}
