package com.freetime.app.data.repository

import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.UpdateUserProfileRequest

class ProfileRepository(
    private val prefs: SharedPreferencesHelper
) {
    private val apiService = ApiClient.getInstance()

    suspend fun updateUserProfile(
        username: String? = null,
        bio: String? = null,
        phone: String? = null,
        status: String? = null
    ): Result<Any> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val userId = prefs.getUserId() ?: throw Exception("User ID not available")

            val request = UpdateUserProfileRequest(
                username = username,
                bio = bio,
                phone = phone,
                status = status
            )

            val response = apiService.updateUserProfile(
                userId = userId,
                request = request,
                token = "Bearer $token"
            )

            if (response.isSuccessful && response.body() != null) {
                // save the new username locally too
                if (username != null) {
                    prefs.saveUsername(username)
                }
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(imageData: String, imageType: String = "image/jpeg"): Result<Any> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val userId = prefs.getUserId() ?: throw Exception("User ID not available")

            val request = com.freetime.app.data.network.ProfileImageUploadRequest(
                imageData = imageData,
                imageType = imageType
            )

            val response = apiService.uploadProfileImage(
                userId = userId,
                request = request,
                token = "Bearer $token"
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to upload profile image: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getProfileImage(userId: String = ""): Result<Any> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val id = if (userId.isEmpty()) (prefs.getUserId() ?: throw Exception("User ID not available")) else userId

            val response = apiService.getProfileImage(
                userId = id,
                token = "Bearer $token"
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get profile image: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteProfileImage(): Result<Unit> {
        return try {
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            val userId = prefs.getUserId() ?: throw Exception("User ID not available")

            val response = apiService.deleteProfileImage(
                userId = userId,
                token = "Bearer $token"
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete profile image: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun getCurrentUserProfile(): Map<String, String?> {
        return mapOf(
            "userId" to prefs.getUserId(),
            "username" to prefs.getUsername(),
            "email" to prefs.getEmail(),
            "phone" to null
        )
    }
}
