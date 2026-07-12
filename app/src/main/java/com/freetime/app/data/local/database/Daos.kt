package com.freetime.app.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Objects for the local encrypted database
 */

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE participantId = :participantId")
    suspend fun getChatByParticipant(participantId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE isGroupChat = 0 ORDER BY updatedAt DESC")
    fun getAllDirectChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE isGroupChat = 1 ORDER BY updatedAt DESC")
    fun getAllGroupChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesInChat(chatId: String)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesForChat(chatId: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesByChatId(chatId: String, limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isRead = 0")
    fun getUnreadMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId")
    suspend fun markAllAsRead(chatId: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND deletedLocally = 0")
    suspend fun getMessageCountInChat(chatId: String): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesByChatIdSync(chatId: String, limit: Int = 100): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId IN (SELECT chatId FROM chats WHERE participantId = :groupId)")
    suspend fun getGroupMessagesSync(groupId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE syncState = :syncState ORDER BY timestamp DESC")
    fun getMessagesBySyncState(syncState: String): Flow<List<MessageEntity>>
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId IN (SELECT groupId FROM group_members WHERE userId = :userId)")
    fun getGroupsForUser(userId: String): Flow<List<GroupEntity>>
}

@Dao
interface GroupMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Update
    suspend fun updateMember(member: GroupMemberEntity)

    @Delete
    suspend fun deleteMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun getGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND isActive = 1")
    suspend fun getActiveMemberCount(groupId: String): Int

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun getMember(groupId: String, userId: String): GroupMemberEntity?

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembers(groupId: String)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMembersSync(groupId: String): List<GroupMemberEntity>

    @Delete
    suspend fun removeGroupMember(member: GroupMemberEntity)
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE channelId = :channelId")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels ORDER BY updatedAt DESC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY updatedAt DESC")
    fun getAllSubscribedChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE channelName LIKE '%' || :query || '%' ORDER BY channelName ASC")
    suspend fun searchChannels(query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY subscriberCount DESC LIMIT 10")
    suspend fun getFeaturedChannels(): List<ChannelEntity>
}

@Dao
interface FriendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequestEntity)

    @Update
    suspend fun updateRequest(request: FriendRequestEntity)

    @Delete
    suspend fun deleteRequest(request: FriendRequestEntity)

    @Query("SELECT * FROM friend_requests WHERE requestId = :requestId")
    suspend fun getRequestById(requestId: String): FriendRequestEntity?

    @Query("SELECT * FROM friend_requests WHERE toUserId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    fun getPendingRequests(userId: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE fromUserId = :userId AND status = 'pending' ORDER BY createdAt DESC")
    fun getSentRequests(userId: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT * FROM friend_requests WHERE (fromUserId = :userId OR toUserId = :userId) AND status = 'accepted'")
    fun getAcceptedRequests(userId: String): Flow<List<FriendRequestEntity>>
}

@Dao
interface FriendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    @Update
    suspend fun updateFriend(friend: FriendEntity)

    @Delete
    suspend fun deleteFriend(friend: FriendEntity)

    @Query("SELECT * FROM friends WHERE friendshipId = :friendshipId")
    suspend fun getFriendById(friendshipId: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE userId = :userId AND isBlocked = 0 ORDER BY lastInteraction DESC")
    fun getFriendsForUser(userId: String): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE userId = :userId AND isBlocked = 1")
    fun getBlockedFriends(userId: String): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE userId = :userId AND friendId = :friendId")
    suspend fun getFriendship(userId: String, friendId: String): FriendEntity?

    @Query("UPDATE friends SET isBlocked = 1 WHERE userId = :userId AND friendId = :friendId")
    suspend fun blockFriend(userId: String, friendId: String)

    @Query("UPDATE friends SET isBlocked = 0 WHERE userId = :userId AND friendId = :friendId")
    suspend fun unblockFriend(userId: String, friendId: String)
}

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Update
    suspend fun updateMedia(media: MediaEntity)

    @Delete
    suspend fun deleteMedia(media: MediaEntity)

    @Query("SELECT * FROM media WHERE mediaId = :mediaId")
    suspend fun getMediaById(mediaId: String): MediaEntity?

    @Query("SELECT * FROM media WHERE messageId = :messageId")
    fun getMediaForMessage(messageId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE mediaType = :type ORDER BY uploadedAt DESC LIMIT :limit")
    fun getMediaByType(type: String, limit: Int = 50): Flow<List<MediaEntity>>

    @Query("UPDATE media SET accessCount = accessCount + 1, lastAccessTime = :timestamp WHERE mediaId = :mediaId")
    suspend fun incrementAccessCount(mediaId: String, timestamp: Long)

    @Query("SELECT * FROM media WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun getExpiredMedia(currentTime: Long): List<MediaEntity>

    @Query("DELETE FROM media WHERE mediaId = :mediaId")
    suspend fun deleteMediaFile(mediaId: String)

    @Query("SELECT * FROM media WHERE messageId = :messageId")
    suspend fun getMediaByMessageId(messageId: String): List<MediaEntity>?

    @Query("SELECT * FROM media ORDER BY uploadedAt DESC")
    suspend fun getAllMedia(): List<MediaEntity>?
}

@Dao
interface DeleteRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: DeleteRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeleteRequest(request: DeleteRequestEntity)

    @Update
    suspend fun updateRequest(request: DeleteRequestEntity)

    @Query("UPDATE delete_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("SELECT * FROM delete_requests WHERE requestId = :requestId")
    suspend fun getRequestById(requestId: String): DeleteRequestEntity?

    @Query("SELECT * FROM delete_requests WHERE requestId = :requestId")
    suspend fun getDeleteRequest(requestId: String): DeleteRequestEntity?

    @Query("SELECT * FROM delete_requests WHERE status = 'pending' ORDER BY createdAt DESC")
    fun getPendingRequests(): Flow<List<DeleteRequestEntity>>

    @Query("SELECT * FROM delete_requests WHERE status = 'pending' ORDER BY createdAt DESC")
    suspend fun getPendingDeleteRequests(): List<DeleteRequestEntity>

    @Query("SELECT * FROM delete_requests WHERE targetId = :targetId AND requestType = :type")
    suspend fun getRequestsForTarget(targetId: String, type: String): List<DeleteRequestEntity>
}

@Dao
interface DeleteApprovalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproval(approval: DeleteApprovalEntity)

    @Update
    suspend fun updateApproval(approval: DeleteApprovalEntity)

    @Query("SELECT COUNT(*) FROM delete_approvals WHERE requestId = :requestId AND approved = 1")
    suspend fun getApprovalCount(requestId: String): Int

    @Query("SELECT * FROM delete_approvals WHERE requestId = :requestId AND userId = :userId")
    suspend fun getUserApproval(requestId: String, userId: String): DeleteApprovalEntity?

    @Query("SELECT * FROM delete_approvals WHERE requestId = :requestId")
    fun getApprovalsForRequest(requestId: String): Flow<List<DeleteApprovalEntity>>
}

@Dao
interface CallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistoryEntity)

    @Update
    suspend fun updateCall(call: CallHistoryEntity)

    @Delete
    suspend fun deleteCall(call: CallHistoryEntity)

    @Query("SELECT * FROM call_history WHERE callId = :callId")
    suspend fun getCallById(callId: String): CallHistoryEntity?

    @Query("SELECT * FROM call_history WHERE participantId = :participantId ORDER BY startTime DESC LIMIT :limit")
    fun getCallHistory(participantId: String, limit: Int = 100): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE callType = 'video' ORDER BY startTime DESC")
    fun getVideoCallHistory(): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE status = 'missed' ORDER BY startTime DESC")
    fun getMissedCalls(): Flow<List<CallHistoryEntity>>

    @Query("DELETE FROM call_history WHERE callId = :callId")
    suspend fun deleteCallById(callId: String)

    @Query("SELECT * FROM call_history WHERE participantId = :participantId")
    suspend fun getCallsForChat(participantId: String): List<CallHistoryEntity>?

    @Query("SELECT * FROM call_history WHERE participantId = :groupId AND isGroupCall = 1")
    suspend fun getGroupCalls(groupId: String): List<CallHistoryEntity>?
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: SyncStateEntity)

    @Update
    suspend fun updateSync(sync: SyncStateEntity)

    @Delete
    suspend fun deleteSync(sync: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE isSynced = 0 ORDER BY createdAt ASC")
    fun getUnsynced(): Flow<List<SyncStateEntity>>

    @Query("UPDATE sync_state SET isSynced = 1, lastSyncAttempt = :timestamp WHERE syncId = :syncId")
    suspend fun markSynced(syncId: String, timestamp: Long)

    @Query("UPDATE sync_state SET syncRetries = syncRetries + 1 WHERE syncId = :syncId")
    suspend fun incrementRetries(syncId: String)

    @Query("DELETE FROM sync_state WHERE isSynced = 1 AND lastSyncAttempt < :olderThan")
    suspend fun cleanupOldSyncedEntries(olderThan: Long)
}
