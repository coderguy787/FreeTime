package com.freetime.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String = "",
    val participantId: String = "",
    val chatName: String? = null,
    val lastMessageTime: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isGroupChat: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    // message content is stored encrypted
    val contentEncrypted: String = "",
    val mediaUrl: String? = null,
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val deletedLocally: Boolean = false,
    val deletedByRecipient: Boolean = false,
    val syncState: String = "pending",
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val mediaType: String? = null,
    val mediaName: String? = null,
    // reactions kept as json (sqlite has no map type)
    val reactions: String = ""
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String = "",
    val groupName: String = "",
    val groupDescription: String? = null,
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val memberCount: Int = 0,
    val isArchived: Boolean = false,
    val avatar: String? = null
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val memberId: String = "",
    val groupId: String = "",
    val userId: String = "",
    val username: String = "",
    val role: String = "member",
    val joinedAt: Long = 0L,
    val isMuted: Boolean = false,
    val isActive: Boolean = true
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String = "",
    val channelName: String = "",
    val description: String? = null,
    val isPrivate: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val subscriberCount: Int = 0,
    val avatar: String? = null
)

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey val requestId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L,
    val respondedAt: Long? = null
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val friendshipId: String = "",
    val userId: String = "",
    val friendId: String = "",
    val friendName: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val tags: String = "[]",
    val status: String? = null,
    val privacyLevel: String = "public",
    val email: String? = null,
    val connectedAt: Long = 0L,
    val lastInteraction: Long = 0L,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val isOnline: Boolean = false
)

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val mediaId: String = "",
    val messageId: String = "",
    val mediaType: String = "",
    val mediaUrl: String = "",
    val uploadedAt: Long = 0L,
    val isEncrypted: Boolean = true,
    val accessCount: Int = 0,
    val lastAccessTime: Long = 0L,
    val expiresAt: Long? = null
)

@Entity(tableName = "delete_requests")
data class DeleteRequestEntity(
    @PrimaryKey val requestId: String = "",
    val requestType: String = "",
    val initiatedBy: String = "",
    val targetId: String = "",
    val approvalCount: Int = 0,
    val requiredApprovals: Int = 1,
    val status: String = "pending",
    val expiresAt: Long? = null,
    val createdAt: Long = 0L
)

@Entity(tableName = "delete_approvals")
data class DeleteApprovalEntity(
    @PrimaryKey val approvalId: String = "",
    val requestId: String = "",
    val userId: String = "",
    val approved: Boolean = false,
    val approvedAt: Long = 0L
)

