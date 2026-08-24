const { v4: uuidv4 } = require('uuid');

const EVENT_TYPES = {
    FRIEND_REQUEST_RECEIVED: 'friend.request.received',
    FRIEND_REQUEST_ACCEPTED: 'friend.request.accepted',
    FRIEND_REQUEST_REJECTED: 'friend.request.rejected',
    FRIEND_REQUEST_CANCELED: 'friend.request.canceled',

    GROUP_MESSAGE_RECEIVED: 'group.message.received',
    GROUP_MEMBER_JOINED: 'group.member.joined',
    GROUP_MEMBER_LEFT: 'group.member.left',
    GROUP_MEMBER_PROMOTED: 'group.member.promoted',
    GROUP_MEMBER_REMOVED: 'group.member.removed',

    GROUP_VOTE_INITIATED: 'group.vote.initiated',
    GROUP_VOTE_CAST: 'group.vote.cast',
    GROUP_VOTE_UPDATED: 'group.vote.updated',
    GROUP_DELETED: 'group.deleted',

    CHANNEL_MESSAGE_RECEIVED: 'channel.message.received',
    CHANNEL_MESSAGE_DELETED: 'channel.message.deleted',
    MEMBER_PROMOTED: 'channel.member.promoted',
    MEMBER_DEMOTED: 'channel.member.demoted',
    MEMBER_JOINED: 'channel.member.joined',
    MEMBER_LEFT: 'channel.member.left',

    MEDIA_DOWNLOAD_REQUESTED: 'media.download.requested',
    MEDIA_DOWNLOAD_APPROVED: 'media.download.approved',
    MEDIA_DOWNLOAD_DENIED: 'media.download.denied',


    USER_ONLINE: 'user.online',
    USER_OFFLINE: 'user.offline',
    USER_PROFILE_UPDATED: 'user.profile.updated'
};

function emitToSocketIO(target, eventType, data) {
    if (!global.socketIoServer) {
        console.warn(`[Socket.IO] Server not initialized - cannot broadcast ${eventType} to ${target}`);
        return false;
    }

    try {
        global.socketIoServer.to(target).emit(eventType, data);
        console.log(`[ Socket.IO] Emitted ${eventType} to ${target}`);
        return true;
    } catch (err) {
        console.error(`[ Socket.IO] Broadcast error to ${target} for ${eventType}:`, err.message);
        return false;
    }
}

function broadcastToUser(clients, userId, eventType, data) {
    // deliver over socket.io and raw ws
    emitToSocketIO(`user:${userId}`, eventType, data);

    const message = {
        id: uuidv4(),
        type: eventType,
        timestamp: new Date().toISOString(),
        data: data
    };

    if (!clients) return true;

    const userClients = Array.from(clients.values()).filter(
        client => (client.userId === userId || (client.user && client.user.id === userId)) && client.ws.readyState === 1
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

function broadcastToUsers(clients, userIds, eventType, data) {
    let successCount = 0;

    userIds.forEach(userId => {
        if (broadcastToUser(clients, userId, eventType, data)) {
            successCount++;
        }
    });

    return successCount;
}

function broadcastToRoom(clients, userIds, roomName, eventType, data) {
    emitToSocketIO(roomName, eventType, data);

    return broadcastToUsers(clients, userIds, eventType, data);
}

function broadcastToAll(clients, eventType, data) {
    if (global.socketIoServer) {
        global.socketIoServer.emit(eventType, data);
    }

    const message = {
        id: uuidv4(),
        type: eventType,
        timestamp: new Date().toISOString(),
        data: data
    };

    if (!clients) return 0;

    let count = 0;
    clients.forEach(client => {
        if (client.ws.readyState === 1) {
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

function broadcastToChannel(clients, memberIds, eventType, data, channelId) {
    if (channelId) {
        return broadcastToRoom(clients, memberIds, `channel:${channelId}`, eventType, data);
    }
    return broadcastToUsers(clients, memberIds, eventType, data);
}

function broadcastToGroup(clients, memberIds, eventType, data, groupId) {
    if (groupId) {
        return broadcastToRoom(clients, memberIds, `group:${groupId}`, eventType, data);
    }
    return broadcastToUsers(clients, memberIds, eventType, data);
}

function notifyFriendRequestReceived(clients, recipientId, friendRequest) {
    return broadcastToUser(clients, recipientId, EVENT_TYPES.FRIEND_REQUEST_RECEIVED, {
        requestId: friendRequest.id,
        senderId: friendRequest.senderId,
        senderUsername: friendRequest.senderUsername,
        senderProfile: friendRequest.senderProfile,
        createdAt: friendRequest.createdAt
    });
}

function notifyFriendRequestAccepted(clients, senderId, friendData) {
    return broadcastToUser(clients, senderId, EVENT_TYPES.FRIEND_REQUEST_ACCEPTED, {
        userId: friendData.friendId,
        username: friendData.friendUsername,
        profile: friendData.friendProfile,
        acceptedAt: new Date().toISOString()
    });
}

function notifyFriendRequestRejected(clients, senderId, friendId) {
    return broadcastToUser(clients, senderId, EVENT_TYPES.FRIEND_REQUEST_REJECTED, {
        userId: friendId,
        rejectedAt: new Date().toISOString()
    });
}

function notifyFriendRequestCanceled(clients, recipientId, senderId) {
    return broadcastToUser(clients, recipientId, EVENT_TYPES.FRIEND_REQUEST_CANCELED, {
        senderId: senderId,
        canceledAt: new Date().toISOString()
    });
}

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

function notifyGroupMemberRemoved(clients, memberIds, removeData, groupId) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_MEMBER_REMOVED, {
        groupId: removeData.groupId || groupId,
        userId: removeData.userId,
        username: removeData.username,
        displayName: removeData.displayName || removeData.username,
        actor: removeData.actor,
        action: 'removed',
        timestamp: Date.now()
    }, groupId);
}

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

function notifyGroupDeleted(clients, memberIds, groupData) {
    return broadcastToGroup(clients, memberIds, EVENT_TYPES.GROUP_DELETED, {
        groupId: groupData.groupId,
        deletedAt: new Date().toISOString(),
        reason: 'Group deletion vote passed'
    }, groupData.groupId);
}

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

function notifyChannelMessageDeleted(clients, memberIds, deletionData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.CHANNEL_MESSAGE_DELETED, {
        messageId: deletionData.messageId,
        channelId: deletionData.channelId,
        deletedBy: deletionData.deletedBy,
        deletedAt: deletionData.deletedAt || new Date().toISOString()
    }, deletionData.channelId);
}

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

function notifyMemberJoined(clients, memberIds, joinData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_JOINED, {
        channelId: joinData.channelId,
        memberId: joinData.memberId,
        memberUsername: joinData.memberUsername,
        joinedAt: new Date().toISOString()
    }, joinData.channelId);
}

function notifyMemberLeft(clients, memberIds, leaveData) {
    return broadcastToChannel(clients, memberIds, EVENT_TYPES.MEMBER_LEFT, {
        channelId: leaveData.channelId,
        memberId: leaveData.memberId,
        memberUsername: leaveData.memberUsername,
        leftAt: new Date().toISOString()
    }, leaveData.channelId);
}

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

function notifyMediaDownloadApproved(clients, requesterId, approvalData) {
    return broadcastToUser(clients, requesterId, EVENT_TYPES.MEDIA_DOWNLOAD_APPROVED, {
        requestId: approvalData.requestId,
        mediaId: approvalData.mediaId,
        downloadUrl: approvalData.downloadUrl,
        expiresAt: approvalData.expiresAt,
        approvedAt: new Date().toISOString()
    });
}

function notifyMediaDownloadDenied(clients, requesterId, denialData) {
    return broadcastToUser(clients, requesterId, EVENT_TYPES.MEDIA_DOWNLOAD_DENIED, {
        requestId: denialData.requestId,
        mediaId: denialData.mediaId,
        reason: denialData.reason,
        deniedAt: new Date().toISOString()
    });
}

function notifyUserOnline(clients, userId, userProfile, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_ONLINE, {
        userId: userId,
        username: userProfile.username,
        onlineAt: new Date().toISOString()
    });
}

function notifyUserOffline(clients, userId, lastSeen, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_OFFLINE, {
        userId: userId,
        lastSeen: lastSeen,
        offlineAt: new Date().toISOString()
    });
}

function notifyUserProfileUpdated(clients, userId, profileData, friendIds) {
    return broadcastToUsers(clients, friendIds, EVENT_TYPES.USER_PROFILE_UPDATED, {
        userId: userId,
        profile: profileData,
        updatedAt: new Date().toISOString()
    });
}

module.exports = {
    EVENT_TYPES,
    broadcastToUser,
    broadcastToUsers,
    broadcastToAll,
    broadcastToChannel,
    broadcastToGroup,

    notifyFriendRequestReceived,
    notifyFriendRequestAccepted,
    notifyFriendRequestRejected,
    notifyFriendRequestCanceled,


    notifyGroupMessage,
    notifyGroupMemberJoined,
    notifyGroupMemberLeft,
    notifyGroupMemberRemoved,
    notifyGroupMemberPromoted,

    notifyGroupVoteInitiated,
    notifyVoteCast,
    notifyGroupDeleted,

    notifyChannelMessage,
    notifyChannelMessageDeleted,
    notifyMemberPromoted,
    notifyMemberDemoted,
    notifyMemberJoined,
    notifyMemberLeft,

    notifyMediaDownloadRequested,
    notifyMediaDownloadApproved,
    notifyMediaDownloadDenied,

    notifyUserOnline,
    notifyUserOffline,
    notifyUserProfileUpdated
};

