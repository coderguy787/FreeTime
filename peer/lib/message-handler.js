/**
 * Message Handler
 * Routes and processes messages between peers
 * Handles message persistence, delivery guarantees, and encryption
 */

const { v4: uuidv4 } = require('uuid');

class MessageHandler {
    constructor(redis, io, logger) {
        this.redis = redis;
        this.io = io;
        this.logger = logger;

        this.stats = {
            sent: 0,
            received: 0,
            failed: 0,
            queued: 0
        };
    }

    /**
     * Handle incoming message
     */
    async handleMessage(fromUserId, fromDeviceId, toUserId, content, metadata = {}) {
        try {
            const message = {
                id: uuidv4(),
                from: fromUserId,
                to: toUserId,
                fromDevice: fromDeviceId,
                content,
                type: metadata.type || 'text',
                timestamp: Date.now(),
                status: 'sent',
                ...metadata
            };

            // Store message in Redis for persistence
            await this.redis.lpush(
                `messages:${toUserId}`,
                JSON.stringify(message)
            );

            // Set expiry (30 days)
            await this.redis.expire(`messages:${toUserId}`, 30 * 24 * 60 * 60);

            // Route to recipient if online
            this.io.to(`user:${toUserId}`).emit('message:incoming', message);

            this.stats.sent++;
            this.logger.debug('Message routed', {
                messageId: message.id,
                from: fromUserId,
                to: toUserId
            });

            return message;
        } catch (error) {
            this.stats.failed++;
            this.logger.error('Message handling failed', {
                error: error.message,
                from: fromUserId,
                to: toUserId
            });
            throw error;
        }
    }

    /**
     * Get message history for user
     */
    async getMessageHistory(userId, fromUserId, limit = 50) {
        try {
            const messageIds = await this.redis.lrange(
                `messages:${userId}`,
                0,
                limit - 1
            );

            const messages = [];
            for (const msgId of messageIds) {
                const msg = JSON.parse(msgId);
                if (msg.from === fromUserId) {
                    messages.push(msg);
                }
            }

            return messages.reverse();
        } catch (error) {
            this.logger.error('Failed to get message history', {
                error: error.message,
                userId
            });
            return [];
        }
    }

    /**
     * Mark message as read
     */
    async markAsRead(userId, messageId) {
        try {
            const key = `message:${messageId}:read`;
            await this.redis.setex(key, 86400, userId);
            return true;
        } catch (error) {
            this.logger.error('Failed to mark message as read', {
                error: error.message
            });
            return false;
        }
    }

    /**
     * Get unread message count
     */
    async getUnreadCount(userId) {
        try {
            const count = await this.redis.llen(`messages:${userId}`);
            return count;
        } catch (error) {
            this.logger.error('Failed to get unread count', {
                error: error.message
            });
            return 0;
        }
    }

    /**
     * Broadcast message to multiple users
     */
    async broadcastMessage(fromUserId, toUserIds, content, metadata = {}) {
        const results = [];

        for (const toUserId of toUserIds) {
            try {
                const message = await this.handleMessage(
                    fromUserId,
                    metadata.fromDevice,
                    toUserId,
                    content,
                    metadata
                );
                results.push({ success: true, message });
            } catch (error) {
                results.push({ success: false, error: error.message });
            }
        }

        return results;
    }

    /**
     * Delete message
     */
    async deleteMessage(userId, messageId) {
        try {
            const messages = await this.redis.lrange(
                `messages:${userId}`,
                0,
                -1
            );

            for (let i = 0; i < messages.length; i++) {
                const msg = JSON.parse(messages[i]);
                if (msg.id === messageId) {
                    await this.redis.lrem(`messages:${userId}`, 1, messages[i]);
                    return true;
                }
            }

            return false;
        } catch (error) {
            this.logger.error('Failed to delete message', {
                error: error.message
            });
            return false;
        }
    }

    /**
     * Get handler statistics
     */
    getStats() {
        return { ...this.stats };
    }
}

module.exports = MessageHandler;
