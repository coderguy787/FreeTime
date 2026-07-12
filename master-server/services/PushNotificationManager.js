/**
 * PushNotificationManager.js
 * Centralized FCM Push Notification Manager for FreeTime
 * 
 * This module provides a unified interface for sending FCM push notifications
 * throughout the application. It handles token lookup, error handling, logging,
 * and provides consistency across all notification types.
 */

const admin = require('firebase-admin');

class PushNotificationManager {
    constructor(dbConnection, adminInstance) {
        this.db = dbConnection;
        this.admin = adminInstance || require('firebase-admin');
    }

    /**
     * Get FCM token for a user from their user document
     * @param {string} userId - The user ID
     * @returns {Promise<string|null>} - The FCM token or null if not found
     */
    async getFcmToken(userId) {
        try {
            const user = await this.db.collection('users').findOne({ id: userId });
            return user?.fcmToken || null;
        } catch (err) {
            console.error(`[PushNotificationManager] Error getting FCM token for ${userId}:`, err.message);
            return null;
        }
    }

    /**
     * Get ALL FCM tokens for a user (supports multiple devices)
     * @param {string} userId - The user ID
     * @returns {Promise<Array>} - Array of FCM token objects with {fcmToken, deviceName, lastUsedAt}
     */
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

    /**
     * Send notification to all devices of a user
     * @param {string} recipientId - The recipient user ID
     * @param {object} payload - Notification payload with {type, ...data}
     * @returns {Promise<number>} - Number of successful sends
     */
    async sendNotificationToAllDevices(recipientId, payload) {
        const tokens = await this.getAllFcmTokens(recipientId);
        if (tokens.length === 0) {
            // Fallback to checking main users collection if FCMTokens is empty
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
                // Build a friendly notification title/body when possible so the system
                // displays a visible notification even when the app process is not running.
                const title = payload.senderName || payload.title || payload.sender_name || 'FreeTime';
                const body = payload.messageContent || payload.message_preview || payload.body || payload.message || '';

                // Ensure all data fields are strings (FCM requires string values in `data`)
                const dataPayload = {};
                for (const key of Object.keys(payload)) {
                    try {
                        const v = payload[key];
                        dataPayload[key] = (v === undefined || v === null) ? '' : String(v);
                    } catch (e) {
                        dataPayload[key] = '';
                    }
                }

                // Determine the correct channel ID for Android
                let channelId = 'messages';
                if (payload.type === 'incomingCall') {
                    channelId = 'calls';
                } else if (payload.type === 'friendRequest' || payload.type === 'friend_request' || payload.type === 'friendRequestAccepted' || payload.type === 'friend_accepted') {
                    channelId = 'social';
                } else if (payload.type.startsWith('media')) {
                    channelId = 'media';
                }

                // ✅ IMPROVED: Use high-priority data-only messages for ALL notifications on Android.
                // This ensures the background handler (EnhancedFirebaseMessagingService) is always
                // triggered even when the app is closed, allowing for custom WhatsApp-style notifications.
                const isCall = payload.type === 'incomingCall';
                
                const message = {
                    token: tokenDoc.fcmToken,
                    data: dataPayload,
                    android: {
                        priority: 'high',
                        ttl: isCall ? 60000 : 86400, // Short TTL for calls, 24h for others
                        direct_boot_ok: true
                    },
                    apns: {
                        headers: {
                            'apns-priority': isCall ? '10' : '5',
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

                // CRITICAL FOR CALLS: We must NOT include a 'notification' block for calls
                // to ensure the system doesn't show a generic notification and instead
                // lets our app handle the incoming call UI via FullScreenIntent.
                
                // For other messages, we also switch to data-only for maximum reliability
                // and consistency in how notifications are displayed (always via our code).
                // However, we include some hint fields for the OS in case direct_boot is active.
                
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

        // Clean up invalid tokens
        if (failedTokens.length > 0) {
            await this.db.collection('FCMTokens').deleteMany({
                fcmToken: { $in: failedTokens }
            });
            console.log(`[PushNotificationManager] Removed ${failedTokens.length} invalid tokens for ${recipientId}`);
        }

        console.log(`[PushNotificationManager] Notification sent to ${successCount}/${tokens.length} devices for ${recipientId}`);
        return successCount;
    }

    /**
     * Send a message notification to a user (to all their devices)
     * @param {string} recipientId - The recipient user ID
     * @param {object} sender - Sender info { userId, displayName }
     * @param {string} messageContent - The message text
     * @returns {Promise<boolean>} - True if sent to at least one device
     */
    async sendMessageNotification(recipientId, sender, messageContent) {
        const preview = messageContent.length > 100 
            ? messageContent.substring(0, 100) + '...' 
            : messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'message', // Matches Android app expectation
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

    /**
     * Send a friend request notification (to all their devices)
     * @param {string} recipientId - The recipient user ID (who receives the request)
     * @param {object} sender - Sender info { userId, username }
     * @param {string} requestId - The friend request ID
     * @returns {Promise<boolean>} - True if sent to at least one device
     */
    async sendFriendRequestNotification(recipientId, sender, requestId) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friendRequest',
            senderId: sender.userId,
            sender_id: sender.userId,
            senderName: sender.username,
            sender_name: sender.username,
            fromUserId: sender.userId,
            fromUsername: sender.username,
            requestId: requestId,
            request_id: requestId
        });

        return result > 0;
    }

    /**
     * Send a friend acceptance notification (to all their devices)
     * @param {string} recipientId - The recipient user ID (the one who sent the original request)
     * @param {object} acceptor - Acceptor info { userId, username, avatar }
     * @returns {Promise<boolean>} - True if sent to at least one device
     */
    async sendFriendAcceptanceNotification(recipientId, acceptor) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friendRequestAccepted',
            friendId: acceptor.userId,
            friend_id: acceptor.userId,
            friendName: acceptor.username,
            friend_name: acceptor.username,
            avatar: acceptor.avatar || '',  // CRITICAL FIX: Include avatar for consistent UI
            avatar_url: acceptor.avatar || '',
            userId: acceptor.userId,
            username: acceptor.username
        });

        return result > 0;
    }

    /**
     * Send a media download request notification
     */
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

    /**
     * Send a channel message notification
     */
    async sendChannelMessageNotification(recipientId, sender, channelInfo) {
        const preview = channelInfo.messageContent.length > 100
            ? channelInfo.messageContent.substring(0, 100) + '...'
            : channelInfo.messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'channel_message', // Matches Android app expectation
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

    /**
     * Send an incoming call notification (to all their devices)
     */
    async sendIncomingCallNotification(recipientId, caller, callInfo) {
        const callerAvatar = caller.avatar || caller.profileImageUrl || caller.profile?.profileImageUrl || null;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'incomingCall',
            callId: callInfo.callId,
            call_id: callInfo.callId,
            callerId: caller.userId,
            caller_id: caller.userId,
            callerName: caller.username,
            caller_name: caller.username,
            callType: callInfo.callType,
            call_type: callInfo.callType,
            callerAvatar: callerAvatar,
            caller_avatar: callerAvatar,
            callerAvatarUrl: callerAvatar,
            caller_avatar_url: callerAvatar,
            offerSdp: callInfo.offerSdp || callInfo.sdp || ''  // ✅ CRITICAL FIX: Include SDP offer
        });

        return result > 0;
    }

    /**
     * Clear invalid FCM token for a user
     * @param {string} userId - The user ID
     * @returns {Promise<boolean>} - True if cleared successfully
     */
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

    /**
     * Send a friend request accepted notification
     */
    async sendFriendAcceptedNotification(recipientId, sender) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'friend_accepted',
            userId: sender.userId,
            username: sender.username,
            acceptedAt: new Date().toISOString()
        });

        return result > 0;
    }

    /**
     * Send a group invite notification (to all their devices)
     */
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

    /**
     * Send a group voting notification
     */
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

    /**
     * Send a media approved notification
     */
    async sendMediaApprovedNotification(recipientId, mediaInfo) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'media_approved',
            mediaId: mediaInfo.mediaId,
            media_id: mediaInfo.mediaId,
            mediaName: mediaInfo.mediaName || 'Media',
            media_name: mediaInfo.mediaName || 'Media',
            downloadUrl: mediaInfo.downloadUrl || '',
            download_url: mediaInfo.downloadUrl || '',
            mediaKey: mediaInfo.mediaKey || '', // CRITICAL for E2E decryption
            media_key: mediaInfo.mediaKey || ''
        });

        return result > 0;
    }

    /**
     * Send a media denied notification
     */
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

    /**
     * Send a missed call notification
     */
    async sendMissedCallNotification(recipientId, caller) {
        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'missedCall',
            callerId: caller.userId,
            caller_id: caller.userId,
            callerName: caller.username,
            caller_name: caller.username
        });

        return result > 0;
    }

    /**
     * Send a group message notification (to all their devices)
     */
    async sendGroupMessageNotification(recipientId, sender, groupInfo, messageContent) {
        const preview = messageContent.length > 100 
            ? messageContent.substring(0, 100) + '...' 
            : messageContent;

        const result = await this.sendNotificationToAllDevices(recipientId, {
            type: 'groupMessage', // Matches Android app expectation
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

    /**
     * Register or update FCM token for a user
     * @param {string} userId - The user ID
     * @param {string} fcmToken - The FCM token
     * @returns {Promise<boolean>} - True if registered successfully
     */
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
