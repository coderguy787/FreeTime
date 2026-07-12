package com.freetime.app.testutil

import com.freetime.app.data.model.*
import java.util.*

/**
 * Test data builders and factories for unit tests
 * 
 * Provides reusable test fixtures and builders for common test data models
 * allowing tests to quickly create realistic test scenarios.
 */
object TestDataFactory {
    
    // ===== Friend Request Builders =====
    
    fun createTestFriendRequest(
        id: String = "req_${UUID.randomUUID()}",
        senderId: String = "user_sender_123",
        senderUsername: String = "sender_user",
        createdAt: Long = System.currentTimeMillis()
    ): FriendRequest {
        return FriendRequest(
            id = id,
            senderId = senderId,
            senderUsername = senderUsername,
            createdAt = createdAt
        )
    }
    
    fun createMultipleFriendRequests(count: Int = 5): List<FriendRequest> {
        return (1..count).map { i ->
            createTestFriendRequest(
                id = "req_$i",
                senderId = "user_$i",
                senderUsername = "user_$i"
            )
        }
    }
    
    // ===== Friend Builders =====
    
    fun createTestFriend(
        id: String = "user_${UUID.randomUUID()}",
        username: String = "test_user",
        isActive: Boolean = true
    ): Friend {
        return Friend(
            id = id,
            username = username,
            isActive = isActive
        )
    }
    
    fun createMultipleFriends(count: Int = 10): List<Friend> {
        return (1..count).map { i ->
            createTestFriend(
                id = "user_$i",
                username = "friend_$i",
                isActive = i % 2 == 0
            )
        }
    }
    
    fun createActiveFriends(count: Int = 5): List<Friend> {
        return (1..count).map { i ->
            createTestFriend(
                id = "user_active_$i",
                username = "active_friend_$i",
                isActive = true
            )
        }
    }
    
    fun createInactiveFriends(count: Int = 5): List<Friend> {
        return (1..count).map { i ->
            createTestFriend(
                id = "user_inactive_$i",
                username = "inactive_friend_$i",
                isActive = false
            )
        }
    }
    
    // ===== Vote Option Builders =====
    
    fun createTestVoteOption(
        id: String = "opt_${UUID.randomUUID()}",
        text: String = "Vote option",
        voteCount: Int = 0,
        totalVotes: Int = 100
    ): VoteOption {
        return VoteOption(
            id = id,
            text = text,
            voteCount = voteCount,
            totalVotes = totalVotes
        )
    }
    
    fun createMultipleVoteOptions(
        count: Int = 3,
        totalVotes: Int = 100
    ): List<VoteOption> {
        return (1..count).map { i ->
            createTestVoteOption(
                id = "opt_$i",
                text = "Option $i",
                voteCount = totalVotes / count,
                totalVotes = totalVotes
            )
        }
    }
    
    // ===== Group Vote Builders =====
    
    fun createTestGroupVote(
        id: String = "vote_${UUID.randomUUID()}",
        groupId: String = "group_123",
        question: String = "Test question?",
        options: List<VoteOption> = createMultipleVoteOptions(3),
        totalMembers: Int = 50,
        voteCount: Int = 50,
        completedAt: Long? = null
    ): GroupVote {
        return GroupVote(
            id = id,
            groupId = groupId,
            question = question,
            options = options,
            totalMembers = totalMembers,
            voteCount = voteCount,
            completedAt = completedAt
        )
    }
    
    fun createActiveVotes(count: Int = 3): List<GroupVote> {
        return (1..count).map { i ->
            createTestGroupVote(
                id = "vote_active_$i",
                question = "Active question $i?",
                completedAt = null
            )
        }
    }
    
    fun createCompletedVotes(count: Int = 3): List<GroupVote> {
        return (1..count).map { i ->
            createTestGroupVote(
                id = "vote_completed_$i",
                question = "Completed question $i?",
                completedAt = System.currentTimeMillis()
            )
        }
    }
    
    fun createMixedVotes(activeCount: Int = 2, completedCount: Int = 2): List<GroupVote> {
        return createActiveVotes(activeCount) + createCompletedVotes(completedCount)
    }
    
    // ===== Channel Builders =====
    
    fun createTestChannel(
        id: String = "channel_${UUID.randomUUID()}",
        name: String = "Test Channel",
        description: String = "Test channel description",
        memberCount: Int = 10,
        createdAt: Long = System.currentTimeMillis(),
        isPrivate: Boolean = false
    ): Channel {
        return Channel(
            id = id,
            name = name,
            description = description,
            memberCount = memberCount,
            createdAt = createdAt,
            isPrivate = isPrivate
        )
    }
    
    fun createMultipleChannels(count: Int = 5): List<Channel> {
        return (1..count).map { i ->
            createTestChannel(
                id = "channel_$i",
                name = "Channel $i",
                memberCount = i * 10
            )
        }
    }
    
    fun createPrivateChannels(count: Int = 3): List<Channel> {
        return (1..count).map { i ->
            createTestChannel(
                id = "private_channel_$i",
                name = "Private Channel $i",
                isPrivate = true
            )
        }
    }
    
    // ===== Channel Member Builders =====
    
    fun createTestChannelMember(
        userId: String = "user_${UUID.randomUUID()}",
        username: String = "test_member",
        role: String = "member",
        isMuted: Boolean = false,
        isActive: Boolean = true
    ): ChannelMember {
        return ChannelMember(
            userId = userId,
            username = username,
            role = role,
            isMuted = isMuted,
            isActive = isActive
        )
    }
    
    fun createMultipleChannelMembers(
        count: Int = 10,
        moderatorCount: Int = 1
    ): List<ChannelMember> {
        return (1..count).map { i ->
            createTestChannelMember(
                userId = "user_$i",
                username = "member_$i",
                role = if (i <= moderatorCount) "moderator" else "member"
            )
        }
    }
    
    fun createModerators(count: Int = 3): List<ChannelMember> {
        return (1..count).map { i ->
            createTestChannelMember(
                userId = "mod_$i",
                username = "moderator_$i",
                role = "moderator"
            )
        }
    }
    
    fun createMutedMembers(count: Int = 5): List<ChannelMember> {
        return (1..count).map { i ->
            createTestChannelMember(
                userId = "muted_$i",
                username = "muted_member_$i",
                isMuted = true
            )
        }
    }
    
    // ===== WebSocket Event Builders =====
    
    fun createTestWebSocketEvent(
        type: String = "TEST_EVENT",
        data: Map<String, Any> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    ): WebSocketEvent {
        return WebSocketEvent(
            type = type,
            data = data,
            timestamp = timestamp
        )
    }
    
    fun createFriendRequestEvent(
        senderId: String = "user_123",
        senderUsername: String = "sender",
        requestId: String = "req_456"
    ): WebSocketEvent {
        return createTestWebSocketEvent(
            type = "FRIEND_REQUEST_RECEIVED",
            data = mapOf(
                "senderId" to senderId,
                "senderUsername" to senderUsername,
                "requestId" to requestId
            )
        )
    }
    
    fun createVoteUpdatedEvent(
        voteId: String = "vote_789",
        optionId: String = "opt_1",
        newCount: String = "25"
    ): WebSocketEvent {
        return createTestWebSocketEvent(
            type = "VOTE_UPDATED",
            data = mapOf(
                "voteId" to voteId,
                "optionId" to optionId,
                "newCount" to newCount
            )
        )
    }
    
    fun createMemberJoinedEvent(
        userId: String = "user_999",
        username: String = "new_member",
        channelId: String = "channel_123"
    ): WebSocketEvent {
        return createTestWebSocketEvent(
            type = "MEMBER_JOINED",
            data = mapOf(
                "userId" to userId,
                "username" to username,
                "channelId" to channelId
            )
        )
    }
    
    fun createMemberLeftEvent(
        userId: String = "user_999",
        username: String = "left_member",
        channelId: String = "channel_123"
    ): WebSocketEvent {
        return createTestWebSocketEvent(
            type = "MEMBER_LEFT",
            data = mapOf(
                "userId" to userId,
                "username" to username,
                "channelId" to channelId
            )
        )
    }
    
    fun createMultipleWebSocketEvents(count: Int = 10): List<WebSocketEvent> {
        val eventTypes = listOf(
            "FRIEND_REQUEST_RECEIVED",
            "VOTE_UPDATED",
            "MEMBER_JOINED",
            "MEMBER_LEFT"
        )
        
        return (1..count).map { i ->
            createTestWebSocketEvent(
                type = eventTypes[i % eventTypes.size],
                data = mapOf("index" to i),
                timestamp = System.currentTimeMillis() + (i * 1000L)
            )
        }
    }
}

/**
 * Test assertion helpers for common test scenarios
 */
object TestAssertions {
    
    fun assertFriendRequestValid(request: FriendRequest) {
        assert(request.id.isNotEmpty())
        assert(request.senderId.isNotEmpty())
        assert(request.senderUsername.isNotEmpty())
        assert(request.createdAt > 0)
    }
    
    fun assertFriendValid(friend: Friend) {
        assert(friend.id.isNotEmpty())
        assert(friend.username.isNotEmpty())
    }
    
    fun assertVoteValid(vote: GroupVote) {
        assert(vote.id.isNotEmpty())
        assert(vote.groupId.isNotEmpty())
        assert(vote.question.isNotEmpty())
        assert(vote.options.isNotEmpty())
        assert(vote.totalMembers > 0)
    }
    
    fun assertChannelValid(channel: Channel) {
        assert(channel.id.isNotEmpty())
        assert(channel.name.isNotEmpty())
        assert(channel.memberCount >= 0)
    }
    
    fun assertChannelMemberValid(member: ChannelMember) {
        assert(member.userId.isNotEmpty())
        assert(member.username.isNotEmpty())
        assert(member.role.isNotEmpty())
    }
    
    fun assertWebSocketEventValid(event: WebSocketEvent) {
        assert(event.type.isNotEmpty())
        assert(event.timestamp > 0)
    }
}

/**
 * Test data builders with builder pattern for more complex scenarios
 */
class GroupVoteBuilder {
    private var id: String = "vote_${UUID.randomUUID()}"
    private var groupId: String = "group_123"
    private var question: String = "Test question?"
    private var options: List<VoteOption> = emptyList()
    private var totalMembers: Int = 50
    private var voteCount: Int = 25
    private var completedAt: Long? = null
    
    fun withId(id: String) = apply { this.id = id }
    fun withGroupId(groupId: String) = apply { this.groupId = groupId }
    fun withQuestion(question: String) = apply { this.question = question }
    fun withOptions(options: List<VoteOption>) = apply { this.options = options }
    fun withTotalMembers(count: Int) = apply { this.totalMembers = count }
    fun withVoteCount(count: Int) = apply { this.voteCount = count }
    fun withCompletedAt(timestamp: Long?) = apply { this.completedAt = timestamp }
    fun asActive() = apply { this.completedAt = null }
    fun asCompleted() = apply { this.completedAt = System.currentTimeMillis() }
    
    fun build(): GroupVote {
        return GroupVote(
            id = id,
            groupId = groupId,
            question = question,
            options = options.ifEmpty { TestDataFactory.createMultipleVoteOptions() },
            totalMembers = totalMembers,
            voteCount = voteCount,
            completedAt = completedAt
        )
    }
}

class ChannelBuilder {
    private var id: String = "channel_${UUID.randomUUID()}"
    private var name: String = "Test Channel"
    private var description: String = "Test description"
    private var memberCount: Int = 10
    private var createdAt: Long = System.currentTimeMillis()
    private var isPrivate: Boolean = false
    
    fun withId(id: String) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withDescription(description: String) = apply { this.description = description }
    fun withMemberCount(count: Int) = apply { this.memberCount = count }
    fun withCreatedAt(timestamp: Long) = apply { this.createdAt = timestamp }
    fun asPrivate() = apply { this.isPrivate = true }
    fun asPublic() = apply { this.isPrivate = false }
    
    fun build(): Channel {
        return Channel(
            id = id,
            name = name,
            description = description,
            memberCount = memberCount,
            createdAt = createdAt,
            isPrivate = isPrivate
        )
    }
}

class ChannelMemberBuilder {
    private var userId: String = "user_${UUID.randomUUID()}"
    private var username: String = "test_user"
    private var role: String = "member"
    private var isMuted: Boolean = false
    private var isActive: Boolean = true
    
    fun withUserId(userId: String) = apply { this.userId = userId }
    fun withUsername(username: String) = apply { this.username = username }
    fun asModeratorRole() = apply { this.role = "moderator" }
    fun asMemberRole() = apply { this.role = "member" }
    fun asMuted() = apply { this.isMuted = true }
    fun asUnmuted() = apply { this.isMuted = false }
    fun asActive() = apply { this.isActive = true }
    fun asInactive() = apply { this.isActive = false }
    
    fun build(): ChannelMember {
        return ChannelMember(
            userId = userId,
            username = username,
            role = role,
            isMuted = isMuted,
            isActive = isActive
        )
    }
}
