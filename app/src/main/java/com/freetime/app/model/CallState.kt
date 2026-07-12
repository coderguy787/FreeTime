package com.freetime.app.model

/**
 * Call state enum for voice call lifecycle management
 */
enum class CallState {
    INITIATING,      // User clicked call button
    CONNECTING,      // WebRTC connecting
    RINGING,         // Call ringing from remote
    ACTIVE,          // Call in progress
    ENDED,           // Call ended normally
    FAILED           // Call failed (network error, rejection, timeout, etc)
}
