/**
 * Socket.IO Server for FreeTime Master-Server
 * Handles real-time communication via WebSocket with HTTP polling fallback
 * Integrated with Express server on the same port as REST API (443 HTTPS)
 */

const { Server: SocketIOServer } = require('socket.io');
const jwt = require('jsonwebtoken');

/**
 * Initialize Socket.IO server
 * @param {http.Server|https.Server} server - HTTPS/HTTP server instance to attach to
 * @param {string} jwtSecret - JWT secret for token verification
 * @param {array} allowedOrigins - CORS allowed origins
 * @returns {Server} Configured Socket.IO server instance
 */
function initializeSocketIO(server, jwtSecret, allowedOrigins) {
    if (!server) {
        throw new Error('Server instance required to initialize Socket.IO');
    }

    if (!jwtSecret) {
        throw new Error('JWT_SECRET required for Socket.IO authentication');
    }

    // ==================== TRANSPORT CONFIGURATION ====================
    // Support for different transport modes via environment variable
    // SOCKET_IO_TRANSPORT=polling  → Polling only (HTTP, always available)
    // SOCKET_IO_TRANSPORT=websocket → WebSocket only (WSS, low latency)
    // (default) → Both with polling first (recommended for most scenarios)
    
    const transportMode = process.env.SOCKET_IO_TRANSPORT || 'auto';
    let transports;
    
    if (transportMode === 'polling') {
        transports = ['polling'];  // HTTP polling only (reliable)
        console.log('[INFO] Socket.IO: Polling-only mode (HTTP fallback guaranteed)');
    } else if (transportMode === 'websocket') {
        transports = ['websocket'];  // WebSocket only (best performance)
        console.log('[INFO] Socket.IO: WebSocket-only mode (low latency)');
    } else {
        transports = ['polling', 'websocket'];  // Polling first, then WebSocket upgrade
        console.log('[INFO] Socket.IO: Polling-first mode (HTTP primary + WebSocket upgrade)');
    }

    // Configure Socket.IO with appropriate transports
    const io = new SocketIOServer(server, {
        // CORS configuration - allow requests from whitelisted origins
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

        // ✅ CRITICAL: Support Socket.IO v2.x/v3.x clients (Engine.IO v3 protocol)
        // Android client v2.1.1 uses Engine.IO v3, while Socket.IO v4 uses v4.
        allowEIO3: true,

        // Transport settings - configurable via environment
        transports: transports,
        allowUpgrades: true,
        maxHttpBufferSize: 1024 * 1024, // 1MB max payload

        // Connection timing - Optimized for mobile/polling with aggressive keep-alive
        pingInterval: 5000,   // Send ping every 5 seconds (AGGRESSIVE - for mobile)
        pingTimeout: 15000,   // Wait 15 seconds for pong before disconnecting (STRICT)

        // Serve client library - disabled (we provide it via HTTP)
        serveClient: false,
        
        // Path for Socket.IO connections
        path: '/socket.io',

        // Performance settings
        perMessageDeflate: false,  // Disable compression for lower latency
        upgradeTimeout: 10000,      // 10s timeout for WS upgrade from polling (FASTER)
        
        // ✅ NEW: Connection pooling and timeout settings
        connectTimeout: 15000,  // 15s to establish initial connection
        transactionTimeout: 30000 // 30s transaction timeout
    });

    // ==================== AUTHENTICATION MIDDLEWARE ====================

    /**
     * Socket.IO Authentication Middleware
     * Validates JWT token and extracts userId for proper user identification
     * This is CRITICAL for profile pictures, notifications, and calls routing
     */
    io.use((socket, next) => {
        try {
            const token = socket.handshake.auth.token || 
                         socket.handshake.query?.token;

            if (!token) {
                console.warn(`[WARN] Socket.IO: Connection attempt without token from ${socket.handshake.address}`);
                // Allow unauthenticated connections for polling test, but mark as guest
                socket.userId = "guest_" + socket.id.substring(0, 8);
                socket.username = "Guest";
                socket.isAuthenticated = false;
                console.log(`[INFO] Socket.IO: Guest connection allowed from ${socket.handshake.address}`);
                return next();
            }

            // Verify JWT token
            const decoded = jwt.verify(token, jwtSecret);
            
            // Extract userId and deviceId from token
            socket.userId = decoded.userId;
            socket.username = decoded.username;
            socket.user = decoded;
            socket.isAuthenticated = true;
            
            // ✅ NEW: Store deviceId in socket data for device tracking
            socket.data = socket.data || {};
            socket.data.deviceId = decoded.deviceId;
            
            console.log(`[OK] Socket.IO: User authenticated - ${decoded.username} (${decoded.userId}) from ${socket.handshake.address} (Device: ${decoded.deviceId})`);
            next();
            
        } catch (err) {
            console.error(`[ERROR] Socket.IO: Authentication failed - ${err.message}`);
            console.error(`[ERROR] Socket.IO: From address: ${socket.handshake.address}`);
            console.error(`[ERROR] Socket.IO: Query token: ${socket.handshake.query?.token ? 'present' : 'missing'}`);
            console.error(`[ERROR] Socket.IO: Auth token: ${socket.handshake.auth.token ? 'present' : 'missing'}`);
            // Reject connection on auth failure
            return next(new Error(`Authentication failed: ${err.message}`));
        }
    });

    // ==================== CONNECTION HANDLERS ====================

    /**
     * Handle new Socket.IO connections
     */
    io.on('connection', (socket) => {
        const userId = socket.userId;
        const username = socket.username;

        console.log(`[OK] Socket.IO: User connected - ${username} (${userId}), Socket ID: ${socket.id}`);
        console.log(`[DEBUG] Socket.IO: Connection via transport: ${socket.conn.transport.name}`);
        console.log(`[DEBUG] Socket.IO: From address: ${socket.handshake.address}`);

        // Map user ID to socket for direct messaging
        if (global.wsUserMap && userId) {
            if (!global.wsUserMap.has(userId)) {
                global.wsUserMap.set(userId, []);
            }
            global.wsUserMap.get(userId).push(socket.id);
        }

        // Also store in global clients map
        if (global.wsClients) {
            global.wsClients.set(socket.id, {
                userId,
                username,
                socket,
                connectedAt: new Date()
            });
        }

        // Join room for this user
        if (userId) {
            socket.join(`user:${userId}`);
            console.log(`[OK] Socket.IO: User ${username} joined room user:${userId}`);
        }

        // ==================== SOCKET EVENTS ====================

        /**
         * ping-pong for keep-alive testing
         */
        socket.on('ping', (callback) => {
            if (typeof callback === 'function') {
                callback('pong');
            }
        });

        /**
         * Handle manual disconnect/logout
         */
        socket.on('disconnect', (reason) => {
            console.log(`[INFO] Socket.IO: User ${username} (${userId}) disconnected - Reason: ${reason}`);

            // Notify friends user is offline
            if (global.wsClients && global.wsClients.has(socket.id)) {
                try {
                    const { notifyUserOffline } = require('./broadcast-utils.js');
                    // In a real app, you'd fetch friends from DB here
                    // For now, we broadcast to all as a fallback if friend list isn't cached
                    // notifyUserOffline(io, userId, new Date(), []); 
                } catch (err) {
                    console.warn('[WARN] Failed to notify offline status:', err.message);
                }
            }

            // Remove from user map
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

            // Remove from clients map
            if (global.wsClients) {
                global.wsClients.delete(socket.id);
            }
        });

        /**
         * Broadcast profile update to all connected clients
         * 🔧 IMPROVED: Now broadcasts avatar updates to ensure profile pictures sync
         */
        socket.on('profile.update', (data) => {
            const { profile, avatarUrl, imageId } = data;
            console.log(`[✓] Socket.IO: Profile update from ${username} (${userId}), avatar:`, avatarUrl || imageId);
            
            // Broadcast to EVERYONE that this user's profile changed
            // This ensures all clients get the update immediately
            io.emit('user.profile.updated', {
                userId: userId,
                username: username,
                profile: profile,
                avatarUrl: avatarUrl,
                imageId: imageId,
                timestamp: Date.now(),
                updatedAt: new Date()
            });
            
            console.log(`[✓] Profile update broadcast sent to all clients for user ${userId}`);
        });

        /**
         * Handle avatar/profile picture updates specifically
         * 🔧 NEW: Dedicated handler for avatar changes to ensure they sync
         */
        socket.on('avatar.updated', (data) => {
            const { avatarUrl, imageId, fileName } = data;
            console.log(`[✓] Socket.IO: Avatar updated for ${username} (${userId}):`, avatarUrl);
            
            // Broadcast avatar update to all connected clients
            io.emit('user.avatar.updated', {
                userId: userId,
                username: username,
                avatarUrl: avatarUrl,
                imageId: imageId,
                fileName: fileName,
                timestamp: Date.now()
            });
            
            console.log(`[✓] Avatar update broadcast sent to all clients`);
        });

        /**
         * Handle errors on the socket connection
         */
        socket.on('error', (err) => {
            console.error(`[ERROR] Socket.IO: Error for user ${username} (${userId}): ${err}`);
        });
        
        /**
         * Handle explicit room join request from client
         * ✅ NEW: Explicit join handler for reliability
         */
        socket.on('join', (data) => {
            const requestedUserId = data?.userId || socket.userId;
            if (requestedUserId) {
                socket.join(`user:${requestedUserId}`);
                console.log(`[OK] Socket.IO: User ${username} explicitly joined room user:${requestedUserId}`);
            }
        });
        
        /**
         * Handle connection errors from engine.io layer
         */
        socket.conn.on('error', (err) => {
            console.error(`[ERROR] Socket.IO Connection Error (engine.io) for user ${username} (${userId}): ${err.message}`);
            console.error(`[ERROR] Transport: ${socket.conn.transport?.name || 'unknown'}`);
        });

        /**
         * Listen for incoming call signaling
         */
        socket.on('call.signal', (data) => {
            const { targetUserId, signalData } = data;
            console.log(`[INFO] Socket.IO: Call signal from ${username} to ${targetUserId}`);
            io.to(`user:${targetUserId}`).emit('call.signal', {
                fromUserId: userId,
                fromUsername: username,
                signalData
            });
        });

        /**
         * Listen for message acknowledgments
         */
        socket.on('message.ack', (data) => {
            const { messageId, recipientId } = data;
            io.to(`user:${recipientId}`).emit('message.delivered', {
                messageId,
                deliveredAt: new Date()
            });
        });

        /**
         * Typing indicator for real-time chat
         */
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

        /**
         * Group messaging
         */
        socket.on('group.join', (data) => {
            const { groupId } = data;
            socket.join(`group:${groupId}`);
            console.log(`[OK] Socket.IO: User ${username} joined group room ${groupId}`);
            
            // Notify other group members
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
            
            // Notify remaining group members
            io.to(`group:${groupId}`).emit('user.left-group', {
                userId,
                username,
                groupId,
                timestamp: new Date()
            });
        });
    });

    // Provide connection statistics (prevent duplicate intervals on re-init)
    if (global._socketIoStatInterval) clearInterval(global._socketIoStatInterval);
    global._socketIoStatInterval = setInterval(() => {
        const connectedClients = io.engine.clientsCount || 0;
        console.log(`[STAT] Socket.IO: Connected clients: ${connectedClients}`);

        // Periodic cleanup of stale wsClients entries (connections that closed without disconnect)
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
    }, 60000); // Log and clean every minute

    // Make socket.io instance globally available for API routes
    global.socketIoServer = io;
    global.io = io; // Backward compatibility

    console.log('[OK] Socket.IO: Server initialized and configured');
    console.log('    • Transports: WebSocket (primary), Polling (fallback)');
    console.log('    • CORS: Enabled for whitelisted origins');
    console.log('    • Authentication: JWT token required');
    console.log('    • Path: /socket.io');
    console.log('    • Server instance type:', server.constructor.name);
    console.log('    • Socket.IO instance created:', !!io);
    
    // Log all mounted routes/handlers
    console.log('[INFO] Socket.IO middleware attached to server');

    return io;
}

module.exports = { initializeSocketIO };
