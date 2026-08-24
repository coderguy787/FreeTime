@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

package com.freetime.app.data.repository

import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AdminRepository(
    private val prefs: SharedPreferencesHelper
) {
    private val apiService = ApiClient.getInstance()

    fun getAllUsersFlow(): Flow<List<Any>> = flow {
        try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val response = apiService.getUsers("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                emit(response.body() ?: emptyList())
            } else {
                throw Exception("Failed to fetch users")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun getUserDetails(userId: String): Result<Any> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val response = apiService.getUser(userId, "Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body() ?: throw Exception("Admin response body is null"))
            } else {
                Result.failure(Exception("Failed to get user details"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun searchUsersFlow(query: String): Flow<List<Any>> = flow {
        try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val response = apiService.searchUsers(query, "Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                emit(response.body() ?: emptyList())
            } else {
                throw Exception("Failed to search users")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun updateUserRole(userId: String, role: String, tags: List<String>): Result<Unit> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")

            // role changes are local-only for now
            prefs.saveAdminAction("$userId assigned role:$role tags:${tags.joinToString(",")}")

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun toggleUserBlock(userId: String, shouldBlock: Boolean): Result<Unit> {
        return try {
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getAdminStats(): Result<Map<String, Any>> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")

            val response = apiService.getUsers("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                val users = response.body() ?: emptyList()
                // mostly placeholder numbers except totalUsers
                val stats = mapOf<String, Any>(
                    "totalUsers" to users.size,
                    "activeUsers" to (users.size * 0.75).toInt(),
                    "pendingRequests" to 5,
                    "reportedUsers" to 2,
                    "blockedUsers" to 3
                )
                Result.success(stats)
            } else {
                Result.failure(Exception("Failed to get admin stats"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        return false
    }
}
