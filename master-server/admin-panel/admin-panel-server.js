/**
 * FreeTime Master-Server Admin Panel Server
 * Web interface for admin dashboard with full service monitoring
 * Port: 3001 (LAN-only by default)
 * 
 * Monitors:
 * - API Server (Port 443 - HTTPS)
 * - WebSocket Server (Port 8080)  
 * - Peer Network (Port 9080)
 * - MongoDB (Port 27017)
 * - System resources
 */

// Load environment variables from config/.env
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../config/.env') });

const express = require('express');
const cors = require('cors');
const http = require('http');
const https = require('https');
const { MongoClient } = require('mongodb');
const os = require('os');
const axios = require('axios');
const AdminComPort = require('./admin-com-port');

// Shared HTTPS agent for proxy requests — prevents socket accumulation
const sharedApiAgent = new https.Agent({
    rejectUnauthorized: false,
    keepAlive: true,
    maxSockets: 10,
    maxFreeSockets: 5,
    timeout: 15000
});

const app = express();
const ADMIN_PANEL_PORT = process.env.PORT_ADMIN_PANEL || 3001;
const BIND_IP = process.env.BIND_IP || '0.0.0.0'; // Bind to all interfaces for health checks
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';

// Configuration
const API_PORT = process.env.PORT_API || 443;  // Changed from 80 to 443 (default HTTPS port)
const WS_PORT = process.env.PORT_WEBSOCKET || 8080;
const PEER_PORT = process.env.PORT_PEER || 9080;
const DOMAIN = process.env.DOMAIN || 'localhost';

// Initialize AdminComPort for secure admin-server communication
const adminComPort = new AdminComPort({
    adminSecret: process.env.ADMIN_SECRET || 'change-this-in-production',
    adminToken: process.env.ADMIN_TOKEN || 'admin-token-change-me',
    jwtSecret: process.env.JWT_SECRET || null,
    timestampWindowSeconds: 300, // 5 minutes (in seconds, not milliseconds)
    rateLimitRequests: 100,
    rateLimitWindow: 60000, // 1 minute in milliseconds
    auditLogPath: path.join(__dirname, '../logs/admin-audit.log')
});

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(__dirname));

// Log all /api/admin/ requests
const fs = require('fs');
const logPath = path.join(__dirname, '../logs/request-debug.log');
app.use('/api/admin/', (req, res, next) => {
    fs.writeFileSync(logPath, `[${new Date().toISOString()}] REQ: ${req.method} ${req.path}\n`, {flag: 'a'});
    next();
});

// Trust X-Forwarded-For headers for logging real client IPs
app.use((req, res, next) => {
    req.clientIP = req.headers['x-forwarded-for'] || req.connection.remoteAddress;
    next();
});

// Service health tracking
const serviceStatus = {
    api: { online: false, lastCheck: null, responseTime: 0 },
    websocket: { online: false, lastCheck: null, responseTime: 0 },
    peer: { online: false, lastCheck: null, responseTime: 0 },
    mongodb: { online: false, lastCheck: null, responseTime: 0 },
    adminPanel: { online: true, uptime: Date.now() }
};

// Utility: Check if port is responding (supports both HTTP and HTTPS)
async function checkServiceHealth(host, port, path = '/health') {
    return new Promise((resolve) => {
        const startTime = Date.now();
        // All service ports (443, 8080, 9080) use HTTPS - only use HTTP for MongoDB
        const isHttps = port !== 27017;
        const protocol = isHttps ? https : http;
        
        const options = {
            hostname: host,
            port: port,
            path: path,
            method: 'GET',
            timeout: 5000,
            rejectUnauthorized: false // Accept self-signed certificates
        };
        
        const req = protocol.request(options, (res) => {
            const responseTime = Date.now() - startTime;
            const isOnline = res.statusCode >= 200 && res.statusCode < 400;
            
            // Debug logging
            console.log(`[HEALTH CHECK] ${host}:${port}${path} -> Status: ${res.statusCode} (${isOnline ? 'OK' : 'FAIL'})`);
            
            if (isOnline) {
                resolve({ online: true, responseTime, statusCode: res.statusCode });
            } else {
                resolve({ online: false, responseTime, statusCode: res.statusCode });
            }
        });
        
        req.on('error', (err) => {
            console.log(`[HEALTH CHECK] ${host}:${port}${path} -> ERROR: ${err.message}`);
            resolve({ online: false, responseTime: Date.now() - startTime, error: true });
        });
        
        req.on('timeout', () => {
            console.log(`[HEALTH CHECK] ${host}:${port}${path} -> TIMEOUT`);
            req.destroy();
            resolve({ online: false, responseTime: Date.now() - startTime, timeout: true });
        });
        
        req.end();
    });
}

// Routes

// HTML dashboard
app.get('/', (req, res) => {
    const htmlPath = path.join(__dirname, 'admin-dashboard.html');
    res.sendFile(htmlPath, (err) => {
        if (err) {
            console.error('Error sending admin-dashboard.html:', err);
            res.status(500).json({ error: 'Admin panel HTML not found' });
        }
    });
});

// Admin panel health
app.get('/health', (req, res) => {
    res.json({
        status: 'online',
        service: 'admin-panel',
        port: ADMIN_PANEL_PORT,
        boundTo: BIND_IP,
        uptime: process.uptime(),
        timestamp: new Date().toISOString(),
        message: `Admin Panel running on ${BIND_IP}:${ADMIN_PANEL_PORT}`
    });
});

// **NEW: Comprehensive Service Monitoring Endpoint (Protected)
app.get('/api/admin/monitor', adminComPort.middleware(), async (req, res) => {
    try {
        // Log admin action
        adminComPort.logAdminAction({
            adminId: req.adminId,
            action: 'monitor_all_services',
            endpoint: '/api/admin/monitor',
            ip: req.clientIP,
            timestamp: new Date()
        });

        // Check all services in parallel
        const [apiHealth, wsHealth, peerHealth, mongoHealth] = await Promise.all([
            checkServiceHealth('127.0.0.1', API_PORT, '/health'),
            checkServiceHealth('127.0.0.1', WS_PORT, '/health'),
            checkServiceHealth('127.0.0.1', PEER_PORT, '/health'),
            checkServiceHealth('127.0.0.1', 27017, null) // MongoDB
        ]);
        
        // Update service status
        serviceStatus.api = { online: apiHealth.online, lastCheck: new Date(), ...apiHealth };
        serviceStatus.websocket = { online: wsHealth.online, lastCheck: new Date(), ...wsHealth };
        serviceStatus.peer = { online: peerHealth.online, lastCheck: new Date(), ...peerHealth };
        serviceStatus.mongodb = { online: mongoHealth.online, lastCheck: new Date(), ...mongoHealth };
        
        // Get system info
        const systemInfo = {
            uptime: os.uptime(),
            cpus: os.cpus().length,
            totalMemory: os.totalmem(),
            freeMemory: os.freemem(),
            platform: os.platform(),
            nodeVersion: process.version
        };
        
        res.json({
            timestamp: new Date().toISOString(),
            services: serviceStatus,
            system: systemInfo,
            architecture: {
                api: `https://localhost:${API_PORT} (HTTPS/TLS encrypted)`,
                websocket: `wss://localhost:${WS_PORT}/ws (Real-time chat, encrypted)`,
                peer: `wss://localhost:${PEER_PORT} (Peer discovery, encrypted)`,
                mongodb: `mongodb://localhost:27017/freetime`,
                adminPanel: `http://${BIND_IP}:${ADMIN_PANEL_PORT} (Admin control panel)`
            },
            summary: {
                totalServices: 5,
                onlineServices: [
                    serviceStatus.api.online,
                    serviceStatus.websocket.online,
                    serviceStatus.peer.online,
                    serviceStatus.mongodb.online,
                    serviceStatus.adminPanel.online
                ].filter(Boolean).length,
                allOnline: [
                    serviceStatus.api.online,
                    serviceStatus.websocket.online,
                    serviceStatus.peer.online,
                    serviceStatus.mongodb.online
                ].every(Boolean)
            }
        });
    } catch (error) {
        res.status(500).json({
            error: 'Failed to check service health',
            message: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

// **NEW: Get individual service status (Protected)
app.get('/api/admin/monitor/api', adminComPort.middleware(), async (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'check_api_service',
        endpoint: '/api/admin/monitor/api',
        ip: req.clientIP
    });
    const health = await checkServiceHealth('127.0.0.1', API_PORT, '/health');
    res.json({ service: 'api', port: API_PORT, ...health, lastCheck: new Date() });
});

app.get('/api/admin/monitor/websocket', adminComPort.middleware(), async (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'check_websocket_service',
        endpoint: '/api/admin/monitor/websocket',
        ip: req.clientIP
    });
    const health = await checkServiceHealth('127.0.0.1', WS_PORT, '/health');
    res.json({ service: 'websocket', port: WS_PORT, ...health, lastCheck: new Date() });
});

app.get('/api/admin/monitor/peer', adminComPort.middleware(), async (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'check_peer_service',
        endpoint: '/api/admin/monitor/peer',
        ip: req.clientIP
    });
    const health = await checkServiceHealth('127.0.0.1', PEER_PORT, '/health');
    res.json({ service: 'peer', port: PEER_PORT, ...health, lastCheck: new Date() });
});

app.get('/api/admin/monitor/mongodb', adminComPort.middleware(), async (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'check_mongodb_service',
        endpoint: '/api/admin/monitor/mongodb',
        ip: req.clientIP
    });
    const health = await checkServiceHealth('127.0.0.1', 27017, null);
    res.json({ service: 'mongodb', port: 27017, ...health, lastCheck: new Date() });
});

// **NEW: Get system statistics (Protected)
app.get('/api/admin/system', adminComPort.middleware(), (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'retrieve_system_stats',
        endpoint: '/api/admin/system',
        ip: req.clientIP
    });
    res.json({
        uptime: process.uptime(),
        nodeVersion: process.version,
        platform: os.platform(),
        arch: os.arch(),
        cpus: os.cpus().length,
        totalMemory: os.totalmem(),
        freeMemory: os.freemem(),
        usedMemory: os.totalmem() - os.freemem(),
        memoryPercent: ((os.totalmem() - os.freemem()) / os.totalmem() * 100).toFixed(2),
        timestamp: new Date().toISOString()
    });
});

// **NEW: Get server configuration (Protected)
app.get('/api/admin/config', adminComPort.middleware(), (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'retrieve_server_config',
        endpoint: '/api/admin/config',
        ip: req.clientIP
    });
    res.json({
        ports: {
            api: API_PORT,
            adminPanel: ADMIN_PANEL_PORT,
            websocket: WS_PORT,
            peer: PEER_PORT,
            mongodb: 27017
        },
        addresses: {
            api: `http://localhost:${API_PORT}`,
            websocket: `ws://localhost:${WS_PORT}`,
            peer: `ws://localhost:${PEER_PORT}`,
            mongodb: 'mongodb://localhost:27017',
            adminPanel: `http://${BIND_IP}:${ADMIN_PANEL_PORT}`
        },
        domain: DOMAIN,
        environment: process.env.NODE_ENV || 'development',
        timestamp: new Date().toISOString()
    });
});

// **NEW: Audit Log Endpoint (Protected)
app.get('/api/admin/audit-logs', adminComPort.middleware(), (req, res) => {
    adminComPort.logAdminAction({
        adminId: req.adminId,
        action: 'retrieve_audit_logs',
        endpoint: '/api/admin/audit-logs',
        ip: req.clientIP
    });
    res.json(adminComPort.getStats());
});

// ==================== API ENDPOINTS ====================

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ 
        status: 'ok', 
        service: 'admin-panel', 
        port: ADMIN_PANEL_PORT,
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

// ==================== PROXY ROUTES TO MAIN API SERVER ====================
// Forward authentication and management endpoints to the main API server
const proxyAdminRequests = async (req, res) => {
    const fs = require('fs');
    try {
        fs.writeFileSync('logs/proxy-debug.log', `[${new Date().toISOString()}] PROXY START ${req.method} ${req.originalUrl}\n`, {flag: 'a'});
        
        const apiUrl = `https://127.0.0.1:${API_PORT}${req.originalUrl}`;
        
        fs.writeFileSync('logs/proxy-debug.log', `[${new Date().toISOString()}] Target URL: ${apiUrl}\n`, {flag: 'a'});
        
        // Forward headers
        const headers = { ...req.headers };
        delete headers['host'];
        headers['content-type'] = 'application/json';
        delete headers['content-length'];
        
        fs.writeFileSync('logs/proxy-debug.log', `[${new Date().toISOString()}] Calling axios...\n`, {flag: 'a'});
        
        const response = await axios({
            method: req.method.toLowerCase(),
            url: apiUrl,
            data: (req.method !== 'GET' && req.method !== 'HEAD') ? req.body : undefined,
            headers: headers,
            validateStatus: () => true,
            timeout: 15000,
            httpsAgent: sharedApiAgent
        });
        
        fs.writeFileSync('logs/proxy-debug.log', `[${new Date().toISOString()}] Got response ${response.status}\n`, {flag: 'a'});
        
        if (response.status >= 500) {
            console.error(`[ERROR] API Server returned ${response.status} for ${req.method} ${req.originalUrl}`);
            if (response.data) console.error(`[ERROR] API Server error detail:`, JSON.stringify(response.data).substring(0, 200));
        }
        
        // Copy response headers
        Object.keys(response.headers).forEach(key => {
            if (!['content-encoding', 'transfer-encoding'].includes(key.toLowerCase())) {
                res.setHeader(key, response.headers[key]);
            }
        });
        
        res.status(response.status).send(response.data);
        
    } catch (error) {
        fs.writeFileSync('logs/proxy-debug.log', `[${new Date().toISOString()}] PROXY CAUGHT ERROR: ${error.code} - ${error.message}\n`, {flag: 'a'});
        res.status(503).json({
            error: 'Service unavailable',
            message: 'Failed to reach API server',
            details: error.message
        });
    }
};

// ==================== PROXY ROUTES TO MAIN API SERVER ====================
// Test route to verify request is reaching the app
app.post('/api/admin/login', (req, res) => {
    console.log('[TEST ROUTE] /api/admin/login received, forwarding to proxy handler');
    proxyAdminRequests(req, res);
});

// Register proxy routes to forward authentication and management endpoints to the main API server
// Includes: login, verify, logout, users, peers, monthly-stats, logs, calls, stats, badges, and any other admin endpoints
app.all(/^\/api\/admin\/.*/, proxyAdminRequests);
app.all(/^\/api\/(logout|verify)$/, proxyAdminRequests);

// Error handling middleware
app.use((err, req, res, next) => {
    console.error('Admin Panel Error:', err);
    
    // Log auth errors
    if (err.message && err.message.includes('signature')) {
        adminComPort.logAdminAction({
            adminId: 'unknown',
            action: 'failed_auth_attempt',
            endpoint: req.path,
            ip: req.clientIP,
            error: err.message
        });
    }
    
    res.status(500).json({ error: 'Internal server error', message: err.message });
});

// 404 handler
app.use((req, res) => {
    res.status(404).json({ error: 'Not found', path: req.path });
});

// Start server
const server = app.listen(ADMIN_PANEL_PORT, BIND_IP, () => {
    console.log(`
╔════════════════════════════════════════════════════════════════╗
║  🖥️  FreeTime Admin Panel - Full Service Monitoring            ║
║  � SECURE: AdminComPort Authentication Enabled                ║
║  📡 IP: ${BIND_IP}                                                ║
║  📡 Port: ${ADMIN_PANEL_PORT}                                      ║
║  🌐 Access: http://${BIND_IP}:${ADMIN_PANEL_PORT}                     ║
║  [WARN]  Local LAN only (192.168.x.x network)                      ║
║                                                                ║
║  🔐 SECURITY FEATURES:                                         ║
║     • HMAC-SHA256 Request Signing                             ║
║     • Timestamp Validation (5-min window)                     ║
║     • Admin Token Authentication                             ║
║     • Rate Limiting: 100 req/60s per admin                    ║
║     • Comprehensive Audit Logging                            ║
║     • Replay Attack Prevention                                ║
║                                                                ║
║  📊 Protected Monitoring Endpoints:                           ║
║     • GET /api/admin/monitor - All services                   ║
║     • GET /api/admin/monitor/api - API service               ║
║     • GET /api/admin/monitor/websocket - WebSocket           ║
║     • GET /api/admin/monitor/peer - Peer network             ║
║     • GET /api/admin/monitor/mongodb - MongoDB               ║
║     • GET /api/admin/system - System metrics                 ║
║     • GET /api/admin/config - Server config                  ║
║     • GET /api/admin/audit-logs - Audit trail                ║
║                                                                ║
║  📡 Service Monitoring (Unprotected Health Checks):           ║
║     • API Server:     http://localhost:${API_PORT}/health        ║
║     • WebSocket:      http://localhost:${WS_PORT}/health         ║
║     • Peer Network:   http://localhost:${PEER_PORT}/health       ║
║     • MongoDB:        mongodb://localhost:27017              ║
║                                                                ║
║  💡 USAGE: All admin endpoints require HMAC-signed requests    ║
║     See AdminComPort documentation for request signing       ║
╚════════════════════════════════════════════════════════════════╝
    `);
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('[INFO] Admin Panel: SIGTERM received, shutting down gracefully');
    server.close(() => {
        console.log('[OK] Admin Panel: Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('[INFO] Admin Panel: SIGINT received, shutting down gracefully');
    server.close(() => {
        console.log('[OK] Admin Panel: Server closed');
        process.exit(0);
    });
});

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
    console.error('[ERROR] Uncaught Exception in Admin Panel:', error);
    process.exit(1);
});

module.exports = { app, server, serviceStatus, adminComPort };
