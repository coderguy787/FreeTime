const express = require('express');
const https = require('https');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { MongoClient, ObjectId } = require('mongodb');
const { v4: uuidv4 } = require('uuid');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const speakeasy = require('speakeasy');
const QRCode = require('qrcode');
const nodemailer = require('nodemailer');
const crypto = require('crypto');
const os = require('os');
const rateLimit = require('express-rate-limit');
const DatabaseConnectionManager = require('../utils/database-connection-manager');
const SessionManager = require('../utils/session-manager.js');
const { initializeSocketIO } = require('../websocket/socket-io-server.js');
const GridFSHandler = require('../utils/gridfs-handler');

let gridFSHandler;
let pushNotificationManager;
function initializeGridFSHandler() {
    if (!gridFSHandler && typeof dbConnection !== 'undefined' && dbConnection.getDatabase) {
        gridFSHandler = new GridFSHandler(dbConnection.getDatabase());
    }
    return gridFSHandler;
}

let admin;
let firebaseInitialized = false;

try {
    admin = require('firebase-admin');
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT || path.join(__dirname, '../config/firebase-service-account.json');

    if (fs.existsSync(serviceAccountPath)) {
        const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount),
            projectId: serviceAccount.project_id
        });
        firebaseInitialized = true;
        console.log('[ FCM] Firebase Admin SDK initialized successfully');
    } else {
        console.warn('[ FCM] Firebase service account not found at:', serviceAccountPath);
        console.warn('[ FCM] Notifications will fall back to WebSocket only');
    }
} catch (err) {
    console.warn('[ FCM] Firebase initialization failed:', err.message);
    console.warn('[ FCM] Notifications will fall back to WebSocket only');
}

async function sendPushNotification(userId, payload) {
    let fcmSent = false;
    let webSocketSent = false;

    try {
        if (firebaseInitialized && admin) {
            try {
                const fcmTokens = await dbConnection.collection('FCMTokens').find({ userId }).toArray();

                if (fcmTokens && fcmTokens.length > 0) {
                    for (const tokenDoc of fcmTokens) {
                        try {
                            const dataPayload = payload.data || {};

                            if (!dataPayload.title) dataPayload.title = payload.notification?.title || 'FreeTime';
                            if (!dataPayload.body) dataPayload.body = payload.notification?.body || 'New message';

                            const messagePayload = {
                                token: tokenDoc.fcmToken,
                                data: dataPayload,
                                android: {
                                    priority: 'high',
                                    ttl: 86400,
                                }
                            };

                            messagePayload.notification = {
                                title: dataPayload.title,
                                body: dataPayload.body
                            };
                            messagePayload.android.notification = {
                                channelId: 'messages',
                                priority: 'high'
                            };

                            await admin.messaging().send(messagePayload);
                            fcmSent = true;
                            console.log(`[ FCM] Message sent to ${userId} via token ${tokenDoc.fcmToken.substring(0, 20)}...`);
                        } catch (tokenErr) {
                            if (tokenErr.code === 'messaging/invalid-registration-token' ||
                                tokenErr.code === 'messaging/registration-token-not-registered') {
                                await dbConnection.collection('FCMTokens').deleteOne({ _id: tokenDoc._id });
                                console.log(`[ FCM] Removed invalid token for ${userId}`);
                            } else {
                                console.warn(`[ FCM] Failed to send to token: ${tokenErr.message}`);
                            }
                        }
                    }
                }
            } catch (firebaseErr) {
                console.warn(`[ FCM] Firebase error: ${firebaseErr.message}`);
            }
        }

        try {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, userId, 'notification:received', {
                title: payload.notification?.title || payload.data?.title || 'New Notification',
                body: payload.notification?.body || payload.data?.body || '',
                data: payload.data || {},
                timestamp: new Date().getTime()
            });
            webSocketSent = true;
        } catch (wsErr) {
            console.warn(`[ NOTIFICATION] WebSocket failed: ${wsErr.message}`);
        }

        if (fcmSent || webSocketSent) {
            console.log(`[ NOTIFICATION] Sent to user ${userId} (FCM: ${fcmSent}, WebSocket: ${webSocketSent})`);
            return true;
        } else {
            console.warn(`[ NOTIFICATION] Failed to send to user ${userId} via any method`);
            return false;
        }
    } catch (err) {
        console.warn(`[ NOTIFICATION] Error sending to ${userId}: ${err.message}`);
        return false;
    }
}

function formatReactions(reactionsArray) {
    if (!reactionsArray) return {};
    if (typeof reactionsArray === 'object' && !Array.isArray(reactionsArray)) {
        return reactionsArray;
    }
    if (!Array.isArray(reactionsArray)) return {};

    const reactionsMap = {};
    reactionsArray.forEach(reaction => {
        if (reaction.emoji) {
            if (!reactionsMap[reaction.emoji]) {
                reactionsMap[reaction.emoji] = [];
            }
            if (reaction.userId && !reactionsMap[reaction.emoji].includes(reaction.userId)) {
                reactionsMap[reaction.emoji].push(reaction.userId);
            }
        }
    });
    return reactionsMap;
}

const app = express();

const envPath = path.join(__dirname, '../config/.env');
console.log(`[INFO] Loading .env from: ${envPath}`);
require('dotenv').config({ path: envPath });

const DEFAULT_MONGODB_HOST = process.env.MONGODB_HOST || '127.0.0.1';
const DEFAULT_MONGODB_PORT = process.env.PORT_MONGODB || '27017';
const DEFAULT_MONGODB_USER = process.env.MONGODB_USER || 'admin';
const DEFAULT_MONGODB_PASSWORD = process.env.MONGODB_PASSWORD || 'changeme';
const DEFAULT_MONGODB_DB = 'freetime';

const MONGODB_URI = process.env.MONGODB_URI || `mongodb://${DEFAULT_MONGODB_USER}:${DEFAULT_MONGODB_PASSWORD}@${DEFAULT_MONGODB_HOST}:${DEFAULT_MONGODB_PORT}/${DEFAULT_MONGODB_DB}?authSource=admin&retryWrites=true&w=majority`;
console.log(`[INFO] MongoDB URI configured: mongodb://${DEFAULT_MONGODB_USER}:****@${DEFAULT_MONGODB_HOST}:${DEFAULT_MONGODB_PORT}/${DEFAULT_MONGODB_DB}`);
const JWT_SECRET = process.env.JWT_SECRET || 'dev-only-secret-change-me';
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'SecurePass123!';
const API_PORT = Number(process.env.PORT_API) || 443;

const SSL_CERT_PATH = process.env.SSL_CERT || path.join(__dirname, '../certs/fullchain.pem');
const SSL_KEY_PATH = process.env.SSL_KEY || path.join(__dirname, '../certs/privkey.pem');
const LOCAL_SSL_CERT_PATH = path.join(__dirname, '../certs/fullchain.pem');
const LOCAL_SSL_KEY_PATH = path.join(__dirname, '../certs/privkey.pem');

console.log('[INFO] Environment values:');
console.log(` PORT_API=${process.env.PORT_API || '(unset)'}`);
console.log(` Resolved API_PORT=${API_PORT}`);
console.log(` SSL_CERT_PATH=${SSL_CERT_PATH}`);
console.log(` SSL_KEY_PATH=${SSL_KEY_PATH}`);

if (!process.env.JWT_SECRET) {
    console.warn('[WARN] JWT_SECRET not set in environment - using default development secret');
    console.warn('[WARN] CHANGE THIS IN PRODUCTION via environment variable JWT_SECRET');
} else {
    console.log('[OK] JWT_SECRET loaded from environment');
}

let sslOptions = null;
try {
    if (fs.existsSync(SSL_CERT_PATH) && fs.existsSync(SSL_KEY_PATH)) {
        sslOptions = {
            cert: fs.readFileSync(SSL_CERT_PATH, 'utf8'),
            key: fs.readFileSync(SSL_KEY_PATH, 'utf8')
        };
        console.log('[ HTTPS] SSL Certificates loaded from configured path:', {
            cert: SSL_CERT_PATH,
            key: SSL_KEY_PATH,
            status: 'READY'
        });
    } else if (SSL_CERT_PATH !== LOCAL_SSL_CERT_PATH && fs.existsSync(LOCAL_SSL_CERT_PATH) && fs.existsSync(LOCAL_SSL_KEY_PATH)) {
        sslOptions = {
            cert: fs.readFileSync(LOCAL_SSL_CERT_PATH, 'utf8'),
            key: fs.readFileSync(LOCAL_SSL_KEY_PATH, 'utf8')
        };
        console.warn('[ HTTPS] Configured SSL certificate path not found. Falling back to local certs:', {
            cert: LOCAL_SSL_CERT_PATH,
            key: LOCAL_SSL_KEY_PATH
        });
    } else {
        console.warn('[ HTTPS] SSL certificates not found at:', {
            cert: SSL_CERT_PATH,
            key: SSL_KEY_PATH
        });
        console.warn('[ HTTPS] To set up Let\'s Encrypt certificates, run on Debian server:');
        console.warn('[ HTTPS] 1. sudo apt install certbot -y');
        console.warn('[ HTTPS] 2. sudo certbot certonly --standalone -d your-domain.com');
        console.warn('[ HTTPS] 3. sudo chmod 644 /etc/letsencrypt/live/your-domain.com/privkey.pem');
        console.warn('[ HTTPS] Server will run in HTTP mode (not suitable for production)');
    }
} catch (err) {
    console.error('[ HTTPS] Failed to load SSL certificates:', err.message);
    console.warn('[ HTTPS] Server will run in HTTP mode');
}

if (!JWT_SECRET) {
    console.error('[ERROR] CRITICAL: JWT_SECRET not set in .env file!');
    console.error('[ERROR] Set JWT_SECRET before running in production');
    process.exit(1);
}

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

app.use((req, res, next) => {
    res.set('Connection', 'keep-alive');
    res.set('Keep-Alive', 'timeout=65, max=100');
    next();
});

const upload = multer({
    dest: path.join(os.tmpdir(), 'freetime-uploads'),
    limits: {
        fileSize: 200 * 1024 * 1024,
        files: 10
    },
    fileFilter: (req, file, cb) => {
        const allowedTypes = [
            'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/svg+xml',
            'video/mp4', 'video/webm', 'video/3gpp', 'video/quicktime',
            'audio/mp3', 'audio/wav', 'audio/ogg', 'audio/m4a', 'audio/mpeg',
            'application/pdf', 'text/plain', 'application/xml', 'text/xml',
            'application/zip', 'application/x-zip-compressed', 'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        ];

        if (allowedTypes.includes(file.mimetype) || file.mimetype.startsWith('image/') || file.mimetype.startsWith('video/') || file.mimetype.startsWith('audio/')) {
            cb(null, true);
        } else {
            cb(null, true);
        }
    }
});
function getFileSizeLimit(mimeType) {
    const MEDIA_LIMIT = 200 * 1024 * 1024;
    const OTHER_LIMIT = 100 * 1024 * 1024;

    const imageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    const videoTypes = ['video/mp4', 'video/webm', 'video/3gpp', 'audio/mp3', 'audio/wav', 'audio/ogg', 'audio/m4a'];

    if (imageTypes.includes(mimeType) || videoTypes.includes(mimeType)) {
        return MEDIA_LIMIT;
    }

    return OTHER_LIMIT;
}

async function encryptMediaFile(fileData, mimeType, mediaId) {
    try {
        if (!fileData || fileData.length === 0) {
            throw new Error('File data is empty or null');
        }

        const crypto = require('crypto');

        const iv = crypto.randomBytes(16);

        const key = crypto.randomBytes(32);

        console.log(`[ENCRYPTION DETAIL] Encrypting ${mediaId}: input=${fileData.length} bytes, IV=${iv.length} bytes, key=${key.length} bytes`);

        const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);

        let encryptedData = cipher.update(fileData);
        encryptedData = Buffer.concat([encryptedData, cipher.final()]);

        console.log(`[ENCRYPTION DETAIL] Encrypted ${mediaId}: output=${encryptedData.length} bytes`);

        const encryptedWithIv = Buffer.concat([iv, encryptedData]);

        return {
            encryptedData: encryptedWithIv,
            encryptionKey: key.toString('base64'),
            iv: iv.toString('base64')
        };
    } catch (err) {
        console.error(`[ENCRYPTION ERROR] Failed encrypting ${mediaId}:`, err.message);
        throw err;
    }
}

async function decryptMediaFile(encryptedData, encryptionKeyBase64) {
    try {
        const crypto = require('crypto');

        const key = Buffer.from(encryptionKeyBase64, 'base64');

        const iv = encryptedData.slice(0, 16);
        const ciphertext = encryptedData.slice(16);

        const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);

        let decrypted = decipher.update(ciphertext);
        decrypted = Buffer.concat([decrypted, decipher.final()]);

        return decrypted;
    } catch (err) {
        console.error('[ENCRYPTION] Error decrypting media:', err);
        throw err;
    }
}

function parseAllowedOrigins(allowedOriginsValue) {
    return allowedOriginsValue
        .split(',')
        .map(origin => origin.trim())
        .filter(origin => origin.length > 0)
        .map(origin => origin.replace(/\/+$/, ''));
}

function isAllowedOrigin(origin) {
    if (!origin) return false;
    const normalizedOrigin = origin.trim().replace(/\/+$/, '');
    return allowedOrigins.includes(normalizedOrigin) || allowedOrigins.includes('*');
}

const allowedOrigins = process.env.ALLOWED_ORIGINS ? parseAllowedOrigins(process.env.ALLOWED_ORIGINS) : [
    'https://example.com',
    'https://example.com:443',
    'https://localhost',
    'https://localhost:3001',
    'https://localhost:8080',
    'https://localhost:9080',
    'http://localhost',
    'http://localhost:3001',
    'http://localhost:8080',
    'http://localhost:9080',
    'http://192.168.1.7',
    'http://192.168.1.7:3001',
    'http://192.168.1.7:8080',
    'http://192.168.1.7:9080',
    'https://192.168.1.7',
    'https://192.168.1.7:3001',
    'https://192.168.1.7:8080',
    'https://192.168.1.7:9080',
    'http://10.0.2.2',
    'http://10.0.2.2:80'
];

let transporter = null;
if (process.env.EMAIL_SERVICE && process.env.EMAIL_SERVICE !== 'disabled') {
    if (process.env.EMAIL_SERVICE === 'ethereal') {
        transporter = nodemailer.createTransport({
            host: 'smtp.ethereal.email',
            port: 587,
            secure: false,
            auth: {
                user: process.env.EMAIL_USER || 'ethereal.user@ethereal.email',
                pass: process.env.EMAIL_PASSWORD || 'ethereal.password'
            }
        });
    } else {
        transporter = nodemailer.createTransport({
            service: process.env.EMAIL_SERVICE,
            auth: {
                user: process.env.EMAIL_USER,
                pass: process.env.EMAIL_PASSWORD
            }
        });
    }
} else {
    console.log('[WARN] Email service disabled - users will rely on authenticator app with backup codes');
}

if (transporter) {
    transporter.verify((error, success) => {
        if (error) {
            console.error('[ERROR] Email service error:', error.message);
            console.log('[WARN] Users will need backup codes for account recovery');
        } else {
            console.log('[OK] Email service ready for 2FA verification');
        }
    });
} else {
    console.log('[OK] Using authenticator app + backup codes for 2FA');
}

app.use((req, res, next) => {
    const origin = req.headers.origin;

    if (!origin || isAllowedOrigin(origin)) {
        res.header('Access-Control-Allow-Origin', origin || '*');
    } else {
        console.log(`[WARN] Requesting origin not in whitelist: ${origin}`);
        res.header('Access-Control-Allow-Origin', origin);
    }

    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, PATCH');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, Accept, Origin, X-CSRF-Token');
    res.header('Access-Control-Allow-Credentials', 'true');
    res.header('Access-Control-Max-Age', '3600');

    if (req.method === 'OPTIONS') {
        res.sendStatus(200);
        return;
    }
    next();
});

app.use(cors({
    origin: (origin, callback) => {
        if (!origin || isAllowedOrigin(origin)) {
            callback(null, true);
        } else {
            console.warn(`CORS blocked origin: ${origin}`);
            callback(new Error('CORS policy: Origin not allowed'));
        }
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'PATCH'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With', 'Accept', 'Origin'],
    preflightContinue: true,
    maxAge: 3600
}));

app.use('/update', express.static(path.join(__dirname, '..', 'update')));

app.use((req, res, next) => {
    if (req.path.includes('socket.io') || req.path.includes('/api/socket.io')) {
        console.log(`[SOCKET.IO REQUEST] ${req.method} ${req.path}`);
        console.log(' • Headers:', {
            contentType: req.get('content-type'),
            host: req.get('host'),
            userAgent: req.get('user-agent')?.substring(0, 50),
            origin: req.get('origin')
        });
        if (req.query) console.log(' • Query:', req.query);
    }

    const originalSend = res.send;
    const originalJson = res.json;

    res.send = function(data) {
        if (req.path.includes('socket.io')) {
            console.log(`[SOCKET.IO RESPONSE] ${req.method} ${req.path} => Status: ${res.statusCode}`);
        }
        return originalSend.call(this, data);
    };

    res.json = function(data) {
        if (req.path.includes('socket.io')) {
            console.log(`[SOCKET.IO JSON] ${req.method} ${req.path} => Status: ${res.statusCode}`);
        }
        return originalJson.call(this, data);
    };

    next();
});

const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 5,
    message: { error: 'Too many login attempts. Please try again later.' },
    standardHeaders: true,
    legacyHeaders: false,
    // admin logins excluded from rate limiting
    skip: (req, res) => {
        return req.path === '/api/admin/login';
    }
});

function detectSuspiciousActivity(userAgent, deviceFingerprint) {
    const reasons = [];

    const botPatterns = [
        /bot/i, /crawler/i, /spider/i, /scraper/i,
        /curl/i, /wget/i, /python/i, /java/i,
        /postman/i, /insomnia/i, /httpie/i
    ];

    if (botPatterns.some(pattern => pattern.test(userAgent))) {
        reasons.push('Bot-like user agent detected');
    }

    if (deviceFingerprint) {
        if (deviceFingerprint.deviceModel && (
            deviceFingerprint.deviceModel.toLowerCase().includes('emulator') ||
            deviceFingerprint.deviceModel.toLowerCase().includes('sdk') ||
            deviceFingerprint.deviceModel.toLowerCase().includes('generic')
        )) {
            reasons.push('Emulator device detected');
        }

        if (deviceFingerprint.buildFingerprint && (
            deviceFingerprint.buildFingerprint.toLowerCase().includes('test-keys') ||
            deviceFingerprint.buildFingerprint.toLowerCase().includes('generic')
        )) {
            reasons.push('Test/development build detected');
        }

        if (deviceFingerprint.deviceBrand === 'unknown' && deviceFingerprint.deviceModel === 'unknown') {
            reasons.push('Unknown device configuration');
        }
    }

    if (deviceFingerprint && deviceFingerprint.timestamp) {
        const timeDiff = Date.now() - deviceFingerprint.timestamp;
        if (timeDiff < 100) {
            reasons.push('Suspicious timing - too fast');
        }
    }

    return {
        isBot: reasons.length > 0,
        reasons: reasons,
        reason: reasons.join(', ')
    };
}

const signupLimiter = rateLimit({
    windowMs: 60 * 60 * 1000,
    max: 3,
    message: { error: 'Too many signup attempts. Please try again later.' },
    standardHeaders: true,
    legacyHeaders: false
});

const totpLimiter = rateLimit({
    windowMs: 10 * 60 * 1000,
    max: 10,
    message: { error: 'Too many verification attempts. Please try again later.' },
    standardHeaders: true,
    legacyHeaders: false
});

const passwordResetLimiter = rateLimit({
    windowMs: 60 * 60 * 1000,
    max: 3,
    message: { error: 'Too many password reset attempts. Please try again later.' },
    standardHeaders: true,
    legacyHeaders: false
});

const REQUEST_TIMEOUT_MS = process.env.REQUEST_TIMEOUT_MS || 30000;

app.use((req, res, next) => {
    req.setTimeout(REQUEST_TIMEOUT_MS, () => {
        console.warn(`Request timeout: ${req.method} ${req.path}`);
        res.status(408).json({ error: 'Request timeout' });
    });
    next();
});

const HEALTH_CHECK_ROUTES = ['/health', '/api/health', '/health/live', '/api/gifs/trending', '/api/gifs/search'];

app.use((req, res, next) => {
    if (HEALTH_CHECK_ROUTES.includes(req.path)) {
        return next();
    }

    if (!dbConnection) {
        return res.status(503).json({
            error: 'Service unavailable',
            message: 'Database is initializing. Please try again in a moment.',
            retry: true
        });
    }
    next();
});

let dbManager = null;
let dbConnection = null;

async function initializeDatabase() {
    try {
        dbManager = new DatabaseConnectionManager(MONGODB_URI, {
            maxPoolSize: 50,
            minPoolSize: 10,
            maxRetries: 3,
            healthCheckIntervalMs: 30000,
            enableHealthCheck: true
        });

        await dbManager.initialize();

        dbConnection = {
            collection: (name) => {
                if (!dbManager.isConnected) throw new Error('Database not connected');
                return dbManager.getCollection(name);
            },
            getDatabase: () => dbManager.getDatabase(),
            get isConnected() { return dbManager.isConnected; }
        };

        console.log('[OK] Database connected with resilience layer (connection pooling, health checks, auto-retry)');

        try {
            const PushNotificationManager = require('../services/PushNotificationManager');
            pushNotificationManager = new PushNotificationManager(dbConnection, admin);
            console.log('[OK] PushNotificationManager initialized');
        } catch (pnErr) {
            console.warn('[WARN] PushNotificationManager init failed, using sendPushNotification fallback:', pnErr.message);
            pushNotificationManager = null;
        }

        if (!global.sessionManager) {
            global.sessionManager = new SessionManager(dbConnection, global.socketIoServer, JWT_SECRET);
        }

        await initializeIndexes();

        try {
            console.log('[MIGRATION] Checking for any public group media records that need updating...');
            const publicGroupMessages = await dbConnection.collection('groupMessages').find({
                mediaShareMode: 'public',
                mediaId: { $ne: null }
            }).toArray();

            if (publicGroupMessages.length > 0) {
                const publicMediaIds = publicGroupMessages.map(m => m.mediaId).filter(id => id);
                if (publicMediaIds.length > 0) {
                    const updateResult = await dbConnection.collection('chatMedia').updateMany(
                        { id: { $in: publicMediaIds }, mediaShareMode: { $ne: 'public' } },
                        { $set: { mediaShareMode: 'public' } }
                    );
                    if (updateResult.modifiedCount > 0) {
                        console.log(`[MIGRATION] Updated ${updateResult.modifiedCount} chatMedia records to public mode`);
                    } else {
                        console.log('[MIGRATION] No chatMedia records needed updating');
                    }
                }
            } else {
                console.log('[MIGRATION] No public group messages found');
            }
        } catch (migrationErr) {
            console.warn('[MIGRATION] Failed to run public media migration:', migrationErr.message);
        }

        return;
    } catch (err) {
        console.error('[ERROR] Database initialization failed:', err.message);
        console.error('[ERROR] Ensure MongoDB is running on Debian server:', `${DEFAULT_MONGODB_HOST}:${DEFAULT_MONGODB_PORT}`);
        console.error('[ERROR] MongoDB URI:', MONGODB_URI.replace(DEFAULT_MONGODB_PASSWORD, '****'));
        dbConnection = null;
        dbManager = null;
        throw err;
    }
}

async function connectDB() {
    return initializeDatabase();
}

async function initializeIndexes() {
    try {
        const usersCol = dbConnection.collection('users');
        const peersCol = dbConnection.collection('peers');
        const logsCol = dbConnection.collection('logs');
        const friendRequestsCol = dbConnection.collection('friendRequests');
        const friendsCol = dbConnection.collection('friends');
        const blockedUsersCol = dbConnection.collection('blockedUsers');
        const messagesCol = dbConnection.collection('messages');

        await usersCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Users index failed:', err.message);
        });
        await usersCol.createIndex({ username: 1 }, { unique: true, sparse: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Username index failed:', err.message);
        });
        await usersCol.createIndex({ email: 1 }, { unique: true, sparse: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Email index failed:', err.message);
        });
        await usersCol.createIndex({ publicTag: 1 }, { unique: true, sparse: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] PublicTag index failed:', err.message);
        });
        await usersCol.createIndex({ 'signupMetadata.ipAddress': 1, createdAt: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Rate limiting index failed:', err.message);
        });

        await peersCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Peers index failed:', err.message);
        });
        await peersCol.createIndex({ name: 1 }, { unique: true, sparse: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Peer name index failed:', err.message);
        });

        await friendRequestsCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Friend requests index failed:', err.message);
        });
        await friendRequestsCol.createIndex({ senderId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] SenderId index failed:', err.message);
        });
        await friendRequestsCol.createIndex({ recipientId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] RecipientId index failed:', err.message);
        });
        await friendRequestsCol.createIndex({ status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Status index failed:', err.message);
        });
        await friendRequestsCol.createIndex({ createdAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] CreatedAt index failed:', err.message);
        });

        await friendsCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Friends index failed:', err.message);
        });
        await friendsCol.createIndex({ userId1: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] UserId1 index failed:', err.message);
        });
        await friendsCol.createIndex({ userId2: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] UserId2 index failed:', err.message);
        });

        await blockedUsersCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Blocked users index failed:', err.message);
        });
        await blockedUsersCol.createIndex({ userId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] BlockedUsers userId index failed:', err.message);
        });
        await blockedUsersCol.createIndex({ blockedUserId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] BlockedUserId index failed:', err.message);
        });

        await messagesCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Messages index failed:', err.message);
        });
        await messagesCol.createIndex({ senderId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Messages senderId index failed:', err.message);
        });
        await messagesCol.createIndex({ recipientId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Messages recipientId index failed:', err.message);
        });
        await messagesCol.createIndex({ createdAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Messages createdAt index failed:', err.message);
        });
        await messagesCol.createIndex({ senderId: 1, recipientId: 1, createdAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Messages compound index failed:', err.message);
        });

        await logsCol.createIndex({ timestamp: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Logs index failed:', err.message);
        });

        const mediaDownloadReqCol = dbConnection.collection('mediaDownloadRequests');
        await mediaDownloadReqCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] MediaDownloadRequests index failed:', err.message);
        });
        await mediaDownloadReqCol.createIndex({ mediaId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] MediaId index failed:', err.message);
        });
        await mediaDownloadReqCol.createIndex({ requesterId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] RequesterId index failed:', err.message);
        });
        await mediaDownloadReqCol.createIndex({ status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Download status index failed:', err.message);
        });
        await mediaDownloadReqCol.createIndex({ expiresAt: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ExpiresAt index failed:', err.message);
        });
        await mediaDownloadReqCol.createIndex({ createdAt: 1 }, { expireAfterSeconds: 86400 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Download TTL index failed:', err.message);
        });

        const chatMediaCol = dbConnection.collection('chatMedia');
        await chatMediaCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChatMedia id index failed:', err.message);
        });
        await chatMediaCol.createIndex({ senderId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChatMedia senderId index failed:', err.message);
        });
        await chatMediaCol.createIndex({ recipientId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChatMedia recipientId index failed:', err.message);
        });
        await chatMediaCol.createIndex({ uploadedAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChatMedia uploadedAt index failed:', err.message);
        });
        await chatMediaCol.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChatMedia TTL index failed:', err.message);
        });

        const groupVotesCol = dbConnection.collection('groupDeletionVotes');
        await groupVotesCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] GroupVotes index failed:', err.message);
        });
        await groupVotesCol.createIndex({ groupId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] GroupId index failed:', err.message);
        });
        await groupVotesCol.createIndex({ status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Vote status index failed:', err.message);
        });
        await groupVotesCol.createIndex({ createdAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Votes createdAt index failed:', err.message);
        });
        await groupVotesCol.createIndex({ groupId: 1, status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Votes compound index failed:', err.message);
        });

        const clearHistoryVotesCol = dbConnection.collection('clearHistoryVotes');
        await clearHistoryVotesCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ClearHistoryVotes index failed:', err.message);
        });
        await clearHistoryVotesCol.createIndex({ groupId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ClearHistoryVotes groupId index failed:', err.message);
        });
        await clearHistoryVotesCol.createIndex({ status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ClearHistoryVotes status index failed:', err.message);
        });
        await clearHistoryVotesCol.createIndex({ groupId: 1, status: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ClearHistoryVotes compound index failed:', err.message);
        });
        await clearHistoryVotesCol.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ClearHistoryVotes TTL index failed:', err.message);
        });

        const channelMsgCol = dbConnection.collection('channelMessages');
        await channelMsgCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChannelMessages index failed:', err.message);
        });
        await channelMsgCol.createIndex({ channelId: 1, createdAt: -1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChannelMessages compound index failed:', err.message);
        });
        await channelMsgCol.createIndex({ isDeleted: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] IsDeleted index failed:', err.message);
        });
        await channelMsgCol.createIndex({ senderId: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] ChannelMessages senderId index failed:', err.message);
        });

        const channelsCol = dbConnection.collection('channels');
        await channelsCol.createIndex({ id: 1 }, { unique: true }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Channels index failed:', err.message);
        });
        await channelsCol.createIndex({ members: 1 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] Channels members index failed:', err.message);
        });
        await channelsCol.createIndex({ adminMembers: 1 }).catch(() => {});

        await usersCol.createIndex({ username: 'text', displayName: 'text' }).catch(() => {});

        await friendRequestsCol.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 }).catch(err => {
            if (err.code !== 48) console.warn('[WARN] FriendRequests TTL index failed:', err.message);
        });

        console.log('[OK] Database indexes initialized');
    } catch (err) {
        console.error('[WARN] Index initialization warning:', err.message);
    }
}

const logsDir = path.join(__dirname, '../logs');
if (!fs.existsSync(logsDir)) fs.mkdirSync(logsDir, { recursive: true });

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
        if (!dbConnection) {
            const logFile = path.join(logsDir, 'admin-events.log');
            const logText = `[${new Date().toISOString()}] ${type.toUpperCase()}: ${message}\n`;
            fs.appendFileSync(logFile, logText);
            return;
        }

        await dbConnection.collection('logs').insertOne(logEntry);

        const logFile = path.join(logsDir, 'admin-events.log');
        const logText = `[${new Date().toISOString()}] ${type.toUpperCase()}: ${message}\n`;
        fs.appendFileSync(logFile, logText);
    } catch (err) {
        const logFile = path.join(logsDir, 'admin-events.log');
        const logText = `[${new Date().toISOString()}] ${type.toUpperCase()}: ${message}\n`;
        fs.appendFileSync(logFile, logText);
        console.error(`Log error for type '${type}':`, err);
    }
}

function generateVerificationCode(length = 6) {
    const digits = '0123456789';
    let code = '';
    for (let i = 0; i < length; i++) {
        code += digits.charAt(Math.floor(Math.random() * digits.length));
    }
    return code;
}

function generateBackupCodes(count = 10) {
    const codes = [];
    for (let i = 0; i < count; i++) {
        const code = crypto.randomBytes(4).toString('hex').toUpperCase();
        codes.push(code);
    }
    return codes;
}

function validateAndSanitize(input, type = 'string', options = {}) {
    if (!input) return null;

    const {
        minLength = 1,
        maxLength = 10000,
        allowedChars = null,
        required = false
    } = options;

    const trimmed = String(input).trim();

    if (required && !trimmed) {
        throw new Error(`${type} is required`);
    }

    if (trimmed.length < minLength || trimmed.length > maxLength) {
        throw new Error(`${type} must be between ${minLength} and ${maxLength} characters`);
    }

    if (allowedChars && !new RegExp(`^[${allowedChars}]+$`).test(trimmed)) {
        throw new Error(`${type} contains invalid characters`);
    }

    return trimmed;
}

async function executeWithTransaction(callback) {
    try {
        if (dbConnection && dbConnection.client && dbConnection.client.topology &&
            (dbConnection.client.topology.description.type === 'ReplicaSetWithPrimary' ||
             dbConnection.client.topology.description.type === 'Sharded')) {
            const session = dbConnection.client.startSession();
            try {
                await session.withTransaction(async () => {
                    return await callback(session);
                });
            } finally {
                await session.endSession();
            }
        } else {
            console.warn('[WARN] MongoDB transactions not available (requires replica set). Executing without transaction.');
            return await callback(null);
        }
    } catch (err) {
        console.error('Transaction error:', err);
        throw err;
    }
}

async function sendVerificationEmail(email, verificationCode, username) {
    if (!transporter) {
        console.log('[WARN] Email service disabled - skipping verification email');
        return true;
    }

    try {
        const mailOptions = {
            from: `${process.env.EMAIL_FROM_NAME} <${process.env.EMAIL_FROM_ADDRESS}>`,
            to: email,
            subject: 'FreeTime - Email Verification Code',
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; border-radius: 10px; color: white;">
                        <h2 style="margin: 0;">Welcome to FreeTime, ${username}!</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9; border-radius: 10px; margin-top: 10px;">
                        <p>Your email verification code is:</p>
                        <div style="background: white; padding: 15px; border-radius: 5px; text-align: center; margin: 20px 0;">
                            <h3 style="font-size: 32px; letter-spacing: 5px; margin: 0; color: #667eea; font-family: monospace;">
                                ${verificationCode}
                            </h3>
                        </div>
                        <p style="color: #666;">This code will expire in 10 minutes.</p>
                        <p style="color: #666; font-size: 12px;">If you didn't request this code, please ignore this email.</p>
                    </div>
                    <div style="text-align: center; color: #999; font-size: 12px; margin-top: 20px;">
                        <p>© 2026 FreeTime. All rights reserved.</p>
                    </div>
                </div>
            `
        };

        const sendSuccess = await transporter.sendMail(mailOptions);
        console.log('[OK] Verification email sent to:', email);
        return true;
    } catch (error) {
        console.error('[ERROR] Email send error:', error);
        return false;
    }
}

async function sendAuthenticatorSetupEmail(email, username, qrCodeDataUrl) {
    if (!transporter) {
        console.log('[WARN] Email service disabled - skipping authenticator setup email');
        console.log(`[INFO] User can scan QR code directly from app`);
        return true;
    }

    try {
        const mailOptions = {
            from: `${process.env.EMAIL_FROM_NAME} <${process.env.EMAIL_FROM_ADDRESS}>`,
            to: email,
            subject: 'FreeTime - Setup Two-Factor Authentication',
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; border-radius: 10px; color: white;">
                        <h2 style="margin: 0;">Two-Factor Authentication Setup</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9; border-radius: 10px; margin-top: 10px;">
                        <p>Hi ${username},</p>
                        <p>Scan this QR code with your authenticator app (Google Authenticator, Microsoft Authenticator, Authy, etc.):</p>
                        <div style="text-align: center; margin: 20px 0;">
                            <img src="${qrCodeDataUrl}" alt="QR Code" style="max-width: 300px; border: 2px solid #667eea; padding: 10px; border-radius: 5px;">
                        </div>
                        <p style="color: #666;">After scanning, enter the 6-digit code from your authenticator app to complete setup.</p>
                        <p style="color: #999; font-size: 12px;">Keep your backup codes in a safe place. You'll need them if you lose access to your authenticator.</p>
                    </div>
                </div>
            `
        };

        const sendSuccess = await transporter.sendMail(mailOptions);
        console.log('[OK] Authenticator setup email sent to:', email);
        return true;
    } catch (error) {
        console.error('[ERROR] Email send error:', error);
        return false;
    }
}

async function sendBackupCodesEmail(email, username, backupCodes) {
    if (!transporter) {
        console.log('[WARN] Email service disabled - not sending backup codes via email');
        return true;
    }

    try {
        const codesHtml = backupCodes
            .map((code, index) => `<div style="padding: 5px 0;">${index + 1}. <code style="font-family: monospace; background: white; padding: 3px 8px; border-radius: 3px;">${code}</code></div>`)
            .join('');

        const mailOptions = {
            from: `${process.env.EMAIL_FROM_NAME} <${process.env.EMAIL_FROM_ADDRESS}>`,
            to: email,
            subject: 'FreeTime - Backup Codes for Two-Factor Authentication',
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; border-radius: 10px; color: white;">
                        <h2 style="margin: 0;">Your Backup Codes</h2>
                    </div>
                    <div style="padding: 20px; background: #f9f9f9; border-radius: 10px; margin-top: 10px;">
                        <p style="color: #d32f2f; font-weight: bold;">[WARN] Important: Save these codes in a safe place!</p>
                        <p>Each code can be used once if you lose access to your authenticator app:</p>
                        <div style="background: white; padding: 15px; border-radius: 5px; margin: 20px 0; font-size: 13px;">
                            ${codesHtml}
                        </div>
                        <p style="color: #666; font-size: 12px;">Do not share these codes with anyone. Store them securely.</p>
                    </div>
                </div>
            `
        };

        const sendSuccess = await transporter.sendMail(mailOptions);
        console.log('[OK] Backup codes email sent to:', email);
        return true;
    } catch (error) {
        console.error('[ERROR] Email send error:', error);
        return false;
    }
}

async function verifyToken(req, res, next) {
    const token = req.headers.authorization?.replace('Bearer ', '');

    if (!token) {
        console.warn('[AUTH] No token provided in Authorization header');
        return res.status(401).json({ error: 'No token provided' });
    }

    try {
        let decoded;
        try {
            decoded = jwt.verify(token, JWT_SECRET);
        } catch (jwtErr) {
            console.warn(`[AUTH] JWT verification failed: ${jwtErr.message}`);
            return res.status(401).json({ error: 'Invalid token', details: jwtErr.message });
        }

        const userId = decoded.userId || decoded.id;
        if (!userId && decoded.role !== 'ADMIN') {
            console.warn('[AUTH] Token missing userId/id and not an admin token');
            return res.status(401).json({ error: 'Invalid token: missing userId' });
        }

        // single active device per account
        if (userId && decoded.deviceId && dbConnection) {
            try {
                const user = await dbConnection.collection('users').findOne({ id: userId });
                if (user && user.currentDeviceId && user.currentDeviceId !== decoded.deviceId) {
                    const isOtherDeviceActive = await global.sessionManager.isUserConnected(userId, decoded.deviceId);

                    if (isOtherDeviceActive) {
                        console.warn(`[AUTH] Device mismatch for user ${userId} (ACTIVE session on other device): token=${decoded.deviceId} stored=${user.currentDeviceId}`);
                        return res.status(401).json({
                            error: 'Token invalid for this device',
                            code: 'CONCURRENT_SESSION_ACTIVE',
                            message: 'Account is actively being used on another device.'
                        });
                    } else {
                        console.log(`[AUTH] Auto-takeover for user ${userId} from device ${decoded.deviceId} (Previous device ${user.currentDeviceId} was inactive)`);
                        await dbConnection.collection('users').updateOne(
                            { id: userId },
                            { $set: { currentDeviceId: decoded.deviceId } }
                        );
                        await dbConnection.collection('activeSessions').updateOne(
                            { userId: userId, deviceId: decoded.deviceId },
                            { $set: { lastActivityTime: new Date() } }
                        );
                    }
                }
            } catch (dbErr) {
                console.error('[AUTH] Database error checking device ID:', dbErr.message);
            }
        }

        req.user = decoded;
        if (!req.user.userId && req.user.id) {
            req.user.userId = req.user.id;
        }

        if (userId && dbConnection) {
            dbConnection.collection('users').updateOne(
                { id: userId },
                {
                    $set: {
                        lastActivityAt: new Date(),
                        isOnline: true
                    }
                }
            ).catch(err => console.error('[AUTH] Failed to update user activity:', err.message));
        }

        next();
    } catch (err) {
        console.error('[AUTH] Unexpected error in verifyToken:', err.message);
        res.status(401).json({ error: 'Authentication failed', details: err.message });
    }
}

app.get('/health', (req, res) => {
    try {
        const uptime = process.uptime();
        res.status(dbConnection ? 200 : 503).json({
            status: dbConnection ? 'healthy' : 'degraded',
            uptime: uptime,
            database: dbConnection ? 'connected' : 'disconnected',
            timestamp: new Date()
        });
    } catch (err) {
        res.status(503).json({ status: 'unhealthy', error: err.message });
    }
});

app.get('/api/health', (req, res) => {
    try {
        const uptime = process.uptime();
        res.status(dbConnection ? 200 : 503).json({
            status: dbConnection ? 'healthy' : 'degraded',
            uptime: uptime,
            database: dbConnection ? 'connected' : 'disconnected',
            timestamp: new Date()
        });
    } catch (err) {
        res.status(503).json({ status: 'unhealthy', error: err.message });
    }
});

app.get('/health/live', (req, res) => {
    res.status(200).json({ status: 'alive', timestamp: new Date() });
});

app.get('/health/ready', (req, res) => {
    const isReady = dbConnection !== null;
    res.status(isReady ? 200 : 503).json({
        status: isReady ? 'ready' : 'not_ready',
        timestamp: new Date()
    });
});

app.get('/api/token/verify', verifyToken, (req, res) => {
    try {
        res.json({
            success: true,
            token_valid: true,
            decoded: {
                userId: req.user?.userId,
                username: req.user?.username,
                twoFaRequired: req.user?.twoFaRequired,
                deviceId: req.user?.deviceId,
                iat: req.user?.iat,
                exp: req.user?.exp
            },
            message: 'Token is valid and was successfully decoded',
            timestamp: new Date()
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/socket.io/test', (req, res) => {
    console.log('[DIAG] Socket.IO test endpoint called by client');
    try {
        const socketIoStatus = {
            timestamp: new Date().toISOString(),
            socketIoInitialized: !!global.socketIoServer,
            socketIoServer: !!global.socketIoServer,
            globalIo: !!global.io,
            wsClientsMap: !!global.wsClients,
            wsUserMap: !!global.wsUserMap,
            connectedClients: global.socketIoServer?.engine?.clientsCount || 0,
            socketIoEngine: {
                initialized: !!global.socketIoServer?.engine,
                client_count: global.socketIoServer?.engine?.clientsCount || 0
            },
            services: {
                socketio: global.socketIoServer ? 'OK' : 'NOT_INITIALIZED',
                wsClients: global.wsClients ? 'OK' : 'NOT_INITIALIZED'
            },
            nodeVersion: process.version,
            socketIOVersion: require('socket.io/package.json').version,
            uptime: process.uptime(),
            message: 'Socket.IO diagnostic check'
        };

        const status = socketIoStatus.socketIoInitialized ? 200 : 503;
        console.log('[DIAG] Socket.IO test response:', socketIoStatus);
        res.status(status).json(socketIoStatus);
    } catch (err) {
        res.status(500).json({
            error: 'Socket.IO diagnostic error',
            message: err.message
        });
    }
});

app.get('/api/socket.io/engine-check', (req, res) => {
    console.log('[DIAG] Socket.IO engine check called');

    if (!global.socketIoServer) {
        console.error('[ERROR] Socket.IO server not initialized');
        return res.status(503).json({
            error: 'Socket.IO not initialized',
            timestamp: new Date().toISOString()
        });
    }

    try {
        const engineInfo = {
            haEngine: !!global.socketIoServer.engine,
            engineType: global.socketIoServer.engine?.constructor?.name || 'unknown',
            clientsConnected: global.socketIoServer.engine?.clientsCount || 0,
            transports: global.socketIoServer.transports || [],
            socketIoPath: global.socketIoServer.opts?.path || '/socket.io',
            allowUpgrades: global.socketIoServer.opts?.allowUpgrades,
            pingInterval: global.socketIoServer.opts?.pingInterval || 25000,
            pingTimeout: global.socketIoServer.opts?.pingTimeout || 60000,
            timestamp: new Date().toISOString()
        };

        console.log('[DIAG] Engine check response:', engineInfo);
        res.json(engineInfo);
    } catch (err) {
        console.error('[ERROR] Engine check failed:', err.message);
        res.status(500).json({
            error: 'Engine check failed',
            message: err.message,
            timestamp: new Date().toISOString()
        });
    }
});

app.get('/api/polling/test', (req, res) => {
    res.json({
        status: 'ok',
        message: 'HTTP polling endpoint is working',
        timestamp: new Date().toISOString(),
        serverInfo: {
            socketIoInitialized: !!global.socketIoServer,
            uptime: process.uptime(),
            databaseConnected: !!dbConnection
        }
    });
});

app.get('/api/diagnostic/signup', async (req, res) => {
    try {
        const diagnostic = {
            timestamp: new Date(),
            serverReady: dbConnection !== null,
            services: {
                database: dbConnection !== null ? 'OK' : 'FAILED',
                rateLimiter: signupLimiter ? 'OK' : 'FAILED'
            },
            config: {
                signupEnabled: true,
                twoFaRequired: true,
                twoFaMethods: ['authenticator'],
                passwordMinLength: 8,
                usernameMinLength: 3
            },
            message: 'Signup endpoint is operational',
            nextSteps: [
                'POST /api/signup with username, email, displayName, password, confirmPassword',
                'Server will respond with tempToken for 2FA setup',
                'Use tempToken with POST /api/setup-authenticator'
            ]
        };

        res.status(200).json(diagnostic);
    } catch (err) {
        res.status(500).json({
            timestamp: new Date(),
            error: 'Diagnostic check failed',
            details: err.message
        });
    }
});

async function checkFriendStatus(userId1, userId2) {
    if (!dbConnection || !dbConnection.collection) return 'not-friends';

    try {
        const friend = await dbConnection.collection('friends').findOne({
            $or: [
                { userId1, userId2 },
                { userId1: userId2, userId2: userId1 }
            ]
        });
        if (friend) return 'friends';

        const request = await dbConnection.collection('friendRequests').findOne({
            senderId: userId1,
            recipientId: userId2,
            status: 'pending'
        });
        if (request) return 'pending';

        const incomingRequest = await dbConnection.collection('friendRequests').findOne({
            senderId: userId2,
            recipientId: userId1,
            status: 'pending'
        });
        if (incomingRequest) return 'requested';

        return 'not-friends';
    } catch (err) {
        console.error('Error checking friend status:', err);
        return 'not-friends';
    }
}

function canViewPrivateField(fieldPrivacySetting, viewerId, targetUserId, friendStatus) {
    if (viewerId === targetUserId) return true;

    if (fieldPrivacySetting === 'nobody') return false;
    if (fieldPrivacySetting === 'everyone') return true;
    if (fieldPrivacySetting === 'friends') return friendStatus === 'friends';

    return false;
}

async function getUserProfileRespectingPrivacy(user, requestUserId) {
    if (!user) return null;

    const targetUserId = user.id;
    const isOwnProfile = requestUserId === targetUserId;

    const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, targetUserId);

    const privacySettings = user.profile?.privacySettings || {};
    const settings = {
        lastSeen: privacySettings.lastSeen || 'friends',
        onlineStatus: privacySettings.onlineStatus || 'friends',
        bio: privacySettings.bio || 'friends',
        groups: privacySettings.groups || 'friends',
        aboutBio: privacySettings.aboutBio || 'friends'
    };

    const now = Date.now();
    const lastActivity = user.lastActivityAt ? new Date(user.lastActivityAt).getTime() : null;
    const isOnline = lastActivity && (now - lastActivity) < 5 * 60 * 1000;

    function getLastSeen() {
        if (isOnline) return 'Online now';
        if (lastActivity) {
            const minutesAgo = Math.floor((now - lastActivity) / (60 * 1000));
            if (minutesAgo < 1) return 'Just now';
            if (minutesAgo < 60) return `${minutesAgo}m ago`;
            const hoursAgo = Math.floor(minutesAgo / 60);
            return hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
        }
        return 'Offline';
    }

    const response = {
        userId: user.id,
        username: user.username || 'Unknown',
        displayName: user.profile?.displayName || user.displayName || user.name || user.username || 'User',
        avatar: user.profile?.profileImageUrl || user.profile?.profileImage || null,
        role: user.role || 'User',
        tags: Array.isArray(user.profile?.tags) ? user.profile.tags : [],
        createdAt: user.createdAt ? user.createdAt.getTime() : Date.now()
    };

    if (isOwnProfile) {
        response.email = user.email || '';
    }

    if (canViewPrivateField(settings.bio, requestUserId, targetUserId, friendStatus)) {
        response.bio = user.profile?.bio || '';
    } else {
        response.bio = isOwnProfile ? (user.profile?.bio || '') : '';
    }

    if (canViewPrivateField(settings.aboutBio, requestUserId, targetUserId, friendStatus)) {
        response.aboutBio = user.profile?.aboutBio || user.profile?.bio || '';
    } else if (isOwnProfile) {
        response.aboutBio = user.profile?.aboutBio || user.profile?.bio || '';
    }

    if (canViewPrivateField(settings.onlineStatus, requestUserId, targetUserId, friendStatus)) {
        response.isOnline = isOnline;
        response.actualOnlineStatus = isOnline ? 'online' : 'offline';
    } else if (isOwnProfile) {
        response.isOnline = isOnline;
        response.actualOnlineStatus = isOnline ? 'online' : 'offline';
    } else {
        response.isOnline = false;
        response.actualOnlineStatus = 'offline';
    }

    if (canViewPrivateField(settings.lastSeen, requestUserId, targetUserId, friendStatus)) {
        response.lastSeen = getLastSeen();
    } else if (isOwnProfile) {
        response.lastSeen = getLastSeen();
    } else {
        response.lastSeen = 'Hidden';
    }

    response.status = user.profile?.status || 'Available';

    if (isOwnProfile) {
        response.privacySettings = settings;
    }

    response.friendStatus = friendStatus;

    return response;
}

app.post('/api/admin/login', async (req, res) => {
    const { username, password } = req.body;

    if (!username || !password) {
        return res.status(400).json({ error: 'Username and password required' });
    }

    try {
        console.log(`[DEBUG] Login attempt: username=${username}`);

        if (!JWT_SECRET) {
            console.error('[ERROR] JWT_SECRET not configured - cannot generate tokens');
            return res.status(503).json({
                error: 'Service unavailable',
                message: 'Authentication service not properly configured. Please contact administrator.'
            });
        }

        if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
            console.log(`[DEBUG] Admin credentials matched, generating token...`);

            let token;
            try {
                token = jwt.sign(
                    { username, role: 'ADMIN' },
                    JWT_SECRET,
                    { expiresIn: '24h' }
                );
                console.log(`[DEBUG] Token generated successfully (length: ${token.length})`);
            } catch (jwtErr) {
                console.error('[ERROR] JWT signing failed:', jwtErr.message);
                return res.status(500).json({
                    error: 'Authentication error',
                    details: 'Failed to generate authentication token',
                    debug: process.env.NODE_ENV === 'development' ? jwtErr.message : undefined
                });
            }

            try {
                if (dbConnection && dbConnection.isConnected) {
                    await logEvent('auth', `Admin login successful: ${username}`);
                    await dbConnection.collection('admins').updateOne(
                        { username },
                        { $set: { username, role: 'ADMIN', lastLogin: new Date() } },
                        { upsert: true }
                    );
                }
            } catch (logErr) {
                console.warn(`[WARN] Failed to log event / upsert admin: ${logErr.message}`);
            }

            return res.json({
                token,
                user: {
                    username,
                    role: 'ADMIN',
                    email: `${username}@freetime.local`
                }
            });
        }

        if (!dbConnection || !dbConnection.isConnected) {
            console.error('[ERROR] Login failed: Database not connected');
            return res.status(503).json({
                error: 'Service unavailable',
                message: 'Database connection is not ready. Please check MongoDB status.'
            });
        }

        const adminUser = await dbConnection.collection('users').findOne({
            username,
            role: 'ADMIN'
        });
        if (adminUser && await bcrypt.compare(password, adminUser.password)) {
            console.log(`[DEBUG] Database admin credentials matched, generating token...`);

            let token;
            try {
                token = jwt.sign(
                    { id: adminUser.id, username, role: 'ADMIN' },
                    JWT_SECRET,
                    { expiresIn: '24h' }
                );
                console.log(`[DEBUG] Token generated successfully (length: ${token.length})`);
            } catch (jwtErr) {
                console.error('[ERROR] JWT signing failed:', jwtErr.message);
                return res.status(500).json({
                    error: 'Authentication error',
                    details: 'Failed to generate authentication token',
                    debug: process.env.NODE_ENV === 'development' ? jwtErr.message : undefined
                });
            }

            try {
                await logEvent('auth', `Admin login successful: ${username}`);
            } catch (logErr) {
                console.warn(`[WARN] Failed to log event: ${logErr.message}`);
            }

            return res.json({
                token,
                user: {
                    id: adminUser.id,
                    username,
                    email: adminUser.email,
                    role: 'ADMIN'
                }
            });
        }

        console.log(`[DEBUG] Invalid credentials for user: ${username}`);
        try {
            await logEvent('auth', `Failed login attempt: ${username}`);
        } catch (logErr) {
            console.warn(`[WARN] Failed to log event: ${logErr.message}`);
        }
        res.status(401).json({ error: 'Invalid credentials' });
    } catch (err) {
        console.error('[ERROR] Login endpoint error:', err);
        console.error('[ERROR] Stack trace:', err.stack);
        res.status(500).json({ error: 'Login failed', details: err.message });
    }
});

app.get('/api/admin/monitor', verifyToken, async (req, res) => {
    if (req.user.role !== 'ADMIN') {
        return res.status(403).json({ error: 'Admin access required' });
    }

    try {
        const uptime = process.uptime();
        const systemUptime = os.uptime();
        const totalMem = os.totalmem();
        const freeMem = os.freemem();
        const usedMem = totalMem - freeMem;

        const services = {
            api: {
                online: true,
                responseTime: 0,
                lastCheck: new Date(),
                status: 'healthy'
            },
            mongodb: {
                online: dbConnection ? dbConnection.isConnected : false,
                lastCheck: new Date(),
                status: (dbConnection && dbConnection.isConnected) ? 'healthy' : 'disconnected'
            },
            websocket: {
                online: global.io ? true : false,
                clients: global.io ? (global.io.engine.clientsCount || 0) : 0,
                lastCheck: new Date()
            }
        };

        const systemInfo = {
            uptime: systemUptime,
            nodeUptime: uptime,
            cpus: os.cpus().length,
            totalMemory: totalMem,
            freeMemory: freeMem,
            usedMemory: usedMem,
            memoryPercent: (usedMem / totalMem * 100).toFixed(2),
            platform: os.platform(),
            nodeVersion: process.version,
            loadAverage: os.loadavg()
        };

        res.json({
            timestamp: new Date().toISOString(),
            services: services,
            system: systemInfo,
            summary: {
                totalServices: 3,
                onlineServices: [services.api.online, services.mongodb.online, services.websocket.online].filter(Boolean).length
            }
        });
    } catch (err) {
        console.error('[ADMIN] Monitor error:', err);
        res.status(500).json({ error: 'Failed to retrieve monitor data', details: err.message });
    }
});

app.get('/api/admin/system', verifyToken, (req, res) => {
    if (req.user.role !== 'ADMIN') {
        return res.status(403).json({ error: 'Admin access required' });
    }

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
        loadAvg: os.loadavg(),
        timestamp: new Date().toISOString()
    });
});

app.get('/api/admin/config', verifyToken, (req, res) => {
    if (req.user.role !== 'ADMIN') {
        return res.status(403).json({ error: 'Admin access required' });
    }

    res.json({
        node_env: process.env.NODE_ENV || 'development',
        port: process.env.PORT || 443,
        domain: process.env.DOMAIN || 'localhost',
        db_type: 'MongoDB',
        features: {
            push_notifications: !!firebaseInitialized,
            websockets: !!global.io,
            two_factor_auth: true
        },
        timestamp: new Date().toISOString()
    });
});

app.post('/api/login', loginLimiter, async (req, res) => {
    const { username, password, force = true } = req.body;

    if (!username || !password) {
        return res.status(400).json({ error: 'Username and password required' });
    }

    const trimmedUsername = String(username).trim();
    if (trimmedUsername.length < 3 || trimmedUsername.length > 100) {
        return res.status(400).json({ error: 'Username must be between 3 and 100 characters' });
    }

    if (String(password).length < 6 || String(password).length > 1000) {
        return res.status(400).json({ error: 'Invalid password format' });
    }

    if (!/^[a-zA-Z0-9._@-]+$/.test(trimmedUsername)) {
        return res.status(400).json({ error: 'Username contains invalid characters' });
    }

    try {
        const user = await dbConnection.collection('users').findOne({
            $or: [
                { username: trimmedUsername.toLowerCase() },
                { email: trimmedUsername.toLowerCase() }
            ]
        });

        if (!user) {
            await logEvent('auth', `Failed login attempt: ${trimmedUsername} (user not found)`);
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        const passwordMatch = await bcrypt.compare(password, user.password);

        if (!passwordMatch) {
            await logEvent('auth', `Failed login attempt: ${trimmedUsername} (wrong password)`);
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        const twoFaEnabled = user.twoFactorAuth && user.twoFactorAuth.enabled && user.twoFactorAuth.accountVerified;
        const twoFaSetupRequired = user.twoFactorAuth && user.twoFactorAuth.mandatorySetup && !twoFaEnabled;
        const twoFaMandatory = user.twoFactorAuth && user.twoFactorAuth.mandatorySetup;
        const incomingDevice = req.body.deviceId || '';

        if (twoFaEnabled) {
            const tempToken = jwt.sign(
                {
                    userId: user.id,
                    username: user.username,
                    twoFaRequired: true,
                    setupRequired: false,
                    setupMode: false,
                    twoFaMethod: 'authenticator',
                    deviceId: incomingDevice
                },
                JWT_SECRET,
                { expiresIn: '10m' }
            );

            return res.json({
                success: true,
                requiresTwoFactor: true,
                tempToken: tempToken,
                twoFaMethod: 'authenticator',
                nextStep: '/api/verify-login-totp',
                setupRequired: false,
                message: 'Please enter your authenticator app code'
            });
        }

        if (twoFaSetupRequired) {
            const tempToken = jwt.sign(
                {
                    userId: user.id,
                    username: user.username,
                    twoFaRequired: true,
                    setupRequired: true,
                    setupMode: true,
                    twoFaMethod: 'authenticator',
                    deviceId: incomingDevice
                },
                JWT_SECRET,
                { expiresIn: '30m' }
            );

            return res.json({
                success: true,
                requiresTwoFactor: true,
                tempToken: tempToken,
                twoFaMethod: 'authenticator',
                nextStep: '/api/setup-authenticator',
                setupRequired: true,
                message: 'Please set up authenticator app 2FA to continue'
            });
        }

        if (!twoFaMandatory) {
            const deviceId = incomingDevice || crypto.randomBytes(16).toString('hex');
            const deviceInfo = {
                platform: req.body.deviceInfo?.platform || 'Unknown',
                deviceName: req.body.deviceInfo?.deviceName || 'Unknown Device',
                osVersion: req.body.deviceInfo?.osVersion || 'Unknown',
                appVersion: req.body.deviceInfo?.appVersion || 'Unknown',
                ipAddress: req.ip || req.connection.remoteAddress
            };

            const sessionResult = await global.sessionManager.createSession(
                user.id,
                deviceId,
                deviceInfo,
                force
            );

            const authToken = jwt.sign(
                {
                    userId: user.id,
                    username: user.username,
                    twoFaRequired: false,
                    deviceId: deviceId,
                    sessionId: sessionResult.sessionId,
                    sessionToken: sessionResult.sessionToken
                },
                JWT_SECRET,
                { expiresIn: '30d' }
            );

            const userData = {
                id: user.id,
                userId: user.id,
                _id: user._id || user.id,
                username: user.username,
                email: user.email,
                name: user.name || user.displayName,
                displayName: user.displayName || user.name,
                avatarUrl: user.avatarUrl,
                role: user.role,
                tags: user.tags || [],
                bio: user.bio || user.profile?.bio,
                status: user.status,
                isOnline: user.isOnline,
                createdAt: user.createdAt ? user.createdAt.getTime() : Date.now()
            };

            return res.json({
                success: true,
                requiresTwoFactor: false,
                token: authToken,
                sessionId: sessionResult.sessionId,
                deviceId: deviceId,
                userId: user.id,
                username: user.username,
                user: userData,
                message: 'Login successful'
            });
        }

        const incomingDeviceFallback = req.body.deviceId || '';
        if (incomingDeviceFallback) {
            const deviceUpdateResult = await dbConnection.collection('users').updateOne(
                { id: user.id },
                { $set: { currentDeviceId: incomingDeviceFallback } }
            );
            if (deviceUpdateResult.matchedCount === 0) {
                return res.status(404).json({ error: 'User not found' });
            }
        }
        const authToken = jwt.sign(
            {
                userId: user.id,
                username: user.username,
                twoFaRequired: false,
                deviceId: incomingDeviceFallback
            },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        const fallbackUserData = {
            id: user.id,
            userId: user.id,
            _id: user._id || user.id,
            username: user.username,
            email: user.email,
            name: user.name || user.displayName,
            displayName: user.displayName || user.name,
            avatarUrl: user.avatarUrl,
            role: user.role,
            tags: user.tags || [],
            bio: user.bio || user.profile?.bio,
            status: user.status,
            isOnline: user.isOnline,
            createdAt: user.createdAt ? user.createdAt.getTime() : Date.now()
        };

        return res.json({
            success: true,
            requiresTwoFactor: false,
            token: authToken,
            userId: user.id,
            username: user.username,
            user: fallbackUserData,
            userId: user.id,
            username: user.username,
            message: 'Login successful'
        });
    } catch (err) {
        await logEvent('auth', `Login error: ${err.message}`);

        if (err.code === 'CONCURRENT_LOGIN') {
            return res.status(403).json({
                success: false,
                error: 'Account already in use by another device',
                code: 'CONCURRENT_LOGIN',
                message: `This account is already logged in on ${err.existingDevice}. Multi-device login is disabled for security.`
            });
        }

        res.status(500).json({ error: 'Login failed', details: err.message });
    }
});

app.post('/api/signup', signupLimiter, async (req, res) => {
    const { username, email, displayName, password, confirmPassword, twoFaMethod } = req.body;

    try {
        if (!username || !email || !password || !confirmPassword) {
            return res.status(400).json({
                success: false,
                error: 'Username, email, password, and confirm password are required'
            });
        }

        if (username.length < 3) {
            return res.status(400).json({
                success: false,
                error: 'Username must be at least 3 characters'
            });
        }
        if (!/^[a-zA-Z0-9_]+$/.test(username)) {
            return res.status(400).json({
                success: false,
                error: 'Username can only contain letters, numbers, and underscores'
            });
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            return res.status(400).json({
                success: false,
                error: 'Invalid email format'
            });
        }

        if (password.length < 8) {
            return res.status(400).json({
                success: false,
                error: 'Password must be at least 8 characters'
            });
        }

        if (!/(?=.*[a-zA-Z])(?=.*\d)(?=.*[@$%^&*!])/.test(password)) {
            return res.status(400).json({
                success: false,
                error: 'Password must contain letters, numbers, and special characters (@$%^&*!)'
            });
        }

        if (password !== confirmPassword) {
            return res.status(400).json({
                success: false,
                error: 'Passwords do not match'
            });
        }

        const selectedTwoFaMethod = 'authenticator';

        if (twoFaMethod && twoFaMethod !== 'authenticator') {
            return res.status(400).json({
                success: false,
                error: 'Only authenticator app 2FA is supported. Please use Google Authenticator, Microsoft Authenticator, or any TOTP-compatible app.'
            });
        }

        const existingUser = await dbConnection.collection('users').findOne({
            $or: [
                { email: email.toLowerCase() },
                { username: username.toLowerCase() }
            ]
        });

        if (existingUser) {
            const field = existingUser.email === email.toLowerCase() ? 'Email' : 'Username';
            return res.status(409).json({
                success: false,
                error: `${field} already registered`
            });
        }

        const now = new Date();
        const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate());

        if (!req.body.deviceFingerprint) {
            return res.status(400).json({
                success: false,
                error: 'Device information is required for security verification',
                details: 'Please ensure app includes device fingerprint data'
            });
        }

        const deviceFingerprint = req.body.deviceFingerprint;
        const deviceId = deviceFingerprint.deviceId || 'unknown';
        const androidId = deviceFingerprint.androidId || 'unknown';
        const hardwareSerial = deviceFingerprint.hardwareSerial || 'unknown';
        const deviceModel = deviceFingerprint.deviceModel || 'unknown';

        let primaryDeviceKey = androidId;

        let secondaryDeviceKey = deviceId;

        let tertiaryDeviceKey = hardwareSerial;

        const isEmulator = deviceFingerprint.buildFingerprint?.includes('generic') ||
                           deviceFingerprint.buildFingerprint?.includes('test-keys') ||
                           deviceModel?.includes('Emulator') ||
                           deviceModel?.includes('SDK');

        if (isEmulator) {
            await logEvent('emulator_signup_attempt', `Emulator signup blocked: ${deviceModel}`, null, {
                email: email,
                deviceModel: deviceModel,
                androidId: androidId
            });
            return res.status(403).json({
                success: false,
                error: 'Signup from emulators or virtual devices is not allowed',
                message: 'Please use a physical Android device to create an account'
            });
        }

        if (primaryDeviceKey && primaryDeviceKey !== 'unknown' && primaryDeviceKey !== '9774d56d682e549c') {
            const recentSignupsByAndroidId = await dbConnection.collection('users').countDocuments({
                'deviceFingerprint.androidId': primaryDeviceKey,
                createdAt: { $gte: threeMonthsAgo }
            });

            if (recentSignupsByAndroidId >= 1) {
                await logEvent('rate_limit_exceeded', `Device rate limit exceeded (ANDROID_ID): ${primaryDeviceKey}`, null, {
                    email: email,
                    androidId: primaryDeviceKey,
                    limitType: 'android_id',
                    previousSignups: recentSignupsByAndroidId
                });
                return res.status(429).json({
                    success: false,
                    error: 'Device limit exceeded',
                    message: 'This device has already been used to create an account. You can create a new account in 3 months.',
                    limitType: 'device',
                    retryAfter: Math.ceil((threeMonthsAgo.getTime() + 90*24*60*60*1000 - now.getTime()) / 1000)
                });
            }
        }

        if (tertiaryDeviceKey && tertiaryDeviceKey !== 'unknown' && tertiaryDeviceKey !== primaryDeviceKey) {
            const recentSignupsBySerial = await dbConnection.collection('users').countDocuments({
                'deviceFingerprint.hardwareSerial': tertiaryDeviceKey,
                createdAt: { $gte: threeMonthsAgo }
            });

            if (recentSignupsBySerial >= 1) {
                await logEvent('rate_limit_exceeded', `Device rate limit exceeded (Serial): ${tertiaryDeviceKey}`, null, {
                    email: email,
                    hardwareSerial: tertiaryDeviceKey,
                    limitType: 'hardware_serial',
                    previousSignups: recentSignupsBySerial
                });
                return res.status(429).json({
                    success: false,
                    error: 'Device limit exceeded',
                    message: 'This device has already been used to create an account. Please wait before creating another account.',
                    limitType: 'device',
                    retryAfter: Math.ceil((threeMonthsAgo.getTime() + 90*24*60*60*1000 - now.getTime()) / 1000)
                });
            }
        }

        if (deviceModel && deviceModel !== 'unknown') {
            const recentSignupsByModel = await dbConnection.collection('users').countDocuments({
                'deviceFingerprint.deviceModel': deviceModel,
                createdAt: { $gte: new Date(now.getTime() - 24*60*60*1000) }
            });

            if (recentSignupsByModel > 5) {
                await logEvent('suspicious_activity', `Many signups from same device model: ${deviceModel}`, null, {
                    email: email,
                    deviceModel: deviceModel,
                    signupsInLast24h: recentSignupsByModel
                });
            }
        }

        let clientIP = req.ip;
        if (!clientIP || clientIP.includes(':')) {
            clientIP = (req.headers['x-forwarded-for'])?.split(',')[0].trim() ||
                      req.socket?.remoteAddress ||
                      req.connection?.remoteAddress ||
                      'unknown';
        }

        const userAgent = req.headers['user-agent'] || 'Unknown';
        const isSuspicious = detectSuspiciousActivity(userAgent, req.body.deviceFingerprint);

        if (isSuspicious.isBot) {
            await logEvent('bot_detection', `Bot activity detected: ${isSuspicious.reason}`, null, {
                email: email,
                ipAddress: clientIP,
                userAgent: userAgent,
                reason: isSuspicious.reason
            });
            return res.status(403).json({
                success: false,
                error: 'Access denied',
                message: 'Suspicious activity detected. Please contact support if you believe this is an error.'
            });
        }

        const newUser = {
            id: uuidv4(),
            username: username.toLowerCase(),
            email: email.toLowerCase(),
            password: await bcrypt.hash(password, 10),
            name: displayName || username,
            role: 'USER',
            tags: [],
            isOnline: false,
            lastSeen: new Date(),
            createdAt: new Date(),
            updatedAt: new Date(),

            signupMetadata: {
                ipAddress: clientIP,
                userAgent: userAgent,
                signupDate: new Date()
            },

            deviceFingerprint: req.body.deviceFingerprint ? {
                deviceId: req.body.deviceFingerprint.deviceId,
                deviceModel: req.body.deviceFingerprint.deviceModel,
                deviceBrand: req.body.deviceFingerprint.deviceBrand,
                osVersion: req.body.deviceFingerprint.osVersion,
                appVersion: req.body.deviceFingerprint.appVersion,
                buildFingerprint: req.body.deviceFingerprint.buildFingerprint,
                androidId: req.body.deviceFingerprint.androidId,
                timestamp: req.body.deviceFingerprint.timestamp
            } : null,

            twoFactorAuth: {
                enabled: false,
                method: selectedTwoFaMethod,
                verifiedAt: null,
                authenticatorSecret: null,
                authenticatorBackupCodes: [],
                emailVerificationCode: null,
                emailVerificationAttempts: 0,
                emailVerificationExpiresAt: null,
                accountVerified: false,
                verificationMethod: null,
                verificationCompletedAt: null,
                mandatorySetup: true
            },

            monthlyCreationLimit: {
                enabled: true,
                maxUsersPerMonth: 1,
                currentMonthUsers: 0,
                lastResetDate: new Date()
            },
            deviceInfo: {
                deviceId: null,
                deviceType: null,
                deviceName: null,
                lastSeen: null,
                isActive: false,
                registeredAt: null
            },
            usageStats: {
                totalLogins: 0,
                totalMessages: 0,
                totalCalls: 0,
                totalConnections: 0,
                lastActivity: new Date(),
                dataUsage: {
                    uploaded: 0,
                    downloaded: 0
                }
            }
        };

        let userInserted = false;
        try {
            await executeWithTransaction(async (session) => {
                await dbConnection.collection('users').insertOne(newUser, { session });
            });
            userInserted = true;
        } catch (dbErr) {
            console.error('Failed to create user:', dbErr);
            await logEvent('user_signup_failed', `User registration failed: ${dbErr.message}`, null, {
                email: email,
                error: dbErr.message
            });
            if (dbErr.code === 11000) {
                const errMsg = dbErr.message || '';
                const field = errMsg.includes('email') ? 'Email' : 'Username';
                return res.status(409).json({
                    success: false,
                    error: `${field} already registered`
                });
            }
            return res.status(500).json({
                success: false,
                error: 'Failed to create account. Please try again.'
            });
        }

        if (!userInserted) {
            return res.status(500).json({
                success: false,
                error: 'Failed to create account. Please try again.'
            });
        }

        await logEvent('user_signup_started', `User registration started: ${email}`, newUser.id, {
            email: email,
            username: username,
            twoFaMethod: twoFaMethod
        });

        const tempToken = jwt.sign(
            {
                userId: newUser.id,
                username: newUser.username,
                setupMode: true
            },
            JWT_SECRET,
            { expiresIn: '30m' }
        );

        console.log(`[SIGNUP] Account created for ${email}, sending 201 response with tempToken`);

        const successResponse = {
            success: true,
            message: `Account created. Complete mandatory 2FA setup with authenticator app`,
            tempToken: tempToken,
            userId: newUser.id,
            twoFaMethod: selectedTwoFaMethod,
            nextStep: '/api/setup-authenticator',
            instructions: {
                step: 'setup_authenticator',
                message: 'Please set up your authenticator app using Google Authenticator, Microsoft Authenticator, or any TOTP-compatible app',
                qrCode: 'Will be provided in next step'
            }
        };

        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.setHeader('Connection', 'close');
        res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');

        return res.status(201).json(successResponse);

    } catch (err) {
        console.error('Signup endpoint error:', err);
        await logEvent('signup_error', `Sign-up failed: ${err.message}`, null, {
            email: email,
            error: err.message,
            stack: err.stack
        });
        return res.status(500).json({
            success: false,
            error: 'An error occurred during registration',
            details: err.message
        });
    }
});

app.post('/api/setup-authenticator', passwordResetLimiter, async (req, res) => {
    const token = req.headers.authorization?.replace('Bearer ', '');

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (!decoded.setupMode) {
            return res.status(401).json({ error: 'Invalid setup mode' });
        }

        const user = await dbConnection.collection('users').findOne({
            id: decoded.userId,
            'twoFactorAuth.accountVerified': false
        });

        if (!user) {
            return res.status(404).json({ error: 'User not found or already verified' });
        }

        const secret = speakeasy.generateSecret({
            name: `FreeTime (${user.email})`,
            issuer: 'FreeTime',
            length: 32
        });

        let qrCode;
        try {
            qrCode = await QRCode.toDataURL(secret.otpauth_url);
        } catch (qrError) {
        console.error('[ERROR] QR code generation failed:', qrError);
            return res.status(500).json({
                error: 'Failed to generate QR code',
                fallback: 'Manual entry available: ' + secret.base32
            });
        }

        const backupCodes = generateBackupCodes(10);
        const now = new Date();

        const updateResult = await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    'twoFactorAuth.authenticatorSecret': secret.base32,
                    'twoFactorAuth.authenticatorBackupCodes': backupCodes.map(code => ({
                        code: code,
                        issuedAt: now,
                        used: false,
                        usedAt: null
                    })),
                    'twoFactorAuth.backupCodesIssuedAt': now
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(500).json({ error: 'Failed to save authenticator setup' });
        }

        await sendAuthenticatorSetupEmail(user.email, user.name, qrCode);

        res.json({
            success: true,
            message: 'Authenticator secret generated. Check your email.',
            qrCode: qrCode,
            secret: secret.base32,
            backupCodes: backupCodes,
            nextStep: '/api/verify-authenticator'
        });

    } catch (error) {
        if (error.name === 'JsonWebTokenError') {
            return res.status(401).json({ error: 'Invalid or expired token' });
        }
        res.status(500).json({ error: 'Setup failed: ' + error.message });
    }
});

app.post('/api/verify-authenticator', async (req, res) => {
    const { totpCode } = req.body;
    const token = req.headers.authorization?.replace('Bearer ', '');

    if (!totpCode) {
        return res.status(400).json({ error: 'TOTP code is required' });
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (!decoded.setupMode) {
            return res.status(401).json({ error: 'Invalid setup mode' });
        }

        const user = await dbConnection.collection('users').findOne({
            id: decoded.userId,
            'twoFactorAuth.accountVerified': false
        });

        if (!user || !user.twoFactorAuth.authenticatorSecret) {
            return res.status(404).json({ error: 'User not found or authenticator not set up' });
        }

        const isValidToken = speakeasy.totp.verify({
            secret: user.twoFactorAuth.authenticatorSecret,
            encoding: 'base32',
            token: totpCode,
            window: 2
        });

        if (!isValidToken) {
            return res.status(401).json({ error: 'Invalid verification code' });
        }

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    'twoFactorAuth.enabled': true,
                    'twoFactorAuth.verifiedAt': new Date(),
                    'twoFactorAuth.accountVerified': true,
                    'twoFactorAuth.verificationMethod': 'authenticator',
                    'twoFactorAuth.verificationCompletedAt': new Date()
                }
            }
        );

        const backupCodes = user.twoFactorAuth.authenticatorBackupCodes.map(bc => bc.code);
        await sendBackupCodesEmail(user.email, user.name, backupCodes);

        const finalToken = jwt.sign(
            {
                userId: user.id,
                username: user.username,
                role: user.role
            },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        await logEvent('user_2fa_verified', `User verified with authenticator: ${user.email}`, user.id);

        res.json({
            success: true,
            message: 'Two-factor authentication verified!',
            token: finalToken,
            user: {
                id: user.id,
                username: user.username,
                email: user.email,
                name: user.name,
                role: user.role
            }
        });

    } catch (error) {
        if (error.name === 'JsonWebTokenError') {
            return res.status(401).json({ error: 'Invalid or expired token' });
        }
        res.status(500).json({ error: 'Verification failed: ' + error.message });
    }
});

app.post('/api/send-verification-email', async (req, res) => {
    const token = req.headers.authorization?.replace('Bearer ', '');

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (!decoded.setupMode) {
            return res.status(401).json({ error: 'Invalid setup mode' });
        }

        const user = await dbConnection.collection('users').findOne({
            id: decoded.userId,
            'twoFactorAuth.accountVerified': false
        });

        if (!user) {
            return res.status(404).json({ error: 'User not found or already verified' });
        }

        const verificationCode = generateVerificationCode(6);
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    'twoFactorAuth.emailVerificationCode': verificationCode,
                    'twoFactorAuth.emailVerificationAttempts': 0,
                    'twoFactorAuth.emailVerificationExpiresAt': expiresAt
                }
            }
        );

        res.json({
            success: true,
            message: 'Email verification disabled - use authenticator app for 2FA',
            expiresIn: 600,
            nextStep: '/api/setup-authenticator'
        });

    } catch (error) {
        if (error.name === 'JsonWebTokenError') {
            return res.status(401).json({ error: 'Invalid or expired token' });
        }
        res.status(500).json({ error: 'Failed to send verification email: ' + error.message });
    }
});

app.post('/api/verify-email-code', async (req, res) => {
    return res.status(403).json({
        error: 'Email verification is disabled',
        message: 'Please use authenticator app for two-factor authentication',
        nextStep: '/api/setup-authenticator'
    });
});

app.get('/api/admin/verify', verifyToken, async (req, res) => {
    res.json({
        valid: true,
        user: req.user
    });
});

app.post('/api/verify-login-totp', totpLimiter, async (req, res) => {
    const { totpCode, rememberMe = true, force = true } = req.body;
    const tempToken = req.headers.authorization?.replace('Bearer ', '');

    if (!totpCode) {
        return res.status(400).json({ error: 'Code is required' });
    }

    try {
        const decoded = jwt.verify(tempToken, JWT_SECRET);
        if (!decoded.twoFaRequired) {
            return res.status(401).json({ error: 'Invalid token' });
        }

        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        if (!user.twoFactorAuth || !user.twoFactorAuth.authenticatorSecret) {
            return res.status(400).json({
                error: 'Two-factor authentication not set up for this user',
                setupRequired: true
            });
        }

        const isTotpFormat = /^\d{6}$/.test(totpCode);
        let isValid = false;
        let usedBackupCode = false;

        if (isTotpFormat) {
            isValid = speakeasy.totp.verify({
                secret: user.twoFactorAuth.authenticatorSecret,
                encoding: 'base32',
                token: totpCode,
                window: 2
            });
        } else {
            if (!user.twoFactorAuth.authenticatorBackupCodes || user.twoFactorAuth.authenticatorBackupCodes.length === 0) {
                return res.status(401).json({ error: 'Invalid code' });
            }

            const normalizedInput = totpCode.replace(/\s/g, '').toUpperCase();
            const backupCodeIndex = user.twoFactorAuth.authenticatorBackupCodes.findIndex(bc =>
                bc.code.replace(/\s/g, '').toUpperCase() === normalizedInput && !bc.used
            );

            if (backupCodeIndex !== -1) {
                isValid = true;
                usedBackupCode = true;

                const backupCodes = JSON.parse(JSON.stringify(user.twoFactorAuth.authenticatorBackupCodes));
                backupCodes[backupCodeIndex].used = true;
                backupCodes[backupCodeIndex].usedAt = new Date();

                await dbConnection.collection('users').updateOne(
                    { id: user.id },
                    { $set: { 'twoFactorAuth.authenticatorBackupCodes': backupCodes } }
                );
            }
        }

        if (!isValid) {
            return res.status(401).json({ error: 'Invalid code' });
        }

        const tokenExpiry = '30d';

        const loginSessionData = {
            isOnline: true,
            lastLogin: new Date(),
            updatedAt: new Date()
        };
        const decodedTemp = decoded;
        const deviceId = decodedTemp.deviceId || crypto.randomBytes(16).toString('hex');

        const deviceInfo = {
            platform: req.body.deviceInfo?.platform || 'Unknown',
            deviceName: req.body.deviceInfo?.deviceName || 'Unknown Device',
            osVersion: req.body.deviceInfo?.osVersion || 'Unknown',
            appVersion: req.body.deviceInfo?.appVersion || 'Unknown',
            ipAddress: req.ip || req.connection.remoteAddress
        };

        const sessionResult = await global.sessionManager.createSession(
            user.id,
            deviceId,
            deviceInfo,
            force
        );

        if (rememberMe) {
            loginSessionData.rememberMeEnabled = true;
            loginSessionData.rememberMeExpiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
        }

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: loginSessionData
            }
        );

        const token = jwt.sign(
            {
                userId: user.id,
                username: user.username,
                role: user.role,
                rememberMe: rememberMe,
                deviceId: deviceId,
                sessionId: sessionResult.sessionId,
                sessionToken: sessionResult.sessionToken
            },
            JWT_SECRET,
            { expiresIn: tokenExpiry }
        );

        let refreshToken = null;
        if (rememberMe) {
            refreshToken = jwt.sign(
                {
                    userId: user.id,
                    type: 'refresh',
                    deviceId: deviceId,
                    sessionId: sessionResult.sessionId
                },
                JWT_SECRET,
                { expiresIn: '30d' }
            );
        }

        await logEvent('auth', `User 2FA verified (${usedBackupCode ? 'backup code' : 'authenticator'}): ${user.username} ${rememberMe ? '(30-day remember enabled)' : ''}`, user.id);

        res.json({
            success: true,
            token: token,
            refreshToken: refreshToken,
            rememberMeEnabled: rememberMe,
            tokenExpiry: tokenExpiry,
            sessionId: sessionResult.sessionId,
            deviceId: deviceId,
            user: {
                id: user.id,
                username: user.username,
                email: user.email,
                name: user.name,
                role: user.role
            }
        });

    } catch (error) {
        console.error('Verification error:', error);

        if (error.code === 'CONCURRENT_LOGIN') {
            return res.status(403).json({
                success: false,
                error: 'Account already in use by another device',
                code: 'CONCURRENT_LOGIN',
                message: `This account is already logged in on ${error.existingDevice}. Multi-device login is disabled for security.`
            });
        }

        res.status(500).json({ error: 'Verification failed', details: error.message });
    }
});

app.post('/api/send-login-verification-email', async (req, res) => {
    const tempToken = req.headers.authorization?.replace('Bearer ', '');

    try {
        const decoded = jwt.verify(tempToken, JWT_SECRET);
        if (!decoded.twoFaRequired) {
            return res.status(401).json({ error: 'Invalid token' });
        }

        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const verificationCode = generateVerificationCode(6);
        const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    'twoFactorAuth.emailVerificationCode': verificationCode,
                    'twoFactorAuth.emailVerificationAttempts': 0,
                    'twoFactorAuth.emailVerificationExpiresAt': expiresAt
                }
            }
        );

        await sendVerificationEmail(user.email, verificationCode, user.name);

        res.json({
            success: true,
            message: 'Verification code sent to your email',
            expiresIn: 600
        });

    } catch (error) {
        res.status(500).json({ error: 'Failed to send email' });
    }
});

app.post('/api/verify-login-email-code', async (req, res) => {
    const { code } = req.body;
    const tempToken = req.headers.authorization?.replace('Bearer ', '');

    if (!code) {
        return res.status(400).json({ error: 'Code is required' });
    }

    if (!/^\d{6}$/.test(String(code).trim())) {
        return res.status(400).json({ error: 'Invalid code format. Code must be 6 digits' });
    }

    try {
        const decoded = jwt.verify(tempToken, JWT_SECRET);
        if (!decoded.twoFaRequired) {
            return res.status(401).json({ error: 'Invalid token' });
        }

        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const twoFa = user.twoFactorAuth;

        if (new Date() > twoFa.emailVerificationExpiresAt) {
            return res.status(401).json({ error: 'Code expired' });
        }

        if (twoFa.emailVerificationAttempts >= 5) {
            return res.status(429).json({ error: 'Too many attempts' });
        }

        if (code !== twoFa.emailVerificationCode) {
            await dbConnection.collection('users').updateOne(
                { id: user.id },
                { $set: { 'twoFactorAuth.emailVerificationAttempts': twoFa.emailVerificationAttempts + 1 } }
            );
            return res.status(401).json({ error: 'Invalid code' });
        }

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    isOnline: true,
                    lastLogin: new Date(),
                    updatedAt: new Date()
                }
            }
        );

        const token = jwt.sign(
            {
                userId: user.id,
                username: user.username,
                role: user.role
            },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        await logEvent('auth', `User 2FA verified (email): ${user.username}`, user.id);

        res.json({
            success: true,
            token: token,
            user: {
                id: user.id,
                username: user.username,
                email: user.email,
                name: user.name,
                role: user.role
            }
        });

    } catch (error) {
        res.status(500).json({ error: 'Verification failed' });
    }
});

app.post('/api/refresh-token', async (req, res) => {
    const { refreshToken } = req.body;

    if (!refreshToken) {
        return res.status(400).json({ error: 'Refresh token is required' });
    }

    try {
        const decoded = jwt.verify(refreshToken, JWT_SECRET);

        if (decoded.type !== 'refresh') {
            return res.status(401).json({ error: 'Invalid refresh token' });
        }

        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        if (!user.rememberMeEnabled || (user.rememberMeExpiresAt && new Date() > user.rememberMeExpiresAt)) {
            return res.status(401).json({ error: 'Remember Me session expired. Please log in again.' });
        }

        const newToken = jwt.sign(
            {
                userId: user.id,
                username: user.username,
                role: user.role,
                rememberMe: true
            },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        const newRefreshToken = jwt.sign(
            {
                userId: user.id,
                type: 'refresh'
            },
            JWT_SECRET,
            { expiresIn: '30d' }
        );

        await dbConnection.collection('users').updateOne(
            { id: user.id },
            {
                $set: {
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('auth', `Token refreshed (30-day remember): ${user.username}`, user.id);

        res.json({
            success: true,
            token: newToken,
            refreshToken: newRefreshToken,
            rememberMeEnabled: true,
            tokenExpiry: '24h',
            refreshTokenExpiry: '30d'
        });

    } catch (error) {
        if (error.name === 'TokenExpiredError') {
            return res.status(401).json({ error: 'Refresh token expired. Please log in again.' });
        }
        console.error('Token refresh error:', error);
        res.status(500).json({ error: 'Token refresh failed', details: error.message });
    }
});

app.get('/api/verify', async (req, res) => {
    const token = req.headers.authorization?.replace('Bearer ', '');

    if (!token) {
        return res.status(401).json({ valid: false, error: 'No token provided' });
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });

        if (!user) {
            return res.status(401).json({ valid: false, error: 'User not found' });
        }

        res.json({
            valid: true,
            user: {
                id: user.id,
                username: user.username,
                email: user.email,
                name: user.name,
                role: user.role,
                tags: user.tags
            }
        });
    } catch (err) {
        res.status(401).json({ valid: false, error: 'Invalid token' });
    }
});

app.post('/api/logout', verifyToken, async (req, res) => {
    try {
        if (req.user?.userId) {
            const updates = {
                isOnline: false,
                lastActivityAt: new Date()
            };
            if (req.user.deviceId) {
                updates.currentDeviceId = null;
            }
            await dbConnection.collection('users').updateOne(
                { id: req.user.userId },
                { $set: updates }
            );

            if (req.user.sessionToken && req.user.deviceId) {
                try {
                    await global.sessionManager.logout(
                        req.user.userId,
                        req.user.sessionToken,
                        req.user.deviceId
                    );
                } catch (err) {
                    console.warn(`[WARN] Failed to logout session: ${err.message}`);
                }
            }
        }
        res.json({ success: true, message: 'Logged out successfully' });
    } catch (err) {
        console.error('Logout error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/sessions', verifyToken, async (req, res) => {
    try {
        const userId = req.user.userId;
        const sessions = await global.sessionManager.getActiveSessions(userId);

        res.json({
            success: true,
            sessions: sessions,
            count: sessions.length
        });
    } catch (err) {
        console.error(`[ERROR] Failed to get sessions: ${err.message}`);
        res.status(500).json({ error: 'Failed to get sessions' });
    }
});

app.delete('/api/sessions/:sessionId', verifyToken, async (req, res) => {
    try {
        const userId = req.user.userId;
        const sessionId = req.params.sessionId;

        const result = await global.sessionManager.terminateSession(userId, sessionId);

        if (result.success) {
            res.json({ success: true, message: 'Session terminated' });
        } else {
            res.status(400).json({ error: result.reason || 'Failed to terminate session' });
        }
    } catch (err) {
        console.error(`[ERROR] Failed to terminate session: ${err.message}`);
        res.status(500).json({ error: 'Failed to terminate session' });
    }
});

async function resetMonthlyCounters() {
    const config = await dbConnection.collection('systemConfig').findOne({
        configType: 'monthly_limits'
    });

    if (!config) return;

    const now = new Date();
    const resetDate = new Date(now.getFullYear(), now.getMonth(), config.settings.resetDay);

    await dbConnection.collection('systemConfig').updateOne(
        { configType: 'monthly_limits' },
        {
            $set: {
                lastReset: now,
                currentMonthStats: {
                    usersCreated: 0,
                    moderatorsCreated: 0,
                    adminsCreated: 0
                }
            }
        }
    );

    const resetMonthlyResult = await dbConnection.collection('users').updateMany(
        {},
        {
            $set: {
                "monthlyCreationLimit.currentMonthUsers": 0,
                "monthlyCreationLimit.lastResetDate": now
            }
        }
    );
    if (resetMonthlyResult.modifiedCount === 0) {
        console.warn(`resetMonthlyLimits: No users found to reset monthly limits`);
    }
}

async function checkMonthlyLimits(adminRole) {
    const config = await dbConnection.collection('systemConfig').findOne({
        configType: 'monthly_limits'
    });

    if (!config) return { canCreate: true, remaining: 'Unlimited' };

    const now = new Date();
    const lastReset = new Date(config.lastReset);

    if (lastReset.getMonth() !== now.getMonth() || lastReset.getFullYear() !== now.getFullYear()) {
        await resetMonthlyCounters();
        config.currentMonthStats = { usersCreated: 0, moderatorsCreated: 0, adminsCreated: 0 };
    }

    let canCreate = false;
    let remaining = 0;

    if (adminRole === 'ADMIN') {
        canCreate = true;
        remaining = 'Unlimited';
    } else if (adminRole === 'MODERATOR') {
        const created = config.currentMonthStats.moderatorsCreated;
        canCreate = created < config.settings.moderatorCreationPerMonth;
        remaining = config.settings.moderatorCreationPerMonth - created;
    } else {
        const created = config.currentMonthStats.usersCreated;
        canCreate = created < config.settings.userCreationPerMonth;
        remaining = config.settings.userCreationPerMonth - created;
    }

    return { canCreate, remaining, config };
}

async function updateMonthlyCounters(role) {
    const config = await dbConnection.collection('systemConfig').findOne({
        configType: 'monthly_limits'
    });

    if (!config) return;

    const updateField = role === 'ADMIN' ? 'adminsCreated' :
                       role === 'MODERATOR' ? 'moderatorsCreated' : 'usersCreated';

    const updateResult = await dbConnection.collection('systemConfig').updateOne(
        { configType: 'monthly_limits' },
        {
            $inc: {
                [`currentMonthStats.${updateField}`]: 1
            }
        }
    );
}

function getNextResetDate() {
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    return nextMonth.toISOString().split('T')[0];
}

app.get('/api/admin/users', verifyToken, async (req, res) => {
    try {
        const users = await dbConnection.collection('users')
            .find({})
            .project({ password: 0 })
            .sort({ createdAt: -1 })
            .toArray();

        const INACTIVITY_TIMEOUT_MS = 1 * 60 * 1000;
        const now = new Date();

        const usersWithStatus = users.map(user => {
            let isOnline = user.isOnline || false;
            const lastActivity = user.lastActivityAt || user.updatedAt || new Date(0);

            if (isOnline && (now - new Date(lastActivity)) > INACTIVITY_TIMEOUT_MS) {
                isOnline = false;
                dbConnection.collection('users').updateOne(
                    { id: user.id },
                    { $set: { isOnline: false } }
                ).catch(err => console.error('Failed to update user online status:', err));
            }

            return {
                ...user,
                isOnline,
                lastSeen: lastActivity,
                status: isOnline ? 'online' : 'offline',
                role: user.role || 'USER',
                isAdmin: user.role === 'ADMIN',
                isModerator: user.role === 'MODERATOR'
            };
        });

        res.json({ users: usersWithStatus });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/users/:id', verifyToken, async (req, res) => {
    try {
        const user = await dbConnection.collection('users').findOne(
            { id: req.params.id },
            { projection: { password: 0 } }
        );

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const userWithRole = {
            ...user,
            role: user.role || 'USER',
            isAdmin: user.role === 'ADMIN',
            isModerator: user.role === 'MODERATOR'
        };

        res.json({ user: userWithRole });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/users', verifyToken, async (req, res) => {
    const { username, email, password, name, role, tags } = req.body;

    if (!username || !email || !password) {
        return res.status(400).json({ error: 'Username, email, and password required' });
    }

    if (username.length < 3 || username.length > 20) {
        return res.status(400).json({ error: 'Username must be 3-20 characters' });
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        return res.status(400).json({ error: 'Invalid email format' });
    }

    if (password.length < 6) {
        return res.status(400).json({ error: 'Password must be at least 6 characters' });
    }

    try {
        const limitCheck = await checkMonthlyLimits(req.user.role);

        if (!limitCheck.canCreate) {
            return res.status(429).json({
                error: 'Monthly creation limit exceeded',
                remaining: limitCheck.remaining,
                resetDate: getNextResetDate(),
                nextReset: getNextResetDate()
            });
        }

        const normalizedUsername = username?.toLowerCase()?.trim();
        const normalizedEmail = email?.toLowerCase()?.trim();

        const existingUser = await dbConnection.collection('users').findOne({
            $or: [
                { username: normalizedUsername },
                { email: normalizedEmail }
            ]
        });

        if (existingUser) {
            return res.status(409).json({
                error: 'User already exists',
                field: existingUser.username === normalizedUsername ? 'username' : 'email'
            });
        }

        const newUser = {
            id: uuidv4(),
            username: normalizedUsername,
            email: normalizedEmail,
            password: await bcrypt.hash(password, 10),
            name: name || username,
            role: role || 'USER',
            tags: tags || [],
            isOnline: false,
            createdAt: new Date(),
            updatedAt: new Date(),
            monthlyCreationLimit: {
                enabled: true,
                maxUsersPerMonth: role === 'ADMIN' ? -1 : (role === 'MODERATOR' ? 5 : 1),
                currentMonthUsers: 0,
                lastResetDate: new Date()
            },
            deviceInfo: {
                deviceId: null,
                deviceType: null,
                deviceName: null,
                lastSeen: null,
                isActive: false,
                registeredAt: null
            },
            usageStats: {
                totalLogins: 0,
                totalMessages: 0,
                totalCalls: 0,
                totalConnections: 0,
                lastActivity: new Date(),
                dataUsage: {
                    uploaded: 0,
                    downloaded: 0
                }
            },

            twoFactorAuth: {
                enabled: false,
                method: 'authenticator',
                verifiedAt: null,
                authenticatorSecret: null,
                authenticatorBackupCodes: [],
                emailVerificationCode: null,
                emailVerificationAttempts: 0,
                emailVerificationExpiresAt: null,
                accountVerified: false,
                verificationMethod: null,
                verificationCompletedAt: null,
                mandatorySetup: true
            }
        };

        const result = await dbConnection.collection('users').insertOne(newUser);

        await updateMonthlyCounters(role);

        await logEvent('user_create', `User created: ${username}`, req.user.id || req.user.userId, {
            userId: newUser.id,
            role: role,
            monthlyRemaining: limitCheck.remaining
        });

        const updatedStats = await checkMonthlyLimits(req.user.roleRole);

        const tempToken = jwt.sign(
            {
                userId: newUser.id,
                username: newUser.username,
                setupMode: true
            },
            JWT_SECRET,
            { expiresIn: '30m' }
        );

        res.json({
            id: newUser.id,
            username: newUser.username,
            email: newUser.email,
            name: newUser.name,
            role: newUser.role,
            tags: newUser.tags,
            createdAt: newUser.createdAt,
            success: true,
            message: 'Account created. User must complete 2FA setup to login.',
            tempToken: tempToken,
            nextStep: '/api/setup-authenticator',
            setupInstructions: {
                step: 'setup_authenticator',
                message: 'User must set up authenticator app 2FA using Google Authenticator, Microsoft Authenticator, or any TOTP-compatible app',
                qrCode: 'Will be provided at next step'
            },
            monthlyStats: {
                created: limitCheck.config.currentMonthStats,
                remaining: updatedStats.remaining,
                nextReset: getNextResetDate(),
                limit: limitCheck.config.settings.userCreationPerMonth
            }
        });

    } catch (err) {
        await logEvent('error', `User creation failed: ${err.message}`, req.user.id || req.user.userId);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/admin/users/:id', verifyToken, async (req, res) => {
    const { username, email, password, name, role, tags, status } = req.body;

    try {
        const updates = {
            updatedAt: new Date()
        };

        if (username) updates.username = username;
        if (email) updates.email = email;
        if (name) updates.name = name;
        if (role) updates.role = role;
        if (typeof req.body.tags === 'string') {
            updates.tags = req.body.tags.split(',').map(t => t.trim()).filter(t => t);
            updates['profile.tags'] = updates.tags;
        } else if (Array.isArray(req.body.tags)) {
            updates.tags = req.body.tags;
            updates['profile.tags'] = req.body.tags;
        }
        if (status) updates.status = status;
        if (password) {
            updates.password = await bcrypt.hash(password, 10);
        }

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: req.params.id },
            { $set: updates },
            { returnDocument: 'after', projection: { password: 0 } }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('user_update', `User updated: ${req.params.id}`, req.user.id || req.user.userId);

        res.json({
            id: result.value.id,
            username: result.value.username,
            email: result.value.email,
            name: result.value.name,
            role: result.value.role || 'USER',
            isAdmin: result.value.role === 'ADMIN',
            isModerator: result.value.role === 'MODERATOR',
            tags: result.value.tags,
            success: true,
            message: 'User updated successfully'
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/users/:id', verifyToken, async (req, res) => {
    try {
        const result = await dbConnection.collection('users').deleteOne({ id: req.params.id });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('user_delete', `User deleted: ${req.params.id}`, req.user.id || req.user.userId);

        res.json({ success: true, message: 'User deleted successfully', id: req.params.id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/users/:userId/badges', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { badgeName, badgeIcon, badgeColor, badgeDescription } = req.body;
    const adminId = req.user?.id || req.user?.username;

    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        if (!badgeName || !badgeIcon) {
            return res.status(400).json({ error: 'Badge name and icon required' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const badge = {
            id: `badge_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            name: badgeName,
            icon: badgeIcon,
            color: badgeColor || '#9C27B0',
            description: badgeDescription || '',
            assignedAt: new Date().toISOString(),
            assignedBy: adminId
        };

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $push: {
                    'profile.badges': badge
                },
                $set: {
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('badge_assigned', `Badge '${badgeName}' assigned to user ${userId}`, adminId, { badge });

        res.json({
            success: true,
            message: 'Badge assigned successfully',
            badge: badge
        });
    } catch (err) {
        console.error('Assign badge error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/users/:userId/badges/:badgeId', verifyToken, async (req, res) => {
    const { userId, badgeId } = req.params;
    const adminId = req.user?.id || req.user?.username;

    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const result = await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $pull: {
                    'profile.badges': { id: badgeId }
                },
                $set: {
                    updatedAt: new Date()
                }
            }
        );

        if (result.modifiedCount === 0) {
            return res.status(404).json({ error: 'Badge not found' });
        }

        await logEvent('badge_removed', `Badge removed from user ${userId}`, adminId, { badgeId });

        res.json({
            success: true,
            message: 'Badge removed successfully'
        });
    } catch (err) {
        console.error('Remove badge error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/reports', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });

        const { status, reportedUserId, reporterUserId, limit = 50, skip = 0, from, to } = req.query;
        const q = {};
        if (status) q.status = status;
        if (reportedUserId) q.reportedUserId = reportedUserId;
        if (reporterUserId) q.reporterUserId = reporterUserId;
        if (from || to) q.createdAt = {};
        if (from) q.createdAt.$gte = new Date(from);
        if (to) q.createdAt.$lte = new Date(to);

        const cursor = dbConnection.collection('abuseReports').find(q).sort({ createdAt: -1 }).skip(parseInt(skip, 10)).limit(Math.min(500, parseInt(limit, 10)));
        const total = await dbConnection.collection('abuseReports').countDocuments(q);
        const reports = await cursor.toArray();

        const enrichedReports = await Promise.all(reports.map(async (report) => {
            const [reporterUser, reportedUser] = await Promise.all([
                report.reporterId ? dbConnection.collection('users').findOne({ id: report.reporterId }, { projection: { username: 1, displayName: 1, id: 1 } }) : null,
                report.reportedUserId ? dbConnection.collection('users').findOne({ id: report.reportedUserId }, { projection: { username: 1, displayName: 1, id: 1 } }) : null
            ]);
            return {
                ...report,
                reporter: reporterUser ? {
                    username: reporterUser.displayName || reporterUser.username || 'Unknown',
                    id: report.reporterId
                } : { username: 'Unknown', id: report.reporterId || '—' },
                reportedUser: reportedUser ? {
                    username: reportedUser.displayName || reportedUser.username || 'Unknown',
                    id: report.reportedUserId
                } : { username: 'Unknown', id: report.reportedUserId || '—' }
            };
        }));

        res.json({ total, reports: enrichedReports });
    } catch (err) {
        console.error('Admin list reports error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/reports/:id', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });

        const report = await dbConnection.collection('abuseReports').findOne({ id: req.params.id });
        if (!report) return res.status(404).json({ error: 'Report not found' });

        res.json({ report });
    } catch (err) {
        console.error('Admin get report error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/reports/:id/resolve', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });

        const { note } = req.body;
        const result = await dbConnection.collection('abuseReports').findOneAndUpdate(
            { id: req.params.id },
            { $set: { status: 'resolved', resolvedBy: req.user?.username || req.user?.id, resolvedAt: new Date(), resolutionNote: note || '' } },
            { returnDocument: 'after' }
        );

        if (!result.value) return res.status(404).json({ error: 'Report not found' });

        await logEvent('report_resolved', `Report ${req.params.id} resolved`, req.user?.username || req.user?.id, { reportId: req.params.id, note });

        res.json({ success: true, report: result.value });
    } catch (err) {
        console.error('Admin resolve report error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/reports/:id/dismiss', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });

        const { note } = req.body;
        const result = await dbConnection.collection('abuseReports').findOneAndUpdate(
            { id: req.params.id },
            { $set: { status: 'dismissed', dismissedBy: req.user?.username || req.user?.id, dismissedAt: new Date(), dismissalNote: note || '' } },
            { returnDocument: 'after' }
        );

        if (!result.value) return res.status(404).json({ error: 'Report not found' });

        await logEvent('report_dismissed', `Report ${req.params.id} dismissed`, req.user?.username || req.user?.id, { reportId: req.params.id, note });

        res.json({ success: true, report: result.value });
    } catch (err) {
        console.error('Admin dismiss report error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/reports', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        const result = await dbConnection.collection('abuseReports').deleteMany({});
        await logEvent('admin_reports_cleared',
            `Admin ${req.user.username} cleared all reports (${result.deletedCount} entries)`,
            req.user.userId);
        res.json({ success: true, deletedCount: result.deletedCount });
    } catch (err) {
        console.error('Clear reports error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/users/:userId/toggle-2fa', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { enabled } = req.body;

    try {
        if (!req.user || req.user.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        if (enabled === undefined || typeof enabled !== 'boolean') {
            return res.status(400).json({ error: 'enabled field required (true/false)' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const currentTwoFA = user.twoFactorAuth || {
            enabled: false,
            method: null,
            secret: null,
            accountVerified: false,
            mandatorySetup: false,
            authenticatorSecret: null,
            authenticatorBackupCodes: []
        };

        const updatedTwoFA = {
            ...currentTwoFA,
            mandatorySetup: enabled,
            enabled: enabled ? currentTwoFA.enabled : false,
            accountVerified: enabled ? currentTwoFA.accountVerified : false,
            secret: enabled ? currentTwoFA.secret : null,
            authenticatorSecret: enabled ? currentTwoFA.authenticatorSecret : null,
            method: enabled ? currentTwoFA.method : null,
            authenticatorBackupCodes: enabled ? currentTwoFA.authenticatorBackupCodes : []
        };

        const updateResult = await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    twoFactorAuth: updatedTwoFA,
                    updatedAt: new Date()
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(500).json({ error: 'Failed to update user 2FA settings' });
        }

        await logEvent('2fa_toggled', `2FA ${enabled ? 'enabled' : 'disabled'} for user ${userId}`, req.user.userId, {
            userId: userId,
            enabled: enabled
        });

        res.json({
            success: true,
            message: `2FA ${enabled ? 'enabled' : 'disabled'} for user`,
            user: {
                id: user.id,
                username: user.username,
                twoFactorAuth: {
                    mandatorySetup: enabled,
                    enabled: updatedTwoFA.enabled
                }
            }
        });
    } catch (err) {
        console.error('Toggle 2FA error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/monthly-stats', verifyToken, async (req, res) => {
    try {
        const config = await dbConnection.collection('systemConfig').findOne({
            configType: 'monthly_limits'
        });

        const limitCheck = await checkMonthlyLimits(req.user.role);

        const deviceStats = await dbConnection.collection('users').aggregate([
            {
                $match: { "deviceInfo.deviceId": { $exists: true, $ne: null } }
            },
            {
                $group: {
                    _id: null,
                    totalDevices: { $sum: 1 },
                    activeDevices: {
                        $sum: { $cond: [{ $eq: ["$deviceInfo.isActive", true] }, 1, 0] }
                    }
                }
            }
        ]).toArray();

        const userStats = await dbConnection.collection('users').aggregate([
            {
                $group: {
                    _id: "$role",
                    count: { $sum: 1 },
                    online: {
                        $sum: { $cond: [{ $eq: ["$isOnline", true] }, 1, 0] }
                    }
                }
            }
        ]).toArray();

        res.json({
            success: true,
            stats: {
                monthly: config ? config.currentMonthStats : { usersCreated: 0, moderatorsCreated: 0, adminsCreated: 0 },
                limits: config ? config.settings : { userCreationPerMonth: 1, moderatorCreationPerMonth: 5 },
                remaining: limitCheck.remaining,
                nextReset: getNextResetDate(),
                devices: deviceStats[0] || { totalDevices: 0, activeDevices: 0 },
                users: userStats.reduce((acc, stat) => {
                    acc[stat._id] = { count: stat.count, online: stat.online };
                    return acc;
                }, {})
            }
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/device/register', async (req, res) => {
    const token = req.headers.authorization?.replace('Bearer ', '');

    if (!token) {
        return res.status(401).json({ error: 'No token provided' });
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        const user = await dbConnection.collection('users').findOne({ id: decoded.userId });

        if (!user) {
            return res.status(401).json({ error: 'User not found' });
        }

        const { deviceId, deviceType, deviceName } = req.body;
        const userId = decoded.userId;

        const existingDevice = await dbConnection.collection('deviceRegistry').findOne({ deviceId });

        if (existingDevice && existingDevice.userId !== userId) {
            return res.status(409).json({
                error: 'Device already registered by another user'
            });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    "deviceInfo.deviceId": deviceId,
                    "deviceInfo.deviceType": deviceType,
                    "deviceInfo.deviceName": deviceName,
                    "deviceInfo.lastSeen": new Date(),
                    "deviceInfo.isActive": true,
                    "deviceInfo.registeredAt": new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await dbConnection.collection('deviceRegistry').updateOne(
            { deviceId },
            {
                $set: {
                    userId,
                    deviceType,
                    deviceName,
                    lastSeen: new Date(),
                    isActive: true,
                    updatedAt: new Date()
                }
            },
            { upsert: true }
        );

        await logEvent('device_register', `Device registered: ${deviceId}`, userId, {
            deviceId,
            deviceType,
            deviceName
        });

        res.json({
            success: true,
            message: 'Device registered successfully',
            device: {
                deviceId,
                deviceType,
                deviceName,
                registeredAt: new Date()
            }
        });

    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/files/upload', verifyToken, upload.single('file'), async (req, res) => {
    const { recipientId, messageId } = req.body;
    const userId = req.user.userId;

    try {
        const uploadDir = path.join(os.tmpdir(), 'freetime-uploads');
        if (!fs.existsSync(uploadDir)) {
            try {
                fs.mkdirSync(uploadDir, { recursive: true });
                console.log('[OK] Created upload directory:', uploadDir);
            } catch (err) {
                console.error('[ERROR] Failed to create upload directory:', err.message);
                return res.status(500).json({ error: 'Upload directory not accessible' });
            }
        }

        if (!req.file) {
            return res.status(400).json({ error: 'No file uploaded' });
        }

        const fileConfig = await dbConnection.collection('systemConfig').findOne({ configType: 'file_upload' });
        const maxSize = fileConfig?.settings?.maxFileSize || 104857600;
        const allowedTypes = fileConfig?.settings?.allowedTypes || [];

        if (req.file.size > maxSize) {
            return res.status(413).json({
                error: `File too large. Maximum size: ${maxSize / 1024 / 1024}MB`
            });
        }

        if (allowedTypes.length > 0 && !allowedTypes.includes(req.file.mimetype)) {
            return res.status(415).json({
                error: `File type not allowed. Allowed types: ${allowedTypes.join(', ')}`
            });
        }

        const fileId = uuidv4();
        const fileExtension = path.extname(req.file.originalname);
        const fileName = `${fileId}${fileExtension}`;
        const filePath = path.join(uploadDir, fileName);

        try {
            fs.copyFileSync(req.file.path, filePath);
            fs.unlinkSync(req.file.path);
        } catch (moveErr) {
            console.error('[ERROR] Failed to move uploaded file:', moveErr.message);
            try {
                fs.renameSync(req.file.path, filePath);
            } catch (renameErr) {
                return res.status(500).json({ error: 'Failed to save uploaded file' });
            }
        }

        const fileRecord = {
            _id: new ObjectId(),
            fileId: fileId,
            fileName: req.file.originalname,
            filePath: filePath,
            fileSize: req.file.size,
            mimeType: req.file.mimetype,
            uploadedBy: userId,
            messageId: messageId || null,
            chatWithUser: recipientId || null,
            uploadedAt: new Date(),
            expiresAt: new Date(Date.now() + (fileConfig?.settings?.retentionDays || 30) * 24 * 60 * 60 * 1000)
        };

        const fileInsertResult = await dbConnection.collection('Files').insertOne(fileRecord);
        if (!fileInsertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to store file record' });
        }

        await logEvent('file_upload', `File uploaded: ${fileName}`, userId, {
            fileId: fileId,
            fileName: req.file.originalname,
            fileSize: req.file.size,
            recipientId
        });

        res.status(201).json({
            success: true,
            file: {
                fileId: fileId,
                fileName: req.file.originalname,
                fileSize: req.file.size,
                mimeType: req.file.mimetype,
                uploadedAt: fileRecord.uploadedAt
            }
        });

    } catch (err) {
        console.error('File upload error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/files/:fileId', verifyToken, async (req, res) => {
    const { fileId } = req.params;
    const userId = req.user.userId;

    try {
        const fileRecord = await dbConnection.collection('Files').findOne({ fileId });

        if (!fileRecord) {
            return res.status(404).json({ error: 'File not found' });
        }

        if (fileRecord.uploadedBy !== userId && fileRecord.chatWithUser !== userId) {
            return res.status(403).json({ error: 'Access denied' });
        }

        if (fileRecord.expiresAt && fileRecord.expiresAt < new Date()) {
            return res.status(410).json({ error: 'File has expired' });
        }

        if (!fs.existsSync(fileRecord.filePath)) {
            return res.status(404).json({ error: 'File not found on disk' });
        }

        res.download(fileRecord.filePath, fileRecord.fileName);

    } catch (err) {
        console.error('File download error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/files/:fileId', verifyToken, async (req, res) => {
    const { fileId } = req.params;
    const userId = req.user.userId;

    try {
        const fileRecord = await dbConnection.collection('Files').findOne({ fileId });

        if (!fileRecord) {
            return res.status(404).json({ error: 'File not found' });
        }

        if (fileRecord.uploadedBy !== userId) {
            return res.status(403).json({ error: 'Access denied' });
        }

        if (fs.existsSync(fileRecord.filePath)) {
            fs.unlinkSync(fileRecord.filePath);
        }

        const deleteResult = await dbConnection.collection('Files').deleteOne({ fileId });
        if (deleteResult.deletedCount === 0) {
            return res.status(500).json({ error: 'Failed to delete file record' });
        }

        await logEvent('file_delete', `File deleted: ${fileRecord.fileName}`, userId, {
            fileId: fileId,
            fileName: fileRecord.fileName
        });

        res.json({ success: true });

    } catch (err) {
        console.error('File delete error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/messages', verifyToken, async (req, res) => {
    const { recipientId, content, messageId, timestamp, replyToMessageId, replyToUsername, replyToText } = req.body;
    const senderId = req.user.userId;

    try {
        if (!recipientId || !content) {
            return res.status(400).json({ error: 'recipientId and content are required' });
        }

        const senderExists = await dbConnection.collection('users').findOne({ id: senderId });
        if (!senderExists) {
            return res.status(401).json({ error: 'Sender user not found in database', success: false });
        }

        const recipientExists = await dbConnection.collection('users').findOne({ id: recipientId });
        if (!recipientExists) {
            return res.status(404).json({ error: 'Recipient user not found in database', success: false });
        }

        const dbMessageId = messageId || `msg_${uuidv4()}`;
        const msgTimestamp = timestamp || Date.now();

        const msgInsertResult = await dbConnection.collection('messages').insertOne({
            id: dbMessageId,
            senderId,
            recipientId,
            content,
            status: 'delivered',
            createdAt: new Date(msgTimestamp),
            updatedAt: new Date(),
            isRead: false,
            replyToMessageId: replyToMessageId || null,
            replyToUsername: replyToUsername || null,
            replyToText: replyToText || null
        });
        if (!msgInsertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to store message', success: false });
        }

        const messageData = {
            _id: dbMessageId,
            senderId: senderId,
            senderUsername: senderExists.username || 'User',
            recipientId: recipientId,
            content: content,
            timestamp: msgTimestamp,
            read: false,
            type: 'text',
            status: 'delivered',
            replyToMessageId: replyToMessageId || null,
            replyToUsername: replyToUsername || null,
            replyToText: replyToText || null
        };

        try {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');

            const enrichedMessage = {
                ...messageData,
                senderUsername: senderExists.username || 'User',
                senderAvatar: senderExists.profile?.profileImageUrl || '',
                senderBio: senderExists.profile?.bio || ''
            };

            broadcastToUser(global.wsClients, recipientId, 'newMessage', enrichedMessage);
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to broadcast message to recipient: ${broadcastErr.message}`);
        }

        try {
            console.log(`[ PRIVATE MESSAGE] Sending notification to recipient ${recipientId}...`);
            const truncatedBody = content.length > 100 ? content.substring(0, 97) + '...' : content;
            await sendPushNotification(recipientId, {
                notification: {
                    title: `Message from ${senderExists.username || 'User'}`,
                    body: truncatedBody,
                    sound: 'default'
                },
                data: {
                    type: 'message',
                    senderId: senderId,
                    senderName: senderExists.username || 'User',
                    messageContent: content,
                    messageId: dbMessageId,
                    timestamp: msgTimestamp.toString()
                }
            });
            console.log(`[ PRIVATE MESSAGE] Notification sent to ${recipientId}`);
        } catch (fcmErr) {
            console.warn(`[ PRIVATE MESSAGE] Notification failed: ${fcmErr.message}`);
        }

        await logEvent('message_sent', `Message sent from ${senderId} to ${recipientId}`, senderId);

        res.json({
            ...messageData,
            success: true
        });
    } catch (err) {
        console.error('Send message error:', err);
        res.status(500).json({ error: err.message, success: false });
    }
});

app.get('/api/messages/:recipientId', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;
    const { limit = 50 } = req.query;

    try {
        const messages = await dbConnection.collection('messages')
            .find({
                $or: [
                    { senderId: userId, recipientId: recipientId },
                    { senderId: recipientId, recipientId: userId }
                ]
            })
            .sort({ createdAt: -1 })
            .limit(parseInt(limit))
            .toArray();

        const formattedMessages = await Promise.all(messages.reverse().map(async (msg) => {
            let replyUser = msg.replyToUsername || null;
            let replyText = msg.replyToText || null;
            if (msg.replyToMessageId) {
                const cleanReplyId = msg.replyToMessageId.replace(/^msg_/, '');
                const replyMsg = await dbConnection.collection('messages').findOne({
                    $or: [
                        { id: cleanReplyId },
                        { id: msg.replyToMessageId },
                        { _id: cleanReplyId }
                    ]
                });
                if (replyMsg) {
                    replyUser = replyMsg.senderUsername || (replyMsg.senderId === userId ? 'You' : 'Them') || null;
                    replyText = replyMsg.content || null;
                }
            }

            const sender = await dbConnection.collection('users').findOne(
                { id: msg.senderId },
                { projection: { profile: 1, tags: 1, role: 1, username: 1 } }
            );
            const senderTags = (sender?.profile?.tags || sender?.tags || []).slice(0, 5);
            const senderRole = sender?.role || 'User';
            const senderIsAdmin = senderRole === 'ADMIN';
            const senderIsModerator = senderRole === 'MODERATOR';
            const senderDisplayName = sender?.profile?.displayName || sender?.displayName || sender?.username || 'User';

            return {
                _id: msg._id?.toString() || msg.id,
                id: msg.id || msg._id?.toString(),
                senderId: msg.senderId,
                recipientId: msg.recipientId,
                content: msg.content,
                status: msg.status,
                read: msg.isRead || msg.read || false,
                timestamp: msg.createdAt ? msg.createdAt.getTime() : Date.now(),
                reactions: formatReactions(msg.reactions),
                replyId: msg.replyToMessageId || null,
                replyUser: replyUser,
                replyText: replyText,
                replyToMessageId: msg.replyToMessageId || null,
                replyToUsername: msg.replyToUsername || null,
                replyToText: msg.replyToText || null,
                senderTags: senderTags,
                senderIsAdmin: senderIsAdmin,
                senderIsModerator: senderIsModerator,
                senderRole: senderRole,
                senderDisplayName: senderDisplayName,
                subject: msg.subject || '',
                isAdminAnnouncement: msg.isAdminAnnouncement || false
            };
        }));

        res.json(formattedMessages);

    } catch (err) {
        console.error('Get chat history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/chat/:recipientId/delete-history', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const result = await dbConnection.collection('messages').deleteMany({
            $or: [
                { senderId: userId, recipientId: recipientId },
                { senderId: recipientId, recipientId: userId }
            ]
        });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'No chat history found to delete' });
        }

        await logEvent('chat_history_deleted', `Chat history deleted between ${userId} and ${recipientId}`, userId);

        if (global.socketIoServer) {
            global.socketIoServer.to(`user:${userId}`).emit('chatHistoryDeleted', {
                recipientId: recipientId,
                deletedCount: result.deletedCount,
                deletedBy: userId,
                timestamp: new Date().getTime()
            });

            global.socketIoServer.to(`user:${recipientId}`).emit('chatHistoryDeleted', {
                recipientId: userId,
                deletedCount: result.deletedCount,
                deletedBy: userId,
                timestamp: new Date().getTime()
            });

            console.log(`[OK] Chat history deleted notification sent to both users via Socket.IO`);
        }

        res.json({
            success: true,
            deletedCount: result.deletedCount
        });
    } catch (err) {
        console.error('Delete chat history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/chat/:recipientId', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const messages = await dbConnection.collection('messages')
            .find({
                $or: [
                    { senderId: userId, recipientId: recipientId },
                    { senderId: recipientId, recipientId: userId }
                ]
            })
            .sort({ createdAt: -1 })
            .limit(50)
            .toArray();

        res.json(messages.reverse().map(msg => ({
            messageId: msg.id,
            senderId: msg.senderId,
            recipientId: msg.recipientId,
            content: msg.content,
            status: msg.status,
            isRead: msg.isRead,
            timestamp: msg.createdAt.getTime()
        })));
    } catch (err) {
        console.error('Get chat history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/media/upload', verifyToken, upload.single('media'), async (req, res) => {
    const senderId = req.user.userId;
    const { recipientId, groupId, mediaShareMode } = req.body;

    if (!dbConnection || !dbConnection.isConnected) {
        console.error('[ MEDIA UPLOAD] Database not connected');
        return res.status(503).json({
            error: 'Database unavailable. Please try again in a moment.',
            status: 'db_unavailable'
        });
    }

    console.log(`[MEDIA UPLOAD] Started - senderId=${senderId}, recipientId=${recipientId}, groupId=${groupId}, shareMode=${mediaShareMode}, file=${req.file?.originalname}`);

    try {
        if (!req.file) {
            console.error(`[MEDIA UPLOAD ERROR] No file uploaded. Body keys: ${Object.keys(req.body).join(',')}`);
            return res.status(400).json({ error: 'No file uploaded' });
        }

        if (!recipientId && !groupId) {
            console.error(`[MEDIA UPLOAD ERROR] Missing recipientId and groupId`);
            return res.status(400).json({ error: 'recipientId or groupId is required' });
        }

        if (groupId) {
            const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId }] });
            if (!group) {
                return res.status(404).json({ error: 'Group not found' });
            }
            const isMember = group.members && group.members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === senderId);
            if (!isMember) {
                return res.status(403).json({ error: 'You are not a member of this group' });
            }
        }

        const sizeLimit = getFileSizeLimit(req.file.mimetype);
        if (req.file.size > sizeLimit) {
            const limitMB = sizeLimit === Infinity ? 'unlimited' : (sizeLimit / 1024 / 1024);
            return res.status(413).json({
                error: `File too large. Limit for ${req.file.mimetype}: ${limitMB}MB`,
                maxSize: sizeLimit,
                uploadedSize: req.file.size
            });
        }

        const mediaId = uuidv4();
        const now = new Date();

        let fileBuffer = req.file.buffer;
        if (!fileBuffer && req.file.path) {
            console.log(`[MEDIA UPLOAD] Reading file from disk: ${req.file.path}`);
            fileBuffer = fs.readFileSync(req.file.path);
        }

        if (!fileBuffer) {
            console.error(`[MEDIA UPLOAD] ERROR - No file data available. Path: ${req.file.path}`);
            return res.status(500).json({ error: 'File data not available' });
        }

        let encryptedResult;
        const isAlreadyEncrypted = (req.body.encrypted === 'true' || req.body.encrypted === true) && req.body.encryptionKey;
        const isPublic = mediaShareMode === 'public';

        if (isPublic) {
            console.log(`[MEDIA UPLOAD] Public media ${mediaId} - skipping encryption`);
            encryptedResult = {
                encryptedData: fileBuffer,
                encryptionKey: null,
                iv: null
            };
        } else if (isAlreadyEncrypted) {
            console.log(`[MEDIA UPLOAD] Media ${mediaId} already encrypted by client - skipping server-side encryption`);
            encryptedResult = {
                encryptedData: fileBuffer,
                encryptionKey: req.body.encryptionKey || 'client-side',
                iv: req.body.iv || 'client-side'
            };
        } else {
            try {
                console.log(`[ENCRYPTION] Encrypting media ${mediaId} - size=${fileBuffer.length} bytes, type=${req.file.mimetype}`);
                encryptedResult = await encryptMediaFile(fileBuffer, req.file.mimetype, mediaId);
                console.log(`[ENCRYPTION] Media ${mediaId} encrypted successfully - result size=${encryptedResult.encryptedData.length} bytes`);
            } catch (encryptErr) {
                console.error(`[ENCRYPTION] Failed to encrypt media ${mediaId}:`, encryptErr);
                return res.status(500).json({ error: `File encryption failed: ${encryptErr.message}` });
            }
        }

        console.log(`[MEDIA UPLOAD] Using GridFS for media ${mediaId} - size=${fileBuffer.length} bytes`);

        const gridfs = initializeGridFSHandler();
        const fileInfo = await gridfs.uploadFile(
            encryptedResult.encryptedData,
            mediaId,
            {
                senderId: senderId,
                recipientId: recipientId,
                groupId: groupId,
                fileName: req.file.originalname,
                mimeType: req.file.mimetype,
                encrypted: !isPublic,
                encryptionKey: encryptedResult.encryptionKey,
                iv: encryptedResult.iv
            }
        );

        const mediaRecord = {
            id: mediaId,
            senderId: senderId,
            recipientId: recipientId,
            groupId: groupId,
            fileName: req.file.originalname,
            mimeType: req.file.mimetype,
            size: req.file.size,
            uploadedAt: now,
            expiresAt: new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000),
            status: 'available',
            encrypted: !isPublic,
            encryptionKey: encryptedResult.encryptionKey,
            iv: encryptedResult.iv,
            gridfsId: fileInfo.fileId,
            mediaShareMode: mediaShareMode || "protected"
        };

        console.log(`[MEDIA UPLOAD] Inserting metadata into database - mediaId=${mediaId}`);
        const mediaInsertResult = await dbConnection.collection('chatMedia').insertOne(mediaRecord);
        if (!mediaInsertResult.insertedId) {
            console.error(`[MEDIA UPLOAD ERROR] Database insert failed - no insertedId returned`);
            return res.status(500).json({ error: 'Failed to store media record' });
        }
        console.log(`[MEDIA UPLOAD SUCCESS] Saved to DB - insertedId=${mediaInsertResult.insertedId}`);

        await logEvent('media_uploaded', `Media uploaded by ${senderId} for ${recipientId}`, senderId, {
            mediaId: mediaId,
            fileName: req.file.originalname,
            mimeType: req.file.mimetype,
            size: req.file.size,
            encrypted: true
        });

        res.status(201).json({
            success: true,
            message: 'Media uploaded successfully (encrypted)',
            mediaId: mediaId,
            fileName: req.file.originalname,
            mimeType: req.file.mimetype,
            size: req.file.size,
            encrypted: !isPublic,
            encryptionKey: encryptedResult.encryptionKey,
            iv: encryptedResult.iv
        });

    } catch (err) {
        console.error(`[MEDIA UPLOAD ERROR] Exception: ${err.message}`, err.stack);
        res.status(500).json({ error: `Media upload failed: ${err.message}` });
    }
});

app.get('/api/media/:mediaId/download', verifyToken, async (req, res) => {
    const { mediaId } = req.params;
    const userId = req.user.userId;

    try {
        if (!mediaId) {
            return res.status(400).json({ error: 'mediaId is required' });
        }

        const mediaRecord = await dbConnection.collection('chatMedia').findOne({
            id: mediaId
        });

        if (!mediaRecord) {
            return res.status(404).json({ error: 'Media not found' });
        }

        const shareMode = (mediaRecord.mediaShareMode || '').toLowerCase();
        if (shareMode !== 'public') {
            if (mediaRecord.senderId !== userId) {
                const downloadRequest = await dbConnection.collection('mediaDownloadRequests').findOne({
                    mediaId: mediaId,
                    requesterId: userId,
                    status: 'approved'
                });

                if (!downloadRequest) {
                    return res.status(403).json({ error: 'Not authorized to download this media. Please request permission first.' });
                }
            }
        } else {
            console.log(`[MEDIA DOWNLOAD] PUBLIC media access granted to ${userId} for ${mediaId}`);
        }

        if (mediaRecord.expiresAt < new Date()) {
            return res.status(410).json({ error: 'Media has expired' });
        }

        let encryptedBuffer;
        if (mediaRecord.gridfsId) {
            console.log(`[MEDIA DOWNLOAD] Retrieving from GridFS: ${mediaRecord.gridfsId}`);
            const gridfs = initializeGridFSHandler();
            encryptedBuffer = await gridfs.downloadFile(mediaRecord.gridfsId);
        } else if (mediaRecord.encryptedData) {
            console.log(`[MEDIA DOWNLOAD] Retrieving from legacy MongoDB document: ${mediaId}`);
            encryptedBuffer = Buffer.from(mediaRecord.encryptedData, 'base64');
        }

        if (!encryptedBuffer) {
            return res.status(404).json({ error: 'Encrypted file data not found' });
        }

        res.setHeader('Content-Type', mediaRecord.mimeType || 'application/octet-stream');
        res.setHeader('Content-Length', encryptedBuffer.length);
        res.setHeader('Content-Disposition', `attachment; filename="${mediaRecord.fileName}"`);
        res.setHeader('X-Encrypted', mediaRecord.encrypted ? 'true' : 'false');
        res.setHeader('X-Encryption-Key', mediaRecord.encryptionKey || '');
        res.setHeader('X-Encryption-IV', mediaRecord.iv || '');

        await logEvent('media_downloaded', `Media downloaded by ${userId}`, userId, {
            mediaId: mediaId,
            fileName: mediaRecord.fileName,
            fileSize: mediaRecord.size
        });

        res.send(encryptedBuffer);

    } catch (err) {
        console.error('Media download error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/media/:mediaId/download-request', verifyToken, async (req, res) => {
    const { mediaId } = req.params;
    const requesterId = req.user.userId;
    const { reason = 'User requested download' } = req.body;

    try {
        if (!mediaId) {
            return res.status(400).json({ error: 'mediaId is required' });
        }

        const downloadRequestId = uuidv4();
        const now = new Date();

        const requester = await dbConnection.collection('users').findOne({ id: requesterId });

        const downloadRequest = {
            id: downloadRequestId,
            mediaId: mediaId,
            requesterId: requesterId,
            requesterName: requester?.username || 'User',
            requesterAvatar: requester?.avatar || requester?.profile?.profileImageUrl || null,
            status: 'pending',
            reason: reason,
            requestedAt: now,
            respondedAt: null,
            expiresAt: new Date(now.getTime() + 24 * 60 * 60 * 1000),
            approvedDownloadLink: null
        };

        const downloadReqResult = await dbConnection.collection('mediaDownloadRequests').insertOne(downloadRequest);
        if (!downloadReqResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create download request' });
        }

        try {
            const mediaRecord = await dbConnection.collection('chatMedia').findOne({ id: mediaId });
            if (mediaRecord && mediaRecord.senderId) {
                const { broadcastToUser } = require('../websocket/broadcast-utils.js');
                broadcastToUser(global.wsClients, mediaRecord.senderId, 'media.download.requested', {
                    requestId: downloadRequestId,
                    mediaId: mediaId,
                    requesterId: requesterId,
                    requesterName: req.user.username,
                    requesterUsername: req.user.username,
                    reason: reason
                });

                if (pushNotificationManager) {
                    await pushNotificationManager.sendMediaDownloadRequestNotification(mediaRecord.senderId, {
                        userId: requesterId,
                        username: req.user.username
                    }, {
                        mediaId: mediaId,
                        mediaName: mediaRecord.fileName,
                        reason: reason
                    });
                } else {
                    await sendPushNotification(mediaRecord.senderId, {
                        notification: {
                            title: 'Media Download Request',
                            body: `${req.user.username} requested to download your media`,
                            sound: 'default'
                        },
                        data: {
                            type: 'media_download_request',
                            mediaId: mediaId,
                            requestId: downloadRequestId,
                            requesterId: requesterId,
                            requesterName: req.user.username
                        }
                    });
                }
            }
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to notify media owner: ${broadcastErr.message}`);
        }

        await logEvent('media_download_request', `Download request for media ${mediaId}`, requesterId, {
            mediaId: mediaId,
            requesterId: requesterId,
            requestId: downloadRequestId
        });

        res.status(201).json({
            success: true,
            message: 'Download request sent to media owner',
            requestId: downloadRequestId,
            status: 'pending',
            expiresAt: downloadRequest.expiresAt
        });

    } catch (err) {
        console.error('Media download request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/media/download-request/:requestId/approve', verifyToken, async (req, res) => {
    const { requestId } = req.params;
    const approverId = req.user.userId;

    try {
        if (!requestId) {
            return res.status(400).json({ error: 'requestId is required' });
        }

        const downloadRequest = await dbConnection.collection('mediaDownloadRequests').findOne({
            id: requestId
        });

        if (!downloadRequest) {
            return res.status(404).json({ error: 'Download request not found' });
        }

        const mediaRecord = await dbConnection.collection('chatMedia').findOne({
            id: downloadRequest.mediaId
        });

        if (!mediaRecord) {
            return res.status(404).json({ error: 'Media not found' });
        }

        if (mediaRecord.senderId !== approverId) {
            return res.status(403).json({ error: 'Only media owner can approve downloads' });
        }

        const now = new Date();
        const updateResult = await dbConnection.collection('mediaDownloadRequests').updateOne(
            { id: requestId },
            {
                $set: {
                    status: 'approved',
                    respondedAt: now,
                    approvedDownloadLink: `/api/media/${downloadRequest.mediaId}/download`
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to update download request' });
        }

        try {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, downloadRequest.requesterId, 'media.download.approved', {
                requestId: requestId,
                mediaId: downloadRequest.mediaId,
                downloadUrl: `/api/media/${downloadRequest.mediaId}/download`,
                fileName: mediaRecord.fileName,
                mimeType: mediaRecord.mimeType,
                size: mediaRecord.size,
                expiresAt: new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000),
                encrypted: mediaRecord.encrypted || false,
                encryptionKey: mediaRecord.encryptionKey || null,
                iv: mediaRecord.iv || null
            });

            try {
                if (pushNotificationManager) {
                    await pushNotificationManager.sendMediaApprovedNotification(downloadRequest.requesterId, {
                        mediaId: downloadRequest.mediaId,
                        mediaName: mediaRecord.fileName,
                        downloadUrl: `/api/media/${downloadRequest.mediaId}/download`,
                        mediaKey: mediaRecord.encryptionKey
                    });
                } else {
                    await sendPushNotification(downloadRequest.requesterId, {
                        notification: {
                            title: 'Media Download Approved',
                            body: `Your request to download media has been approved`,
                            sound: 'default'
                        },
                        data: {
                            type: 'media_download_approved',
                            mediaId: downloadRequest.mediaId,
                            requestId: requestId,
                            encrypted: (mediaRecord.encrypted || false).toString()
                        }
                    });
                }
            } catch (fcmErr) {
                console.warn(`[WARN] FCM push notification failed for media approval: ${fcmErr.message}`);
            }
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to broadcast media approval: ${broadcastErr.message}`);
        }

        await logEvent('media_download_approved', `Download request approved for media ${downloadRequest.mediaId}`, approverId, {
            requestId: requestId,
            mediaId: downloadRequest.mediaId,
            requesterId: downloadRequest.requesterId
        });

        res.json({
            success: true,
            message: 'Download request approved',
            mediaId: downloadRequest.mediaId,
            fileName: mediaRecord.fileName,
            mimeType: mediaRecord.mimeType,
            size: mediaRecord.size,
            downloadLink: `/api/media/${downloadRequest.mediaId}/download`,
            expiresAt: new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000),
            encrypted: mediaRecord.encrypted || false,
            encryptionKey: mediaRecord.encryptionKey || null,
            iv: mediaRecord.iv || null
        });

    } catch (err) {
        console.error('Approve download request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/media/download-request/:requestId/deny', verifyToken, async (req, res) => {
    const { requestId } = req.params;
    const denierId = req.user.userId;
    const { reason = 'Request denied' } = req.body;

    try {
        if (!requestId) {
            return res.status(400).json({ error: 'requestId is required' });
        }

        const downloadRequest = await dbConnection.collection('mediaDownloadRequests').findOne({
            id: requestId
        });

        if (!downloadRequest) {
            return res.status(404).json({ error: 'Download request not found' });
        }

        const now = new Date();
        const updateResult = await dbConnection.collection('mediaDownloadRequests').updateOne(
            { id: requestId },
            {
                $set: {
                    status: 'denied',
                    respondedAt: now
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to update download request' });
        }

        try {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, downloadRequest.requesterId, 'media.download.denied', {
                requestId: requestId,
                mediaId: downloadRequest.mediaId,
                reason: reason
            });
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to broadcast media denial: ${broadcastErr.message}`);
        }

        await logEvent('media_download_denied', `Download request denied for media ${downloadRequest.mediaId}`, denierId, {
            requestId: requestId,
            mediaId: downloadRequest.mediaId,
            requesterId: downloadRequest.requesterId
        });

        res.json({
            success: true,
            message: 'Download request denied',
            reason: reason
        });

    } catch (err) {
        console.error('Deny download request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/media/download-requests/pending', verifyToken, async (req, res) => {
    const userId = req.user.userId;

    try {
        const ownedMedia = await dbConnection.collection('chatMedia').find({ senderId: userId }).project({ id: 1 }).toArray();
        const ownedMediaIds = ownedMedia.map(m => m.id).filter(Boolean);

        if (ownedMediaIds.length === 0) {
            return res.json({ success: true, count: 0, requests: [] });
        }

        const pendingRequests = await dbConnection.collection('mediaDownloadRequests')
            .find({
                status: 'pending',
                mediaId: { $in: ownedMediaIds }
            })
            .sort({ requestedAt: -1 })
            .limit(100)
            .toArray();

        res.json({
            success: true,
            count: pendingRequests.length,
            requests: pendingRequests.map(req => ({
                requestId: req.id,
                mediaId: req.mediaId,
                requesterId: req.requesterId,
                requesterName: req.requesterName || 'User',
                requesterAvatar: req.requesterAvatar || null,
                reason: req.reason,
                requestedAt: req.requestedAt,
                expiresAt: req.expiresAt,
                status: req.status
            }))
        });

    } catch (err) {
        console.error('Get download requests error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/deletion-vote/initiate', verifyToken, async (req, res) => {
    const { groupId } = req.params;
    const { groupName } = req.body;
    const initiatorId = req.user.userId;

    try {
        if (!groupId || !groupName) {
            return res.status(400).json({ error: 'groupId and groupName are required' });
        }

        const group = await dbConnection.collection('groups').findOne({
            id: groupId,
            adminId: initiatorId
        });

        if (!group) {
            return res.status(403).json({ error: 'Only group admin can initiate deletion vote' });
        }

        const voteId = uuidv4();
        const now = new Date();
        const expiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000);

        const totalMembers = group.members ? group.members.length : 0;

        const groupVote = {
            id: voteId,
            groupId: groupId,
            groupName: groupName,
            voteType: 'deletion',
            status: 'active',
            initiatorId: initiatorId,
            initiatorUsername: req.user.username,
            createdAt: now,
            expiresAt: expiresAt,
            totalMembers: totalMembers,
            approvalThreshold: 50,
            votes: [],
            approvalCount: 0,
            rejectionCount: 0
        };

        const groupVoteResult = await dbConnection.collection('groupDeletionVotes').insertOne(groupVote);
        if (!groupVoteResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create deletion vote' });
        }

        await logEvent('group_deletion_vote_initiated',
            `Group deletion vote started for group: ${groupName}`,
            initiatorId,
            { groupId, voteId, totalMembers }
        );

        res.status(201).json({
            success: true,
            voteId: voteId,
            message: `Group deletion vote started for "${groupName}"`,
            votingPeriodHours: 24,
            totalMembers: totalMembers,
            approvalsNeeded: Math.ceil(totalMembers * 0.5) + 1
        });

    } catch (err) {
        console.error('Initiate group deletion vote error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/deletion-vote/:voteId/vote', verifyToken, async (req, res) => {
    const { groupId, voteId } = req.params;
    const { vote } = req.body;
    const voterId = req.user.userId;

    try {
        if (typeof vote !== 'boolean') {
            return res.status(400).json({ error: 'vote must be true (approve) or false (reject)' });
        }

        const groupVote = await dbConnection.collection('groupDeletionVotes').findOne({
            id: voteId,
            groupId: groupId,
            status: 'active'
        });

        if (!groupVote) {
            return res.status(404).json({ error: 'Voting period has ended or vote not found' });
        }

        const alreadyVoted = groupVote.votes.some(v => v.voterId === voterId);
        if (alreadyVoted) {
            return res.status(409).json({ error: 'You have already voted on this' });
        }

        const group = await dbConnection.collection('groups').findOne({
            id: groupId
        });

        if (!group || !group.members.includes(voterId)) {
            return res.status(403).json({ error: 'Only group members can vote' });
        }

        const now = new Date();
        const updateResult = await dbConnection.collection('groupDeletionVotes').updateOne(
            { id: voteId },
            {
                $push: {
                    votes: {
                        voterId: voterId,
                        vote: vote,
                        votedAt: now
                    }
                },
                $inc: {
                    ...(vote ? { approvalCount: 1 } : { rejectionCount: 1 })
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to record vote' });
        }

        const updatedVote = await dbConnection.collection('groupDeletionVotes').findOne({
            id: voteId
        });

        const approvalPercentage = updatedVote.totalMembers > 0
            ? Math.round((updatedVote.approvalCount / updatedVote.totalMembers) * 100)
            : 0;

        const isApproved = updatedVote.approvalCount > (updatedVote.totalMembers * 0.5);

        if (isApproved) {
            await dbConnection.collection('groupDeletionVotes').updateOne(
                { id: voteId },
                { $set: { status: 'approved' } }
            );

            await dbConnection.collection('groups').deleteOne({ id: groupId });

            await logEvent('group_deleted', `Group deleted by member vote: ${groupVote.groupName}`, voterId);
        }

        await logEvent('group_vote_cast', `Vote cast on group deletion: ${groupId}`, voterId, {
            voteId: voteId,
            vote: vote ? 'approved' : 'rejected',
            approvalPercentage: approvalPercentage
        });

        res.json({
            success: true,
            voteId: voteId,
            voteRecorded: vote ? 'approve' : 'reject',
            approvalPercentage: approvalPercentage,
            totalVotesCast: updatedVote.votes.length,
            totalMembers: updatedVote.totalMembers,
            voteApproved: isApproved
        });

    } catch (err) {
        console.error('Cast group deletion vote error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId/votes/active', verifyToken, async (req, res) => {
    const { groupId } = req.params;
    const userId = req.user.userId;

    try {
        const [deletionVotes, historyVotes] = await Promise.all([
            dbConnection.collection('groupDeletionVotes')
                .find({
                    groupId: groupId,
                    status: 'active'
                })
                .toArray(),
            dbConnection.collection('clearHistoryVotes')
                .find({
                    groupId: groupId,
                    status: 'active'
                })
                .toArray()
        ]);

        const deletionVotesWithStatus = deletionVotes.map(vote => {
            const hasVoted = vote.votes.some(v => v.voterId === userId);
            const approvalPercentage = vote.totalMembers > 0
                ? Math.round((vote.approvalCount / vote.totalMembers) * 100)
                : 0;

            return {
                voteId: vote.id,
                voteType: vote.voteType || 'DELETION',
                groupName: vote.groupName,
                initiatorUsername: vote.initiatorUsername,
                status: vote.status,
                approvalThreshold: vote.approvalThreshold,
                totalMembers: vote.totalMembers,
                approvingVotes: vote.approvalCount,
                rejectingVotes: vote.rejectionCount,
                approvalPercentage: approvalPercentage,
                hasUserVoted: hasVoted,
                expiresAt: vote.expiresAt instanceof Date ? vote.expiresAt.getTime() : new Date(vote.expiresAt).getTime(),
                createdAt: vote.createdAt instanceof Date ? vote.createdAt.getTime() : new Date(vote.createdAt).getTime()
            };
        });

        const historyVotesWithStatus = historyVotes.map(vote => {
            const hasVoted = !!(vote.votes && vote.votes[userId]);
            const approvalPercentage = vote.memberCount > 0
                ? Math.round((vote.voteCount.yes / vote.memberCount) * 100)
                : 0;

            return {
                voteId: vote.id,
                voteType: 'CLEAR_HISTORY',
                groupName: vote.groupName || 'Group',
                initiatorUsername: vote.initiatorUsername,
                status: vote.status,
                approvalThreshold: vote.memberCount > 0 ? Math.round((Math.max(1, Math.floor(vote.memberCount / 2)) / vote.memberCount) * 100) : 50,
                totalMembers: vote.memberCount,
                approvingVotes: vote.voteCount.yes,
                rejectingVotes: vote.voteCount.no,
                approvalPercentage: approvalPercentage,
                hasUserVoted: hasVoted,
                expiresAt: new Date(vote.expiresAt).getTime(),
                createdAt: new Date(vote.createdAt).getTime()
            };
        });

        const allVotes = [...deletionVotesWithStatus, ...historyVotesWithStatus];

        res.json({
            success: true,
            count: allVotes.length,
            votes: allVotes
        });

    } catch (err) {
        console.error('Get active votes error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId/votes/:voteId/results', verifyToken, async (req, res) => {
    const { groupId, voteId } = req.params;

    try {
        const vote = await dbConnection.collection('groupDeletionVotes').findOne({
            id: voteId,
            groupId: groupId
        });

        if (!vote) {
            return res.status(404).json({ error: 'Vote not found' });
        }

        const approvalPercentage = vote.totalMembers > 0
            ? Math.round((vote.approvalCount / vote.totalMembers) * 100)
            : 0;

        const isApproved = vote.approvalCount > (vote.totalMembers * 0.5);

        res.json({
            success: true,
            voteId: voteId,
            voteType: vote.voteType,
            status: vote.status,
            approved: isApproved,
            approvalPercentage: approvalPercentage,
            totalMembers: vote.totalMembers,
            approvingVotes: vote.approvalCount,
            rejectingVotes: vote.rejectionCount,
            votesReceived: vote.votes.length,
            result: isApproved ? 'approved' : 'rejected',
            expiresAt: vote.expiresAt.getTime(),
            completedAt: vote.completedAt ? vote.completedAt.getTime() : null
        });

    } catch (err) {
        console.error('Get vote results error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/chat/:recipientId/files', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const files = await dbConnection.collection('Files').find({
            $or: [
                { chatWithUser: recipientId, uploadedBy: userId },
                { chatWithUser: userId, uploadedBy: recipientId }
            ]
        }).sort({ uploadedAt: -1 }).toArray();

        res.json({
            success: true,
            files: files.map(file => ({
                fileId: file.fileId,
                fileName: file.fileName,
                fileSize: file.fileSize,
                mimeType: file.mimeType,
                uploadedBy: file.uploadedBy,
                uploadedAt: file.uploadedAt
            }))
        });

    } catch (err) {
        console.error('Get chat files error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/messages/:messageId/files', verifyToken, async (req, res) => {
    const { messageId } = req.params;
    const { fileId } = req.body;
    const userId = req.user.userId;

    try {
        const fileRecord = await dbConnection.collection('Files').findOne({ fileId });
        if (!fileRecord || fileRecord.uploadedBy !== userId) {
            return res.status(404).json({ error: 'File not found or access denied' });
        }

        await dbConnection.collection('Files').updateOne(
            { fileId },
            {
                $set: {
                    messageId: messageId,
                    chatWithUser: null,
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('file_attach', `File ${fileId} attached to message ${messageId}`, userId, {
            fileId: fileId,
            messageId: messageId
        });

        res.json({ success: true });

    } catch (err) {
        console.error('Attach file error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/messages/:messageId/files', verifyToken, async (req, res) => {
    const { groupId, messageId } = req.params;
    const { fileId } = req.body;
    const userId = req.user.userId;

    try {
        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && group.members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === userId);
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const message = await dbConnection.collection('groupMessages').findOne({ messageId });
        if (!message || message.groupId !== (group.id || groupId)) {
            return res.status(404).json({ error: 'Message not found in group' });
        }

        const fileRecord = await dbConnection.collection('Files').findOne({ fileId });
        if (!fileRecord || fileRecord.uploadedBy !== userId) {
            return res.status(404).json({ error: 'File not found or access denied' });
        }

        await dbConnection.collection('Files').updateOne(
            { fileId },
            {
                $set: {
                    messageId: messageId,
                    messageType: 'group',
                    groupId: group.id || groupId,
                    updatedAt: new Date()
                }
            }
        );

        await dbConnection.collection('groupMessages').updateOne(
            { messageId },
            { $push: { files: fileId } }
        );

        await logEvent('file_attach_group', `File ${fileId} attached to group message ${messageId}`, userId, {
            fileId: fileId,
            messageId: messageId,
            groupId: group.id || groupId
        });

        res.json({ success: true });

    } catch (err) {
        console.error('Attach file to group message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/:userId/fcm-token', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { fcmToken, deviceName } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (!dbConnection || !dbConnection.isConnected) {
            console.error('[ FCM TOKEN] Database not connected');
            return res.status(503).json({
                error: 'Database unavailable. Please try again in a moment.',
                status: 'db_unavailable'
            });
        }

        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot register token for another user' });
        }

        const fcmConfig = await dbConnection.collection('systemConfig').findOne({ configType: 'fcm' });
        const maxTokens = fcmConfig?.settings?.maxTokensPerUser || 5;

        const existingTokens = await dbConnection.collection('FCMTokens').find({ userId }).toArray();

        if (existingTokens.length >= maxTokens) {
            const oldestToken = existingTokens.reduce((oldest, current) => {
                const oldestDate = new Date(oldest.registeredAt || oldest.lastUsedAt || new Date());
                const currentDate = new Date(current.registeredAt || current.lastUsedAt || new Date());
                return currentDate < oldestDate ? current : oldest;
            });

            await dbConnection.collection('FCMTokens').deleteOne({ _id: oldestToken._id });
            console.log(`[OK] Auto-removed oldest FCM token for user ${userId} to make room for new device`);
        }

        const tokenExists = existingTokens.some(token => token.fcmToken === fcmToken);
        if (tokenExists) {
            await dbConnection.collection('FCMTokens').updateOne(
                { userId, fcmToken },
                {
                    $set: {
                        deviceName: deviceName || 'Unknown Device',
                        lastUsedAt: new Date(),
                        updatedAt: new Date()
                    }
                }
            );
            console.log(`[OK] Updated existing FCM token for user ${userId}`);
        } else {
            const fcmInsertResult = await dbConnection.collection('FCMTokens').insertOne({
                _id: new ObjectId(),
                userId: userId,
                fcmToken: fcmToken,
                deviceName: deviceName || 'Unknown Device',
                registeredAt: new Date(),
                lastUsedAt: new Date()
            });
            if (!fcmInsertResult.insertedId) {
                return res.status(500).json({ error: 'Failed to register FCM token' });
            }
            console.log(`[OK] Registered new FCM token for user ${userId} on device ${deviceName}`);
        }

        await logEvent('fcm_token_register', `FCM token registered for user ${userId}`, userId, {
            deviceName: deviceName,
            tokenCount: Math.max(1, Math.min(existingTokens.length, maxTokens))
        });

        res.status(201).json({
            success: true,
            message: 'FCM token registered successfully'
        });

    } catch (err) {
        console.error('FCM token registration error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/users/:userId/fcm-token', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { fcmToken } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot unregister token for another user' });
        }

        const result = await dbConnection.collection('FCMTokens').deleteOne({
            userId,
            fcmToken: fcmToken
        });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'FCM token not found' });
        }

        await logEvent('fcm_token_unregister', `FCM token unregistered for user ${userId}`, userId, {
            fcmToken: fcmToken
        });

        res.json({ success: true });

    } catch (err) {
        console.error('FCM token unregistration error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/notifications/send-fcm', verifyToken, async (req, res) => {
    const { userId, title, message, type, data } = req.body;
    const requestUserId = req.user.userId;

    try {
        const user = await dbConnection.collection('users').findOne({ id: requestUserId });
        if (!user || user.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Insufficient permissions' });
        }

        const targetUser = await dbConnection.collection('users').findOne({ id: userId });
        if (!targetUser) {
            return res.status(404).json({ error: 'Target user not found' });
        }

        const fcmTokens = await dbConnection.collection('FCMTokens').find({ userId }).toArray();

        if (fcmTokens.length === 0) {
            return res.status(400).json({ error: 'User has no registered FCM tokens' });
        }

        const notification = {
            data: {
                type: type || 'message',
                title: title,
                body: message,
                senderId: requestUserId,
                ...data
            },
            notification: {
                title: title,
                body: message,
                sound: 'default'
            }
        };

        await logEvent('fcm_notification_sent', `FCM notification sent to user ${userId}`, requestUserId, {
            targetUserId: userId,
            title: title,
            type: type,
            tokenCount: fcmTokens.length
        });

        res.json({
            success: true,
            message: `Notification queued for ${fcmTokens.length} devices`,
            notification: notification
        });

    } catch (err) {
        console.error('Send FCM notification error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/notifications/subscribe', verifyToken, async (req, res) => {
    const { topic } = req.body;
    const userId = req.user.userId;

    try {
        const fcmConfig = await dbConnection.collection('systemConfig').findOne({ configType: 'fcm' });
        const allowedTopics = fcmConfig?.settings?.defaultTopics || ['messages', 'system'];

        if (!allowedTopics.includes(topic)) {
            return res.status(400).json({
                error: `Topic not allowed. Allowed topics: ${allowedTopics.join(', ')}`
            });
        }

        await logEvent('topic_subscribe', `User ${userId} subscribed to topic ${topic}`, userId, {
            topic: topic
        });

        res.json({
            success: true,
            message: `Subscribed to topic: ${topic}`
        });

    } catch (err) {
        console.error('Subscribe to topic error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/notifications/register-token', verifyToken, async (req, res) => {
    const { fcmToken, fcm_token, platform = 'android', deviceName, device_name } = req.body;
    const userId = req.user.userId;

    const token = fcmToken || fcm_token;
    const device = deviceName || device_name || 'Android Device';

    if (!token) {
        return res.status(400).json({ error: 'fcmToken or fcm_token is required' });
    }

    try {
        const fcmConfig = await dbConnection.collection('systemConfig').findOne({ configType: 'fcm' });
        const maxTokens = fcmConfig?.settings?.maxTokensPerUser || 5;

        const existingTokens = await dbConnection.collection('FCMTokens').find({ userId }).toArray();

        if (existingTokens.length >= maxTokens) {
            const oldestToken = existingTokens.reduce((oldest, current) => {
                const oldestDate = new Date(oldest.registeredAt || oldest.lastUsedAt || new Date());
                const currentDate = new Date(current.registeredAt || current.lastUsedAt || new Date());
                return currentDate < oldestDate ? current : oldest;
            });
            await dbConnection.collection('FCMTokens').deleteOne({ _id: oldestToken._id });
        }

        const tokenExists = existingTokens.find(t => t.fcmToken === token);
        if (tokenExists) {
            await dbConnection.collection('FCMTokens').updateOne(
                { userId, fcmToken: token },
                {
                    $set: {
                        deviceName: device,
                        platform: platform,
                        lastUsedAt: new Date(),
                        updatedAt: new Date()
                    }
                }
            );
        } else {
            await dbConnection.collection('FCMTokens').insertOne({
                _id: new ObjectId(),
                userId: userId,
                fcmToken: token,
                platform: platform,
                deviceName: device,
                registeredAt: new Date(),
                lastUsedAt: new Date()
            });
        }

        await logEvent('fcm_token_register_compat', `FCM token registered via compat endpoint for user ${userId}`, userId);

        res.json({
            success: true,
            message: 'FCM token registered successfully'
        });
    } catch (err) {
        console.error('Register FCM token compat error:', err);
        res.status(500).json({ error: err.message });
    }
});
app.post('/api/notifications/unsubscribe', verifyToken, async (req, res) => {
    const { topic } = req.body;
    const userId = req.user.userId;

    try {
        await logEvent('topic_unsubscribe', `User ${userId} unsubscribed from topic ${topic}`, userId, {
            topic: topic
        });

        res.json({
            success: true,
            message: `Unsubscribed from topic: ${topic}`
        });

    } catch (err) {
        console.error('Unsubscribe from topic error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/notifications/history', verifyToken, async (req, res) => {
    const userId = req.user.userId;
    const { limit = 50, offset = 0 } = req.query;

    try {
        const notifications = await dbConnection.collection('logs').find({
            userId: userId,
            event: { $in: [
                'fcm_notification_sent',
                'message_received',
                'message_sent',
                'call_initiated',
                'friend_request_sent',
                'friend_request_received',
                'friend_accepted',
                'media_download_request',
                'media_approved',
                'media_denied'
            ] }
        })
        .sort({ timestamp: -1 })
        .limit(parseInt(limit))
        .skip(parseInt(offset))
        .toArray();

        res.json({
            success: true,
            notifications: notifications,
            pagination: {
                limit: parseInt(limit),
                offset: parseInt(offset),
                hasMore: notifications.length === parseInt(limit)
            }
        });

    } catch (err) {
        console.error('Get notification history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/notifications/pending', verifyToken, async (req, res) => {
    const userId = req.user.userId;
    const lastFetchTime = req.query.lastFetch ? new Date(parseInt(req.query.lastFetch)) : new Date(0);

    try {
        console.log(`[ Pending Notifications] Fetching for user ${userId} since ${lastFetchTime.toISOString()}`);

        const messages = await dbConnection.collection('messages').find({
            $or: [
                { recipientId: userId, read: false },
                { recipientId: userId, timestamp: { $gt: lastFetchTime } }
            ],
            timestamp: { $gt: lastFetchTime }
        })
        .sort({ timestamp: -1 })
        .limit(20)
        .toArray();

        const formattedMessages = messages.map(msg => ({
            chatId: msg.chatId || msg.senderId,
            senderId: msg.senderId,
            senderName: msg.senderName || 'Unknown',
            senderAvatar: msg.senderAvatar,
            content: msg.content || msg.message || '',
            timestamp: msg.timestamp.getTime()
        }));

        console.log(`[ Pending Notifications] Returning ${formattedMessages.length} messages`);

        res.json({
            success: true,
            messages: formattedMessages,
            fetchedAt: new Date().getTime()
        });

    } catch (err) {
        console.error('[ Pending Notifications Error]', err);
        res.status(500).json({
            error: err.message,
            messages: []
        });
    }
});

app.post('/api/keys/exchange/:peerId', verifyToken, async (req, res) => {
    const { peerId } = req.params;
    const { publicKey } = req.body;
    const userId = req.user.userId;

    try {
        const encryptionConfig = await dbConnection.collection('systemConfig').findOne({ configType: 'encryption' });
        const keyAlgorithm = encryptionConfig?.settings?.keyAlgorithm || 'RSA';
        const keySize = encryptionConfig?.settings?.keySize || 2048;

        if (!publicKey || publicKey.length < 100) {
            return res.status(400).json({ error: 'Invalid public key format' });
        }

        await dbConnection.collection('PublicKeys').updateOne(
            { userId, peerId },
            {
                $set: {
                    publicKey: publicKey,
                    keyType: keyAlgorithm,
                    keySize: keySize,
                    updatedAt: new Date()
                }
            },
            { upsert: true }
        );

        const userPublicKey = await dbConnection.collection('users').findOne({ id: userId });

        await logEvent('key_exchange', `Public key exchanged with peer ${peerId}`, userId, {
            peerId: peerId,
            keyAlgorithm: keyAlgorithm,
            keySize: keySize
        });

        res.json({
            success: true,
            peerPublicKey: publicKey,
            userPublicKey: userPublicKey?.encryption?.publicKey || null,
            keyInfo: {
                algorithm: keyAlgorithm,
                keySize: keySize
            }
        });

    } catch (err) {
        console.error('Key exchange error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/keys/:userId', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const publicKey = user?.encryption?.publicKey || null;
        const keyInfo = user?.encryption || {};

        res.json({
            success: true,
            publicKey: publicKey,
            keyInfo: {
                algorithm: keyInfo.keyType || 'RSA',
                keySize: keyInfo.keySize || 2048,
                createdAt: keyInfo.createdAt
            }
        });

    } catch (err) {
        console.error('Get public key error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/profile', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { username, bio, status, privacyLevel, tags } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s profile' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const updateData = {};

        if (username && typeof username === 'string' && username.trim()) {
            const existingUser = await dbConnection.collection('users').findOne({
                username: username,
                id: { $ne: userId }
            });

            if (existingUser) {
                return res.status(409).json({ error: 'Username already taken' });
            }

            updateData['username'] = username;
        }

        const finalBio = bio || (req.body.profile && req.body.profile.bio);
        const finalStatus = status || (req.body.profile && req.body.profile.status);
        const finalDisplayName = req.body.displayName || (req.body.profile && req.body.profile.displayName);
        const finalPrivacyLevel = privacyLevel || (req.body.profile && req.body.profile.privacyLevel);

        if (finalBio !== undefined && finalBio !== null && typeof finalBio === 'string') {
            updateData['profile.bio'] = finalBio;
        }
        if (finalStatus !== undefined && finalStatus !== null && typeof finalStatus === 'string') {
            updateData['profile.status'] = finalStatus;
        }
        if (finalDisplayName !== undefined && finalDisplayName !== null && typeof finalDisplayName === 'string') {
            updateData['profile.displayName'] = finalDisplayName;
            updateData['displayName'] = finalDisplayName;
        }
        if (finalPrivacyLevel !== undefined && finalPrivacyLevel !== null && typeof finalPrivacyLevel === 'string') {
            updateData['profile.privacyLevel'] = finalPrivacyLevel;
        }
        if (tags !== undefined && Array.isArray(tags)) {
            updateData['profile.tags'] = tags.filter(t => typeof t === 'string' && t.trim()).map(t => t.trim());
        }

        if (req.body.privacySettings !== undefined && typeof req.body.privacySettings === 'object') {
            const validatePrivacyOption = (val) => {
                const validOptions = ['nobody', 'friends', 'everyone'];
                return validOptions.includes(val) ? val : 'friends';
            };

            updateData['profile.privacySettings'] = {
                lastSeen: validatePrivacyOption(req.body.privacySettings.lastSeen),
                onlineStatus: validatePrivacyOption(req.body.privacySettings.onlineStatus),
                bio: validatePrivacyOption(req.body.privacySettings.bio),
                groups: validatePrivacyOption(req.body.privacySettings.groups),
                aboutBio: validatePrivacyOption(req.body.privacySettings.aboutBio)
            };
        }

        updateData['profile.lastUpdated'] = new Date();
        updateData['updatedAt'] = new Date();

        const result = await dbConnection.collection('users').updateOne(
            { id: userId },
            { $set: updateData }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('profile_update', `Profile updated for user ${userId}`, userId, {
            fields: Object.keys(updateData)
        });

        try {
            const { notifyUserProfileUpdated } = require('../websocket/broadcast-utils.js');
            const userWithFriends = await dbConnection.collection('users').findOne({ id: userId });
            const friends = (userWithFriends && userWithFriends.friends) || [];
            notifyUserProfileUpdated(global.wsClients, userId, updateData, friends);
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to broadcast profile update: ${broadcastErr.message}`);
        }

        res.json({
            success: true,
            profile: updateData
        });

    } catch (err) {
        console.error('Update profile error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId/privacy-settings', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Can only view your own privacy settings' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const privacySettings = user.profile?.privacySettings || {
            lastSeen: 'friends',
            onlineStatus: 'friends',
            bio: 'friends',
            groups: 'friends',
            aboutBio: 'friends'
        };

        res.json({
            success: true,
            privacySettings
        });
    } catch (err) {
        console.error('Get privacy settings error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/privacy-settings', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Can only update your own privacy settings' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const validatePrivacyOption = (val) => {
            const validOptions = ['nobody', 'friends', 'everyone'];
            return validOptions.includes(val) ? val : 'friends';
        };

        const updateData = {
            'profile.privacySettings': {
                lastSeen: validatePrivacyOption(req.body.lastSeen !== undefined ? req.body.lastSeen : user.profile?.privacySettings?.lastSeen),
                onlineStatus: validatePrivacyOption(req.body.onlineStatus !== undefined ? req.body.onlineStatus : user.profile?.privacySettings?.onlineStatus),
                bio: validatePrivacyOption(req.body.bio !== undefined ? req.body.bio : user.profile?.privacySettings?.bio),
                groups: validatePrivacyOption(req.body.groups !== undefined ? req.body.groups : user.profile?.privacySettings?.groups),
                aboutBio: validatePrivacyOption(req.body.aboutBio !== undefined ? req.body.aboutBio : user.profile?.privacySettings?.aboutBio)
            },
            'profile.lastUpdated': new Date(),
            'updatedAt': new Date()
        };

        const result = await dbConnection.collection('users').updateOne(
            { id: userId },
            { $set: updateData }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'User not found' });
        }

        res.json({
            success: true,
            privacySettings: updateData['profile.privacySettings'],
            message: 'Privacy settings updated successfully'
        });
    } catch (err) {
        console.error('Update privacy settings error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/:userId/profile-image', (req, res, next) => {
    if (req.is('application/json')) {
        return next();
    }
    upload.single('profileImage')(req, res, next);
}, verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user?.userId;

    console.log('[UPLOAD] Profile image POST request:', {
        urlUserId: userId,
        tokenUserId: requestUserId,
        hasFile: !!req.file,
        fileName: req.file?.originalname,
        fileSize: req.file?.size,
        mimeType: req.file?.mimetype,
        hasAuth: !!req.user,
        authHeaders: req.headers.authorization ? 'YES' : 'NO',
        contentType: req.headers['content-type']
    });

    try {
        if (userId !== requestUserId) {
            console.error('[UPLOAD] USER MISMATCH:', { urlUserId, requestUserId });
            return res.status(403).json({
                error: 'Cannot upload profile image for another user',
                details: `URL userId (${userId}) != Token userId (${requestUserId})`
            });
        }

        let base64Image, mimeType, originalname, fileSize;

        if (req.file) {
            const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
            if (!allowedTypes.includes(req.file.mimetype)) {
                return res.status(415).json({ error: `Image type not allowed. Allowed types: ${allowedTypes.join(', ')}` });
            }
            const fileBuffer = fs.readFileSync(req.file.path);
            base64Image = fileBuffer.toString('base64');
            mimeType = req.file.mimetype;
            originalname = req.file.originalname;
            fileSize = req.file.size;
        } else if (req.body && req.body.image) {
            base64Image = req.body.image;
            mimeType = req.body.mimeType || 'image/jpeg';
            originalname = 'upload.' + (mimeType.split('/')[1] || 'jpg');
            fileSize = Math.round(base64Image.length * 0.75);
        } else {
            return res.status(400).json({ error: 'No profile image uploaded. Send multipart with field "profileImage" or JSON with "image" (base64).' });
        }

        const imageId = uuidv4();
        const imageUrl = `/api/users/${userId}/profile-image/${imageId}`;

        console.log('[UPLOAD] Storing image:', { userId, imageId, fileName: originalname, size: fileSize });

        const imagesCollection = dbConnection.collection('profile_images');
        const imageInsertResult = await imagesCollection.insertOne({
            imageId: imageId,
            userId: userId,
            filename: originalname,
            mimetype: mimeType,
            base64Data: base64Image,
            fileSize: fileSize,
            createdAt: new Date(),
            updatedAt: new Date()
        });

        if (!imageInsertResult.insertedId) {
            console.error('[UPLOAD] DB INSERT FAILED');
            return res.status(500).json({
                error: 'Failed to store image in database',
                success: false
            });
        }

        console.log('[UPLOAD] Image stored:', { insertedId: imageInsertResult.insertedId });

        const profileUpdateResult = await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.profileImageUrl': imageUrl,
                    'profile.profileImageId': imageId,
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        if (profileUpdateResult.matchedCount === 0) {
            console.error('[UPLOAD] USER NOT FOUND:', { userId });
            return res.status(404).json({
                error: 'User not found or profile not updated',
                success: false
            });
        }

        if (profileUpdateResult.modifiedCount === 0) {
            console.warn(`[UPLOAD] Profile already has same image for user ${userId}`);
        }

        if (req.file && req.file.path && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }

        await logEvent('profile_image_upload', `Profile image uploaded for user ${userId}`, userId, {
            fileName: originalname,
            fileSize: fileSize,
            imageUrl: imageUrl
        });

        console.log('[UPLOAD] SUCCESS:', { userId, imageUrl });

        try {
            if (global.io) {
                global.io.emit('profile_updated', {
                    userId: userId,
                    field: 'avatar',
                    value: imageUrl,
                    timestamp: new Date()
                });
                console.log('[SOCKET.IO] Profile update broadcast sent for user:', userId);
            }
        } catch (emitErr) {
            console.warn('[SOCKET.IO] Failed to broadcast profile update:', emitErr.message);
        }

        res.status(201).json({
            success: true,
            profileImage: {
                imageUrl: imageUrl,
                fileName: req.file.originalname,
                fileSize: req.file.size
            }
        });

    } catch (err) {
        console.error('[UPLOAD] EXCEPTION:', { error: err.message, stack: err.stack });
        if (req.file && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }
        res.status(500).json({
            error: `Server error: ${err.message}`,
            errorType: err.constructor.name
        });
    }
});

app.get('/api/users/:userId/profile-image/:imageId?', async (req, res) => {
    const { userId, imageId } = req.params;

    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    try {
        console.log(`[IMG] Fetching profile image:`, { userId, imageId });

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            console.warn(`[IMG] User not found:`, { userId });
            return res.status(404).json({ error: 'User not found' });
        }

        const privacySettings = user.privacySettings || { profileImage: 'public' };
        if (privacySettings.profileImage === 'private') {
            console.warn(`[IMG] Profile image is private:`, { userId });
            return res.status(403).json({ error: 'User has made their profile image private' });
        }

        let image;

        if (imageId) {
            console.log(`[IMG] Querying by imageId:`, { userId, imageId });
            image = await dbConnection.collection('profile_images').findOne({
                imageId: imageId,
                userId: userId
            });

            if (!image) {
                console.warn(`[IMG] Image not found by ID:`, { userId, imageId });
                const existingImages = await dbConnection.collection('profile_images')
                    .find({ userId: userId })
                    .project({ imageId: 1 })
                    .limit(5)
                    .toArray();
                console.warn(`[IMG] Existing images for user ${userId}:`, existingImages);
            }
        } else {
            const profileImageUrl = user?.profile?.profileImageUrl;
            console.log(`[IMG] User profile.profileImageUrl:`, { userId, url: profileImageUrl });

            if (!profileImageUrl) {
                console.warn(`[IMG] No profileImageUrl in user profile:`, { userId });
                return res.status(404).json({ error: 'Profile image not found - no URL in profile' });
            }

            const extractedId = profileImageUrl.split('/').pop();
            console.log(`[IMG] Extracted imageId from URL:`, { userId, extractedId });

            image = await dbConnection.collection('profile_images').findOne({
                imageId: extractedId,
                userId: userId
            });

            if (!image) {
                console.warn(`[IMG] Image not found by extracted ID:`, { userId, extractedId });
            }
        }

        if (!image) {
            console.error(`[IMG] Profile image not found in database:`, { userId, imageId });
            return res.status(404).json({ error: 'Profile image not found in database' });
        }

        console.log(`[IMG] Image found, serving:`, { userId, imageId: image.imageId, size: image.base64Data?.length });

        let base64Data = image.base64Data;
        if (!base64Data) {
            console.error(`[IMG] Image has no base64Data:`, { userId, imageId });
            return res.status(500).json({ error: 'Image data is corrupted (missing base64Data)' });
        }

        if (base64Data.startsWith('data:')) {
            base64Data = base64Data.split(',')[1];
        }

        let imageBuffer;
        try {
            imageBuffer = Buffer.from(base64Data, 'base64');
            if (imageBuffer.length === 0) {
                console.error(`[IMG] Buffer is empty after conversion:`, { userId, imageId });
                return res.status(500).json({ error: 'Image data is corrupted (empty buffer)' });
            }
        } catch (bufErr) {
            console.error(`[IMG] Failed to convert base64 to buffer:`, { userId, imageId, error: bufErr.message });
            return res.status(500).json({ error: 'Failed to decode image data' });
        }

        const mimeType = image.mimetype || 'image/jpeg';
        res.setHeader('Content-Type', mimeType);
        res.setHeader('Content-Length', imageBuffer.length);
        res.setHeader('Cache-Control', 'public, max-age=86400');
        res.setHeader('X-Image-Source', 'profile-images-collection');

        console.log(`[IMG] Sending image to client:`, { userId, mimeType, size: imageBuffer.length });
        res.send(imageBuffer);

    } catch (err) {
        console.error(`[IMG] Error serving profile image:`, { error: err.message, stack: err.stack });
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/users/:userId/profile-image', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot delete another user\'s profile image' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.profileImageUrl': '',
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('profile_image_delete', `Profile image deleted for user ${userId}`, userId);

        res.json({ success: true });

    } catch (err) {
        console.error('Delete profile image error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId/online-status', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        const user = await dbConnection.collection('users').findOne(
            { id: userId },
            { projection: { id: 1, isOnline: 1, lastSeen: 1, lastActivityAt: 1, status: 1, 'profile.privacySettings': 1 } }
        );

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const isOwnProfile = requestUserId === userId;
        const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, userId);
        const privacySettings = user.profile?.privacySettings || {};
        const settings = {
            lastSeen: privacySettings.lastSeen || 'friends',
            onlineStatus: privacySettings.onlineStatus || 'friends'
        };

        const now = Date.now();
        const lastActivity = user.lastActivityAt ? new Date(user.lastActivityAt).getTime() : null;
        const isOnline = lastActivity && (now - lastActivity) < 5 * 60 * 1000;

        let lastSeenStr = null;
        if (isOnline) {
            lastSeenStr = 'Online now';
        } else if (lastActivity) {
            const minutesAgo = Math.floor((now - lastActivity) / (60 * 1000));
            if (minutesAgo < 1) lastSeenStr = 'Just now';
            else if (minutesAgo < 60) lastSeenStr = `${minutesAgo}m ago`;
            else {
                const hoursAgo = Math.floor(minutesAgo / 60);
                lastSeenStr = hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
            }
        } else {
            lastSeenStr = 'Offline';
        }

        res.json({
            success: true,
            userId: user.id,
            isOnline: canViewPrivateField(settings.onlineStatus, requestUserId, userId, friendStatus) ? isOnline : false,
            lastSeen: canViewPrivateField(settings.lastSeen, requestUserId, userId, friendStatus) ? lastSeenStr : 'Hidden',
            status: user.status || 'offline'
        });
    } catch (err) {
        console.error('Get online status error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/online-status-batch', verifyToken, async (req, res) => {
    const { userIds } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (!Array.isArray(userIds)) {
            return res.status(400).json({ error: 'userIds must be an array' });
        }

        const users = await dbConnection.collection('users')
            .find({ id: { $in: userIds } })
            .project({ id: 1, isOnline: 1, lastSeen: 1, lastActivityAt: 1, status: 1, 'profile.privacySettings': 1 })
            .toArray();

        const otherIds = userIds.filter(id => id !== requestUserId);
        const friendRelations = otherIds.length > 0 ? await dbConnection.collection('friends').find({
            $or: [
                { userId1: requestUserId, userId2: { $in: otherIds } },
                { userId2: requestUserId, userId1: { $in: otherIds } }
            ]
        }).toArray() : [];
        const friendIds = new Set();
        friendRelations.forEach(f => {
            friendIds.add(f.userId1 === requestUserId ? f.userId2 : f.userId1);
        });

        const now = Date.now();
        const statusMap = {};
        users.forEach(user => {
            const isOwnProfile = requestUserId === user.id;
            const friendStatus = isOwnProfile ? 'friends' : (friendIds.has(user.id) ? 'friends' : 'not-friends');
            const privacySettings = user.profile?.privacySettings || {};
            const settings = {
                lastSeen: privacySettings.lastSeen || 'friends',
                onlineStatus: privacySettings.onlineStatus || 'friends'
            };

            const lastActivity = user.lastActivityAt ? new Date(user.lastActivityAt).getTime() : null;
            const isOnline = (lastActivity && (now - lastActivity) < 5 * 60 * 1000) || user.isOnline === true;

            let lastSeenStr = null;
            if (isOnline) lastSeenStr = 'Online now';
            else if (lastActivity) {
                const minutesAgo = Math.floor((now - lastActivity) / (60 * 1000));
                if (minutesAgo < 1) lastSeenStr = 'Just now';
                else if (minutesAgo < 60) lastSeenStr = `${minutesAgo}m ago`;
                else {
                    const hoursAgo = Math.floor(minutesAgo / 60);
                    lastSeenStr = hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
                }
            } else lastSeenStr = 'Offline';

            statusMap[user.id] = {
                userId: user.id,
                isOnline: canViewPrivateField(settings.onlineStatus, requestUserId, user.id, friendStatus) ? isOnline : false,
                lastSeen: canViewPrivateField(settings.lastSeen, requestUserId, user.id, friendStatus) ? lastSeenStr : 'Hidden',
                status: user.status || 'offline'
            };
        });

        userIds.forEach(id => {
            if (!statusMap[id]) {
                statusMap[id] = {
                    userId: id,
                    isOnline: false,
                    lastSeen: null,
                    status: 'offline'
                };
            }
        });

        res.json({
            success: true,
            statuses: statusMap
        });
    } catch (err) {
        console.error('Get online status batch error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/:userId/profile-banner', (req, res, next) => {
    if (req.is('application/json')) {
        return next();
    }
    upload.single('profileBanner')(req, res, next);
}, verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot upload profile banner for another user' });
        }

        let base64Image, mimeType, originalname, fileSize;

        if (req.file) {
            const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
            if (!allowedTypes.includes(req.file.mimetype)) {
                return res.status(415).json({ error: `Image type not allowed. Allowed types: ${allowedTypes.join(', ')}` });
            }
            const fileBuffer = fs.readFileSync(req.file.path);
            base64Image = fileBuffer.toString('base64');
            mimeType = req.file.mimetype;
            originalname = req.file.originalname;
            fileSize = req.file.size;
        } else if (req.body && req.body.image) {
            base64Image = req.body.image;
            mimeType = req.body.mimeType || 'image/jpeg';
            originalname = 'banner.' + (mimeType.split('/')[1] || 'jpg');
            fileSize = Math.round(base64Image.length * 0.75);
        } else {
            return res.status(400).json({ error: 'No profile banner uploaded. Send multipart with field "profileBanner" or JSON with "image" (base64).' });
        }

        const bannerId = uuidv4();
        const bannerUrl = `/api/users/${userId}/profile-banner/${bannerId}`;

        const bannersCollection = dbConnection.collection('profile_banners');
        const bannerInsertResult = await bannersCollection.insertOne({
            bannerId: bannerId,
            userId: userId,
            filename: originalname,
            mimetype: mimeType,
            base64Data: base64Image,
            fileSize: fileSize,
            createdAt: new Date(),
            updatedAt: new Date()
        });

        if (!bannerInsertResult.insertedId) {
            return res.status(500).json({
                error: 'Failed to store banner in database',
                success: false
            });
        }

        const bannerUpdateResult = await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.bannerUrl': bannerUrl,
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        if (bannerUpdateResult.matchedCount === 0) {
            return res.status(404).json({
                error: 'User not found or banner not updated',
                success: false
            });
        }

        if (bannerUpdateResult.modifiedCount === 0) {
            console.warn(`Banner update for user ${userId} matched but did not modify (possible duplicate banner)`);
        }

        if (req.file && req.file.path && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }

        await logEvent('profile_banner_upload', `Profile banner uploaded for user ${userId}`, userId, {
            fileName: originalname,
            fileSize: fileSize,
            bannerUrl: bannerUrl
        });

        res.status(201).json({
            success: true,
            bannerUrl: bannerUrl,
            profileBanner: {
                bannerUrl: bannerUrl,
                fileName: originalname,
                fileSize: fileSize
            }
        });

    } catch (err) {
        console.error('Profile banner upload error:', err);
        if (req.file && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId/profile-banner/:bannerId', async (req, res) => {
    const { userId, bannerId } = req.params;
    try {
        const banner = await dbConnection.collection('profile_banners').findOne({
            bannerId: bannerId,
            userId: userId
        });

        if (!banner) {
            return res.status(404).json({ error: 'Profile banner not found' });
        }

        let base64Data = banner.base64Data;
        if (base64Data.startsWith('data:')) {
            base64Data = base64Data.split(',')[1];
        }

        const bannerBuffer = Buffer.from(base64Data, 'base64');

        res.setHeader('Content-Type', banner.mimetype || 'image/jpeg');
        res.setHeader('Content-Length', bannerBuffer.length);
        res.setHeader('Cache-Control', 'public, max-age=86400');

        res.send(bannerBuffer);

    } catch (err) {
        console.error('Get profile banner error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/users/:userId/profile-banner', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot delete another user\'s profile banner' });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.bannerUrl': '',
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('profile_banner_delete', `Profile banner deleted for user ${userId}`, userId);
        res.json({ success: true });

    } catch (err) {
        console.error('Delete profile banner error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/:userId/block/:blockUserId', verifyToken, async (req, res) => {
    const { userId, blockUserId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot block users for another account' });
        }

        if (blockUserId === userId) {
            return res.status(400).json({ error: 'Cannot block yourself' });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $addToSet: {
                    'profile.blockedUsers': blockUserId
                },
                $set: {
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('user_blocked', `User ${blockUserId} blocked by ${userId}`, userId);

        res.json({ success: true, message: 'User blocked' });

    } catch (err) {
        console.error('Block user error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/users/:userId/block/:blockUserId', verifyToken, async (req, res) => {
    const { userId, blockUserId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot unblock users for another account' });
        }

        const unblockResult = await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $pull: {
                    'profile.blockedUsers': blockUserId
                },
                $set: {
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        if (unblockResult.matchedCount === 0) {
            return res.status(404).json({ error: 'User not found' });
        }

        if (unblockResult.modifiedCount === 0) {
            return res.status(404).json({ error: 'User was not in blocked list' });
        }

        await logEvent('user_unblocked', `User ${blockUserId} unblocked by ${userId}`, userId);

        res.json({ success: true, message: 'User unblocked' });

    } catch (err) {
        console.error('Unblock user error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/users/:reportedUserId/report', verifyToken, async (req, res) => {
    const { reportedUserId } = req.params;
    const { reason, description } = req.body;
    const reporterId = req.user.userId;

    try {
        if (!reportedUserId || !reason) {
            return res.status(400).json({ error: 'reportedUserId and reason are required' });
        }

        if (reportedUserId === reporterId) {
            return res.status(400).json({ error: 'Cannot report yourself' });
        }

        const reportedUser = await dbConnection.collection('users').findOne({ id: reportedUserId });
        if (!reportedUser) {
            return res.status(404).json({ error: 'Reported user not found' });
        }

        const reportId = `report_${uuidv4()}`;
        const abuseReportResult = await dbConnection.collection('abuseReports').insertOne({
            id: reportId,
            reporterId,
            reportedUserId,
            reason,
            description: description || '',
            status: 'open',
            createdAt: new Date(),
            updatedAt: new Date()
        });
        if (!abuseReportResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create abuse report' });
        }

        await logEvent('user_reported', `User ${reportedUserId} reported by ${reporterId} for reason: ${reason}`, reporterId);

        res.json({
            success: true,
            reportId,
            message: 'User report submitted successfully. Our team will review it shortly.'
        });
    } catch (err) {
        console.error('Report user error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users', verifyToken, async (req, res) => {
    const { search } = req.query;
    const limit = Math.min(parseInt(req.query.limit) || 50, 100);
    const skip = parseInt(req.query.skip) || 0;

    try {
        let filter = { role: { $ne: 'ADMIN' } };

        if (search && search.trim().length > 0) {
            const searchRegex = { $regex: search.trim(), $options: 'i' };
            filter = {
                ...filter,
                $or: [
                    { username: searchRegex },
                    { 'profile.displayName': searchRegex }
                ]
            };
        }

        const users = await dbConnection
            .collection('users')
            .find(filter)
            .project({
                id: 1,
                username: 1,
                'profile.displayName': 1,
                'profile.profileImageUrl': 1,
                'profile.status': 1,
                'profile.privacyLevel': 1,
                createdAt: 1
            })
            .skip(skip)
            .limit(limit)
            .sort({ username: 1 })
            .toArray();

        res.json({
            success: true,
            users: users.map(user => ({
                userId: user.id,
                username: user.username,
                displayName: user.profile?.displayName || user.username,
                avatar: user.profile?.profileImageUrl || user.profile?.profileImage || user.profileImage || null,
                status: user.profile?.status || 'Available',
                privacyLevel: user.profile?.privacyLevel || 'public',
                createdAt: user.createdAt?.getTime() || Date.now()
            }))
        });
    } catch (err) {
        console.error('Get users error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/search', verifyToken, async (req, res) => {
    const { q } = req.query;
    const currentUserId = req.user.userId;
    const limit = Math.min(parseInt(req.query.limit) || 20, 100);

    try {
        const currentUser = await dbConnection.collection('users').findOne({ id: currentUserId });
        const blockedUserIds = currentUser?.profile?.blockedUsers || [];

        let users;

        if (!q || q.trim().length === 0) {
            users = await dbConnection.collection('users')
                .find({ id: { $ne: currentUserId, $nin: blockedUserIds } })
                .project({
                    password: 0,
                    twoFactorAuth: 0,
                    securityAnswers: 0
                })
                .limit(limit)
                .toArray();
        } else {
            if (q.trim().length < 2) {
                return res.status(400).json({ error: 'Search query must be at least 2 characters' });
            }

            const searchRegex = new RegExp(q.trim(), 'i');
            users = await dbConnection.collection('users')
                .find({
                    $or: [
                        { username: searchRegex },
                        { name: searchRegex },
                        { 'profile.displayName': searchRegex },
                        { 'profile.bio': searchRegex },
                        { 'profile.tags': searchRegex }
                    ],
                    id: { $ne: currentUserId, $nin: blockedUserIds }
                })
                .project({
                    password: 0,
                    twoFactorAuth: 0,
                    securityAnswers: 0
                })
                .limit(limit)
                .toArray();
        }

        users = users.filter(user => {
            const privacyLevel = user.profile?.privacyLevel || 'public';
            return privacyLevel === 'public' || user.id === currentUserId;
        });

        const otherIds = users.map(u => u.id).filter(id => id !== currentUserId);
        const friendRelations = otherIds.length > 0 ? await dbConnection.collection('friends').find({
            $or: [
                { userId1: currentUserId, userId2: { $in: otherIds } },
                { userId2: currentUserId, userId1: { $in: otherIds } }
            ]
        }).toArray() : [];
        const friendIds = new Set();
        friendRelations.forEach(f => {
            friendIds.add(f.userId1 === currentUserId ? f.userId2 : f.userId1);
        });

        const pendingRequests = otherIds.length > 0 ? await dbConnection.collection('friendRequests').find({
            $or: [
                { senderId: currentUserId, recipientId: { $in: otherIds }, status: 'pending' },
                { senderId: { $in: otherIds }, recipientId: currentUserId, status: 'pending' }
            ]
        }).toArray() : [];
        const pendingSent = new Set();
        const pendingReceived = new Set();
        pendingRequests.forEach(r => {
            if (r.senderId === currentUserId) pendingSent.add(r.recipientId);
            if (r.recipientId === currentUserId) pendingReceived.add(r.senderId);
        });

        const blockers = otherIds.length > 0 ? await dbConnection.collection('users').find({
            id: { $in: otherIds },
            'profile.blockedUsers': currentUserId
        }, { projection: { id: 1 } }).toArray() : [];
        const blockedMeIds = new Set(blockers.map(u => u.id));

        const enrichedResults = users.map(user => {
            const isOwnProfile = currentUserId === user.id;
            const friendStatus = isOwnProfile ? 'friends' : (friendIds.has(user.id) ? 'friends' : 'not-friends');
            const privacySettings = user.profile?.privacySettings || {};
            const settings = {
                bio: privacySettings.bio || 'friends',
                onlineStatus: privacySettings.onlineStatus || 'friends'
            };

            return {
                userId: user.id,
                username: user.username,
                displayName: user.profile?.displayName || user.name || user.username,
                bio: user.profile?.bio || '',
                isOnline: canViewPrivateField(settings.onlineStatus, currentUserId, user.id, friendStatus) ? (user.isOnline || false) : false,
                avatar: user.profile?.profileImageUrl || user.avatar || null,
                status: user.profile?.status || 'Available',
                privacyLevel: user.profile?.privacyLevel || 'public',
                tags: Array.isArray(user.profile?.tags) ? user.profile.tags : [],
                role: user.role || 'USER',
                isAdmin: user.role === 'ADMIN',
                isModerator: user.role === 'MODERATOR',
                isFriend: friendIds.has(user.id),
                hasPendingRequest: pendingSent.has(user.id) || pendingReceived.has(user.id),
                pendingRequestId: null,
                hasBlockedMe: blockedMeIds.has(user.id)
            };
        });

        const accessibleResults = enrichedResults.filter(u => !u.hasBlockedMe);

        res.json({
            success: true,
            query: q || '',
            count: accessibleResults.length,
            users: accessibleResults
        });

    } catch (err) {
        console.error('User search error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/check-username', verifyToken, async (req, res) => {
    const { username } = req.query;

    try {
        if (!username || username.trim().length === 0) {
            return res.status(400).json({ error: 'Username is required' });
        }

        const user = await dbConnection.collection('users').findOne({
            username: username.toLowerCase().trim()
        });

        res.json({
            exists: !!user
        });
    } catch (err) {
        console.error('Check username error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (!dbConnection || !dbConnection.collection) {
            console.error('Database not connected');
            return res.status(503).json({ error: 'Database unavailable', success: false });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found', success: false });
        }

        const privacyLevel = user.profile?.privacyLevel || 'public';
        if (privacyLevel === 'private' && userId !== requestUserId) {
            return res.status(403).json({ error: 'User profile is private', success: false });
        }

        try {
            const profileData = await getUserProfileRespectingPrivacy(user, requestUserId);

            const responseData = {
                success: true,
                user: profileData
            };

            res.json(responseData);
        } catch (buildErr) {
            console.error('Error building response:', buildErr);
            res.status(500).json({ error: 'Error building response', success: false });
        }
    } catch (err) {
        console.error('Get user profile error:', err);
        if (!res.headersSent) {
            res.status(500).json({ error: err.message || 'Internal server error', success: false });
        }
    }
});

app.get('/api/users/:userId/status', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;
    const startTime = Date.now();

    try {
        if (!dbConnection) {
            console.warn('[STATUS] Database not available');
            return res.status(503).json({ error: 'Service temporarily unavailable', success: false });
        }

        if (!userId || userId.trim().length === 0) {
            console.warn('[STATUS] Empty userId provided');
            return res.status(400).json({ error: 'Invalid userId', success: false });
        }

        console.log(`[STATUS] Query for user: ${userId}`);

        const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Status query timeout')), 5000)
        );

        let queryPromise;
        try {
            queryPromise = dbConnection.collection('users').findOne(
                { id: userId },
                { projection: { id: 1, isOnline: 1, lastActivityAt: 1, lastLogin: 1, 'profile.status': 1, 'profile.privacySettings': 1 } }
            );
        } catch (queryErr) {
            console.error('[STATUS] Database query error:', queryErr.message);
            return res.status(503).json({ error: 'Database error', success: false });
        }

        const user = await Promise.race([queryPromise, timeoutPromise]);

        if (!user) {
            console.log(`[STATUS] User not found: ${userId}`);
            return res.status(404).json({ error: 'User not found', success: false });
        }

        const isOwnProfile = requestUserId === userId;
        const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, userId);
        const privacySettings = user.profile?.privacySettings || {};
        const settings = {
            lastSeen: privacySettings.lastSeen || 'friends',
            onlineStatus: privacySettings.onlineStatus || 'friends'
        };

        const now = Date.now();
            const lastActivity = user.lastActivityAt ? new Date(user.lastActivityAt).getTime() : null;
            const isOnline = lastActivity && (now - lastActivity) < 5 * 60 * 1000;

        let lastSeen = '';
        if (isOnline) {
            lastSeen = 'Online now';
        } else if (lastActivity) {
            const minutesAgo = Math.floor((now - lastActivity) / (60 * 1000));
            if (minutesAgo < 1) {
                lastSeen = 'Just now';
            } else if (minutesAgo < 60) {
                lastSeen = `${minutesAgo}m ago`;
            } else {
                const hoursAgo = Math.floor(minutesAgo / 60);
                lastSeen = hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
            }
        } else {
            lastSeen = 'Offline';
        }

        const duration = Date.now() - startTime;

        const canViewOnline = canViewPrivateField(settings.onlineStatus, requestUserId, userId, friendStatus);
        const canViewLastSeen = canViewPrivateField(settings.lastSeen, requestUserId, userId, friendStatus);

        const response = {
            success: true,
            userId: user.id,
            isOnline: canViewOnline ? isOnline : false,
            actualOnlineStatus: canViewOnline ? (isOnline ? 'online' : 'offline') : 'offline',
            lastSeen: canViewLastSeen ? lastSeen : 'Hidden',
            lastActivityAt: canViewLastSeen ? lastActivity : null,
            userStatus: user.profile?.status || 'Available'
        };

        console.log(`[STATUS] Returned status for ${userId} (${duration}ms)`);

        try {
            res.json(response);
        } catch (resErr) {
            console.error('[STATUS] Error sending response:', resErr.message);
        }

        if (duration > 1000) {
            console.warn(`[SLOW] Status endpoint took ${duration}ms for user ${userId}`);
        }
    } catch (err) {
        const duration = Date.now() - startTime;
        console.error(`[STATUS] Request failed after ${duration}ms: ${err.message}`);

        if (err.message === 'Status query timeout') {
            try {
                return res.status(503).json({ error: 'Service temporarily unavailable', success: false });
            } catch (e) {
                console.error('[STATUS] Failed to send timeout response:', e.message);
            }
        }

        try {
            res.status(500).json({ error: err.message, success: false });
        } catch (e) {
            console.error('[STATUS] Failed to send error response:', e.message);
        }
    }
});

app.post('/api/users/status/bulk', verifyToken, async (req, res) => {
    const { userIds = [] } = req.body;
    const requestUserId = req.user.userId;
    const startTime = Date.now();

    try {
        if (!Array.isArray(userIds) || userIds.length === 0) {
            return res.status(400).json({ error: 'userIds array required', success: false });
        }

        if (userIds.length > 100) {
            return res.status(400).json({ error: 'Maximum 100 users per request', success: false });
        }

        const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error('Bulk status query timeout')), 5000)
        );

        const queryPromise = dbConnection.collection('users')
            .find(
                { id: { $in: userIds } },
                { projection: { id: 1, isOnline: 1, lastActivityAt: 1, 'profile.status': 1, 'profile.privacySettings': 1 } }
            )
            .toArray();

        const users = await Promise.race([queryPromise, timeoutPromise]);

        const otherIds = userIds.filter(id => id !== requestUserId);
        const friendRelations = otherIds.length > 0 ? await dbConnection.collection('friends').find({
            $or: [
                { userId1: requestUserId, userId2: { $in: otherIds } },
                { userId2: requestUserId, userId1: { $in: otherIds } }
            ]
        }).toArray() : [];
        const friendIds = new Set();
        friendRelations.forEach(f => {
            friendIds.add(f.userId1 === requestUserId ? f.userId2 : f.userId1);
        });

        const now = Date.now();
        const statuses = users.map(user => {
            const isOwnProfile = requestUserId === user.id;
            const friendStatus = isOwnProfile ? 'friends' : (friendIds.has(user.id) ? 'friends' : 'not-friends');
            const privacySettings = user.profile?.privacySettings || {};
            const settings = {
                lastSeen: privacySettings.lastSeen || 'friends',
                onlineStatus: privacySettings.onlineStatus || 'friends'
            };

            const lastActivity = user.lastActivityAt ? new Date(user.lastActivityAt).getTime() : null;
            const isOnline = (lastActivity && (now - lastActivity) < 5 * 60 * 1000) || user.isOnline === true;

            let lastSeen = '';
            if (isOnline) {
                lastSeen = 'Online now';
            } else if (lastActivity) {
                const minutesAgo = Math.floor((now - lastActivity) / (60 * 1000));
                if (minutesAgo < 1) {
                    lastSeen = 'Just now';
                } else if (minutesAgo < 60) {
                    lastSeen = `${minutesAgo}m ago`;
                } else {
                    const hoursAgo = Math.floor(minutesAgo / 60);
                    lastSeen = hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
                }
            } else {
                lastSeen = 'Offline';
            }

            const canViewOnline = canViewPrivateField(settings.onlineStatus, requestUserId, user.id, friendStatus);
            const canViewLastSeen = canViewPrivateField(settings.lastSeen, requestUserId, user.id, friendStatus);

            return {
                userId: user.id,
                isOnline: canViewOnline ? isOnline : false,
                actualOnlineStatus: canViewOnline ? (isOnline ? 'online' : 'offline') : 'offline',
                lastSeen: canViewLastSeen ? lastSeen : 'Hidden',
                lastActivityAt: canViewLastSeen ? lastActivity : null,
                userStatus: user.profile?.status || 'Available'
            };
        });

        const duration = Date.now() - startTime;
        res.json({
            success: true,
            statuses,
            queriedCount: userIds.length,
            foundCount: statuses.length
        });

        if (duration > 1000) {
            console.warn(`[SLOW] Bulk status endpoint took ${duration}ms for ${userIds.length} users`);
        }
    } catch (err) {
        console.error('Get bulk user status error:', err.message);
        if (err.message === 'Bulk status query timeout') {
            return res.status(503).json({ error: 'Service temporarily unavailable', success: false });
        }
        res.status(500).json({ error: err.message, success: false });
    }
});

app.put('/api/users/:userId', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;
    const { displayName, status, bio } = req.body;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s profile' });
        }

        const updateData = {};

        if (displayName !== undefined) {
            updateData['profile.displayName'] = displayName;
        }

        if (status !== undefined) {
            updateData['profile.status'] = status;
        }

        if (bio !== undefined) {
            updateData['profile.bio'] = bio;
        }

        if (Object.keys(updateData).length === 0) {
            return res.status(400).json({ error: 'No fields to update' });
        }

        updateData['updatedAt'] = new Date();

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: userId },
            { $set: updateData },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('user_updated', `User profile updated for ${userId}`, userId);

        res.json({
            success: true,
            user: {
                userId: result.value.id,
                username: result.value.username,
                displayName: result.value.profile?.displayName || result.value.username,
                status: result.value.profile?.status,
                bio: result.value.profile?.bio,
                avatar: result.value.profile?.profileImageUrl || null
            }
        });
    } catch (err) {
        console.error('Update user error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/username', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { username } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s username' });
        }

        if (!username || username.trim().length === 0) {
            return res.status(400).json({ error: 'Username is required' });
        }

        const trimmedUsername = username.toLowerCase().trim();

        const existingUser = await dbConnection.collection('users').findOne({
            username: trimmedUsername,
            id: { $ne: userId }
        });

        if (existingUser) {
            return res.status(409).json({ error: 'Username already taken' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const lastChange = user.profile?.lastUsernameChangeAt || user.createdAt?.getTime() || Date.now();
        const now = Date.now();
        const daysSinceLastChange = Math.floor((now - lastChange) / (1000 * 60 * 60 * 24));

        if (daysSinceLastChange < 4) {
            const remainingDays = 4 - daysSinceLastChange;
            const hoursSinceLastChange = Math.floor((now - lastChange) / (1000 * 60 * 60));
            const remainingHours = Math.max(1, (4 * 24) - hoursSinceLastChange);
            return res.status(429).json({
                error: `Please wait ${remainingDays > 0 ? remainingDays + ' day(s)' : remainingHours + ' hour(s)'} before changing your username again`,
                remainingDays: remainingDays,
                remainingHours: remainingHours
            });
        }

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: userId },
            {
                $set: {
                    username: trimmedUsername,
                    'profile.lastUsernameChangeAt': now,
                    updatedAt: new Date()
                }
            },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('username_updated', `Username changed to ${trimmedUsername}`, userId);

        res.json({
            success: true,
            username: result.value.username,
            message: 'Username updated successfully'
        });
    } catch (err) {
        console.error('Update username error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/displayName', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { displayName } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s display name' });
        }

        if (!displayName || displayName.trim().length === 0) {
            return res.status(400).json({ error: 'Display name is required' });
        }

        if (displayName.trim().length > 50) {
            return res.status(400).json({ error: 'Display name must be 50 characters or less' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const lastChange = user.profile?.lastDisplayNameChangeAt || user.createdAt?.getTime() || Date.now();
        const now = Date.now();
        const hoursSinceLastChange = Math.floor((now - lastChange) / (1000 * 60 * 60));

        if (hoursSinceLastChange < 24) {
            const remainingHours = 24 - hoursSinceLastChange;
            return res.status(429).json({
                error: `Please wait ${remainingHours} hour(s) before changing your display name again`,
                remainingHours: remainingHours
            });
        }

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: userId },
            {
                $set: {
                    'profile.displayName': displayName.trim(),
                    'profile.lastDisplayNameChangeAt': now,
                    'displayName': displayName.trim(),
                    'name': displayName.trim(),
                    updatedAt: new Date()
                }
            },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('display_name_updated', `Display name changed to "${displayName.trim()}"`, userId);

        res.json({
            success: true,
            displayName: result.value.profile?.displayName,
            message: 'Display name updated successfully'
        });
    } catch (err) {
        console.error('Update display name error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/status-message', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { message } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s status message' });
        }

        const statusMessage = (message || '').trim().substring(0, 200);

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: userId },
            {
                $set: {
                    'profile.statusMessage': statusMessage,
                    updatedAt: new Date()
                }
            },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('status_message_updated', `Status message updated for user ${userId}`, userId);

        res.json({
            success: true,
            statusMessage: result.value.profile?.statusMessage || '',
            message: 'Status message updated successfully'
        });
    } catch (err) {
        console.error('Update status message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/preferences/language', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { language } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s preferences' });
        }

        const lang = (language || 'en').trim().substring(0, 10);

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.preferredLanguage': lang,
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('language_preference_updated', `Language preference updated for user ${userId}`, userId);

        res.json({
            success: true,
            language: lang,
            message: 'Language preference updated successfully'
        });
    } catch (err) {
        console.error('Update language preference error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/preferences/theme', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { theme } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s preferences' });
        }

        const validThemes = ['light', 'dark', 'system', 'blue', 'purple', 'green', 'orange', 'pink', 'red', 'teal', 'slate', 'neutral', 'stone', 'amber', 'lime', 'emerald', 'cyan', 'sky', 'indigo', 'violet', 'rose'];
        const themeValue = (theme || 'dark').trim().toLowerCase();
        const finalTheme = validThemes.includes(themeValue) ? themeValue : 'dark';

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.preferredTheme': finalTheme,
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('theme_preference_updated', `Theme preference updated for user ${userId}`, userId);

        res.json({
            success: true,
            theme: finalTheme,
            message: 'Theme preference updated successfully'
        });
    } catch (err) {
        console.error('Update theme preference error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/change-password', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { oldPassword, newPassword, totpCode } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot change another user\'s password' });
        }

        if (!oldPassword || !newPassword) {
            return res.status(400).json({ error: 'Old password and new password are required' });
        }

        if (String(newPassword).length < 6 || String(newPassword).length > 1000) {
            return res.status(400).json({ error: 'New password must be between 6 and 1000 characters' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const passwordMatch = await bcrypt.compare(oldPassword, user.password);
        if (!passwordMatch) {
            await logEvent('password_change_failed', `Failed password change attempt for user ${userId}`, userId);
            return res.status(401).json({ error: 'Current password is incorrect' });
        }

        if (user.twoFactorAuth && user.twoFactorAuth.enabled && user.twoFactorAuth.secret) {
            if (!totpCode) {
                return res.status(400).json({ error: 'Two-factor authentication code is required' });
            }
            const speakeasy = require('speakeasy');
            const totpValid = speakeasy.totp.verify({
                secret: user.twoFactorAuth.secret,
                encoding: 'base32',
                token: totpCode,
                window: 2
            });
            if (!totpValid) {
                return res.status(401).json({ error: 'Invalid two-factor authentication code' });
            }
        }

        const hashedPassword = await bcrypt.hash(newPassword, 10);
        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    password: hashedPassword,
                    'profile.passwordChangedAt': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await dbConnection.collection('tokens').updateMany(
            { userId: userId, token: { $ne: req.headers.authorization?.replace('Bearer ', '') } },
            { $set: { invalidated: true, invalidatedAt: new Date() } }
        );

        await logEvent('password_changed', `Password changed for user ${userId}`, userId);

        res.json({
            success: true,
            message: 'Password changed successfully. Other sessions have been invalidated.'
        });
    } catch (err) {
        console.error('Change password error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/users/:userId/delete-account', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { reason } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot delete another user\'s account' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const deletionRecord = {
            userId: userId,
            username: user.username,
            email: user.email,
            reason: reason || 'No reason provided',
            deletedAt: new Date(),
            previousData: {
                createdAt: user.createdAt,
                friendCount: (user.friends || []).length,
                groupCount: (user.groups || []).length
            }
        };
        await dbConnection.collection('deleted_accounts').insertOne(deletionRecord);

        try {
            const { notifyFriendRemoved } = require('../websocket/broadcast-utils');
            const friends = (user.friends || []).map(f => typeof f === 'object' ? f.userId || f.id : f);
            friends.forEach(friendId => {
                notifyFriendRemoved(global.wsClients, friendId, userId);
            });
        } catch (broadcastErr) {
            console.warn(`[WARN] Failed to broadcast account deletion: ${broadcastErr.message}`);
        }

        const groups = user.groups || [];
        for (const groupId of groups) {
            await dbConnection.collection('groups').updateOne(
                { id: groupId },
                {
                    $pull: {
                        members: { $in: [userId, { userId: userId }] },
                        admins: userId,
                        adminIds: userId
                    }
                }
            );
        }

        const friends = user.friends || [];
        for (const friend of friends) {
            const friendId = typeof friend === 'object' ? friend.userId || friend.id : friend;
            await dbConnection.collection('users').updateOne(
                { id: friendId },
                { $pull: { friends: { userId: userId } } }
            );
        }

        await dbConnection.collection('messages').deleteMany({
            $or: [{ senderId: userId }, { recipientId: userId }]
        });

        await dbConnection.collection('tokens').deleteMany({ userId: userId });

        await dbConnection.collection('users').deleteOne({ id: userId });

        await logEvent('account_deleted', `Account deleted for user ${userId}`, userId);

        res.json({
            success: true,
            message: 'Account deleted successfully'
        });
    } catch (err) {
        console.error('Delete account error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/bio', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { bio } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot update another user\'s bio' });
        }

        const bioText = bio || '';

        const result = await dbConnection.collection('users').findOneAndUpdate(
            { id: userId },
            {
                $set: {
                    'profile.bio': bioText,
                    updatedAt: new Date()
                }
            },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'User not found' });
        }

        await logEvent('bio_updated', `User bio updated`, userId);

        res.json({
            success: true,
            bio: result.value.profile?.bio || '',
            message: 'Bio updated successfully'
        });
    } catch (err) {
        console.error('Update bio error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId/public-profile', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user?.userId;

    try {
        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        if (user.profile?.blockedUsers?.includes(requestUserId)) {
            return res.status(403).json({ error: 'User has blocked you' });
        }

        const isOwnProfile = requestUserId === userId;
        const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, userId);
        const rawPrivacySettings = user.profile?.privacySettings || {};
        const settings = {
            bio: rawPrivacySettings.bio || 'friends',
            tags: rawPrivacySettings.tags || 'friends',
            status: rawPrivacySettings.status || 'friends',
            avatar: rawPrivacySettings.avatar || 'friends',
            badges: rawPrivacySettings.badges || 'friends',
            banner: rawPrivacySettings.banner || 'friends'
        };

        const canView = (field) => canViewPrivateField(settings[field], requestUserId, userId, friendStatus);

        const pubProfile = {
            displayName: user.profile?.displayName || user.displayName || user.name || user.username || '',
            username: user.username || '',
            bio: canView('bio') ? (user.profile?.bio || '') : '',
            pronouns: user.profile?.pronouns || '',
            avatar: canView('avatar') ? (user.profile?.profileImageUrl || user.profile?.profileImage || user.profileImage || null) : null,
            bannerUrl: canView('banner') ? (user.profile?.bannerUrl || null) : null,
            badges: canView('badges') ? (user.profile?.badges || []) : [],
            tags: canView('tags') ? (user.profile?.tags || user.tags || []) : [],
            status: canView('status') ? (user.profile?.status || 'Available') : 'Available',
            privacyLevel: user.profile?.privacyLevel || 'public',
            isCurrentUser: isOwnProfile,
            isBlocked: false,
            role: user.role || 'User',
            userId: user.id || user.userId
        };

        res.json(pubProfile);
    } catch (err) {
        console.error('Get public profile error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/:userId/badges', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const requestUserId = req.user.userId;

    try {
        const user = await dbConnection.collection('users').findOne({ id: userId });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const isOwnProfile = requestUserId === userId;
        const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, userId);
        const privacySettings = user.profile?.privacySettings || {};
        const canViewBadges = canViewPrivateField(privacySettings.badges || 'friends', requestUserId, userId, friendStatus);

        res.json({
            success: true,
            badges: canViewBadges ? (user.profile?.badges || []) : []
        });
    } catch (err) {
        console.error('Get badges error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/users/:userId/privacy', verifyToken, async (req, res) => {
    const { userId } = req.params;
    const { privacyLevel } = req.body;
    const requestUserId = req.user.userId;

    try {
        if (userId !== requestUserId) {
            return res.status(403).json({ error: 'Cannot set another user\'s privacy level' });
        }

        const validLevels = ['public', 'friends', 'private'];
        if (!validLevels.includes(privacyLevel)) {
            return res.status(400).json({
                error: `Invalid privacy level. Valid levels: ${validLevels.join(', ')}`
            });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $set: {
                    'profile.privacyLevel': privacyLevel,
                    'profile.lastUpdated': new Date(),
                    updatedAt: new Date()
                }
            }
        );

        await logEvent('privacy_update', `Privacy level updated for user ${userId}`, userId, {
            privacyLevel: privacyLevel
        });

        res.json({
            success: true,
            privacyLevel: privacyLevel
        });

    } catch (err) {
        console.error('Set privacy level error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/peers', verifyToken, async (req, res) => {
    try {
        const peers = await dbConnection.collection('peers')
            .find({})
            .sort({ createdAt: -1 })
            .toArray();

        res.json({ peers });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/peers', verifyToken, async (req, res) => {
    const { name, type, address, port, apiKey } = req.body;

    if (!name || !type || !address || !port) {
        return res.status(400).json({ error: 'Name, type, address, and port required' });
    }

    try {
        const existing = await dbConnection.collection('peers').findOne({ name });
        if (existing) {
            return res.status(409).json({ error: 'Peer name already exists' });
        }

        const newPeer = {
            id: uuidv4(),
            name,
            type,
            address,
            port,
            apiKey: apiKey ? await bcrypt.hash(apiKey, 10) : null,
            connected: false,
            lastConnected: null,
            latency: null,
            createdAt: new Date(),
            updatedAt: new Date()
        };

        const peerInsertResult = await dbConnection.collection('peers').insertOne(newPeer);
        if (!peerInsertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create peer connection' });
        }

        await logEvent('peer_create', `Peer added: ${name}`, req.user.id || req.user.userId, { peerId: newPeer.id });

        res.status(201).json({
            success: true,
            peer: newPeer
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/admin/peers/:id', verifyToken, async (req, res) => {
    const { name, type, address, port } = req.body;

    try {
        const updates = {
            updatedAt: new Date()
        };

        if (name) updates.name = name;
        if (type) updates.type = type;
        if (address) updates.address = address;
        if (port) updates.port = port;

        const result = await dbConnection.collection('peers').findOneAndUpdate(
            { id: req.params.id },
            { $set: updates },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'Peer not found' });
        }

        await logEvent('peer_update', `Peer updated: ${req.params.id}`, req.user.id || req.user.username);

        res.json({
            success: true,
            peer: result.value
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/peers/:id/disconnect', verifyToken, async (req, res) => {
    try {
        const result = await dbConnection.collection('peers').findOneAndUpdate(
            { id: req.params.id },
            {
                $set: {
                    connected: false,
                    updatedAt: new Date()
                }
            },
            { returnDocument: 'after' }
        );

        if (!result.value) {
            return res.status(404).json({ error: 'Peer not found' });
        }

        await logEvent('peer_disconnect', `Peer disconnected: ${req.params.id}`, req.user.id || req.user.username);

        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/peers/:id/test', verifyToken, async (req, res) => {
    try {
        const peer = await dbConnection.collection('peers').findOne({ id: req.params.id });

        if (!peer) {
            return res.status(404).json({ error: 'Peer not found' });
        }

        const startTime = Date.now();

        const maxRetries = 3;
        const baseTimeout = 2000;
        let lastError;

        for (let attempt = 0; attempt < maxRetries; attempt++) {
            try {
                const timeout = baseTimeout * Math.pow(2, attempt);
                const controller = new AbortController();
                const timeoutId = setTimeout(() => controller.abort(), timeout);

                const response = await fetch(`http://${peer.address}:${peer.port}/health`, {
                    signal: controller.signal
                });

                clearTimeout(timeoutId);

                const latency = Date.now() - startTime;

                if (response.ok) {
                    await dbConnection.collection('peers').updateOne(
                        { id: req.params.id },
                        {
                            $set: {
                                connected: true,
                                lastConnected: new Date(),
                                latency,
                                updatedAt: new Date()
                            }
                        }
                    );

                    await logEvent('peer_test', `Peer test successful: ${peer.name} (${latency}ms)`, req.user.id || req.user.username);

                    return res.json({
                        success: true,
                        latency,
                        message: 'Peer is online'
                    });
                } else {
                    throw new Error('Peer returned non-OK status');
                }
            } catch (err) {
                lastError = err;
                if (attempt < maxRetries - 1) {
                    const waitTime = baseTimeout * Math.pow(2, attempt);
                    console.warn(`[WARN] Peer test attempt ${attempt + 1} failed, retrying in ${waitTime}ms...`);
                    await new Promise(resolve => setTimeout(resolve, waitTime));
                }
            }
        }

        await logEvent('peer_test_fail', `Peer test failed after ${maxRetries} attempts: ${peer.name}`, req.user.id || req.user.username);

        res.status(503).json({
            success: false,
            message: 'Peer is offline or unreachable'
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/peers/:id', verifyToken, async (req, res) => {
    try {
        const result = await dbConnection.collection('peers').deleteOne({ id: req.params.id });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'Peer not found' });
        }

        await logEvent('peer_delete', `Peer deleted: ${req.params.id}`, req.user.id || req.user.username);

        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/logs', verifyToken, async (req, res) => {
    try {
        const filter = req.query.filter || '';
        const count = parseInt(req.query.count) || 50;

        const query = filter ? { type: new RegExp(filter, 'i') } : {};

        const logs = await dbConnection.collection('logs')
            .find(query)
            .sort({ timestamp: -1 })
            .limit(count)
            .toArray();

        res.json({ logs });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/logs', verifyToken, async (req, res) => {
    try {
        const clearResult = await dbConnection.collection('logs').deleteMany({});

        await logEvent('logs_clear', `System logs cleared (${clearResult.deletedCount} entries removed)`, req.user.id || req.user.username);

        res.json({ success: true, deletedCount: clearResult.deletedCount });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/users/username/:username', verifyToken, async (req, res) => {
    const { username } = req.params;
    const requestUserId = req.user.userId;

    try {
        if (!username) {
            return res.status(400).json({ error: 'Username is required' });
        }

        const user = await dbConnection.collection('users').findOne({
            username: username.toLowerCase().trim()
        });

        if (!user) {
            return res.status(404).json({
                error: 'User not found',
                username: username
            });
        }

        const isOwnProfile = requestUserId === user.id;
        const friendStatus = isOwnProfile ? 'friends' : await checkFriendStatus(requestUserId, user.id);
        const privacySettings = user.profile?.privacySettings || {};
        const canViewOnline = canViewPrivateField(privacySettings.onlineStatus || 'friends', requestUserId, user.id, friendStatus);

        res.json({
            success: true,
            user: {
                id: user.id,
                username: user.username,
                displayName: user.name,
                avatar: user.avatar || null,
                isOnline: canViewOnline ? (user.isOnline || false) : false,
                createdAt: user.createdAt.getTime(),
                role: user.role
            }
        });

    } catch (err) {
        console.error('Get user profile error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/friends/request', verifyToken, async (req, res) => {
    const { recipientId } = req.body;
    const senderId = req.user.userId;

    try {
        if (!recipientId) {
            return res.status(400).json({ error: 'recipientId is required' });
        }

        if (senderId === recipientId) {
            return res.status(400).json({ error: 'Cannot send friend request to yourself' });
        }

        const recipient = await dbConnection.collection('users').findOne({ id: recipientId });
        if (!recipient) {
            return res.status(404).json({ error: 'Recipient user not found' });
        }

        const outgoingRequest = await dbConnection.collection('friendRequests').findOne({
            senderId, recipientId
        });

        const incomingRequest = await dbConnection.collection('friendRequests').findOne({
            senderId: recipientId, recipientId: senderId
        });

        if (incomingRequest && !outgoingRequest) {
            console.log(`[FRIEND SYSTEM] Mutual friend request detected: ${senderId} <-> ${recipientId}`);

            const updateResult = await dbConnection.collection('friendRequests').updateOne(
                { id: incomingRequest.id },
                { $set: { status: 'accepted', updatedAt: new Date() } }
            );
            if (updateResult.matchedCount === 0) {
                return res.status(500).json({ error: 'Failed to update friend request status' });
            }

            const sender = await dbConnection.collection('users').findOne({ id: senderId });
            const recipient = await dbConnection.collection('users').findOne({ id: recipientId });

            const friendshipId = uuidv4();
            const friendshipResult = await dbConnection.collection('friends').insertOne({
                id: friendshipId,
                userId1: senderId,
                userId2: recipientId,
                username1: sender.username,
                username2: recipient.username,
                avatar1: sender.avatar || '',
                avatar2: recipient.avatar || '',
                createdAt: new Date(),
                updatedAt: new Date()
            });
            if (!friendshipResult.insertedId) {
                return res.status(500).json({ error: 'Failed to create friendship record' });
            }

            const deleteResult = await dbConnection.collection('friendRequests').deleteOne({ id: incomingRequest.id });
            if (deleteResult.deletedCount === 0) {
                console.warn(`[FRIEND SYSTEM] Failed to delete mutual friend request ${incomingRequest.id}`);
            }

            await logEvent('friend_request_auto_accepted', `Mutual friend request - now friends: ${senderId} <-> ${recipientId}`, senderId);

            if (global.wsClients) {
                const { broadcastToUser } = require('../websocket/broadcast-utils.js');
                broadcastToUser(global.wsClients, senderId, 'friend.request.auto_accepted', {
                    userId: recipientId,
                    username: recipient.username,
                    avatar: recipient.avatar || ''
                });
                broadcastToUser(global.wsClients, recipientId, 'friend.request.auto_accepted', {
                    userId: senderId,
                    username: sender.username,
                    avatar: sender.avatar || ''
                });
            }

            return res.status(200).json({
                success: true,
                message: 'Mutual friend request - now friends!',
                autoAccepted: true,
                friendshipId
            });
        }

        if (outgoingRequest) {
            console.log(`[FRIEND SYSTEM] Removing old request ${outgoingRequest.id} to allow fresh attempt`);
            await dbConnection.collection('friendRequests').deleteOne({ id: outgoingRequest.id });
        }

        const isFriend = await dbConnection.collection('friends').findOne({
            $or: [
                { userId1: senderId, userId2: recipientId },
                { userId1: recipientId, userId2: senderId }
            ]
        });

        if (isFriend) {
            return res.status(409).json({ error: 'Already friends with this user' });
        }

        const sender = await dbConnection.collection('users').findOne({ id: senderId });
        const user = sender;
        if (!sender) {
            return res.status(404).json({ error: 'Sender not found' });
        }

        const requestId = uuidv4();
        const now = new Date();
        const insertResult = await dbConnection.collection('friendRequests').insertOne({
            id: requestId,
            senderId,
            senderName: sender.username,
            senderAvatar: sender.avatar || '',
            recipientId,
            status: 'pending',
            createdAt: now,
            updatedAt: now
        });
        if (!insertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create friend request' });
        }

        await logEvent('friend_request_sent', `Friend request sent from ${senderId} to ${recipientId}`, senderId);

        const eventData = {
            requestId,
            senderId,
            senderUsername: sender.username,
            createdAt: now.toISOString()
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, recipientId, 'friend.request.received', eventData);
        }

try {
    if (pushNotificationManager) {
        await pushNotificationManager.sendFriendRequestNotification(recipientId, {
            userId: senderId,
            username: sender.username,
            avatar: sender.profile?.profileImageUrl || sender.avatar || ''
        }, requestId);
    } else {
                await sendPushNotification(recipientId, {
                    notification: {
                        title: `Friend Request`,
                        body: `${sender.username} sent you a friend request`,
                        sound: 'default'
                    },
                    data: {
                        type: 'friend_request',
                        senderId: senderId,
                        senderName: sender.username,
                        requestId: requestId
                    }
                });
            }
        } catch (fcmErr) {
            console.warn('Friend request FCM failed:', fcmErr.message);
        }

        res.json({
            success: true,
            requestId,
            message: 'Friend request sent'
        });
    } catch (err) {
        console.error('Send friend request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/friends/request/username', verifyToken, async (req, res) => {
    const { username } = req.body;
    const senderId = req.user.userId;

    try {
        if (!username) {
            return res.status(400).json({ error: 'username is required' });
        }

        const normalizedUsername = username.toLowerCase().trim();

        if (normalizedUsername.length < 3) {
            return res.status(400).json({ error: 'Username must be at least 3 characters' });
        }

        const recipient = await dbConnection.collection('users').findOne({
            username: normalizedUsername
        });

        if (!recipient) {
            return res.status(404).json({
                error: 'User not found',
                username: username,
                hint: 'Make sure you have the exact username'
            });
        }

        const recipientId = recipient.id;

        if (senderId === recipientId) {
            return res.status(400).json({ error: 'Cannot send friend request to yourself' });
        }

        const sender = await dbConnection.collection('users').findOne({ id: senderId });
        if (!sender) {
            return res.status(404).json({ error: 'Sender not found' });
        }

        const outgoingRequest = await dbConnection.collection('friendRequests').findOne({
            senderId, recipientId
        });

        const incomingRequest = await dbConnection.collection('friendRequests').findOne({
            senderId: recipientId, recipientId: senderId
        });

        if (incomingRequest && !outgoingRequest) {
            console.log(`[FRIEND SYSTEM] Mutual friend request detected (username): ${senderId} <-> ${recipientId}`);

            const updateReqResult = await dbConnection.collection('friendRequests').updateOne(
                { id: incomingRequest.id },
                { $set: { status: 'accepted', updatedAt: new Date() } }
            );
            if (updateReqResult.matchedCount === 0) {
                return res.status(500).json({ error: 'Failed to update friend request' });
            }

            const friendshipId = uuidv4();
            const friendshipResult = await dbConnection.collection('friends').insertOne({
                id: friendshipId,
                userId1: senderId,
                userId2: recipientId,
                username1: sender.username,
                username2: recipient.username,
                avatar1: sender.avatar || '',
                avatar2: recipient.avatar || '',
                createdAt: new Date(),
                updatedAt: new Date()
            });
            if (!friendshipResult.insertedId) {
                return res.status(500).json({ error: 'Failed to create friendship' });
            }

            const deleteReqResult = await dbConnection.collection('friendRequests').deleteOne({ id: incomingRequest.id });
            if (deleteReqResult.deletedCount === 0) {
                console.warn(`Failed to delete friend request ${incomingRequest.id}`);
            }

            await logEvent('friend_request_auto_accepted_username', `Mutual friend request - now friends: ${senderId} <-> ${recipientId}`, senderId);

            if (global.wsClients) {
                const { broadcastToUser } = require('../websocket/broadcast-utils.js');
                broadcastToUser(global.wsClients, senderId, 'friend.request.auto_accepted', {
                    userId: recipientId,
                    username: recipient.username,
                    avatar: recipient.avatar || ''
                });
                broadcastToUser(global.wsClients, recipientId, 'friend.request.auto_accepted', {
                    userId: senderId,
                    username: sender.username,
                    avatar: sender.avatar || ''
                });
            }

            return res.status(200).json({
                success: true,
                message: 'Mutual friend request - now friends!',
                autoAccepted: true,
                friendshipId
            });
        }

        if (outgoingRequest) {
            console.log(`[FRIEND SYSTEM] Removing old request ${outgoingRequest.id} to allow fresh attempt`);
            await dbConnection.collection('friendRequests').deleteOne({ id: outgoingRequest.id });
        }

        const isFriend = await dbConnection.collection('friends').findOne({
            $or: [
                { userId1: senderId, userId2: recipientId },
                { userId1: recipientId, userId2: senderId }
            ]
        });

        if (isFriend) {
            return res.status(409).json({ error: 'Already friends with this user' });
        }

        const requestId = uuidv4();
        await dbConnection.collection('friendRequests').insertOne({
            id: requestId,
            senderId,
            senderUsername: sender.username,
            senderName: sender.name || sender.username,
            senderAvatar: sender.avatar || '',
            recipientId,
            recipientUsername: recipient.username,
            status: 'pending',
            createdAt: new Date(),
            updatedAt: new Date(),
            expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
        });

        await logEvent('friend_request_sent_username',
            `Friend request sent from @${sender.username} to @${recipient.username}`,
            senderId,
            { recipientId, recipientUsername: recipient.username }
        );

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, recipientId, 'friend.request.received', {
                requestId,
                senderId,
                senderUsername: sender.username,
                createdAt: new Date().toISOString()
            });
        }

        res.status(201).json({
            success: true,
            requestId,
            message: `Friend request sent to @${recipient.username}`,
            recipient: {
                username: recipient.username,
                displayName: recipient.name
            }
        });
    } catch (err) {
        console.error('Send friend request by username error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/friends/requests/:requestId', verifyToken, async (req, res) => {
    const { requestId } = req.params;
    const userId = req.user.userId;

    try {
        if (!requestId) {
            return res.status(400).json({ error: 'requestId is required' });
        }

        const friendRequest = await dbConnection.collection('friendRequests').findOne({
            id: requestId
        });

        if (!friendRequest) {
            return res.status(404).json({ error: 'Friend request not found' });
        }

        if (friendRequest.senderId !== userId) {
            return res.status(403).json({ error: 'Only the sender can cancel this request' });
        }

        const deleteResult = await dbConnection.collection('friendRequests').deleteOne({
            id: requestId
        });

        if (deleteResult.deletedCount === 0) {
            return res.status(400).json({ error: 'Failed to delete request' });
        }

        await logEvent('friend_request_canceled',
            `Friend request canceled: ${requestId}`,
            userId
        );

        res.json({
            success: true,
            message: 'Friend request canceled'
        });
    } catch (err) {
        console.error('Cancel friend request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/friends/requests/outgoing/pending', verifyToken, async (req, res) => {
    const userId = req.user.userId;

    try {
        const pendingRequests = await dbConnection.collection('friendRequests')
            .find({
                senderId: userId,
                status: 'pending'
            })
            .sort({ createdAt: -1 })
            .toArray();

        const requests = pendingRequests.map(req => ({
            requestId: req.id,
            recipientUsername: req.recipientUsername || req.recipientId,
            recipientId: req.recipientId,
            status: req.status,
            sentAt: req.createdAt.getTime(),
            expiresAt: req.expiresAt ? req.expiresAt.getTime() : null
        }));

        res.json({
            success: true,
            count: requests.length,
            requests
        });
    } catch (err) {
        console.error('Get outgoing requests error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/friends/requests/incoming/pending', verifyToken, async (req, res) => {
    const userId = req.user.userId;

    try {
        const pendingRequests = await dbConnection.collection('friendRequests')
            .find({
                recipientId: userId,
                status: 'pending'
            })
            .sort({ createdAt: -1 })
            .toArray();

        const requests = pendingRequests.map(req => ({
            id: req.id,
            requestId: req.id,
            senderUsername: req.senderUsername || req.senderName,
            senderId: req.senderId,
            senderName: req.senderName,
            recipientId: req.recipientId,
            recipientUsername: req.recipientUsername,
            status: req.status,
            sentAt: req.createdAt.getTime(),
            createdAt: req.createdAt.toISOString(),
            updatedAt: req.updatedAt ? req.updatedAt.toISOString() : req.createdAt.toISOString(),
            expiresAt: req.expiresAt ? req.expiresAt.getTime() : null
        }));

        res.json({
            success: true,
            count: requests.length,
            requests
        });
    } catch (err) {
        console.error('Get incoming requests error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/friends/requests/pending', verifyToken, async (req, res) => {
    const userId = req.user.userId;

    try {
        const pendingRequests = await dbConnection.collection('friendRequests')
            .find({
                recipientId: userId,
                status: 'pending'
            })
            .sort({ createdAt: -1 })
            .toArray();

        const requests = pendingRequests.map(req => ({
            id: req.id,
            senderId: req.senderId,
            senderName: req.senderName,
            avatar: req.senderAvatar,
            timestamp: req.createdAt
        }));

        res.json(requests);
    } catch (err) {
        console.error('Get pending requests error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/friends/requests/:senderId/accept', verifyToken, async (req, res) => {
    const { senderId } = req.params;
    const userId = req.user.userId;

    try {
        const friendRequest = await dbConnection.collection('friendRequests').findOne({
            senderId: senderId,
            recipientId: userId,
            status: 'pending'
        });

        if (!friendRequest) {
            return res.status(404).json({ error: 'Friend request not found' });
        }

        const friendshipId = uuidv4();
        const now = new Date();

        const recipientUser = await dbConnection.collection('users').findOne({ id: userId });

        let transactionSuccess = false;
        try {
            await executeWithTransaction(async (session) => {
                const friendshipInsertResult = await dbConnection.collection('friends').insertOne({
                    id: friendshipId,
                    userId1: senderId,
                    userId2: userId,
                    createdAt: now,
                    updatedAt: now
                }, { session });
                if (!friendshipInsertResult.insertedId) {
                    throw new Error('Failed to create friendship record');
                }

                const updateResult = await dbConnection.collection('friendRequests').updateOne(
                    { id: friendRequest.id },
                    { $set: { status: 'accepted', updatedAt: now } },
                    { session }
                );
                if (updateResult.matchedCount === 0) {
                    throw new Error('Failed to update friend request status');
                }
            });
            transactionSuccess = true;
        } catch (transErr) {
            console.error('Transaction error accepting friend request:', transErr.message);
            return res.status(500).json({ error: 'Failed to accept friend request: ' + transErr.message });
        }
        if (!transactionSuccess) {
            return res.status(500).json({ error: 'Transaction failed to complete' });
        }

        await logEvent('friend_request_accepted', `${userId} accepted friend request from ${senderId}`, userId);

        const eventData = {
            userId,
            username: recipientUser?.username || 'Unknown User',
            acceptedAt: now.toISOString()
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, senderId, 'friend.request.accepted', eventData);
        }

        try {
            if (pushNotificationManager) {
                await pushNotificationManager.sendFriendAcceptedNotification(senderId, {
                    userId: userId,
                    username: recipientUser?.username || 'Unknown User'
                });
            } else {
                await sendPushNotification(senderId, {
                    notification: {
                        title: 'Friend Request Accepted',
                        body: `${recipientUser?.username || 'Someone'} accepted your friend request`,
                        sound: 'default'
                    },
                    data: {
                        type: 'friend_accepted',
                        userId: userId,
                        username: recipientUser?.username || 'Unknown',
                        acceptedAt: now.toISOString()
                    }
                });
            }
        } catch (fcmErr) {
            console.warn('Friend accept FCM failed:', fcmErr.message);
        }

        res.json({
            success: true,
            message: 'Friend request accepted',
            friendshipId
        });
    } catch (err) {
        console.error('Accept friend request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/friends/requests/:senderId', verifyToken, async (req, res) => {
    const { senderId } = req.params;
    const userId = req.user.userId;

    try {
        const result = await dbConnection.collection('friendRequests').deleteOne({
            senderId: senderId,
            recipientId: userId,
            status: 'pending'
        });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'Friend request not found' });
        }

        await logEvent('friend_request_rejected', `${userId} rejected friend request from ${senderId}`, userId);

        res.json({
            success: true,
            message: 'Friend request rejected'
        });
    } catch (err) {
        console.error('Delete friend request error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/friends', verifyToken, async (req, res) => {
    const userId = req.user.userId;

    try {
        const user = await dbConnection.collection('users').findOne({ id: userId });
        const blockedUserIds = user?.profile?.blockedUsers || [];

        const friendships = await dbConnection.collection('friends')
            .find({
                $or: [
                    { userId1: userId },
                    { userId2: userId }
                ]
            })
            .toArray();

        const friendIds = friendships.map(f => f.userId1 === userId ? f.userId2 : f.userId1)
            .filter(id => !blockedUserIds.includes(id));

        const friends = await dbConnection.collection('users')
            .find({ id: { $in: friendIds } })
            .toArray();

        const friendList = await Promise.all(friends.map(async friend => {
            const lastActivityAt = friend.lastActivityAt ? new Date(friend.lastActivityAt) : null;
            const now = new Date();
            const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000);
            const rawIsOnline = lastActivityAt && lastActivityAt > fiveMinutesAgo;

            const friendPrivacy = friend.profile?.privacySettings || {};
            const friendStatus = await checkFriendStatus(userId, friend.id);

            const canSeeOnline = canViewPrivateField(
                friendPrivacy.onlineStatus || 'friends', userId, friend.id, friendStatus
            );
            const canSeeLastSeen = canViewPrivateField(
                friendPrivacy.lastSeen || 'friends', userId, friend.id, friendStatus
            );

            let lastSeen = friend.lastActivityAt || friend.lastLogin || 'Never';
            if (!canSeeLastSeen) {
                lastSeen = 'Hidden';
            } else if (!rawIsOnline && lastActivityAt) {
                const minutesAgo = Math.floor((now - lastActivityAt) / (60 * 1000));
                if (minutesAgo < 1) lastSeen = 'Just now';
                else if (minutesAgo < 60) lastSeen = `${minutesAgo}m ago`;
                else {
                    const hoursAgo = Math.floor(minutesAgo / 60);
                    lastSeen = hoursAgo < 24 ? `${hoursAgo}h ago` : 'Offline';
                }
            }

            return {
                userId: friend.id,
                username: friend.username,
                name: friend.name || friend.username,
                email: friend.email || '',
                avatar: friend.profile?.profileImageUrl || friend.avatar || null,
                bio: friend.profile?.bio || '',
                status: friend.profile?.status || 'Available',
                privacyLevel: friend.profile?.privacyLevel || 'public',
                tags: friend.profile?.tags || friend.tags || [],
                role: friend.role || 'USER',
                isAdmin: friend.role === 'ADMIN',
                isModerator: friend.role === 'MODERATOR',
                isOnline: canSeeOnline ? rawIsOnline : false,
                lastSeen: lastSeen
            };
        }));

        res.json({ friends: friendList });
    } catch (err) {
        console.error('Get friends list error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/friends/:friendId', verifyToken, async (req, res) => {
    const { friendId } = req.params;
    const userId = req.user.userId;

    try {
        const friendship = await dbConnection.collection('friends').findOne({
            $or: [
                { userId1: userId, userId2: friendId },
                { userId1: friendId, userId2: userId }
            ]
        });

        if (!friendship) {
            return res.status(404).json({ error: 'Friendship not found' });
        }

        const deleteResult = await dbConnection.collection('friends').deleteOne({ id: friendship.id });
        if (deleteResult.deletedCount === 0) {
            return res.status(500).json({ error: 'Failed to remove friendship - database error' });
        }

        await logEvent('friend_removed', `${userId} removed friend ${friendId}`, userId);

        res.json({
            success: true,
            message: 'Friend removed'
        });
    } catch (err) {
        console.error('Remove friend error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/friends/:userId/block', verifyToken, async (req, res) => {
    const { userId: blockUserId } = req.params;
    const currentUserId = req.user.userId;

    try {
        if (currentUserId === blockUserId) {
            return res.status(400).json({ error: 'Cannot block yourself' });
        }

        const blockId = uuidv4();

        const blockInsertResult = await dbConnection.collection('blockedUsers').insertOne({
            id: blockId,
            userId: currentUserId,
            blockedUserId: blockUserId,
            createdAt: new Date(),
            updatedAt: new Date()
        });
        if (!blockInsertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create block record' });
        }

        const unfriendResult = await dbConnection.collection('friends').deleteOne({
            $or: [
                { userId1: currentUserId, userId2: blockUserId },
                { userId1: blockUserId, userId2: currentUserId }
            ]
        });

        const deleteRequestsResult = await dbConnection.collection('friendRequests').deleteMany({
            $or: [
                { senderId: currentUserId, recipientId: blockUserId },
                { senderId: blockUserId, recipientId: currentUserId }
            ]
        });
        if (deleteRequestsResult.deletedCount === 0) {
            console.warn(`No pending friend requests found to delete between ${currentUserId} and ${blockUserId}`);
        }

        await logEvent('user_blocked', `${currentUserId} blocked user ${blockUserId}`, currentUserId);

        res.json({
            success: true,
            message: 'User blocked',
            blockId
        });
    } catch (err) {
        console.error('Block user error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/health', (req, res) => {
    res.json({
        status: 'online',
        timestamp: new Date(),
        uptime: process.uptime()
    });
});

app.get('/api/health', (req, res) => {
    res.json({
        status: 'UP',
        timestamp: new Date(),
        uptime: process.uptime(),
        service: 'FreeTime Master-Server API',
        version: '1.0.0'
    });
});

app.get('/api/admin/stats', verifyToken, async (req, res) => {
    try {
        const totalUsers = await dbConnection.collection('users').countDocuments();

        const onlineUsers = await dbConnection.collection('users').countDocuments({ isOnline: true });

        const totalPeers = await dbConnection.collection('peers').countDocuments();
        const connectedPeers = await dbConnection.collection('peers').countDocuments({ connected: true });
        const admins = await dbConnection.collection('users').countDocuments({ role: 'ADMIN' });
        const mods = await dbConnection.collection('users').countDocuments({ role: 'MODERATOR' });
        const users = await dbConnection.collection('users').countDocuments({ role: 'USER' });

        let wsStats = {
            active: 0,
            maximum: 35000,
            queued: 0,
            percentageUsed: 0
        };

        try {
            const wsResponse = await fetch('http://localhost:8080/queue-status', { timeout: 2000 });
            if (wsResponse.ok) {
                const wsData = await wsResponse.json();
                const q = wsData.queue || {};
                wsStats = {
                    active: q.currentActive || 0,
                    maximum: q.maxCapacity || 35000,
                    queued: q.size || 0,
                    percentageUsed: q.percentageUsed || 0
                };
            }
        } catch (err) {
            console.warn('[WARN] Could not fetch WebSocket queue stats:', err.message);
        }

        const allUsersList = await dbConnection.collection('users').find({}, { projection: { tags: 1 } }).toArray();
        const vip = allUsersList.filter(u => u.tags && u.tags.includes('vip')).length;

        const openReports = await dbConnection.collection('abuseReports').countDocuments({ status: 'open' });
        const resolvedReports = await dbConnection.collection('abuseReports').countDocuments({ status: 'resolved' });
        const dismissedReports = await dbConnection.collection('abuseReports').countDocuments({ status: 'dismissed' });

        res.json({
            users: {
                total: totalUsers,
                online: onlineUsers,
                offline: totalUsers - onlineUsers,
                admins,
                mods,
                users,
                vip
            },
            reports: {
                open: openReports,
                resolved: resolvedReports,
                dismissed: dismissedReports
            },
            peers: {
                total: totalPeers,
                connected: connectedPeers
            },
            connectionQueue: {
                active: wsStats.active,
                maximum: wsStats.maximum,
                queued: wsStats.queued,
                available: Math.max(0, wsStats.maximum - wsStats.active),
                percentageUsed: wsStats.percentageUsed,
                atCapacity: wsStats.active >= wsStats.maximum,
                message: wsStats.active >= wsStats.maximum ?
                    `Server at capacity. ${wsStats.queued} connections waiting.` :
                    `${Math.max(0, wsStats.maximum - wsStats.active)} connections available`
            },
            uptime: process.uptime(),
            timestamp: new Date()
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/broadcast', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const { message, targetUsers, title } = req.body;
        if (!message || !title) {
            return res.status(400).json({ error: 'Message and title are required' });
        }

        const broadcastId = require('uuid').v4();
        const now = new Date();

        let targets = [];
        if (targetUsers === 'all') {
            const allUsers = await dbConnection.collection('users').find({}, { projection: { id: 1 } }).toArray();
            targets = allUsers.map(u => u.id);
        } else if (Array.isArray(targetUsers)) {
            targets = targetUsers;
        } else {
            return res.status(400).json({ error: 'targetUsers must be "all" or an array of user IDs' });
        }

        const broadcast = {
            id: broadcastId,
            title: title,
            message: message,
            createdBy: req.user.userId,
            sentTo: targets,
            totalRecipients: targets.length,
            sentAt: now,
            delivered: 0,
            failed: 0
        };

        await dbConnection.collection('broadcasts').insertOne(broadcast);

        const { broadcastToUser } = require('../websocket/broadcast-utils.js');
        let delivered = 0;
        let failed = 0;

        for (const userId of targets) {
            try {
                broadcastToUser(global.wsClients, userId, 'admin:broadcast', {
                    id: broadcastId,
                    title: title,
                    message: message,
                    sentAt: now
                });
                delivered++;
            } catch (err) {
                try {
                    const targetUser = await dbConnection.collection('users').findOne({ id: userId });
                    if (targetUser && targetUser.fcmToken) {
                        delivered++;
                    }
                } catch (fcmErr) {
                    failed++;
                }
            }
        }

        await dbConnection.collection('broadcasts').updateOne(
            { id: broadcastId },
            { $set: { delivered, failed } }
        );

        await logEvent('admin_broadcast_sent', `Broadcast sent to ${targets.length} users`, req.user.userId);

        res.json({
            id: broadcastId,
            message: message,
            sentTo: targets.length,
            delivered: delivered,
            failed: failed,
            timestamp: now
        });
    } catch (err) {
        console.error('Broadcast error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/email/send', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const { subject, htmlContent, plainText, targetUsers } = req.body;
        if (!subject || (!htmlContent && !plainText)) {
            return res.status(400).json({ error: 'Subject and content (HTML or plain text) are required' });
        }

        const emailBatchId = require('uuid').v4();
        const now = new Date();

        let targets = [];
        if (targetUsers === 'all') {
            const allUsers = await dbConnection.collection('users').find({}, { projection: { id: 1, email: 1 } }).toArray();
            targets = allUsers.filter(u => u.email);
        } else if (Array.isArray(targetUsers)) {
            const users = await dbConnection.collection('users').find(
                { id: { $in: targetUsers } },
                { projection: { id: 1, email: 1 } }
            ).toArray();
            targets = users.filter(u => u.email);
        } else {
            return res.status(400).json({ error: 'targetUsers must be "all" or an array of user IDs' });
        }

        const emailBatch = {
            id: emailBatchId,
            subject: subject,
            content: htmlContent || plainText,
            createdBy: req.user.userId,
            sentTo: targets.map(u => u.id),
            totalRecipients: targets.length,
            sentAt: now,
            delivered: 0,
            failed: 0,
            bounced: 0
        };

        await dbConnection.collection('emailBatches').insertOne(emailBatch);

        let delivered = 0;
        let failed = 0;

        for (const userObj of targets) {
            try {
                android.util.Log.d('EMAIL_SEND', `Email queued for ${userObj.email}`);
                delivered++;
            } catch (err) {
                failed++;
            }
        }

        await dbConnection.collection('emailBatches').updateOne(
            { id: emailBatchId },
            { $set: { delivered, failed } }
        );

        await logEvent('admin_email_sent', `Email batch sent to ${targets.length} users`, req.user.userId);

        res.json({
            id: emailBatchId,
            subject: subject,
            sentTo: targets.length,
            queued: delivered,
            failed: failed,
            timestamp: now
        });
    } catch (err) {
        console.error('Email send error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels', verifyToken, async (req, res) => {
    const { name, description, isPublic } = req.body;
    const createdBy = req.user.userId;

    try {
        if (!name || name.trim().length === 0) {
            return res.status(400).json({ error: 'Channel name is required' });
        }

        const channelId = uuidv4();
        const now = new Date();

        const channel = {
            id: channelId,
            name: name.trim(),
            description: description || '',
            createdBy: createdBy,
            createdAt: now,
            updatedAt: now,
            isPublic: isPublic !== false,
            members: [createdBy],
            adminIds: [createdBy],
            messageCount: 0,
            subscriberCount: 1,
            type: 'public',
            isAdminOnly: false
        };

        await dbConnection.collection('channels').insertOne(channel);
        await logEvent('channel_created', `Channel ${name} created by ${createdBy}`, createdBy);

        if (wss && wss.clients) {
            const broadcastMessage = JSON.stringify({
                type: 'channel_created',
                event: 'channel_created',
                channelId: channelId,
                channel: channel,
                timestamp: new Date()
            });

            Array.from(wss.clients).forEach(client => {
                if (client.userId && client.readyState === 1) {
                    client.send(broadcastMessage);
                }
            });
            console.log(` Broadcast channel_created event for channel: ${name}`);
        }

        res.status(201).json({
            success: true,
            channelId: channelId,
            channel: channel
        });
    } catch (err) {
        console.error('Create channel error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/channels', verifyToken, async (req, res) => {
    const { query } = req.query;

    try {
        let filter = { isPublic: true };

        if (query) {
            filter = {
                ...filter,
                $or: [
                    { name: { $regex: query, $options: 'i' } },
                    { description: { $regex: query, $options: 'i' } }
                ]
            };
        }

        const channels = await dbConnection
            .collection('channels')
            .find(filter)
            .limit(50)
            .sort({ messageCount: -1 })
            .toArray();

        res.json({
            success: true,
            channels: channels
        });
    } catch (err) {
        console.error('List channels error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/channels/featured', verifyToken, async (req, res) => {
    try {
        const featured = await dbConnection
            .collection('channels')
            .find({ isPublic: true })
            .sort({ messageCount: -1 })
            .limit(10)
            .toArray();

        res.json({
            success: true,
            channels: featured
        });
    } catch (err) {
        console.error('Get featured channels error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels/:channelId/subscribe', verifyToken, async (req, res) => {
    const { channelId } = req.params;
    const userId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({ id: channelId });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        if (channel.members && channel.members.includes(userId)) {
            return res.status(409).json({ error: 'Already subscribed to this channel' });
        }

        await dbConnection.collection('channels').updateOne(
            { id: channelId },
            {
                $addToSet: { members: userId },
                $inc: { subscriberCount: 1 },
                $set: { updatedAt: new Date() }
            }
        );

        await logEvent('channel_subscribed', `User subscribed to ${channel.name}`, userId);

        res.json({
            success: true,
            message: 'Subscribed to channel'
        });
    } catch (err) {
        console.error('Subscribe to channel error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels/:channelId/unsubscribe', verifyToken, async (req, res) => {
    const { channelId } = req.params;
    const userId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({ id: channelId });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        if (!channel.members || !channel.members.includes(userId)) {
            return res.status(404).json({ error: 'Not subscribed to this channel' });
        }

        await dbConnection.collection('channels').updateOne(
            { id: channelId },
            {
                $pull: { members: userId },
                $inc: { subscriberCount: -1 },
                $set: { updatedAt: new Date() }
            }
        );

        await logEvent('channel_unsubscribed', `User unsubscribed from ${channel.name}`, userId);

        res.json({
            success: true,
            message: 'Unsubscribed from channel'
        });
    } catch (err) {
        console.error('Unsubscribe from channel error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/channels/:channelId/permissions/message', verifyToken, async (req, res) => {
    const { channelId } = req.params;
    const userId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        const isMember = channel.members && channel.members.includes(userId);
        if (!isMember) {
            return res.status(403).json({
                canSendMessage: false,
                reason: 'Not a member of this channel',
                role: 'none'
            });
        }

        const isAdmin = channel.adminIds && channel.adminIds.includes(userId);
        const isAdminOnly = channel.type === 'admin_only' || channel.isAdminOnly;

        if (isAdminOnly && !isAdmin) {
            return res.json({
                canSendMessage: false,
                reason: 'This is an admin-only channel',
                role: 'member',
                permissionType: 'admin_only'
            });
        }

        res.json({
            canSendMessage: true,
            reason: null,
            role: isAdmin ? 'admin' : 'member',
            permissionType: channel.type || 'public'
        });

    } catch (err) {
        console.error('Check message permission error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels/:channelId/messages', verifyToken, async (req, res) => {
    const { channelId } = req.params;
    const { content } = req.body;
    const senderId = req.user.userId;

    try {
        if (!content || content.trim().length === 0) {
            return res.status(400).json({ error: 'Message content is required' });
        }

        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        const isMember = channel.members && channel.members.includes(senderId);
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this channel' });
        }

        const isAdmin = channel.adminIds && channel.adminIds.includes(senderId);
        if ((channel.type === 'admin_only' || channel.isAdminOnly) && !isAdmin) {
            return res.status(403).json({ error: 'Only admins can send messages in this channel' });
        }

        const messageId = uuidv4();
        const now = new Date();

        await dbConnection.collection('channelMessages').insertOne({
            id: messageId,
            channelId: channelId,
            senderId: senderId,
            senderUsername: req.user.username,
            content: content.trim(),
            createdAt: now,
            updatedAt: now,
            isDeleted: false,
            editedAt: null,
            likes: 0
        });

        const messageEventData = {
            messageId,
            channelId,
            senderId,
            senderUsername: req.user.username,
            content: content.trim(),
            createdAt: now
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            const members = channel.members || [];
            members.forEach(memberId => {
                if (memberId !== senderId) {
                    broadcastToUser(global.wsClients, memberId, 'channel.message.received', messageEventData);
                }
            });
        }

        try {
            const members = channel.members || [];
            members.forEach(async (memberId) => {
                if (memberId !== senderId) {
                    const messagePreview = messageBody.length > 100 ? messageBody.substring(0, 97) + '...' : messageBody;
                    await sendPushNotification(memberId, {
                        notification: {
                            title: `${user?.username || 'Someone'} in #${channel.name}`,
                            body: messagePreview
                        },
                        data: {
                            type: 'channel_message',
                            channelName: channel.name,
                            channel_name: channel.name,
                            channelId: channelId,
                            channel_id: channelId,
                            senderName: user?.username || 'Unknown',
                            sender_name: user?.username || 'Unknown',
                            messagePreview: messagePreview,
                            message_preview: messagePreview,
                            messageContent: messageBody,
                            messageId: messageId
                        }
                    });
                }
            });
        } catch (fcmErr) {
            console.warn('Channel message FCM failed:', fcmErr.message);
        }

        await logEvent('channel_message_sent', `Message sent to channel ${channel.name}`, senderId);

        res.status(201).json({
            success: true,
            messageId: messageId,
            timestamp: now.getTime()
        });

    } catch (err) {
        console.error('Send channel message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/channels/:channelId/members', verifyToken, async (req, res) => {
    const { channelId } = req.params;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        const memberIds = channel.members || [];
        const members = await dbConnection.collection('users')
            .find({
                id: { $in: memberIds }
            })
            .project({
                password: 0,
                twoFactorAuth: 0,
                email: 0
            })
            .toArray();

        const enrichedMembers = members.map(member => ({
            userId: member.id,
            username: member.username,
            displayName: member.name,
            role: (channel.adminIds && channel.adminIds.includes(member.id)) ? 'admin' : 'member',
            isAdmin: channel.adminIds && channel.adminIds.includes(member.id),
            isOnline: member.isOnline || false
        }));

        res.json({
            success: true,
            channelId: channelId,
            memberCount: enrichedMembers.length,
            members: enrichedMembers.sort((a, b) => {
                if (a.isAdmin !== b.isAdmin) return b.isAdmin ? 1 : -1;
                return a.username.localeCompare(b.username);
            })
        });

    } catch (err) {
        console.error('Get channel members error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels/:channelId/members/:memberId/promote', verifyToken, async (req, res) => {
    const { channelId, memberId } = req.params;
    const requesterId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        if (!channel.adminIds || !channel.adminIds.includes(requesterId)) {
            return res.status(403).json({ error: 'Only channel admins can promote members' });
        }

        if (!channel.members || !channel.members.includes(memberId)) {
            return res.status(404).json({ error: 'User is not a channel member' });
        }

        if (channel.adminIds && channel.adminIds.includes(memberId)) {
            return res.status(409).json({ error: 'User is already an admin' });
        }

        const updateResult = await dbConnection.collection('channels').updateOne(
            { id: channelId },
            {
                $push: { adminIds: memberId }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to promote user' });
        }

        await logEvent('channel_member_promoted',
            `Member promoted to admin in channel ${channel.name}`,
            requesterId,
            { channelId: channelId, memberId: memberId }
        );

        res.json({
            success: true,
            message: `User promoted to channel admin`,
            memberId: memberId,
            role: 'admin'
        });

    } catch (err) {
        console.error('Promote member error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/channels/:channelId/members/:memberId/demote', verifyToken, async (req, res) => {
    const { channelId, memberId } = req.params;
    const requesterId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        if (!channel.adminIds || !channel.adminIds.includes(requesterId)) {
            return res.status(403).json({ error: 'Only channel admins can demote members' });
        }

        if (!channel.adminIds || !channel.adminIds.includes(memberId)) {
            return res.status(404).json({ error: 'User is not an admin' });
        }

        if (channel.adminIds.length === 1 && channel.adminIds[0] === memberId) {
            return res.status(400).json({ error: 'Cannot demote the last channel admin' });
        }

        const updateResult = await dbConnection.collection('channels').updateOne(
            { id: channelId },
            {
                $pull: { adminIds: memberId }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to demote user' });
        }

        await logEvent('channel_member_demoted',
            `Member demoted from admin in channel ${channel.name}`,
            requesterId,
            { channelId: channelId, memberId: memberId }
        );

        res.json({
            success: true,
            message: `User demoted to regular member`,
            memberId: memberId,
            role: 'member'
        });

    } catch (err) {
        console.error('Demote member error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/channels/:channelId/messages/:messageId', verifyToken, async (req, res) => {
    const { channelId, messageId } = req.params;
    const requesterId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        if (!channel.adminIds || !channel.adminIds.includes(requesterId)) {
            return res.status(403).json({ error: 'Only channel admins can delete messages' });
        }

        const message = await dbConnection.collection('channelMessages').findOne({
            id: messageId,
            channelId: channelId
        });

        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        const updateResult = await dbConnection.collection('channelMessages').updateOne(
            { id: messageId },
            {
                $set: {
                    isDeleted: true,
                    deletedAt: new Date(),
                    deletedBy: requesterId
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(400).json({ error: 'Failed to delete message' });
        }

        await logEvent('channel_message_deleted',
            `Message deleted from channel ${channel.name}`,
            requesterId,
            { channelId: channelId, messageId: messageId }
        );

        res.json({
            success: true,
            message: 'Message deleted',
            messageId: messageId
        });

    } catch (err) {
        console.error('Delete channel message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/channels/:channelId', verifyToken, async (req, res) => {
    const { channelId } = req.params;
    const userId = req.user.userId;

    try {
        const channel = await dbConnection.collection('channels').findOne({
            id: channelId
        });

        if (!channel) {
            return res.status(404).json({ error: 'Channel not found' });
        }

        const isMember = channel.members && channel.members.includes(userId);
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this channel' });
        }

        const isAdmin = channel.adminIds && channel.adminIds.includes(userId);

        res.json({
            success: true,
            channelId: channel.id,
            name: channel.name,
            description: channel.description || '',
            type: channel.type || 'public',
            isAdminOnly: channel.type === 'admin_only' || channel.isAdminOnly,
            isAdmin: isAdmin,
            adminCount: channel.adminIds ? channel.adminIds.length : 0,
            memberCount: channel.members ? channel.members.length : 0,
            createdAt: channel.createdAt.getTime()
        });

    } catch (err) {
        console.error('Get channel info error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/chat/:recipientId/typing', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const typingData = {
            userId,
            username: req.user.username,
            recipientId,
            isTyping: true,
            timestamp: Date.now()
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, recipientId, 'user.typing', typingData);
        }

        res.json({ success: true, message: 'Typing indicator sent' });
    } catch (err) {
        console.error('Typing indicator error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/chat/:recipientId/typing-stop', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const typingData = {
            userId,
            username: req.user.username,
            recipientId,
            isTyping: false,
            timestamp: Date.now()
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            broadcastToUser(global.wsClients, recipientId, 'user.typing', typingData);
        }

        res.json({ success: true, message: 'Typing stopped' });
    } catch (err) {
        console.error('Stop typing error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/messages/:messageId/read', verifyToken, async (req, res) => {
    const { messageId } = req.params;
    const userId = req.user.userId;

    try {
        const now = new Date();

        await dbConnection.collection('messages').updateOne(
            { id: messageId },
            {
                $set: {
                    readAt: now,
                    readBy: userId,
                    isRead: true
                }
            }
        );

        const message = await dbConnection.collection('messages').findOne({ id: messageId });

        if (message && message.senderId !== userId) {
            if (global.wsClients) {
                const { broadcastToUser } = require('../websocket/broadcast-utils.js');
                const readReceiptData = {
                    messageId,
                    readBy: userId,
                    username: req.user.username,
                    readAt: now,
                    conversationId: message.conversationId
                };
                broadcastToUser(global.wsClients, message.senderId, 'message.read', readReceiptData);
            }
        }

        res.json({
            success: true,
            message: 'Message marked as read',
            readAt: now
        });
    } catch (err) {
        console.error('Mark message read error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/chat/:recipientId/read-all', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;

    try {
        const now = new Date();

        const result = await dbConnection.collection('messages').updateMany(
            {
                senderId: recipientId,
                recipientId: userId,
                isRead: false
            },
            {
                $set: {
                    readAt: now,
                    readBy: userId,
                    isRead: true
                }
            }
        );

        if (global.wsClients && result.modifiedCount > 0) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            const readReceiptData = {
                conversationId: recipientId,
                readBy: userId,
                username: req.user.username,
                readAt: now,
                allMessages: true,
                messageCount: result.modifiedCount
            };
            broadcastToUser(global.wsClients, recipientId, 'conversation.allRead', readReceiptData);
        }

        res.json({
            success: true,
            message: `${result.modifiedCount} messages marked as read`,
            markedCount: result.modifiedCount
        });
    } catch (err) {
        console.error('Mark all read error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/messages/:messageId/read-status', verifyToken, async (req, res) => {
    const { messageId } = req.params;
    const userId = req.user.userId;

    try {
        const message = await dbConnection.collection('messages').findOne({ id: messageId });

        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (message.senderId !== userId) {
            return res.status(403).json({ error: 'Not authorized' });
        }

        res.json({
            success: true,
            messageId,
            isRead: message.isRead || false,
            readBy: message.readBy || null,
            readAt: message.readAt || null,
            deliveredAt: message.deliveredAt || null
        });
    } catch (err) {
        console.error('Get read status error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/messages/:messageId/reactions', verifyToken, async (req, res) => {
    try {
        const { messageId } = req.params;
        const { emoji } = req.body;
        const userId = req.user.userId;

        if (!emoji || typeof emoji !== 'string') {
            return res.status(400).json({ error: 'Emoji is required' });
        }

        const message = await dbConnection.collection('messages').findOne({ id: messageId });
        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (!message.reactions) {
            message.reactions = [];
        }

        const reactionExists = message.reactions.some(r => r.emoji === emoji && r.userId === userId);
        if (reactionExists) {
            return res.status(400).json({ error: 'User already reacted with this emoji' });
        }

        message.reactions.push({
            emoji,
            userId,
            timestamp: new Date()
        });

        await dbConnection.collection('messages').updateOne(
            { id: messageId },
            { $set: { reactions: message.reactions, updatedAt: new Date() } }
        );

        console.log(`[OK] Reaction added: ${emoji} by ${userId} to message ${messageId}`);

        try {
            const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
            const recipients = [message.senderId, message.recipientId].filter(id => id !== userId);

            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                io.emit('message:reaction:added', {
                    messageId,
                    userId,
                    username: req.user.username,
                    emoji,
                    reactions: message.reactions
                });
            }

            broadcastToUsers(global.wsClients, recipients, 'message:reaction:added', {
                messageId,
                userId,
                username: req.user.username,
                emoji,
                reactions: message.reactions
            });
        } catch (e) {
            console.warn('Broadcast reaction failed:', e.message);
        }

        res.json({
            success: true,
            messageId,
            emoji,
            reactions: message.reactions
        });
    } catch (err) {
        console.error('Add reaction error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/messages/:messageId', verifyToken, async (req, res) => {
    try {
        const { messageId } = req.params;
        const userId = req.user.userId;

        const message = await dbConnection.collection('messages').findOne({ id: messageId });
        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (message.senderId !== userId) {
            return res.status(403).json({ error: 'You can only delete your own messages' });
        }

        if (message.mediaId) {
            try {
                await dbConnection.collection('media').deleteOne({ mediaId: message.mediaId });
            } catch (e) {
                console.warn('Failed to delete media for message:', e.message);
            }
        }

        await dbConnection.collection('messages').deleteOne({ id: messageId });

        console.log(`[OK] Message deleted: ${messageId} by ${userId}`);

        try {
            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                io.to(`user:${message.senderId}`).emit('message:deleted', { messageId });
                io.to(`user:${message.recipientId}`).emit('message:deleted', { messageId });
            }
        } catch (e) {
            console.warn('Broadcast message deletion failed:', e.message);
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Delete message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId/messages/:messageId', verifyToken, async (req, res) => {
    try {
        const { groupId, messageId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const members = (group.members || []).map(m => typeof m === 'string' ? m : (m.userId || m.id || ''));
        if (!members.includes(userId) && group.createdBy !== userId && req.user.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const message = await dbConnection.collection('groupMessages').findOne({
            $or: [{ messageId: messageId }, { id: messageId }]
        });
        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (message.senderId !== userId) {
            return res.status(403).json({ error: 'You can only delete your own messages' });
        }

        await dbConnection.collection('groupMessages').deleteOne({
            $or: [{ messageId: messageId }, { id: messageId }]
        });

        console.log(`[OK] Group message deleted: ${messageId} by ${userId} in group ${groupId}`);

        try {
            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                io.to(`group:${groupId}`).emit('groupMessage:deleted', {
                    groupId: groupId,
                    messageId: messageId,
                    deletedBy: userId,
                    timestamp: Date.now()
                });
            }
        } catch (e) {
            console.warn('Broadcast group message deletion failed:', e.message);
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Delete group message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/messages/:messageId/reactions/:emoji', verifyToken, async (req, res) => {
    try {
        const { messageId, emoji } = req.params;
        const userId = req.user.userId;

        const message = await dbConnection.collection('messages').findOne({ id: messageId });
        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (!message.reactions || message.reactions.length === 0) {
            return res.status(400).json({ error: 'No reactions on this message' });
        }

        const initialLength = message.reactions.length;
        message.reactions = message.reactions.filter(r => !(r.emoji === decodeURIComponent(emoji) && r.userId === userId));

        if (message.reactions.length === initialLength) {
            return res.status(400).json({ error: 'User has not reacted with this emoji' });
        }

        await dbConnection.collection('messages').updateOne(
            { id: messageId },
            { $set: { reactions: message.reactions, updatedAt: new Date() } }
        );

        console.log(`[OK] Reaction removed: ${emoji} by ${userId} from message ${messageId}`);

        try {
            const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
            const recipients = [message.senderId, message.recipientId].filter(id => id !== userId);

            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                io.emit('message:reaction:remove', {
                    messageId,
                    userId,
                    username: req.user.username,
                    emoji: decodeURIComponent(emoji),
                    reactions: message.reactions
                });
            }

            broadcastToUsers(global.wsClients, recipients, 'message:reaction:remove', {
                messageId,
                userId,
                username: req.user.username,
                emoji: decodeURIComponent(emoji),
                reactions: message.reactions
            });
        } catch (e) {
            console.warn('Broadcast reaction removal failed:', e.message);
        }

        res.json({
            success: true,
            messageId,
            emoji: decodeURIComponent(emoji),
            reactions: message.reactions
        }); } catch (err) {
        console.error('Remove reaction error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/messages/:messageId/reactions', verifyToken, async (req, res) => {
    try {
        const { groupId, messageId } = req.params;
        const { emoji } = req.body;
        const userId = req.user.userId;

        console.log(`[REACTION] POST /api/groups/${groupId}/messages/${messageId}/reactions - emoji='${emoji}', body keys=${Object.keys(req.body).join(',')}`);

        if (!emoji || typeof emoji !== 'string') {
            console.error(`[ REACTION ERROR] Missing or invalid emoji. Received: '${emoji}' (type: ${typeof emoji})`);
            return res.status(400).json({ error: 'Emoji is required and must be a string' });
        }

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && group.members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === userId);
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const actualGroupId = group.id || groupId;
        const msgIdWithPrefix = messageId.startsWith('msg_') ? messageId : `msg_${messageId}`;
        const msgIdWithoutPrefix = messageId.startsWith('msg_') ? messageId.substring(4) : messageId;

        let message = await dbConnection.collection('groupMessages').findOne({
            $or: [
                { id: messageId, groupId: actualGroupId },
                { messageId: messageId, groupId: actualGroupId },
                { id: msgIdWithPrefix, groupId: actualGroupId },
                { messageId: msgIdWithPrefix, groupId: actualGroupId },
                { id: msgIdWithoutPrefix, groupId: actualGroupId },
                { messageId: msgIdWithoutPrefix, groupId: actualGroupId }
            ]
        });
        if (!message) {
            console.error(` Message not found. Searched for: ${messageId}, ${msgIdWithPrefix}, ${msgIdWithoutPrefix} in group ${actualGroupId}`);
            return res.status(404).json({ error: 'Message not found' });
        }

        if (!message.reactions) {
            message.reactions = [];
        }

        const reactionExists = message.reactions.some(r => r.emoji === emoji && r.userId === userId);
        if (reactionExists) {
            return res.status(400).json({ error: 'User already reacted with this emoji' });
        }

        message.reactions.push({
            emoji,
            userId,
            timestamp: new Date()
        });

        await dbConnection.collection('groupMessages').updateOne(
            { _id: message._id },
            { $set: { reactions: message.reactions, updatedAt: new Date() } }
        );

        console.log(`[OK] GROUP Reaction added: ${emoji} by ${userId} to message ${messageId} in group ${groupId}`);

        try {
            const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
            const members = (group.members || []).filter(m => m).map(m => typeof m === 'object' ? (m.userId || m.id) : m);

            const reactionPayload = {
                groupId: group.id || groupId,
                messageId,
                userId,
                emoji,
                reactions: message.reactions
            };

            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                console.log(`[ GROUP REACTION] Broadcasting via Socket.IO to all connections...`);
                io.emit('group:message:reaction:added', reactionPayload);

                io.to(`group:${group.id || groupId}`).emit('group:message:reaction:added', reactionPayload);
            }

            console.log(`[ GROUP REACTION] Broadcasting to ${members.length} members via WebSocket...`);
            broadcastToUsers(global.wsClients, members, 'group:message:reaction:added', reactionPayload);
            console.log(`[ GROUP REACTION] Broadcast complete`);
        } catch (e) {
            console.warn('Broadcast GROUP reaction failed:', e.message);
        }

        res.json({
            success: true,
            groupId: group.id || groupId,
            messageId,
            emoji,
            reactions: message.reactions
        });
    } catch (err) {
        console.error('Add GROUP reaction error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId/messages/:messageId/reactions/:emoji', verifyToken, async (req, res) => {
    try {
        const { groupId, messageId, emoji } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && group.members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === userId);
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const actualGroupId = group.id || groupId;
        const msgIdWithPrefix = messageId.startsWith('msg_') ? messageId : `msg_${messageId}`;
        const msgIdWithoutPrefix = messageId.startsWith('msg_') ? messageId.substring(4) : messageId;

        let message = await dbConnection.collection('groupMessages').findOne({
            $or: [
                { id: messageId, groupId: actualGroupId },
                { messageId: messageId, groupId: actualGroupId },
                { id: msgIdWithPrefix, groupId: actualGroupId },
                { messageId: msgIdWithPrefix, groupId: actualGroupId },
                { id: msgIdWithoutPrefix, groupId: actualGroupId },
                { messageId: msgIdWithoutPrefix, groupId: actualGroupId }
            ]
        });
        if (!message) {
            return res.status(404).json({ error: 'Message not found' });
        }

        if (!message.reactions || message.reactions.length === 0) {
            return res.status(400).json({ error: 'No reactions on this message' });
        }

        const initialLength = message.reactions.length;
        message.reactions = message.reactions.filter(r => !(r.emoji === decodeURIComponent(emoji) && r.userId === userId));

        if (message.reactions.length === initialLength) {
            return res.status(400).json({ error: 'User has not reacted with this emoji' });
        }

        await dbConnection.collection('groupMessages').updateOne(
            { _id: message._id },
            { $set: { reactions: message.reactions, updatedAt: new Date() } }
        );

        console.log(`[OK] GROUP Reaction removed: ${emoji} by ${userId} from message ${messageId} in group ${groupId}`);

        try {
            const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
            const members = (group.members || []).filter(m => m).map(m => typeof m === 'object' ? (m.userId || m.id) : m);

            const reactionPayload = {
                groupId: group.id || groupId,
                messageId,
                userId,
                emoji: decodeURIComponent(emoji),
                reactions: message.reactions
            };

            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                console.log(`[ GROUP REACTION REMOVAL] Broadcasting via Socket.IO...`);
                io.emit('group:message:reaction:removed', reactionPayload);

                io.to(`group:${group.id || groupId}`).emit('group:message:reaction:removed', reactionPayload);
            }

            console.log(`[ GROUP REACTION REMOVAL] Broadcasting to ${members.length} members...`);
            broadcastToUsers(global.wsClients, members, 'group:message:reaction:removed', reactionPayload);
            console.log(`[ GROUP REACTION REMOVAL] Broadcast complete`);
        } catch (e) {
            console.warn('Broadcast GROUP reaction removal failed:', e.message);
        }

        res.json({
            success: true,
            groupId: group.id || groupId,
            messageId,
            emoji: decodeURIComponent(emoji),
            reactions: message.reactions
        }); } catch (err) {
        console.error('Remove GROUP reaction error:', err);
        res.status(500).json({ error: err.message });
    }
});
app.post('/api/messages/voice', upload.single('voiceFile'), verifyToken, async (req, res) => {
    const { recipientId, duration } = req.body;
    const senderId = req.user.userId;

    try {
        if (!req.file) {
            return res.status(400).json({ error: 'No voice file uploaded' });
        }

        if (!recipientId) {
            return res.status(400).json({ error: 'recipientId required' });
        }

        const fileBuffer = fs.readFileSync(req.file.path);
        const base64Audio = fileBuffer.toString('base64');

        const messageId = uuidv4();
        const voiceMessage = {
            id: messageId,
            senderId: senderId,
            recipientId: recipientId,
            type: 'voice',
            content: { base64Audio, duration: parseInt(duration) || 0 },
            audioBase64: base64Audio,
            duration: parseInt(duration) || 0,
            status: 'sent',
            createdAt: new Date(),
            timestamp: Date.now()
        };

        await dbConnection.collection('messages').insertOne(voiceMessage);

        if (fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }

        res.status(201).json({
            success: true,
            messageId: messageId,
            message: 'Voice message sent'
        });
    } catch (err) {
        console.error('Upload voice message error:', err);
        if (req.file && fs.existsSync(req.file.path)) {
            fs.unlinkSync(req.file.path);
        }
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/messages/:messageId/voice', verifyToken, async (req, res) => {
    const { messageId } = req.params;
    const userId = req.user.userId;

    try {
        const message = await dbConnection.collection('messages').findOne({ id: messageId });

        if (!message) {
            return res.status(404).json({ error: 'Voice message not found' });
        }

        if (message.senderId !== userId && message.recipientId !== userId) {
            return res.status(403).json({ error: 'Unauthorized to access this message' });
        }

        if (message.type !== 'voice') {
            return res.status(400).json({ error: 'Message is not a voice message' });
        }

        const base64Audio = message.audioBase64 || message.content?.base64Audio;
        if (!base64Audio) {
            return res.status(404).json({ error: 'Audio data not found' });
        }

        const audioBuffer = Buffer.from(base64Audio, 'base64');

        res.setHeader('Content-Type', 'audio/mp4');
        res.setHeader('Content-Length', audioBuffer.length);
        res.send(audioBuffer);
    } catch (err) {
        console.error('Get voice message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/chat/:recipientId/all-messages', verifyToken, async (req, res) => {
    const { recipientId } = req.params;
    const userId = req.user.userId;
    const limit = parseInt(req.query.limit) || 100;
    const skip = parseInt(req.query.skip) || 0;

    try {
        const messages = await dbConnection.collection('messages')
            .find({
                $or: [
                    { senderId: userId, recipientId: recipientId },
                    { senderId: recipientId, recipientId: userId }
                ]
            })
            .sort({ createdAt: -1 })
            .skip(skip)
            .limit(limit)
            .toArray();

        res.json({
            success: true,
            total: messages.length,
            messages: messages.reverse().map(msg => ({
                messageId: msg.id || msg._id,
                senderId: msg.senderId,
                recipientId: msg.recipientId,
                content: msg.content,
                status: msg.status || 'sent',
                isRead: msg.isRead || false,
                deliveredAt: msg.deliveredAt,
                readAt: msg.readAt,
                timestamp: msg.createdAt ? msg.createdAt.getTime() : msg.timestamp
            }))
        });
    } catch (err) {
        console.error('Get all messages error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.use((err, req, res, next) => {
    if (err.name === 'MulterError') {
        console.error('[MULTER ERROR]', {
            code: err.code,
            message: err.message,
            field: err.field,
            url: req.path
        });

        if (err.code === 'FILE_TOO_LARGE') {
            return res.status(413).json({
                error: 'File is too large',
                maxSize: '100MB'
            });
        } else if (err.code === 'LIMIT_FILE_COUNT') {
            return res.status(413).json({
                error: 'Too many files',
                maxFiles: 5
            });
        } else {
            return res.status(400).json({
                error: `Upload error: ${err.message}`,
                code: err.code
            });
        }
    }

    console.error('[ERROR]', {
        message: err.message,
        name: err.name,
        code: err.code,
        status: err.status,
        url: req.path
    });

    res.status(err.status || 500).json({
        error: err.message || 'Internal server error',
        errorType: err.name
    });
});

const GIPHY_API_KEY = process.env.GIPHY_API_KEY || '';

const FALLBACK_GIFS = [
    { id: '3o7TKsQ8nTx5LqIqIM', title: 'Celebration Dance', keywords: ['celebration', 'dance', 'party', 'happy', 'excited', 'congrats'], w: 480, h: 270 },
    { id: '26BRuo6sLetdttPAQ', title: 'Dancing', keywords: ['dance', 'party', 'fun', 'groove', 'move'], w: 480, h: 344 },
    { id: 'l0HlBO7eyXzSZq1uU', title: 'Dancing Cat', keywords: ['cat', 'dance', 'funny', 'animal', 'party'], w: 480, h: 270 },
    { id: 'xT0xeuOy2Fcl9vGsI', title: 'Dancing Kid', keywords: ['dance', 'happy', 'excited', 'fun', 'celebrate'], w: 480, h: 270 },
    { id: 'l0MYt5jPR6QX5pnqM', title: 'Thumbs Up', keywords: ['thumbs', 'up', 'like', 'agree', 'ok', 'good', 'approve', 'yes'], w: 480, h: 344 },
    { id: 'l0He3iDtVQE2XvL84', title: 'LOL', keywords: ['lol', 'laugh', 'funny', 'haha', 'hilarious', 'lmao'], w: 480, h: 270 },
    { id: 'l0MYPL0vnLbYPKuTu', title: 'Eye Roll', keywords: ['eyeroll', 'roll', 'eyes', 'sarcastic', 'whatever', 'annoyed'], w: 480, h: 344 },
    { id: '3o6Zt481J7q6nV4cQM', title: 'Excited', keywords: ['excited', 'happy', 'yes', 'awesome', 'amazing'], w: 480, h: 344 },
    { id: 'xT0BKBjvCFV6oA8Hsc', title: 'Happy Dance', keywords: ['happy', 'dance', 'celebrate', 'joy', 'excited'], w: 480, h: 270 },
    { id: '3o7bu3XilJ3gAUBJM8', title: 'Applause', keywords: ['applause', 'clap', 'bravo', 'well done', 'good job', 'great', 'proud'], w: 480, h: 270 },
    { id: '26tPk0I79JwfoVqXq', title: 'Happy Tears', keywords: ['happy', 'cry', 'tears', 'joy', 'touching', 'emotional', 'sweet'], w: 480, h: 270 },
    { id: 'l0Hl7GhwS1WBN4rAk', title: 'Good Morning', keywords: ['good morning', 'morning', 'wake', 'hello', 'hi'], w: 480, h: 344 },
    { id: 'l0MYt5jPR6QX5pnqM', title: 'Good Job', keywords: ['good job', 'well done', 'great', 'awesome', 'proud', 'congrats'], w: 480, h: 344 },
    { id: 'xT0xeuOy2Fcl9vGsI', title: 'Let\'s Go', keywords: ['lets go', 'lets', 'go', 'party', 'excited', 'ready'], w: 480, h: 270 },
    { id: '3o6Zt481J7q6nV4cQM', title: 'OMG', keywords: ['omg', 'shock', 'surprised', 'wow', 'no way', 'unbelievable'], w: 480, h: 344 },
    { id: 'l0He3iDtVQE2XvL84', title: 'Haha Funny', keywords: ['haha', 'funny', 'lol', 'laugh', 'hilarious', 'joke'], w: 480, h: 270 },
    { id: '26BRuo6sLetdttPAQ', title: 'Party Time', keywords: ['party', 'dance', 'celebrate', 'fun', 'club'], w: 480, h: 344 },
    { id: 'l0HlBO7eyXzSZq1uU', title: 'Cat Dancing', keywords: ['cat', 'kitty', 'dance', 'cute', 'animal', 'pet'], w: 480, h: 270 },
    { id: 'xT0BKBjvCFV6oA8Hsc', title: 'So Happy', keywords: ['happy', 'joy', 'excited', 'glad', 'cheerful'], w: 480, h: 270 },
    { id: 'l0MYPL0vnLbYPKuTu', title: 'Not Impressed', keywords: ['not', 'impressed', 'unimpressed', 'meh', 'whatever', 'bored'], w: 480, h: 344 },
    { id: '3o7bu3XilJ3gAUBJM8', title: 'Standing Ovation', keywords: ['standing', 'ovation', 'applause', 'clap', 'bravo', 'encore'], w: 480, h: 270 },
    { id: '3o7TKsQ8nTx5LqIqIM', title: 'Birthday', keywords: ['birthday', 'celebrate', 'party', 'cake', 'happy birthday'], w: 480, h: 270 },
    { id: '26tPk0I79JwfoVqXq', title: 'Touched', keywords: ['touched', 'emotional', 'heartfelt', 'sweet', 'cry'], w: 480, h: 270 },
    { id: 'l0Hl7GhwS1WBN4rAk', title: 'Hello There', keywords: ['hello', 'hi', 'hey', 'greet', 'wave'], w: 480, h: 344 },
    { id: 'xT0xeuOy2Fcl9vGsI', title: 'Weekend', keywords: ['weekend', 'friday', 'party', 'celebrate', 'fun'], w: 480, h: 270 },
    { id: 'l0He3iDtVQE2XvL84', title: 'Rolling on Floor', keywords: ['rofl', 'laugh', 'funny', 'floor', 'hilarious'], w: 480, h: 270 },
    { id: '3o6Zt481J7q6nV4cQM', title: 'Shocked', keywords: ['shock', 'surprise', 'wow', 'omg', 'gasp', 'stunned'], w: 480, h: 344 },
    { id: 'l0MYt5jPR6QX5pnqM', title: 'Perfect', keywords: ['perfect', 'excellent', 'great', 'awesome', 'nice', 'good'], w: 480, h: 344 },
    { id: '26BRuo6sLetdttPAQ', title: 'Grooving', keywords: ['groove', 'dance', 'music', 'beat', 'rhythm'], w: 480, h: 344 },
    { id: 'l0HlBO7eyXzSZq1uU', title: 'Cute Cat', keywords: ['cat', 'cute', 'adorable', 'kitty', 'meow', 'animal'], w: 480, h: 270 },
    { id: 'xT0BKBjvCFV6oA8Hsc', title: 'Yes!', keywords: ['yes', 'yeah', 'awesome', 'success', 'win', 'victory'], w: 480, h: 270 },
    { id: 'l0MYPL0vnLbYPKuTu', title: 'Bored', keywords: ['bored', 'tired', 'sleepy', 'whatever', 'meh'], w: 480, h: 344 },
    { id: '3o7bu3XilJ3gAUBJM8', title: 'Great Job Everyone', keywords: ['great', 'job', 'team', 'everyone', 'well done', 'congrats'], w: 480, h: 270 },
    { id: '3o7TKsQ8nTx5LqIqIM', title: 'Happy Birthday', keywords: ['happy', 'birthday', 'celebrate', 'cake', 'party'], w: 480, h: 270 },
    { id: '26tPk0I79JwfoVqXq', title: 'Feeling Loved', keywords: ['love', 'loved', 'grateful', 'thankful', 'heart', 'blessed'], w: 480, h: 270 },
    { id: 'l0Hl7GhwS1WBN4rAk', title: 'Waving Hello', keywords: ['wave', 'hello', 'hi', 'greeting', 'hey'], w: 480, h: 344 },
    { id: 'xT0xeuOy2Fcl9vGsI', title: 'So Excited', keywords: ['excited', 'eager', 'can\'t wait', 'ready', 'party'], w: 480, h: 270 },
    { id: 'l0He3iDtVQE2XvL84', title: 'That\'s Funny', keywords: ['funny', 'joke', 'laugh', 'hilarious', 'humor', 'comedy'], w: 480, h: 270 },
    { id: '3o6Zt481J7q6nV4cQM', title: 'Mind Blown', keywords: ['mind', 'blown', 'amazing', 'unbelievable', 'wow', 'shocked'], w: 480, h: 344 },
    { id: 'l0MYt5jPR6QX5pnqM', title: 'Nice', keywords: ['nice', 'cool', 'awesome', 'sweet', 'good', 'great'], w: 480, h: 344 },
    { id: '26BRuo6sLetdttPAQ', title: 'Friday Dance', keywords: ['friday', 'dance', 'weekend', 'party', 'tgif'], w: 480, h: 344 },
    { id: 'l0HlBO7eyXzSZq1uU', title: 'Dancing Dog', keywords: ['dog', 'dance', 'cute', 'pet', 'animal', 'funny'], w: 480, h: 270 },
    { id: 'xT0BKBjvCFV6oA8Hsc', title: 'Good News', keywords: ['good', 'news', 'great', 'awesome', 'wonderful', 'excited'], w: 480, h: 270 },
    { id: 'l0MYPL0vnLbYPKuTu', title: 'Sigh', keywords: ['sigh', 'frustrated', 'annoyed', 'tired', 'whatever'], w: 480, h: 344 },
    { id: '3o7bu3XilJ3gAUBJM8', title: 'Congratulations', keywords: ['congratulations', 'congrats', 'well done', 'proud', 'great', 'achievement'], w: 480, h: 270 },
    { id: '3o7TKsQ8nTx5LqIqIM', title: 'Woohoo', keywords: ['woohoo', 'yay', 'celebrate', 'party', 'happy', 'excited'], w: 480, h: 270 },
    { id: '26tPk0I79JwfoVqXq', title: 'Touched My Heart', keywords: ['heart', 'love', 'touched', 'emotional', 'sweet', 'caring'], w: 480, h: 270 },
    { id: 'l0Hl7GhwS1WBN4rAk', title: 'Good Night', keywords: ['good night', 'night', 'sleep', 'bed', '晚安'], w: 480, h: 344 }
];

function giphyCdnUrl(id) {
    return `https://i.giphy.com/${id}.gif`;
}

function buildGiphyResponse(gifs) {
    return gifs.map(g => ({
        id: g.id,
        title: g.title,
        images: {
            original: { url: giphyCdnUrl(g.id), width: String(g.w), height: String(g.h) },
            fixed_height: { url: giphyCdnUrl(g.id), width: String(Math.round(g.w * 200 / g.h)), height: '200' },
            fixed_height_small: { url: giphyCdnUrl(g.id), width: String(Math.round(g.w * 100 / g.h)), height: '100' }
        }
    }));
}

function proxyGiphyRequest(giphyPath, searchTerm, limit, res) {
    if (!GIPHY_API_KEY) {
        console.log('[GIPHY] No API key configured, using fallback GIF library');
        return serveFallback(searchTerm, limit, res);
    }
    const https = require('https');
    const giphyUrl = `https://api.giphy.com${giphyPath}&api_key=${GIPHY_API_KEY}`;

    const req = https.get(giphyUrl, { timeout: 10000 }, (upstream) => {
        let body = '';
        upstream.on('data', (chunk) => { body += chunk; });
        upstream.on('end', () => {
            try {
                const data = JSON.parse(body);
                if (data.meta && (data.meta.status === 401 || data.meta.status === 403)) {
                    console.warn('[GIPHY] API key rejected (' + data.meta.status + ' ' + data.meta.msg + '), using fallback');
                    return serveFallback(searchTerm, limit, res);
                }
                res.json(data);
            } catch (e) {
                console.error('[GIPHY] Failed to parse upstream response:', e.message);
                serveFallback(searchTerm, limit, res);
            }
        });
    });

    req.on('error', (err) => {
        console.error('[GIPHY] Upstream request failed:', err.message);
        serveFallback(searchTerm, limit, res);
    });

    req.on('timeout', () => {
        req.destroy();
        console.warn('[GIPHY] Upstream request timed out, using fallback');
        serveFallback(searchTerm, limit, res);
    });
}

function serveFallback(searchTerm, limit, res) {
    let results = FALLBACK_GIFS;
    if (searchTerm) {
        const q = searchTerm.toLowerCase();
        results = FALLBACK_GIFS.filter(g =>
            g.title.toLowerCase().includes(q) ||
            g.keywords.some(k => k.toLowerCase().includes(q))
        );
    }
    const deduplicated = [];
    const seen = new Set();
    for (const g of results) {
        if (!seen.has(g.id)) {
            seen.add(g.id);
            deduplicated.push(g);
        }
    }
    const shuffled = deduplicated.sort(() => Math.random() - 0.5);
    const sliced = shuffled.slice(0, limit || 30);
    res.json({ data: buildGiphyResponse(sliced), pagination: { total_count: sliced.length, count: sliced.length, offset: 0 } });
}

app.get('/api/gifs/trending', (req, res) => {
    const limit = Math.min(parseInt(req.query.limit) || 30, 50);
    proxyGiphyRequest(`/v1/gifs/trending?limit=${limit}&rating=g`, null, limit, res);
});

app.get('/api/gifs/search', (req, res) => {
    const q = req.query.q;
    if (!q || !q.trim()) {
        return res.status(400).json({ error: 'Query parameter q is required' });
    }
    const limit = Math.min(parseInt(req.query.limit) || 30, 50);
    const encoded = encodeURIComponent(q.trim());
    proxyGiphyRequest(`/v1/gifs/search?q=${encoded}&limit=${limit}&rating=g`, q.trim(), limit, res);
});

const setupEnhancedFeatures = require('./enhanced-features-api.js');

let server = null;

async function start() {
    return new Promise((resolve, reject) => {
        try {
            const useDirectHttps = sslOptions && API_PORT === 443;
            const serverImpl = useDirectHttps ? https.createServer(sslOptions, app) : require('http').createServer(app);
            server = serverImpl;

            server.keepAliveTimeout = 65000;
            server.headersTimeout = 66000;
            server.requestTimeout = REQUEST_TIMEOUT_MS || 30000;

            server.on('connection', (socket) => {
                socket.setKeepAlive(true, 30000);
            });

            server.on('error', (err) => {
                if (err.code === 'EACCES') {
                    console.error(`[ERROR] Permission denied to bind to port ${API_PORT}. Ports below 1024 require sudo.`);
                } else if (err.code === 'EADDRINUSE') {
                    console.error(`[ERROR] Port ${API_PORT} is already in use.`);
                } else {
                    console.error(`[ERROR] Server startup error: ${err.message}`);
                }
                reject(err);
            });

            server.listen(API_PORT, '0.0.0.0', async () => {
                const protocol = useDirectHttps ? 'HTTPS' : 'HTTP';
                const secureNote = useDirectHttps ? 'Secure direct HTTPS mode' : 'HTTP mode behind reverse proxy';
                console.log(`\n[OK] FreeTime Master-Server Admin API Started\n[INFO] Protocol: ${protocol}\n[INFO] Mode: ${secureNote}\n[INFO] Listening on port ${API_PORT}\n[INFO] Domain: example.com\n[INFO] API Port: ${API_PORT} (${protocol})\n[INFO] WebSocket Port: 8080\n[INFO] Peer Port: 9080\n[INFO] Admin Port: 3001\n[INFO] Data store: ${MONGODB_URI.split('/').pop()}\n[INFO] Request timeout: ${REQUEST_TIMEOUT_MS}ms\n `);

                try {
                    initializeSocketIO(server, JWT_SECRET, allowedOrigins);
                    console.log(`[OK] Socket.IO real-time server initialized on same port (${API_PORT})`);
                } catch (ioErr) {
                    console.error('[ERROR] Socket.IO initialization failed:', ioErr.message);
                    console.warn('[WARN] Server running without real-time Socket.IO support');
                }

                console.log('[INFO] Connecting to database in background...');
                try {
                    await connectDB();
                    console.log('[OK] Database connected and ready');
                    logEvent('system', 'Database connection established');
                    try {
                        setupEnhancedFeatures(app, verifyToken, dbConnection, global.wsClients, logEvent, upload);
                        console.log('[OK] Enhanced features API routes initialized');
                        logEvent('system', 'Enhanced features API integrated and ready');
                    } catch (enhancedErr) {
                        console.error('[ERROR] Failed to setup enhanced features:', enhancedErr.message);
                        logEvent('error', `Enhanced features setup failed: ${enhancedErr.message}`);
                    }
                    try {
                        await ensureAnnouncementUserExists();
                    } catch (annErr) {
                        console.error('[ERROR] Announcement user setup failed:', annErr.message);
                    }
                } catch (dbErr) {
                    console.error('[ERROR] Database connection failed:', dbErr.message);
                    console.error('[ERROR] Server is running but database operations will fail');
                    logEvent('error', `Database connection failed: ${dbErr.message}`);
                }

                resolve(server);
            });
        } catch (err) {
            console.error('[ERROR] Failed to start server:', err);
            reject(err);
        }
    });
}

start().catch(err => {
    console.error('[CRITICAL] Server startup error:', err);
    process.exit(1);
});

app.post('/api/admin/broadcast-message', verifyToken, async (req, res) => {
    try {
        const { recipientType, messageContent, messageType, specificUser } = req.body;
        const adminUsername = req.user.username || 'Admin';

        if (!messageContent || messageContent.trim().length === 0) {
            return res.status(400).json({ error: 'Message content required' });
        }

        let recipientCount = 0;

        if (global.io) {
            const message = {
                id: require('uuid').v4(),
                type: 'admin.broadcast',
                messageType: messageType,
                messageContent: messageContent,
                sentBy: adminUsername,
                timestamp: new Date(),
                recipientType: recipientType
            };

            if (recipientType === 'all' || recipientType === 'online') {
                global.io.emit('admin.broadcast', message);
                recipientCount = global.io.engine.clientsCount || 0;
            } else if (recipientType === 'specific' && specificUser) {
                global.io.to(`user:${specificUser}`).emit('admin.broadcast', message);
                recipientCount = 1;
            }

            console.log(`[Admin Broadcast] ${adminUsername} sent "${messageType}" message to ${recipientCount} users`);
        }

        try {
            const broadcastResult = await dbConnection.collection('admin_broadcasts').insertOne({
                id: require('uuid').v4(),
                messageType: messageType,
                messageContent: messageContent,
                sentBy: adminUsername,
                recipientType: recipientType,
                specificUser: specificUser || null,
                recipientCount: recipientCount,
                timestamp: new Date(),
                status: 'sent'
            });
            if (!broadcastResult.insertedId) {
                console.error('Failed to insert broadcast record into database');
            }
        } catch (dbErr) {
            console.warn('Failed to store broadcast in DB:', dbErr.message);
        }

        res.json({
            success: true,
            message: 'Broadcast sent successfully',
            recipientCount: recipientCount
        });
    } catch (err) {
        console.error('Broadcast message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/chat-history', verifyToken, async (req, res) => {
    try {
        const messages = await dbConnection.collection('admin_broadcasts')
            .find({})
            .sort({ timestamp: -1 })
            .limit(50)
            .toArray();

        res.json(messages);
    } catch (err) {
        console.error('Get chat history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/connected-users-count', verifyToken, async (req, res) => {
    try {
        const count = global.io ? (global.io.engine.clientsCount || 0) : 0;
        res.json({ count: count });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

const ANNOUNCEMENT_USER_ID = 'announcement_system';
const ANNOUNCEMENT_USERNAME = 'announcement';
const ANNOUNCEMENT_DISPLAY_NAME = ' Announcement';

async function ensureAnnouncementUserExists() {
    try {
        const existing = await dbConnection.collection('users').findOne({ id: ANNOUNCEMENT_USER_ID });
        if (!existing) {
            const now = new Date();
            await dbConnection.collection('users').insertOne({
                id: ANNOUNCEMENT_USER_ID,
                username: ANNOUNCEMENT_USERNAME,
                displayName: ANNOUNCEMENT_DISPLAY_NAME,
                name: ANNOUNCEMENT_DISPLAY_NAME,
                email: 'announcement@freetimeapp.com',
                password: require('crypto').randomBytes(32).toString('hex'),
                role: 'ADMIN',
                tags: ['SYSTEM', 'ANNOUNCEMENT'],
                profile: {
                    displayName: ANNOUNCEMENT_DISPLAY_NAME,
                    profileImageUrl: null,
                    bio: 'Official announcements from the FreeTime team',
                    status: 'active'
                },
                createdAt: now,
                updatedAt: now,
                isOnline: false,
                status: 'active'
            });
            console.log('[OK] Announcement system user created');
        }
    } catch (err) {
        console.error('[ERROR] Failed to create announcement user:', err.message);
    }
}

const emailTransporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST || 'smtp.gmail.com',
    port: process.env.SMTP_PORT || 587,
    secure: process.env.SMTP_SECURE === 'true' || false,
    auth: {
        user: process.env.SMTP_USER || '',
        pass: process.env.SMTP_PASSWORD || ''
    }
});

app.get('/api/admin/user-lookup', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const q = req.query.q;
        if (!q) {
            return res.status(400).json({ error: 'Query parameter q is required' });
        }

        const user = await dbConnection.collection('users').findOne({
            $or: [
                { id: q },
                { username: new RegExp('^' + q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i') }
            ]
        });

        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        res.json({
            id: user.id,
            username: user.username,
            displayName: user.displayName || user.profile?.displayName || user.username,
            email: user.profile?.email || user.email
        });
    } catch (err) {
        console.error('User lookup error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/send-mass-email', verifyToken, async (req, res) => {
    try {
        const {
            subject,
            htmlContent,
            recipientFilter,
            filterValue,
            fromEmail,
            recipientId
        } = req.body;

        if (!subject || !htmlContent) {
            return res.status(400).json({ error: 'Subject and content required' });
        }

        let query = {};

        if (recipientFilter === 'all') {
            query = { 'profile.email': { $exists: true, $ne: '' } };
        } else if (recipientFilter === 'by-tags' && filterValue) {
            const tags = Array.isArray(filterValue)
                ? filterValue
                : filterValue.split(',').map(t => t.trim()).filter(Boolean);

            if (tags.length > 0) {
                query = {
                    'profile.tags': { $in: tags },
                    'profile.email': { $exists: true, $ne: '' }
                };
            } else {
                query = { 'profile.email': { $exists: true, $ne: '' } };
            }
        } else if (recipientFilter === 'specific' && recipientId) {
            const targetUser = await dbConnection.collection('users').findOne({
                $or: [
                    { id: recipientId },
                    { username: recipientId }
                ]
            });
            if (targetUser && (targetUser.profile?.email || targetUser.email)) {
                query = { id: targetUser.id };
            } else {
                return res.status(400).json({ error: 'Specific user not found or has no email' });
            }
        }

        const users = await dbConnection.collection('users').find(query).toArray();
        const recipientsList = users.map(u => ({
            id: u.id,
            email: u.profile?.email || u.email,
            username: u.username
        })).filter(u => u.email && u.email.includes('@'));

        if (recipientsList.length === 0) {
            return res.status(400).json({ error: 'No matching recipients found' });
        }

        const senderAddress = fromEmail || process.env.SMTP_FROM || 'noreply@freetimeapp.com';

        setImmediate(async () => {
            let successCount = 0;
            let failureCount = 0;
            let lastError = '';

            for (const recipient of recipientsList) {
                try {
                    await emailTransporter.sendMail({
                        from: senderAddress,
                        to: recipient.email,
                        subject: subject,
                        html: htmlContent,
                        text: htmlContent.replace(/<[^>]*>/g, '')
                    });
                    successCount++;
                } catch (emailErr) {
                    console.error(`Failed to send email to ${recipient.email}:`, emailErr.message);
                    if (!lastError) lastError = emailErr.message;
                    failureCount++;
                }
            }

            try {
                const campaignResult = await dbConnection.collection('email_campaigns').insertOne({
                    id: require('uuid').v4(),
                    subject: subject,
                    recipientFilter: recipientFilter,
                    filterValue: filterValue,
                    recipientId: recipientFilter === 'specific' ? recipientId : undefined,
                    fromEmail: fromEmail || undefined,
                    totalRecipients: recipientsList.length,
                    successCount: successCount,
                    failureCount: failureCount,
                    lastError: lastError || undefined,
                    sentBy: req.user.username,
                    sentAt: new Date(),
                    status: 'completed'
                });
                if (!campaignResult.insertedId) {
                    console.error('Failed to insert email campaign record into database');
                }
            } catch (dbErr) {
                console.warn('Failed to store campaign:', dbErr.message);
            }

            console.log(`[Email Campaign] Sent to ${successCount}/${recipientsList.length} users`);
        });

        res.json({
            success: true,
            message: 'Email campaign started',
            totalRecipients: recipientsList.length,
            status: 'sending'
        });
    } catch (err) {
        console.error('Mass email error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/announcement/send', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const { recipientId, message, subject } = req.body;
        if (!recipientId || !message) {
            return res.status(400).json({ error: 'recipientId and message are required' });
        }

        const targetUser = await dbConnection.collection('users').findOne({ id: recipientId });
        if (!targetUser) {
            return res.status(404).json({ error: 'Target user not found' });
        }

        await ensureAnnouncementUserExists();

        const messageId = `msg_${require('uuid').v4()}`;
        const now = new Date();
        const announcementMessage = {
            id: messageId,
            _id: messageId,
            senderId: ANNOUNCEMENT_USER_ID,
            recipientId: recipientId,
            content: message,
            type: 'text',
            status: 'delivered',
            createdAt: now,
            updatedAt: now,
            isRead: false,
            isAdminAnnouncement: true,
            subject: subject || ''
        };

        await dbConnection.collection('messages').insertOne(announcementMessage);

        const io = global.io || global.socketIoServer || global.socketIoWebSocketServer;
        if (io) {
            const enrichedMessage = {
                ...announcementMessage,
                _id: messageId,
                id: messageId,
                senderId: ANNOUNCEMENT_USER_ID,
                senderUsername: ANNOUNCEMENT_USERNAME,
                senderDisplayName: ANNOUNCEMENT_DISPLAY_NAME,
                senderRole: 'ADMIN',
                senderTags: ['SYSTEM', 'ANNOUNCEMENT'],
                isAdminAnnouncement: true,
                timestamp: now.getTime()
            };
            io.to(`user:${recipientId}`).emit('newMessage', enrichedMessage);
        }

        try {
            await sendPushNotification(recipientId, {
                notification: {
                    title: subject || ' System Announcement',
                    body: message.length > 100 ? message.substring(0, 97) + '...' : message
                },
                data: {
                    type: 'announcement',
                    senderId: ANNOUNCEMENT_USER_ID,
                    senderName: ANNOUNCEMENT_DISPLAY_NAME,
                    messageContent: message,
                    subject: subject || '',
                    timestamp: now.getTime().toString()
                }
            });
        } catch (pushErr) {
            console.warn('Failed to send push notification:', pushErr.message);
        }

        await logEvent('admin_announcement_sent',
            `Admin ${req.user.username} sent announcement to ${targetUser.username || recipientId}`,
            req.user.userId);

        try {
            await dbConnection.collection('announcement_history').insertOne({
                id: `ah_${require('uuid').v4()}`,
                type: 'single',
                subject: subject || '',
                message: message,
                recipientId: recipientId,
                recipientName: targetUser.username || targetUser.displayName || recipientId,
                sentBy: req.user.username,
                sentAt: now,
                totalRecipients: 1
            });
        } catch (histErr) {
            console.warn('Failed to save announcement history:', histErr.message);
        }

        res.json({
            success: true,
            message: 'Announcement sent successfully',
            messageId: messageId,
            recipientId: recipientId,
            recipientName: targetUser.username || targetUser.displayName || recipientId
        });
    } catch (err) {
        console.error('Announcement send error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/announcement/broadcast', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        const { message, subject } = req.body;
        if (!message) {
            return res.status(400).json({ error: 'message is required' });
        }

        await ensureAnnouncementUserExists();

        const allUsers = await dbConnection.collection('users')
            .find({}, { projection: { id: 1 } })
            .toArray();

        const now = new Date();
        const io = global.io || global.socketIoServer || global.socketIoWebSocketServer;

        const announcementMessages = allUsers.map(u => {
            const msgId = `msg_${require('uuid').v4()}`;
            return {
                _id: msgId,
                id: msgId,
                senderId: ANNOUNCEMENT_USER_ID,
                recipientId: u.id,
                content: message,
                type: 'text',
                status: 'delivered',
                createdAt: now,
                updatedAt: now,
                isRead: false,
                isAdminAnnouncement: true,
                subject: subject || ''
            };
        });

        if (announcementMessages.length > 0) {
            await dbConnection.collection('messages').insertMany(announcementMessages);
        }

        if (io) {
            for (const msg of announcementMessages) {
                io.to(`user:${msg.recipientId}`).emit('newMessage', {
                    _id: msg.id,
                    id: msg.id,
                    senderId: ANNOUNCEMENT_USER_ID,
                    senderUsername: ANNOUNCEMENT_USERNAME,
                    senderDisplayName: ANNOUNCEMENT_DISPLAY_NAME,
                    senderRole: 'ADMIN',
                    senderTags: ['SYSTEM', 'ANNOUNCEMENT'],
                    content: message,
                    type: 'text',
                    isAdminAnnouncement: true,
                    subject: subject || '',
                    recipientId: msg.recipientId,
                    timestamp: now.getTime()
                });
            }
        }

        setImmediate(async () => {
            for (const u of allUsers) {
                try {
                    await sendPushNotification(u.id, {
                        notification: {
                            title: subject || ' System Announcement',
                            body: message.length > 100 ? message.substring(0, 97) + '...' : message
                        },
                        data: {
                            type: 'announcement',
                            senderId: ANNOUNCEMENT_USER_ID,
                            senderName: ANNOUNCEMENT_DISPLAY_NAME,
                            messageContent: message,
                            subject: subject || '',
                            timestamp: now.getTime().toString()
                        }
                    });
                } catch (pushErr) {
                    console.warn(`Failed to send push to ${u.id}:`, pushErr.message);
                }
            }
        });

        await logEvent('admin_announcement_broadcast',
            `Admin ${req.user.username} broadcast announcement to ${allUsers.length} users`,
            req.user.userId);

        try {
            await dbConnection.collection('announcement_history').insertOne({
                id: `ah_${require('uuid').v4()}`,
                type: 'broadcast',
                subject: subject || '',
                message: message,
                sentBy: req.user.username,
                sentAt: now,
                totalRecipients: allUsers.length
            });
        } catch (histErr) {
            console.warn('Failed to save announcement history:', histErr.message);
        }

        res.json({
            success: true,
            message: `Announcement broadcast to ${allUsers.length} users`,
            totalRecipients: allUsers.length
        });
    } catch (err) {
        console.error('Announcement broadcast error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/announcement/history', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }
        const history = await dbConnection.collection('announcement_history')
            .find({})
            .sort({ sentAt: -1 })
            .limit(50)
            .toArray();
        res.json({ history });
    } catch (err) {
        console.error('Get announcement history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/announcement/history', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }
        const result = await dbConnection.collection('announcement_history').deleteMany({});
        await logEvent('admin_announcement_history_cleared',
            `Admin ${req.user.username} cleared announcement history (${result.deletedCount} entries)`,
            req.user.userId);
        res.json({ success: true, deletedCount: result.deletedCount });
    } catch (err) {
        console.error('Clear announcement history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/email-campaigns', verifyToken, async (req, res) => {
    try {
        const campaigns = await dbConnection.collection('email_campaigns')
            .find({})
            .sort({ sentAt: -1 })
            .limit(50)
            .toArray();

        res.json(campaigns);
    } catch (err) {
        console.error('Get campaigns error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/email-campaigns', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }
        const result = await dbConnection.collection('email_campaigns').deleteMany({});
        await logEvent('admin_email_campaigns_cleared',
            `Admin ${req.user.username} cleared email campaign history (${result.deletedCount} entries)`,
            req.user.userId);
        res.json({ success: true, deletedCount: result.deletedCount });
    } catch (err) {
        console.error('Clear email campaigns error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/admin/test-email', verifyToken, async (req, res) => {
    try {
        const testEmail = req.body.email || req.user.email;

        if (!testEmail) {
            return res.status(400).json({ error: 'Email address required' });
        }

        await emailTransporter.sendMail({
            from: process.env.SMTP_FROM || 'noreply@freetimeapp.com',
            to: testEmail,
            subject: 'FreeTime Admin Panel - Test Email',
            html: '<h2>Test Email</h2><p>If you received this, email is working correctly!</p>'
        });

        res.json({
            success: true,
            message: 'Test email sent successfully'
        });
    } catch (err) {
        console.error('Test email error:', err);
        res.status(500).json({ error: 'Failed to send test email: ' + err.message });
    }
});

async function broadcastShutdownNotification() {
    if (!firebaseInitialized || !admin) {
        console.warn('[ BROADCAST] Firebase not initialized — cannot send shutdown notifications');
        return;
    }

    try {
        const tokens = await dbConnection.collection('FCMTokens').find({}).toArray();
        if (!tokens || tokens.length === 0) {
            console.log('[BROADCAST] No FCM tokens found — nothing to send');
            return;
        }

        console.log(`[BROADCAST] Sending server shutdown notification to ${tokens.length} device(s)...`);

        const uniqueTokens = [...new Set(tokens.map(t => t.fcmToken))];
        let successCount = 0;
        let failCount = 0;

        for (const token of uniqueTokens) {
            try {
                await admin.messaging().send({
                    token,
                    data: {
                        type: 'server_shutdown',
                        title: 'FreeTime servers are offline',
                        body: 'Only private text messages are available. Group chats and media are temporarily disabled.'
                    },
                    android: {
                        priority: 'high',
                        ttl: 300000
                    }
                });
                successCount++;
            } catch (err) {
                failCount++;
                if (err.code === 'messaging/invalid-registration-token' ||
                    err.code === 'messaging/registration-token-not-registered') {
                    await dbConnection.collection('FCMTokens').deleteOne({ fcmToken: token });
                } else {
                    console.warn(`[BROADCAST] Failed to send to token: ${err.message}`);
                }
            }
        }

        console.log(`[BROADCAST] Shutdown notifications sent: ${successCount} ok, ${failCount} failed`);
    } catch (err) {
        console.error('[BROADCAST] Error broadcasting shutdown notification:', err.message);
    }
}

app.post('/api/admin/broadcast-shutdown', verifyToken, async (req, res) => {
    try {
        if (req.user?.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Admin access required' });
        }

        await broadcastShutdownNotification();
        res.json({ success: true, message: 'Shutdown notification sent to all users' });
    } catch (err) {
        console.error('Broadcast shutdown error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/internal/broadcast-shutdown', async (req, res) => {
    const clientIp = req.ip || req.connection?.remoteAddress || '';
    const isLocal = clientIp.includes('127.0.0.1') || clientIp.includes('::1') || clientIp.includes('::ffff:127.0.0.1');
    if (!isLocal) {
        return res.status(403).json({ error: 'Internal endpoint — localhost only' });
    }

    try {
        await broadcastShutdownNotification();
        res.json({ success: true, message: 'Shutdown notification sent to all users' });
    } catch (err) {
        console.error('Internal broadcast shutdown error:', err);
        res.status(500).json({ error: err.message });
    }
});

async function gracefulShutdown(signal) {
    console.log(`\n${signal} signal received: Gracefully shutting down...`);

    try {
        await broadcastShutdownNotification();
    } catch (err) {
        console.error('[WARN] Failed to broadcast shutdown notification:', err.message);
    }

    if (server) {
        if (global.socketIoServer) {
            console.log('[OK] Closing Socket.IO server...');
            global.socketIoServer.close();
        }

        server.close(async () => {
            console.log('[OK] HTTP server closed');

            try {
                if (dbManager && typeof dbManager.shutdown === 'function') {
                    await dbManager.shutdown();
                    console.log('[OK] Database connection closed');
                }
            } catch (err) {
                console.error('Error closing database:', err);
            }

            console.log('[OK] Graceful shutdown completed');
            process.exit(0);
        });

        setTimeout(() => {
            console.error('[ERROR] Forced shutdown after timeout');
            process.exit(1);
        }, 10000);
    } else {
        process.exit(0);
    }
}

app.post('/api/groups', verifyToken, async (req, res) => {
    const { name, description, members } = req.body;
    const createdBy = req.user.userId;

    try {
        if (!name || name.trim().length === 0) {
            return res.status(400).json({ error: 'Group name is required' });
        }

        const groupId = uuidv4();
        const now = new Date();

        const creator = await dbConnection.collection('users').findOne({ id: createdBy });

        const memberIds = [createdBy, ...(members || [])];
        const uniqueMemberIds = [...new Set(memberIds)];

        const groupMembers = [];
        for (const memberId of uniqueMemberIds) {
            const user = await dbConnection.collection('users').findOne({ id: memberId });
            if (user) {
                groupMembers.push({
                    userId: memberId,
                    username: user.username || 'Unknown',
                    displayName: user.displayName || '',
                    avatar: user.profile?.profileImageUrl || user.profile?.profileImage || user.avatar || null,
                    role: user.role || 'USER',
                    tags: user.tags || [],
                    displayedStatus: user.displayedStatus || 'offline',
                    isAdmin: memberId === createdBy,
                    joinedAt: now.toISOString()
                });
            }
        }

        const inviteCode = groupId.substring(0, 8).toUpperCase();
        const deepLink = `freetime://group/invite/${groupId}`;
        const webLink = `https://example.com/group/invite/${inviteCode}`;

        const group = {
            id: groupId,
            groupId: groupId,
            name: name.trim(),
            description: description || '',
            createdBy: createdBy,
            createdAt: now,
            updatedAt: now,
            members: groupMembers,
            admins: [createdBy],
            adminIds: [createdBy],
            messageCount: 0,
            memberCount: groupMembers.length,
            inviteLink: deepLink,
            inviteCode: inviteCode,
            webInviteLink: webLink,
            webInviteCode: inviteCode,
            isActive: true
        };

        const insertResult = await dbConnection.collection('groups').insertOne(group);
        if (!insertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to create group in database' });
        }

        await logEvent('group_created', `Group ${name} created by ${createdBy}`, createdBy);

        let broadcastSent = false;

        if (global.io || global.socketIoServer) {
            try {
                const io = global.io || global.socketIoServer;
                io.emit('group_created', {
                    groupId: groupId,
                    group: group,
                    timestamp: now
                });
                broadcastSent = true;
                console.log(`[OK] Group creation broadcast via Socket.IO for group ${groupId}`);
            } catch (ioErr) {
                console.warn(`[WARN] Socket.IO broadcast failed: ${ioErr.message}`);
            }
        }

        if (!broadcastSent) {
            try {
                const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
                broadcastToUsers(global.wsClients, uniqueMemberIds, 'group.created', {
                    groupId: groupId,
                    groupName: group.name,
                    description: group.description,
                    memberCount: group.memberCount,
                    createdBy: createdBy,
                    createdAt: now.toISOString()
                });
                broadcastSent = true;
                console.log(`[OK] Group creation broadcast via broadcastToUsers for group ${groupId}`);
            } catch (broadcastErr) {
                console.warn(`[WARN] Broadcast-utils broadcast failed: ${broadcastErr.message}`);
            }
        }

        res.status(201).json({
            success: true,
            groupId: groupId,
            inviteLink: deepLink,
            inviteCode: inviteCode,
            group: group,
            broadcastStatus: broadcastSent ? 'delivered' : 'queued'
        });
    } catch (err) {
        console.error('Create group error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups', verifyToken, async (req, res) => {
    try {
        const userId = req.user.userId;

        const groups = await dbConnection.collection('groups')
            .find({
                $or: [
                    { 'members.userId': userId },
                    { 'members.id': userId },
                    { members: userId },
                    { adminIds: userId },
                    { admins: userId }
                ]
            })
            .toArray();

        const allMemberIds = new Set();
        groups.forEach(group => {
            (group.members || []).forEach(m => {
                const mid = typeof m === 'object' ? (m.userId || m.id) : m;
                if (mid) allMemberIds.add(mid);
            });
        });
        const existingUsers = await dbConnection.collection('users').find(
            { id: { $in: [...allMemberIds] } },
            { projection: { id: 1 } }
        ).toArray();
        const existingUserIds = new Set(existingUsers.map(u => u.id));

        const groupsData = groups.map(group => {
            const activeMembers = (group.members || []).filter(m => {
                const mid = typeof m === 'object' ? (m.userId || m.id) : m;
                return existingUserIds.has(mid);
            });
            return {
                groupId: group.id || group.groupId || (group._id ? group._id.toString() : null),
                name: group.name,
                description: group.description,
                createdBy: group.createdBy,
                creatorId: group.createdBy || group.creatorId,
                avatar: group.avatar,
                members: activeMembers,
                adminIds: group.adminIds || [],
                createdAt: group.createdAt,
                memberCount: activeMembers.length,
                messageCount: group.messageCount || 0,
                inviteLink: group.inviteLink || `freetime://group/invite/${group.id || group.groupId || (group._id ? group._id.toString() : '')}`,
                inviteCode: group.inviteCode || (group.id || group.groupId || (group._id ? group._id.toString() : 'UNKNOWN')).toString().substring(0, 8).toUpperCase(),
                isPrivate: group.isPrivate || false
            };
        });

        res.json({
            success: true,
            groups: groupsData,
            total: groupsData.length
        });
    } catch (err) {
        console.error('Get groups error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId }] });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = group.members && Array.isArray(group.members) &&
                         group.members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === userId);

        if (!isMember) {
            const inAdmins = (Array.isArray(group.adminIds) && group.adminIds.includes(userId)) ||
                             (Array.isArray(group.admins) && group.admins.includes(userId));
            if (!inAdmins) {
                return res.status(403).json({ error: 'Not a member of this group' });
            }
        }

        const membersWithFreshAvatars = (await Promise.all(
            (group.members || []).filter(m => m !== null).map(async (member) => {
                const mUserId = typeof member === 'object' ? (member.userId || member.id) : member;
                const latestUser = await dbConnection.collection('users').findOne({ id: mUserId });
                if (!latestUser) return null;
                return {
                    ...(typeof member === 'object' ? member : { userId: member }),
                    avatar: latestUser?.profile?.profileImageUrl || latestUser?.profile?.profileImage || (typeof member === 'object' ? member.avatar : null) || null,
                    systemRole: latestUser.role || 'USER',
                    isSystemAdmin: latestUser.role === 'ADMIN',
                    isSystemModerator: latestUser.role === 'MODERATOR'
                };
            })
        )).filter(m => m !== null);

        const actualGroupId = group.id || group.groupId;
        const generatedInviteCode = group.inviteCode || actualGroupId.substring(0, 8).toUpperCase();

        res.json({
            success: true,
            group: {
                groupId: groupId,
                name: group.name,
                description: group.description,
                createdBy: group.createdBy,
                creatorId: group.createdBy || group.creatorId,
                avatar: group.avatar,
                members: membersWithFreshAvatars,
                adminIds: group.adminIds || [],
                admins: group.admins || [group.createdBy],
                inviteLink: group.inviteLink || `freetime://group/invite/${groupId}`,
                webInviteLink: `https://example.com/group/invite/${generatedInviteCode}`,
                inviteCode: generatedInviteCode,
                webInviteCode: generatedInviteCode,
                createdAt: group.createdAt,
                memberCount: membersWithFreshAvatars.length,
                messageCount: group.messageCount || 0,
                isPrivate: group.isPrivate !== undefined ? group.isPrivate : true,
                profilePictureUrl: group.profilePictureUrl || null,
                profilePictureThumbnailUrl: group.profilePictureThumbnailUrl || null,
                profilePictureUpdatedAt: group.profilePictureUpdatedAt || null
            }
        });
    } catch (err) {
        console.error('Get group details error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/invite', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { userIds } = req.body;
        const userId = req.user.userId;

        console.log(' [INVITE] POST /api/groups/:groupId/invite');
        console.log(' [INVITE] groupId:', groupId, 'type:', typeof groupId);
        console.log(' [INVITE] userIds:', userIds);
        console.log(' [INVITE] userId from token:', userId, 'type:', typeof userId);

        if (!Array.isArray(userIds) || userIds.length === 0) {
            return res.status(400).json({ error: 'User IDs array is required' });
        }

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId }] });
        if (!group) {
            console.log(' [INVITE] Group not found for groupId:', groupId);
            return res.status(404).json({ error: 'Group not found' });
        }

        console.log(' [INVITE] Found group:', group.name);
        console.log(' [INVITE] group.admins:', group.admins, 'type:', Array.isArray(group.admins) ? 'array' : typeof group.admins);
        console.log(' [INVITE] group.admins entries:', group.admins?.map(a => `${a} (type: ${typeof a})`));
        console.log(' [INVITE] Is userId in admins?', group.admins?.includes(userId));

        const isRequesterAdmin =
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isRequesterAdmin) {
            console.log(' [INVITE] FORBIDDEN: User', userId, 'not in admins. group.admins:', group.admins, 'group.adminIds:', group.adminIds);
            return res.status(403).json({ error: 'Only admins can invite members' });
        }

        console.log(' [INVITE] User is admin, proceeding with invite');

        const inviterUser = await dbConnection.collection('users').findOne({ id: userId });
        const isInviterModerator = inviterUser?.role === 'MODERATOR' || inviterUser?.role === 'moderator';
        const isInviterAdmin = inviterUser?.role === 'ADMIN' || inviterUser?.role === 'admin';
        const isInviterSystemAdmin = isInviterModerator || isInviterAdmin;

        console.log(` [INVITE] Inviter ${userId}: moderator=${isInviterModerator}, admin=${isInviterAdmin}, systemAdmin=${isInviterSystemAdmin}`);

        const invitedUsers = [];
        const pendingInvites = [];
        const existingMembers = group.members.map(m => m.userId);

        for (const inviteUserId of userIds) {
            if (existingMembers.includes(inviteUserId)) {
                continue;
            }

            const invitedUser = await dbConnection.collection('users').findOne({ id: inviteUserId });
            if (!invitedUser) {
                continue;
            }

            const newMember = {
                userId: inviteUserId,
                username: invitedUser.username || 'Unknown',
                displayName: invitedUser.displayName || '',
                avatar: invitedUser.profile?.profileImageUrl || invitedUser.profile?.profileImage || invitedUser.avatar || null,
                role: invitedUser.role || 'USER',
                tags: invitedUser.tags || [],
                displayedStatus: invitedUser.displayedStatus || 'offline',
                isAdmin: false,
                joinedAt: new Date().toISOString()
            };

            if (isInviterSystemAdmin) {
                invitedUsers.push(newMember);
            } else {
                pendingInvites.push({
                    inviteId: uuidv4(),
                    groupId: group.id || groupId,
                    groupName: group.name,
                    groupIcon: group.icon || null,
                    invitedUserId: inviteUserId,
                    invitedUsername: invitedUser.username,
                    invitedUserDisplayName: invitedUser.displayName || invitedUser.username,
                    inviterUserId: userId,
                    inviterUsername: inviterUser.username,
                    inviterDisplayName: inviterUser.displayName || inviterUser.username,
                    status: 'pending',
                    createdAt: new Date().toISOString(),
                    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
                });
            }
        }

        if (invitedUsers.length > 0) {
            await dbConnection.collection('groups').updateOne(
                { groupId },
                {
                    $push: { members: { $each: invitedUsers } },
                    $set: { updatedAt: new Date().toISOString() }
                }
            );

            for (const invitedUser of invitedUsers) {
                await dbConnection.collection('users').updateOne(
                    { id: invitedUser.userId },
                    { $push: { userGroups: { groupId, groupName: group.name } } }
                );
            }

            const io = global.io || global.socketIoServer || global.socketIoWebSocketServer;
            for (const invitedUser of invitedUsers) {
                if (io) {
                    io.to(`user:${invitedUser.userId}`).emit('group_invite', {
                        inviteId: '',
                        groupId: groupId,
                        groupName: group.name,
                        groupIcon: group.icon || null,
                        inviterId: userId,
                        inviterName: inviterUser.displayName || inviterUser.username,
                        inviterUsername: inviterUser.username,
                        inviterDisplayName: inviterUser.displayName || inviterUser.username,
                        status: 'accepted',
                        createdAt: new Date().toISOString()
                    });
                    io.to(`group:${groupId}`).emit('groupUpdated', {
                        ...group,
                        members: [...(group.members || []), ...invitedUsers]
                    });
                }

                try {
                    if (pushNotificationManager) {
                        await pushNotificationManager.sendGroupInviteNotification(
                            invitedUser.userId,
                            { userId: userId, username: inviterUser.displayName || inviterUser.username },
                            { groupId: groupId, groupName: group.name },
                            ''
                        );
                    } else {
                        await sendPushNotification(invitedUser.userId, {
                            notification: {
                                title: 'Added to Group',
                                body: `${inviterUser.displayName || inviterUser.username} added you to ${group.name}`,
                                sound: 'default'
                            },
                            data: {
                                type: 'groupInvite',
                                groupId: groupId,
                                groupName: group.name,
                                inviterName: inviterUser.displayName || inviterUser.username,
                                inviteId: ''
                            }
                        });
                    }
                } catch (fcmErr) {
                    console.warn('Direct add FCM failed:', fcmErr.message);
                }
            }

            await logEvent('group_members_invited_direct',
                `${invitedUsers.length} members directly added to group ${group.name} (inviter is system moderator/admin)`,
                userId);
        }

        if (pendingInvites.length > 0) {
            await dbConnection.collection('groupInvitations').insertMany(pendingInvites);

            const io = global.io || global.socketIoServer || global.socketIoWebSocketServer;
            if (io) {
                for (const invite of pendingInvites) {
                    const wsPayload = {
                        inviteId: invite.inviteId,
                        groupId: invite.groupId,
                        groupName: invite.groupName,
                        groupIcon: invite.groupIcon,
                        inviterId: invite.inviterUserId,
                        inviterName: invite.inviterDisplayName || invite.inviterUsername,
                        inviterUsername: invite.inviterUsername,
                        inviterDisplayName: invite.inviterDisplayName,
                        status: 'pending',
                        createdAt: invite.createdAt
                    };
                    io.to(`user:${invite.invitedUserId}`).emit('group_invite', wsPayload);
                    io.to(`user:${invite.invitedUserId}`).emit('group.invite.pending', wsPayload);
                }
            }

            for (const invite of pendingInvites) {
                try {
                    if (pushNotificationManager) {
                        await pushNotificationManager.sendGroupInviteNotification(
                            invite.invitedUserId,
                            { userId: invite.inviterUserId, username: invite.inviterDisplayName || invite.inviterUsername },
                            { groupId: invite.groupId, groupName: invite.groupName },
                            invite.inviteId
                        );
                    } else {
                        await sendPushNotification(invite.invitedUserId, {
                            notification: {
                                title: 'Group Invite',
                                body: `${invite.inviterDisplayName || invite.inviterUsername} invited you to ${invite.groupName}`,
                                sound: 'default'
                            },
                            data: {
                                type: 'groupInvite',
                                groupId: invite.groupId,
                                groupName: invite.groupName,
                                inviterName: invite.inviterDisplayName || invite.inviterUsername,
                                inviteId: invite.inviteId
                            }
                        });
                    }
                } catch (fcmErr) {
                    console.warn('Group invite FCM failed:', fcmErr.message);
                }
            }

            await logEvent('group_invites_pending',
                `${pendingInvites.length} pending invitations sent to group ${group.name} (awaiting user consent)`,
                userId);
        }

        res.json({
            success: true,
            message: `${invitedUsers.length} members added directly, ${pendingInvites.length} pending invitations sent`,
            directlyAdded: invitedUsers,
            pendingInvites: pendingInvites.map(i => ({
                inviteId: i.inviteId,
                invitedUsername: i.invitedUsername,
                status: i.status
            }))
        });
    } catch (err) {
        console.error('Invite to group error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/invite-link', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { expiresIn } = req.body || {};
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isAdmin) {
            return res.status(403).json({ error: 'Only admins can generate invite links' });
        }

        const duration = Math.min(Number(expiresIn) || 3600000, 86400000);
        const expiresAt = Date.now() + duration;
        const timeSlot = Math.floor(Date.now() / 3600000);
        const code = `FT${Buffer.from(groupId).toString('base64url').substring(0, 8).toUpperCase()}${timeSlot.toString(36).toUpperCase()}`;

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            { $set: { inviteCode: code, webInviteCode: code, updatedAt: new Date().toISOString() } }
        );

        const baseServerUrl = (process.env.MAIN_SERVER_URL || 'https://freetime.app').trimEnd('/');
        const shareLink = `${baseServerUrl}/api/groups/web/invite/${code}`;

        res.json({
            success: true,
            inviteCode: code,
            shareLink: shareLink,
            expiresAt: expiresAt
        });
    } catch (err) {
        console.error('Generate invite link error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/join-by-invite', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.some(m => (typeof m === 'object' && m.userId === userId) || m === userId)
        );

        if (isMember) {
            return res.status(409).json({ error: 'You are already a member of this group' });
        }

        const maxMembers = group.maxMembers || 500;
        if (group.members && group.members.length >= maxMembers) {
            return res.status(400).json({ error: 'Group is at maximum capacity' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const newMember = {
            userId: userId,
            username: user.username || 'Unknown',
            displayName: user.displayName || '',
            avatar: user.profile?.profileImageUrl || user.profile?.profileImage || user.avatar || null,
            role: user.role || 'USER',
            tags: user.tags || [],
            displayedStatus: user.displayedStatus || 'offline',
            isAdmin: false,
            joinedAt: new Date().toISOString()
        };

        const updateResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $push: { members: newMember },
                $set: {
                    updatedAt: new Date().toISOString(),
                    memberCount: (group.members?.length || 0) + 1
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(500).json({ error: 'Failed to add member to group' });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $push: {
                    userGroups: {
                        groupId: group.id || groupId,
                        groupName: group.name,
                        joinedAt: new Date().toISOString()
                    }
                }
            }
        );

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${groupId}`).emit('group.member.joined', {
                groupId: groupId,
                userId: userId,
                username: user.username,
                displayName: user.displayName,
                memberCount: (group.members?.length || 0) + 1,
                timestamp: new Date().toISOString()
            });
        }

        try {
            const existingMemberIds = (group.members || [])
                .filter(m => m != null)
                .map(m => typeof m === 'object' ? (m.userId || m.id) : m)
                .filter(Boolean)
                .filter(id => id !== userId);

            const joinerName = user.displayName || user.username || 'Someone';

            for (const memberId of existingMemberIds) {
                await sendPushNotification(memberId, {
                    notification: {
                        title: group.name || 'Group',
                        body: `${joinerName} joined the group`,
                        sound: 'default'
                    },
                    data: {
                        type: 'group.member.joined',
                        groupId: groupId,
                        groupName: group.name,
                        userId: userId,
                        username: joinerName
                    }
                });
            }
        } catch (fcmErr) {
            console.error(`[FCM] Group member joined notification error: ${fcmErr.message}`);
        }

        await logEvent('group_member_joined_via_invite', `${user.username} joined group ${group.name} via invite link`, userId, {
            groupId: groupId,
            groupName: group.name
        });

        res.json({
            success: true,
            message: `Successfully joined ${group.name}`,
            group: {
                id: group.id || groupId,
                name: group.name,
                description: group.description,
                memberCount: (group.members?.length || 0) + 1
            }
        });
    } catch (err) {
        console.error('Join group by invite error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/group-invitations', verifyToken, async (req, res) => {
    try {
        const userId = req.user.userId;

        const invitations = await dbConnection.collection('groupInvitations').find({
            invitedUserId: userId,
            status: 'pending',
            expiresAt: { $gt: new Date().toISOString() }
        }).toArray();

        res.json({
            success: true,
            invitations: invitations || []
        });
    } catch (err) {
        console.error('Get invitations error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/group-invitations/:inviteId/accept', verifyToken, async (req, res) => {
    try {
        const { inviteId } = req.params;
        const userId = req.user.userId;

        const invitation = await dbConnection.collection('groupInvitations').findOne({ inviteId });
        if (!invitation) {
            return res.status(404).json({ error: 'Invitation not found' });
        }

        if (invitation.invitedUserId !== userId) {
            return res.status(403).json({ error: 'This invitation is not for you' });
        }

        if (invitation.status !== 'pending') {
            return res.status(400).json({ error: `Invitation already ${invitation.status}` });
        }

        if (invitation.expiresAt && new Date(invitation.expiresAt) < new Date()) {
            return res.status(400).json({ error: 'Invitation has expired' });
        }

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: invitation.groupId }, { groupId: invitation.groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = group.members.some(m => m.userId === userId);
        if (isMember) {
            return res.status(409).json({ error: 'You are already a member of this group' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });

        const newMember = {
            userId: userId,
            username: user?.username || 'Unknown',
            displayName: user?.displayName || '',
            avatar: user?.profile?.profileImageUrl || user?.profile?.profileImage || user?.avatar || null,
            role: user?.role || 'USER',
            tags: user?.tags || [],
            displayedStatus: user?.displayedStatus || 'offline',
            isAdmin: false,
            joinedAt: new Date().toISOString()
        };

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: invitation.groupId }, { groupId: invitation.groupId }] },
            {
                $push: { members: newMember },
                $set: { updatedAt: new Date().toISOString() }
            }
        );

        await dbConnection.collection('users').updateOne(
            { id: userId },
            { $push: { userGroups: { groupId: invitation.groupId, groupName: invitation.groupName } } }
        );

        await dbConnection.collection('groupInvitations').updateOne(
            { inviteId },
            {
                $set: {
                    status: 'accepted',
                    acceptedAt: new Date().toISOString()
                }
            }
        );

        io.to(`group:${invitation.groupId}`).emit('group.member.joined', {
            groupId: invitation.groupId,
            userId: userId,
            username: user?.username,
            displayName: user?.displayName,
            joinedAt: newMember.joinedAt
        });

        try {
            const existingMemberIds = (group.members || [])
                .filter(m => m != null)
                .map(m => typeof m === 'object' ? (m.userId || m.id) : m)
                .filter(Boolean)
                .filter(id => id !== userId);

            const joinerName = user?.displayName || user?.username || 'Someone';

            for (const memberId of existingMemberIds) {
                await sendPushNotification(memberId, {
                    notification: {
                        title: invitation.groupName || 'Group',
                        body: `${joinerName} joined the group`,
                        sound: 'default'
                    },
                    data: {
                        type: 'group.member.joined',
                        groupId: invitation.groupId,
                        groupName: invitation.groupName,
                        userId: userId,
                        username: joinerName
                    }
                });
            }
        } catch (fcmErr) {
            console.error(`[FCM] Group member joined notification error: ${fcmErr.message}`);
        }

        await logEvent('group_invite_accepted', `${user?.username} accepted invitation to group ${invitation.groupName}`, userId);

        res.json({
            success: true,
            message: 'Invitation accepted',
            group: {
                id: group.id,
                name: group.name
            }
        });
    } catch (err) {
        console.error('Accept invitation error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/group-invitations/:inviteId/decline', verifyToken, async (req, res) => {
    try {
        const { inviteId } = req.params;
        const userId = req.user.userId;

        const invitation = await dbConnection.collection('groupInvitations').findOne({ inviteId });
        if (!invitation) {
            return res.status(404).json({ error: 'Invitation not found' });
        }

        if (invitation.invitedUserId !== userId) {
            return res.status(403).json({ error: 'This invitation is not for you' });
        }

        if (invitation.status !== 'pending') {
            return res.status(400).json({ error: `Invitation already ${invitation.status}` });
        }

        await dbConnection.collection('groupInvitations').updateOne(
            { inviteId },
            {
                $set: {
                    status: 'declined',
                    declinedAt: new Date().toISOString()
                }
            }
        );

        await logEvent('group_invite_declined', `User ${userId} declined invitation to group ${invitation.groupName}`, userId);

        res.json({
            success: true,
            message: 'Invitation declined'
        });
    } catch (err) {
        console.error('Decline invitation error:', err);
        res.status(500).json({ error: err.message });
    }
});

async function autoPromoteOldestMember(group, dbConnection, io, userId) {
    try {
        const adminIds = Array.isArray(group.adminIds) ? group.adminIds : (Array.isArray(group.admins) ? group.admins : []);
        const members = Array.isArray(group.members) ? group.members : [];

        const validMembers = members.filter(m => m !== null);

        const otherMembers = validMembers.filter(m => {
            const mid = (m && typeof m === 'object') ? (m.userId || m.id) : m;
            return mid && mid !== userId;
        });

        if (otherMembers.length > 0) {
            const oldestMember = otherMembers.sort((a, b) => {
                const aDate = (a && typeof a === 'object' ? a.joinDate : null) || 0;
                const bDate = (b && typeof b === 'object' ? b.joinDate : null) || 0;

                if (aDate && bDate) return new Date(aDate) - new Date(bDate);
                if (aDate) return -1;
                if (bDate) return 1;
                return 0;
            })[0];

            if (oldestMember) {
                const oldestMemberId = (oldestMember && typeof oldestMember === 'object') ? (oldestMember.userId || oldestMember.id) : oldestMember;
                if (!oldestMemberId) return;

                await dbConnection.collection('groups').updateOne(
                    { $or: [{ id: group.id }, { groupId: group.id }, { id: group.groupId }, { groupId: group.groupId }] },
                    {
                        $addToSet: { admins: oldestMemberId, adminIds: oldestMemberId },
                        $set: { updatedAt: new Date() }
                    }
                );

                const promotedUser = await dbConnection.collection('users').findOne(
                    { id: oldestMemberId },
                    { projection: { username: 1 } }
                );
                const promotedUsername = promotedUser?.username || 'Member';

                if (io) {
                    io.to(`group:${group.id || group.groupId}`).emit('group.member.promoted', {
                        groupId: group.id || group.groupId,
                        userId: oldestMemberId,
                        username: promotedUsername,
                        isAutoPromotion: true,
                        reason: 'Last admin left - auto-promoted as oldest member',
                        timestamp: new Date().toISOString()
                    });
                }

                try {
                    const grpName = group.name || 'Group';
                    await sendPushNotification(oldestMemberId, {
                        notification: {
                            title: 'Auto-Promoted to Admin',
                            body: `You were auto-promoted to admin in "${grpName}" (last admin left)`,
                            sound: 'default'
                        },
                        data: {
                            type: 'group.member.promoted',
                            groupId: group.id || group.groupId,
                            groupName: grpName,
                            userId: oldestMemberId,
                            username: promotedUsername,
                            isAutoPromotion: 'true'
                        }
                    });
                } catch (fcmErr) {
                    console.error(`[FCM] Group auto-promote notification error: ${fcmErr.message}`);
                }

                console.log(`[AUTO-PROMOTE] ${promotedUsername} auto-promoted to admin in group ${group.name} (oldest member, last admin left)`);
                await logEvent('group_auto_promote', `${promotedUsername} auto-promoted to admin as oldest member`, oldestMemberId, {
                    groupId: group.id || group.groupId,
                    groupName: group.name,
                    reason: 'last_admin_left'
                });

                return oldestMemberId;
            }
        }
    } catch (err) {
        console.error(`[ERROR] Auto-promote failed: ${err.message}`);
    }
    return null;
}

app.delete('/api/groups/:groupId/members/:memberId', verifyToken, async (req, res) => {
    try {
        const { groupId, memberId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isRemoveAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (userId !== memberId && !isRemoveAdmin) {
            return res.status(403).json({ error: 'Not authorized' });
        }

        if (memberId === group.createdBy && userId !== group.createdBy) {
            return res.status(403).json({ error: 'Cannot remove the group creator' });
        }

        const allAdmins = new Set([
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => typeof a === 'string') : []),
            ...(Array.isArray(group.admins) ? group.admins.filter(a => typeof a === 'string') : []),
            group.createdBy
        ].filter(Boolean));
        const adminCount = allAdmins.size;
        const isLastAdmin = userId === memberId && adminCount <= 1;
        if (isLastAdmin) {
            return res.status(400).json({ error: 'Cannot remove last administrator' });
        }

        const wasLastAdmin = allAdmins.size === 1 && allAdmins.has(memberId);

        let removeMemberResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $pull: {
                    members: { userId: memberId },
                    admins: memberId,
                    adminIds: memberId
                },
                $set: { updatedAt: new Date().toISOString() }
            }
        );
        if (removeMemberResult.modifiedCount === 0) {
            removeMemberResult = await dbConnection.collection('groups').updateOne(
                { $or: [{ id: groupId }, { groupId: groupId }] },
                {
                    $pull: {
                        members: memberId,
                        admins: memberId,
                        adminIds: memberId
                    },
                    $set: { updatedAt: new Date().toISOString() }
                }
            );
        }
        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $pull: {
                    admins: { userId: memberId },
                    adminIds: { userId: memberId }
                }
            }
        );

        if (removeMemberResult.matchedCount === 0) {
            return res.status(500).json({ error: 'Failed to find group for member removal' });
        }

        if (removeMemberResult.modifiedCount === 0) {
            return res.status(404).json({ error: 'Member not found in group' });
        }

        const removeGroupRefResult = await dbConnection.collection('users').updateOne(
            { id: memberId },
            { $pull: { userGroups: { groupId } } }
        );

        if (wasLastAdmin) {
            const updatedGroup = await dbConnection.collection('groups').findOne({
                $or: [{ id: groupId }, { groupId: groupId }]
            });
            const io = require('../websocket/socket-io-server').io;
            if (updatedGroup) {
                await autoPromoteOldestMember(updatedGroup, dbConnection, io, memberId);
            }
        }

        await logEvent('group_member_removed', `Member ${memberId} removed from group ${group.name}`, userId);

        res.json({
            success: true,
            message: 'Member removed successfully'
        });
    } catch (err) {
        console.error('Remove group member error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId/members', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => (typeof m === 'object' && m.userId === userId))
        );
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const adminsSet = new Set([
            ...(Array.isArray(group.admins) ? group.admins.filter(a => typeof a === 'string') : []),
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => typeof a === 'string') : []),
            group.createdBy
        ].filter(Boolean));
        const objectAdmins = [
            ...(Array.isArray(group.admins) ? group.admins.filter(a => a && typeof a === 'object') : []),
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => a && typeof a === 'object') : [])
        ];

        const memberIds = group.members.filter(m => m != null).map(m => (typeof m === 'object' ? (m.userId || m.id) : m)).filter(Boolean);
        const memberUsers = memberIds.length > 0 ? await dbConnection.collection('users').find(
            { id: { $in: memberIds } },
            { projection: { id: 1, role: 1 } }
        ).toArray() : [];
        const memberRoleMap = {};
        memberUsers.forEach(u => { memberRoleMap[u.id] = u.role || 'USER'; });

        res.json({
            success: true,
            members: group.members.map(member => {
                const mid = typeof member === 'object' ? (member.userId || member.id) : member;
                const systemRole = memberRoleMap[mid] || 'USER';
                return {
                    ...member,
                    isAdmin: adminsSet.has(mid) || objectAdmins.some(a => a.userId === mid || a.id === mid),
                    systemRole: systemRole,
                    isSystemAdmin: systemRole === 'ADMIN',
                    isSystemModerator: systemRole === 'MODERATOR'
                };
            })
        });
    } catch (err) {
        console.error('Get group members error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/leave', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => m && typeof m === 'object' && (m.userId === userId || m.id === userId))
        );

        const inAdmins = (Array.isArray(group.adminIds) && group.adminIds.includes(userId)) ||
                         (Array.isArray(group.admins) && group.admins.includes(userId));

        if (!isMember && !inAdmins) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const adminIds = Array.isArray(group.adminIds) ? group.adminIds : (Array.isArray(group.admins) ? group.admins : []);
        const isAdmin = inAdmins || adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId));

        const adminCount = (Array.isArray(group.members) ? group.members : []).filter(m => {
            if (!m) return false;
            const mid = typeof m === 'object' ? (m.userId || m.id) : m;
            return (Array.isArray(group.adminIds) && group.adminIds.includes(mid)) ||
                   (Array.isArray(group.admins) && group.admins.includes(mid));
        }).length;

        let transferAdminToUserId = req.body?.transferAdminToUserId || null;
        let deleteGroup = req.body?.deleteGroup || false;

        if (isAdmin && adminCount === 1) {
            if (!transferAdminToUserId && !deleteGroup) {
                const otherMembers = (Array.isArray(group.members) ? group.members : []).filter(m => {
                    if (!m) return false;
                    const mid = typeof m === 'object' ? (m.userId || m.id) : m;
                    return mid !== userId;
                });

                return res.status(400).json({
                    error: 'Last admin must transfer admin role or delete group',
                    code: 'LAST_ADMIN_ACTION_REQUIRED',
                    remainingMemberCount: otherMembers.length,
                    requiresAction: {
                        transferAdminToUserId: 'Optional - another member to promote',
                        deleteGroup: 'Optional - true to delete entire group'
                    }
                });
            }

            if (transferAdminToUserId) {
                const targetMember = (Array.isArray(group.members) ? group.members : []).find(m => {
                    if (!m) return false;
                    const mid = typeof m === 'object' ? (m.userId || m.id) : m;
                    return mid === transferAdminToUserId;
                });

                if (!targetMember) {
                    return res.status(400).json({ error: 'Target member not found in group' });
                }

                const newAdminIds = [...adminIds, transferAdminToUserId];
                await dbConnection.collection('groups').updateOne(
                    { $or: [{ id: groupId }, { groupId: groupId }] },
                    {
                        $set: {
                            adminIds: newAdminIds,
                            admins: newAdminIds,
                            updatedAt: new Date()
                        }
                    }
                );

                console.log(`[OK] Admin transferred from ${userId} to ${transferAdminToUserId} in group ${groupId}`);
            }

            if (deleteGroup) {
                await dbConnection.collection('groups').deleteOne({
                    $or: [{ id: groupId }, { groupId: groupId }]
                });

                if (global.socketIoServer) {
                    global.socketIoServer.emit('groupDeleted', {
                        groupId: groupId,
                        deletedBy: userId,
                        timestamp: new Date().getTime()
                    });
                }

                console.log(`[OK] Group ${groupId} deleted by last admin ${userId}`);

                return res.json({
                    success: true,
                    message: 'Group deleted successfully',
                    action: 'deleted'
                });
            }
        }

        const updateOps = {
            $pull: {
                members: userId
            },
            $set: {
                updatedAt: new Date(),
                memberCount: Math.max(0, (Array.isArray(group.members) ? group.members.length : 0) - 1)
            }
        };

        if (isAdmin) {
            updateOps.$pull.admins = userId;
            updateOps.$pull.adminIds = userId;
        }

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            updateOps
        );

        const pullObjectCondition = { userId: userId };
        const updateOpsObject = {
            $pull: {
                members: pullObjectCondition
            }
        };
        if (isAdmin) {
            updateOpsObject.$pull.admins = pullObjectCondition;
            updateOpsObject.$pull.adminIds = pullObjectCondition;
        }
        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            updateOpsObject
        );

        const groupAfterRemoval = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        const io = global.io || global.socketIoServer || global.socketIoWebSocketServer;
        if (groupAfterRemoval && isAdmin && adminCount === 1) {
            const remainingAdmins = (groupAfterRemoval.adminIds || groupAfterRemoval.admins || []);
            if (remainingAdmins.length === 0 && io) {
                await autoPromoteOldestMember(groupAfterRemoval, dbConnection, io, userId);
            }
        }

        await logEvent('group_member_left', `User ${userId} left group ${group.name}`, userId);

        if (global.socketIoServer) {
            global.socketIoServer.emit('groupMemberLeft', {
                groupId: groupId,
                userId: userId,
                memberCount: Math.max(0, (Array.isArray(group.members) ? group.members.length : 0) - 1),
                timestamp: new Date().getTime()
            });
            console.log(`[OK] Group member left notification sent via Socket.IO for group ${groupId}`);
        }

        res.json({
            success: true,
            message: 'Left group successfully'
        });
    } catch (err) {
        console.error('Leave group error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/web/invite/:inviteCode', async (req, res) => {
    try {
        const { inviteCode } = req.params;

        const group = await dbConnection.collection('groups').findOne({
            $or: [
                { webInviteCode: inviteCode.toUpperCase() },
                { inviteCode: inviteCode.toUpperCase() },
                { id: inviteCode },
                { groupId: inviteCode }
            ]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const groupId = group.id || group.groupId;
        const groupName = group.name || 'Secure Group';
        const groupAvatar = group.avatar || '';

        if (req.headers.accept && req.headers.accept.includes('text/html')) {
            const html = `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Join Group: ${groupName}</title>
    <!-- Open Graph / Facebook -->
    <meta property="og:type" content="website">
    <meta property="og:url" content="https://example.com/api/groups/web/invite/${inviteCode}">
    <meta property="og:title" content="Join ${groupName} on FreeTime">
    <meta property="og:description" content="You have been invited to join '${groupName}' on FreeTime Secure Chat. Click to join the conversation.">
    <meta property="og:image" content="${groupAvatar || 'https://example.com/logo.png'}">

    <!-- Twitter -->
    <meta property="twitter:card" content="summary_large_image">
    <meta property="twitter:url" content="https://example.com/api/groups/web/invite/${inviteCode}">
    <meta property="twitter:title" content="Join ${groupName} on FreeTime">
    <meta property="twitter:description" content="You have been invited to join '${groupName}' on FreeTime Secure Chat. Click to join the conversation.">
    <meta property="twitter:image" content="${groupAvatar || 'https://example.com/logo.png'}">

    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; background-color: #f0f2f5; margin: 0; }
        .card { background: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); text-align: center; max-width: 420px; width: 90%; }
        .avatar { width: 120px; height: 120px; border-radius: 30px; background: linear-gradient(135deg, #0084ff, #00c6ff); margin: 0 auto 24px; display: flex; align-items: center; justify-content: center; color: white; font-size: 48px; font-weight: bold; overflow: hidden; box-shadow: 0 4px 15px rgba(0,132,255,0.3); }
        .avatar img { width: 100%; height: 100%; object-fit: cover; }
        h1 { margin: 0 0 12px; color: #1c1e21; font-size: 24px; }
        p { color: #606770; margin-bottom: 32px; font-size: 16px; line-height: 1.5; }
        .btn { display: inline-block; background: #0084ff; color: white; padding: 14px 32px; border-radius: 10px; text-decoration: none; font-weight: bold; font-size: 18px; transition: all 0.2s; box-shadow: 0 4px 12px rgba(0,132,255,0.2); }
        .btn:hover { background: #0073e6; transform: translateY(-2px); box-shadow: 0 6px 15px rgba(0,132,255,0.3); }
        .footer { margin-top: 40px; font-size: 14px; color: #8a8d91; }
        .logo { font-weight: bold; color: #0084ff; }
    </style>
</head>
<body>
    <div class="card">
        <div class="avatar">
            ${groupAvatar ? `<img src="${groupAvatar}" alt="${groupName}">` : groupName.charAt(0).toUpperCase()}
        </div>
        <h1>${groupName}</h1>
        <p>You've been invited to join this secure group chat on <span class="logo">FreeTime</span>.</p>
        <a href="freetime://group/invite/${groupId}" class="btn">Join Group</a>
        <div class="footer">
            FreeTime Secure Chat<br>Private & Encrypted Communication
        </div>
    </div>
    <script>
        // Auto-redirect to deep link after a short delay if on mobile
        window.onload = function() {
            const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
            if (isMobile) {
                setTimeout(function() {
                    window.location.href = "freetime://group/invite/${groupId}";
                }, 1000);
            }
        };
    </script>
</body>
</html>`;
            return res.send(html);
        }

        res.json({
            success: true,
            group: {
                id: groupId,
                name: groupName,
                description: group.description,
                avatar: groupAvatar,
                memberCount: (group.members || []).length,
                createdAt: group.createdAt,
                isPublic: !group.isPrivate,
                creatorName: group.creatorName || 'Admin',
                inviteCode: group.webInviteCode || group.inviteCode
            }
        });
    } catch (err) {
        console.error('Get group info by invite code error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/web/join', verifyToken, async (req, res) => {
    try {
        const { inviteCode } = req.body;
        if (!inviteCode) {
            return res.status(400).json({ error: 'Invite code is required' });
        }

        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [
                { webInviteCode: inviteCode.toUpperCase() },
                { inviteCode: inviteCode.toUpperCase() },
                { id: inviteCode },
                { groupId: inviteCode }
            ]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.some(m => (typeof m === 'object' && m.userId === userId) || m === userId)
        );

        if (isMember) {
            return res.status(409).json({ error: 'You are already a member of this group' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        if (!user) {
            return res.status(404).json({ error: 'User not found' });
        }

        const newMember = {
            userId: userId,
            username: user.username || 'Unknown',
            displayName: user.displayName || '',
            avatar: user.avatar || null,
            role: user.role || 'USER',
            tags: user.tags || [],
            displayedStatus: user.displayedStatus || 'offline',
            isAdmin: false,
            joinedAt: new Date().toISOString()
        };

        const updateResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: group.id }, { groupId: group.id }] },
            {
                $push: { members: newMember },
                $set: {
                    updatedAt: new Date().toISOString(),
                    memberCount: (group.members?.length || 0) + 1
                }
            }
        );

        if (updateResult.modifiedCount === 0) {
            return res.status(500).json({ error: 'Failed to join group' });
        }

        await dbConnection.collection('users').updateOne(
            { id: userId },
            {
                $push: {
                    userGroups: {
                        groupId: group.id || group.groupId,
                        groupName: group.name,
                        joinedAt: new Date().toISOString()
                    }
                }
            }
        );

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${group.id || group.groupId}`).emit('group.member.joined', {
                groupId: group.id || group.groupId,
                userId: userId,
                username: user.username,
                displayName: user.displayName,
                joinedVia: 'web',
                memberCount: (group.members?.length || 0) + 1,
                timestamp: new Date().toISOString()
            });
        }

        try {
            const existingMemberIds = (group.members || [])
                .filter(m => m != null)
                .map(m => typeof m === 'object' ? (m.userId || m.id) : m)
                .filter(Boolean)
                .filter(id => id !== userId);

            const joinerName = user.displayName || user.username || 'Someone';

            for (const memberId of existingMemberIds) {
                await sendPushNotification(memberId, {
                    notification: {
                        title: group.name || 'Group',
                        body: `${joinerName} joined the group`,
                        sound: 'default'
                    },
                    data: {
                        type: 'group.member.joined',
                        groupId: group.id || group.groupId,
                        groupName: group.name,
                        userId: userId,
                        username: joinerName
                    }
                });
            }
        } catch (fcmErr) {
            console.error(`[FCM] Group member joined notification error: ${fcmErr.message}`);
        }

        await logEvent('group_joined_via_web', `${user.username} joined group ${group.name} via web link`, userId, {
            groupId: group.id || group.groupId,
            groupName: group.name,
            inviteCode: inviteCode
        });

        res.json({
            success: true,
            message: `Successfully joined ${group.name}!`,
            group: {
                id: group.id || group.groupId,
                name: group.name,
                description: group.description,
                avatar: group.avatar,
                memberCount: (group.members?.length || 0) + 1
            }
        });
    } catch (err) {
        console.error('Web group join error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/join/:code', verifyToken, async (req, res) => {
    try {
        const { code } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ inviteCode: code }, { webInviteCode: code }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Invalid invite code. Group not found.' });
        }

        const groupId = group.id || group.groupId;

        const isMember = (group.members || []).some(m => {
            if (typeof m === 'string') return m === userId;
            return (m.userId || m.id) === userId;
        });
        if (isMember) {
            return res.json({ success: true, groupId: groupId, message: 'Already a member' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        const displayName = user?.displayName || user?.username || 'Unknown';
        const username = user?.username || 'unknown';
        const profilePic = user?.profilePic || user?.profilePicture || null;

        const newMember = {
            userId: userId,
            username: username,
            displayName: displayName,
            profilePic: profilePic,
            role: 'member',
            isAdmin: false,
            joinedAt: new Date().toISOString()
        };

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $push: { members: newMember },
                $set: { updatedAt: new Date().toISOString() }
            }
        );

        if (typeof io !== 'undefined') {
            const updatedGroup = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
            io.to(`group:${groupId}`).emit('groupUpdated', updatedGroup);
            io.to(`user:${userId}`).emit('memberJoined', { groupId, userId, displayName, username, timestamp: new Date().toISOString() });
        }

        res.json({ success: true, groupId: groupId, groupName: group.name });
    } catch (err) {
        console.error('Join by code error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isCreator = group.createdBy === userId;
        const members = group.members || [];
        const isLastMember = members.length <= 1 && members.some(m => {
            const memberId = typeof m === 'object' ? (m.userId || m.id) : m;
            return memberId === userId;
        });

        if (!isCreator && !isLastMember && req.user.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Only the group creator or the last member can delete this group' });
        }

        const memberIds = members.map(m => typeof m === 'object' ? m.userId : m);

        await dbConnection.collection('groups').deleteOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });

        await logEvent('group_deleted', `Group ${group.name} was deleted by ${userId}`, userId, {
            groupId: groupId,
            groupName: group.name
        });

        try {
            const io = global.socketIoServer || global.io || global.socketIoWebSocketServer;
            if (io) {
                io.emit('groupDeleted', {
                    groupId: groupId,
                    deletedBy: userId,
                    timestamp: new Date().getTime()
                });
            }

            const { broadcastToUsers } = require('../websocket/broadcast-utils.js');
            broadcastToUsers(global.wsClients, memberIds, 'groupDeleted', {
                groupId: groupId,
                deletedBy: userId,
                timestamp: new Date().getTime()
            });
        } catch (e) {
            console.warn('Broadcast group deletion failed:', e.message);
        }

        res.json({
            success: true,
            message: 'Group deleted successfully'
        });
    } catch (err) {
        console.error('Delete group error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/messages', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { message, content, messageId: clientMessageId, timestamp: clientTimestamp, replyToMessageId, replyToUsername, replyToText, mediaShareMode } = req.body;
        const userId = req.user.userId;

        const messageContent = (message || content || '').trim();
        if (!messageContent) {
            return res.status(400).json({ error: 'Message cannot be empty' });
        }

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => (typeof m === 'object' && m.userId === userId))
        );
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const user = await dbConnection.collection('users').findOne({ id: userId });
        const dbMessageId = clientMessageId || `msg_${uuidv4()}`;
        const msgTimestamp = clientTimestamp || Date.now();

        let finalReplyUsername = replyToUsername || null;
        let finalReplyText = replyToText || null;
        if (replyToMessageId && (!replyToUsername || !replyToText)) {
            console.log(`[REPLY STORE] Looking up reply message: ${replyToMessageId} for context`);
            const replyIdClean = replyToMessageId.replace(/^msg_/, '');
            const originalMsg = await dbConnection.collection('groupMessages').findOne({
                $or: [
                    { id: replyToMessageId },
                    { id: replyIdClean },
                    { messageId: replyToMessageId },
                    { messageId: replyIdClean }
                ]
            });
            if (originalMsg) {
                finalReplyUsername = replyToUsername || originalMsg.senderUsername || originalMsg.senderId || null;
                finalReplyText = replyToText || originalMsg.content || originalMsg.message || null;
                console.log(`[REPLY STORE] Populated reply metadata: user=${finalReplyUsername}`);
            } else {
                console.warn(`[REPLY STORE] Could not find original message: ${replyToMessageId}`);
            }
        }

        const groupMessage = {
            id: dbMessageId,
            messageId: dbMessageId,
            groupId: group.id || groupId,
            senderId: userId,
            senderUsername: user?.username || 'Unknown',
            senderAvatar: user?.avatar || null,
            content: messageContent,
            message: messageContent,
            status: 'delivered',
            timestamp: msgTimestamp,
            createdAt: new Date(msgTimestamp),
            isRead: false,
            replyToMessageId: replyToMessageId || null,
            replyToUsername: finalReplyUsername,
            replyToText: finalReplyText,
            mediaShareMode: mediaShareMode || "protected",
            mediaId: null,
            mediaType: null,
            mediaName: null
        };

        const mediaRegex = /\[Media:\s*([^\|\]\s]+)/;
        const mediaMatch = messageContent.match(mediaRegex);
        if (mediaMatch && mediaMatch[1]) {
            groupMessage.mediaId = mediaMatch[1];
            console.log(`[GROUP MESSAGE] Extracted mediaId from content: ${groupMessage.mediaId}`);
            const fileNamePart = messageContent.split('] ').slice(1).join('] ').trim();
            if (fileNamePart) {
                groupMessage.mediaName = fileNamePart;
                const extension = fileNamePart.split('.').pop()?.toLowerCase() || '';
                if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp'].includes(extension)) {
                    groupMessage.mediaType = 'image';
                } else if (['mp4', 'mov', 'avi', 'mkv', 'webm', '3gp'].includes(extension)) {
                    groupMessage.mediaType = 'video';
                } else if (['mp3', 'wav', 'ogg', 'aac', 'm4a'].includes(extension)) {
                    groupMessage.mediaType = 'audio';
                } else {
                    groupMessage.mediaType = 'document';
                }
                console.log(`[GROUP MESSAGE] Extracted mediaName=${groupMessage.mediaName}, mediaType=${groupMessage.mediaType}`);
            }
        }

        const msgInsertResult = await dbConnection.collection('groupMessages').insertOne(groupMessage);
        if (!msgInsertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to send group message' });
        }

        const updateResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            { $inc: { messageCount: 1 }, $set: { updatedAt: new Date() } }
        );
        if (updateResult.matchedCount === 0) {
            console.warn(`Group ${groupId} not found for message count update`);
        }

        const messageEventData = {
            messageId: dbMessageId,
            groupId: group.id || groupId,
            senderId: userId,
            senderUsername: user?.username || 'Unknown',
            senderAvatar: user?.profile?.profileImageUrl || user?.avatar || null,
            content: messageContent,
            timestamp: msgTimestamp,
            createdAt: msgTimestamp,
            mediaShareMode: groupMessage.mediaShareMode,
            mediaId: groupMessage.mediaId,
            mediaType: groupMessage.mediaType,
            mediaName: groupMessage.mediaName
        };

        if (global.wsClients) {
            const { broadcastToUser } = require('../websocket/broadcast-utils.js');
            const members = (group.members || []).filter(m => m).map(m => typeof m === 'object' ? (m.userId || m.id) : m);
            members.forEach(memberId => {
                if (memberId !== userId) {
                    broadcastToUser(global.wsClients, memberId, 'group.message.received', messageEventData);
                }
            });
        }

        try {
            const members = (group.members || []).filter(m => m).map(m => typeof m === 'object' ? (m.userId || m.id) : m);
            const groupName = group.name || 'Group';
            const recipientCount = members.length - 1;

            console.log(`[ GROUP MESSAGE] Sending notifications to ${recipientCount} member(s)...`);

            if (pushNotificationManager) {
                for (const memberId of members) {
                    if (memberId !== userId) {
                        pushNotificationManager.sendGroupMessageNotification(memberId, {
                            userId: userId,
                            username: user?.username || 'Unknown',
                            displayName: user?.displayName || user?.username || 'Unknown'
                        }, {
                            id: group.id || groupId,
                            groupId: group.id || groupId,
                            name: groupName,
                            groupName: groupName
                        }, messageContent).catch(err =>
                            console.warn(`[ GROUP MESSAGE] Failed to send to ${memberId}: ${err.message}`)
                        );
                    }
                }
            }
            console.log(`[ GROUP MESSAGE] Notification dispatch complete for ${recipientCount} member(s)`);
        } catch (fcmErr) {
            console.error(`[ GROUP MESSAGE] Notification error: ${fcmErr.message}`);
        }

        res.status(201).json({
            _id: dbMessageId,
            id: dbMessageId,
            messageId: dbMessageId,
            groupId: group.id || groupId,
            senderId: userId,
            senderUsername: user?.username || 'Unknown',
            senderAvatar: user?.avatar || null,
            content: messageContent,
            message: messageContent,
            status: 'delivered',
            timestamp: msgTimestamp,
            read: false,
            type: 'text',
            replyToMessageId: replyToMessageId || null,
            replyToUsername: replyToUsername || null,
            replyToText: replyToText || null,
            mediaShareMode: groupMessage.mediaShareMode,
            mediaId: groupMessage.mediaId,
            mediaType: groupMessage.mediaType,
            mediaName: groupMessage.mediaName,
            success: true
        });
    } catch (err) {
        console.error('Send group message error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId/messages', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { limit = 50 } = req.query;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => (typeof m === 'object' && m.userId === userId))
        );
        if (!isMember) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const messages = await dbConnection.collection('groupMessages')
            .find({
                $or: [
                    { groupId: group.id || groupId },
                    { groupId: groupId }
                ]
            })
            .sort({ timestamp: -1, createdAt: -1 })
            .limit(parseInt(limit) || 50)
            .toArray();

        res.json({
            success: true,
            messages: await Promise.all(messages.reverse().map(async (msg) => {
                let senderAvatar = msg.senderAvatar;
                if (!senderAvatar && msg.senderId) {
                    const sender = await dbConnection.collection('users').findOne({ id: msg.senderId }, { projection: { profile: 1, avatar: 1 } });
                    senderAvatar = sender?.profile?.profileImageUrl || sender?.avatar || null;
                }

                let replyUser = msg.replyToUsername || null;
                let replyText = msg.replyToText || null;
                if (msg.replyToMessageId) {
                    const replyId = msg.replyToMessageId;
                    const cleanReplyId = replyId.replace(/^msg_/, '');
                    const msgIdWithPrefix = `msg_${cleanReplyId}`;

                    console.log(`[REPLY LOOKUP] Searching for reply in group ${msg.groupId}: replyId=${replyId}, cleanId=${cleanReplyId}, withPrefix=${msgIdWithPrefix}`);

                    let replyMsg = await dbConnection.collection('groupMessages').findOne({
                        groupId: msg.groupId || group.id || groupId,
                        $or: [
                            { id: replyId },
                            { id: cleanReplyId },
                            { id: msgIdWithPrefix },
                            { messageId: replyId },
                            { messageId: cleanReplyId },
                            { messageId: msgIdWithPrefix }
                        ]
                    });

                    if (!replyMsg) {
                        console.warn(`[REPLY NOT FOUND] Trying without groupId filter. Searching for: ${replyId}`);
                        replyMsg = await dbConnection.collection('groupMessages').findOne({
                            $or: [
                                { id: replyId },
                                { id: cleanReplyId },
                                { id: msgIdWithPrefix },
                                { messageId: replyId },
                                { messageId: cleanReplyId },
                                { messageId: msgIdWithPrefix }
                            ]
                        });
                    }

                    if (replyMsg) {
                        replyUser = replyMsg.senderUsername || replyMsg.senderId || null;
                        replyText = replyMsg.content || replyMsg.message || null;
                        console.log(`[REPLY FOUND] Found reply message: user=${replyUser}, text=${replyText?.substring(0, 50)}`);
                    } else {
                        console.error(`[REPLY NOT FOUND] Could not find message with id: ${replyId} in group ${msg.groupId}`);
                    }
                }

                let senderTags = [];
                let senderIsAdmin = false;
                let senderIsModerator = false;
                let senderRole = 'User';
                let senderDisplayName = msg.senderUsername || 'User';

                if (msg.senderId) {
                    const sender = await dbConnection.collection('users').findOne(
                        { id: msg.senderId },
                        { projection: { profile: 1, tags: 1, role: 1, username: 1 } }
                    );
                    if (sender) {
                        senderTags = (sender.profile?.tags || sender.tags || []).slice(0, 5);
                        senderRole = sender.role || 'User';
                        senderIsAdmin = senderRole === 'ADMIN';
                        senderIsModerator = senderRole === 'MODERATOR';
                        senderDisplayName = sender.profile?.displayName || sender.displayName || sender.username || msg.senderUsername || 'User';
                    }
                }

                return {
                    _id: msg._id?.toString() || msg.id || msg.messageId,
                    id: msg.id || msg.messageId,
                    messageId: msg.messageId || msg.id,
                    groupId: msg.groupId,
                    senderId: msg.senderId,
                    senderUsername: msg.senderUsername,
                    senderAvatar: senderAvatar,
                    content: msg.content || msg.message,
                    message: msg.message || msg.content,
                    status: msg.status || 'delivered',
                    timestamp: typeof msg.timestamp === 'number' ? msg.timestamp : (msg.createdAt ? new Date(msg.createdAt).getTime() : Date.now()),
                    read: msg.isRead || msg.read || false,
                    type: 'text',
                    reactions: formatReactions(msg.reactions),
                    replyId: msg.replyToMessageId || null,
                    replyUser: replyUser,
                    replyText: replyText,
                    replyToMessageId: msg.replyToMessageId || null,
                    replyToUsername: msg.replyToUsername || null,
                    replyToText: msg.replyToText || null,
                    mediaId: msg.mediaId || null,
                    mediaType: msg.mediaType || null,
                    mediaName: msg.mediaName || null,
                    mediaShareMode: msg.mediaShareMode || "protected",
                    senderTags: senderTags,
                    senderIsAdmin: senderIsAdmin,
                    senderIsModerator: senderIsModerator,
                    senderRole: senderRole,
                    senderDisplayName: senderDisplayName
                };
            })),
            total: messages.length
        });
    } catch (err) {
        console.error('Get group messages error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/clear-history-vote', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => (typeof m === 'object' && m.userId === userId))
        );
        if (!isMember) {
            return res.status(403).json({ error: 'Only group members can vote' });
        }

        const existingVote = await dbConnection.collection('clearHistoryVotes').findOne({
            groupId: groupId,
            status: { $in: ['active', 'pending'] }
        });

        if (existingVote) {
            return res.status(400).json({
                error: 'A clear history vote is already in progress',
                voteId: existingVote.id
            });
        }

        const voteId = uuidv4();
        const vote = {
            id: voteId,
            groupId: groupId,
            groupName: group.name,
            initiatorId: userId,
            initiatorUsername: (await dbConnection.collection('users').findOne({ id: userId }, { projection: { username: 1 } }))?.username || 'Member',
            status: 'active',
            votes: { [userId]: 'yes' },
            voteCount: { yes: 1, no: 0 },
            memberCount: group.members.length,
            requiredVotes: Math.max(1, Math.floor(group.members.length / 2)),
            createdAt: new Date().toISOString(),
            expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
        };

        await dbConnection.collection('clearHistoryVotes').insertOne(vote);

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${groupId}`).emit('group.clearHistory.voteStarted', {
                voteId: voteId,
                groupId: groupId,
                initiatorUsername: vote.initiatorUsername,
                memberCount: vote.memberCount,
                requiredVotes: vote.requiredVotes,
                message: `${vote.initiatorUsername} started a vote to clear group history. ${vote.requiredVotes} votes needed.`,
                timestamp: new Date().toISOString()
            });
        }

        console.log(`[OK] Clear history vote initiated: ${voteId} in group ${groupId} (needs ${vote.requiredVotes} votes)`);

        res.json({
            success: true,
            voteId: voteId,
            message: `Vote started. ${vote.requiredVotes} votes required to clear history.`,
            requiredVotes: vote.requiredVotes,
            memberCount: vote.memberCount
        });
    } catch (err) {
        console.error('Clear history vote initiation error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/clear-history-vote/:voteId/vote', verifyToken, async (req, res) => {
    try {
        const { groupId, voteId } = req.params;
        const { vote: voteChoice } = req.body;
        const userId = req.user.userId;

        if (!['yes', 'no'].includes(voteChoice)) {
            return res.status(400).json({ error: 'Vote must be "yes" or "no"' });
        }

        const vote = await dbConnection.collection('clearHistoryVotes').findOne({ id: voteId });
        if (!vote) {
            return res.status(404).json({ error: 'Vote not found' });
        }

        if (vote.status !== 'active') {
            return res.status(400).json({ error: 'This vote is no longer active' });
        }

        if (new Date() > new Date(vote.expiresAt)) {
            await dbConnection.collection('clearHistoryVotes').updateOne({ id: voteId }, { $set: { status: 'expired' } });
            return res.status(400).json({ error: 'This vote has expired' });
        }

        if (vote.votes[userId]) {
            return res.status(400).json({ error: 'You have already voted on this issue' });
        }

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        const isMember = Array.isArray(group.members) && (
            group.members.includes(userId) ||
            group.members.some(m => (typeof m === 'object' && m.userId === userId))
        );
        if (!isMember) {
            return res.status(403).json({ error: 'Only group members can vote' });
        }

        const memberUsername = (await dbConnection.collection('users').findOne({ id: userId }, { projection: { username: 1 } }))?.username || 'Member';
        const newVotes = { ...vote.votes, [userId]: voteChoice };
        const newVoteCount = {
            yes: Object.values(newVotes).filter(v => v === 'yes').length,
            no: Object.values(newVotes).filter(v => v === 'no').length
        };

        await dbConnection.collection('clearHistoryVotes').updateOne(
            { id: voteId },
            {
                $set: {
                    votes: newVotes,
                    voteCount: newVoteCount
                }
            }
        );

        let voteResult = null;
        if (newVoteCount.yes >= vote.requiredVotes) {
            voteResult = 'passed';
            await dbConnection.collection('clearHistoryVotes').updateOne({ id: voteId }, { $set: { status: 'passed' } });

            const deleteResult = await dbConnection.collection('groupMessages').deleteMany({ groupId: groupId });

            await dbConnection.collection('groups').updateOne(
                { $or: [{ id: groupId }, { groupId: groupId }] },
                { $set: { historyClearedAt: new Date().toISOString(), updatedAt: new Date().toISOString() } }
            );

            const io = require('../websocket/socket-io-server').io;
            if (io) {
                io.to(`group:${groupId}`).emit('group.clearHistory.passed', {
                    voteId: voteId,
                    groupId: groupId,
                    title: 'Clear History Passed',
                    body: `Clear history vote passed! ${deleteResult.deletedCount} messages removed.`,
                    deletedCount: deleteResult.deletedCount,
                    timestamp: new Date().toISOString()
                });
            }

            console.log(`[OK] Clear history vote PASSED: ${voteId} - cleared ${deleteResult.deletedCount} messages`);
            await logEvent('group_history_voted_clear', `Group ${groupId} cleared chat history via democratic vote (${newVoteCount.yes}/${vote.memberCount} votes)`, userId);
        } else if (newVoteCount.no >= vote.requiredVotes || (newVoteCount.no + newVoteCount.yes >= vote.memberCount && newVoteCount.yes < vote.requiredVotes)) {
            voteResult = 'failed';
            await dbConnection.collection('clearHistoryVotes').updateOne({ id: voteId }, { $set: { status: 'failed' } });

            const io = require('../websocket/socket-io-server').io;
            if (io) {
                io.to(`group:${groupId}`).emit('group.clearHistory.failed', {
                    voteId: voteId,
                    groupId: groupId,
                    yesVotes: newVoteCount.yes,
                    noVotes: newVoteCount.no,
                    message: `Clear history vote failed. (${newVoteCount.yes} yes, ${newVoteCount.no} no)`,
                    timestamp: new Date().toISOString()
                });
            }

            console.log(`[INFO] Clear history vote FAILED: ${voteId} - insufficient votes`);
        } else {
            const io = require('../websocket/socket-io-server').io;
            if (io) {
                io.to(`group:${groupId}`).emit('group.clearHistory.voteUpdated', {
                    voteId: voteId,
                    groupId: groupId,
                    yesVotes: newVoteCount.yes,
                    noVotes: newVoteCount.no,
                    requiredVotes: vote.requiredVotes,
                    voterUsername: memberUsername,
                    voterChoice: voteChoice,
                    message: `${memberUsername} voted to ${voteChoice === 'yes' ? 'clear' : 'keep'} history. (${newVoteCount.yes}/${vote.requiredVotes} votes needed)`,
                    timestamp: new Date().toISOString()
                });
            }
        }

        res.json({
            success: true,
            voteRecorded: true,
            yesVotes: newVoteCount.yes,
            noVotes: newVoteCount.no,
            requiredVotes: vote.requiredVotes,
            result: voteResult,
            message: voteResult ? `Vote ${voteResult}!` : 'Vote recorded. Vote in progress...'
        });
    } catch (err) {
        console.error('Clear history vote error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId/messages', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isClearAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isClearAdmin && req.user.role !== 'ADMIN') {
            return res.status(403).json({ error: 'Only group admins can clear chat history' });
        }

        const result = await dbConnection.collection('groupMessages').deleteMany({
            groupId: groupId
        });

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            { $set: { historyClearedAt: new Date().toISOString(), updatedAt: new Date().toISOString() } }
        );

        await logEvent('group_history_deleted', `Group history for ${groupId} cleared by ${userId}`, userId);

        if (global.socketIoServer) {
            global.socketIoServer.emit('groupHistoryDeleted', {
                groupId: groupId,
                deletedBy: userId,
                title: 'Clear History Passed',
                timestamp: new Date().getTime()
            });
        }

        res.json({
            success: true,
            deletedCount: result.deletedCount
        });
    } catch (err) {
        console.error('Delete group history error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/groups/:groupId/admins/:memberId', verifyToken, async (req, res) => {
    try {
        const { groupId, memberId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isPromoteAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isPromoteAdmin) {
            return res.status(403).json({ error: 'Only group admins can manage group admins' });
        }

        const member = group.members.find(m => m.userId === memberId);
        if (!member) {
            return res.status(404).json({ error: 'Member not found in group' });
        }

        const allAdminIds = new Set([
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => typeof a === 'string') : []),
            ...(Array.isArray(group.admins) ? group.admins.filter(a => typeof a === 'string') : []),
            group.createdBy
        ].filter(Boolean));
        if (allAdminIds.has(memberId)) {
            return res.status(409).json({ error: 'Member is already a group admin' });
        }

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $addToSet: { adminIds: memberId, admins: memberId },
                $set: { updatedAt: new Date().toISOString() }
            }
        );

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }], 'members.userId': memberId },
            { $set: { 'members.$.isAdmin': true } }
        );

        await logEvent('group_admin_promoted', `Member ${memberId} promoted to admin in group ${group.name} (GROUP-ONLY, not system admin)`, userId);

        const promotedUser = await dbConnection.collection('users').findOne({ id: memberId }, { projection: { username: 1, displayName: 1 } });
        const promotedUsername = promotedUser?.username || 'Member';
        const promotedDisplayName = promotedUser?.displayName || promotedUsername;
        if (global.socketIoServer) {
            global.socketIoServer.to(`group:${groupId}`).emit('group.member.promoted', {
                groupId: groupId,
                userId: memberId,
                username: promotedUsername,
                promotedBy: userId,
                timestamp: new Date().toISOString()
            });
        }

        try {
            const groupName = group.name || 'Group';
            await sendPushNotification(memberId, {
                notification: {
                    title: 'Promoted to Admin',
                    body: `You were promoted to admin in "${groupName}"`,
                    sound: 'default'
                },
                data: {
                    type: 'group.member.promoted',
                    groupId: groupId,
                    groupName: groupName,
                    userId: memberId,
                    username: promotedDisplayName,
                    promotedBy: userId
                }
            });
        } catch (fcmErr) {
            console.error(`[FCM] Group member promoted notification error: ${fcmErr.message}`);
        }

        res.json({
            success: true,
            message: 'Member promoted to group admin (group-only privilege)',
            groupId: group.id || groupId,
            memberId: memberId,
            isGroupAdminOnly: true,
            isSystemAdminUnaffected: true
        });
    } catch (err) {
        console.error('Promote admin error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId/admins/:memberId', verifyToken, async (req, res) => {
    try {
        const { groupId, memberId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isDemoteAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isDemoteAdmin) {
            return res.status(403).json({ error: 'Only group admins can manage group admins' });
        }

        const allAdminIds = new Set([
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => typeof a === 'string') : []),
            ...(Array.isArray(group.admins) ? group.admins.filter(a => typeof a === 'string') : []),
            group.createdBy
        ].filter(Boolean));
        const allObjectAdmins = [
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => a && typeof a === 'object') : []),
            ...(Array.isArray(group.admins) ? group.admins.filter(a => a && typeof a === 'object') : [])
        ];
        const isTargetAdmin = allAdminIds.has(memberId) || allObjectAdmins.some(a => a.userId === memberId || a.id === memberId);
        if (!isTargetAdmin) {
            return res.status(400).json({ error: 'Target member is not a group admin' });
        }

        const isRequesterCreator = group.createdBy === userId;
        const isDemotingCreator = group.createdBy === memberId;
        const isDemotingSelf = userId === memberId;

        if (isDemotingSelf) {
            return res.status(400).json({ error: 'Cannot demote yourself' });
        }

        if (isDemotingCreator) {
            return res.status(403).json({ error: 'Cannot demote the group creator' });
        }

        const adminCount = allAdminIds.size;
        if (adminCount <= 1 && isTargetAdmin) {
            return res.status(400).json({ error: 'Cannot remove last group admin' });
        }

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $pull: {
                    adminIds: { $in: [memberId, { userId: memberId }] },
                    admins: { $in: [memberId, { userId: memberId }] }
                },
                $set: { updatedAt: new Date().toISOString() }
            }
        );

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }], 'members.userId': memberId },
            { $set: { 'members.$.isAdmin': false } }
        );

        await logEvent('group_admin_demoted', `Member ${memberId} demoted by admin ${userId} in group ${group.name}`, userId);

        const demotedUser = await dbConnection.collection('users').findOne({ id: memberId }, { projection: { username: 1 } });
        const demotedUsername = demotedUser?.username || 'Member';
        if (global.socketIoServer) {
            global.socketIoServer.to(`group:${groupId}`).emit('group.member.demoted', {
                groupId: groupId,
                userId: memberId,
                username: demotedUsername,
                demotedBy: userId,
                timestamp: new Date().toISOString()
            });
        }

        res.json({
            success: true,
            message: 'Admin privileges removed (group-only)',
            groupId: group.id || groupId,
            memberId: memberId,
            isGroupAdminOnly: true,
            isSystemAdminUnaffected: true
        });
    } catch (err) {
        console.error('Demote admin error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/groups/:groupId/picture', verifyToken, upload.single('picture'), async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        console.log(`[DEBUG] Picture upload request: groupId=${groupId}, userId=${userId}, hasFile=${!!req.file}`);

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            console.error(`[ERROR] Group not found: ${groupId}`);
            return res.status(404).json({ error: 'Group not found' });
        }

        const isPictureUploadAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;

        if (!isPictureUploadAdmin) {
            console.error(`[ERROR] User ${userId} is not admin of group ${groupId}. group.adminIds:`, group.adminIds, 'group.admins:', group.admins);
            return res.status(403).json({ error: 'Only group admins can upload group pictures' });
        }

        if (!req.file) {
            console.error(`[ERROR] No file provided in picture upload for group ${groupId}`);
            return res.status(400).json({ error: 'No image file provided' });
        }

        console.log(`[DEBUG] Uploading file: ${req.file.originalname} (${req.file.size} bytes)`);

        let fileBuffer = req.file.buffer;
        if (!fileBuffer && req.file.path) {
            console.log(`[GROUP PICTURE] Reading file from disk: ${req.file.path}`);
            fileBuffer = fs.readFileSync(req.file.path);
        }

        if (!fileBuffer) {
            console.error(`[GROUP PICTURE] ERROR - No file data available. Path: ${req.file.path}`);
            return res.status(500).json({ error: 'File data not available' });
        }

        const gridfs = initializeGridFSHandler();
        const fileInfo = await gridfs.uploadFile(
            fileBuffer,
            `group_${groupId}_${Date.now()}`,
            {
                groupId: groupId,
                uploadedBy: userId,
                originalName: req.file.originalname
            }
        );

        console.log(`[DEBUG] File uploaded to GridFS: ${fileInfo.fileId}`);

        if (group.profilePictureFileId) {
            try {
                await gridfs.deleteFile(group.profilePictureFileId);
                console.log(`[OK] Old group picture deleted: ${group.profilePictureFileId}`);
            } catch (delErr) {
                console.warn('[WARN] Could not delete old group picture:', delErr.message);
            }
        }

        const updateResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $set: {
                    profilePictureFileId: fileInfo.fileId.toString(),
                    profilePictureUrl: `/api/groups/${groupId}/picture`,
                    profilePictureUpdatedAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString()
                }
            }
        );

        console.log(`[DEBUG] Group updated. Matched: ${updateResult.matchedCount}, Modified: ${updateResult.modifiedCount}`);

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${groupId}`).emit('group:pictureUpdated', {
                groupId: groupId,
                pictureUrl: `/api/groups/${groupId}/picture`,
                updatedBy: userId,
                updatedAt: new Date().toISOString()
            });
        }

        console.log(`[OK] Group picture uploaded: ${groupId}`);
        res.json({
            success: true,
            message: 'Group picture updated successfully',
            pictureUrl: `/api/groups/${groupId}/picture`,
            fileId: fileInfo.fileId.toString()
        });

    } catch (err) {
        console.error('Upload group picture error:', err);
        console.error('Stack trace:', err.stack);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/groups/:groupId/picture', async (req, res) => {
    try {
        const { groupId } = req.params;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group || !group.profilePictureFileId) {
            return res.status(404).json({ error: 'Group picture not found' });
        }

        const gridfs = initializeGridFSHandler();
        const fileBuffer = await gridfs.downloadFile(group.profilePictureFileId);

        res.setHeader('Cache-Control', 'public, max-age=604800');
        res.setHeader('Content-Type', 'image/jpeg');
        res.send(fileBuffer);

        console.log(`[OK] Group picture downloaded: ${groupId}`);

    } catch (err) {
        console.error('Download group picture error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/groups/:groupId/name', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { name } = req.body;
        const userId = req.user.userId;

        if (!name || name.trim().length === 0) {
            return res.status(400).json({ error: 'Group name cannot be empty' });
        }
        if (name.length > 100) {
            return res.status(400).json({ error: 'Group name too long (max 100 characters)' });
        }

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isNameAdmin =
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isNameAdmin) {
            return res.status(403).json({ error: 'Only group admins can change group name' });
        }

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $set: {
                    name: name.trim(),
                    groupName: name.trim(),
                    updatedAt: new Date().toISOString(),
                    updatedBy: userId
                }
            }
        );

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${groupId}`).emit('group:nameUpdated', {
                groupId: groupId,
                name: name.trim(),
                updatedBy: userId,
                updatedAt: new Date().toISOString()
            });
        }

        console.log(`[OK] Group name updated: ${groupId} -> ${name}`);
        res.json({
            success: true,
            message: 'Group name updated successfully',
            name: name.trim(),
            groupId: groupId
        });

    } catch (err) {
        console.error('Update group name error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/groups/:groupId', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { name, description, isPrivate } = req.body;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({
            $or: [{ id: groupId }, { groupId: groupId }]
        });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const members = Array.isArray(group.members) ? group.members : [];
        const isMember = members.some(m => m && (typeof m === 'object' ? (m.userId || m.id) : m) === userId);

        const adminIds = Array.isArray(group.adminIds) ? group.adminIds : (Array.isArray(group.admins) ? group.admins : []);
        const isAdmin = adminIds.some(a => a && (typeof a === 'object' ? (a.userId || a.id) : a) === userId);
        const isOwner = group.createdBy === userId || group.ownerId === userId;

        if (!isMember && !isAdmin && !isOwner) {
            return res.status(403).json({ error: 'Not a member of this group' });
        }

        const hasAdminRights = isAdmin || isOwner;

        const updateObj = { updatedAt: new Date() };
        const eventObj = { groupId: groupId, updatedBy: userId, updatedAt: new Date().getTime() };

        if (name !== undefined && name !== group.name && name !== group.groupName) {
            if (!hasAdminRights) {
                return res.status(403).json({ error: 'Only group admins can change the group name' });
            }
            if (!name || name.trim().length === 0) {
                return res.status(400).json({ error: 'Group name cannot be empty' });
            }
            if (name.length > 100) {
                return res.status(400).json({ error: 'Group name too long (max 100 characters)' });
            }
            updateObj.name = name.trim();
            updateObj.groupName = name.trim();
            eventObj.name = name.trim();
        }

        if (description !== undefined && description !== group.description) {
            if (description && description.length > 500) {
                return res.status(400).json({ error: 'Group description too long (max 500 characters)' });
            }
            updateObj.description = description ? description.trim() : '';
            eventObj.description = updateObj.description;
        }

        if (isPrivate !== undefined && isPrivate !== group.isPrivate) {
            if (!hasAdminRights) {
                return res.status(403).json({ error: 'Only group admins can change group privacy settings' });
            }
            updateObj.isPrivate = !!isPrivate;
            eventObj.isPrivate = !!isPrivate;
        }

        if (Object.keys(updateObj).length <= 1) {
            return res.json({
                success: true,
                message: 'No changes detected',
                groupId: groupId
            });
        }

        const updateResult = await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            { $set: updateObj }
        );

        if (updateResult.matchedCount === 0) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            const broadcastObj = {
                groupId: groupId,
                id: groupId,
                name: name !== undefined ? name.trim() : group.name,
                description: description !== undefined ? description.trim() : group.description,
                isPrivate: isPrivate !== undefined ? isPrivate : group.isPrivate,
                updatedBy: userId,
                updatedAt: new Date().getTime(),
                ...eventObj
            };
            io.to(`group:${groupId}`).emit('group:updated', broadcastObj);
            io.to(`group:${groupId}`).emit('group.updated', broadcastObj);
        }

        try {
            const memberIds = (group.members || [])
                .filter(m => m != null)
                .map(m => typeof m === 'object' ? (m.userId || m.id) : m)
                .filter(Boolean)
                .filter(id => id !== userId);

            const updater = await dbConnection.collection('users').findOne({ id: userId }, { projection: { username: 1, displayName: 1 } });
            const updaterName = updater?.displayName || updater?.username || 'Someone';
            const groupNameFinal = broadcastObj.name || group.name;

            for (const memberId of memberIds) {
                await sendPushNotification(memberId, {
                    notification: {
                        title: 'Group Updated',
                        body: `${updaterName} updated "${groupNameFinal}"`,
                        sound: 'default'
                    },
                    data: {
                        type: 'groupUpdated',
                        groupId: groupId,
                        groupName: groupNameFinal,
                        updatedBy: userId,
                        updatedByName: updaterName
                    }
                });
            }
        } catch (fcmErr) {
            console.error(`[FCM] Group update notification error: ${fcmErr.message}`);
        }

        console.log(`[OK] Group updated: ${groupId}`, updateObj);
        res.json({
            success: true,
            message: 'Group updated successfully',
            groupId: groupId,
            ...eventObj
        });

    } catch (err) {
        console.error('Update group error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/groups/:groupId/picture', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;

        const group = await dbConnection.collection('groups').findOne({ $or: [{ id: groupId }, { groupId: groupId }] });
        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isPicDeleteAdmin =
            (Array.isArray(group.adminIds) && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            (Array.isArray(group.admins) && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
            group.createdBy === userId;
        if (!isPicDeleteAdmin) {
            return res.status(403).json({ error: 'Only group admins can delete group pictures' });
        }

        if (group.profilePictureFileId) {
            const gridfs = initializeGridFSHandler();
            await gridfs.deleteFile(group.profilePictureFileId);
            console.log(`[OK] Group picture deleted from GridFS: ${group.profilePictureFileId}`);
        }

        await dbConnection.collection('groups').updateOne(
            { $or: [{ id: groupId }, { groupId: groupId }] },
            {
                $unset: {
                    profilePictureFileId: 1,
                    profilePictureUrl: 1,
                    profilePictureUpdatedAt: 1
                },
                $set: { updatedAt: new Date().toISOString() }
            }
        );

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.to(`group:${groupId}`).emit('group:pictureRemoved', {
                groupId: groupId,
                removedBy: userId,
                removedAt: new Date().toISOString()
            });
        }

        console.log(`[OK] Group picture removed: ${groupId}`);
        res.json({
            success: true,
            message: 'Group picture removed successfully'
        });

    } catch (err) {
        console.error('Delete group picture error:', err);
        res.status(500).json({ error: err.message });
    }
});

const APK_FILE_NAME = 'FreeTimeApp.apk';
const APP_VERSION = {
    minimumVersion: '1.0'
};

function getApkPath() {
    return path.join(__dirname, '..', 'update', APK_FILE_NAME);
}

function apkExists() {
    try {
        return fs.existsSync(getApkPath());
    } catch (e) {
        return false;
    }
}

async function getActiveUpdate() {
    try {
        return await dbConnection.collection('appUpdates').findOne(
            { isActive: true },
            { sort: { launchedAt: -1 } }
        );
    } catch (e) {
        return null;
    }
}

async function getEffectiveVersion() {
    const adminUpdate = await getActiveUpdate();
    if (adminUpdate && apkExists()) {
        return {
            latestVersion: adminUpdate.version,
            latestVersionCode: adminUpdate.versionCode,
            minimumVersion: APP_VERSION.minimumVersion,
            forceUpdate: adminUpdate.forceUpdate,
            releaseNotes: adminUpdate.releaseNotes
        };
    }
    return {
        latestVersion: APP_VERSION.minimumVersion,
        latestVersionCode: 0,
        minimumVersion: APP_VERSION.minimumVersion,
        forceUpdate: false,
        releaseNotes: ''
    };
}

function buildDownloadUrl(req) {
    const host = req.get('host') || 'example.com';
    const protocol = req.protocol || 'https';
    return `${protocol}://${host}/update/${APK_FILE_NAME}`;
}

app.get('/api/app/version-info', async (req, res) => {
    try {
        const adminUpdate = await getActiveUpdate();
        const hasApk = apkExists();
        let effective;
        if (adminUpdate && hasApk) {
            effective = {
                latestVersion: adminUpdate.version,
                latestVersionCode: adminUpdate.versionCode,
                minimumVersion: APP_VERSION.minimumVersion,
                forceUpdate: adminUpdate.forceUpdate,
                releaseNotes: adminUpdate.releaseNotes
            };
        } else {
            effective = {
                latestVersion: APP_VERSION.minimumVersion,
                latestVersionCode: 0,
                minimumVersion: APP_VERSION.minimumVersion,
                forceUpdate: false,
                releaseNotes: ''
            };
        }
        res.json({
            latestVersion: effective.latestVersion,
            latestVersionCode: effective.latestVersionCode,
            minimumVersion: effective.minimumVersion,
            forceUpdate: effective.forceUpdate,
            releaseNotes: effective.releaseNotes,
            downloadUrl: hasApk ? buildDownloadUrl(req) : '',
            updateId: adminUpdate ? adminUpdate.id : null,
            launchedAt: adminUpdate ? adminUpdate.launchedAt : null
        });
    } catch (e) {
        console.error('[VERSION_INFO_ERROR]', e.message);
        res.json({
            latestVersion: APP_VERSION.minimumVersion,
            latestVersionCode: 0,
            minimumVersion: APP_VERSION.minimumVersion,
            forceUpdate: false,
            releaseNotes: '',
            downloadUrl: '',
            updateId: null,
            launchedAt: null
        });
    }
});

app.post('/api/app/version-check', verifyToken, async (req, res) => {
    try {
        const { clientVersion, clientVersionCode, clientType } = req.body;
        if (!clientVersion || !clientType) {
            return res.status(400).json({ error: 'Missing required fields: clientVersion, clientType' });
        }
        const effective = await getEffectiveVersion();
        const currentCode = parseInt(clientVersionCode) || 0;
        const needsUpdate = currentCode < effective.latestVersionCode;
        const compatible = currentCode >= parseInt(effective.minimumVersion.replace(/\D/g, '')) || 0;
        res.json({
            compatible: compatible !== false,
            latestVersion: effective.latestVersion,
            latestVersionCode: effective.latestVersionCode,
            shouldUpdate: needsUpdate,
            forceUpdate: effective.forceUpdate,
            downloadUrl: apkExists() ? buildDownloadUrl(req) : '',
            releaseNotes: effective.releaseNotes,
            message: needsUpdate ? 'Update available' : 'Version OK'
        });
    } catch (e) {
        console.error('[VERSION_CHECK_ERROR]', e.message);
        res.json({ compatible: true, shouldUpdate: false, message: 'Version check unavailable' });
    }
});

app.post('/api/admin/update/launch', verifyToken, async (req, res) => {
    try {
        if (!req.user || req.user.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        const { version, versionCode, releaseNotes, forceUpdate } = req.body;
        if (!version || !releaseNotes) {
            return res.status(400).json({ error: 'version and releaseNotes are required' });
        }

        const parsedVersionCode = parseInt(versionCode);
        if (!versionCode || isNaN(parsedVersionCode) || parsedVersionCode < 1) {
            return res.status(400).json({ error: 'versionCode must be a positive integer' });
        }

        if (!apkExists()) {
            return res.status(400).json({
                error: `No APK found in update/. Drop the file as "${APK_FILE_NAME}" first, then launch the update.`
            });
        }

        await dbConnection.collection('appUpdates').updateMany(
            { isActive: true },
            { $set: { isActive: false, deactivatedAt: new Date().toISOString() } }
        );

        const updateRecord = {
            id: `update_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            version,
            versionCode: parseInt(versionCode) || 1,
            releaseNotes,
            forceUpdate: !!forceUpdate,
            launchedAt: new Date().toISOString(),
            isActive: true,
            createdBy: req.user.userId || req.user.username,
            acknowledgedBy: []
        };

        await dbConnection.collection('appUpdates').insertOne(updateRecord);

        const io = require('../websocket/socket-io-server').io;
        if (io) {
            io.emit('app.update.launched', {
                id: updateRecord.id,
                version: updateRecord.version,
                versionCode: updateRecord.versionCode,
                releaseNotes: updateRecord.releaseNotes,
                forceUpdate: updateRecord.forceUpdate,
                launchedAt: updateRecord.launchedAt
            });
        }

        await logEvent('app_update_launched', `App update ${version} launched by ${req.user.username || req.user.userId}`, req.user.userId);
        console.log(`[OK] App update launched: v${version}`);

        res.json({ success: true, update: updateRecord });
    } catch (err) {
        console.error('Launch update error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/update/info', verifyToken, async (req, res) => {
    try {
        if (!req.user || req.user.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        const update = await dbConnection.collection('appUpdates').findOne(
            { isActive: true },
            { sort: { launchedAt: -1 } }
        );
        res.json({ update: update || null });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/admin/update/:id', verifyToken, async (req, res) => {
    try {
        if (!req.user || req.user.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        const { releaseNotes, forceUpdate } = req.body;
        const existing = await dbConnection.collection('appUpdates').findOne({ id: req.params.id });
        if (!existing) {
            return res.status(404).json({ error: 'Update not found' });
        }
        await dbConnection.collection('appUpdates').updateOne(
            { id: req.params.id },
            { $set: { releaseNotes, forceUpdate: !!forceUpdate, updatedAt: new Date().toISOString() } }
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/api/admin/update/:id', verifyToken, async (req, res) => {
    try {
        if (!req.user || req.user.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        await dbConnection.collection('appUpdates').updateOne(
            { id: req.params.id },
            { $set: { isActive: false, deactivatedAt: new Date().toISOString() } }
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/admin/update/stats', verifyToken, async (req, res) => {
    try {
        if (!req.user || req.user.role !== 'ADMIN') return res.status(403).json({ error: 'Admin access required' });
        const update = await dbConnection.collection('appUpdates').findOne(
            { isActive: true },
            { sort: { launchedAt: -1 } }
        );
        if (!update) {
            return res.json({ update: null, totalUsers: 0, updatedCount: 0, notUpdatedCount: 0, users: [] });
        }

        const allUsers = await dbConnection.collection('users').find({}, { projection: { _id: 1, userId: 1, username: 1, lastAppVersionCode: 1, lastActiveAt: 1 } }).toArray();
        const totalUsers = allUsers.length;
        const updatedUsers = update.acknowledgedBy || [];
        const updatedCount = updatedUsers.length;
        const notUpdatedCount = totalUsers - updatedCount;

        const users = allUsers.map(u => {
            const uid = u.userId || u._id?.toString() || '';
            return {
                id: uid,
                username: u.username,
                hasUpdated: updatedUsers.includes(uid),
                lastVersionCode: u.lastAppVersionCode || 0,
                lastActive: u.lastActiveAt || null
            };
        });

        res.json({ update, totalUsers, updatedCount, notUpdatedCount, users });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/app/update/acknowledge', verifyToken, async (req, res) => {
    try {
        const userId = req.user.userId;
        const { updateId, versionCode } = req.body;

        if (updateId) {
            await dbConnection.collection('appUpdates').updateOne(
                { id: updateId },
                { $addToSet: { acknowledgedBy: userId } }
            );
        }

        if (versionCode) {
            await dbConnection.collection('users').updateOne(
                { $or: [{ id: userId }, { userId: userId }] },
                { $set: { lastAppVersionCode: parseInt(versionCode), lastAppVersionCheckedAt: new Date().toISOString() } }
            );
        }

        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/app/latest-update', async (req, res) => {
    try {
        const update = await dbConnection.collection('appUpdates').findOne(
            { isActive: true },
            { sort: { launchedAt: -1 } }
        );
        if (update) {
            const { acknowledgedBy, ...safeUpdate } = update;
            res.json({ update: safeUpdate });
        } else {
            res.json({ update: null });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

process.on('uncaughtException', (err) => {
    console.error('[ERROR] Uncaught Exception:', err);
    logEvent('error', `Uncaught exception: ${err.message}`);
    gracefulShutdown('uncaughtException');
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('[ERROR] Unhandled Rejection at:', promise, 'reason:', reason);
    logEvent('error', `Unhandled rejection: ${reason}`);
});
