const http = require('http');
const fs = require('fs');
const pathModule = require('path');
const { MongoClient } = require('mongodb');
require('dotenv').config({ path: pathModule.join(__dirname, '../config/.env') });

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

    if (path === '/api/admin/users' && method === 'GET') {
        const token = req.headers.authorization?.replace('Bearer ', '');

        if (!token) {
            res.writeHead(401, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Unauthorized' }));
            return;
        }

        try {
            const users = await usersCollection.find(
                { isActive: true },
                {
                    projection: {
                        password: 0,
                        _id: 0,
                        tags: 1,
                        status: 1,
                        isOnline: 1,
                        lastLogin: 1,
                        username: 1,
                        displayName: 1,
                        role: 1,
                        email: 1
                    }
                }
            ).toArray();

            const response = users.map(user => {
                const now = new Date();
                const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000;

                // status strings must match the android panel
                let status = 'OFFLINE';
                if (user.isActive) {
                    if (isOnline) {
                        status = 'ONLINE';
                    } else if (lastLogin && (now - lastLogin) < 24 * 60 * 60 * 1000) {
                        status = 'RECENTLY_ONLINE';
                    }
                }

                return {
                    ...user,
                    isOnline: isOnline,
                    status: status
                };
            });

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(response));
        } catch (error) {
            console.error('Error fetching users:', error);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to fetch users' }));
        }
    } else if (path === '/api/auth/login' && method === 'POST') {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
        });

        req.on('end', async () => {
            try {
                const data = JSON.parse(body);
                const { username, password } = data;

                const user = await usersCollection.findOne({
                    username: username,
                    isActive: true
                });

                if (!user) {
                    res.writeHead(401, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Invalid credentials' }));
                    return;
                }

                if (username === 'server' && password === 'admin123') {
                    const token = 'demo-token-' + Date.now();
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({
                        token: token,
                        user: {
                            id: user.id || user._id,
                            username: user.username,
                            displayName: user.displayName,
                            role: user.role
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
    } else if (path === '/health' && method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'ok',
            database: 'connected',
            timestamp: new Date().toISOString()
        }));
    } else if (path === '/api/stats' && method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            totalUsers: 3,
            onlineUsers: 1,
            activeConnections: 2,
            serverUptime: '2h 15m',
            memoryUsage: 45.2,
            cpuUsage: 12.5
        }));
    } else if (path === '/api/users' && method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify([
            {
                _id: "695a775878f244bb18b08114",
                username: "server",
                displayName: "Server Administrator",
                role: "admin",
                email: "admin@securechat.local",
                isActive: true,
                isOnline: true,
                status: "ONLINE",
                tags: ["owner", "administrator"],
                lastLogin: new Date().toISOString()
            },
            {
                _id: "695544d9f98c301d5d8de666",
                username: "androidtest",
                displayName: "Android Test User",
                role: "user",
                email: "android@securechat.local",
                isActive: true,
                isOnline: false,
                status: "OFFLINE",
                tags: ["verified"],
                lastLogin: "2026-01-04T15:44:25.909Z"
            }
        ]));
    } else if (path === '/api/users' && method === 'POST') {
        res.writeHead(201, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            message: "User created successfully",
            user: {
                _id: "new-user-" + Date.now(),
                username: "newuser",
                displayName: "New User",
                role: "user",
                isActive: true
            }
        }));
    } else if (path === '/api/users/' && method === 'PUT') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            message: "User updated successfully"
        }));
    } else if (path === '/api/users/' && method === 'DELETE') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            message: "User deleted successfully"
        }));
    } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Not Found' }));
    }
    if (method === 'GET' && (path === '/' || path === '/admin-panel.html' || path === '/admin.html')) {
        const filePath = pathModule.join(__dirname, 'public', 'admin-live.html');

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
    } else if (method === 'GET' && path.startsWith('/')) {
        const filePath = pathModule.join(__dirname, 'public', path);

        fs.readFile(filePath, (err, content) => {
            if (err) {
                res.writeHead(404, { 'Content-Type': 'text/html' });
                res.end('<h1>File Not Found</h1>');
                return;
            }

            const ext = pathModule.extname(filePath);
            let contentType = 'text/html';
            if (ext === '.css') contentType = 'text/css';
            if (ext === '.js') contentType = 'application/javascript';
            if (ext === '.json') contentType = 'application/json';

            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        });
        return;
    } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Not Found' }));
    }
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
