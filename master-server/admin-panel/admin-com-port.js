const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');

class AdminComPort {
    constructor(config = {}) {
        this.adminSecret = process.env.ADMIN_SECRET || config.adminSecret || this.generateSecret();
        this.adminToken = process.env.ADMIN_TOKEN || config.adminToken || this.generateToken();
        this.jwtSecret = process.env.JWT_SECRET || config.jwtSecret || null;
        this.adminId = config.adminId || 'default-admin';
        this.timestampWindowSeconds = config.timestampWindowSeconds || 300;
        this.rateLimitWindow = config.rateLimitWindow || 60000;
        this.rateLimitRequests = config.rateLimitRequests || 100;

        this.adminRequests = new Map();

        this.auditLog = [];
        this.maxAuditLogs = config.maxAuditLogs || 10000;
        this.auditLogPath = config.auditLogPath || path.join(__dirname, '../logs/admin-communication.log');

        this.loadAuditLogs();

        console.log('[ADMIN-COM] Admin Communication Port initialized');
        console.log(`[ADMIN-COM] Admin ID: ${this.adminId}`);
        console.log(`[ADMIN-COM] Timestamp window: ${this.timestampWindowSeconds}s`);
        console.log(`[ADMIN-COM] Rate limit: ${this.rateLimitRequests} req/${this.rateLimitWindow}ms`);
        console.log(`[ADMIN-COM] Admin Token: ${this.adminToken}`);
        console.log(`[ADMIN-COM] Admin Secret: ${this.adminSecret.substring(0, 20)}...`);
    }

    generateSecret() {
        return crypto.randomBytes(32).toString('base64');
    }

    generateToken() {
        return crypto.randomBytes(24).toString('hex');
    }

    signRequest(method, endpoint, body = '', timestamp = Date.now()) {
        const data = `${method}:${endpoint}:${body}:${timestamp}`;
        const sig = crypto
            .createHmac('sha256', this.adminSecret)
            .update(data)
            .digest('hex');

        return { signature: sig, timestamp };
    }

    verifySignature(method, endpoint, body, signature, timestamp) {
        // reject signatures reused outside this window
        const now = Date.now();
        const timeDiff = Math.abs(now - timestamp);
        const timeDiffSeconds = timeDiff / 1000;

        if (timeDiffSeconds > this.timestampWindowSeconds) {
            return {
                valid: false,
                reason: `Timestamp expired: ${timeDiffSeconds}s > ${this.timestampWindowSeconds}s`
            };
        }

        const expectedData = `${method}:${endpoint}:${body}:${timestamp}`;
        const expectedSignature = crypto
            .createHmac('sha256', this.adminSecret)
            .update(expectedData)
            .digest('hex');
        const isValid = signature === expectedSignature;

        if (!isValid) {
            console.log(`[ADMIN-COM] Signature mismatch:
    Received: ${signature}
    Expected: ${expectedSignature}
    Message:  ${expectedData}
    Secret:   ${this.adminSecret}`);
        }

        return {
            valid: isValid,
            reason: isValid ? 'Valid' : 'Invalid signature'
        };
    }

    checkRateLimit(adminId) {
        const now = Date.now();

        if (!this.adminRequests.has(adminId)) {
            this.adminRequests.set(adminId, []);
        }

        const requests = this.adminRequests.get(adminId);

        const validRequests = requests.filter(req => now - req.timestamp < this.rateLimitWindow);
        this.adminRequests.set(adminId, validRequests);

        if (validRequests.length >= this.rateLimitRequests) {
            return {
                allowed: false,
                remaining: 0,
                resetTime: validRequests[0].timestamp + this.rateLimitWindow
            };
        }

        validRequests.push({ timestamp: now });

        return {
            allowed: true,
            remaining: this.rateLimitRequests - validRequests.length,
            resetTime: now + this.rateLimitWindow
        };
    }

    logAdminAction(action, details, status = 'success', error = null) {
        const logEntry = {
            timestamp: new Date().toISOString(),
            adminId: this.adminId,
            action,
            details,
            status,
            error: error ? error.toString() : null,
            requestId: crypto.randomBytes(8).toString('hex')
        };

        this.auditLog.push(logEntry);

        if (this.auditLog.length > this.maxAuditLogs) {
            this.auditLog = this.auditLog.slice(-this.maxAuditLogs);
        }

        this.writeAuditLog(logEntry);

        console.log(`[AUDIT] ${action}: ${status}`, logEntry.requestId);

        return logEntry.requestId;
    }

    writeAuditLog(entry) {
        try {
            const logDir = path.dirname(this.auditLogPath);
            if (!fs.existsSync(logDir)) {
                fs.mkdirSync(logDir, { recursive: true });
            }

            const line = JSON.stringify(entry) + '\n';
            fs.appendFileSync(this.auditLogPath, line);
        } catch (error) {
            console.error('[AUDIT] Failed to write log:', error);
        }
    }

    loadAuditLogs() {
        try {
            if (fs.existsSync(this.auditLogPath)) {
                const content = fs.readFileSync(this.auditLogPath, 'utf-8');
                const lines = content.split('\n').filter(line => line.trim());
                this.auditLog = lines.map(line => {
                    try {
                        return JSON.parse(line);
                    } catch {
                        return null;
                    }
                }).filter(Boolean);
            }
        } catch (error) {
            console.warn('[AUDIT] Failed to load audit logs:', error.message);
        }
    }

    getAuditLogs(limit = 100, filter = {}) {
        let logs = [...this.auditLog];

        if (filter.status) {
            logs = logs.filter(l => l.status === filter.status);
        }
        if (filter.action) {
            logs = logs.filter(l => l.action === filter.action);
        }
        if (filter.startDate) {
            const start = new Date(filter.startDate);
            logs = logs.filter(l => new Date(l.timestamp) >= start);
        }

        return logs.reverse().slice(0, limit);
    }

    middleware() {
        return (req, res, next) => {
            const authHeader = req.headers['x-admin-token'];
            const signature = req.headers['x-admin-signature'];
            const timestamp = parseInt(req.headers['x-admin-timestamp']);
            const bearerToken = req.headers['authorization']?.replace('Bearer ', '') || null;

            if (bearerToken) {
                try {
                    if (!this.jwtSecret) {
                        this.logAdminAction('auth_check', { endpoint: req.path }, 'failed', 'JWT_SECRET not configured');
                        return res.status(500).json({ error: 'JWT authentication not configured on server' });
                    }

                    const decoded = jwt.verify(bearerToken, this.jwtSecret);

                    this.logAdminAction('auth_check', { endpoint: req.path, user: decoded.userId }, 'success', 'JWT verified');
                    res.set('X-RateLimit-Remaining', '100');
                    next();
                    return;
                } catch (error) {
                    this.logAdminAction('auth_check', { endpoint: req.path }, 'failed', `Invalid JWT: ${error.message}`);
                    return res.status(401).json({ error: 'Invalid JWT token', details: error.message });
                }
            }

            if (!authHeader || !signature || !timestamp) {
                this.logAdminAction('auth_check', { endpoint: req.path }, 'failed', 'Missing credentials');
                return res.status(401).json({
                    error: 'Missing authentication credentials',
                    required: ['Authorization: Bearer JWT', 'OR', 'x-admin-token', 'x-admin-signature', 'x-admin-timestamp']
                });
            }

            if (authHeader !== this.adminToken) {
                this.logAdminAction('auth_check', { endpoint: req.path }, 'failed', 'Invalid token');
                return res.status(401).json({ error: 'Invalid admin token' });
            }

            const rateLimit = this.checkRateLimit(this.adminId);
            if (!rateLimit.allowed) {
                this.logAdminAction('rate_limit', { endpoint: req.path }, 'failed', 'Rate limit exceeded');
                return res.status(429).json({
                    error: 'Rate limit exceeded',
                    resetTime: rateLimit.resetTime
                });
            }

            const body = (req.body && typeof req.body === 'object' && Object.keys(req.body).length > 0)
                ? JSON.stringify(req.body)
                : '';

            const verify = this.verifySignature(
                req.method,
                req.path,
                body,
                signature,
                timestamp
            );

            if (!verify.valid) {
                console.log(`[ADMIN-COM] FAILED VERIFICATION at ${req.path}`);
                this.logAdminAction('signature_verify', { endpoint: req.path }, 'failed', verify.reason);
                return res.status(401).json({
                    error: 'Invalid request signature',
                    details: verify.reason
                });
            }

            res.set('X-RateLimit-Remaining', rateLimit.remaining);
            next();
        };
    }

    createTestRequest(method, endpoint, body = null) {
        const bodyStr = body ? JSON.stringify(body) : '';
        const { signature, timestamp } = this.signRequest(method, endpoint, bodyStr);

        return {
            method,
            url: endpoint,
            headers: {
                'x-admin-token': this.adminToken,
                'x-admin-signature': signature,
                'x-admin-timestamp': timestamp,
                'Content-Type': 'application/json'
            },
            body: body || undefined
        };
    }

    getStats() {
        return {
            totalAuditLogs: this.auditLog.length,
            successfulRequests: this.auditLog.filter(l => l.status === 'success').length,
            failedRequests: this.auditLog.filter(l => l.status === 'failed').length,
            activeAdmins: this.adminRequests.size,
            adminId: this.adminId,
            secretConfigured: !!this.adminSecret,
            tokenConfigured: !!this.adminToken,
            auditLogPath: this.auditLogPath
        };
    }
}

module.exports = AdminComPort;
