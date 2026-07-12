package com.freetime.app.data.repository

import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.models.GroupVote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Group Voting Repository
 * Wraps group voting operations with caching and error handling
 * Manages voting on group actions such as group deletion
 */
class GroupVotingRepository(
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper
) {
    companion object {
        private const val TAG = "GroupVotingRepository"
        private const val VOTE_APPROVAL_THRESHOLD = 0.5 // >50% for approval
    }

    // ============ Get Votes ============

    /**
     * Get all votes for a specific voting session
     * @param groupId The group ID
     * @param voteId The voting session ID
     * @return Result containing list of votes
     */
    suspend fun getVotes(
        groupId: String,
        voteId: String
    ): Result<List<GroupVote>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching votes for group: $groupId, voteId: $voteId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/$voteId",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val votes = parseVotes(response.body ?: "[]")
                Log.d(TAG, "Retrieved ${votes.size} votes")
                Result.success(votes)
            } else {
                Log.e(TAG, "Failed to fetch votes: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch votes: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching votes", e)
            Result.failure(e)
        }
    }

    /**
     * Get vote summary/results for a voting session
     * @param groupId The group ID
     * @param voteId The voting session ID
     * @return Result containing vote results
     */
    suspend fun getVoteResults(
        groupId: String,
        voteId: String
    ): Result<VoteResults> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching vote results for group: $groupId, voteId: $voteId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/$voteId/results",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val results = parseVoteResults(response.body)
                Log.d(TAG, "Retrieved vote results - Approved: ${results.approvalCount}, Rejected: ${results.rejectionCount}")
                Result.success(results)
            } else {
                Log.e(TAG, "Failed to fetch vote results: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch vote results: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching vote results", e)
            Result.failure(e)
        }
    }

    // ============ Cast Vote ============

    /**
     * Cast a vote on a group action
     * Each member can vote once per voting session
     * @param groupId The group ID
     * @param voteId The voting session ID
     * @param voteChoice true for approve, false for reject
     * @return Result containing the cast vote response
     */
    suspend fun castVote(
        groupId: String,
        voteId: String,
        voteChoice: Boolean
    ): Result<VoteCastResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Casting vote on group: $groupId, voteId: $voteId, choice: $voteChoice")

            val jsonBody = "{\"vote\": $voteChoice}"
            
            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/groups/$groupId/votes/$voteId/cast",
                jsonBody,
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200 || response.statusCode == 201) {
                Log.d(TAG, "Vote cast successfully")
                Result.success(VoteCastResponse(
                    success = true,
                    message = if (voteChoice) "Vote to approve" else "Vote to reject",
                    voteId = voteId,
                    userId = userId,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                Log.e(TAG, "Failed to cast vote: ${response.statusCode}")
                Result.failure(Exception("Failed to cast vote: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error casting vote", e)
            Result.failure(e)
        }
    }

    /**
     * Check if current user has already voted
     * @param groupId The group ID
     * @param voteId The voting session ID
     * @return true if user has already voted, false otherwise
     */
    suspend fun hasUserVoted(
        groupId: String,
        voteId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Checking if user has voted on voteId: $voteId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/$voteId/has-voted",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val hasVoted = response.body.contains("\"hasVoted\":true")
                Log.d(TAG, "User has voted: $hasVoted")
                Result.success(hasVoted)
            } else {
                Log.e(TAG, "Failed to check vote status: ${response.statusCode}")
                Result.failure(Exception("Failed to check vote status"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user has voted", e)
            Result.failure(e)
        }
    }

    // ============ Helper Functions ============

    /**
     * Parse votes from JSON response
     */
    private fun parseVotes(jsonString: String): List<GroupVote> {
        return try {
            if (jsonString.trim() == "[]") {
                return emptyList()
            }

            // Note: This method parses individual vote records
            // but GroupVote model represents voting questions, not individual votes
            // Return empty list for now - this would need API redesign
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing votes", e)
            emptyList()
        }
    }

    /**
     * Parse vote results from JSON response
     */
    private fun parseVoteResults(jsonString: String): VoteResults {
        return try {
            val approvalCount = extractJsonField(jsonString, "approvalCount", "0").toIntOrNull() ?: 0
            val rejectionCount = extractJsonField(jsonString, "rejectionCount", "0").toIntOrNull() ?: 0
            val totalMembers = extractJsonField(jsonString, "totalMembers", "0").toIntOrNull() ?: 0
            val status = extractJsonField(jsonString, "status", "pending")
            val expiresAt = extractJsonField(jsonString, "expiresAt", System.currentTimeMillis().toString()).toLongOrNull() ?: System.currentTimeMillis()

            val approvalPercentage = if (totalMembers > 0) {
                (approvalCount.toDouble() / totalMembers.toDouble()) * 100
            } else {
                0.0
            }

            VoteResults(
                approvalCount = approvalCount,
                rejectionCount = rejectionCount,
                totalMembers = totalMembers,
                approvalPercentage = approvalPercentage,
                isPassed = approvalPercentage > (VOTE_APPROVAL_THRESHOLD * 100),
                status = status,
                expiresAt = expiresAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing vote results", e)
            VoteResults(
                approvalCount = 0,
                rejectionCount = 0,
                totalMembers = 0,
                approvalPercentage = 0.0,
                isPassed = false,
                status = "error",
                expiresAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Extract a field value from JSON string
     */
    private fun extractJsonField(json: String, fieldName: String, defaultValue: String): String {
        return try {
            val pattern = """"$fieldName"\s*:\s*(?:"([^"]*)"|([^,}]*))""".toRegex()
            val matchResult = pattern.find(json)
            matchResult?.groupValues?.let { values ->
                values[1].takeIf { it.isNotEmpty() } ?: values[2]
            } ?: defaultValue
        } catch (e: Exception) {
            Log.d(TAG, "Error extracting JSON field: $fieldName", e)
            defaultValue
        }
    }

    // Stub methods for ViewModel compatibility
    suspend fun getActiveVotes(@Suppress("UNUSED_PARAMETER") groupId: String): List<GroupVote> = emptyList()
    suspend fun getCompletedVotes(@Suppress("UNUSED_PARAMETER") groupId: String): List<GroupVote> = emptyList()
    suspend fun castVote(@Suppress("UNUSED_PARAMETER") voteId: String, @Suppress("UNUSED_PARAMETER") optionId: String) {}
}

// Data classes for voting operations
data class VoteResults(
    val approvalCount: Int,
    val rejectionCount: Int,
    val totalMembers: Int,
    val approvalPercentage: Double,
    val isPassed: Boolean,
    val status: String, // "pending", "passed", "failed", "expired"
    val expiresAt: Long
)

data class VoteCastResponse(
    val success: Boolean,
    val message: String,
    val voteId: String,
    val userId: String,
    val timestamp: Long
)