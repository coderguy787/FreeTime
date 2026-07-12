#!/usr/bin/env node

/**
 * WebSocket-Only Socket.IO Server
 * Runs on port 8080 (can use WSS with certificate)
 * Provides ONLY WebSocket transport for low-latency real-time communication
 * 
 * Main polling service (port 443) provides REST API + HTTP polling fallback
 */

const express = require('express');
const https = require('https');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const { Server: SocketIOServer } = require('socket.io');

// ==================== CONFIGURATION ====================

const WEBSOCKET_PORT = process.env.WEBSOCKET_PORT || 8080;
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-here';

// Get allowed origins from environment
const allowedOrigins = (process.env.ALLOWED_ORIGINS || 'http://localhost:3000,https://example.com')
    .split(',')
    .map(origin => origin.trim());

// ==================== SSL/HTTPS CONFIGURATION ====================
const SSL_CERT_PATH = process.env.SSL_CERT || path.join(__dirname, '../certs/fullchain.pem');
const SSL_KEY_PATH = process.env.SSL_KEY || path.join(__dirname, '../certs/privkey.pem');

let sslOptions = null;
try {
    if (fs.existsSync(SSL_CERT_PATH) && fs.existsSync(SSL_KEY_PATH)) {
        sslOptions = {
            cert: fs.readFileSync(SSL_CERT_PATH, 'utf8'),
            key: fs.readFileSync(SSL_KEY_PATH, 'utf8')
        };
        console.log('[OK] WebSocket-Only Service: SSL certificates loaded (WSS enabled)');
    } else {
        console.warn('[WARN] WebSocket-Only Service: SSL certificates not found - will run in unencrypted mode (WS)');
    }
} catch (err) {
    console.warn('[WARN] WebSocket-Only Service: Failed to load SSL certificates:', err.message);
}

console.log(`
╔════════════════════════════════════════════════════════════════╗
║     FreeTime WebSocket-Only Service (Polling Alternative)      ║
╚════════════════════════════════════════════════════════════════╝

[INFO] Starting WebSocket-Only Socket.IO Server
[INFO] Port: ${WEBSOCKET_PORT}
[INFO] Transport: WebSocket only (ideal for real-time features)
[INFO] Fallback: Use polling service on port 443 if this fails
`);

// ==================== CREATE EXPRESS APP ====================

const app = express();
app.use(express.json());

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'websocket-service-healthy',
        port: WEBSOCKET_PORT,
        timestamp: new Date().toISOString()
    });
});

// ==================== SOCKET.IO INITIALIZATION ====================

const { initializeSocketIO } = require('./socket-io-server.js');

let server;
let isSecure = false;

// ✅ CRITICAL FIX: Use HTTPS if certificates are available
// This ensures WebSocket connections are secure (wss:// not ws://)
// Android app security policies require secure WebSocket connections
if (sslOptions) {
    server = https.createServer(sslOptions, app);
    isSecure = true;
    console.log('[OK] WebSocket-Only Service: Using HTTPS (secure wss:// connection)');
} else {
    // Fallback to HTTP only if certs not available (not recommended for production)
    server = app.listen(WEBSOCKET_PORT, '0.0.0.0');
    console.warn('[WARN] WebSocket-Only Service: No SSL certs found, using HTTP (insecure ws://)');
}

// Start server and initialize Socket.IO
if (sslOptions) {
    server.listen(WEBSOCKET_PORT, '0.0.0.0', () => {
        const protocol = isSecure ? 'wss' : 'ws';
        console.log(`
[OK] WebSocket-Only Service Started
     • Listening on: ${protocol}://0.0.0.0:${WEBSOCKET_PORT}
     • Transport: WebSocket only (no polling fallback)
     • Protocol: ${isSecure ? 'Secure (HTTPS)' : 'Unencrypted (HTTP - NOT RECOMMENDED)'}
     • Use this for: Real-time calls, instant messages, notifications
     • Falls back to: HTTP polling on port 443 if WSS fails
    `);

    // Initialize Socket.IO with WebSocket transport only
    try {
        const io = initializeSocketIO(server, JWT_SECRET, allowedOrigins);
        
        // Override transports to WebSocket only
        io.opts.transports = ['websocket'];
        console.log('[OK] Socket.IO configured for WebSocket-only transport');
        console.log(`[OK] Secure Connection: ${isSecure ? 'wss://' : 'ws://'} enabled`);
        
        // Store reference globally
        global.socketIoWebSocketServer = io;
        
    } catch (ioErr) {
        console.error('[ERROR] Socket.IO initialization failed:', ioErr.message);
        process.exit(1);
    }
});
} else {
    // Non-HTTPS mode (not running listen() separately above)
    server.listen(WEBSOCKET_PORT, '0.0.0.0', () => {
        console.log(`
[OK] WebSocket-Only Service Started (HTTP Mode)
     • Listening on: ws://0.0.0.0:${WEBSOCKET_PORT}
     • Transport: WebSocket only (no polling fallback)
     • ⚠️  WARNING: Using insecure ws:// instead of wss://
     • Falls back to: HTTP polling on port 443 if WS fails
    `);

        // Initialize Socket.IO
        try {
            const io = initializeSocketIO(server, JWT_SECRET, allowedOrigins);
            io.opts.transports = ['websocket'];
            console.log('[OK] Socket.IO configured for WebSocket-only transport');
            global.socketIoWebSocketServer = io;
        } catch (ioErr) {
            console.error('[ERROR] Socket.IO initialization failed:', ioErr.message);
            process.exit(1);
        }
    });
}

server.on('error', (err) => {
    console.error('[ERROR] Server error:', err);
    process.exit(1);
});

// ==================== GRACEFUL SHUTDOWN ====================

process.on('SIGTERM', () => {
    console.log('[INFO] SIGTERM received, shutting down gracefully...');
    server.close(() => {
        console.log('[OK] WebSocket service shutdown complete');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('[INFO] SIGINT received, shutting down gracefully...');
    server.close(() => {
        console.log('[OK] WebSocket service shutdown complete');
        process.exit(0);
    });
});

console.log('[INFO] WebSocket service ready. Waiting for connections...');
