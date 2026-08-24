const { v4: uuidv4 } = require('uuid');

// user sessions stored in redis
class SessionManager {
    constructor(redisClient, options = {}) {
        this.redis = redisClient;
        this.sessionTTL = options.sessionTTL || 86400;
        this.sessionPrefix = 'session:';
        this.userSessionPrefix = 'user_sessions:';
    }

    async createSession(userId, deviceId, token, metadata = {}) {
        const sessionId = uuidv4();

        const sessionData = {
            id: sessionId,
            userId,
            deviceId,
            token: token.substring(0, 20) + '...',
            createdAt: Date.now(),
            lastActivity: Date.now(),
            ...metadata
        };

        await this.redis.setex(
            `${this.sessionPrefix}${sessionId}`,
            this.sessionTTL,
            JSON.stringify(sessionData)
        );

        await this.redis.lpush(
            `${this.userSessionPrefix}${userId}`,
            sessionId
        );

        return sessionData;
    }

    async getSession(sessionId) {
        const data = await this.redis.get(`${this.sessionPrefix}${sessionId}`);
        return data ? JSON.parse(data) : null;
    }

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

    async invalidateSession(sessionId) {
        const session = await this.getSession(sessionId);
        if (!session) return false;

        await this.redis.del(`${this.sessionPrefix}${sessionId}`);

        await this.redis.lrem(
            `${this.userSessionPrefix}${session.userId}`,
            0,
            sessionId
        );

        return true;
    }

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

    async isDeviceRegistered(userId, deviceId) {
        const sessions = await this.getUserSessions(userId);
        return sessions.some(s => s.deviceId === deviceId);
    }

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
