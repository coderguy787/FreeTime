const http = require('http');
const fs = require('fs');
const path = require('path');

const ADMIN_PORT = 3001;

const adminServer = http.createServer((req, res) => {
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

        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                const { username, password } = data;

                // demo credentials, real auth is in master-server-api
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
        const users = [
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
        ];

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(users));
        return;
    }

    if (path === '/api/stats' && method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            totalUsers: 2,
            onlineUsers: 1,
            activeConnections: 2,
            serverUptime: '2h 15m',
            memoryUsage: 45.2,
            cpuUsage: 12.5
        }));
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

adminServer.listen(ADMIN_PORT, '0.0.0.0', () => {
    console.log(`Basic Admin API Server running on port ${ADMIN_PORT}`);
    console.log(`Admin panel accessible at: http://localhost:${ADMIN_PORT}`);
});
