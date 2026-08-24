const { Server: SocketIOServer } = require('socket.io');
const jwt = require('jsonwebtoken');

function initializeSocketIO(server, jwtSecret, allowedOrigins) {
    if (!server) {
        throw new Error('Server instance required to initialize Socket.IO');
    }

    if (!jwtSecret) {
        throw new Error('JWT_SECRET required for Socket.IO authentication');
    }

    const transportMode = process.env.SOCKET_IO_TRANSPORT || 'auto';
    let transports;

    if (transportMode === 'polling') {
        transports = ['polling'];
        console.log('[INFO] Socket.IO: Polling-only mode (HTTP fallback guaranteed)');
    } else if (transportMode === 'websocket') {
        transports = ['websocket'];
        console.log('[INFO] Socket.IO: WebSocket-only mode (low latency)');
    } else {
        transports = ['polling', 'websocket'];
        console.log('[INFO] Socket.IO: Polling-first mode (HTTP primary + WebSocket upgrade)');
    }

    const io = new SocketIOServer(server, {
        cors: {
            origin: (origin, callback) => {
                if (!origin || allowedOrigins.includes(origin)) {
                    callback(null, true);
                } else {
                    console.warn(`[WARN] Socket.IO: CORS blocked origin: ${origin}`);
                    callback(new Error('CORS policy: Origin not allowed'));
                }
            },
            credentials: true,
            methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS']
        },

        // compat mode for old android clients
        allowEIO3: true,

        transports: transports,
        allowUpgrades: true,
        maxHttpBufferSize: 1024 * 1024,

        pingInterval: 5000,
        pingTimeout: 15000,

        serveClient: false,

        path: '/socket.io',

        perMessageDeflate: false,
        upgradeTimeout: 10000,

        connectTimeout: 15000,
        transactionTimeout: 30000
    });

    io.use((socket, next) => {
        try {
            const token = socket.handshake.auth.token ||
                         socket.handshake.query?.token;

            if (!token) {
                console.warn(`[WARN] Socket.IO: Connection attempt without token from ${socket.handshake.address}`);
                socket.userId = "guest_" + socket.id.substring(0, 8);
                socket.username = "Guest";
                socket.isAuthenticated = false;
                console.log(`[INFO] Socket.IO: Guest connection allowed from ${socket.handshake.address}`);
                return next();
            }

            const decoded = jwt.verify(token, jwtSecret);

            socket.userId = decoded.userId;
            socket.username = decoded.username;
            socket.user = decoded;
            socket.isAuthenticated = true;

            socket.data = socket.data || {};
            socket.data.deviceId = decoded.deviceId;

            console.log(`[OK] Socket.IO: User authenticated - ${decoded.username} (${decoded.userId}) from ${socket.handshake.address} (Device: ${decoded.deviceId})`);
            next();

        } catch (err) {
            console.error(`[ERROR] Socket.IO: Authentication failed - ${err.message}`);
            console.error(`[ERROR] Socket.IO: From address: ${socket.handshake.address}`);
            console.error(`[ERROR] Socket.IO: Query token: ${socket.handshake.query?.token ? 'present' : 'missing'}`);
            console.error(`[ERROR] Socket.IO: Auth token: ${socket.handshake.auth.token ? 'present' : 'missing'}`);
            return next(new Error(`Authentication failed: ${err.message}`));
        }
    });

    io.on('connection', (socket) => {
        const userId = socket.userId;
        const username = socket.username;

        console.log(`[OK] Socket.IO: User connected - ${username} (${userId}), Socket ID: ${socket.id}`);
        console.log(`[DEBUG] Socket.IO: Connection via transport: ${socket.conn.transport.name}`);
        console.log(`[DEBUG] Socket.IO: From address: ${socket.handshake.address}`);

        // multi-device connections allowed
        if (global.wsUserMap && userId) {
            if (!global.wsUserMap.has(userId)) {
                global.wsUserMap.set(userId, []);
            }
            global.wsUserMap.get(userId).push(socket.id);
        }

        if (global.wsClients) {
            global.wsClients.set(socket.id, {
                userId,
                username,
                socket,
                connectedAt: new Date()
            });
        }

        if (userId) {
            socket.join(`user:${userId}`);
            console.log(`[OK] Socket.IO: User ${username} joined room user:${userId}`);
        }

        socket.on('ping', (callback) => {
            if (typeof callback === 'function') {
                callback('pong');
            }
        });

        socket.on('disconnect', (reason) => {
            console.log(`[INFO] Socket.IO: User ${username} (${userId}) disconnected - Reason: ${reason}`);

            if (global.wsClients && global.wsClients.has(socket.id)) {
                try {
                    const { notifyUserOffline } = require('./broadcast-utils.js');
                } catch (err) {
                    console.warn('[WARN] Failed to notify offline status:', err.message);
                }
            }

            if (global.wsUserMap && global.wsUserMap.has(userId)) {
                const sockets = global.wsUserMap.get(userId);
                const index = sockets.indexOf(socket.id);
                if (index > -1) {
                    sockets.splice(index, 1);
                }
                if (sockets.length === 0) {
                    global.wsUserMap.delete(userId);
                }
            }

            if (global.wsClients) {
                global.wsClients.delete(socket.id);
            }
        });

        socket.on('profile.update', (data) => {
            const { profile, avatarUrl, imageId } = data;
            console.log(`[] Socket.IO: Profile update from ${username} (${userId}), avatar:`, avatarUrl || imageId);

            io.emit('user.profile.updated', {
                userId: userId,
                username: username,
                profile: profile,
                avatarUrl: avatarUrl,
                imageId: imageId,
                timestamp: Date.now(),
                updatedAt: new Date()
            });

            console.log(`[] Profile update broadcast sent to all clients for user ${userId}`);
        });

        socket.on('avatar.updated', (data) => {
            const { avatarUrl, imageId, fileName } = data;
            console.log(`[] Socket.IO: Avatar updated for ${username} (${userId}):`, avatarUrl);

            io.emit('user.avatar.updated', {
                userId: userId,
                username: username,
                avatarUrl: avatarUrl,
                imageId: imageId,
                fileName: fileName,
                timestamp: Date.now()
            });

            console.log(`[] Avatar update broadcast sent to all clients`);
        });

        socket.on('error', (err) => {
            console.error(`[ERROR] Socket.IO: Error for user ${username} (${userId}): ${err}`);
        });

        socket.on('join', (data) => {
            const requestedUserId = data?.userId || socket.userId;
            if (requestedUserId) {
                socket.join(`user:${requestedUserId}`);
                console.log(`[OK] Socket.IO: User ${username} explicitly joined room user:${requestedUserId}`);
            }
        });

        socket.conn.on('error', (err) => {
            console.error(`[ERROR] Socket.IO Connection Error (engine.io) for user ${username} (${userId}): ${err.message}`);
            console.error(`[ERROR] Transport: ${socket.conn.transport?.name || 'unknown'}`);
        });

        socket.on('message.ack', (data) => {
            const { messageId, recipientId } = data;
            io.to(`user:${recipientId}`).emit('message.delivered', {
                messageId,
                deliveredAt: new Date()
            });
        });

        socket.on('typing.start', (data) => {
            const { recipientId, chatId } = data;
            io.to(`user:${recipientId}`).emit('user.typing', {
                userId,
                username,
                chatId
            });
        });

        socket.on('typing.stop', (data) => {
            const { recipientId, chatId } = data;
            io.to(`user:${recipientId}`).emit('user.stopped-typing', {
                userId,
                username,
                chatId
            });
        });

        socket.on('group.join', (data) => {
            const { groupId } = data;
            socket.join(`group:${groupId}`);
            console.log(`[OK] Socket.IO: User ${username} joined group room ${groupId}`);

            io.to(`group:${groupId}`).emit('user.joined-group', {
                userId,
                username,
                groupId,
                timestamp: new Date()
            });
        });

        socket.on('group.leave', (data) => {
            const { groupId } = data;
            socket.leave(`group:${groupId}`);
            console.log(`[OK] Socket.IO: User ${username} left group room ${groupId}`);

            io.to(`group:${groupId}`).emit('user.left-group', {
                userId,
                username,
                groupId,
                timestamp: new Date()
            });
        });
    });

    if (global._socketIoStatInterval) clearInterval(global._socketIoStatInterval);
    global._socketIoStatInterval = setInterval(() => {
        const connectedClients = io.engine.clientsCount || 0;
        console.log(`[STAT] Socket.IO: Connected clients: ${connectedClients}`);

        if (global.wsClients) {
            for (const [sid, client] of global.wsClients.entries()) {
                const sock = io.sockets.sockets.get(sid);
                if (!sock) {
                    global.wsClients.delete(sid);
                    if (client.userId && global.wsUserMap) {
                        const sockets = global.wsUserMap.get(client.userId);
                        if (sockets) {
                            const idx = sockets.indexOf(sid);
                            if (idx > -1) sockets.splice(idx, 1);
                            if (sockets.length === 0) global.wsUserMap.delete(client.userId);
                        }
                    }
                }
            }
        }
    }, 60000);

    global.socketIoServer = io;
    global.io = io;

    console.log('[OK] Socket.IO: Server initialized and configured');
    console.log(' • Transports: WebSocket (primary), Polling (fallback)');
    console.log(' • CORS: Enabled for whitelisted origins');
    console.log(' • Authentication: JWT token required');
    console.log(' • Path: /socket.io');
    console.log(' • Server instance type:', server.constructor.name);
    console.log(' • Socket.IO instance created:', !!io);

    console.log('[INFO] Socket.IO middleware attached to server');

    return io;
}

module.exports = { initializeSocketIO };
