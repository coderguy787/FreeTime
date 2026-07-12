package com.freetime.app.ui.screens

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.ApiService
import com.freetime.app.data.network.SendFriendRequestRequest
import com.freetime.app.data.network.FriendRequest
import com.freetime.app.data.network.Friend
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Friend System API Implementation
 */

/**
 * Send friend request to a user
 */
suspend fun sendFriendRequest(
    context: Context,
    recipientId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Sending friend request to $recipientId")
        
        try {
            val apiService = ApiClient.getInstance()
            val request = SendFriendRequestRequest(recipientId = recipientId)
            val response = apiService.sendFriendRequest(request, "Bearer $token")
            
            if (response.isSuccessful) {
                Log.d("FriendSystem", "Friend request sent successfully")
                Result.success(true)
            } else {
                val errorMsg = "Failed to send friend request: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to send friend request: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to send friend request: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Get pending friend requests
 */
suspend fun getPendingFriendRequests(context: Context): Result<List<FriendRequest>> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Fetching pending friend requests")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.getPendingFriendRequests("Bearer $token")
            
            if (response.isSuccessful) {
                response.body()?.let { requests ->
                    Log.d("FriendSystem", "Found ${requests.size} pending requests")
                    Result.success(requests)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMsg = "Failed to fetch pending requests: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to fetch pending requests: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to fetch friend requests: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Accept friend request
 */
suspend fun acceptFriendRequest(
    context: Context,
    senderId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Accepting friend request from: $senderId")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.acceptFriendRequest("Bearer $token", senderId)
            
            if (response.isSuccessful) {
                Log.d("FriendSystem", "Friend request accepted")
                Result.success(true)
            } else {
                val errorMsg = "Failed to accept friend request: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to accept friend request: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to accept friend request: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Reject friend request
 */
suspend fun rejectFriendRequest(
    context: Context,
    senderId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Rejecting friend request from: $senderId")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.rejectFriendRequest("Bearer $token", senderId)
            
            if (response.isSuccessful) {
                Log.d("FriendSystem", "Friend request rejected")
                Result.success(true)
            } else {
                val errorMsg = "Failed to reject friend request: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to reject friend request: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to reject friend request: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Get friends list
 */
suspend fun getFriendsList(context: Context): Result<List<Friend>> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Fetching friends list")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.getFriendsList("Bearer $token")
            
            if (response.isSuccessful) {
                response.body()?.let { friends ->
                    Log.d("FriendSystem", "Found ${friends.size} friends")
                    Result.success(friends)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMsg = "Failed to fetch friends list: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to fetch friends list: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to fetch friends list: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Remove friend
 */
suspend fun removeFriend(
    context: Context,
    friendId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Removing friend: $friendId")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.removeFriend("Bearer $token", friendId)
            
            if (response.isSuccessful) {
                Log.d("FriendSystem", "Friend removed")
                Result.success(true)
            } else {
                val errorMsg = "Failed to remove friend: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to remove friend: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to remove friend: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Block user
 */
suspend fun blockUser(
    context: Context,
    userId: String
): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
        val prefs = SharedPreferencesHelper(context)
        val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
        
        Log.d("FriendSystem", "Blocking user: $userId")
        
        try {
            val apiService = ApiClient.getInstance()
            val response = apiService.blockUser("Bearer $token", userId)
            
            if (response.isSuccessful) {
                Log.d("FriendSystem", "User blocked")
                Result.success(true)
            } else {
                val errorMsg = "Failed to block user: ${response.code()}"
                Log.e("FriendSystem", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("FriendSystem", "Failed to block user: ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.e("FriendSystem", "Failed to block user: ${e.message}", e)
        Result.failure(e)
    }
}


