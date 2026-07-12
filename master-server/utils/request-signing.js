/**
 * Request Signing and Verification
 * Provides HMAC-based request signing for API integrity
 * Prevents tampering with API requests between client and server
 */

const crypto = require('crypto');

class RequestSigner {
    constructor(secretKey) {
        if (!secretKey) {
            throw new Error('Secret key is required for request signing');
        }
        this.secretKey = secretKey;
    }
    
    /**
     * Generate signature for request
     * @param {string} method - HTTP method (GET, POST, etc.)
     * @param {string} path - Request path
     * @param {string|Buffer} body - Request body
     * @param {string} timestamp - Timestamp (ISO format)
     * @returns {string} Signature (hex)
     */
    generateSignature(method, path, body, timestamp) {
        // Create message to sign: METHOD|PATH|TIMESTAMP|BODY_HASH
        const bodyHash = this.hashBody(body);
        const message = `${method.toUpperCase()}|${path}|${timestamp}|${bodyHash}`;
        
        // Sign with HMAC-SHA256
        const signature = crypto
            .createHmac('sha256', this.secretKey)
            .update(message)
            .digest('hex');
        
        return signature;
    }
    
    /**
     * Hash request body for integrity check
     * @param {string|Buffer|Object} body - Request body
     * @returns {string} Body hash (hex)
     */
    hashBody(body) {
        let bodyStr;
        
        if (typeof body === 'string') {
            bodyStr = body;
        } else if (Buffer.isBuffer(body)) {
            bodyStr = body.toString('utf8');
        } else if (typeof body === 'object') {
            bodyStr = JSON.stringify(body);
        } else {
            bodyStr = '';
        }
        
        return crypto
            .createHash('sha256')
            .update(bodyStr)
            .digest('hex');
    }
    
    /**
     * Verify request signature
     * @param {string} method - HTTP method
     * @param {string} path - Request path
     * @param {string|Buffer|Object} body - Request body
     * @param {string} signature - Signature from client
     * @param {string} timestamp - Timestamp from client
     * @param {number} maxAgeSec - Max age of timestamp in seconds (default: 300)
     * @returns {Object} { valid: boolean, error?: string }
     */
    verifySignature(method, path, body, signature, timestamp, maxAgeSec = 300) {
        // Validate timestamp format
        try {
            const timestampDate = new Date(timestamp);
            if (isNaN(timestampDate.getTime())) {
                return { valid: false, error: 'Invalid timestamp format' };
            }
            
            // Check timestamp age
            const now = new Date();
            const diffSec = (now - timestampDate) / 1000;
            
            if (diffSec < -5) {
                return { valid: false, error: 'Timestamp is in the future' };
            }
            
            if (diffSec > maxAgeSec) {
                return { valid: false, error: `Timestamp too old (${Math.round(diffSec)}s > ${maxAgeSec}s)` };
            }
        } catch (err) {
            return { valid: false, error: 'Timestamp validation failed' };
        }
        
        // Generate expected signature
        const expectedSignature = this.generateSignature(method, path, body, timestamp);
        
        // Compare signatures (constant-time comparison to prevent timing attacks)
        const valid = crypto.timingSafeEqual(
            Buffer.from(signature, 'hex'),
            Buffer.from(expectedSignature, 'hex')
        );
        
        return { valid };
    }
}

/**
 * Express middleware for request signing verification
 * Expects headers: X-Signature, X-Timestamp
 */
function requestSigningMiddleware(signingSecretKey, options = {}) {
    const signer = new RequestSigner(signingSecretKey);
    const bypassPaths = options.bypassPaths || ['/health', '/health/live', '/health/ready'];
    const maxAge = options.maxAge || 300; // 5 minutes
    
    return (req, res, next) => {
        // Bypass public endpoints
        if (bypassPaths.includes(req.path)) {
            return next();
        }
        
        try {
            const signature = req.headers['x-signature'];
            const timestamp = req.headers['x-timestamp'];
            
            if (!signature || !timestamp) {
                return res.status(401).json({
                    error: 'Missing request signature or timestamp',
                    code: 'MISSING_SIGNATURE'
                });
            }
            
            // Get request body
            const body = req.body ? JSON.stringify(req.body) : '';
            
            // Verify signature
            const result = signer.verifySignature(
                req.method,
                req.path,
                body,
                signature,
                timestamp,
                maxAge
            );
            
            if (!result.valid) {
                return res.status(401).json({
                    error: result.error || 'Invalid request signature',
                    code: 'INVALID_SIGNATURE'
                });
            }
            
            // Signature verified
            req.signatureVerified = true;
            next();
        } catch (err) {
            console.error('Request signing middleware error:', err);
            res.status(500).json({
                error: 'Signature verification failed',
                code: 'SIGNATURE_ERROR'
            });
        }
    };
}

/**
 * Client utility for signing requests
 * Used in Android app or external clients
 */
class RequestSigningClient {
    constructor(secretKey) {
        this.signer = new RequestSigner(secretKey);
    }
    
    /**
     * Sign a request (returns headers to add)
     * @param {string} method - HTTP method
     * @param {string} path - Request path
     * @param {Object|string} body - Request body
     * @returns {Object} Headers to add to request
     */
    signRequest(method, path, body) {
        const timestamp = new Date().toISOString();
        const signature = this.signer.generateSignature(method, path, body, timestamp);
        
        return {
            'X-Signature': signature,
            'X-Timestamp': timestamp
        };
    }
}

module.exports = {
    RequestSigner,
    RequestSigningClient,
    requestSigningMiddleware
};
