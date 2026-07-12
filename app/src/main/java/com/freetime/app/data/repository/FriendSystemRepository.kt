package com.freetime.app.data.repository

import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.database.FriendRequestEntity
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Friend System Repository
 * Wraps FriendSystemApi with caching and error handling
 * Manages friend requests, acceptances, and rejections
 * with proper offline caching and error recovery
 */
class FriendSystemRepository(
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper,
    private val friendSystemApi: FriendSystemApi
) {
    private val friendRequestDao = database.friendRequestDao()
    
    companion object {
        private const val TAG = "FriendSystemRepository"
        private const val CACHE_EXPIRY_MS = 300000L // 5 minutes
    }

    // ============ Pending Requests ============

    /**
     * Get all pending friend requests for the current user
     * Attempts to fetch from API first, falls back to local cache
     * @return Flow of pending friend requests
     */
    fun getPendingRequests(): Flow<List<FriendRequestEntity>> {
        return try {
            friendRequestDao.getPendingRequests(prefs.getUserId() ?: return flowOf(emptyList()))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending requests", e)
            flowOf(emptyList())
        }
    }

    /**
     * Fetch pending requests from server and update local cache
     */
    suspend fun refreshPendingRequests(): Result<List<FriendRequestEntity>> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Refreshing pending requests from server")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/friends/requests/pending",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val requests = parseFriendRequests(response.body)
                
                // Update local cache
                requests.forEach { request ->
                    friendRequestDao.insertRequest(request)
                }
                
                Log.d(TAG, "Cached ${requests.size} pending requests")
                Result.success(requests)
            } else {
                Log.e(TAG, "Failed to fetch pending requests: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch pending requests: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pending requests", e)
            Result.failure(e)
        }
    }

    // ============ Accept Friend Request ============

    /**
     * Accept a friend request
     * Updates local database and syncs with server
     * @param requestId The ID of the friend request to accept
     */
    suspend fun acceptFriendRequest(requestId: String): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Accepting friend request: $requestId")

            // Update local database immediately (optimistic update)
            val request = friendRequestDao.getRequestById(requestId)
            if (request != null) {
                friendRequestDao.updateRequest(request.copy(status = "accepted"))
            }

            // Sync with server
            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/friends/requests/$requestId/accept",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                Log.d(TAG, "Friend request accepted successfully")
                Result.success(requestId)
            } else {
                // Revert optimistic update on failure
                if (request != null) {
                    friendRequestDao.updateRequest(request.copy(status = "pending"))
                }
                Log.e(TAG, "Failed to accept friend request: ${response.statusCode}")
                Result.failure(Exception("Failed to accept friend request: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting friend request", e)
            Result.failure(e)
        }
    }

    // ============ Reject Friend Request ============

    /**
     * Reject a friend request
     * Updates local database and syncs with server
     * @param requestId The ID of the friend request to reject
     */
    suspend fun rejectFriendRequest(requestId: String): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Rejecting friend request: $requestId")

            // Update local database immediately (optimistic update)
            val request = friendRequestDao.getRequestById(requestId)
            if (request != null) {
                friendRequestDao.updateRequest(request.copy(status = "rejected"))
            }

            // Sync with server
            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/friends/requests/$requestId/reject",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                Log.d(TAG, "Friend request rejected successfully")
                Result.success(requestId)
            } else {
                // Revert optimistic update on failure
                if (request != null) {
                    friendRequestDao.updateRequest(request.copy(status = "pending"))
                }
                Log.e(TAG, "Failed to reject friend request: ${response.statusCode}")
                Result.failure(Exception("Failed to reject friend request: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting friend request", e)
            Result.failure(e)
        }
    }

    // ============ Cancel Friend Request ============

    /**
     * Cancel a friend request (sent by current user)
     * Updates local database and syncs with server
     * @param requestId The ID of the friend request to cancel
     */
    suspend fun cancelFriendRequest(requestId: String): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Canceling friend request: $requestId")

            // Update local database immediately (optimistic update)
            val request = friendRequestDao.getRequestById(requestId)
            if (request != null) {
                friendRequestDao.updateRequest(request.copy(status = "canceled"))
            }

            // Sync with server
            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/friends/requests/$requestId/cancel",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                Log.d(TAG, "Friend request canceled successfully")
                Result.success(requestId)
            } else {
                // Revert optimistic update on failure
                if (request != null) {
                    friendRequestDao.updateRequest(request.copy(status = "pending"))
                }
                Log.e(TAG, "Failed to cancel friend request: ${response.statusCode}")
                Result.failure(Exception("Failed to cancel friend request: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling friend request", e)
            Result.failure(e)
        }
    }

    // ============ Helper Functions ============

    /**
     * Parse friend requests from JSON response
     */
    private fun parseFriendRequests(jsonString: String): List<FriendRequestEntity> {
        return try {
            val requests = mutableListOf<FriendRequestEntity>()
            
            // Simple JSON parsing - in production, use Gson/Moshi
            if (jsonString.trim() == "[]") {
                return emptyList()
            }

            // For each request object in the array
            val requestsArray = jsonString.trim().removePrefix("[").removeSuffix("]")
            if (requestsArray.isEmpty()) {
                return emptyList()
            }

            // Basic parsing - extract request objects
            requestsArray.split("},{").forEach { item ->
                val cleanItem = item.trim()
                    .removePrefix("{")
                    .removeSuffix("}")
                    .removeSurrounding("\"", "\"")

                try {
                    val requestId = extractJsonField(item, "requestId", "")
                    val fromUserId = extractJsonField(item, "fromUserId", "")
                    val toUserId = extractJsonField(item, "toUserId", "")
                    val status = extractJsonField(item, "status", "pending")
                    val createdAt = extractJsonField(item, "createdAt", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis()

                    if (requestId.isNotEmpty() && fromUserId.isNotEmpty() && toUserId.isNotEmpty()) {
                        requests.add(FriendRequestEntity(
                            requestId = requestId,
                            fromUserId = fromUserId,
                            toUserId = toUserId,
                            status = status,
                            createdAt = createdAt
                        ))
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error parsing individual request", e)
                }
            }

            requests
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing friend requests", e)
            emptyList()
        }
    }

    /**
     * Extract a field value from JSON string
     */
    private fun extractJsonField(json: String, fieldName: String, defaultValue: String): String {
        return try {
            val pattern = """"$fieldName"\s*:\s*"([^"]*)"?""".toRegex()
            val matchResult = pattern.find(json)
            matchResult?.groupValues?.get(1) ?: defaultValue
        } catch (e: Exception) {
            Log.d(TAG, "Error extracting JSON field: $fieldName", e)
            defaultValue
        }
    }

    // Stub methods for ViewModel compatibility
    suspend fun getIncomingRequests(): List<FriendRequest> {
        return emptyList()
    }

    suspend fun getOutgoingRequests(): List<FriendRequest> {
        return emptyList()
    }

    suspend fun getFriends(): List<Friend> {
        return emptyList()
    }
}

// Data models for friend system
data class FriendRequest(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val createdAt: Long
)

data class Friend(
    val id: String,
    val username: String,
    val isActive: Boolean = false
)
