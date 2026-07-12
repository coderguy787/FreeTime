#!/usr/bin/env node

/**
 * SecureChat Android App Admin API Server with MongoDB Integration
 * Manages user accounts for the Android social app with real database storage
 */

const http = require('http');
const url = require('url');
const { WebSocketServer } = require('ws');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const { MongoClient } = require('mongodb');
const bcrypt = require('bcryptjs');

// Configuration
const PORT = process.env.PORT || 8081; // Port 8081 for Android app
const ADMIN_PORT = 3001; // Admin panel port
const MONGODB_URI = 'mongodb://127.0.0.1:27017/securechat';
const MONGODB_DB = 'securechat';

// MongoDB connection
let db;
let usersCollection;
let statsCollection;

// User roles and tags
const USER_ROLES = ['USER', 'MODERATOR', 'ADMIN', 'OWNER'];
const USER_TAGS = ['owner', 'admin', 'moderator', 'vip', 'verified', 'developer', 'support'];

// Initialize MongoDB connection
async function initMongoDB() {
    try {
        const client = new MongoClient(MONGODB_URI);
        await client.connect();
        db = client.db(MONGODB_DB);
        
        // Create collections
        usersCollection = db.collection('users');
        statsCollection = db.collection('statistics');
        
        // Create indexes
        await usersCollection.createIndex({ id: 1 }, { unique: true });
        await usersCollection.createIndex({ username: 1 }, { unique: true, sparse: true });
        await usersCollection.createIndex({ email: 1 }, { unique: true, sparse: true });
        await usersCollection.createIndex({ publicTag: 1 }, { unique: true, sparse: true });
        // Remove phoneNumber unique index to allow null values
        
        console.log('Connected to MongoDB successfully');
        
        // Initialize default admin if not exists
        await initializeDefaultAdmin();
        
        return true;
    } catch (error) {
        console.error('Failed to connect to MongoDB:', error);
        return false;
    }
}

// Initialize default admin user
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

// Helper functions
function generateToken(user) {
    return crypto.randomBytes(32).toString('hex');
}

function validateUsername(username) {
    // Username must be 3-20 characters, letters/numbers/underscore only
    return /^[a-zA-Z0-9_]{3,20}$/.test(username);
}

function validateEmail(email) {
    // Basic email validation
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function validatePublicTag(tag) {
    // Public tags must start with @ and be 3-20 characters long
    return /^@[a-zA-Z0-9_]{2,19}$/.test(tag);
}

// Statistics functions
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

// Create HTTP server
const server = http.createServer(async (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const path = parsedUrl.pathname;
    const method = req.method;
    
    // Check which port this request came from
    const requestPort = req.socket.localPort;
    
    // Enable CORS
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
        
        // Health check
        if (path === '/health' || path === '/api/health') {
            response = { 
                status: 'ok', 
                timestamp: new Date().toISOString(),
                mongodb: db ? 'connected' : 'disconnected'
            };
        }
        
        // Admin authentication
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
                    // Store session in memory (in production, use Redis)
                    sessions.set(token, { 
                        userId: user.id, 
                        role: user.role,
                        username: user.name 
                    });
                    
                    // Update last login
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
        
        // Admin: Verify token
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
        
        // Admin: Get statistics
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
        
        // Admin: Get all users
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
                    // Add calculated online status and display status for each user
                    response = users.map(user => {
                        // Calculate online status based on lastLogin time
                        const now = new Date();
                        const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                        const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000; // Online if last login was within last 5 minutes
                        
                        // Calculate display status
                        let status = 'OFFLINE';
                        if (user.isActive) {
                            if (isOnline) {
                                status = 'ONLINE';
                            } else if (lastLogin && (now - lastLogin) < 24 * 60 * 60 * 1000) { // Last login within 24 hours
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
        
        // Admin: Create user
        else if (path === '/api/admin/users' && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            
            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                try {
                    const { name, username, email, phoneNumber, publicTag, role, tags, password } = data;
                    
                    // Validate required fields
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
                        // Check if username already exists (active users only)
                        const existingUser = await usersCollection.findOne({ username, isActive: true });
                        
                        if (existingUser) {
                            statusCode = 409;
                            response = { error: 'Username already exists' };
                        } else if (email) {
                            // Check if email already exists (active users only)
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
                            // Validate public tag
                            if (publicTag && !validatePublicTag(publicTag)) {
                                statusCode = 400;
                                response = { error: 'Invalid public tag format' };
                                return;
                            }
                            
                            // Check if public tag already exists
                            if (publicTag) {
                                const existingTag = await usersCollection.findOne({ publicTag, isActive: true });
                                if (existingTag) {
                                    statusCode = 409;
                                    response = { error: 'Public tag already exists' };
                                    return;
                                }
                            }

                            // If a soft-deleted user exists, reactivate instead of creating a duplicate
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
                                    // 2FA Configuration: Admin-created users don't require 2FA by default
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
                            
                            // Create new user
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
                                // 2FA Configuration: Admin-created users don't require 2FA by default
                                twoFactorAuth: {
                                    enabled: false,
                                    method: null,
                                    secret: null,
                                    accountVerified: false,
                                    mandatorySetup: false, // Admin can customize 2FA requirement
                                    backupCodes: []
                                }
                            };
                            
                            const createResult = await usersCollection.insertOne(newUser);
                            if (!createResult.insertedId) {
                                throw new Error('Failed to create user - database error');
                            }
                            
                            // Update statistics
                            await updateStatistics();
                            
                            // Remove password from response
                            const { password: _, ...userResponse } = newUser;
                            response = userResponse;
                        }
                    }
                } catch (error) {
                    if (error.code === 11000) {
                        // Duplicate key error
                        statusCode = 409;
                        response = { error: 'Username, email, or public tag already exists' };
                    } else {
                        statusCode = 500;
                        response = { error: 'Failed to create user' };
                    }
                }
            }
        }
        
        // Admin: Update user
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
                        // Check if new username is valid and not taken
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
                        
                        // Check if new email is valid and not taken
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
                        
                        // Check if new public tag is valid and not taken
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
                            
                            // Hash password if provided
                            if (password && password.trim()) {
                                try {
                                    updateData.password = await bcrypt.hash(password, 10);
                                } catch (hashError) {
                                    statusCode = 500;
                                    response = { error: 'Failed to hash password' };
                                    return;
                                }
                            }
                            
                            // Ensure twoFactorAuth field exists (initialize if missing)
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
        
        // Admin: Delete user
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
        
        // Admin: Toggle 2FA requirement for a user
        else if (path.match(/^\/api\/admin\/users\/[^/]+\/toggle-2fa$/) && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const userId = path.split('/')[4]; // Extract userId from /api/admin/users/:userId/toggle-2fa
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
                        // Initialize twoFactorAuth if missing
                        const currentTwoFA = user.twoFactorAuth || {
                            enabled: false,
                            method: null,
                            secret: null,
                            accountVerified: false,
                            mandatorySetup: false,
                            backupCodes: []
                        };
                        
                        // Update 2FA requirement
                        const updatedTwoFA = {
                            ...currentTwoFA,
                            mandatorySetup: enabled,
                            // If disabling 2FA, clear it completely so user must re-setup with new QR code
                            enabled: enabled ? currentTwoFA.enabled : false,
                            accountVerified: enabled ? currentTwoFA.accountVerified : false,
                            secret: enabled ? currentTwoFA.secret : null, // Clear secret when disabling
                            method: enabled ? currentTwoFA.method : null, // Clear method when disabling
                            backupCodes: enabled ? currentTwoFA.backupCodes : [] // Clear backup codes when disabling
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
        
        // Android App API Endpoints
        
        // Android app registration
        else if (path === '/api/v1/auth/register' && method === 'POST') {
            const { username, email, password, displayName, phoneNumber } = data;
            
            try {
                // Check if user already exists
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
                    // Create new user
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
                    
                    // Generate token
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
        
        // Android app login
        else if (path === '/api/v1/auth/login' && method === 'POST') {
            const { username, password } = data;
            try {
                // Find user by email, public tag, name, or username
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
                    // Generate token for Android app
                    const token = generateToken();
                    sessions.set(token, { 
                        userId: user.id, 
                        role: user.role,
                        username: user.name,
                        type: 'android'
                    });
                    
                    // Update last login
                    await usersCollection.updateOne(
                        { id: user.id },
                        { $set: { lastLogin: new Date(), isOnline: true } }
                    );
                    
                    // Transform user data to match Android app expectations
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
                        refreshToken: null, // Not implemented yet
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
        
        // Get current user
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
                    // Transform to match Android app User model
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
        
        // Get user by ID
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
        
        // Search users
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
            
            // Transform users to match Android app format
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
        
        // Update profile
        else if (path === '/api/v1/users/me' && method === 'PUT') {
            const { displayName, avatarUrl, status } = data;
            // For demo, update first user
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
        
        // Delete account
        else if (path === '/api/v1/users/me' && method === 'DELETE') {
            // For demo, just return success
            response = {};
        }

        // WebRTC Call Signaling Endpoints
        
        // Initiate call
        else if (path === '/api/v1/calls/initiate' && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            
            if (!session) {
                statusCode = 401;
                response = { error: 'Unauthorized' };
            } else {
                const { toUserId, isVideoCall } = data;
                const callId = uuidv4();
                
                // Create call record
                const callRecord = {
                    id: callId,
                    fromUserId: session.userId,
                    toUserId: toUserId,
                    isVideoCall: isVideoCall || false,
                    status: 'INITIATED',
                    createdAt: new Date(),
                    updatedAt: new Date()
                };
                
                // Store call in memory (in production, use database)
                if (!global.calls) global.calls = new Map();
                global.calls.set(callId, callRecord);
                
                // Send call notification to target user via WebSocket
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

        // Accept call
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
                    
                    // Notify caller that call was accepted
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

        // Reject/End call
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
                    
                    // Notify other participant
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

        // WebRTC Signaling - ICE candidates
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
                    
                    // Forward ICE candidate to other participant
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

        // WebRTC Signaling - Offer/Answer
        else if (path.startsWith('/api/v1/calls/') && (path.endsWith('/offer') || path.endsWith('/answer')) && method === 'POST') {
            const token = req.headers.authorization?.replace('Bearer ', '');
            const session = sessions.get(token);
            const pathParts = path.split('/');
            const callId = pathParts[3];
            const signalType = pathParts[4]; // 'offer' or 'answer'
            
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
                    
                    // Forward SDP to other participant
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

        // Get active calls for user
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

        // Default response
        else {
            statusCode = 404;
            response = { error: 'Endpoint not found' };
        }
        
        // Log response
        const endTime = Date.now();
        const duration = endTime - startTime;
        responseSize = JSON.stringify(response).length;
        const clientIP = req.connection.remoteAddress || req.socket.remoteAddress || 'unknown';
        
        console.log(`[${new Date().toISOString()}] ${req.method} ${req.url} - ${statusCode} - ${duration}ms - ${responseSize} bytes - IP: ${clientIP}`);
        
        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(response));
    });
});

// In-memory session store
const sessions = new Map();

// WebSocket server for real-time updates
const wss = new WebSocketServer({ port: 3003 });

// Store WebSocket connections by token
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
    
    // Send initial connection confirmation
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
            
            // Handle different message types
            switch (data.type) {
                case 'ping':
                    ws.send(JSON.stringify({ type: 'pong', timestamp: new Date().toISOString() }));
                    break;
                case 'chat_message':
                    // Broadcast to other connected users
                    broadcastMessage(data, token);
                    break;
                case 'call_offer':
                case 'call_answer':
                case 'ice_candidate':
                case 'call_accept':
                case 'call_reject':
                case 'call_end':
                    // Handle WebRTC signaling
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

// Broadcast message to all connected clients except sender
function broadcastMessage(message, excludeToken) {
    const messageStr = JSON.stringify(message);
    wsConnections.forEach((ws, token) => {
        if (token !== excludeToken && ws.readyState === ws.OPEN) {
            ws.send(messageStr);
        }
    });
}

// Handle WebRTC call signaling
function handleCallSignaling(data, token, ws) {
    const session = sessions.get(token);
    if (!session) return;
    
    console.log(`Call signaling: ${data.type} from ${session.username}`);
    
    switch (data.type) {
        case 'call_offer':
        case 'call_answer':
        case 'ice_candidate':
            // Forward to target user
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
            // Handle call state changes
            const callId = data.data.callId;
            const call = global.calls?.get(callId);
            if (call) {
                // Update call state
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
                
                // Notify other participant
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

// Start server
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
    
    // Also create admin server on ADMIN_PORT
    const adminServer = http.createServer(async (req, res) => {
        const parsedUrl = url.parse(req.url, true);
        const path = parsedUrl.pathname;
        const method = req.method;
        
        // Check which port this request came from
        const requestPort = req.socket.localPort;
        
        // Enable CORS
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
            
            // Handle admin routes
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
                        // Add calculated online status and display status for each user
                        response = users.map(user => {
                            // Calculate online status based on lastLogin time
                            const now = new Date();
                            const lastLogin = user.lastLogin ? new Date(user.lastLogin) : null;
                            const isOnline = lastLogin && (now - lastLogin) < 5 * 60 * 1000; // Online if last login was within last 5 minutes
                            
                            // Calculate display status
                            let status = 'OFFLINE';
                            if (user.isActive) {
                                if (isOnline) {
                                    status = 'ONLINE';
                                } else if (lastLogin && (now - lastLogin) < 24 * 60 * 60 * 1000) { // Last login within 24 hours
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
