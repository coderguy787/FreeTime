package com.freetime.app.data.repository

import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Friend Request Manager - Handles username-based friend requests
 * Similar to Discord's friend request system
 */
class FriendRequestManager(
    private val friendRepository: FriendRepository,
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper
) {
    
    /**
     * Send friend request by username
     * Steps:
     * 1. Check if already friends or blocked
     * 2. Create friend request in local database
     * 3. Sync request to server
     */
    suspend fun sendFriendRequestByUsername(targetUserId: String): Result<String> {
        return try {
            val currentUserId = prefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
            
            // Step 1: Check if already friends
            val areAlreadyFriends = friendRepository.areFriends(currentUserId, targetUserId)
            if (areAlreadyFriends) {
                return Result.failure(Exception("Already friends with this user"))
            }

            // Step 2: Send friend request
            val requestId = friendRepository.sendFriendRequest(targetUserId)
            
            Result.success(requestId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun acceptFriendRequest(requestId: String): Result<Unit> {
        return try {
            friendRepository.acceptFriendRequest(requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Reject a friend request
     */
    suspend fun rejectFriendRequest(requestId: String): Result<Unit> {
        return try {
            friendRepository.rejectFriendRequest(requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * End friendship (Discord-like "End Friendship" button)
     */
    suspend fun endFriendship(friendUserId: String): Result<Unit> {
        return try {
            friendRepository.endFriendship(friendUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Block a user
     */
    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            friendRepository.blockUser(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Unblock a user
     */
    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        return try {
            friendRepository.unblockFriend(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get pending friend requests with pagination
     */
    fun getPendingRequests(limit: Int = 50): Flow<List<FriendRequestSummary>> {
        return friendRepository.getPendingFriendRequests().map { requests ->
            requests.take(limit).map { request ->
                FriendRequestSummary(
                    requestId = request.id,
                    fromUserId = request.senderId,
                    toUserId = "",  // API response doesn't include recipient ID, use empty
                    createdAt = request.timestamp
                )
            }
        }
    }
}

/**
 * Data class for friend request summary
 */
data class FriendRequestSummary(
    val requestId: String,
    val fromUserId: String,
    val toUserId: String,
    val createdAt: Long
)
