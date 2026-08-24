package com.freetime.app.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.putResponse
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

data class SecureMessageResponse(
    val messageId: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val encryptionType: String = "AES-256"
)

data class SecureContactResponse(
    val userId: String,
    val username: String,
    val status: String,
    val isOnline: Boolean,
    val lastSeen: Long?,
    val isFriend: Boolean,
    val isBlocked: Boolean
)

data class SecureProfileResponse(
    val userId: String,
    val username: String,
    val email: String,
    val status: String,
    val avatar: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val statusMessage: String?
)

data class TokenResponse(
    val token: String,
    val refreshToken: String?,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

class PostLoginApiImpl(private val context: Context) {
    private val prefs = SharedPreferencesHelper(context)

    companion object {
        private const val TAG = "PostLoginApiImpl"
    }

    suspend fun sendMessage(
        recipientId: String,
        content: String
    ): Result<SecureMessageResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            // message id generated locally, the api accepts it
            val messageId = "msg_${UUID.randomUUID()}"
            val timestamp = System.currentTimeMillis()

            val jsonBody = Gson().toJson(mapOf(
                "recipientId" to recipientId,
                "content" to content,
                "messageId" to messageId,
                "timestamp" to timestamp
            ))

            Log.d(TAG, "Sending secure message to $recipientId")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/messages",
                jsonBody,
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                val messageResponse = parseSecureMessageResponse(response.body)
                Log.d(TAG, "Message sent successfully: $messageId")
                Result.success(messageResponse)
            } else {
                Log.e(TAG, "Failed to send message: ${response.statusCode}")
                Result.failure(Exception("Failed to send message: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    suspend fun fetchMessages(
        recipientId: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<SecureMessageResponse>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching messages from $recipientId (limit: $limit, offset: $offset)")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/messages/$recipientId?limit=$limit&offset=$offset",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val messages = parseSecureMessageList(response.body ?: "[]")
                Log.d(TAG, "Fetched ${messages.size} messages")
                Result.success(messages)
            } else {
                Log.e(TAG, "Failed to fetch messages: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch messages"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages", e)
            Result.failure(e)
        }
    }

    suspend fun markMessageAsRead(messageId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Marking message as read: $messageId")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/messages/$messageId/read",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Message marked as read")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to mark message as read: ${response.statusCode}")
                Result.failure(Exception("Failed to mark message as read"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message as read", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageHistory(recipientId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Deleting message history with $recipientId")

            val response = RawSocketHttpClient.deleteResponse(
                "$baseUrl/api/messages/$recipientId/delete-history",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Message history deleted successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete message history: ${response.statusCode}")
                Result.failure(Exception("Failed to delete message history"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message history", e)
            Result.failure(e)
        }
    }

    suspend fun getContacts(): Result<List<SecureContactResponse>> =
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))

            Log.d(TAG, "Fetching contacts list from /api/friends")

            val apiService = ApiClient.getInstance()
            val response = apiService.getFriendsList("Bearer $token")

            return@withContext if (response.isSuccessful) {
                val friends = response.body() ?: emptyList()
                val contacts = friends.map { friend ->
                    SecureContactResponse(
                        userId = friend.userId,
                        username = friend.username,
                        status = friend.status,
                        isOnline = friend.isOnline,
                        lastSeen = friend.lastSeen,
                        isFriend = true,
                        isBlocked = false
                    )
                }
                Log.d(TAG, "Fetched ${contacts.size} contacts")
                Result.success(contacts)
            } else {
                Log.e(TAG, "Failed to fetch contacts: ${response.code()}")

                Result.failure(Exception("Failed to fetch contacts"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts", e)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): Result<SecureProfileResponse> =
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching profile for user: $userId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/users/$userId/profile",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val profile = parseProfileResponse(response.body)
                Log.d(TAG, "Fetched profile for user: $userId")
                Result.success(profile)
            } else {
                Log.e(TAG, "Failed to fetch profile: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch profile"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(statusMessage: String): Result<SecureProfileResponse> =
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Updating user profile")

            val jsonBody = Gson().toJson(mapOf(
                "statusMessage" to statusMessage
            ))

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/users/profile/update",
                jsonBody,
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                val profile = parseProfileResponse(response.body)
                Log.d(TAG, "Profile updated successfully")
                Result.success(profile)
            } else {
                Log.e(TAG, "Failed to update profile: ${response.statusCode}")
                Result.failure(Exception("Failed to update profile"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile", e)
            Result.failure(e)
        }
    }



    suspend fun refreshToken(): Result<TokenResponse> =
        withContext(Dispatchers.IO) {
        try {
            val refreshToken = prefs.getRefreshToken() ?: return@withContext Result.failure(Exception("No refresh token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Refreshing authentication token")

            val jsonBody = Gson().toJson(mapOf(
                "refreshToken" to refreshToken
            ))

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/refresh-token",
                jsonBody,
                mapOf("Content-Type" to "application/json")
            )

            return@withContext if (response.statusCode == 200) {
                val tokenResponse = parseTokenResponse(response.body)

                prefs.saveToken(tokenResponse.token)
                if (tokenResponse.refreshToken != null) {
                    prefs.saveRefreshToken(tokenResponse.refreshToken)
                }

                Log.d(TAG, "Token refreshed successfully")
                Result.success(tokenResponse)
            } else {
                Log.e(TAG, "Failed to refresh token: ${response.statusCode}")
                Result.failure(Exception("Failed to refresh token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            Result.failure(e)
        }
    }

    private fun parseSecureMessageResponse(jsonString: String): SecureMessageResponse {
        return try {
            SecureMessageResponse(
                messageId = extractJsonFieldNonNull(jsonString, "messageId", ""),
                recipientId = extractJsonFieldNonNull(jsonString, "recipientId", ""),
                content = extractJsonFieldNonNull(jsonString, "content", ""),
                timestamp = extractJsonFieldNonNull(jsonString, "timestamp", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis(),
                status = extractJsonFieldNonNull(jsonString, "status", "sent"),
                encryptionType = extractJsonFieldNonNull(jsonString, "encryptionType", "AES-256")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message response", e)
            SecureMessageResponse("", "", "", System.currentTimeMillis(), "error")
        }
    }

    private fun parseSecureMessageList(jsonString: String): List<SecureMessageResponse> {
        return try {
            val messages = mutableListOf<SecureMessageResponse>()
            if (jsonString.trim() == "[]") return emptyList()

            jsonString.split("},{").forEach { item ->
                try {
                    val response = SecureMessageResponse(
                        messageId = extractJsonFieldNonNull(item, "messageId", ""),
                        recipientId = extractJsonFieldNonNull(item, "recipientId", ""),
                        content = extractJsonFieldNonNull(item, "content", ""),
                        timestamp = extractJsonFieldNonNull(item, "timestamp", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis(),
                        status = extractJsonFieldNonNull(item, "status", "sent")
                    )
                    if (response.messageId.isNotEmpty()) messages.add(response)
                } catch (e: Exception) {
                    Log.d(TAG, "Error parsing individual message", e)
                }
            }
            messages
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message list", e)
            emptyList()
        }
    }

    private fun parseContactList(jsonString: String): List<SecureContactResponse> {
        return try {
            val contacts = mutableListOf<SecureContactResponse>()
            if (jsonString.trim() == "[]") return emptyList()

            jsonString.split("},{").forEach { item ->
                try {
                    val contact = SecureContactResponse(
                        userId = extractJsonFieldNonNull(item, "userId", ""),
                        username = extractJsonFieldNonNull(item, "username", ""),
                        status = extractJsonFieldNonNull(item, "status", "offline"),
                        isOnline = extractJsonFieldNonNull(item, "isOnline", "false").toBoolean(),
                        lastSeen = extractJsonFieldNonNull(item, "lastSeen", "null").toLongOrNull(),
                        isFriend = extractJsonFieldNonNull(item, "isFriend", "false").toBoolean(),
                        isBlocked = extractJsonFieldNonNull(item, "isBlocked", "false").toBoolean()
                    )
                    if (contact.userId.isNotEmpty()) contacts.add(contact)
                } catch (e: Exception) {
                    Log.d(TAG, "Error parsing individual contact", e)
                }
            }
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing contact list", e)
            emptyList()
        }
    }

    private fun parseProfileResponse(jsonString: String): SecureProfileResponse {
        return try {
            SecureProfileResponse(
                userId = extractJsonFieldNonNull(jsonString, "userId", ""),
                username = extractJsonFieldNonNull(jsonString, "username", ""),
                email = extractJsonFieldNonNull(jsonString, "email", ""),
                status = extractJsonFieldNonNull(jsonString, "status", ""),
                avatar = extractJsonField(jsonString, "avatar", null),
                createdAt = extractJsonFieldNonNull(jsonString, "createdAt", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis(),
                updatedAt = extractJsonFieldNonNull(jsonString, "updatedAt", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis(),
                statusMessage = extractJsonField(jsonString, "statusMessage", null)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing profile response", e)
            SecureProfileResponse("", "", "", "", null, 0, 0, null)
        }
    }


    private fun parseTokenResponse(jsonString: String): TokenResponse {
        return try {
            TokenResponse(
                token = extractJsonFieldNonNull(jsonString, "token", ""),
                refreshToken = extractJsonField(jsonString, "refreshToken", null),
                expiresIn = extractJsonFieldNonNull(jsonString, "expiresIn", "3600").toLongOrNull() ?: 3600,
                tokenType = extractJsonFieldNonNull(jsonString, "tokenType", "Bearer")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing token response", e)
            TokenResponse("", null, 0)
        }
    }

    private fun extractJsonField(json: String, fieldName: String, defaultValue: String?): String? {
        return try {
            val pattern = """"$fieldName"\s*:\s*(?:"([^"]*)"|([^,}]*))""".toRegex()
            val matchResult = pattern.find(json)
            matchResult?.groupValues?.let { values ->
                values[1].takeIf { it.isNotEmpty() } ?: values[2].takeIf { it.isNotEmpty() }
            } ?: defaultValue
        } catch (e: Exception) {
            Log.d(TAG, "Error extracting JSON field: $fieldName", e)
            defaultValue
        }
    }

    private fun extractJsonFieldNonNull(json: String, fieldName: String, defaultValue: String): String {
        return extractJsonField(json, fieldName, defaultValue) ?: defaultValue
    }
}
