

const WebSocket = require('ws');
const https = require('https');
const express = require('express');
const path = require('path');
const fs = require('fs');
const { MongoClient } = require('mongodb');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

require('dotenv').config({ path: path.join(__dirname, '../config/.env') });

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';
const JWT_SECRET = process.env.JWT_SECRET;
const WEBSOCKET_PORT = process.env.PORT_WEBSOCKET || 8080;
const WEBSOCKET_TIMEOUT = process.env.WEBSOCKET_TIMEOUT || 30000;

const SSL_CERT_PATH = process.env.SSL_CERT || path.join(__dirname, '../certs/fullchain.pem');
const SSL_KEY_PATH = process.env.SSL_KEY || path.join(__dirname, '../certs/privkey.pem');

let sslOptions = null;
try {
    if (fs.existsSync(SSL_CERT_PATH) && fs.existsSync(SSL_KEY_PATH)) {
        sslOptions = {
            cert: fs.readFileSync(SSL_CERT_PATH, 'utf8'),
            key: fs.readFileSync(SSL_KEY_PATH, 'utf8')
        };
        console.log('[OK] WebSocket: SSL certificates loaded (WSS enabled)');
    } else {
        console.warn('[WARNING] WebSocket: SSL certificates not found - running in unencrypted mode (WS)');
    }
} catch (err) {
    console.error('[ERROR] WebSocket: Failed to load SSL certificates:', err.message);
    console.warn('[WARNING] WebSocket: Running in unencrypted mode (WS)');
}

if (!JWT_SECRET) {
    console.error('\u274c CRITICAL: JWT_SECRET not set in .env file!');
    console.error('\u274c Set JWT_SECRET environment variable before running in production');
    process.exit(1);
}

const admin = require('firebase-admin');
let firebaseInitialized = false;
try {
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT || path.join(__dirname, '../config/firebase-service-account.json');
    if (fs.existsSync(serviceAccountPath)) {
        const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount),
            projectId: serviceAccount.project_id
        });
        firebaseInitialized = true;
        console.log('[ FCM] Firebase initialized for WebSocket server');
    } else {
        console.warn('[ FCM] Firebase service account not found — FCM disabled in WebSocket');
    }
} catch (err) {
    console.warn('[ FCM] Firebase initialization failed:', err.message);
}

async function sendFcmToUser(userId, payload) {
    if (!firebaseInitialized) return;
    try {
        const tokens = await dbConnection.collection('FCMTokens').find({ userId }).toArray();
        if (!tokens || tokens.length === 0) return;
        for (const tokenDoc of tokens) {
            try {
                const messagePayload = {
                    token: tokenDoc.fcmToken,
                    data: payload.data || {},
                    android: {
                        priority: 'high',
                        ttl: 86400,
                        notification: {
                            channelId: 'messages',
                            priority: 'high'
                        }
                    }
                };
                if (payload.notification) {
                    messagePayload.notification = payload.notification;
                }
                await admin.messaging().send(messagePayload);
            } catch (err) {
                if (err.code === 'messaging/invalid-registration-token' || err.code === 'messaging/registration-token-not-registered') {
                    await dbConnection.collection('FCMTokens').deleteOne({ _id: tokenDoc._id });
                }
            }
        }
    } catch (err) {
        console.warn(`[FCM] Error sending to user ${userId}: ${err.message}`);
    }
}

const app = express();
app.use(cors());
app.use(express.json());

const server = sslOptions ? https.createServer(sslOptions, app) : require('http').createServer(app);

server.timeout = 30000;
server.keepAliveTimeout = 65000;
server.headersTimeout = 66000;
server.requestTimeout = 30000;

const wss = new WebSocket.Server({
    server,
    path: '/ws',
    verifyClient: verifyClient
});

let dbConnection = null;

async function connectDB() {
    try {
        const client = new MongoClient(MONGODB_URI, {
            maxPoolSize: 20,
            minPoolSize: 2,
            maxIdleTimeMS: 60000,
            socketTimeoutMS: 30000,
            serverSelectionTimeoutMS: 5000,
            retryWrites: true
        });
        await client.connect();
        dbConnection = client.db('freetime');
        console.log('[OK] WebSocket: Connected to MongoDB');
    } catch (err) {
        console.error('[ERROR] WebSocket: MongoDB connection failed:', err);
        process.exit(1);
    }
}

function verifyClient(info) {
    const { req } = info;
    const token = req.headers.authorization?.replace('Bearer ', '') ||
                  (req.url && req.url.includes('token=') &&
                   new URL(req.url, 'http://localhost').searchParams.get('token'));

    if (!token) {
        console.log('[ERROR] WebSocket: No token provided');
        return false;
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        req.user = decoded;
        return true;
    } catch (err) {
        console.log('[ERROR] WebSocket: Invalid token');
        return false;
    }
}

// limit concurrent connections
const MAX_CONNECTIONS = parseInt(process.env.MAX_CONNECTIONS || '35000');
const MAX_QUEUE_SIZE = parseInt(process.env.MAX_QUEUE_SIZE || '1000');
const QUEUE_CHECK_INTERVAL = 5000;
const QUEUE_TIMEOUT_MS = 30000;

global.wsClients = new Map();
const clients = global.wsClients;

global.wsUserMap = new Map();
const connectionQueue = [];
let activeConnections = 0;
let rejectedConnections = 0;
let queuedConnectionsTimeout = 0;

function getConnectionStats() {
    return {
        active: activeConnections,
        max: MAX_CONNECTIONS,
        queued: connectionQueue.length,
        rejected: rejectedConnections,
        percentageUsed: ((activeConnections / MAX_CONNECTIONS) * 100).toFixed(2),
        queueUtilization: ((connectionQueue.length / MAX_QUEUE_SIZE) * 100).toFixed(2)
    };
}

async function processConnectionQueue() {
    const now = Date.now();
    for (let i = connectionQueue.length - 1; i >= 0; i--) {
        const queuedClient = connectionQueue[i];
        if (queuedClient.queuedAt && (now - queuedClient.queuedAt) > QUEUE_TIMEOUT_MS) {
            console.warn(` WebSocket: Connection queue timeout - ${queuedClient.user.username} (waited ${now - queuedClient.queuedAt}ms)`);
            connectionQueue.splice(i, 1);

            try {
                queuedClient.ws.close(1008, 'Queue timeout - connection took too long');
            } catch (e) {
            }
        }
    }

    while (connectionQueue.length > 0 && activeConnections < MAX_CONNECTIONS) {
        const queuedClient = connectionQueue.shift();

        try {
            await queuedClient.connect();
        } catch (err) {
            console.error('[ERROR] WebSocket: Failed to process queued connection:', err);
        }
    }
}

setInterval(processConnectionQueue, QUEUE_CHECK_INTERVAL);

wss.on('connection', async (ws, req) => {
    const user = req.user;
    const clientId = `${user.id || user.username}-${Date.now()}`;

    console.log(` WebSocket: Client connection attempt - ${user.username || user.id}`);

    if (connectionQueue.length >= MAX_QUEUE_SIZE) {
        console.error(`[ERROR] WebSocket: Connection queue full - rejecting ${user.username}`);
        rejectedConnections++;

        ws.send(JSON.stringify({
            type: 'error',
            code: 'SERVER_OVERLOADED',
            message: 'Server at maximum capacity. Please try again in a moment.',
            retryAfter: 30
        }));
        ws.close(1008, 'Server queue full');
        return;
    }

    if (activeConnections >= MAX_CONNECTIONS) {
        console.log(` WebSocket: Connection queued - ${user.username} (Queue size: ${connectionQueue.length + 1}/${MAX_QUEUE_SIZE})`);

        const queueEntry = {
            ws,
            user,
            clientId,
            queuedAt: Date.now(),
            connect: async function() {
                return new Promise((resolve, reject) => {
                    try {
                        handleNewConnection(this.ws, this.user, this.clientId);
                        resolve();
                    } catch (err) {
                        reject(err);
                    }
                });
            }
        };
        connectionQueue.push(queueEntry);

        ws.send(JSON.stringify({
            type: 'connection',
            event: 'queued',
            data: {
                message: 'Server at capacity. Connection queued.',
                queuePosition: connectionQueue.length,
                estimatedWait: `${Math.ceil((connectionQueue.length * 30) / 10)} seconds`,
                serverStats: getConnectionStats()
            }
        }));

        return;
    }

    handleNewConnection(ws, user, clientId);
});

async function handleNewConnection(ws, user, clientId) {
    activeConnections++;

    console.log(`[OK] WebSocket: Client connected - ${user.username || user.id} (Active: ${activeConnections}/${MAX_CONNECTIONS})`);

    clients.set(clientId, {
        ws,
        user,
        clientId,
        connectedAt: new Date(),
        lastPing: new Date(),
        isActive: true,
        userGroupIds: []
    });

    if (user.id) {
        global.wsUserMap.set(user.id, ws);
    }

    await loadUserGroups(clientId, user.id);

    if (!dbConnection) {
        console.error('[ERROR] WebSocket: Database connection unavailable - cannot update user status');
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Database temporarily unavailable. Connection will work for cached messages only.'
        }));
        return;
    }

    try {
        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    isOnline: true,
                    lastLogin: new Date(),
                    updatedAt: new Date(),
                    'connectionInfo.connectedAt': new Date(),
                    'connectionInfo.clientId': clientId,
                    'connectionInfo.connectionType': 'websocket'
                },
                $push: {
                    'connectionHistory': {
                        clientId: clientId,
                        connectedAt: new Date(),
                        disconnectedAt: null,
                        type: 'websocket'
                    }
                }
            }
        );

        console.log(` WebSocket: Online status updated for ${user.username}`);
    } catch (err) {
        console.error('[ERROR] WebSocket: Failed to update online status:', err);
    }

    ws.send(JSON.stringify({
        type: 'connection',
        event: 'connected',
        data: {
            clientId,
            user: { id: user.id, username: user.username, role: user.role },
            timestamp: new Date().toISOString()
        }
    }));

    await logEvent('websocket_connect', `WebSocket connected: ${user.username}`, user.id);

    ws.on('message', async (message) => {
        const client = clients.get(clientId);
        if (client) {
            client.lastMessageReceived = new Date();
            client.messageCount = (client.messageCount || 0) + 1;
        }

        try {
            if (!message || message.length === 0) {
                console.warn('[WARN] WebSocket: Empty message received');
                return;
            }

            let data;
            try {
                data = JSON.parse(message);
            } catch (parseErr) {
                console.error('[ERROR] WebSocket: Invalid JSON received:', parseErr.message);
                ws.send(JSON.stringify({
                    type: 'error',
                    code: 'INVALID_JSON',
                    message: 'Invalid message format'
                }));
                return;
            }

            if (!data.type) {
                console.warn('[WARN] WebSocket: Message missing type field');
                ws.send(JSON.stringify({
                    type: 'error',
                    code: 'MISSING_TYPE',
                    message: 'Message must have a type field'
                }));
                return;
            }

            try {
                await handleMessage(clientId, data, user);
            } catch (handlerErr) {
                console.error('[ERROR] WebSocket: Message handler error:', handlerErr.message);

                try {
                    ws.send(JSON.stringify({
                        type: 'error',
                        code: 'MESSAGE_PROCESSING_FAILED',
                        message: 'Failed to process message',
                        details: process.env.NODE_ENV === 'development' ? handlerErr.message : undefined
                    }));
                } catch (sendErr) {
                    console.error('[ERROR] WebSocket: Failed to send error response:', sendErr.message);
                }

            }
        } catch (err) {
            console.error('[ERROR] WebSocket: Unexpected message handler error:', err);

            try {
                ws.send(JSON.stringify({
                    type: 'error',
                    code: 'INTERNAL_ERROR',
                    message: 'An internal error occurred'
                }));
            } catch (e) {
            }
        }
    });

    ws.on('pong', () => {
        const client = clients.get(clientId);
        if (client) {
            client.lastPing = new Date();
        }
    });

    ws.on('close', async () => {
        console.log(` WebSocket: Client disconnected - ${user.username || user.id}`);

        clients.delete(clientId);
        activeConnections--;

        if (user.id && global.wsUserMap.get(user.id) === ws) {
            global.wsUserMap.delete(user.id);
        }

        if (dbConnection && user.id) {
            try {
                const client = clients.get(clientId) || { connectedAt: new Date() };
                const connectionDuration = new Date() - (client.connectedAt || new Date());

                await dbConnection.collection('users').updateOne(
                    { id: user.id },
                    {
                        $set: {
                            isOnline: false,
                            lastSeen: new Date(),
                            'connectionInfo.disconnectedAt': new Date(),
                            'connectionInfo.connectionDuration': connectionDuration,
                            updatedAt: new Date()
                        },
                        $push: {
                            'connectionHistory.$[elem].disconnectedAt': new Date()
                        }
                    },
                    {
                        arrayFilters: [{ 'elem.clientId': clientId }]
                    }
                );

                console.log(` WebSocket: Offline status updated for ${user.username} (duration: ${Math.floor(connectionDuration / 1000)}s)`);

                await logEvent('websocket_disconnect', `WebSocket disconnected: ${user.username}`, user.id, {
                    clientId: clientId,
                    connectionDuration: connectionDuration,
                    activeConnections: activeConnections,
                    queueLength: connectionQueue.length
                });
            } catch (err) {
                console.error('[ERROR] WebSocket: Failed to update offline status:', err);
            }
        }

        if (connectionQueue.length > 0) {
            console.log(` WebSocket: Processing queue (${connectionQueue.length} waiting, ${activeConnections}/${MAX_CONNECTIONS} active)`);
            processConnectionQueue();
        }

        broadcast({
            type: 'user_status',
            event: 'offline',
            data: {
                userId: user.id,
                username: user.username,
                timestamp: new Date().toISOString(),
                connectionDuration: new Date() - (clients.get(clientId)?.connectedAt || new Date())
            }
        }, clientId);
    });

    ws.on('error', (err) => {
        console.error('[ERROR] WebSocket: Error:', err);
    });
}

async function handleMessage(clientId, data, user) {
    const { type, event, payload } = data;

    switch (type) {
        case 'ping':
            const client = clients.get(clientId);
            if (client) {
                client.ws.send(JSON.stringify({
                    type: 'pong',
                    timestamp: new Date().toISOString()
                }));
            }
            break;

        case 'message':
            await handleChatMessage(clientId, payload, user);
            break;

        case 'groupMessage':
            await handleGroupMessage(clientId, payload, user);
            break;

        case 'typing':
            broadcast({
                type: 'typing',
                event: 'user_typing',
                data: {
                    userId: user.id,
                    username: user.username,
                    isTyping: payload?.isTyping || false,
                    timestamp: new Date().toISOString()
                }
            }, clientId);
            break;

        case 'user_status':
            await handleUserStatus(clientId, payload, user);
            break;

        case 'peer_status':
            await handlePeerStatus(clientId, payload, user);
            break;

        default:
            console.log(` WebSocket: Unknown message type: ${type}`);
    }
}

async function handleChatMessage(clientId, payload, user) {
    try {
        const { to, message, chatId } = payload;

        if (!message || !to) {
            return;
        }

        const messageDoc = {
            id: uuidv4(),
            chatId: chatId || `direct-${user.id}-${to}`,
            from: user.id,
            to: to,
            message: message,
            type: 'text',
            status: 'delivered',
            createdAt: new Date(),
            updatedAt: new Date()
        };

        if (dbConnection) {
            await dbConnection.collection('messages').insertOne(messageDoc);

            await logEvent('message', `Message sent: ${user.username} ${to}`, user.id, {
                messageId: messageDoc.id,
                chatId: messageDoc.chatId
            });
        }

        broadcast({
            type: 'message',
            event: 'new_message',
            data: messageDoc
        }, clientId);

    } catch (err) {
        console.error('[ERROR] WebSocket: Message handling error:', err);
    }
}

async function handleGroupMessage(clientId, payload, user) {
    try {
        const { groupId, message } = payload;

        if (!message || !groupId) {
            return;
        }

        if (dbConnection) {
            const group = await dbConnection.collection('groups').findOne({ groupId });
            if (!group) {
                console.warn(`[WARNING] Group ${groupId} not found`);
                return;
            }

            const isMember = group.members.some(m => m.userId === user.id);
            if (!isMember) {
                console.warn(`[WARNING] User ${user.id} not a member of group ${groupId}`);
                return;
            }

            const groupMessage = {
                messageId: uuidv4(),
                groupId,
                senderId: user.id,
                senderUsername: user.username,
                senderAvatar: user.avatar || null,
                message: message.trim(),
                timestamp: new Date().toISOString()
            };

            await dbConnection.collection('groupMessages').insertOne(groupMessage);

            await dbConnection.collection('groups').updateOne(
                { groupId },
                { $inc: { messageCount: 1 } }
            );

            await logEvent('group_message', `Message sent in group ${groupId}`, user.id, {
                messageId: groupMessage.messageId,
                groupId: groupMessage.groupId
            });

            broadcastToGroup(groupId, {
                type: 'groupMessage',
                event: 'new_group_message',
                data: groupMessage
            });

            try {
                const members = (group.members || [])
                    .filter(m => m && m.userId !== user.id)
                    .map(m => m.userId);
                const groupName = group.name || 'Group';
                const truncatedBody = message.trim().length > 100
                    ? message.trim().substring(0, 97) + '...'
                    : message.trim();

                for (const memberId of members) {
                    sendFcmToUser(memberId, {
                        data: {
                            type: 'groupMessage',
                            groupId: groupId,
                            groupName: groupName,
                            senderId: user.id,
                            senderName: user.username || 'Unknown',
                            senderDisplayName: user.displayName || user.username || 'Unknown',
                            messageContent: truncatedBody,
                            message_preview: truncatedBody,
                            messageId: groupMessage.messageId,
                            timestamp: groupMessage.timestamp,
                            senderAvatar: user.avatar || null,
                            title: `${user.username || 'User'} in ${groupName}`,
                            body: truncatedBody
                        }
                    }).catch(() => {});
                }
            } catch (notifErr) {
                console.warn(`[FCM] Failed to send group message notifications: ${notifErr.message}`);
            }
        }

    } catch (err) {
        console.error('[ERROR] WebSocket: Group message handling error:', err);
    }
}

function broadcastToGroup(groupId, message) {
    const messageStr = JSON.stringify(message);

    for (const [clientId, client] of clients.entries()) {
        if (client.userGroupIds && client.userGroupIds.includes(groupId)) {
            try {
                client.ws.send(messageStr);
            } catch (err) {
                console.warn(`[WARNING] Failed to send message to client ${clientId}`);
            }
        }
    }
}

function setClientGroups(clientId, groupIds) {
    const client = clients.get(clientId);
    if (client) {
        client.userGroupIds = groupIds || [];
    }
}

async function loadUserGroups(clientId, userId) {
    try {
        if (dbConnection) {
            const groups = await dbConnection.collection('groups')
                .find({ 'members.userId': userId })
                .toArray();

            const groupIds = groups.map(g => g.groupId);
            setClientGroups(clientId, groupIds);
        }
    } catch (err) {
        console.warn(`[WARNING] Failed to load user groups for ${userId}:`, err.message);
    }
}

async function handleUserStatus(clientId, payload, user) {
    try {
        const { status, isOnline } = payload;

        if (dbConnection && user.id) {
            await dbConnection.collection('users').updateOne(
                { id: user.id },
                {
                    $set: {
                        status: status || 'active',
                        isOnline: isOnline !== undefined ? isOnline : true,
                        lastSeen: new Date()
                    }
                }
            );
        }

        broadcast({
            type: 'user_status',
            event: 'status_change',
            data: {
                userId: user.id,
                username: user.username,
                status: status || 'active',
                isOnline: isOnline !== undefined ? isOnline : true,
                timestamp: new Date().toISOString()
            }
        }, clientId);

    } catch (err) {
        console.error('[ERROR] WebSocket: Status update error:', err);
    }
}

async function handlePeerStatus(clientId, payload, user) {
    try {
        const { peerId, status, latency } = payload;

        if (dbConnection && peerId) {
            await dbConnection.collection('peers').updateOne(
                { id: peerId },
                {
                    $set: {
                        connected: status === 'online',
                        latency: latency,
                        lastConnected: new Date()
                    }
                }
            );
        }

        broadcast({
            type: 'peer_status',
            event: 'peer_update',
            data: {
                peerId,
                status,
                latency,
                timestamp: new Date().toISOString()
            }
        }, clientId);

    } catch (err) {
        console.error('[ERROR] WebSocket: Peer status error:', err);
    }
}

function broadcast(message, excludeClientId = null) {
    const messageStr = JSON.stringify(message);

    clients.forEach((client, clientId) => {
        if (clientId !== excludeClientId && client.ws.readyState === WebSocket.OPEN) {
            try {
                client.ws.send(messageStr);
            } catch (err) {
                console.error('[ERROR] WebSocket: Broadcast error:', err);
            }
        }
    });
}

function getOnlineUsersCount() {
    return Array.from(clients.values()).length;
}

function getConnectedClients() {
    return Array.from(clients.values()).map(client => ({
        username: client.user.username,
        role: client.user.role,
        connectedAt: client.connectedAt,
        lastPing: client.lastPing
    }));
}

async function logEvent(type, message, userId = null, metadata = {}) {
    const logEntry = {
        id: uuidv4(),
        type,
        message,
        userId,
        timestamp: new Date(),
        metadata
    };

    try {
        if (dbConnection) {
            await dbConnection.collection('logs').insertOne(logEntry);
        }

        const fs = require('fs');
        const logsDir = path.join(__dirname, '../logs');
        if (!fs.existsSync(logsDir)) fs.mkdirSync(logsDir, { recursive: true });

        const logFile = path.join(logsDir, 'websocket-events.log');
        const logText = `[${new Date().toISOString()}] ${type.toUpperCase()}: ${message}\n`;
        fs.appendFileSync(logFile, logText);
    } catch (err) {
        console.error('WebSocket log error:', err);
    }
}

app.get('/health', (req, res) => {
    res.json({
        status: 'online',
        service: 'websocket-server',
        port: WEBSOCKET_PORT,
        connectedClients: getOnlineUsersCount(),
        uptime: process.uptime(),
        connectionStats: getConnectionStats(),
        timestamp: new Date().toISOString()
    });
});

app.get('/stats', (req, res) => {
    res.json({
        connectedClients: getOnlineUsersCount(),
        clients: getConnectedClients(),
        uptime: process.uptime(),
        memory: process.memoryUsage(),
        connections: {
            active: activeConnections,
            maximum: MAX_CONNECTIONS,
            queued: connectionQueue.length,
            rejected: rejectedConnections,
            percentageUsed: ((activeConnections / MAX_CONNECTIONS) * 100).toFixed(2),
            availableSlots: MAX_CONNECTIONS - activeConnections
        },
        queueStats: {
            waitingConnections: connectionQueue.length,
            estimatedWaitTime: `${Math.ceil((connectionQueue.length * 30) / 10)} seconds`,
            queueProcessingEnabled: true,
            lastProcessed: new Date().toISOString()
        },
        timestamp: new Date().toISOString()
    });
});

app.get('/queue-status', (req, res) => {
    res.json({
        queue: {
            size: connectionQueue.length,
            maxCapacity: MAX_CONNECTIONS,
            currentActive: activeConnections,
            percentageUsed: ((activeConnections / MAX_CONNECTIONS) * 100).toFixed(2),
            availableSlots: MAX_CONNECTIONS - activeConnections,
            rejectedCount: rejectedConnections
        },
        clients: {
            connected: clients.size,
            registered: activeConnections
        },
        timing: {
            estimatedProcessingTime: '5 seconds per batch',
            lastQueueCheck: new Date().toISOString(),
            queueCheckInterval: `${QUEUE_CHECK_INTERVAL / 1000} seconds`
        }
    });
});

// clean up dead sockets periodically
setInterval(() => {
    clients.forEach((client, clientId) => {
        if (client.ws.readyState === WebSocket.OPEN) {
            try {
                client.ws.ping();
            } catch (err) {
                console.error('[ERROR] WebSocket: Ping error:', err);
                clients.delete(clientId);
            }
        } else {
            clients.delete(clientId);
        }
    });
}, 30000);

async function startServer() {
    await connectDB();

    server.listen(WEBSOCKET_PORT, '0.0.0.0', () => {
        const protocol = sslOptions ? 'WSS (Secure)' : 'WS (Unencrypted)';
        console.log(`[OK] WebSocket Server running on port ${WEBSOCKET_PORT} (${protocol})`);
        console.log(`[INFO] Protocol: ${protocol}`);
        console.log(`[INFO] Health: https://localhost:${WEBSOCKET_PORT}/health`);
        console.log(`[INFO] Stats: https://localhost:${WEBSOCKET_PORT}/stats`);
    });
}

process.on('SIGTERM', () => {
    console.log('[INFO] WebSocket: SIGTERM received, shutting down gracefully');
    server.close(() => {
        console.log('[OK] WebSocket: Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('[INFO] WebSocket: SIGINT received, shutting down gracefully');
    server.close(() => {
        console.log('[OK] WebSocket: Server closed');
        process.exit(0);
    });
});

if (require.main === module) {
    startServer().catch(err => {
        console.error('[ERROR] WebSocket: Failed to start server:', err);
        process.exit(1);
    });
}

module.exports = { app, server, wss, startServer };
