const admin = require('firebase-admin');

class PushNotificationManager {
    constructor(dbConnection, adminInstance) {
        this.db = dbConnection;
        this.admin = adminInstance || require('firebase-admin');
    }

    async getFcmToken(userId) {
        try {
            const user = await this.db.collection('users').findOne({ id: userId });
            return user?.fcmToken || null;
        } catch (err) {
            console.error(`[PushNotificationManager] Error getting FCM token for ${userId}:`, err.message);
            return null;
        }
    }

    async getAllFcmTokens(userId) {
        try {
            const tokens = await this.db.collection('FCMTokens')
                .find({ userId })
                .project({ fcmToken: 1, deviceName: 1, lastUsedAt: 1 })
                .toArray();
            return tokens || [];
        } catch (err) {
            console.error(`[PushNotificationManager] Error getting all FCM tokens for ${userId}:`, err.message);
            return [];
        }
    }

    async sendNotificationToAllDevices(recipientId, payload) {
        const tokens = await this.getAllFcmTokens(recipientId);
        if (tokens.length === 0) {
            const mainToken = await this.getFcmToken(recipientId);
            if (!mainToken) {
                console.warn(`[PushNotificationManager] No FCM tokens for user ${recipientId}`);
                return 0;
            }
            tokens.push({ fcmToken: mainToken });
        }

        let successCount = 0;
        const failedTokens = [];

        for (const tokenDoc of tokens) {
            try {
                const title = payload.senderName || payload.title || payload.sender_name || 'FreeTime';
                const body = payload.messageContent || payload.message_preview || payload.body || payload.message || '';

                // fcm requires string values
                const dataPayload = {};
                for (const key of Object.keys(payload)) {
                    try {
                        const v = payload[key];
                        dataPayload[key] = (v === undefined || v === null) ? '' : String(v);
                    } catch (e) {
                        dataPayload[key] = '';
                    }
                }

                let channelId = 'messages';
                if (payload.type === 'friendRequest' || payload.type === 'friend_request' || payload.type === 'friendRequestAccepted' || payload.type === 'friend_accepted') {
                    channelId = 'social';
                } else if (payload.type.startsWith('media')) {
                    channelId = 'media';
                }

                const message = {
                    token: tokenDoc.fcmToken,
                    data: dataPayload,
                    android: {
                        priority: 'high',
                        ttl: 86400,
                        direct_boot_ok: true
                    },
                    apns: {
                        headers: {
                            'apns-priority': '5',
                            'apns-push-type': 'alert'
                        },
                        payload: {
                            aps: {
                                alert: {
                                    title: title,
                                    body: body
                                },
                                sound: 'default',
                                badge: 1,
                                'content-available': 1
                            }
                        }
                    }
                };

                await this.admin.messaging().send(message);
                successCount++;
            } catch (err) {
                if (err.code === 'messaging/registration-token-not-registered' ||
                    err.code === 'messaging/mismatched-credential' ||
                    err.code === 'messaging/invalid-argument') {
                    failedTokens.push(tokenDoc.fcmToken);
                } else {
                    console.error(`[PushNotificationManager] Error sending to token for ${recipientId}:`, err.message);
                }
            }
        }

        if (failedTokens.length > 0) {
            await this.db.collection('FCMTokens').deleteMany({
                fcmToken: { $in: failedTokens }
            });
            console.log(`[PushNotificationManager] Removed ${failedTokens.length} invalid tokens for ${recipientId}`);
        }

        console.log(`[PushNotificationManager] Notification sent to ${successCount}/${tokens.length} devices for ${recipientId}`);
        return successCount;
    }

    async sendMessageNotification(recipientId, sender, messageContent) {
        const preview = messageContent.length > 100
            ? messageContent.substring(0, 100) + '...'
            : messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'message',
            senderId: sender.userId,
            sender_id: sender.userId,
            senderName: sender.displayName || 'New Message',
            sender_name: sender.displayName || 'New Message',
            messageContent: preview,
            message_preview: preview,
            chatId: sender.userId
        });

        return result > 0;
    }

    async sendFriendRequestNotification(recipientId, sender, requestId) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friendRequest',
            senderId: sender.userId,
            sender_id: sender.userId,
            senderName: sender.username,
            sender_name: sender.username,
            senderAvatar: sender.avatar || '',
            avatar: sender.avatar || '',
            fromUserId: sender.userId,
            fromUsername: sender.username,
            requestId: requestId,
            request_id: requestId
        });

        return result > 0;
    }

    async sendFriendAcceptanceNotification(recipientId, acceptor) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friendRequestAccepted',
            friendId: acceptor.userId,
            friend_id: acceptor.userId,
            friendName: acceptor.username,
            friend_name: acceptor.username,
            avatar: acceptor.avatar || '',
            avatar_url: acceptor.avatar || '',
            userId: acceptor.userId,
            username: acceptor.username
        });

        return result > 0;
    }

    async sendMediaDownloadRequestNotification(recipientId, requester, mediaInfo) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'mediaDownloadRequest',
            requesterId: requester.userId,
            requester_id: requester.userId,
            requesterName: requester.username,
            requester_name: requester.username,
            mediaId: mediaInfo.mediaId,
            media_id: mediaInfo.mediaId,
            mediaName: mediaInfo.mediaName || 'media file',
            media_name: mediaInfo.mediaName || 'media file',
            reason: mediaInfo.reason || ''
        });

        return result > 0;
    }

    async sendChannelMessageNotification(recipientId, sender, channelInfo) {
        const preview = channelInfo.messageContent.length > 100
            ? channelInfo.messageContent.substring(0, 100) + '...'
            : channelInfo.messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'channel_message',
            channelId: channelInfo.channelId,
            channel_id: channelInfo.channelId,
            channelName: channelInfo.channelName,
            channel_name: channelInfo.channelName,
            senderId: sender.userId,
            sender_id: sender.userId,
            senderName: sender.username,
            sender_name: sender.username,
            messageContent: preview,
            message_preview: preview,
            chatId: channelInfo.channelId
        });

        return result > 0;
    }

    
    async clearFcmToken(userId) {
        try {
            await this.db.collection('users').updateOne(
                { id: userId },
                { $unset: { fcmToken: '', fcmTokenUpdatedAt: '' } }
            );
            console.log(`[PushNotificationManager] Cleared invalid FCM token for ${userId}`);
            return true;
        } catch (err) {
            console.error(`[PushNotificationManager] Error clearing FCM token for ${userId}:`, err.message);
            return false;
        }
    }

    async sendFriendAcceptedNotification(recipientId, sender) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friend_accepted',
            userId: sender.userId,
            username: sender.username,
            acceptedAt: new Date().toISOString()
        });

        return result > 0;
    }

    async sendGroupInviteNotification(recipientId, inviter, groupInfo, inviteId) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'groupInvite',
            groupId: groupInfo.groupId,
            group_id: groupInfo.groupId,
            groupName: groupInfo.groupName,
            group_name: groupInfo.groupName,
            message: inviter.username + ' invited you to join ' + groupInfo.groupName,
            event_message: inviter.username + ' invited you to join ' + groupInfo.groupName,
            inviterId: inviter.userId,
            inviter_id: inviter.userId,
            inviterName: inviter.username,
            inviter_name: inviter.username,
            inviteId: inviteId || '',
            invite_id: inviteId || ''
        });

        return result > 0;
    }

    async sendGroupVotingNotification(recipientId, groupInfo, eventMessage) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'group_voting',
            groupId: groupInfo.groupId,
            group_id: groupInfo.groupId,
            groupName: groupInfo.groupName,
            group_name: groupInfo.groupName,
            eventMessage: eventMessage,
            event_message: eventMessage
        });

        return result > 0;
    }

    async sendMediaApprovedNotification(recipientId, mediaInfo) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'media_approved',
            mediaId: mediaInfo.mediaId,
            media_id: mediaInfo.mediaId,
            mediaName: mediaInfo.mediaName || 'Media',
            media_name: mediaInfo.mediaName || 'Media',
            downloadUrl: mediaInfo.downloadUrl || '',
            download_url: mediaInfo.downloadUrl || '',
            mediaKey: mediaInfo.mediaKey || '',
            media_key: mediaInfo.mediaKey || ''
        });

        return result > 0;
    }

    async sendMediaDeniedNotification(recipientId, mediaInfo) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'media_denied',
            mediaId: mediaInfo.mediaId,
            media_id: mediaInfo.mediaId,
            mediaName: mediaInfo.mediaName || 'Media',
            media_name: mediaInfo.mediaName || 'Media',
            reason: mediaInfo.reason || 'No reason provided'
        });

        return result > 0;
    }

    
    async sendGroupMessageNotification(recipientId, sender, groupInfo, messageContent) {
        const preview = messageContent.length > 100
            ? messageContent.substring(0, 100) + '...'
            : messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'groupMessage',
            groupId: groupInfo.groupId || groupInfo.id,
            group_id: groupInfo.groupId || groupInfo.id,
            groupName: groupInfo.groupName || groupInfo.name || 'Group',
            group_name: groupInfo.groupName || groupInfo.name || 'Group',
            senderId: sender.userId || sender.id,
            sender_id: sender.userId || sender.id,
            senderName: sender.displayName || sender.username || 'Someone',
            sender_name: sender.displayName || sender.username || 'Someone',
            messageContent: preview,
            message_preview: preview,
            message: preview,
            content: preview,
            senderAvatar: sender.avatar || null
        });

        return result > 0;
    }

    async registerFcmToken(userId, fcmToken) {
        try {
            await this.db.collection('users').updateOne(
                { id: userId },
                {
                    $set: {
                        fcmToken: fcmToken,
                        fcmTokenUpdatedAt: new Date()
                    }
                }
            );
            console.log(`[PushNotificationManager] FCM token registered/updated for ${userId}`);
            return true;
        } catch (err) {
            console.error(`[PushNotificationManager] Error registering FCM token for ${userId}:`, err.message);
            return false;
        }
    }
}

module.exports = PushNotificationManager;
