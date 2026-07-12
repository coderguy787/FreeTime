// Redis Rate Limiting Module
// High-performance rate limiting to replace database queries

const Redis = require('ioredis');
const crypto = require('crypto');

class RedisRateLimiter {
    constructor(options = {}) {
        this.redis = new Redis({
            host: options.redisHost || process.env.REDIS_HOST || 'localhost',
            port: options.redisPort || process.env.REDIS_PORT || 6379,
            password: options.redisPassword || process.env.REDIS_PASSWORD,
            maxRetriesPerRequest: 3,
            retryDelayOnFailover: 100,
            lazyConnect: true,
            keepAlive: 30000,
            family: 4,
            keyPrefix: options.keyPrefix || 'freetime:',
            db: options.redisDb || 0
        });

        this.defaultWindowMs = options.windowMs || 7776000000; // 90 days
        this.maxRequests = options.maxRequests || 1;
        
        this.redis.on('connect', () => console.log('🔗 Redis connected for rate limiting'));
        this.redis.on('error', (err) => console.error('❌ Redis rate limiter error:', err.message));
        this.redis.on('close', () => console.log('🔌 Redis connection closed'));
    }

    async checkIPRateLimit(ip, windowMs = null, maxRequests = null) {
        const key = `rate_limit:ip:${this.hashIP(ip)}`;
        const window = windowMs || this.defaultWindowMs;
        const max = maxRequests || this.maxRequests;
        
        try {
            const pipeline = this.redis.pipeline();
            pipeline.incr(key);
            pipeline.expire(key, Math.ceil(window / 1000));
            pipeline.get(key);
            
            const results = await pipeline.exec();
            const currentCount = results[2][1];
            
            return {
                allowed: currentCount <= max,
                count: currentCount,
                remaining: Math.max(0, max - currentCount),
                resetTime: Date.now() + window,
                key: key
            };
        } catch (error) {
            console.error('Redis IP rate limit check failed:', error);
            return { allowed: true, count: 0, remaining: max, resetTime: Date.now() + window, error: error.message };
        }
    }

    async checkDeviceRateLimit(deviceId, windowMs = null, maxRequests = null) {
        if (!deviceId) return { allowed: true, count: 0, remaining: 1 };
        
        const key = `rate_limit:device:${this.hashDevice(deviceId)}`;
        const window = windowMs || this.defaultWindowMs;
        const max = maxRequests || this.maxRequests;
        
        try {
            const pipeline = this.redis.pipeline();
            pipeline.incr(key);
            pipeline.expire(key, Math.ceil(window / 1000));
            pipeline.get(key);
            
            const results = await pipeline.exec();
            const currentCount = results[2][1];
            
            return {
                allowed: currentCount <= max,
                count: currentCount,
                remaining: Math.max(0, max - currentCount),
                resetTime: Date.now() + window,
                key: key
            };
        } catch (error) {
            console.error('Redis device rate limit check failed:', error);
            return { allowed: true, count: 0, remaining: max, resetTime: Date.now() + window, error: error.message };
        }
    }

    async checkCombinedRateLimit(ip, deviceId, windowMs = null, maxRequests = null) {
        const window = windowMs || this.defaultWindowMs;
        const max = maxRequests || this.maxRequests;
        
        try {
            const pipeline = this.redis.pipeline();
            
            const ipKey = `rate_limit:ip:${this.hashIP(ip)}`;
            pipeline.incr(ipKey);
            pipeline.expire(ipKey, Math.ceil(window / 1000));
            pipeline.get(ipKey);
            
            let deviceKey = null;
            if (deviceId) {
                deviceKey = `rate_limit:device:${this.hashDevice(deviceId)}`;
                pipeline.incr(deviceKey);
                pipeline.expire(deviceKey, Math.ceil(window / 1000));
                pipeline.get(deviceKey);
            }
            
            const results = await pipeline.exec();
            const ipCount = results[2][1];
            const deviceCount = deviceId ? results[5][1] : 0;
            const maxCount = Math.max(ipCount, deviceCount);
            
            return {
                allowed: maxCount <= max,
                ipCount: ipCount,
                deviceCount: deviceCount,
                count: maxCount,
                remaining: Math.max(0, max - maxCount),
                resetTime: Date.now() + window,
                ipKey: ipKey,
                deviceKey: deviceKey
            };
        } catch (error) {
            console.error('Redis combined rate limit check failed:', error);
            return { allowed: true, count: 0, ipCount: 0, deviceCount: 0, remaining: max, resetTime: Date.now() + window, error: error.message };
        }
    }

    async trackSuspiciousActivity(identifier, reason, ttl = 3600) {
        const key = `suspicious:${this.hashIP(identifier)}`;
        
        try {
            const pipeline = this.redis.pipeline();
            pipeline.sadd(key, reason);
            pipeline.expire(key, ttl);
            pipeline.smembers(key);
            
            const results = await pipeline.exec();
            const activities = results[2][1];
            
            return { tracked: true, activities: activities, score: activities.length, key: key };
        } catch (error) {
            console.error('Redis suspicious activity tracking failed:', error);
            return { tracked: false, activities: [], score: 0, error: error.message };
        }
    }

    async isBlocked(identifier) {
        const key = `blocked:${this.hashIP(identifier)}`;
        
        try {
            const isBlocked = await this.redis.exists(key);
            return { blocked: isBlocked === 1, key: key };
        } catch (error) {
            console.error('Redis block check failed:', error);
            return { blocked: false, error: error.message };
        }
    }

    async blockIdentifier(identifier, duration = 86400, reason = 'Rate limit exceeded') {
        const key = `blocked:${this.hashIP(identifier)}`;
        
        try {
            await this.redis.setex(key, duration, JSON.stringify({
                reason: reason,
                blockedAt: Date.now(),
                duration: duration
            }));
            
            return { blocked: true, key: key, duration: duration, reason: reason };
        } catch (error) {
            console.error('Redis block operation failed:', error);
            return { blocked: false, error: error.message };
        }
    }

    hashIP(ip) {
        return crypto.createHash('sha256')
            .update(ip + 'freetime-salt')
            .digest('hex')
            .substring(0, 16);
    }

    hashDevice(deviceId) {
        return crypto.createHash('sha256')
            .update(deviceId + 'freetime-device-salt')
            .digest('hex')
            .substring(0, 16);
    }

    async getStats() {
        try {
            const info = await this.redis.info('memory');
            const keyspace = await this.redis.info('keyspace');
            
            return { memory: info, keyspace: keyspace, connected: this.redis.status === 'ready' };
        } catch (error) {
            return { error: error.message, connected: false };
        }
    }

    async disconnect() {
        try {
            await this.redis.quit();
            console.log('🔌 Redis rate limiter disconnected');
        } catch (error) {
            console.error('Error disconnecting Redis:', error);
        }
    }
}

module.exports = RedisRateLimiter;
