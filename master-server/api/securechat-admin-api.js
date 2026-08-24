#!/usr/bin/env node

const http = require('http');
const url = require('url');
const { WebSocketServer } = require('ws');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const { MongoClient } = require('mongodb');
const bcrypt = require('bcryptjs');

const PORT = process.env.PORT || 8081;
const ADMIN_PORT = 3001;
const MONGODB_URI = 'mongodb://127.0.0.1:27017/securechat';
const MONGODB_DB = 'securechat';

let db;
let usersCollection;
let statsCollection;

const USER_ROLES = ['USER', 'MODERATOR', 'ADMIN', 'OWNER'];
const USER_TAGS = ['owner', 'admin', 'moderator', 'vip', 'verified', 'developer', 'support'];

async function initMongoDB() {
    try {
        const client = new MongoClient(MONGODB_URI);
        await client.connect();
        db = client.db(MONGODB_DB);

        usersCollection = db.collection('users');
        statsCollection = db.collection('statistics');

        await usersCollection.createIndex({ id: 1 }, { unique: true });
        await usersCollection.createIndex({ username: 1 }, { unique: true, sparse: true });
        await usersCollection.createIndex({ email: 1 }, { unique: true, sparse: true });
        await usersCollection.createIndex({ publicTag: 1 }, { unique: true, sparse: true });

        console.log('Connected to MongoDB successfully');

        await initializeDefaultAdmin();

        return true;
    } catch (error) {
        console.error('Failed to connect to MongoDB:', error);
        return false;
    }
}

async function initializeDefaultAdmin() {
    try {
        const existingAdmin = await usersCollection.findOne({ role: 'OWNER' });
        if (!existingAdmin) {
            const hashedPassword = await bcrypt.hash('Bubufuz42', 10);
            const adminUser = {
                id: uuidv4(),
                name: 'System Owner',
                email: 'owner@securechat.local',
                password: hashedPassword,
                role: 'OWNER',
                tags: ['owner'],
                status: 'ONLINE',
                isOnline: true,
                lastSeen: new Date(),
                phoneNumber: null,
                publicTag: '@owner',
                avatarUrl: null,
                createdAt: new Date(),
                updatedAt: new Date(),
                isActive: true,
                isVerified: true
            };

            const adminCreateResult = await usersCollection.insertOne(adminUser);
            if (!adminCreateResult.insertedId) {
                throw new Error('Failed to create default admin user');
            }
            console.log('Default admin user created');
        }
    } catch (error) {
        console.error('Failed to initialize default admin:', error);
    }
}

// admin sessions are memory-only, restart logs everyone out
function generateToken(user) {
    return crypto.randomBytes(32).toString('hex');
}

function validateUsername(username) {
    return /^[a-zA-Z0-9_]{3,20}$/.test(username);
}

function validateEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function validatePublicTag(tag) {
    return /^@[a-zA-Z0-9_]{2,19}$/.test(tag);
}

async function updateStatistics() {
    try {
        const totalUsers = await usersCollection.countDocuments({ isActive: true });
        const onlineUsers = await usersCollection.countDocuments({ isOnline: true, isActive: true });
        const roleStats = await usersCollection.aggregate([
            { $match: { isActive: true } },
            { $group: { _id: '$role', count: { $sum: 1 } } }
        ]).toArray();
        const tagStats = await usersCollection.aggregate([
            { $match: { isActive: true } },
            { $unwind: '$tags' },
            { $group: { _id: '$tags', count: { $sum: 1 } } }
        ]).toArray();

        const stats = {
            timestamp: new Date(),
            totalUsers,
            onlineUsers,
            roleStats: roleStats.reduce((acc, stat) => {
                acc[stat._id] = stat.count;
                return acc;
            }, {}),
            tagStats: tagStats.reduce((acc, stat) => {
                acc[stat._id] = stat.count;
                return acc;
            }, {})
        };

        await statsCollection.replaceOne(
            { _id: 'current' },
            stats,
            { upsert: true }
        );

        return stats;
    } catch (error) {
        console.error('Failed to update statistics:', error);
        return null;
    }
}

const server = http.createServer(async (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const path = parsedUrl.pathname;
    const method = req.method;

    const requestPort = req.socket.localPort;

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    console.log(`[${requestPort === ADMIN_PORT ? 'ADMIN' : 'API'}] ${method} ${path}`);

    const startTime = Date.now();
    let body = '';
    req.on('data', chunk => {
        body += chunk.toString();
    });

    req.on('end', async () => {
        let data = null;
        if (body && method !== 'GET') {
            try {
                data = JSON.parse(body);
            } catch (e) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid JSON' }));
                return;
            }
        }

        let response = null;
        let statusCode = 200;

        if (path === '/health' || path === '/api/health') {
            response = {
                status: 'ok',
                timestamp: new Date().toISOString(),
                mongodb: db ? 'connected' : 'disconnected'
            };
        }

        else if (path === '/api/admin/login' && method === 'POST') {
            const { username, password } = data;
            try {
                const user = await usersCollection.findOne({
                    $or: [
                        { email: username },
                        { username: username },
                        { publicTag: username },
                        { name: username }
                    ]
                });

                if (user && await bcrypt.compare(password, user.password)) {
                    const token = generateToken();
                    sessions.set(token, {
                        userId: user.id,
                        role: user.role,
                        username: user.name
                    });

                    await usersCollection.updateOne(
                        { id: user.id },
                        {
                            $set: {
                                lastLogin: new Date(),
                                isOnline: true
                            }
                        }
                    );

                    response = {
                        token,
                        user: {
                            id: user.id,
                            name: user.name,
                            email: user.email,
                            role: user.role,
                            tags: user.tags,
                            publicTag: user.publicTag
                        }
                    };
                } else {
                    statusCode = 401;
                    response = { error: 'Invalid credentials' };
                }
            } catch (error) {
                statusCode = 500;
                response = { error: 'Login failed' };
            }
        }

        else if (path === '/api/admin/verify' && method === 'GET') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { valid: false, error: 'Invalid token' };
            } else {
                response = {
                    valid: true,
                    user: {
                        id: session.userId,
                        username: session.username,
                        role: session.role
                    }
                };
            }
        }

        else if (path === '/api/admin/stats' && method === 'GET') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const stats = await updateStatistics();
                response = stats || { error: 'Failed to get statistics' };
            }
        }

        else if (path === '/api/admin/users' && method === 'GET') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            console.log(`Token received: ${token ? token.substring(0, 10) + '...' : 'none'}`);
            console.log(`Sessions stored: ${sessions.size}`);
            console.log(`Session valid: ${session ? 'yes' : 'no'}`);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
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
                                lastLogin: 1
                            }
                        }
                    ).toArray();
                    response = users.map(user => {
                        const now = new Date();
                        const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                        const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000;

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
                } catch (error) {
                    statusCode = 500;
                    response = { error: 'Failed to fetch users' };
                }
            }
        }

        else if (path === '/api/admin/users' && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                try {
                    const { name, username, email, phoneNumber, publicTag, role, tags, password } = data;

                    if (!name || !username) {
                        statusCode = 400;
                        response = { error: 'Name and username are required' };
                    } else if (!validateUsername(username)) {
                        statusCode = 400;
                        response = { error: 'Invalid username format (3-20 chars, letters/numbers/_)' };
                    } else if (email && !validateEmail(email)) {
                        statusCode = 400;
                        response = { error: 'Invalid email format' };
                    } else {
                        const existingUser = await usersCollection.findOne({ username, isActive: true });

                        if (existingUser) {
                            statusCode = 409;
                            response = { error: 'Username already exists' };
                        } else if (email) {
                            const existingEmail = await usersCollection.findOne({ email, isActive: true });
                            if (existingEmail) {
                                statusCode = 409;
                                response = { error: 'Email already exists' };
                            } else {
                                await createUser();
                            }
                        } else {
                            await createUser();
                        }

                        async function createUser() {
                            if (publicTag && !validatePublicTag(publicTag)) {
                                statusCode = 400;
                                response = { error: 'Invalid public tag format' };
                                return;
                            }

                            if (publicTag) {
                                const existingTag = await usersCollection.findOne({ publicTag, isActive: true });
                                if (existingTag) {
                                    statusCode = 409;
                                    response = { error: 'Public tag already exists' };
                                    return;
                                }
                            }

                            const existingInactiveUser = await usersCollection.findOne({
                                $or: [
                                    { username },
                                    ...(email ? [{ email }] : []),
                                    ...(publicTag ? [{ publicTag }] : [])
                                ],
                                isActive: false
                            });

                            if (existingInactiveUser) {
                                const updatedData = {
                                    name: name.trim(),
                                    username: username.trim(),
                                    email: email?.trim() || null,
                                    phoneNumber: phoneNumber?.trim() || null,
                                    publicTag: publicTag?.trim() || null,
                                    role: role || 'USER',
                                    tags: tags || [],
                                    status: 'OFFLINE',
                                    isOnline: false,
                                    lastSeen: new Date(),
                                    updatedAt: new Date(),
                                    isActive: true,
                                    isVerified: role === 'OWNER',
                                    deletedAt: null,
                                    twoFactorAuth: {
                                        enabled: false,
                                        method: null,
                                        secret: null,
                                        accountVerified: false,
                                        mandatorySetup: false,
                                        backupCodes: []
                                    }
                                };

                                if (password && password.trim()) {
                                    updatedData.password = await bcrypt.hash(password, 10);
                                }

                                const reactivateResult = await usersCollection.updateOne(
                                    { id: existingInactiveUser.id },
                                    { $set: updatedData }
                                );
                                if (reactivateResult.matchedCount === 0) {
                                    return res.status(500).json({ error: 'Failed to reactivate user' });
                                }

                                const refreshedUser = await usersCollection.findOne(
                                    { id: existingInactiveUser.id },
                                    { projection: { password: 0, _id: 0 } }
                                );

                                response = refreshedUser;
                                return;
                            }

                            const userId = uuidv4();
                            const hashedPassword = await bcrypt.hash(password || 'Bubufuz42', 10);

                            const newUser = {
                                id: userId,
                                name: name.trim(),
                                username: username.trim(),
                                email: email?.trim() || null,
                                phoneNumber: phoneNumber?.trim() || null,
                                publicTag: publicTag?.trim() || null,
                                password: hashedPassword,
                                role: role || 'USER',
                                tags: tags || [],
                                status: 'OFFLINE',
                                isOnline: false,
                                lastSeen: new Date(),
                                createdAt: new Date(),
                                updatedAt: new Date(),
                                isActive: true,
                                isVerified: role === 'OWNER',
                                avatarUrl: null,
                                twoFactorAuth: {
                                    enabled: false,
                                    method: null,
                                    secret: null,
                                    accountVerified: false,
                                    mandatorySetup: false,
                                    backupCodes: []
                                }
                            };

                            const createResult = await usersCollection.insertOne(newUser);
                            if (!createResult.insertedId) {
                                throw new Error('Failed to create user - database error');
                            }

                            await updateStatistics();

                            const { password: _, ...userResponse } = newUser;
                            response = userResponse;
                        }
                    }
                } catch (error) {
                    if (error.code === 11000) {
                        statusCode = 409;
                        response = { error: 'Username, email, or public tag already exists' };
                    } else {
                        statusCode = 500;
                        response = { error: 'Failed to create user' };
                    }
                }
            }
        }

        else if (path.startsWith('/api/admin/users/') && method === 'PUT') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const userId = path.split('/').pop();

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                try {
                    const { name, username, email, phoneNumber, publicTag, role, tags, status, password } = data;
                    const user = await usersCollection.findOne({ id: userId });

                    if (!user) {
                        statusCode = 404;
                        response = { error: 'User not found' };
                    } else {
                        if (username && username !== user.username) {
                            if (!validateUsername(username)) {
                                statusCode = 400;
                                response = { error: 'Invalid username format (3-20 chars, letters/numbers/_)' };
                                return;
                            }
                            const existingUsername = await usersCollection.findOne({ username });
                            if (existingUsername) {
                                statusCode = 400;
                                response = { error: 'Username already taken' };
                                return;
                            }
                        }

                        if (email && email !== user.email) {
                            if (!validateEmail(email)) {
                                statusCode = 400;
                                response = { error: 'Invalid email format' };
                                return;
                            }
                            const existingEmail = await usersCollection.findOne({ email });
                            if (existingEmail) {
                                statusCode = 400;
                                response = { error: 'Email already taken' };
                                return;
                            }
                        }

                        if (publicTag && !validatePublicTag(publicTag)) {
                            statusCode = 400;
                            response = { error: 'Invalid public tag format' };
                            return;
                        } else if (publicTag && publicTag !== user.publicTag) {
                            const existingTag = await usersCollection.findOne({ publicTag });
                            if (existingTag) {
                                statusCode = 400;
                                response = { error: 'Public tag already exists' };
                                return;
                            }
                        }

                        if (statusCode === 200) {
                            const updateData = {
                                updatedAt: new Date()
                            };

                            if (name) updateData.name = name;
                            if (username) updateData.username = username;
                            if (email) updateData.email = email;
                            if (phoneNumber !== undefined) updateData.phoneNumber = phoneNumber || null;
                            if (publicTag) updateData.publicTag = publicTag;
                            if (role) updateData.role = role;
                            if (tags) updateData.tags = tags;
                            if (status) updateData.status = status;

                            if (password && password.trim()) {
                                try {
                                    updateData.password = await bcrypt.hash(password, 10);
                                } catch (hashError) {
                                    statusCode = 500;
                                    response = { error: 'Failed to hash password' };
                                    return;
                                }
                            }

                            if (!user.twoFactorAuth) {
                                updateData.twoFactorAuth = {
                                    enabled: false,
                                    method: null,
                                    secret: null,
                                    accountVerified: false,
                                    mandatorySetup: false,
                                    backupCodes: []
                                };
                            }

                            await usersCollection.updateOne(
                                { id: userId },
                                { $set: updateData }
                            );

                            const updatedUser = await usersCollection.findOne(
                                { id: userId },
                                { projection: { password: 0, _id: 0 } }
                            );

                            response = updatedUser;
                        }
                    }
                } catch (error) {
                    console.error('Error updating user:', error);
                    statusCode = 500;
                    response = { error: 'Failed to update user: ' + error.message };
                }
            }
        }

        else if (path.startsWith('/api/admin/users/') && method === 'DELETE') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const userId = path.split('/').pop();

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                try {
                    const user = await usersCollection.findOne({ id: userId });
                    if (!user) {
                        statusCode = 404;
                        response = { error: 'User not found' };
                    } else if (user.role === 'OWNER') {
                        statusCode = 403;
                        response = { error: 'Cannot delete owner account' };
                    } else {
                        await usersCollection.updateOne(
                            { id: userId },
                            {
                                $set: {
                                    isActive: false,
                                    updatedAt: new Date(),
                                    deletedAt: new Date()
                                }
                            }
                        );
                        response = { message: 'User deleted successfully' };
                    }
                } catch (error) {
                    statusCode = 500;
                    response = { error: 'Failed to delete user' };
                }
            }
        }

        else if (path.match(/^\/api\/admin\/users\/[^/]+\/toggle-2fa$/) && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const userId = path.split('/')[4];
            const { enabled } = data;

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else if (enabled === undefined) {
                statusCode = 400;
                response = { error: 'enabled field required (true/false)' };
            } else {
                try {
                    const user = await usersCollection.findOne({ id: userId });
                    if (!user) {
                        statusCode = 404;
                        response = { error: 'User not found' };
                    } else {
                        const currentTwoFA = user.twoFactorAuth || {
                            enabled: false,
                            method: null,
                            secret: null,
                            accountVerified: false,
                            mandatorySetup: false,
                            backupCodes: []
                        };

                        const updatedTwoFA = {
                            ...currentTwoFA,
                            mandatorySetup: enabled,
                            enabled: enabled ? currentTwoFA.enabled : false,
                            accountVerified: enabled ? currentTwoFA.accountVerified : false,
                            secret: enabled ? currentTwoFA.secret : null,
                            method: enabled ? currentTwoFA.method : null,
                            backupCodes: enabled ? currentTwoFA.backupCodes : []
                        };

                        await usersCollection.updateOne(
                            { id: userId },
                            {
                                $set: {
                                    twoFactorAuth: updatedTwoFA,
                                    updatedAt: new Date()
                                }
                            }
                        );

                        const updatedUser = await usersCollection.findOne(
                            { id: userId },
                            { projection: { password: 0, _id: 0 } }
                        );

                        response = {
                            message: `2FA ${enabled ? 'enabled' : 'disabled'} for user`,
                            user: updatedUser
                        };
                    }
                } catch (error) {
                    console.error('Error toggling 2FA:', error);
                    statusCode = 500;
                    response = { error: 'Failed to toggle 2FA' };
                }
            }
        }

        else if (path === '/api/v1/auth/register' && method === 'POST') {
            const { username, email, password, displayName, phoneNumber } = data;

            try {
                const existingUser = await usersCollection.findOne({
                    $or: [
                        { email: email?.toLowerCase() },
                        { username: username?.toLowerCase() },
                        { publicTag: `@${username}` }
                    ],
                    isActive: true
                });

                if (existingUser) {
                    statusCode = 409;
                    response = { error: 'User already exists' };
                } else {
                    const userId = uuidv4();
                    const hashedPassword = await bcrypt.hash(password, 10);

                    const newUser = {
                        id: userId,
                        name: displayName || username,
                        username: username.toLowerCase(),
                        email: email?.toLowerCase() || null,
                        phoneNumber: phoneNumber || null,
                        publicTag: `@${username}`,
                        password: hashedPassword,
                        role: 'USER',
                        tags: [],
                        status: 'OFFLINE',
                        isOnline: false,
                        createdAt: new Date(),
                        updatedAt: new Date(),
                        isActive: true,
                        isVerified: false,
                        avatarUrl: null
                    };

                    await usersCollection.insertOne(newUser);

                    const token = generateToken();
                    sessions.set(token, {
                        userId: userId,
                        role: 'USER',
                        username: username,
                        type: 'android'
                    });

                    const apiUser = {
                        id: userId,
                        username: username,
                        email: email,
                        role: 'USER',
                        isAdmin: false,
                        displayName: displayName || username,
                        status: 'OFFLINE'
                    };

                    response = {
                        success: true,
                        token,
                        refreshToken: null,
                        user: apiUser,
                        requiresTwoFactor: false
                    };
                }
            } catch (error) {
                console.error('Registration error:', error);
                statusCode = 500;
                response = { error: 'Registration failed', details: error.message };
            }
        }

        else if (path === '/api/v1/auth/login' && method === 'POST') {
            const { username, password } = data;
            try {
                const user = await usersCollection.findOne({
                    $or: [
                        { email: username },
                        { publicTag: username },
                        { name: username },
                        { username: username.toLowerCase() }
                    ],
                    isActive: true
                });

                if (user && await bcrypt.compare(password, user.password)) {
                    const token = generateToken();
                    sessions.set(token, {
                        userId: user.id,
                        role: user.role,
                        username: user.name,
                        type: 'android'
                    });

                    await usersCollection.updateOne(
                        { id: user.id },
                        { $set: { lastLogin: new Date(), isOnline: true } }
                    );

                    const apiUser = {
                        id: user.id,
                        username: user.username || user.name.toLowerCase().replace(/\s+/g, ''),
                        email: user.email,
                        role: user.role,
                        isAdmin: user.role === 'ADMIN' || user.role === 'OWNER',
                        displayName: user.name,
                        status: user.status
                    };

                    response = {
                        success: true,
                        token,
                        refreshToken: null,
                        user: apiUser,
                        requiresTwoFactor: false
                    };
                } else {
                    statusCode = 401;
                    response = { error: 'Invalid credentials' };
                }
            } catch (error) {
                statusCode = 500;
                response = { error: 'Login failed' };
            }
        }

        else if (path === '/api/v1/users/me' && method === 'GET') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const user = await usersCollection.findOne(
                    { id: session.userId, isActive: true },
                    { projection: { password: 0, _id: 0 } }
                );

                if (user) {
                    response = {
                        id: user.id,
                        username: user.username || user.name.toLowerCase().replace(/\s+/g, ''),
                        email: user.email,
                        role: user.role,
                        isAdmin: user.role === 'ADMIN' || user.role === 'OWNER',
                        displayName: user.name,
                        status: user.status,
                        isOnline: user.isOnline,
                        avatarUrl: user.avatarUrl,
                        publicTag: user.publicTag,
                        tags: user.tags,
                        phoneNumber: user.phoneNumber,
                        lastSeen: user.lastSeen,
                        createdAt: user.createdAt,
                        updatedAt: user.updatedAt
                    };
                } else {
                    statusCode = 404;
                    response = { error: 'User not found' };
                }
            }
        }

        else if (path.startsWith('/api/v1/users/') && method === 'GET') {
            const userId = path.split('/').pop();
            const user = await usersCollection.findOne(
                { id: userId, isActive: true },
                { projection: { password: 0, _id: 0 } }
            );
            if (user) {
                response = user;
            } else {
                statusCode = 404;
                response = { error: 'User not found' };
            }
        }

        else if (path === '/api/v1/users' && method === 'GET') {
            const query = parsedUrl.query.query?.toLowerCase() || '';
            const users = await usersCollection.find(
                {
                    isActive: true,
                    $or: [
                        { name: { $regex: query, $options: 'i' } },
                        { email: { $regex: query, $options: 'i' } },
                        { publicTag: { $regex: query, $options: 'i' } },
                        { username: { $regex: query, $options: 'i' } }
                    ]
                },
                { projection: { password: 0, _id: 0 } }
            ).toArray();

            response = users.map(user => ({
                id: user.id,
                username: user.username || user.name.toLowerCase().replace(/\s+/g, ''),
                email: user.email,
                role: user.role,
                isAdmin: user.role === 'ADMIN' || user.role === 'OWNER',
                displayName: user.name,
                status: user.status,
                isOnline: user.isOnline,
                avatarUrl: user.avatarUrl,
                publicTag: user.publicTag,
                tags: user.tags,
                phoneNumber: user.phoneNumber,
                lastSeen: user.lastSeen,
                createdAt: user.createdAt,
                updatedAt: user.updatedAt
            }));
        }

        else if (path === '/api/v1/users/me' && method === 'PUT') {
            const { displayName, avatarUrl, status } = data;
            const user = await usersCollection.findOne({ isActive: true });
            if (user) {
                const updateData = { updatedAt: new Date() };
                if (displayName) updateData.name = displayName;
                if (avatarUrl) updateData.avatarUrl = avatarUrl;
                if (status) updateData.status = status;

                const demoUpdateResult = await usersCollection.updateOne(
                    { id: user.id },
                    { $set: updateData }
                );
                if (demoUpdateResult.matchedCount === 0) {
                    console.warn('Demo user not found for profile update');
                }

                const updatedUser = await usersCollection.findOne(
                    { id: user.id },
                    { projection: { password: 0, _id: 0 } }
                );
                response = updatedUser;
            } else {
                statusCode = 404;
                response = { error: 'User not found' };
            }
        }

        else if (path === '/api/v1/users/me' && method === 'DELETE') {
            response = {};
        }

        else if (path === '/api/v1/calls/initiate' && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const { toUserId, isVideoCall } = data;
                const callId = uuidv4();

                const callRecord = {
                    id: callId,
                    fromUserId: session.userId,
                    toUserId: toUserId,
                    isVideoCall: isVideoCall || false,
                    status: 'INITIATED',
                    createdAt: new Date(),
                    updatedAt: new Date()
                };

                if (!global.calls) global.calls = new Map();
                global.calls.set(callId, callRecord);

                const targetSession = Array.from(sessions.values()).find(s => s.userId === toUserId);
                if (targetSession) {
                    const targetWs = wsConnections.get(Array.from(sessions.entries()).find(([_, s]) => s.userId === toUserId)?.[0]);
                    if (targetWs && targetWs.readyState === targetWs.OPEN) {
                        targetWs.send(JSON.stringify({
                            type: 'incoming_call',
                            data: {
                                callId: callId,
                                fromUserId: session.userId,
                                fromUsername: session.username,
                                isVideoCall: isVideoCall || false
                            }
                        }));
                    }
                }

                response = {
                    success: true,
                    callId: callId,
                    status: 'INITIATED'
                };
            }
        }

        else if (path.startsWith('/api/v1/calls/') && path.endsWith('/accept') && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const callId = path.split('/')[3];

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const call = global.calls?.get(callId);
                if (!call) {
                    statusCode = 404;
                    response = { error: 'Call not found' };
                } else if (call.toUserId !== session.userId) {
                    statusCode = 403;
                    response = { error: 'Not authorized to accept this call' };
                } else {
                    call.status = 'ACCEPTED';
                    call.updatedAt = new Date();

                    const callerSession = Array.from(sessions.values()).find(s => s.userId === call.fromUserId);
                    if (callerSession) {
                        const callerWs = wsConnections.get(Array.from(sessions.entries()).find(([_, s]) => s.userId === call.fromUserId)?.[0]);
                        if (callerWs && callerWs.readyState === callerWs.OPEN) {
                            callerWs.send(JSON.stringify({
                                type: 'call_accepted',
                                data: {
                                    callId: callId,
                                    acceptedBy: session.username
                                }
                            }));
                        }
                    }

                    response = {
                        success: true,
                        callId: callId,
                        status: 'ACCEPTED'
                    };
                }
            }
        }

        else if (path.startsWith('/api/v1/calls/') && path.endsWith('/end') && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const callId = path.split('/')[3];

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const call = global.calls?.get(callId);
                if (!call) {
                    statusCode = 404;
                    response = { error: 'Call not found' };
                } else if (call.fromUserId !== session.userId && call.toUserId !== session.userId) {
                    statusCode = 403;
                    response = { error: 'Not authorized to end this call' };
                } else {
                    call.status = 'ENDED';
                    call.endedBy = session.userId;
                    call.updatedAt = new Date();

                    const otherUserId = call.fromUserId === session.userId ? call.toUserId : call.fromUserId;
                    const otherSession = Array.from(sessions.values()).find(s => s.userId === otherUserId);
                    if (otherSession) {
                        const otherWs = wsConnections.get(Array.from(sessions.entries()).find(([_, s]) => s.userId === otherUserId)?.[0]);
                        if (otherWs && otherWs.readyState === otherWs.OPEN) {
                            otherWs.send(JSON.stringify({
                                type: 'call_ended',
                                data: {
                                    callId: callId,
                                    endedBy: session.username
                                }
                            }));
                        }
                    }

                    response = {
                        success: true,
                        callId: callId,
                        status: 'ENDED'
                    };
                }
            }
        }

        else if (path.startsWith('/api/v1/calls/') && path.endsWith('/ice-candidate') && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const callId = path.split('/')[3];

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const call = global.calls?.get(callId);
                if (!call) {
                    statusCode = 404;
                    response = { error: 'Call not found' };
                } else {
                    const { candidate } = data;

                    const otherUserId = call.fromUserId === session.userId ? call.toUserId : call.fromUserId;
                    const otherSession = Array.from(sessions.values()).find(s => s.userId === otherUserId);
                    if (otherSession) {
                        const otherWs = wsConnections.get(Array.from(sessions.entries()).find(([_, s]) => s.userId === otherUserId)?.[0]);
                        if (otherWs && otherWs.readyState === otherWs.OPEN) {
                            otherWs.send(JSON.stringify({
                                type: 'ice_candidate',
                                data: {
                                    callId: callId,
                                    candidate: candidate,
                                    fromUserId: session.userId
                                }
                            }));
                        }
                    }

                    response = { success: true };
                }
            }
        }

        else if (path.startsWith('/api/v1/calls/') && (path.endsWith('/offer') || path.endsWith('/answer')) && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const pathParts = path.split('/');
            const callId = pathParts[3];
            const signalType = pathParts[4];

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const call = global.calls?.get(callId);
                if (!call) {
                    statusCode = 404;
                    response = { error: 'Call not found' };
                } else {
                    const { sdp } = data;

                    const otherUserId = call.fromUserId === session.userId ? call.toUserId : call.fromUserId;
                    const otherSession = Array.from(sessions.values()).find(s => s.userId === otherUserId);
                    if (otherSession) {
                        const otherWs = wsConnections.get(Array.from(sessions.entries()).find(([_, s]) => s.userId === otherUserId)?.[0]);
                        if (otherWs && otherWs.readyState === otherWs.OPEN) {
                            otherWs.send(JSON.stringify({
                                type: signalType,
                                data: {
                                    callId: callId,
                                    sdp: sdp,
                                    fromUserId: session.userId
                                }
                            }));
                        }
                    }

                    response = { success: true };
                }
            }
        }

        else if (path === '/api/v1/calls' && method === 'GET') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);

            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const userCalls = [];
                if (global.calls) {
                    for (const [callId, call] of global.calls.entries()) {
                        if ((call.fromUserId === session.userId || call.toUserId === session.userId) &&
                            call.status !== 'ENDED') {
                            userCalls.push({
                                id: call.id,
                                otherUserId: call.fromUserId === session.userId ? call.toUserId : call.fromUserId,
                                isVideoCall: call.isVideoCall,
                                status: call.status,
                                createdAt: call.createdAt,
                                isIncoming: call.toUserId === session.userId
                            });
                        }
                    }
                }

                response = {
                    success: true,
                    calls: userCalls
                };
            }
        }

        else {
            statusCode = 404;
            response = { error: 'Endpoint not found' };
        }

        const endTime = Date.now();
        const duration = endTime - startTime;
        responseSize = JSON.stringify(response).length;
        const clientIP = req.connection.remoteAddress || req.socket.remoteAddress || 'unknown';

        console.log(`[${new Date().toISOString()}] ${req.method} ${req.url} - ${statusCode} - ${duration}ms - ${responseSize} bytes - IP: ${clientIP}`);

        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(response));
    });
});

const sessions = new Map();

// websocket port, panel expects 3003
const wss = new WebSocketServer({ port: 3003 });

const wsConnections = new Map();

wss.on('connection', (ws, request) => {
    const url = new URL(request.url || '/', 'http://localhost');
    const token = url.searchParams.get('token');

    console.log('WebSocket connection attempt, token:', token ? token.substring(0, 10) + '...' : 'none');

    if (!token) {
        console.log('WebSocket connection rejected: no token');
        ws.close(1008, 'Token required');
        return;
    }

    const session = sessions.get(token);
    if (!session) {
        console.log('WebSocket connection rejected: invalid token');
        ws.close(1008, 'Invalid token');
        return;
    }

    console.log(`WebSocket connected for user: ${session.username}`);
    wsConnections.set(token, ws);

    ws.send(JSON.stringify({
        type: 'connection_established',
        data: {
            userId: session.userId,
            username: session.username,
            timestamp: new Date().toISOString()
        }
    }));

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message.toString());
            console.log('WebSocket message received:', data.type);

            switch (data.type) {
                case 'ping':
                    ws.send(JSON.stringify({ type: 'pong', timestamp: new Date().toISOString() }));
                    break;
                case 'chat_message':
                    broadcastMessage(data, token);
                    break;
                case 'call_offer':
                case 'call_answer':
                case 'ice_candidate':
                case 'call_accept':
                case 'call_reject':
                case 'call_end':
                    handleCallSignaling(data, token, ws);
                    break;
                default:
                    console.log('Unknown message type:', data.type);
            }
        } catch (error) {
            console.error('Failed to parse WebSocket message:', error);
        }
    });

    ws.on('close', () => {
        console.log(`WebSocket disconnected for user: ${session.username}`);
        wsConnections.delete(token);
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
        wsConnections.delete(token);
    });
});

function broadcastMessage(message, excludeToken) {
    const messageStr = JSON.stringify(message);
    wsConnections.forEach((ws, token) => {
        if (token !== excludeToken && ws.readyState === ws.OPEN) {
            ws.send(messageStr);
        }
    });
}

function handleCallSignaling(data, token, ws) {
    const session = sessions.get(token);
    if (!session) return;

    console.log(`Call signaling: ${data.type} from ${session.username}`);

    switch (data.type) {
        case 'call_offer':
        case 'call_answer':
        case 'ice_candidate':
            const targetUserId = data.data.toUserId;
            const targetSession = Array.from(sessions.entries()).find(([_, s]) => s.userId === targetUserId);
            if (targetSession) {
                const targetWs = wsConnections.get(targetSession[0]);
                if (targetWs && targetWs.readyState === targetWs.OPEN) {
                    data.data.fromUserId = session.userId;
                    data.data.fromUsername = session.username;
                    targetWs.send(JSON.stringify(data));
                }
            }
            break;

        case 'call_accept':
        case 'call_reject':
        case 'call_end':
            const callId = data.data.callId;
            const call = global.calls?.get(callId);
            if (call) {
                switch (data.type) {
                    case 'call_accept':
                        call.status = 'ACCEPTED';
                        break;
                    case 'call_reject':
                    case 'call_end':
                        call.status = 'ENDED';
                        call.endedBy = session.userId;
                        break;
                }
                call.updatedAt = new Date();

                const otherUserId = call.fromUserId === session.userId ? call.toUserId : call.fromUserId;
                const otherSession = Array.from(sessions.entries()).find(([_, s]) => s.userId === otherUserId);
                if (otherSession) {
                    const otherWs = wsConnections.get(otherSession[0]);
                    if (otherWs && otherWs.readyState === otherWs.OPEN) {
                        data.data.fromUserId = session.userId;
                        data.data.fromUsername = session.username;
                        otherWs.send(JSON.stringify(data));
                    }
                }
            }
            break;
    }
}

async function startServer() {
    const mongoConnected = await initMongoDB();
    if (!mongoConnected) {
        console.error('Failed to connect to MongoDB. Exiting...');
        process.exit(1);
    }

    server.listen(PORT, '0.0.0.0', () => {
        console.log(`SecureChat Android App API Server running on port ${PORT}`);
        console.log(`Accessible from: http://example.com:${PORT}`);
        console.log(`Also accessible from: http://YOUR_SERVER_IP:${PORT}`);
        console.log(`Local access: http://192.168.1.100:${PORT}`);
        console.log(`Admin panel should connect to http://localhost:${ADMIN_PORT}`);
    });

    const adminServer = http.createServer(async (req, res) => {
        const parsedUrl = url.parse(req.url, true);
        const path = parsedUrl.pathname;
        const method = req.method;

        const requestPort = req.socket.localPort;

        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

        if (req.method === 'OPTIONS') {
            res.writeHead(200);
            res.end();
            return;
        }

        console.log(`[ADMIN] ${method} ${path}`);

        const startTime = Date.now();
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
        });

        req.on('end', async () => {
            let data = null;
            if (body && method !== 'GET') {
                try {
                    data = JSON.parse(body);
                } catch (error) {
                    console.error('JSON parse error:', error);
                }
            }

            if (path === '/api/admin/users' && method === 'GET') {
                const token = req.headers.authorization?.replace('Bearer ', '');
                const session = sessions.get(token);

                console.log(`[ADMIN] Token received: ${token ? token.substring(0, 10) + '...' : 'none'}`);
                console.log(`[ADMIN] Sessions stored: ${sessions.size}`);
                console.log(`[ADMIN] Session valid: ${session ? 'yes' : 'no'}`);

                if (!session) {
                    res.writeHead(401, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Unauthorized' }));
                    return;
                } else {
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
                                    lastLogin: 1
                                }
                            }
                        ).toArray();
                        response = users.map(user => {
                            const now = new Date();
                            const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                            const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000;

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
                    } catch (error) {
                        res.writeHead(500, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: 'Failed to fetch users' }));
                    }
                }
            } else {
                res.writeHead(404, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Not Found' }));
            }
        });
    });

    adminServer.listen(ADMIN_PORT, '0.0.0.0', () => {
        console.log(`\n╔════════════════════════════════════════════════════════╗`);
        console.log(`║          ADMIN PANEL SERVER STARTED                   ║`);
        console.log(`╠════════════════════════════════════════════════════════╣`);
        console.log(`║ Port: ${ADMIN_PORT}                                                ║`);
        console.log(`║                                                        ║`);
        console.log(`║ Access URLs:                                           ║`);
        console.log(`║ • Local:      http://localhost:${ADMIN_PORT}                   ║`);
        console.log(`║ • Local LAN:  http://192.168.1.100:${ADMIN_PORT}                 ║`);
        console.log(`║ • Public:     http://example.com:${ADMIN_PORT}         ║`);
        console.log(`║ • Public IP:  http://YOUR_SERVER_IP:${ADMIN_PORT}                ║`);
        console.log(`║                                                        ║`);
        console.log(`║ Admin Dashboard HTML:                                  ║`);
        console.log(`║ • http://localhost:${ADMIN_PORT}/admin-dashboard.html       ║`);
        console.log(`║ • http://192.168.1.100:${ADMIN_PORT}/admin-dashboard.html      ║`);
        console.log(`╚════════════════════════════════════════════════════════╝\n`);
    });

    console.log(`MongoDB connected: ${MONGODB_DB}`);
}

startServer().catch(console.error);
