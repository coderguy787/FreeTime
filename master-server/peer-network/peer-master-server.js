/**
 * FreeTime Master-Server Peer Network Server
 * Peer-to-peer network coordination and message routing
 * Port: 9080 (WSS - WebSocket Secure)
 * ✅ NOW SUPPORTS SOCKET.IO for Android app compatibility
 */

const express = require('express');
const https = require('https');
const fs = require('fs');
const WebSocket = require('ws');
const path = require('path');
const { MongoClient } = require('mongodb');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');
const { Server: SocketIOServer } = require('socket.io');  // ✅ ADD: Socket.IO for app compatibility
const DatabaseConnectionManager = require('../utils/database-connection-manager');
const WebSocketConnectionManager = require('../utils/websocket-connection-manager');

// Environment configuration
require('dotenv').config({ path: path.join(__dirname, '../config/.env') });

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-here';
const PEER_PORT = process.env.PORT_PEER || 9080;
const PEER_HOST = process.env.PEER_HOST || '0.0.0.0';
const PEER_TIMEOUT = process.env.PEER_TIMEOUT || 5000;

// SSL/HTTPS Configuration for WSS
const SSL_CERT_PATH = process.env.SSL_CERT || path.join(__dirname, '../certs/fullchain.pem');
const SSL_KEY_PATH = process.env.SSL_KEY || path.join(__dirname, '../certs/privkey.pem');

let sslOptions = null;
try {
    if (fs.existsSync(SSL_CERT_PATH) && fs.existsSync(SSL_KEY_PATH)) {
        sslOptions = {
            cert: fs.readFileSync(SSL_CERT_PATH, 'utf8'),
            key: fs.readFileSync(SSL_KEY_PATH, 'utf8')
        };
        console.log('[OK] Peer Network: SSL certificates loaded (WSS enabled)');
    } else {
        console.warn('[WARNING] Peer Network: SSL certificates not found - running in unencrypted mode (WS)');
    }
} catch (err) {
    console.error('[ERROR] Peer Network: Failed to load SSL certificates:', err.message);
    console.warn('[WARNING] Peer Network: Running in unencrypted mode (WS)');
}

// Create Express app
const app = express();
app.use(cors());
app.use(express.json());

// Create HTTPS server for Peer Network Secure (WSS)
const server = sslOptions ? https.createServer(sslOptions, app) : require('http').createServer(app);

// MongoDB connection with resilience
let dbManager = null;
let dbConnection = null; // Backward compatibility wrapper
let wsManager = null;    // WebSocket server manager

async function connectDB() {
    try {
        // Initialize database manager with production settings
        dbManager = new DatabaseConnectionManager(MONGODB_URI, {
            maxPoolSize: 30,
            minPoolSize: 5,
            maxRetries: 3,
            healthCheckIntervalMs: 30000,
            enableHealthCheck: true
        });
        
        await dbManager.initialize();
        console.log('[OK] Peer Network: Connected to MongoDB with resilience layer');
        
        // Set up backward compatibility wrapper
        dbConnection = {
            collection: (name) => dbManager.getCollection(name),
            getDatabase: () => dbManager.getDatabase()
        };
        
        // Initialize peer collections
        await initializePeerSystem();
    } catch (err) {
        console.error('[ERROR] Peer Network: MongoDB connection failed:', err);
        process.exit(1);
    }
}

// Initialize peer system
async function initializePeerSystem() {
    try {
        const peersCol = dbConnection.collection('peers');
        
        // Create indexes
        await peersCol.createIndex({ id: 1 }, { unique: true }).catch(() => {});
        await peersCol.createIndex({ name: 1 }, { unique: true, sparse: true }).catch(() => {});
        await peersCol.createIndex({ connected: 1 }).catch(() => {});
        await peersCol.createIndex({ lastConnected: -1 }).catch(() => {});
        
        console.log('[OK] Peer Network: Database initialized');
    } catch (err) {
        console.error('[WARN] Peer Network: Initialization warning:', err.message);
    }
}

// Connected peers management
const connectedPeers = new Map();

// Peer registration
app.post('/register', async (req, res) => {
    try {
        const { name, type, address, port, apiKey, region } = req.body;

        // Validation
        if (!name || !type || !address || !port) {
            return res.status(400).json({ error: 'Name, type, address, and port required' });
        }

        if (!['PUBLIC_IP', 'DOMAIN', 'LOCAL_IP'].includes(type)) {
            return res.status(400).json({ error: 'Invalid peer type' });
        }

        // Check for existing peer
        const existingPeer = await dbConnection.collection('peers').findOne({
            $or: [{ name }, { address, port }]
        });

        if (existingPeer) {
            return res.status(409).json({ 
                error: existingPeer.name === name ? 'Peer name already exists' : 'Peer address already exists'
            });
        }

        // Create new peer
        const newPeer = {
            id: uuidv4(),
            name,
            type,
            address,
            port: parseInt(port),
            apiKey: await require('bcryptjs').hash(apiKey || 'default-key', 10),
            region: region || 'Unknown',
            connected: false,
            lastConnected: new Date(),
            latency: null,
            uptime: 0,
            messageCount: 0,
            createdAt: new Date(),
            updatedAt: new Date()
        };

        await dbConnection.collection('peers').insertOne(newPeer);

        await logEvent('peer_register', `Peer registered: ${name}`, null, { 
            peerId: newPeer.id,
            address: `${address}:${port}`
        });

        res.status(201).json({
            success: true,
            peer: { ...newPeer, apiKey: undefined }
        });

    } catch (err) {
        console.error('[ERROR] Peer registration error:', err);
        res.status(500).json({ error: err.message });
    }
});

// Peer authentication
async function authenticatePeer(req, res, next) {
    const token = req.headers.authorization?.replace('Bearer ', '') || 
                  req.headers['x-api-key'];

    if (!token) {
        return res.status(401).json({ error: 'Authentication required' });
    }

    try {
        // Try JWT token first
        const decoded = jwt.verify(token, JWT_SECRET);
        req.peer = decoded;
        return next();
    } catch (jwtErr) {
        // Try API key authentication
        const peer = await dbConnection.collection('peers').findOne({
            apiKey: token
        });

        if (peer) {
            req.peer = { id: peer.id, name: peer.name, role: 'PEER' };
            return next();
        }

        return res.status(401).json({ error: 'Invalid authentication' });
    }
}

// Connect peer to network
app.post('/connect', authenticatePeer, async (req, res) => {
    try {
        const { capabilities } = req.body;
        const peerId = req.peer.id;

        // Update peer connection status
        await dbConnection.collection('peers').updateOne(
            { id: peerId },
            { 
                $set: { 
                    connected: true,
                    lastConnected: new Date(),
                    capabilities: capabilities || [],
                    updatedAt: new Date()
                }
            }
        );

        // Add to connected peers
        connectedPeers.set(peerId, {
            peer: req.peer,
            connectedAt: new Date(),
            lastPing: new Date(),
            capabilities: capabilities || []
        });

        await logEvent('peer_connect', `Peer connected: ${req.peer.name}`, null, { peerId });

        // Notify other peers
        await notifyPeers('peer_connected', {
            peerId,
            name: req.peer.name,
            connectedAt: new Date().toISOString()
        }, peerId);

        res.json({
            success: true,
            message: 'Connected to peer network',
            networkSize: connectedPeers.size,
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        console.error('[ERROR] Peer connection error:', err);
        res.status(500).json({ error: err.message });
    }
});

// Disconnect peer from network
app.post('/disconnect', authenticatePeer, async (req, res) => {
    try {
        const peerId = req.peer.id;

        // Update peer connection status
        await dbConnection.collection('peers').updateOne(
            { id: peerId },
            { 
                $set: { 
                    connected: false,
                    updatedAt: new Date()
                }
            }
        );

        // Remove from connected peers
        connectedPeers.delete(peerId);

        await logEvent('peer_disconnect', `Peer disconnected: ${req.peer.name}`, null, { peerId });

        // Notify other peers
        await notifyPeers('peer_disconnected', {
            peerId,
            name: req.peer.name,
            disconnectedAt: new Date().toISOString()
        });

        res.json({
            success: true,
            message: 'Disconnected from peer network'
        });

    } catch (err) {
        console.error('[ERROR] Peer disconnection error:', err);
        res.status(500).json({ error: err.message });
    }
});

// Route message between peers
app.post('/message', authenticatePeer, async (req, res) => {
    try {
        const { to, message, type = 'text', metadata } = req.body;
        const fromPeerId = req.peer.id;

        if (!to || !message) {
            return res.status(400).json({ error: 'Recipient and message required' });
        }

        // Find target peer
        const targetPeer = await dbConnection.collection('peers').findOne({ id: to });
        
        if (!targetPeer) {
            return res.status(404).json({ error: 'Target peer not found' });
        }

        if (!targetPeer.connected) {
            return res.status(400).json({ error: 'Target peer not connected' });
        }

        // Create message document
        const messageDoc = {
            id: uuidv4(),
            from: fromPeerId,
            to: to,
            message: message,
            type: type,
            metadata: metadata || {},
            status: 'sent',
            createdAt: new Date(),
            updatedAt: new Date()
        };

        // Save to database
        await dbConnection.collection('peer_messages').insertOne(messageDoc);

        // Update message count
        await dbConnection.collection('peers').updateOne(
            { id: fromPeerId },
            { $inc: { messageCount: 1 } }
        );

        // Send message to target peer
        const delivered = await sendMessageToPeer(targetPeer, messageDoc);

        if (delivered) {
            // Update message status
            await dbConnection.collection('peer_messages').updateOne(
                { id: messageDoc.id },
                { $set: { status: 'delivered', deliveredAt: new Date() } }
            );

            await logEvent('peer_message', `Message: ${req.peer.name} → ${targetPeer.name}`, null, { 
                messageId: messageDoc.id,
                from: fromPeerId,
                to: to
            });

            res.json({
                success: true,
                messageId: messageDoc.id,
                status: 'delivered',
                timestamp: new Date().toISOString()
            });
        } else {
            res.status(500).json({ error: 'Failed to deliver message to target peer' });
        }

    } catch (err) {
        console.error('[ERROR] Peer message error:', err);
        res.status(500).json({ error: err.message });
    }
});

// Send message to specific peer
async function sendMessageToPeer(peer, message) {
    try {
        const peerUrl = `http://${peer.address}:${peer.port}/message/receive`;
        
        const response = await axios.post(peerUrl, message, {
            timeout: PEER_TIMEOUT,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${process.env.JWT_SECRET}`
            }
        });

        return response.status === 200;
    } catch (err) {
        console.error(`[ERROR] Failed to send message to peer ${peer.name}:`, err.message);
        return false;
    }
}

// Receive message from peer
app.post('/message/receive', authenticatePeer, async (req, res) => {
    try {
        const message = req.body;

        // Store received message
        await dbConnection.collection('peer_messages').updateOne(
            { id: message.id },
            { 
                $set: { 
                    status: 'received',
                    receivedAt: new Date(),
                    updatedAt: new Date()
                }
            }
        );

        // Notify connected clients via WebSocket if available
        // This would integrate with the WebSocket server
        broadcastToClients('peer_message', {
            message,
            timestamp: new Date().toISOString()
        });

        res.json({
            success: true,
            status: 'received',
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        console.error('[ERROR] Message receive error:', err);
        res.status(500).json({ error: err.message });
    }
});

// Get list of connected peers
app.get('/peers', authenticatePeer, async (req, res) => {
    try {
        const peers = await dbConnection.collection('peers')
            .find({ connected: true })
            .project({ apiKey: 0 })
            .sort({ lastConnected: -1 })
            .toArray();

        res.json({
            peers,
            count: peers.length,
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Test peer connectivity
app.post('/test/:peerId', authenticatePeer, async (req, res) => {
    try {
        const { peerId } = req.params;
        
        const peer = await dbConnection.collection('peers').findOne({ id: peerId });
        
        if (!peer) {
            return res.status(404).json({ error: 'Peer not found' });
        }

        const startTime = Date.now();
        
        try {
            const response = await axios.get(`http://${peer.address}:${peer.port}/health`, {
                timeout: PEER_TIMEOUT
            });
            
            const latency = Date.now() - startTime;

            // Update peer latency
            await dbConnection.collection('peers').updateOne(
                { id: peerId },
                { 
                    $set: { 
                        latency: latency,
                        lastConnected: new Date(),
                        updatedAt: new Date()
                    }
                }
            );

            res.json({
                status: 'online',
                latency: latency,
                timestamp: new Date().toISOString(),
                responseTime: `${latency}ms`
            });

        } catch (err) {
            // Peer is offline
            await dbConnection.collection('peers').updateOne(
                { id: peerId },
                { 
                    $set: { 
                        connected: false,
                        updatedAt: new Date()
                    }
                }
            );

            res.json({
                status: 'offline',
                error: err.message,
                timestamp: new Date().toISOString()
            });
        }

    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'online',
        service: 'peer-network-server',
        port: PEER_PORT,
        connectedPeers: connectedPeers.size,
        uptime: process.uptime(),
        memory: process.memoryUsage(),
        timestamp: new Date().toISOString()
    });
});

// Network statistics
app.get('/stats', authenticatePeer, async (req, res) => {
    try {
        const totalPeers = await dbConnection.collection('peers').countDocuments();
        const connectedPeersCount = await dbConnection.collection('peers').countDocuments({ connected: true });
        const totalMessages = await dbConnection.collection('peer_messages').countDocuments();

        const recentMessages = await dbConnection.collection('peer_messages')
            .find({ createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000) } })
            .countDocuments();

        res.json({
            totalPeers,
            connectedPeers: connectedPeersCount,
            totalMessages,
            messagesLast24h: recentMessages,
            uptime: process.uptime(),
            memory: process.memoryUsage(),
            timestamp: new Date().toISOString()
        });

    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Notify all connected peers
async function notifyPeers(event, data, excludePeerId = null) {
    const message = { type: 'network_event', event, data };
    
    for (const [peerId, peerInfo] of connectedPeers) {
        if (peerId !== excludePeerId) {
            try {
                const peer = await dbConnection.collection('peers').findOne({ id: peerId });
                if (peer && peer.connected) {
                    await sendMessageToPeer(peer, message);
                }
            } catch (err) {
                console.error(`[ERROR] Failed to notify peer ${peerId}:`, err.message);
            }
        }
    }
}

// Broadcast to WebSocket clients (if integration available)
function broadcastToClients(event, data) {
    // This would integrate with the WebSocket server
    // For now, just log the event
    console.log(`📡 Broadcasting to clients: ${event}`, data);
}

// Logging function
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

        // Also write to file
        const fs = require('fs');
        const logsDir = path.join(__dirname, '../logs');
        if (!fs.existsSync(logsDir)) fs.mkdirSync(logsDir, { recursive: true });
        
        const logFile = path.join(logsDir, 'peer-events.log');
        const logText = `[${new Date().toISOString()}] ${type.toUpperCase()}: ${message}\n`;
        fs.appendFileSync(logFile, logText);
    } catch (err) {
        console.error('Peer log error:', err);
    }
}

// Peer health monitoring
setInterval(async () => {
    for (const [peerId, peerInfo] of connectedPeers) {
        try {
            const peer = await dbConnection.collection('peers').findOne({ id: peerId });
            
            if (peer) {
                const response = await axios.get(`http://${peer.address}:${peer.port}/health`, {
                    timeout: PEER_TIMEOUT
                });

                // Update peer latency
                await dbConnection.collection('peers').updateOne(
                    { id: peerId },
                    { 
                        $set: { 
                            latency: Date.now() - peerInfo.lastPing.getTime(),
                            lastPing: new Date()
                        }
                    }
                );

                peerInfo.lastPing = new Date();
            } else {
                connectedPeers.delete(peerId);
            }
        } catch (err) {
            // Mark peer as disconnected
            await dbConnection.collection('peers').updateOne(
                { id: peerId },
                { 
                    $set: { 
                        connected: false,
                        updatedAt: new Date()
                    }
                }
            );
            
            connectedPeers.delete(peerId);
            
            await logEvent('peer_timeout', `Peer timeout: ${peerInfo.peer.name}`, null, { peerId });
        }
    }
}, 60000); // Check every minute

// Initialize WebSocket server for peer-to-peer communication
async function initializeWebSocketServer() {
    try {
        const wsPath = '/ws/peer';
        const wss = new WebSocket.Server({ server, path: wsPath });
        
        console.log(`[OK] Peer Network: Raw WebSocket server initialized on ${wsPath}`);
        
        // ✅ ADD: Initialize Socket.IO for Android app compatibility
        // This runs alongside the raw WebSocket server on the same port/server
        const io = new SocketIOServer(server, {
            cors: {
                origin: '*',
                credentials: true,
                methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS']
            },
            transports: ['websocket', 'polling'],
            path: '/socket.io/'
        });
        
        // ✅ Socket.IO Authentication Middleware
        io.use((socket, next) => {
            try {
                const token = socket.handshake.auth.token || socket.handshake.query?.token;
                if (!token) {
                    return next(new Error('Authentication: No token provided'));
                }
                
                const decoded = jwt.verify(token, JWT_SECRET);
                socket.userId = decoded.userId;
                socket.peerId = decoded.peerId || decoded.userId;
                next();
            } catch (err) {
                console.warn(`[WARN] Socket.IO auth failed: ${err.message}`);
                next(new Error('Invalid authentication token'));
            }
        });
        
        // ✅ Socket.IO Connection Handler
        io.on('connection', (socket) => {
            console.log(`[OK] Socket.IO peer connected: ${socket.peerId} (Socket ID: ${socket.id})`);
            
            socket.on('peer-message', (data) => {
                console.log(`[INFO] Peer message: ${socket.peerId} → ${data.to}`);
                io.to(data.to).emit('peer-message-received', {
                    from: socket.peerId,
                    content: data.content,
                    timestamp: new Date().toISOString()
                });
            });
            
            socket.on('disconnect', () => {
                console.log(`[INFO] Socket.IO peer disconnected: ${socket.peerId}`);
            });
        });
        
        console.log(`[OK] Peer Network: Socket.IO server initialized for app compatibility`);
        
        // Continue with raw WebSocket setup...
        
        wss.on('connection', async (ws, req) => {
            const peerId = req.headers['x-peer-id'] || uuidv4();
            const clientIp = req.socket.remoteAddress;
            
            console.log(`[INFO] Peer WebSocket connected: ${peerId} from ${clientIp}`);
            
            // Set up heartbeat mechanism
            let isAlive = true;
            const heartbeatInterval = setInterval(() => {
                if (!isAlive) {
                    console.log(`[WARN] Peer ${peerId}: No heartbeat response, terminating`);
                    ws.terminate();
                    clearInterval(heartbeatInterval);
                    return;
                }
                isAlive = false;
                ws.ping();
            }, 30000); // 30 second heartbeat
            
            ws.on('pong', () => {
                isAlive = true;
            });
            
            // Handle incoming peer messages
            ws.on('message', async (data) => {
                try {
                    const message = JSON.parse(data);
                    
                    switch (message.type) {
                        case 'peer-message':
                            // Route message to target peer
                            const targetPeer = await dbConnection.collection('peers').findOne({ id: message.to });
                            if (targetPeer) {
                                // Broadcast to other connected peers
                                broadcastToClients('peer-message', {
                                    from: peerId,
                                    to: message.to,
                                    content: message.content,
                                    timestamp: new Date().toISOString()
                                });
                            }
                            break;
                            
                        case 'register-peer':
                            // Register peer in database
                            await dbConnection.collection('peers').updateOne(
                                { id: peerId },
                                { 
                                    $set: { 
                                        wsConnected: true,
                                        wsLastActivity: new Date(),
                                        clientIp
                                    }
                                }
                            );
                            ws.send(JSON.stringify({ 
                                type: 'peer-registered',
                                peerId,
                                message: 'Successfully registered with peer network'
                            }));
                            break;
                            
                        default:
                            console.log(`[DEBUG] Unknown message type: ${message.type}`);
                    }
                } catch (err) {
                    console.error('[ERROR] WebSocket message error:', err.message);
                }
            });
            
            // Handle peer disconnect
            ws.on('close', async () => {
                clearInterval(heartbeatInterval);
                console.log(`[INFO] Peer WebSocket disconnected: ${peerId}`);
                
                // Update peer status
                try {
                    await dbConnection.collection('peers').updateOne(
                        { id: peerId },
                        { 
                            $set: { 
                                wsConnected: false,
                                wsLastDisconnect: new Date()
                            }
                        }
                    );
                } catch (err) {
                    console.warn(`[WARN] Failed to update peer status: ${err.message}`);
                }
            });
            
            ws.on('error', (err) => {
                console.error(`[ERROR] Peer WebSocket error for ${peerId}:`, err.message);
            });
        });
        
        wss.on('error', (err) => {
            console.error('[ERROR] WebSocket server error:', err);
        });
        
        return wss;
    } catch (err) {
        console.error('[ERROR] Failed to initialize WebSocket server:', err);
        throw err;
    }
}

// Start server
async function startServer() {
    await connectDB();
    
    // Initialize WebSocket server for peer communication
    await initializeWebSocketServer();
    
    server.listen(PEER_PORT, PEER_HOST, () => {
        const protocol = sslOptions ? 'WSS (Secure)' : 'WS (Unencrypted)';
        console.log(`[OK] Peer Network Server running on ${PEER_HOST}:${PEER_PORT} (${protocol})`);
        console.log(`[INFO] WebSocket Protocol: ${protocol}`);
        console.log(`[INFO] WebSocket Path: /ws/peer`);
        console.log(`[INFO] Health Endpoint: https://localhost:${PEER_PORT}/health`);
        console.log(`[INFO] Stats Endpoint: https://localhost:${PEER_PORT}/stats`);
        console.log(`[INFO] MongoDB: Connected with resilience layer`);
    });
}

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('[INFO] Peer Network: SIGTERM received, shutting down gracefully');
    server.close(() => {
        console.log('✅ Peer Network: Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('[INFO] Peer Network: SIGINT received, shutting down gracefully');
    server.close(() => {
        console.log('[OK] Peer Network: Server closed');
        process.exit(0);
    });
});

// Start the server only if run directly
if (require.main === module) {
    startServer().catch(err => {
        console.error('[ERROR] Peer Network: Failed to start server:', err);
        process.exit(1);
    });
}

module.exports = { app, server, startServer };
