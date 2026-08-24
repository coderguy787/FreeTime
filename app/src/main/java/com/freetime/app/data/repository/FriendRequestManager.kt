package com.freetime.app.data.repository

import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FriendRequestManager(
    private val friendRepository: FriendRepository,
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper
) {
    suspend fun sendFriendRequestByUsername(targetUserId: String): Result<String> {
        return try {
            val currentUserId = prefs.getUserId() ?: return Result.failure(Exception("User not logged in"))

            // check local cache first to avoid duplicate requests
            val areAlreadyFriends = friendRepository.areFriends(currentUserId, targetUserId)
            if (areAlreadyFriends) {
                return Result.failure(Exception("Already friends with this user"))
            }

            val requestId = friendRepository.sendFriendRequest(targetUserId)

            Result.success(requestId)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(requestId: String): Result<Unit> {
        return try {
            friendRepository.acceptFriendRequest(requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(requestId: String): Result<Unit> {
        return try {
            friendRepository.rejectFriendRequest(requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun endFriendship(friendUserId: String): Result<Unit> {
        return try {
            friendRepository.endFriendship(friendUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            friendRepository.blockUser(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        return try {
            friendRepository.unblockFriend(targetUserId)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun getPendingRequests(limit: Int = 50): Flow<List<FriendRequestSummary>> {
        return friendRepository.getPendingFriendRequests().map { requests ->
            requests.take(limit).map { request ->
                FriendRequestSummary(
                    requestId = request.id,
                    fromUserId = request.senderId,
                    toUserId = "",
                    createdAt = request.timestamp
                )
            }
        }
    }
}

data class FriendRequestSummary(
    val requestId: String,
    val fromUserId: String,
    val toUserId: String,
    val createdAt: Long
)
