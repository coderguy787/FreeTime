package com.freetime.app.data.repository

import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.api.FreeTimeApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Call Repository - Handles voice/video call operations via real API
 */
class CallRepository(
    private val prefs: SharedPreferencesHelper,
    private val context: android.content.Context
) {
    private val apiService = FreeTimeApiService(context)
    private val _callHistory = MutableStateFlow<List<String>>(emptyList())
    val callHistory: StateFlow<List<String>> = _callHistory

    /**
     * Initiate a call to a recipient
     */
    suspend fun initiateCall(recipientId: String, callType: String = "voice"): Result<String> {
        return try {
            val result = apiService.initiateSimpleCall(recipientId, callType)
            
            result.fold(
                onSuccess = { callId ->
                    // Add to call history
                    val currentHistory = _callHistory.value.toMutableList()
                    currentHistory.add(0, "$callType call to $recipientId (ID: $callId)")
                    _callHistory.value = currentHistory.take(20) // Keep last 20 calls
                    Result.success(callId)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Answer an incoming call
     */
    suspend fun answerCall(callId: String, answerSdp: String): Result<String> {
        return try {
            val result = apiService.answerCall(callId, answerSdp)
            
            result.fold(
                onSuccess = { response ->
                    // Add to call history
                    val currentHistory = _callHistory.value.toMutableList()
                    currentHistory.add(0, "Answered call $callId")
                    _callHistory.value = currentHistory.take(20)
                    // Return the callId or message from response
                    Result.success(response.callId)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * End an active call
     */
    suspend fun endCall(callId: String): Result<String> {
        return try {
            val result = apiService.endCall(callId)
            
            result.fold(
                onSuccess = { response ->
                    // Add to call history
                    val currentHistory = _callHistory.value.toMutableList()
                    currentHistory.add(0, "Ended call $callId")
                    _callHistory.value = currentHistory.take(20)
                    Result.success(response)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Reject an incoming call
     */
    suspend fun rejectCall(callId: String): Result<String> {
        return try {
            val result = apiService.rejectCall(callId)
            
            result.fold(
                onSuccess = { response ->
                    // Add to call history
                    val currentHistory = _callHistory.value.toMutableList()
                    currentHistory.add(0, "Rejected call $callId")
                    _callHistory.value = currentHistory.take(20)
                    Result.success(response)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get call history flow
     */
    fun getCallHistoryFlow(): Flow<List<String>> = _callHistory

    /**
     * Clear call history
     */
    fun clearCallHistory() {
        _callHistory.value = emptyList()
    }

    /**
     * Add ICE candidate for call
     */
    suspend fun addCallCandidate(callId: String, candidate: String): Result<Unit> {
        return try {
            apiService.addCallCandidate(callId, candidate)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get call details
     */
    suspend fun getCall(callId: String): Result<org.json.JSONObject> {
        return try {
            apiService.getCall(callId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * End/delete a call
     */
    suspend fun deleteCall(callId: String): Result<Unit> {
        return try {
            apiService.deleteCall(callId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
