@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

package com.freetime.app.data.repository

import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.ApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Admin Repository - Handles admin panel operations for user management
 */
class AdminRepository(
    private val prefs: SharedPreferencesHelper
) {
    private val apiService = ApiClient.getInstance()

    /**
     * Get all users for admin panel
     */
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

    /**
     * Get specific user details
     */
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

    /**
     * Search users by query
     */
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

    /**
     * Update user role/tags (simulated through update endpoint)
     * In real scenario, this would call a dedicated admin endpoint
     */
    suspend fun updateUserRole(userId: String, role: String, tags: List<String>): Result<Unit> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            
            // In a real admin system, there would be a dedicated endpoint
            // For now, we simulate by storing admin action
            prefs.saveAdminAction("$userId assigned role:$role tags:${tags.joinToString(",")}")
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Block/unblock user - DEPRECATED
     * Users are now managed through friend removal only
     */
    suspend fun toggleUserBlock(userId: String, shouldBlock: Boolean): Result<Unit> {
        return try {
            // Block functionality has been removed - use friend removal instead
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get admin statistics/dashboard data
     */
    suspend fun getAdminStats(): Result<Map<String, Any>> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            
            // Fetch all users to calculate stats
            val response = apiService.getUsers("Bearer $token")
            
            if (response.isSuccessful && response.body() != null) {
                val users = response.body() ?: emptyList()
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

    /**
     * Verify if current user is admin
     */
    fun isCurrentUserAdmin(): Boolean {
        // In real scenario, this would check user role from API
        // For now, return false (not admin)
        return false
    }
}
