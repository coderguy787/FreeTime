package com.freetime.app.data.repository

import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.network.RawSocketHttpClient
import com.freetime.app.data.network.postResponse
import com.freetime.app.data.network.getResponse
import com.freetime.app.data.network.deleteResponse
import com.freetime.app.data.network.ApiClient
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Group Voting System
 * Allows groups to vote on important actions like:
 * - Delete group (requires >50% member approval)
 * - Remove member (requires >50% member approval)
 * - Change group name/settings (admin action)
 */
class GroupVotingSystem(
    private val database: FreeTimeDatabase,
    private val prefs: SharedPreferencesHelper
) {
    companion object {
        private const val TAG = "GroupVotingSystem"
        private const val VOTE_APPROVAL_THRESHOLD = 0.5 // >50% for approval
        private const val VOTE_EXPIRY_HOURS = 24
    }

    /**
     * Initiate group deletion vote
     * Only group admin can initiate
     */
    suspend fun initiateGroupDeletionVote(
        groupId: String,
        groupName: String
    ): Result<GroupVoteResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Initiating group deletion vote for group: $groupId")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/groups/$groupId/deletion-vote/initiate",
                "{\"groupName\": \"$groupName\"}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 201) {
                Log.d(TAG, "Group deletion vote initiated successfully")
                Result.success(GroupVoteResponse(
                    success = true,
                    message = "Group deletion vote started",
                    voteId = extractVoteId(response.body ?: "{}"),
                    durationHours = VOTE_EXPIRY_HOURS
                ))
            } else {
                Log.e(TAG, "Failed to initiate vote: ${response.statusCode}")
                Result.failure(Exception("Failed to initiate group deletion vote"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating group deletion vote", e)
            Result.failure(e)
        }
    }

    /**
     * Cast vote on group deletion
     * Each member can vote once
     */
    suspend fun castVoteOnGroupDeletion(
        groupId: String,
        voteId: String,
        voteChoice: Boolean // true = approve, false = reject
    ): Result<VoteCastResponse> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val userId = prefs.getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Casting vote on group deletion: groupId=$groupId, voteId=$voteId, choice=$voteChoice")

            val response = RawSocketHttpClient.postResponse(
                "$baseUrl/api/groups/$groupId/deletion-vote/$voteId/vote",
                "{\"vote\": $voteChoice}",
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                )
            )

            return@withContext if (response.statusCode == 200) {
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
                Result.failure(Exception("Failed to cast vote"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error casting vote", e)
            Result.failure(e)
        }
    }

    /**
     * Get current voting status for a group
     */
    suspend fun getGroupVotingStatus(
        groupId: String
    ): Result<GroupVoteStatus> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching voting status for group: $groupId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/active",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val voteStatus = parseVoteStatus(response.body ?: "{}")
                Log.d(TAG, "Retrieved voting status: ${voteStatus.voteType}")
                Result.success(voteStatus)
            } else {
                Log.e(TAG, "Failed to fetch voting status: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch voting status"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching voting status", e)
            Result.failure(e)
        }
    }

    /**
     * Get voting results (after voting period ends)
     */
    suspend fun getGroupVoteResults(
        groupId: String,
        voteId: String
    ): Result<GroupVoteResults> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching vote results: groupId=$groupId, voteId=$voteId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/$voteId/results",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val results = parseVoteResults(response.body ?: "{}")
                Log.d(TAG, "Vote results: approved=${results.approved}, " +
                    "approvalPercentage=${results.approvalPercentage}%")
                Result.success(results)
            } else {
                Log.e(TAG, "Failed to fetch vote results: ${response.statusCode}")
                Result.failure(Exception("Failed to fetch vote results"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching vote results", e)
            Result.failure(e)
        }
    }

    /**
     * Get all active votes in a group
     */
    suspend fun getGroupActiveVotes(
        groupId: String
    ): Result<List<ActiveGroupVote>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val baseUrl = ApiClient.getBaseUrl()

            Log.d(TAG, "Fetching active votes for group: $groupId")

            val response = RawSocketHttpClient.getResponse(
                "$baseUrl/api/groups/$groupId/votes/active",
                mapOf("Authorization" to "Bearer $token")
            )

            return@withContext if (response.statusCode == 200) {
                val votes = parseActiveVotes(response.body ?: "[]")
                Log.d(TAG, "Retrieved ${votes.size} active votes")
                Result.success(votes)
            } else {
                Result.failure(Exception("Failed to fetch active votes"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active votes", e)
            Result.failure(e)
        }
    }

    // Helper functions

    private fun extractVoteId(responseBody: String): String {
        return try {
            val startIdx = responseBody.indexOf("\"voteId\"") + 10
            val endIdx = responseBody.indexOf("\"", startIdx)
            responseBody.substring(startIdx, endIdx)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun parseVoteStatus(jsonBody: String): GroupVoteStatus {
        return try {
            val gson = Gson()
            gson.fromJson(jsonBody, GroupVoteStatus::class.java)
                ?: throw Exception("Null response")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse vote status - invalid JSON", e)
            GroupVoteStatus(
                voteId = "", groupId = "", voteType = "", status = "",
                approvalThreshold = 50, totalMembers = 0, votesReceived = 0,
                approvingVotes = 0, rejectingVotes = 0, approvalPercentage = 0,
                expiresAt = 0, hasVoted = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vote status", e)
            GroupVoteStatus(
                voteId = "", groupId = "", voteType = "", status = "",
                approvalThreshold = 50, totalMembers = 0, votesReceived = 0,
                approvingVotes = 0, rejectingVotes = 0, approvalPercentage = 0,
                expiresAt = 0, hasVoted = false
            )
        }
    }

    private fun parseVoteResults(jsonBody: String): GroupVoteResults {
        return try {
            val gson = Gson()
            gson.fromJson(jsonBody, GroupVoteResults::class.java)
                ?: throw Exception("Null response")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse vote results - invalid JSON", e)
            GroupVoteResults(
                voteId = "", approved = false, approvalPercentage = 0,
                totalMembers = 0, approvingVotes = 0, rejectingVotes = 0,
                result = "error"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vote results", e)
            GroupVoteResults(
                voteId = "", approved = false, approvalPercentage = 0,
                totalMembers = 0, approvingVotes = 0, rejectingVotes = 0,
                result = "error"
            )
        }
    }

    private fun parseActiveVotes(jsonBody: String): List<ActiveGroupVote> {
        return try {
            val gson = Gson()
            val jsonArray = JsonParser.parseString(jsonBody).asJsonArray
            val votes = mutableListOf<ActiveGroupVote>()
            
            for (element in jsonArray) {
                try {
                    val vote = gson.fromJson(element, ActiveGroupVote::class.java)
                    votes.add(vote)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Failed to parse individual vote", e)
                    // Continue with other votes
                }
            }
            votes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse active votes", e)
            emptyList()
        }
    }
}

/**
 * Response for vote initiation
 */
data class GroupVoteResponse(
    val success: Boolean,
    val message: String,
    val voteId: String?,
    val durationHours: Int
)

/**
 * Response for casting a vote
 */
/**
 * Current status of a group vote
 */
data class GroupVoteStatus(
    val voteId: String,
    val groupId: String,
    val voteType: String, // "deletion", "remove_member", "settings_change"
    val status: String, // "active", "completed", "expired"
    val approvalThreshold: Int, // 50 for >50%
    val totalMembers: Int,
    val votesReceived: Int,
    val approvingVotes: Int,
    val rejectingVotes: Int,
    val approvalPercentage: Int,
    val expiresAt: Long,
    val hasVoted: Boolean
)

/**
 * Results of a completed vote
 */
data class GroupVoteResults(
    val voteId: String,
    val approved: Boolean,
    val approvalPercentage: Int,
    val totalMembers: Int,
    val approvingVotes: Int,
    val rejectingVotes: Int,
    val result: String // "approved", "rejected", "expired"
)

/**
 * Active vote in a group (for listing)
 */
data class ActiveGroupVote(
    val voteId: String,
    val voteType: String,
    val initiatedBy: String, // username
    val status: String,
    val approvalThreshold: Int,
    val approvingVotes: Int,
    val rejectingVotes: Int,
    val votesReceived: Int,
    val totalMembers: Int,
    val expiresAt: Long,
    val hasUserVoted: Boolean
)
