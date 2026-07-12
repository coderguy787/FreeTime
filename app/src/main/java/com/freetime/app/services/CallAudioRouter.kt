package com.freetime.app.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.AudioTrack

/**
 * ✅ UNIFIED CALLING SYSTEM - Audio Routing Manager
 *
 * Handles proper audio device routing for WebRTC calls:
 * - Remote audio track connection to speaker/earpiece
 * - Audio focus management
 * - Microphone and speaker control
 * - Audio mode switching for calls
 */
class CallAudioRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false

    companion object {
        private const val TAG = "CallAudioRouter"
    }

    /**
     * ✅ Initialize audio for call mode
     * Switches to in-communication audio mode and requests audio focus
     */
    fun initializeCallAudio(useSpeaker: Boolean = false) {
        Log.d(TAG, "📱 Initializing call audio (useSpeaker=$useSpeaker)")

        try {
            // Save previous state for restoration later
            previousAudioMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            Log.d(TAG, "  📋 Saved audio state: mode=$previousAudioMode, speaker=$previousSpeakerphoneOn")

            // ✅ CRITICAL: Set audio mode to MODE_IN_COMMUNICATION
            // This tells Android we're in a call, enabling proper echo cancellation
            Log.d(TAG, "  🔄 Setting audio mode to MODE_IN_COMMUNICATION (value=2)")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "  ✅ Audio mode set to: ${audioManager.mode}")

            // ✅ Request audio focus for voice communication
            Log.d(TAG, "  🔊 Requesting audio focus for VOICE_COMMUNICATION")
            requestAudioFocus()
            Log.d(TAG, "  ✅ Audio focus requested")

            // Set speaker output preference
            Log.d(TAG, "  📢 Setting speakerphone to: $useSpeaker")
            audioManager.isSpeakerphoneOn = useSpeaker
            Log.d(TAG, "  ✅ Speakerphone enabled: ${audioManager.isSpeakerphoneOn}")

            Log.d(TAG, "✅ Call audio initialized - Mode: IN_COMMUNICATION, Speaker: $useSpeaker")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize audio: ${e.message}", e)
            e.printStackTrace()
        }
    }

    /**
     * ✅ Route remote audio track to speaker/earpiece
     * This is called after WebRTC's onTrack receives a remote audio track
     */
    fun routeRemoteAudio(audioTrack: AudioTrack, useSpeaker: Boolean = false) {
        Log.d(TAG, "🔊 Routing remote audio track (speaker=$useSpeaker)")

        try {
            // Ensure audio track is enabled
            Log.d(TAG, "  🔄 Enabling remote audio track...")
            audioTrack.setEnabled(true)
            val trackEnabled = audioTrack.enabled()
            Log.d(TAG, "  ✅ Remote audio track enabled: $trackEnabled")

            // Configure speaker output
            Log.d(TAG, "  📢 Setting speakerphone to: $useSpeaker")
            audioManager.isSpeakerphoneOn = useSpeaker
            val speakerActual = audioManager.isSpeakerphoneOn
            Log.d(TAG, "  ✅ Speakerphone is now: $speakerActual")

            // Ensure audio focus is held
            Log.d(TAG, "  🔊 Ensuring audio focus is held...")
            requestAudioFocus()
            Log.d(TAG, "  ✅ Audio focus maintained")

            Log.d(TAG, "✅ Remote audio routed successfully (speaker=$speakerActual)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to route remote audio: ${e.message}", e)
            e.printStackTrace()
        }
    }

    /**
     * ✅ Control microphone (mute/unmute)
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        Log.d(TAG, "🎤 Setting microphone enabled=$enabled")
        // Note: Actual microphone control is handled by WebRTCManager
        // This method is here for consistency with audio router interface
    }

    /**
     * ✅ Control speaker output
     */
    fun setSpeakerphoneOn(enabled: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = enabled
            Log.d(TAG, "📢 Speakerphone set to: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to control speakerphone: ${e.message}", e)
        }
    }

    /**
     * ✅ Cleanup audio after call ends
     * Restores previous audio state
     */
    fun cleanupCallAudio() {
        Log.d(TAG, "🧹 ========== CLEANING UP CALL AUDIO ==========")
        Log.d(TAG, "  📊 Current state: mode=${audioManager.mode}, speaker=${audioManager.isSpeakerphoneOn}")
        Log.d(TAG, "  📊 Previous state: mode=$previousAudioMode, speaker=$previousSpeakerphoneOn")

        try {
            // Restore previous audio mode
            Log.d(TAG, "  🔄 Restoring audio mode to: $previousAudioMode")
            audioManager.mode = previousAudioMode
            Log.d(TAG, "    ✅ Audio mode restored (now: ${audioManager.mode})")
            
            Log.d(TAG, "  🔄 Restoring speakerphone to: $previousSpeakerphoneOn")
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
            Log.d(TAG, "    ✅ Speakerphone restored (now: ${audioManager.isSpeakerphoneOn})")

            // Release audio focus
            Log.d(TAG, "  🎯 Abandoning audio focus...")
            abandonAudioFocus()
            Log.d(TAG, "    ✅ Audio focus abandoned")

            Log.d(TAG, "✅ Audio cleaned up successfully")
            Log.d(TAG, "🧹 ========== AUDIO CLEANUP COMPLETE ==========")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cleanup audio: ${e.message}", e)
            e.printStackTrace()
        }
    }

    // ===== PRIVATE HELPERS =====

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()

                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                Log.d(TAG, "🎯 Audio focus requested: ${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "DENIED"}")
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d(TAG, "🎯 Audio focus requested (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to request audio focus: ${e.message}", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            Log.d(TAG, "✅ Audio focus abandoned")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to abandon audio focus: ${e.message}", e)
        }
    }
}
