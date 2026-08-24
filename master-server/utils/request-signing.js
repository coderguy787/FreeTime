const crypto = require('crypto');

class RequestSigner {
    constructor(secretKey) {
        if (!secretKey) {
            throw new Error('Secret key is required for request signing');
        }
        this.secretKey = secretKey;
    }

    generateSignature(method, path, body, timestamp) {
        const bodyHash = this.hashBody(body);
        const message = `${method.toUpperCase()}|${path}|${timestamp}|${bodyHash}`;

        const signature = crypto
            .createHmac('sha256', this.secretKey)
            .update(message)
            .digest('hex');

        return signature;
    }

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

    verifySignature(method, path, body, signature, timestamp, maxAgeSec = 300) {
        try {
            const timestampDate = new Date(timestamp);
            if (isNaN(timestampDate.getTime())) {
                return { valid: false, error: 'Invalid timestamp format' };
            }

            const now = new Date();
            // tolerate small clock differences
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

        const expectedSignature = this.generateSignature(method, path, body, timestamp);

        // constant-time comparison
        const valid = crypto.timingSafeEqual(
            Buffer.from(signature, 'hex'),
            Buffer.from(expectedSignature, 'hex')
        );

        return { valid };
    }
}

function requestSigningMiddleware(signingSecretKey, options = {}) {
    const signer = new RequestSigner(signingSecretKey);
    const bypassPaths = options.bypassPaths || ['/health', '/health/live', '/health/ready'];
    const maxAge = options.maxAge || 300;

    return (req, res, next) => {
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

            const body = req.body ? JSON.stringify(req.body) : '';

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

class RequestSigningClient {
    constructor(secretKey) {
        this.signer = new RequestSigner(secretKey);
    }

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
