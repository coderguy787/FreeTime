package com.freetime.app.data.repository

import android.content.Context
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.repository.PostLoginApiImpl
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.putResponse
import com.freetime.app.data.local.database.FriendRequestEntity
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Friend System Extensions
 * Adds username-based friend request functionality similar to Discord
 * Features:
 * - Send friend request using @username instead of userId
 * - Cancel pending friend request ("End Request")
 * - View pending requests
 * - Accept/Deny friend requests
 */
class FriendSystemApi(
    private val database: FreeTimeDatabase,
    private val context: Context,
    private val prefs: SharedPreferencesHelper
) {
    private val friendRequestDao = database.friendRequestDao()

    companion object {
        private const val TAG = "FriendSystemApi"
    }

    /**
     * Send friend request by username (Discord-style)
     * Example: "sendFriendRequestByUsername("@leonardo")"
     */
    suspend fun sendFriendRequestByUsername(
        usernameOrTag: String
    ): Result<FriendRequestResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            // Remove @ symbol if present
            val username = usernameOrTag.removePrefix("@").trim()
            
            if (username.isEmpty()) {
                return@withContext Result.failure(Exception("Username cannot be empty"))
            }

            if (username.length < 3) {
                return@withContext Result.failure(Exception("Username must be at least 3 characters"))
            }

            Log.d(TAG, "Sending friend request to username: $username")

            // Call API to send friend request
            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/friends/request/username",
                "{\"username\": \"$username\"}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                // Parse response
                val responseBody = response.body
                  Log.d(TAG, "Friend request sent successfully to $username")                
                Result.success(FriendRequestResponse(
                    success = true,
                    message = "Friend request sent to @$username",
                    requestId = extractRequestId(responseBody)
                ))
            } else {
                Log.e(TAG, "Failed to send friend request: ${response.statusCode}")
                Result.failure(Exception("Failed to send friend request: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Cancel pending friend request ("End Request" like Discord)
     * Can only cancel requests that are in "pending" status
     */
    suspend fun cancelFriendRequest(
        requestId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Canceling friend request: $requestId")

            // Call API to cancel request
            val response = RawSocketHttpClient.deleteResponse(
                "$baseUrl/api/friends/requests/$requestId",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Friend request canceled successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to cancel request: ${response.statusCode}")
                Result.failure(Exception("Failed to cancel request"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Get all pending outgoing friend requests
     * Returns requests sent by the current user that haven't been accepted/denied yet
     */
    suspend fun getPendingOutgoingRequests(): Result<List<PendingFriendRequest>> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching pending outgoing friend requests")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/friends/requests/outgoing/pending",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val requests = parsePendingRequests(response.body)
                Log.d(TAG, "Retrieved ${requests.size} pending outgoing requests")
                Result.success(requests)
            } else {
                Log.e(TAG, "Failed to fetch requests: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch requests"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pending requests", e)
            Result.failure(e)
        }
    }

    /**
     * Get all pending incoming friend requests
     * Returns requests from other users to the current user
     */
    suspend fun getPendingIncomingRequests(): Result<List<PendingFriendRequest>> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching pending incoming friend requests")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/friends/requests/incoming/pending",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val requests = parsePendingRequests(response.body)
                Log.d(TAG, "Retrieved ${requests.size} pending incoming requests")
                Result.success(requests)
            } else {
                Log.e(TAG, "Failed to fetch incoming requests: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch incoming requests"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching incoming requests", e)
            Result.failure(e)
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun acceptFriendRequest(
        requestId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Accepting friend request: $requestId")

            val response = RawSocketHttpClient.putResponse(
                "$baseUrl/api/friends/requests/$requestId/accept",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Friend request accepted successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to accept request: ${response.statusCode}")
                Result.failure(Exception("Failed to accept request"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Deny/Reject a friend request
     */
    suspend fun denyFriendRequest(
        requestId: String,
        reason: String = "User declined"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Denying friend request: $requestId")

            val response = RawSocketHttpClient.putResponse(
                "$baseUrl/api/friends/requests/$requestId/deny",
                "{\"reason\": \"$reason\"}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Friend request denied successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to deny request: ${response.statusCode}")
                Result.failure(Exception("Failed to deny request"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error denying friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Search for users by username
     * Supports partial matching (e.g., "leo" matches "leonardo")
     */
    suspend fun searchUsersByUsername(
        query: String
    ): Result<List<UserSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = com.freetime.app.data.network.ApiClient.getBaseUrl()

            Log.d(TAG, "Searching for users: $query")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/users/search?q=$query",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val results = parseUserSearchResults(response.body)
                Log.d(TAG, "Found ${results.size} users matching query: $query")
                Result.success(results)
            } else {
                Log.e(TAG, "Failed to search users: ${response.statusCode}")
                Result.failure(Exception("Failed to search users"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users", e)
            Result.failure(e)
        }
    }

    // Helper functions

    private fun extractRequestId(responseBody: String): String {
        return try {
            // Simple JSON parsing for requestId
            val startIdx = responseBody.indexOf("\"requestId\"") + 13
            val endIdx = responseBody.indexOf("\"", startIdx)
            responseBody.substring(startIdx, endIdx)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun parsePendingRequests(jsonBody: String): List<PendingFriendRequest> {
        return try {
            val gson = Gson()
            // Parse as JSON array
            val jsonArray = JsonParser.parseString(jsonBody).asJsonArray
            val requests = mutableListOf<PendingFriendRequest>()
            
            for (element in jsonArray) {
                try {
                    val request = gson.fromJson(element, PendingFriendRequest::class.java)
                    requests.add(request)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Failed to parse individual pending request", e)
                    // Continue with other requests
                }
            }
            requests
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse pending requests", e)
            emptyList()
        }
    }

    private fun parseUserSearchResults(jsonBody: String): List<UserSearchResult> {
        return try {
            val gson = Gson()
            // Parse as JSON array
            val jsonArray = JsonParser.parseString(jsonBody).asJsonArray
            val results = mutableListOf<UserSearchResult>()
            
            for (element in jsonArray) {
                try {
                    val result = gson.fromJson(element, UserSearchResult::class.java)
                    results.add(result)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Failed to parse individual user search result", e)
                    // Continue with other results
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user search results", e)
            emptyList()
        }
    }
}

/**
 * Model for friend request response
 */
data class FriendRequestResponse(
    val success: Boolean,
    val message: String,
    val requestId: String? = null
)

/**
 * Model for pending friend request
 */
data class PendingFriendRequest(
    val requestId: String,
    val fromUserId: String,
    val fromUsername: String,
    val toUserId: String,
    val toUsername: String,
    val sentAt: Long,
    val status: String = "pending" // "pending", "accepted", "denied"
)

/**
 * Model for user search result
 */
data class UserSearchResult(
    val userId: String,
    val username: String,
    val displayName: String = "",
    val bio: String = "",
    val avatar: String? = null,
    val tags: List<String> = emptyList(),
    val status: String = "offline",
    val privacyLevel: String = "public",
    val isOnline: Boolean = false,
    val isFriend: Boolean = false,
    val hasPendingRequest: Boolean = false
)
