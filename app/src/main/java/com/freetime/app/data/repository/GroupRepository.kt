package com.freetime.app.data.repository

import com.freetime.app.data.local.database.GroupEntity
import com.freetime.app.data.local.database.GroupMemberEntity
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.encryption.EncryptionManager
import com.freetime.app.data.network.ApiService
import com.freetime.app.data.network.CreateGroupRequest
import com.freetime.app.data.network.SendGroupMessageRequest
import com.freetime.app.data.network.LeaveGroupRequest
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import android.util.Log

/**
 * Repository for group chat operations
 * Manages group creation, membership, and messaging
 */
class GroupRepository(
    private val database: FreeTimeDatabase,
    private val encryptionManager: EncryptionManager,
    private val apiService: ApiService,
    private val prefs: SharedPreferencesHelper
) {
    private val groupDao = database.groupDao()
    private val groupMemberDao = database.groupMemberDao()
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    
    companion object {
        private const val TAG = "GroupRepository"
    }

    /**
     * Get all groups for the current user
     */
    fun getUserGroups(): Flow<List<GroupEntity>> {
        return groupDao.getAllGroups()
    }

    /**
     * Get a specific group by ID
     */
    suspend fun getGroupById(groupId: String): GroupEntity? {
        return groupDao.getGroupById(groupId)
    }

    /**
     * Create a new group via REAL API
     */
    suspend fun createGroup(
        groupName: String,
        groupDescription: String? = null,
        memberUsernames: List<String>
    ): String {
        try {
            val currentUserId = prefs.getUserId() ?: throw Exception("User not logged in")
            val token = prefs.getAccessToken() ?: throw Exception("User not authenticated")
            
            // Call REAL API to create group
            val createGroupRequest = CreateGroupRequest(
                name = groupName,
                description = groupDescription,
                members = memberUsernames
            )
            
            val response = apiService.createGroup(createGroupRequest, "Bearer $token")
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Failed to create group on server")
            }
            
            val groupResponse = response.body() ?: throw Exception("Group response body is null")
            val serverGroupId = groupResponse.groupId
            
            // Save locally
            val group = GroupEntity(
                groupId = serverGroupId,
                groupName = groupName,
                groupDescription = groupDescription,
                createdBy = currentUserId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                memberCount = memberUsernames.size + 1
            )
            
            groupDao.insertGroup(group)
            
            // Add creator as admin locally
            addGroupMember(
                GroupMemberEntity(
                    memberId = "member_${System.currentTimeMillis()}_${currentUserId}",
                    groupId = serverGroupId,
                    userId = currentUserId,
                    username = prefs.getUsername() ?: "Unknown",
                    role = "admin",
                    joinedAt = System.currentTimeMillis()
                )
            )
            
            // Create group chat entry locally
            createGroupChat(serverGroupId, groupName)
            
            Log.d(TAG, "Group created successfully on server: $serverGroupId")
            return serverGroupId
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create group", e)
            throw e
        }
    }

    /**
     * Create a chat entry for the group
     */
    suspend fun createGroupChat(groupId: String, groupName: String): String {
        val chatId = "chat_group_${System.currentTimeMillis()}"
        val chat = com.freetime.app.data.local.database.ChatEntity(
            chatId = chatId,
            participantId = groupId,
            chatName = groupName,
            createdAt = System.currentTimeMillis(),
            isGroupChat = true
        )
        chatDao.insertChat(chat)
        return chatId
    }

    /**
     * Add member to group
     */
    suspend fun addGroupMember(member: GroupMemberEntity) {
        groupMemberDao.insertMember(member)
    }

    /**
     * Remove member from group
     */
    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        try {
            val member = groupMemberDao.getMember(groupId, userId)
            if (member != null) {
                groupMemberDao.deleteMember(member)
                
                // Update member count
                val group = groupDao.getGroupById(groupId)
                if (group != null) {
                    groupDao.updateGroup(
                        group.copy(memberCount = group.memberCount - 1)
                    )
                }
                
                // Sync with server
                val token = prefs.getAccessToken() ?: return
                apiService.removeGroupMember(
                    groupId = groupId,
                    userId = userId,
                    token = "Bearer $token"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove group member", e)
        }
    }

    /**
     * Get all members of a group
     */
    suspend fun getGroupMembers(groupId: String): List<GroupMemberEntity> {
        return groupMemberDao.getGroupMembersSync(groupId)
    }

    /**
     * Get group member count
     */
    suspend fun getGroupMemberCount(groupId: String): Int {
        return groupMemberDao.getGroupMembersSync(groupId).size
    }

    /**
     * Send message to group
     */
    suspend fun sendGroupMessage(
        groupId: String,
        content: String,
        mediaUrl: String? = null
    ): String {
        try {
            val messageId = "msg_${System.currentTimeMillis()}"
            val currentUserId = prefs.getUserId() ?: throw Exception("User not logged in")
            
            // Encrypt message
            val encryptedContent = encryptionManager.encrypt(
                content,
                associatedData = "$groupId:$currentUserId"
            )
            
            // Get group chat
            val groupChat = chatDao.getChatByParticipant(groupId)
            if (groupChat == null) {
                Log.e(TAG, "Group chat not found for groupId: $groupId")
                throw Exception("Group chat not found")
            }
            
            val message = com.freetime.app.data.local.database.MessageEntity(
                messageId = messageId,
                chatId = groupChat.chatId,
                senderId = currentUserId,
                contentEncrypted = encryptedContent,
                mediaUrl = mediaUrl,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                deletedLocally = false,
                deletedByRecipient = false,
                syncState = "pending"
            )
            
            messageDao.insertMessage(message)
            
            // Sync with server
            syncGroupMessage(messageId, groupId, encryptedContent)
            
            Log.d(TAG, "Group message sent successfully: $messageId")
            return messageId
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send group message", e)
            throw e
        }
    }

    /**
     * Get all messages in a group
     */
    suspend fun getGroupMessages(groupId: String, limit: Int = 100): List<com.freetime.app.data.local.database.MessageEntity> {
        // Get chat for this group and fetch messages
        return chatDao.getChatByParticipant(groupId)?.let {
            messageDao.getMessagesByChatIdSync(it.chatId, limit)
        } ?: emptyList()
    }

    /**
     * Delete all messages in group (for voting system)
     */
    suspend fun deleteAllGroupMessages(groupId: String) {
        val messages = messageDao.getGroupMessagesSync(groupId)
        messages.forEach { message ->
            messageDao.updateMessage(
                message.copy(
                    deletedLocally = true,
                    deletedByRecipient = true,
                    syncState = "deleted"
                )
            )
        }
    }

    /**
     * Leave group
     */
    suspend fun leaveGroup(groupId: String) {
        try {
            val currentUserId = prefs.getUserId() ?: return
            removeMemberFromGroup(groupId, currentUserId)
            
            // Notify server
            val token = prefs.getAccessToken() ?: return
            apiService.leaveGroup(
                groupId = groupId,
                request = LeaveGroupRequest(groupId, currentUserId),
                token = "Bearer $token"
            )
            
            Log.d(TAG, "Left group successfully: $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave group", e)
        }
    }

    /**
     * Sync group creation with server
     */
    private suspend fun syncGroupWithServer(
        group: GroupEntity,
        memberUsernames: List<String>,
        creatorId: String
    ) {
        try {
            val token = prefs.getAccessToken() ?: return
            
            val request = CreateGroupRequest(
                name = group.groupName,
                description = group.groupDescription,
                members = memberUsernames
            )
            
            apiService.createGroup(
                request = request,
                token = "Bearer $token"
            )
            
            Log.d(TAG, "Group synced with server: ${group.groupId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync group with server", e)
            // Continue anyway, will retry on next sync
        }
    }

    /**
     * Sync group message with server
     */
    private suspend fun syncGroupMessage(
        messageId: String,
        groupId: String,
        encryptedContent: String
    ) {
        try {
            val token = prefs.getAccessToken() ?: return
            
            val request = SendGroupMessageRequest(
                messageId = messageId,
                groupId = groupId,
                content = encryptedContent,
                timestamp = System.currentTimeMillis()
            )
            
            apiService.sendGroupMessage(
                groupId = groupId,
                request = request,
                token = "Bearer $token"
            )
            
            // Update sync state
            messageDao.getMessageById(messageId)?.let { message ->
                messageDao.updateMessage(message.copy(syncState = "synced"))
            }
            
            Log.d(TAG, "Message synced with server: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync message with server", e)
            // Message will be retried later
        }
    }
}
