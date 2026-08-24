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
import com.freetime.app.data.network.DeleteHistoryRequestDto
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

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

// api helpers used after login
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

        val json = Gson().fromJson(responseBody, com.google.gson.JsonElement::class.java)
        val usersArray = json.asJsonObject.get("users").asJsonArray

        val contacts = usersArray.map { userJson ->
            val obj = userJson.asJsonObject
            ContactResponse(
                userId = obj.get("userId").asString,
                username = obj.get("username").asString,
                status = obj.get("status").asString ?: "Available",
                isOnline = false,
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

        val apiService = ApiClient.getInstance()

        val updateRequest = com.freetime.app.data.network.UpdateUserProfileRequest(
            username = username,
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
                    avatar = null,
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

suspend fun logoutUser(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)

        prefs.clearAuthData()

        Log.d("PostLoginApi", "User logged out successfully")
        Result.success(true)
    } catch (e: Exception) {
        Log.e("PostLoginApi", "Failed to logout: ${e.message}", e)
        Result.failure(e)
    }
}





