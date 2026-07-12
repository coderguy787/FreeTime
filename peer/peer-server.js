#!/usr/bin/env node

/**
 * FreeTime Peer Server
 * 
 * Handles distributed messaging, calls, and user verification
 * Capacity: 80,000 concurrent users per peer
 * Architecture: Node.js cluster with multi-core support + Redis session backend
 * 
 * Features:
 * - Multi-worker clustering
 * - WebSocket connection pooling (Socket.io + Redis adapter)
 * - Distributed session management via Redis
 * - Master server synchronization for account verification
 * - Call signaling and message routing
 * - 2FA security checks via master server
 * - Real-time metrics and health monitoring
 */

require('dotenv').config({ path: './config/.env' });

const cluster = require('cluster');
const os = require('os');
const express = require('express');
const socketIo = require('socket.io');
const { createAdapter } = require('@socket.io/redis-adapter');
const redis = require('redis');
const axios = require('axios');
const jwt = require('jsonwebtoken');
const helmet = require('helmet');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const winston = require('winston');
const { v4: uuidv4 } = require('uuid');
const RedisConnectionManager = require('./redis-connection');

// ==================== CONFIGURATION ====================

const config = {
    NODE_ENV: process.env.NODE_ENV || 'development',
    PEER_PORT: parseInt(process.env.PEER_PORT) || 9090,
    PEER_NAME: process.env.PEER_NAME || `peer-${os.hostname()}`,
    PEER_REGION: process.env.PEER_REGION || 'unknown',
    MASTER_URL: process.env.MASTER_SERVER_URL || 'https://example.com',
    MASTER_PORT: parseInt(process.env.MASTER_SERVER_PORT) || 80,
    MASTER_API_KEY: process.env.MASTER_API_KEY,
    MAX_CONNECTIONS: parseInt(process.env.MAX_CONNECTIONS) || 80000,
    WORKER_PROCESSES: parseInt(process.env.WORKER_PROCESSES) || 0,
    JWT_SECRET: process.env.JWT_SECRET || 'default-secret-change-me',
    REDIS_HOST: process.env.REDIS_HOST || '127.0.0.1',
    REDIS_PORT: parseInt(process.env.REDIS_PORT) || 6379,
    REDIS_DB: parseInt(process.env.REDIS_DB) || 1,
    SESSION_TTL: parseInt(process.env.SESSION_TTL) || 86400,
    LOG_LEVEL: process.env.LOG_LEVEL || 'info'
};

// ==================== CONFIGURATION VALIDATION ====================

// Validate critical configuration before startup
if (!config.MASTER_API_KEY || config.MASTER_API_KEY === 'your-unique-peer-api-key-here-32-chars') {
    console.error('\n[FATAL] MASTER_API_KEY is not set or still has placeholder value');
    console.error('        Set MASTER_API_KEY in config/.env with a unique 32+ character string');
    process.exit(1);
}

if (!config.JWT_SECRET || config.JWT_SECRET === 'default-secret-change-me') {
    console.error('\n[FATAL] JWT_SECRET is not set or still has unsafe default value');
    console.error('        Set JWT_SECRET in config/.env with a unique random string');
    process.exit(1);
}

if (config.MASTER_PORT !== 443 && config.MASTER_URL.startsWith('https://')) {
    console.warn('\n[WARN] Potential protocol mismatch: URL uses HTTPS but port is not 443');
    console.warn(`        URL: ${config.MASTER_URL}:${config.MASTER_PORT}`);
    console.warn('        This may cause connection failures');
}

// ==================== LOGGING ====================

const logger = winston.createLogger({
    level: config.LOG_LEVEL,
    format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.errors({ stack: true }),
        winston.format.json()
    ),
    defaultMeta: { service: config.PEER_NAME, pid: process.pid },
    transports: [
        new winston.transports.File({ 
            filename: 'logs/error.log', 
            level: 'error' 
        }),
        new winston.transports.File({ 
            filename: 'logs/combined.log' 
        })
    ]
});

if (config.NODE_ENV !== 'production') {
    logger.add(new winston.transports.Console({
        format: winston.format.combine(
            winston.format.colorize(),
            winston.format.simple()
        )
    }));
}

// ==================== MASTER CLUSTERING ====================

const numWorkers = config.WORKER_PROCESSES === 0 
    ? os.cpus().length 
    : config.WORKER_PROCESSES;

const connectionsPerWorker = Math.floor(config.MAX_CONNECTIONS / numWorkers);

if (cluster.isMaster) {
    console.log(`
╔════════════════════════════════════════════════════╗
║    FreeTime Peer Server - Starting Master          ║
║    Region: ${config.PEER_REGION}${' '.repeat(23 - config.PEER_REGION.length)}║
║    Max Connections: ${String(config.MAX_CONNECTIONS).padEnd(30)}║
║    Workers: ${String(numWorkers).padEnd(41)}║
║    Per-Worker Limit: ${String(connectionsPerWorker).padEnd(25)}║
╚════════════════════════════════════════════════════╝
    `);
    
    logger.info('Master process started', {
        pid: process.pid,
        workers: numWorkers,
        maxConnections: config.MAX_CONNECTIONS,
        connectionsPerWorker
    });

    // Fork workers
    for (let i = 0; i < numWorkers; i++) {
        const worker = cluster.fork();
        logger.info(`Worker ${worker.id} forked`, { workerId: worker.id, pid: worker.process.pid });
    }

    // Handle worker crashes
    cluster.on('exit', (worker, code, signal) => {
        logger.error(`Worker ${worker.id} died`, {
            workerId: worker.id,
            code,
            signal,
            restart: 'immediate'
        });
        
        // Respawn worker
        const newWorker = cluster.fork();
        logger.info(`Worker respawned`, { newWorkerId: newWorker.id });
    });

    // Graceful shutdown
    process.on('SIGTERM', () => {
        logger.info('Master received SIGTERM - shutting down gracefully');
        for (const id in cluster.workers) {
            cluster.workers[id].kill();
        }
        process.exit(0);
    });

} else {
    // ==================== WORKER PROCESS ====================
    
    const app = express();
    const server = require('http').createServer(app);
    const io = socketIo(server, {
        cors: { origin: '*' },
        maxHttpBufferSize: 1024 * 1024,
        transports: ['websocket', 'polling']
    });

    // ==================== REDIS CLIENTS ====================

    const redisManager = new RedisConnectionManager({
        host: config.REDIS_HOST,
        port: config.REDIS_PORT,
        db: config.REDIS_DB,
        maxRetries: 3,
        retryDelayMs: 100,
        maxBackoffMs: 3000,
        connectTimeoutMs: 5000,
        commandTimeoutMs: 5000,
        circuitBreakerThreshold: 5,
        circuitBreakerResetMs: 30000
    });

    // Keep pub/sub clients separate for Socket.IO adapter
    const pubClient = redis.createClient({
        host: config.REDIS_HOST,
        port: config.REDIS_PORT,
        db: config.REDIS_DB
    });

    const subClient = redis.createClient({
        host: config.REDIS_HOST,
        port: config.REDIS_PORT,
        db: config.REDIS_DB
    });

    // Initialize Redis connection manager on startup
    redisManager.initialize(1).then(() => {
        logger.info('Redis connection manager initialized successfully');
    }).catch(err => {
        logger.error('[FATAL] Failed to initialize Redis', { error: err.message });
        if (config.NODE_ENV === 'production') {
            setTimeout(() => process.exit(1), 5000);
        }
    });

    // Pub/sub client error handlers
    pubClient.on('error', (err) => {
        logger.error('Redis pub client error', { 
            error: err.message,
            host: config.REDIS_HOST,
            port: config.REDIS_PORT
        });
    });

    subClient.on('error', (err) => {
        logger.error('Redis sub client error', { 
            error: err.message,
            host: config.REDIS_HOST,
            port: config.REDIS_PORT
        });
    });

    pubClient.on('ready', () => logger.info('Redis pub client connected'));
    subClient.on('ready', () => logger.info('Redis sub client connected'));

    // ==================== MIDDLEWARE ====================

    app.use(helmet());
    app.use(compression());
    app.use(express.json({ limit: '10mb' }));
    app.use(express.urlencoded({ limit: '10mb', extended: true }));

    const limiter = rateLimit({
        windowMs: 1 * 60 * 1000,
        max: 100,
        message: 'Too many requests from this IP'
    });
    app.use('/api/', limiter);

    // ==================== SOCKET.IO WITH REDIS ADAPTER ====================

    io.adapter(createAdapter(pubClient, subClient));

    const connectionManager = new Map();
    const messageQueue = [];
    let activeConnections = 0;

    // ==================== MASTER SERVER VERIFICATION ====================

    const masterConnector = {
        async verifyToken(token) {
            try {
                const response = await axios.get(
                    `${config.MASTER_URL}:${config.MASTER_PORT}/api/verify-token`,
                    {
                        headers: { 'Authorization': `Bearer ${token}` },
                        timeout: 10000
                    }
                );
                return response.data;
            } catch (error) {
                logger.error('Master verification failed', {
                    error: error.message,
                    token: token.substring(0, 20) + '...'
                });
                return null;
            }
        },

        async reportMetrics(metrics) {
            try {
                await axios.post(
                    `${config.MASTER_URL}:${config.MASTER_PORT}/api/peer/metrics`,
                    {
                        peerId: config.PEER_NAME,
                        ...metrics,
                        timestamp: new Date()
                    },
                    {
                        headers: { 'X-Peer-Token': config.MASTER_API_KEY },
                        timeout: 5000
                    }
                );
            } catch (error) {
                logger.warn('Could not report metrics', { error: error.message });
            }
        },

        async verify2FA(userId, totpCode, deviceId) {
            try {
                const response = await axios.post(
                    `${config.MASTER_URL}:${config.MASTER_PORT}/api/verify-peer-2fa`,
                    { userId, totpCode, deviceId },
                    {
                        headers: { 'X-Peer-Token': config.MASTER_API_KEY },
                        timeout: 10000
                    }
                );
                return response.data;
            } catch (error) {
                logger.error('2FA verification failed', { error: error.message });
                return null;
            }
        }
    };

    // ==================== SOCKET.IO EVENT HANDLERS ====================

    const CallHandler = require('./lib/call-handler');
    const callHandler = new CallHandler(redisClient, io, masterConnector, logger);

    io.on('connection', (socket) => {
        const connectionId = uuidv4();
        activeConnections++;

        logger.debug('Socket connection', {
            socketId: socket.id,
            connectionId,
            totalConnections: activeConnections
        });

        // Store connection metadata
        const connection = {
            id: connectionId,
            socketId: socket.id,
            userId: null,
            deviceId: null,
            token: null,
            createdAt: Date.now(),
            lastActivity: Date.now()
        };

        connectionManager.set(socket.id, connection);

        // ==================== AUTHENTICATION ====================

        socket.on('authenticate', async (token, deviceId, callback) => {
            try {
                // Verify token with master server
                const verified = await masterConnector.verifyToken(token);

                if (!verified || !verified.user) {
                    logger.warn('Authentication failed', { socketId: socket.id });
                    return callback({ success: false, error: 'Invalid token' });
                }

                connection.userId = verified.user.userId;
                connection.deviceId = deviceId;
                connection.token = token;
                connection.lastActivity = Date.now();

                // Store in Redis with TTL
                await redisManager.set(
                    `session:${socket.id}`,
                    JSON.stringify(connection),
                    config.SESSION_TTL
                );

                socket.join(`user:${verified.user.userId}`);
                socket.join(`device:${deviceId}`);

                logger.info('User authenticated', {
                    socketId: socket.id,
                    userId: verified.user.userId,
                    deviceId
                });

                callback({ success: true, user: verified.user });
            } catch (error) {
                logger.error('Authentication error', { error: error.message });
                callback({ success: false, error: error.message });
            }
        });

        // ==================== MESSAGING ====================

        socket.on('send_message', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }

                const message = {
                    id: uuidv4(),
                    from: connection.userId,
                    to: data.recipientId,
                    content: data.content,
                    type: data.type || 'text',
                    timestamp: Date.now(),
                    deviceId: connection.deviceId
                };

                // Store in Redis for delivery guarantee
                await redisManager.lpush(
                    `message_queue:${data.recipientId}`,
                    JSON.stringify(message)
                );

                // Route to recipient if online
                io.to(`user:${data.recipientId}`).emit('receive_message', message);

                logger.debug('Message routed', {
                    messageId: message.id,
                    from: connection.userId,
                    to: data.recipientId
                });

                callback({ success: true, messageId: message.id });
            } catch (error) {
                logger.error('Message send error', { error: error.message });
                callback({ success: false, error: error.message });
            }
        });

        // ==================== CALL SIGNALING (NEW IMPLEMENTATION) ====================

        socket.on('call:initiate', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }
                const call = await callHandler.initiateCall(connection.userId, data.recipientId, data.callType, data.offer);
                callback({ success: true, callId: call.id });
            } catch (error) {
                logger.error('Call initiation error', { error: error.message, stack: error.stack });
                callback({ success: false, error: 'Failed to initiate call' });
            }
        });

        socket.on('call:answer', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }
                const call = await callHandler.answerCall(data.callId, connection.userId, data.answer, data.totpCode, connection.deviceId);
                callback({ success: true, call });
            } catch (error) {
                logger.error('Call answer error', { error: error.message, stack: error.stack });
                callback({ success: false, error: error.message || 'Failed to answer call' });
            }
        });

        socket.on('call:ice-candidate', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }
                await callHandler.addICECandidate(data.callId, connection.userId, data.candidate);
                callback({ success: true });
            } catch (error) {
                logger.error('ICE candidate error', { error: error.message, stack: error.stack });
                callback({ success: false, error: 'Failed to add ICE candidate' });
            }
        });

        socket.on('call:reject', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }
                await callHandler.rejectCall(data.callId, connection.userId, data.reason);
                callback({ success: true });
            } catch (error) {
                logger.error('Call rejection error', { error: error.message, stack: error.stack });
                callback({ success: false, error: 'Failed to reject call' });
            }
        });

        socket.on('call:end', async (data, callback) => {
            try {
                if (!connection.userId) {
                    return callback({ success: false, error: 'Not authenticated' });
                }
                await callHandler.endCall(data.callId);
                callback({ success: true });
            } catch (error) {
                logger.error('Call end error', { error: error.message, stack: error.stack });
                callback({ success: false, error: 'Failed to end call' });
            }
        });

        // ==================== CONNECTIVITY ====================

        socket.on('disconnect', async () => {
            activeConnections--;

            logger.debug('Socket disconnected', {
                socketId: socket.id,
                userId: connection.userId,
                totalConnections: activeConnections
            });

            connectionManager.delete(socket.id);
            await redisManager.del(`session:${socket.id}`);
        });

        socket.on('error', (error) => {
            logger.error('Socket error', {
                socketId: socket.id,
                error: error.message
            });
        });
    });

    // ==================== REST ENDPOINTS ====================

    app.get('/health', async (req, res) => {
        try {
            const redisHealth = await redisManager.healthCheck();
            res.json({
                status: redisHealth.healthy ? 'healthy' : 'degraded',
                peer: config.PEER_NAME,
                activeConnections,
                maxCapacity: connectionsPerWorker,
                utilizationPercent: Math.round((activeConnections / connectionsPerWorker) * 100),
                uptime: process.uptime(),
                memory: process.memoryUsage(),
                redis: {
                    healthy: redisHealth.healthy,
                    message: redisHealth.message,
                    error: redisHealth.error || undefined,
                    circuitBreakerState: redisHealth.circuitBreakerState,
                    poolSize: redisHealth.poolSize
                }
            });
        } catch (err) {
            res.status(503).json({
                status: 'unhealthy',
                error: err.message,
                peer: config.PEER_NAME
            });
        }
    });

    app.get('/api/diagnostics/redis', async (req, res) => {
        try {
            const redisHealth = await redisManager.healthCheck();
            const stats = redisManager.getStats();
            
            res.json({
                timestamp: new Date().toISOString(),
                redis: {
                    connected: redisHealth.healthy,
                    connectionString: `${config.REDIS_HOST}:${config.REDIS_PORT}/db${config.REDIS_DB}`,
                    health: redisHealth,
                    stats: stats
                },
                message: redisHealth.healthy 
                    ? 'Redis connection pool is operational'
                    : 'Redis connection pool degraded or unavailable'
            });
        } catch (err) {
            res.status(503).json({
                timestamp: new Date().toISOString(),
                error: err.message,
                connectionString: `${config.REDIS_HOST}:${config.REDIS_PORT}/db${config.REDIS_DB}`
            });
        }
    });

    app.get('/api/peer/stats', (req, res) => {
        res.json({
            peerId: config.PEER_NAME,
            region: config.PEER_REGION,
            activeConnections,
            maxCapacity: config.MAX_CONNECTIONS,
            utilizationPercent: Math.round((activeConnections / config.MAX_CONNECTIONS) * 100),
            messageQueueLength: messageQueue.length,
            memoryUsage: process.memoryUsage(),
            uptime: process.uptime(),
            timestamp: Date.now()
        });
    });

    app.post('/api/peer/route-message', express.json(), (req, res) => {
        try {
            const { recipientId, message } = req.body;
            
            io.to(`user:${recipientId}`).emit('receive_message', message);
            res.json({ status: 'routed' });
        } catch (error) {
            res.status(500).json({ error: error.message });
        }
    });

    // ==================== METRICS REPORTING ====================

    setInterval(async () => {
        const metrics = {
            activeConnections,
            utilizationPercent: Math.round((activeConnections / connectionsPerWorker) * 100),
            memoryUsage: process.memoryUsage(),
            uptime: process.uptime(),
            workerId: cluster.worker.id,
            pid: process.pid
        };

        await masterConnector.reportMetrics(metrics);
    }, 10000);

    // ==================== GRACEFUL SHUTDOWN ====================

    process.on('SIGTERM', async () => {
        logger.info('Worker received SIGTERM - shutting down', { workerId: cluster.worker.id });
        
        io.emit('server_shutdown', { message: 'Peer server shutting down' });
        
        // Gracefully shutdown Redis
        await redisManager.shutdown();
        
        server.close(() => {
            process.exit(0);
        });

        setTimeout(() => {
            logger.error('Graceful shutdown timeout - force exiting');
            process.exit(1);
        }, 30000);
    });

    // ==================== START SERVER ====================

    server.listen(config.PEER_PORT + cluster.worker.id, () => {
        logger.info(`Worker listening`, {
            workerId: cluster.worker.id,
            port: config.PEER_PORT + cluster.worker.id,
            pid: process.pid
        });
        console.log(`[Worker ${cluster.worker.id}] Listening on port ${config.PEER_PORT + cluster.worker.id}`);
    });
}

module.exports = { config, logger };
