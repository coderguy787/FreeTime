/**
 * Redis Connection Manager with Circuit Breaker & Connection Pool
 * Handles resilience, retries, connection pooling, and failure detection
 */

const redis = require('redis');
const logger = require('./logger');

class CircuitBreaker {
    constructor(options = {}) {
        this.failureThreshold = options.failureThreshold || 5;
        this.resetTimeout = options.resetTimeout || 30000; // 30 seconds
        this.state = 'CLOSED'; // CLOSED, OPEN, HALF_OPEN
        this.failureCount = 0;
        this.lastFailureTime = null;
        this.successCount = 0;
        this.successThreshold = options.successThreshold || 3;
    }

    recordSuccess() {
        this.failureCount = 0;
        if (this.state === 'HALF_OPEN') {
            this.successCount++;
            if (this.successCount >= this.successThreshold) {
                this.state = 'CLOSED';
                this.successCount = 0;
                logger.info('Circuit breaker: CLOSED (recovered)');
            }
        }
    }

    recordFailure() {
        this.failureCount++;
        this.lastFailureTime = Date.now();
        
        if (this.failureCount >= this.failureThreshold && this.state === 'CLOSED') {
            this.state = 'OPEN';
            logger.warn('Circuit breaker: OPEN (failures detected)', { 
                failureCount: this.failureCount 
            });
            
            setTimeout(() => {
                this.state = 'HALF_OPEN';
                this.failureCount = 0;
                this.successCount = 0;
                logger.info('Circuit breaker: HALF_OPEN (attempting recovery)');
            }, this.resetTimeout);
        }
    }

    canExecute() {
        return this.state !== 'OPEN';
    }

    getState() {
        return {
            state: this.state,
            failureCount: this.failureCount,
            lastFailureTime: this.lastFailureTime
        };
    }
}

class RedisConnectionManager {
    constructor(config = {}) {
        this.config = {
            host: config.host || process.env.REDIS_HOST || 'localhost',
            port: config.port || process.env.REDIS_PORT || 6379,
            password: config.password || process.env.REDIS_PASSWORD,
            db: config.db || 0,
            maxRetries: config.maxRetries || 3,
            retryDelayMs: config.retryDelayMs || 100,
            maxBackoffMs: config.maxBackoffMs || 3000,
            connectTimeoutMs: config.connectTimeoutMs || 5000,
            commandTimeoutMs: config.commandTimeoutMs || 5000,
            ...config
        };

        this.circuitBreaker = new CircuitBreaker({
            failureThreshold: config.circuitBreakerThreshold || 5,
            resetTimeout: config.circuitBreakerResetMs || 30000
        });

        this.clients = []; // Connection pool
        this.primaryClient = null;
        this.isShuttingDown = false;
    }

    /**
     * Create individual Redis client with error handling
     */
    createClient() {
        const client = redis.createClient({
            host: this.config.host,
            port: this.config.port,
            password: this.config.password,
            db: this.config.db,
            retry_strategy: (options) => {
                if (options.error && options.error.code === 'ECONNREFUSED') {
                    return new Error('Redis refused connection');
                }
                if (options.total_retry_time > this.config.connectTimeoutMs) {
                    return new Error('Redis retry time exhausted');
                }
                if (options.attempt > this.config.maxRetries) {
                    return undefined;
                }

                // Exponential backoff: 100ms → 200ms → 400ms → ...
                const delay = Math.min(
                    this.config.retryDelayMs * Math.pow(2, options.attempt - 1),
                    this.config.maxBackoffMs
                );
                return delay;
            },
            enable_offline_queue: true,
            socket_connect_timeout: this.config.connectTimeoutMs,
            socket_keepalive: true
        });

        // Event handlers
        client.on('error', (err) => {
            logger.error('Redis client error', { 
                error: err.message,
                code: err.code,
                address: `${this.config.host}:${this.config.port}`
            });
            this.circuitBreaker.recordFailure();
        });

        client.on('connect', () => {
            logger.info('Redis client connected');
            this.circuitBreaker.recordSuccess();
        });

        client.on('ready', () => {
            logger.debug('Redis client ready');
        });

        client.on('reconnecting', () => {
            logger.warn('Redis client reconnecting');
        });

        return client;
    }

    /**
     * Initialize connection pool
     */
    async initialize(poolSize = 1) {
        try {
            for (let i = 0; i < poolSize; i++) {
                const client = this.createClient();
                this.clients.push(client);
                
                // Verify connection
                await new Promise((resolve, reject) => {
                    client.ping((err, reply) => {
                        if (err) {
                            logger.error(`Pool client ${i} ping failed`, { error: err.message });
                            reject(err);
                        } else {
                            logger.debug(`Pool client ${i} verified`, { ping: reply });
                            resolve();
                        }
                    });
                });
            }

            this.primaryClient = this.clients[0];
            logger.info('Redis connection pool initialized', { 
                poolSize: this.clients.length,
                primaryClient: true 
            });

            return true;
        } catch (err) {
            logger.error('Failed to initialize Redis connection pool', { 
                error: err.message,
                config: `${this.config.host}:${this.config.port}`
            });
            
            // Clean up partial pool
            this.clients.forEach(client => {
                try { client.quit(); } catch (e) {}
            });
            this.clients = [];
            
            throw err;
        }
    }

    /**
     * Execute command with circuit breaker and retry logic
     */
    async executeCommand(command, args = [], options = {}) {
        const maxRetries = options.maxRetries || this.config.maxRetries;
        const timeoutMs = options.timeoutMs || this.config.commandTimeoutMs;

        if (!this.circuitBreaker.canExecute()) {
            const state = this.circuitBreaker.getState();
            logger.warn('Circuit breaker OPEN - rejecting request', state);
            throw new Error(`Circuit breaker is ${state.state}`);
        }

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                const client = this.getClient();
                
                // Execute with timeout
                const result = await Promise.race([
                    new Promise((resolve, reject) => {
                        client[command](...args, (err, reply) => {
                            if (err) reject(err);
                            else resolve(reply);
                        });
                    }),
                    new Promise((_, reject) =>
                        setTimeout(() => reject(new Error(`Redis ${command} timeout`)), timeoutMs)
                    )
                ]);

                this.circuitBreaker.recordSuccess();
                return result;

            } catch (err) {
                this.circuitBreaker.recordFailure();

                if (attempt === maxRetries) {
                    logger.error(`Redis ${command} failed after ${maxRetries} retries`, {
                        error: err.message,
                        command,
                        args: args.length > 0 ? `${args.length} args` : 'no args'
                    });
                    throw err;
                }

                // Exponential backoff before retry
                const backoffMs = Math.min(
                    100 * Math.pow(2, attempt - 1),
                    this.config.maxBackoffMs
                );
                await new Promise(resolve => setTimeout(resolve, backoffMs));
                logger.debug(`Retrying Redis ${command}`, { attempt: attempt + 1, backoffMs });
            }
        }
    }

    /**
     * Get client from pool (round-robin)
     */
    getClient() {
        if (this.clients.length === 0) {
            throw new Error('No Redis clients available in pool');
        }
        if (this.clients.length === 1) {
            return this.clients[0];
        }
        // Round-robin selection
        const client = this.clients[Math.floor(Math.random() * this.clients.length)];
        return client;
    }

    /**
     * Convenience methods for common operations
     */
    async get(key) {
        return this.executeCommand('get', [key]);
    }

    async set(key, value, expirySeconds = null) {
        const args = [key, value];
        if (expirySeconds) {
            args.push('EX', expirySeconds);
        }
        return this.executeCommand('set', args);
    }

    async del(key) {
        return this.executeCommand('del', [key]);
    }

    async exists(key) {
        return this.executeCommand('exists', [key]);
    }

    async incr(key) {
        return this.executeCommand('incr', [key]);
    }

    async lpush(key, value) {
        return this.executeCommand('lpush', [key, value]);
    }

    async lpop(key) {
        return this.executeCommand('lpop', [key]);
    }

    async rpush(key, value) {
        return this.executeCommand('rpush', [key, value]);
    }

    async llen(key) {
        return this.executeCommand('llen', [key]);
    }

    async hset(key, field, value) {
        return this.executeCommand('hset', [key, field, value]);
    }

    async hget(key, field) {
        return this.executeCommand('hget', [key, field]);
    }

    async hgetall(key) {
        return this.executeCommand('hgetall', [key]);
    }

    async expire(key, seconds) {
        return this.executeCommand('expire', [key, seconds]);
    }

    /**
     * Health check
     */
    async healthCheck() {
        try {
            const result = await this.executeCommand('ping', []);
            return {
                healthy: result === 'PONG',
                message: 'Redis healthy',
                circuitBreakerState: this.circuitBreaker.getState(),
                poolSize: this.clients.length
            };
        } catch (err) {
            return {
                healthy: false,
                error: err.message,
                circuitBreakerState: this.circuitBreaker.getState(),
                poolSize: this.clients.length
            };
        }
    }

    /**
     * Graceful shutdown
     */
    async shutdown() {
        if (this.isShuttingDown) return;
        this.isShuttingDown = true;

        logger.info('Shutting down Redis connection pool...');
        
        for (const client of this.clients) {
            try {
                await new Promise((resolve) => {
                    client.quit(() => resolve());
                });
            } catch (err) {
                logger.warn('Error closing Redis client', { error: err.message });
                try { client.end(true); } catch (e) {}
            }
        }

        this.clients = [];
        this.primaryClient = null;
        logger.info('Redis connection pool shutdown complete');
    }

    /**
     * Get statistics
     */
    getStats() {
        return {
            poolSize: this.clients.length,
            circuitBreakerState: this.circuitBreaker.getState(),
            primaryClientActive: !!this.primaryClient,
            isShuttingDown: this.isShuttingDown
        };
    }
}

module.exports = RedisConnectionManager;
