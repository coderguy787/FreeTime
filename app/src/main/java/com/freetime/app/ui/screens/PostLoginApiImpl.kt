package com.freetime.app.ui.screens

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.MessageResponse
import com.freetime.app.data.network.SendMessageRequest
import com.freetime.app.data.network.CallResponse
import com.freetime.app.data.network.DeleteHistoryRequestDto
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

/**
 * Implementation of all post-login API calls for messaging, contacts, calls, and profile
 */

// ==================== DATA MODELS ====================

data class ContactResponse(
    val userId: String,
    val username: String,
    val status: String,
    val isOnline: Boolean,
    val lastSeen: Long?
)

data class ProfileResponse(
    val userId: String,
    val username: String,
    val email: String,
    val status: String,
    val avatar: String?,
    val createdAt: Long
)

// ==================== MESSAGE APIs ====================

/**
 * Send a message to a recipient
 */
suspend fun sendMessage(
    context: Context,
    recipientId: String,
    content: String
): Result<MessageResponse> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        val apiService = ApiClient.getInstance()
        val messageRequest = SendMessageRequest(
            recipientId = recipientId,
            content = content
        )
        
        Log.d("PostLoginApi", "Sending message to $recipientId via ApiClient")
        
        val response = apiService.sendMessage(messageRequest, "Bearer $token")
        
        if (response.isSuccessful) {
            response.body()?.let { messageResponse ->
                Log.d("PostLoginApi", "Message sent successfully: ${messageResponse._id}")
                Result.success(messageResponse)
            } ?: Result.failure(Exception("Empty response body"))
        } else {
            val errorMsg = "Failed to send message: ${response.code()} ${response.message()}"
            Log.e("PostLoginApi", errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to send message: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Fetch messages for a recipient
 */
suspend fun fetchMessages(
    context: Context,
    recipientId: String,
    limit: Int = 50
): Result<List<MessageResponse>> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        val apiService = ApiClient.getInstance()
        
        Log.d("PostLoginApi", "Fetching messages for $recipientId via ApiClient")
        
        // NOTE: limit parameter is not supported by server API - only recipientId and token accepted
        val response = apiService.getMessages(recipientId, "Bearer $token")
        
        if (response.isSuccessful) {
            response.body()?.let { messages ->
                Log.d("PostLoginApi", "Fetched ${messages.size} messages")
                Result.success(messages)
            } ?: Result.failure(Exception("Empty response body"))
        } else {
            val errorMsg = "Failed to fetch messages: ${response.code()} ${response.message()}"
            Log.e("PostLoginApi", errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to fetch messages: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Delete chat history with a contact
 */
suspend fun deleteMessageHistory(
    context: Context,
    recipientId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        val apiService = ApiClient.getInstance()
        
        Log.d("PostLoginApi", "Deleting message history with $recipientId")
        
        val response = apiService.deleteHistoryWithUser(
            recipientId,
            DeleteHistoryRequestDto(
                targetUserId = recipientId,
                chatId = "all",
                deletionType = "one_side"
            ),
            "Bearer $token"
        )
        
        if (response.isSuccessful) {
            Log.d("PostLoginApi", "Message history deleted successfully")
            Result.success(true)
        } else {
            val errorMsg = "Failed to delete message history: ${response.code()} ${response.message()}"
            Log.e("PostLoginApi", errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to delete message history: ${e.message}", e)
        Result.failure(e)
    }
}

// ==================== CONTACTS/USERS APIs ====================

/**
 * Search for users by username
 */
suspend fun searchUsers(
    context: Context,
    query: String
): Result<List<ContactResponse>> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        Log.d("PostLoginApi", "Searching for users with query: $query")
        
        val responseBody = RawSocketHttpClient.get(
            "$baseUrl/api/users/search?q=$query",
            mapOf("Authorization" to "Bearer $token")
        )
        
        // Parse the response which contains { success, users, ... }
        val json = Gson().fromJson(responseBody, com.google.gson.JsonElement::class.java)
        val usersArray = json.asJsonObject.get("users").asJsonArray
        
        val contacts = usersArray.map { userJson ->
            val obj = userJson.asJsonObject
            ContactResponse(
                userId = obj.get("userId").asString,
                username = obj.get("username").asString,
                status = obj.get("status").asString ?: "Available",
                isOnline = false, // Search endpoint doesn't provide this
                lastSeen = null
            )
        }
        
        Log.d("PostLoginApi", "Found ${contacts.size} users")
        Result.success(contacts)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to search users: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Get user profile
 */
suspend fun getUserProfile(
    context: Context,
    userId: String
): Result<ProfileResponse> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        Log.d("PostLoginApi", "Fetching profile for $userId")
        
        val responseBody = RawSocketHttpClient.get(
            "$baseUrl/api/users/$userId",
            mapOf("Authorization" to "Bearer $token")
        )
        
        val profile = Gson().fromJson(responseBody, ProfileResponse::class.java)
        Log.d("PostLoginApi", "Profile fetched: ${profile.username}")
        Result.success(profile)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to fetch profile: ${e.message}", e)
        Result.failure(e)
    }
}

// ==================== PROFILE APIs ====================

/**
 * Update user profile
 */
suspend fun updateProfile(
    context: Context,
    username: String? = null,
    status: String? = null,
    bio: String? = null
): Result<ProfileResponse> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("No user ID"))
        
        // Use the proper API client instead of raw socket
        val apiService = ApiClient.getInstance()
        
        val updateRequest = com.freetime.app.data.network.UpdateUserProfileRequest(
            username = username, // Server expects 'username' field
            bio = bio,
            status = status
        )
        
        Log.d("PostLoginApi", "Updating profile for $userId with data: $updateRequest")
        
        val response = apiService.updateUserProfile(userId, updateRequest, "Bearer $token")
        
        if (response.isSuccessful) {
            response.body()?.let { profileResponse ->
                Log.d("PostLoginApi", "Profile updated successfully")
                Result.success(ProfileResponse(
                    userId = profileResponse.userId,
                    username = profileResponse.username,
                    email = profileResponse.email,
                    status = profileResponse.status ?: status ?: "Online",
                    avatar = null, // UserResponse doesn't have avatar field
                    createdAt = profileResponse.createdAt
                ))
            } ?: Result.failure(Exception("Empty response body"))
        } else {
            val errorMsg = "Failed to update profile: ${response.code()} ${response.message()}"
            Log.e("PostLoginApi", errorMsg)
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to update profile: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Logout user
 */
suspend fun logoutUser(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        
        // Clear all auth data
        prefs.clearAuthData()
        
        Log.d("PostLoginApi", "User logged out successfully")
        Result.success(true)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to logout: ${e.message}", e)
        Result.failure(e)
    }
}

// ==================== CALL APIs ====================

/**
 * Initiate a call
 */
suspend fun initiateCall(
    context: Context,
    recipientId: String,
    callType: String = "voice" // "voice" or "video"
): Result<CallResponse> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        val callId = "call_${UUID.randomUUID().toString()}"
        
        val jsonBody = Gson().toJson(mapOf(
            "callId" to callId,
            "recipientId" to recipientId,
            "callType" to callType,
            "timestamp" to System.currentTimeMillis()
        ))
        
        Log.d("PostLoginApi", "Initiating $callType call with $recipientId")
        
        val responseBody = RawSocketHttpClient.post(
            "$baseUrl/api/calls/initiate",
            jsonBody,
            mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")
        )
        
        val callResponse = Gson().fromJson(responseBody, CallResponse::class.java)
        Log.d("PostLoginApi", "Call initiated: ${callResponse.callId}")
        Result.success(callResponse)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to initiate call: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Answer a call
 */
suspend fun answerCall(
    context: Context,
    callId: String
): Result<CallResponse> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        val jsonBody = Gson().toJson(mapOf(
            "status" to "answered",
            "timestamp" to System.currentTimeMillis()
        ))
        
        Log.d("PostLoginApi", "Answering call: $callId")
        
        val responseBody = RawSocketHttpClient.post(
            "$baseUrl/api/calls/$callId/answer",
            jsonBody,
            mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")
        )
        
        val callResponse = Gson().fromJson(responseBody, CallResponse::class.java)
        Log.d("PostLoginApi", "Call answered")
        Result.success(callResponse)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to answer call: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Reject a call
 */
suspend fun rejectCall(
    context: Context,
    callId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        Log.d("PostLoginApi", "Rejecting call: $callId")
        
        RawSocketHttpClient.post(
            "$baseUrl/api/calls/$callId/reject",
            "{}",
            mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")
        )
        
        Log.d("PostLoginApi", "Call rejected")
        Result.success(true)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to reject call: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * End a call
 */
suspend fun endCall(
    context: Context,
    callId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        val baseUrl = ApiClient.getBaseUrl()
        
        Log.d("PostLoginApi", "Ending call: $callId")
        
        RawSocketHttpClient.delete(
            "$baseUrl/api/calls/$callId",
            mapOf("Authorization" to "Bearer $token")
        )
        
        Log.d("PostLoginApi", "Call ended")
        Result.success(true)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to end call: ${e.message}", e)
        Result.failure(e)
    }
}


