package com.freetime.app.data.repository

import com.freetime.app.data.local.database.DeleteRequestEntity
import com.freetime.app.data.local.database.DeleteApprovalEntity
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.network.ApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import android.util.Log
import kotlinx.coroutines.flow.Flow

data class InitiateGroupDeleteRequest(
    val groupId: String,
    val initiatedBy: String,
    val requiredApprovals: Int
)

data class VoteOnDeleteRequest(
    val requestId: String,
    val userId: String,
    val approved: Boolean,
    val votedAt: Long = System.currentTimeMillis()
)

data class ExecuteGroupDeletionRequest(
    val groupId: String,
    val requestId: String
)

/**
 * Repository for delete voting system
 * Manages group deletion requests and approvals
 */
class DeleteVotingRepository(
    private val database: FreeTimeDatabase,
    private val apiService: ApiService,
    private val prefs: SharedPreferencesHelper
) {
    private val deleteRequestDao = database.deleteRequestDao()
    private val deleteApprovalDao = database.deleteApprovalDao()
    
    companion object {
        private const val TAG = "DeleteVotingRepository"
        private const val VOTING_DURATION_MS = 86400000L // 24 hours
    }

    suspend fun initiateGroupDeleteRequest(groupId: String, memberCount: Int): String {
        return try {
            val currentUserId = prefs.getUserId() ?: throw Exception("User not logged in")
            val requiredApprovals = (memberCount / 2) + 1
            val requestId = "delreq_${System.currentTimeMillis()}"
            val now = System.currentTimeMillis()
            
            val deleteRequest = DeleteRequestEntity(
                requestId = requestId,
                requestType = "group",
                initiatedBy = currentUserId,
                targetId = groupId,
                approvalCount = 1,
                requiredApprovals = requiredApprovals,
                status = "pending",
                createdAt = now,
                expiresAt = now + VOTING_DURATION_MS
            )
            
            deleteRequestDao.insertDeleteRequest(deleteRequest)
            
            val initiatorApproval = DeleteApprovalEntity(
                approvalId = "app_${System.currentTimeMillis()}_initiator",
                requestId = requestId,
                userId = currentUserId,
                approved = true,
                approvedAt = now
            )
            
            deleteApprovalDao.insertApproval(initiatorApproval)
            
            Log.d(TAG, "Delete request initiated: $requestId")
            requestId
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate delete request", e)
            throw e
        }
    }

    suspend fun voteOnDeleteRequest(requestId: String, approve: Boolean) {
        try {
            val currentUserId = prefs.getUserId() ?: return
            
            val approval = DeleteApprovalEntity(
                approvalId = "app_${System.currentTimeMillis()}_$currentUserId",
                requestId = requestId,
                userId = currentUserId,
                approved = approve,
                approvedAt = System.currentTimeMillis()
            )
            
            deleteApprovalDao.insertApproval(approval)
            Log.d(TAG, "Vote recorded for request: $requestId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vote", e)
        }
    }

    fun getPendingDeleteRequests(): Flow<List<DeleteRequestEntity>> {
        return deleteRequestDao.getPendingRequests()
    }

    suspend fun getDeleteRequestDetails(requestId: String): Map<String, Any> {
        return try {
            val request = deleteRequestDao.getDeleteRequest(requestId) ?: return emptyMap()
            val approvedCount = deleteApprovalDao.getApprovalCount(requestId).toInt()
            val timeRemaining = maxOf(0, (request.expiresAt ?: 0) - System.currentTimeMillis())
            
            mapOf(
                "requestId" to requestId,
                "groupId" to request.targetId,
                "status" to request.status,
                "approvals" to approvedCount,
                "required" to request.requiredApprovals,
                "timeRemaining" to timeRemaining
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get details", e)
            emptyMap()
        }
    }

    suspend fun cancelDeleteRequest(requestId: String) {
        try {
            deleteRequestDao.updateRequestStatus(requestId, "cancelled")
            Log.d(TAG, "Request cancelled: $requestId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel", e)
        }
    }
}
