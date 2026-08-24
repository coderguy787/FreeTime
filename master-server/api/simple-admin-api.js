const http = require('http');
const fs = require('fs');
const path = require('path');
const { MongoClient } = require('mongodb');
require('dotenv').config({ path: path.join(__dirname, '../config/.env') });

const ADMIN_PORT = process.env.ADMIN_PORT || 3001;
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/freetime';
const DB_NAME = process.env.DB_NAME || 'freetime';

let usersCollection;

async function initMongoDB() {
    try {
        const client = new MongoClient(MONGODB_URI);
        await client.connect();
        const db = client.db(DB_NAME);
        usersCollection = db.collection('users');
        console.log('Connected to MongoDB successfully');
        return true;
    } catch (error) {
        console.error('Failed to connect to MongoDB:', error);
        return false;
    }
}

const adminServer = http.createServer(async (req, res) => {
    const parsedUrl = require('url').parse(req.url, true);
    const path = parsedUrl.pathname;
    const method = req.method;

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    console.log(`[ADMIN] ${method} ${path}`);

    if (path === '/health' && method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'ok',
            database: 'connected',
            timestamp: new Date().toISOString()
        }));
        return;
    }

    if (path === '/api/auth/login' && method === 'POST') {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
        });

        req.on('end', async () => {
            try {
                const data = JSON.parse(body);
                const { username, password } = data;

                if (username === 'server' && password === 'admin123') {
                    const token = 'demo-token-' + Date.now();
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({
                        token: token,
                        user: {
                            id: 'server-admin',
                            username: username,
                            displayName: 'Server Administrator',
                            role: 'admin'
                        }
                    }));
                } else {
                    res.writeHead(401, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Invalid credentials' }));
                }

            } catch (error) {
                res.writeHead(500, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Login failed' }));
            }
        });
        return;
    }

    if (path === '/api/users' && method === 'GET') {
        try {
            const users = await usersCollection.find({ isActive: true }).toArray();

            // online = active in the last 5 min
            const response = users.map(user => {
                const now = new Date();
                const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000;

                return {
                    _id: user._id,
                    username: user.username,
                    displayName: user.displayName || user.username,
                    role: user.role || 'user',
                    email: user.email || '',
                    isActive: user.isActive,
                    isOnline: isOnline,
                    status: isOnline ? 'ONLINE' : 'OFFLINE',
                    tags: user.tags || [],
                    lastLogin: user.lastLogin || new Date().toISOString()
                };
            });

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(response));
        } catch (error) {
            console.error('Error fetching users:', error);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to fetch users' }));
        }
        return;
    }

    if (path === '/api/stats' && method === 'GET') {
        try {
            const users = await usersCollection.find({ isActive: true }).toArray();
            const onlineUsers = users.filter(user => {
                const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                return lastLogin && (new Date() - lastLogin) < 5 * 60 * 1000;
            });

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                totalUsers: users.length,
                onlineUsers: onlineUsers.length,
                activeConnections: 2,
                serverUptime: '2h 15m',
                memoryUsage: 45.2,
                cpuUsage: 12.5
            }));
        } catch (error) {
            console.error('Error fetching stats:', error);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to fetch stats' }));
        }
        return;
    }

    if (method === 'GET' && (path === '/' || path === '/admin-panel.html' || path === '/admin.html')) {
        const filePath = path.join(__dirname, 'public', 'admin-live.html');

        fs.readFile(filePath, (err, content) => {
            if (err) {
                res.writeHead(404, { 'Content-Type': 'text/html' });
                res.end('<h1>Admin Panel Not Found</h1>');
                return;
            }

            res.writeHead(200, { 'Content-Type': 'text/html' });
            res.end(content);
        });
        return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not Found' }));
});

async function startServer() {
    const mongoConnected = await initMongoDB();
    if (!mongoConnected) {
        console.error('Failed to connect to MongoDB. Exiting...');
        process.exit(1);
    }

    adminServer.listen(ADMIN_PORT, '0.0.0.0', () => {
        console.log(`Admin API Server running on port ${ADMIN_PORT}`);
        console.log(`Admin panel accessible at: http://localhost:${ADMIN_PORT}`);
    });
}

startServer().catch(console.error);
