package com.freetime.app.services

import android.util.Log
import com.freetime.app.model.CallState
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe call state manager that prevents race conditions
 * in call state transitions from multiple threads (WebSocket, UI, timeouts)
 */
class CallStateManager {
    private val lock = ReentrantReadWriteLock()
    private var currentState: CallState? = null
    private var currentCallId: String = ""
    private var listeners = mutableListOf<(CallState?) -> Unit>()
    
    companion object {
        private const val TAG = "CallStateManager"
    }
    
    /**
     * Get current call state in a thread-safe way
     */
    fun getState(): CallState? = lock.read { currentState }
    
    /**
     * Get current call ID in a thread-safe way
     */
    fun getCallId(): String = lock.read { currentCallId }
    
    /**
     * Check if a call is currently active/in progress
     */
    fun isCallActive(): Boolean = lock.read {
        currentState == CallState.ACTIVE || 
        currentState == CallState.RINGING ||
        currentState == CallState.CONNECTING
    }
    
    /**
     * Update call state with validation of state transitions
     * Returns true if state was changed, false if transition was invalid/ignored
     */
    fun updateState(newState: CallState?, callId: String = ""): Boolean = lock.write {
        val previousState = currentState
        
        // Prevent invalid state transitions
        if (!isValidTransition(previousState, newState)) {
            Log.w(TAG, "Invalid state transition: $previousState -> $newState. Ignoring.")
            return@write false
        }
        
        currentState = newState
        if (callId.isNotEmpty()) {
            currentCallId = callId
        }
        
        Log.d(TAG, "CallState transition: $previousState -> $newState (callId=$currentCallId)")
        
        // Notify listeners
        listeners.forEach { it(newState) }
        
        return@write true
    }
    
    /**
     * Clear call state (called when call ends)
     */
    fun clearState() {
        lock.write {
            currentState = null
            currentCallId = ""
            Log.d(TAG, "Call state cleared")
            listeners.forEach { it(null) }
        }
    }
    
    /**
     * Register listener for state changes
     */
    fun addListener(listener: (CallState?) -> Unit) = lock.write {
        listeners.add(listener)
    }
    
    /**
     * Remove listener
     */
    fun removeListener(listener: (CallState?) -> Unit) = lock.write {
        listeners.remove(listener)
    }
    
    /**
     * Validate state transitions to prevent invalid combinations
     */
    private fun isValidTransition(from: CallState?, to: CallState?): Boolean {
        // Null -> any state is valid (starting a call)
        if (from == null) return true
        
        // Valid transitions
        return when {
            // From INITIATING
            from == CallState.INITIATING && (to == CallState.RINGING || to == CallState.FAILED) -> true
            
            // From RINGING
            from == CallState.RINGING && (to == CallState.ACTIVE || to == CallState.CONNECTING || to == CallState.ENDED || to == CallState.FAILED) -> true
            
            // From CONNECTING
            from == CallState.CONNECTING && (to == CallState.ACTIVE || to == CallState.ENDED || to == CallState.FAILED) -> true
            
            // From ACTIVE
            from == CallState.ACTIVE && (to == CallState.ENDED || to == CallState.FAILED) -> true
            
            // From FAILED or ENDED, can only clear (go to null)
            (from == CallState.FAILED || from == CallState.ENDED) && to == null -> true
            
            // Stay in same state is allowed
            from == to -> true
            
            else -> false
        }
    }
}
