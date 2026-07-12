/**
 * Session Manager
 * Manages user sessions across distributed peers via Redis
 * Provides session persistence and distribution
 */

const { v4: uuidv4 } = require('uuid');

class SessionManager {
    constructor(redisClient, options = {}) {
        this.redis = redisClient;
        this.sessionTTL = options.sessionTTL || 86400; // 24 hours
        this.sessionPrefix = 'session:';
        this.userSessionPrefix = 'user_sessions:';
    }

    /**
     * Create a new session
     */
    async createSession(userId, deviceId, token, metadata = {}) {
        const sessionId = uuidv4();
        
        const sessionData = {
            id: sessionId,
            userId,
            deviceId,
            token: token.substring(0, 20) + '...', // Don't store full token
            createdAt: Date.now(),
            lastActivity: Date.now(),
            ...metadata
        };

        // Store session
        await this.redis.setex(
            `${this.sessionPrefix}${sessionId}`,
            this.sessionTTL,
            JSON.stringify(sessionData)
        );

        // Track user sessions
        await this.redis.lpush(
            `${this.userSessionPrefix}${userId}`,
            sessionId
        );

        return sessionData;
    }

    /**
     * Get session by ID
     */
    async getSession(sessionId) {
        const data = await this.redis.get(`${this.sessionPrefix}${sessionId}`);
        return data ? JSON.parse(data) : null;
    }

    /**
     * Update session activity
     */
    async updateSessionActivity(sessionId) {
        const session = await this.getSession(sessionId);
        if (!session) return null;

        session.lastActivity = Date.now();
        
        await this.redis.setex(
            `${this.sessionPrefix}${sessionId}`,
            this.sessionTTL,
            JSON.stringify(session)
        );

        return session;
    }

    /**
     * Get all sessions for a user
     */
    async getUserSessions(userId) {
        const sessionIds = await this.redis.lrange(
            `${this.userSessionPrefix}${userId}`,
            0,
            -1
        );

        const sessions = [];
        for (const sessionId of sessionIds) {
            const session = await this.getSession(sessionId);
            if (session) {
                sessions.push(session);
            }
        }

        return sessions;
    }

    /**
     * Invalidate a session
     */
    async invalidateSession(sessionId) {
        const session = await this.getSession(sessionId);
        if (!session) return false;

        await this.redis.del(`${this.sessionPrefix}${sessionId}`);

        // Remove from user sessions list
        await this.redis.lrem(
            `${this.userSessionPrefix}${session.userId}`,
            0,
            sessionId
        );

        return true;
    }

    /**
     * Invalidate all sessions for a user
     */
    async invalidateUserSessions(userId) {
        const sessionIds = await this.redis.lrange(
            `${this.userSessionPrefix}${userId}`,
            0,
            -1
        );

        for (const sessionId of sessionIds) {
            await this.invalidateSession(sessionId);
        }

        await this.redis.del(`${this.userSessionPrefix}${userId}`);
        return sessionIds.length;
    }

    /**
     * Check if device is registered for user
     */
    async isDeviceRegistered(userId, deviceId) {
        const sessions = await this.getUserSessions(userId);
        return sessions.some(s => s.deviceId === deviceId);
    }

    /**
     * Get session statistics
     */
    async getStats() {
        const keys = await this.redis.keys(`${this.sessionPrefix}*`);
        const totalSessions = keys.length;

        const userKeys = await this.redis.keys(`${this.userSessionPrefix}*`);
        const totalUsers = userKeys.length;

        return {
            totalSessions,
            totalUsers,
            averageSessionsPerUser: totalUsers > 0 ? totalSessions / totalUsers : 0
        };
    }
}

module.exports = SessionManager;
