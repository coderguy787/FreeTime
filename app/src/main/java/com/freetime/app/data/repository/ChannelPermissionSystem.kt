@file:Suppress("UNUSED_PARAMETER", "NO_CAST_NEEDED")

package com.freetime.app.data.repository

import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.models.ChannelMember
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Channel Permission System
 * Manages channel-specific permissions:
 * - Admin-only messaging (announcement channels)
 * - Member read-only access
 * - Admin moderation (delete messages)
 * - Member promotion to admin
 * - Permission inheritance from group
 */
class ChannelPermissionSystem(
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper
) {
    companion object {
        private const val TAG = "ChannelPermissionSystem"
    }

    /**
     * Check if user can send message in channel
     */
    suspend fun canSendMessageInChannel(
        channelId: String,
        userId: String
    ): Result<ChannelMessagePermission> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Checking message permission for user $userId in channel $channelId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/channels/$channelId/permissions/message",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val permission = parseMessagePermission(response.body)
                Log.d(TAG, "Message permission: canMessage=${permission.canSendMessage}")
                Result.success(permission)
            } else {
                Log.e(TAG, "Failed to check permissions: ${response.statusCode}")
                Result.failure(Exception("Failed to check message permissions"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking message permission", e)
            Result.failure(e)
        }
    }

    /**
     * Send message to channel with permission check
     */
    suspend fun sendChannelMessage(
        channelId: String,
        content: String
    ): Result<ChannelMessageResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Sending message to channel: $channelId")

            // Check if user has permission to send message
            val permissionResult = canSendMessageInChannel(channelId, prefs.getUserId() ?: "")
            if (permissionResult.isFailure) {
                return@withContext Result.failure(Exception("Permission denied"))
            }

            val permission = permissionResult.getOrNull()
            if (permission != null && !permission.canSendMessage) {
                return@withContext Result.failure(
                    Exception("Cannot send message: ${permission.reason ?: "Permission denied"}")
                )
            }

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/channels/$channelId/messages",
                "{\"content\": \"$content\"}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 201) {
                Log.d(TAG, "Message sent successfully to channel $channelId")
                Result.success(ChannelMessageResponse(
                    success = true,
                    message = "Message sent",
                    messageId = extractMessageId(response.body)
                ))
            } else {
                Log.e(TAG, "Failed to send message: ${response.statusCode}")
                Result.failure(Exception("Failed to send message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending channel message", e)
            Result.failure(e)
        }
    }

    /**
     * Promote member to admin
     */
    suspend fun promoteToChannelAdmin(
        channelId: String,
        memberId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Promoting user $memberId to admin in channel $channelId")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/channels/$channelId/members/$memberId/promote",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "User promoted to admin successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to promote user: ${response.statusCode}")
                Result.failure(Exception("Failed to promote user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error promoting user", e)
            Result.failure(e)
        }
    }

    /**
     * Demote admin to regular member
     */
    suspend fun demoteFromChannelAdmin(
        channelId: String,
        adminId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Demoting user $adminId from admin in channel $channelId")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/channels/$channelId/members/$adminId/demote",
                "{}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "User demoted successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to demote user: ${response.statusCode}")
                Result.failure(Exception("Failed to demote user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error demoting user", e)
            Result.failure(e)
        }
    }

    /**
     * Get channel members with their roles
     */
    suspend fun getChannelMembers(
        channelId: String
    ): Result<List<ChannelMember>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching members for channel: $channelId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/channels/$channelId/members",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val members = parseChannelMembers(response.body)
                Log.d(TAG, "Retrieved ${members.size} channel members")
                Result.success(members)
            } else {
                Log.e(TAG, "Failed to fetch members: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch members"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel members", e)
            Result.failure(e)
        }
    }

    /**
     * Delete message from channel (admin only)
     */
    suspend fun deleteChannelMessage(
        channelId: String,
        messageId: String,
        _reason: String = "Removed by moderator"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Deleting message $messageId from channel $channelId")

            val response = RawSocketHttpClient.deleteResponse(
                "$baseUrl/api/channels/$channelId/messages/$messageId",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
                Log.d(TAG, "Message deleted successfully")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete message: ${response.statusCode}")
                Result.failure(Exception("Failed to delete message"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message", e)
            Result.failure(e)
        }
    }

    /**
     * Get channel info including permission type
     */
    suspend fun getChannelInfo(
        channelId: String
    ): Result<ChannelInfo> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching channel info: $channelId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/channels/$channelId",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val channelInfo = parseChannelInfo(response.body)
                Log.d(TAG, "Retrieved channel info: ${channelInfo.name}")
                Result.success(channelInfo)
            } else {
                Log.e(TAG, "Failed to fetch channel info: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch channel info"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel info", e)
            Result.failure(e)
        }
    }

    // Helper functions

    private fun parseMessagePermission(jsonBody: String): ChannelMessagePermission {
        return try {
            val gson = Gson()
            gson.fromJson(jsonBody, ChannelMessagePermission::class.java)
                ?: throw Exception("Null response")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse message permission - invalid JSON", e)
            ChannelMessagePermission(
                canSendMessage = false,
                reason = "Error parsing permissions",
                role = "none",
                permissionType = "unknown"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message permission", e)
            ChannelMessagePermission(
                canSendMessage = false,
                reason = "Error parsing permissions",
                role = "none",
                permissionType = "unknown"
            )
        }
    }

    private fun parseChannelMembers(jsonBody: String): List<ChannelMember> {
        return try {
            val gson = Gson()
            val jsonArray = JsonParser.parseString(jsonBody).asJsonArray
            val members = mutableListOf<ChannelMember>()
            
            for (element in jsonArray) {
                try {
                    val member = gson.fromJson(element, ChannelMember::class.java)
                    members.add(member)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Failed to parse individual channel member", e)
                    // Continue with other members
                }
            }
            members
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse channel members", e)
            emptyList()
        }
    }

    private fun parseChannelInfo(jsonBody: String): ChannelInfo {
        return try {
            val gson = Gson()
            gson.fromJson(jsonBody, ChannelInfo::class.java)
                ?: throw Exception("Null response")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse channel info - invalid JSON", e)
            ChannelInfo(
                channelId = "",
                name = "",
                description = "",
                type = "unknown",
                isAdminOnly = false,
                adminIds = emptyList(),
                memberCount = 0,
                createdAt = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse channel info", e)
            ChannelInfo(
                channelId = "",
                name = "",
                description = "",
                type = "unknown",
                isAdminOnly = false,
                adminIds = emptyList(),
                memberCount = 0,
                createdAt = 0
            )
        }
    }

    private fun extractMessageId(jsonBody: String): String {
        return try {
            val gson = Gson()
            val jsonObject = gson.fromJson(jsonBody, com.google.gson.JsonObject::class.java)
            jsonObject.get("messageId")?.asString ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract message ID", e)
            "unknown"
        }
    }
}

/**
 * Response for message send
 */
data class ChannelMessageResponse(
    val success: Boolean,
    val message: String,
    val messageId: String?
)

/**
 * Message permission for a user in a channel
 */
data class ChannelMessagePermission(
    val canSendMessage: Boolean,
    val reason: String?,
    val role: String, // "admin", "moderator", "member", "none"
    val permissionType: String // "admin_only", "public", "private"
)

/**
 * Channel information
 */
data class ChannelInfo(
    val channelId: String,
    val name: String,
    val description: String,
    val type: String, // "public", "private", "admin_only"
    val isAdminOnly: Boolean,
    val adminIds: List<String>,
    val memberCount: Int,
    val createdAt: Long
)
