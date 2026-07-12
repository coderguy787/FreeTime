/**
 * WebSocket Broadcast Utilities
 * Broadcasting system for all real-time events:
 * - Friend requests
 * - Group voting
 * - Channel messages
 * - Media download updates
 * 
 * Supports both raw WebSockets (port 8080) and Socket.IO (port 443).
 */

const { v4: uuidv4 } = require('uuid');

/**
 * Event types for all features
 */
const EVENT_TYPES = {
    // Friend System Events
    FRIEND_REQUEST_RECEIVED: 'friend.request.received',
    FRIEND_REQUEST_ACCEPTED: 'friend.request.accepted',
    FRIEND_REQUEST_REJECTED: 'friend.request.rejected',
    FRIEND_REQUEST_CANCELED: 'friend.request.canceled',
    
    // Group Events
    GROUP_MESSAGE_RECEIVED: 'group.message.received',
    GROUP_MEMBER_JOINED: 'group.member.joined',
    GROUP_MEMBER_LEFT: 'group.member.left',
    GROUP_MEMBER_PROMOTED: 'group.member.promoted',
    GROUP_MEMBER_REMOVED: 'group.member.removed',
    
    // Group Voting Events
    GROUP_VOTE_INITIATED: 'group.vote.initiated',
    GROUP_VOTE_CAST: 'group.vote.cast',
    GROUP_VOTE_UPDATED: 'group.vote.updated',
    GROUP_DELETED: 'group.deleted',
    
    // Channel Events
    CHANNEL_MESSAGE_RECEIVED: 'channel.message.received',
    CHANNEL_MESSAGE_DELETED: 'channel.message.deleted',
    MEMBER_PROMOTED: 'channel.member.promoted',
    MEMBER_DEMOTED: 'channel.member.demoted',
    MEMBER_JOINED: 'channel.member.joined',
    MEMBER_LEFT: 'channel.member.left',
    
    // Media Download Events
    MEDIA_DOWNLOAD_REQUESTED: 'media.download.requested',
    MEDIA_DOWNLOAD_APPROVED: 'media.download.approved',
    MEDIA_DOWNLOAD_DENIED: 'media.download.denied',
    
    // Call Events
    CALL_INCOMING: 'call.incoming',
    CALL_ACCEPTED: 'call.accepted',
    CALL_REJECTED: 'call.rejected',
    CALL_MISSED: 'call.missed',
    CALL_ENDED: 'call.ended',
    
    // Online Status
    USER_ONLINE: 'user.online',
    USER_OFFLINE: 'user.offline',
    USER_PROFILE_UPDATED: 'user.profile.updated'
};

/**
 * Helper to emit via Socket.IO if available
 * @param {string} target - Room name or user room (user:userId)
 * @param {string} eventType - Event name
 * @param {object} data - Data to send
 */
function emitToSocketIO(target, eventType, data) {
    if (!global.socketIoServer) {
        console.warn(`[Socket.IO] Server not initialized - cannot broadcast ${eventType} to ${target}`);
        return false;
    }
    
    try {
        // Socket.IO expects event names, not a wrapped message object with 'type' field
        global.socketIoServer.to(target).emit(eventType, data);
        console.log(`[✓ Socket.IO] Emitted ${eventType} to ${target}`);
        return true;
    } catch (err) {
        console.error(`[✗ Socket.IO] Broadcast error to ${target} for ${eventType}:`, err.message);
        return false;
    }
}

/**
 * Broadcast message to specific user
 * @param {Map} clients - Connected raw WebSocket clients map
 * @param {string} userId - Target user ID
 * @param {string} eventType - Event type (see EVENT_TYPES)
 * @param {object} data - Event data
 */
function broadcastToUser(clients, userId, eventType, data) {
    // 1. Send via Socket.IO (Preferred for Android)
    emitToSocketIO(`user:${userId}`, eventType, data);

    // 2. Send via raw WebSockets (Legacy/Admin Panel)
    const message = {
        id: uuidv4(),
        type: eventType,
        timestamp: new Date().toISOString(),
        data: data
    };

    if (!clients) return true; // Socket.IO already sent, consider success

    const userClients = Array.from(clients.values()).filter(
        client => (client.userId === userId || (client.user && client.user.id === userId)) && client.ws.readyState === 1 // 1 = OPEN
    );

    userClients.forEach(client => {
        try {
            client.ws.send(JSON.stringify(message));
        } catch (err) {
            console.error(`Error broadcasting to ${userId}:`, err.message);
        }
    });

    return true;
}

/**
 * Broadcast to multiple users
 * @param {Map} clients - Connected clients map
 * @param {array} userIds - Array of user IDs
 * @param {string} eventType - Event type
 * @param {object} data - Event data
 */
function broadcastToUsers(clients, userIds, eventType, data) {
    let successCount = 0;
    
    userIds.forEach(userId => {
        if (broadcastToUser(clients, userId, eventType, data)) {
            successCount++;
        }
    });

    return successCount;
}

/**
 * Broadcast to a room/group via Socket.IO and fallback to individual users for raw WS
 */
function broadcastToRoom(clients, userIds, roomName, eventType, data) {
    // 1. Send via Socket.IO Room (Very efficient)
    emitToSocketIO(roomName, eventType, data);

    // 2. Fallback to raw WS users
    return broadcastToUsers(clients, userIds, eventType, data);
}

/**
 * Broadcast to all connected users
 * @param {Map} clients - Connected clients map
 * @param {string} eventType - Event type
 * @param {object} data - Event data
 */
function broadcastToAll(clients, eventType, data) {
    // 1. Send via Socket.IO
    if (global.socketIoServer) {
        global.socketIoServer.emit(eventType, data);
    }

    // 2. Send via raw WebSockets
    const message = {
        id: uuidv4(),
        type: eventType,
        timestamp: new Date().toISOString(),
        data: data
    };

    if (!clients) return 0;

    let count = 0;
    clients.forEach(client => {
        if (client.ws.readyState === 1) { // OPEN
            try {
                client.ws.send(JSON.stringify(message));
                count++;
            } catch (err) {
                console.error(`Error broadcasting:`, err.message);
            }
        }
    });

    return count;
}

/**
 * Broadcast to channel members
 * @param {Map} clients - Connected clients map
 * @param {array} memberIds - Array of member IDs
 * @param {string} eventType - Event type
 * @param {object} data - Event data
 * @param {string} channelId - Optional channel ID for Socket.IO room
 */
function broadcastToChannel(clients, memberIds, eventType, data, channelId) {
    if (channelId) {
        return broadcastToRoom(clients, memberIds, `channel:${channelId}`, eventType, data);
    }
    return broadcastToUsers(clients, memberIds, eventType, data);
}

/**
 * Broadcast to group members
 * @param {Map} clients - Connected clients map
 * @param {array} memberIds - Array of member IDs
 * @param {string} eventType - Event type
 * @param {object} data - Event data
 * @param {string} groupId - Optional group ID for Socket.IO room
 */
function broadcastToGroup(clients, memberIds, eventType, data, groupId) {
    if (groupId) {
        return broadcastToRoom(clients, memberIds, `group:${groupId}`, eventType, data);
    }
    return broadcastToUsers(clients, memberIds, eventType, data);
}

// ============================================
// FRIEND SYSTEM BROADCAST FUNCTIONS
// ============================================

/**
 * Notify user of incoming friend request
 */
function notifyFriendRequestReceived(clients, recipientId, friendRequest) {
    return broadcastToUser(clients, recipientId, EVENT_TYPES.FRIEND_REQUEST_RECEIVED, {
        requestId: friendRequest.id,
        senderId: friendRequest.senderId,
        senderUsername: friendRequest.senderUsername,
        senderProfile: friendRequest.senderProfile,
        createdAt: friendRequest.createdAt
    });
}

/**
 * Notify sender that request was accepted
 */
function notifyFriendRequestAccepted(clients, senderId, friendData) {
    return broadcastToUser(clients, senderId, EVENT_TYPES.FRIEND_REQUEST_ACCEPTED, {
        userId: friendData.friendId,
        username: friendData.friendUsername,
        profile: friendData.friendProfile,
        acceptedAt: new Date().toISOString()
    });
}

/**
 * Notify sender that request was rejected
 */
function notifyFriendRequestRejected(clients, senderId, friendId) {
    return broadcastToUser(clients, senderId, EVENT_TYPES.FRIEND_REQUEST_REJECTED, {
        userId: friendId,
        rejectedAt: new Date().toISOString()
    });
}

/**
 * Notify recipient that request was canceled
 */
function notifyFriendRequestCanceled(clients, recipientId, senderId) {
    return broadcastToUser(clients, recipientId, EVENT_TYPES.FRIEND_REQUEST_CANCELED, {
        senderId: senderId,
        canceledAt: new Date().toISOString()
    });
}

// ============================================
// GROUP BROADCAST FUNCTIONS
// ============================================

/**
 * Notify group members of new message
 */
function notifyGroupMessage(clients, memberIds, messageData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MESSAGE_RECEIVED, {
        messageId: messageData.messageId,
        groupId: messageData.groupId || groupId,
        senderId: messageData.senderId,
        senderUsername: messageData.senderUsername,
        senderAvatar: messageData.senderAvatar,
        content: messageData.content,
        mediaId: messageData.mediaId,
        mediaType: messageData.mediaType,
        createdAt: messageData.createdAt || Date.now()
    }, groupId);
}

/**
 * Notify group of new member joined
 */
function notifyGroupMemberJoined(clients, memberIds, joinData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MEMBER_JOINED, {
        groupId: joinData.groupId || groupId,
        userId: joinData.userId,
        username: joinData.username,
        displayName: joinData.displayName || joinData.username,
        avatar: joinData.avatar,
        action: 'joined',
        timestamp: Date.now()
    }, groupId);
}

/**
 * Notify group of member leaving
 */
function notifyGroupMemberLeft(clients, memberIds, leaveData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MEMBER_LEFT, {
        groupId: leaveData.groupId || groupId,
        userId: leaveData.userId,
        username: leaveData.username,
        displayName: leaveData.displayName || leaveData.username,
        action: 'left',
        timestamp: Date.now()
    }, groupId);
}

/**
 * Notify group of member being removed (kicked)
 */
function notifyGroupMemberRemoved(clients, memberIds, removeData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MEMBER_REMOVED, {
        groupId: removeData.groupId || groupId,
        userId: removeData.userId,
        username: removeData.username,
        displayName: removeData.displayName || removeData.username,
        actor: removeData.actor, // Who performed the removal
        action: 'removed',
        timestamp: Date.now()
    }, groupId);
}

/**
 * Notify group of member promotion
 */
function notifyGroupMemberPromoted(clients, memberIds, promotionData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MEMBER_PROMOTED, {
        groupId: promotionData.groupId || groupId,
        userId: promotionData.userId || promotionData.memberId,
        username: promotionData.username || promotionData.memberUsername,
        promotedBy: promotionData.promotedBy,
        action: 'promoted',
        timestamp: Date.now()
    }, groupId);
}

// ============================================
// GROUP VOTING BROADCAST FUNCTIONS
// ============================================

/**
 * Notify group members of voting initiation
 */
function notifyGroupVoteInitiated(clients, memberIds, voteData) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_VOTE_INITIATED, {
        voteId: voteData.voteId,
        groupId: voteData.groupId,
        initiatedBy: voteData.initiatorUsername || voteData.initiatedBy,
        totalMembers: voteData.totalMembers,
        approvalThreshold: voteData.approvalThreshold,
        expiresAt: voteData.expiresAt,
        createdAt: new Date().toISOString()
    }, voteData.groupId);
}

/**
 * Notify group members of new vote cast
 */
function notifyVoteCast(clients, memberIds, voteData) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_VOTE_CAST, {
        voteId: voteData.voteId,
        groupId: voteData.groupId,
        votedBy: voteData.votedBy,
        vote: voteData.vote,
        currentStats: {
            approvalCount: voteData.approvalCount,
            rejectionCount: voteData.rejectionCount,
            totalVoted: voteData.totalVoted,
            approvalPercentage: voteData.approvalPercentage
        },
        votedAt: new Date().toISOString()
    }, voteData.groupId);
}

/**
 * Notify members if group is deleted (vote passed)
 */
function notifyGroupDeleted(clients, memberIds, groupData) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_DELETED, {
        groupId: groupData.groupId,
        deletedAt: new Date().toISOString(),
        reason: 'Group deletion vote passed'
    }, groupData.groupId);
}

// ============================================
// CHANNEL BROADCAST FUNCTIONS
// ============================================

/**
 * Notify channel members of new message
 */
function notifyChannelMessage(clients, memberIds, messageData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.CHANNEL_MESSAGE_RECEIVED, {
        messageId: messageData.messageId,
        channelId: messageData.channelId,
        senderId: messageData.senderId,
        senderUsername: messageData.senderUsername,
        content: messageData.content,
        mediaId: messageData.mediaId,
        createdAt: messageData.createdAt || new Date().toISOString()
    }, messageData.channelId);
}

/**
 * Notify channel of message deletion
 */
function notifyChannelMessageDeleted(clients, memberIds, deletionData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.CHANNEL_MESSAGE_DELETED, {
        messageId: deletionData.messageId,
        channelId: deletionData.channelId,
        deletedBy: deletionData.deletedBy,
        deletedAt: deletionData.deletedAt || new Date().toISOString()
    }, deletionData.channelId);
}

/**
 * Notify channel of member promotion
 */
function notifyMemberPromoted(clients, memberIds, promotionData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_PROMOTED, {
        channelId: promotionData.channelId,
        memberId: promotionData.memberId,
        memberUsername: promotionData.memberUsername,
        promotedBy: promotionData.promotedBy,
        newRole: 'admin',
        promotedAt: new Date().toISOString()
    }, promotionData.channelId);
}

/**
 * Notify channel of member demotion
 */
function notifyMemberDemoted(clients, memberIds, demotionData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_DEMOTED, {
        channelId: demotionData.channelId,
        memberId: demotionData.memberId,
        memberUsername: demotionData.memberUsername,
        demotedBy: demotionData.demotedBy,
        newRole: 'member',
        demotedAt: new Date().toISOString()
    }, demotionData.channelId);
}

/**
 * Notify channel of new member joined
 */
function notifyMemberJoined(clients, memberIds, joinData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_JOINED, {
        channelId: joinData.channelId,
        memberId: joinData.memberId,
        memberUsername: joinData.memberUsername,
        joinedAt: new Date().toISOString()
    }, joinData.channelId);
}

/**
 * Notify channel of member leaving
 */
function notifyMemberLeft(clients, memberIds, leaveData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_LEFT, {
        channelId: leaveData.channelId,
        memberId: leaveData.memberId,
        memberUsername: leaveData.memberUsername,
        leftAt: new Date().toISOString()
    }, leaveData.channelId);
}

// ============================================
// MEDIA DOWNLOAD BROADCAST FUNCTIONS
// ============================================

/**
 * Notify media owner of download request
 */
function notifyMediaDownloadRequested(clients, ownerId, requestData) {
    return broadcastToUser(clients, ownerId, EVENT_TYPES.MEDIA_DOWNLOAD_REQUESTED, {
        requestId: requestData.requestId,
        mediaId: requestData.mediaId,
        requesterId: requestData.requesterId,
        requesterUsername: requestData.requesterUsername,
        reason: requestData.reason,
        requestedAt: new Date().toISOString()
    });
}

/**
 * Notify requester that download was approved
 */
function notifyMediaDownloadApproved(clients, requesterId, approvalData) {
    return broadcastToUser(clients, requesterId, EVENT_TYPES.MEDIA_DOWNLOAD_APPROVED, {
        requestId: approvalData.requestId,
        mediaId: approvalData.mediaId,
        downloadUrl: approvalData.downloadUrl,
        expiresAt: approvalData.expiresAt,
        approvedAt: new Date().toISOString()
    });
}

/**
 * Notify requester that download was denied
 */
function notifyMediaDownloadDenied(clients, requesterId, denialData) {
    return broadcastToUser(clients, requesterId, EVENT_TYPES.MEDIA_DOWNLOAD_DENIED, {
        requestId: denialData.requestId,
        mediaId: denialData.mediaId,
        reason: denialData.reason,
        deniedAt: new Date().toISOString()
    });
}

// ============================================
// CALL BROADCAST FUNCTIONS
// ============================================

/**
 * Notify recipient of incoming call
 */
function notifyCallIncoming(clients, recipientId, callData) {
    return broadcastToUser(clients, recipientId, EVENT_TYPES.CALL_INCOMING, {
        callId: callData.callId,
        callerId: callData.callerId,
        callerUsername: callData.callerUsername,
        callerProfile: callData.callerProfile,
        callType: callData.callType,
        offer: callData.offer,
        incomingAt: new Date().toISOString()
    });
}

/**
 * Notify caller that call was accepted
 */
function notifyCallAccepted(clients, callerId, callData) {
    return broadcastToUser(clients, callerId, EVENT_TYPES.CALL_ACCEPTED, {
        callId: callData.callId,
        recipientId: callData.recipientId,
        answer: callData.answer,
        acceptedAt: new Date().toISOString()
    });
}

/**
 * Notify caller that call was rejected
 */
function notifyCallRejected(clients, callerId, callData) {
    return broadcastToUser(clients, callerId, EVENT_TYPES.CALL_REJECTED, {
        callId: callData.callId,
        recipientId: callData.recipientId,
        rejectedAt: new Date().toISOString()
    });
}

/**
 * Notify caller that call was missed
 */
function notifyCallMissed(clients, callerId, callData) {
    return broadcastToUser(clients, callerId, EVENT_TYPES.CALL_MISSED, {
        callId: callData.callId,
        recipientId: callData.recipientId,
        missedAt: new Date().toISOString()
    });
}

/**
 * Notify both parties that call ended
 */
function notifyCallEnded(clients, callerId, recipientId, callData) {
    const notificationData = {
        callId: callData.callId,
        duration: callData.duration,
        endedAt: new Date().toISOString()
    };
    
    broadcastToUser(clients, callerId, EVENT_TYPES.CALL_ENDED, notificationData);
    broadcastToUser(clients, recipientId, EVENT_TYPES.CALL_ENDED, notificationData);
    
    return true;
}

// ============================================
// USER STATUS BROADCAST FUNCTIONS
// ============================================

/**
 * Notify contacts that user came online
 */
function notifyUserOnline(clients, userId, userProfile, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_ONLINE, {
        userId: userId,
        username: userProfile.username,
        onlineAt: new Date().toISOString()
    });
}

/**
 * Notify contacts that user went offline
 */
function notifyUserOffline(clients, userId, lastSeen, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_OFFLINE, {
        userId: userId,
        lastSeen: lastSeen,
        offlineAt: new Date().toISOString()
    });
}

/**
 * Notify contacts that user profile was updated
 */
function notifyUserProfileUpdated(clients, userId, profileData, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_PROFILE_UPDATED, {
        userId: userId,
        profile: profileData,
        updatedAt: new Date().toISOString()
    });
}

// ============================================
// EXPORTS
// ============================================

module.exports = {
    EVENT_TYPES,
    broadcastToUser,
    broadcastToUsers,
    broadcastToAll,
    broadcastToChannel,
    broadcastToGroup,
    
    // Friend system
    notifyFriendRequestReceived,
    notifyFriendRequestAccepted,
    notifyFriendRequestRejected,
    notifyFriendRequestCanceled,
    
    // Calls
    notifyCallIncoming,
    notifyCallAccepted,
    notifyCallRejected,
    notifyCallMissed,
    notifyCallEnded,
    
    // Group
    notifyGroupMessage,
    notifyGroupMemberJoined,
    notifyGroupMemberLeft,
    notifyGroupMemberRemoved,
    notifyGroupMemberPromoted,
    
    // Group voting
    notifyGroupVoteInitiated,
    notifyVoteCast,
    notifyGroupDeleted,
    
    // Channel
    notifyChannelMessage,
    notifyChannelMessageDeleted,
    notifyMemberPromoted,
    notifyMemberDemoted,
    notifyMemberJoined,
    notifyMemberLeft,
    
    // Media
    notifyMediaDownloadRequested,
    notifyMediaDownloadApproved,
    notifyMediaDownloadDenied,
    
    // User status
    notifyUserOnline,
    notifyUserOffline,
    notifyUserProfileUpdated
};

