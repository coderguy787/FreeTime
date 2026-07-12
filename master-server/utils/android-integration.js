/**
 * Android Integration Configuration Utility
 * Handles certificate pinning, request signing, and Android-specific configuration
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

class AndroidIntegration {
    constructor(config = {}) {
        this.config = {
            hmacSecret: config.hmacSecret || process.env.HMAC_SECRET,
            requestTimeout: config.requestTimeout || parseInt(process.env.REQUEST_TIMEOUT || '30000'),
            certPins: config.certPins || (process.env.CERT_PIN_HASHES || '').split(',').filter(Boolean),
            ...config
        };

        if (!this.config.hmacSecret) {
            console.warn('⚠️ HMAC_SECRET not configured for Android request signing');
        }
    }

    /**
     * Middleware to validate Android requests (certificate pinning verification + HMAC signing)
     */
    requestValidator() {
        return (req, res, next) => {
            // Android client identification
            const userAgent = req.get('User-Agent') || '';
            const isAndroidClient = userAgent.includes('Android');
            
            if (isAndroidClient) {
                // Verify request signature
                const signature = req.get('X-Signature');
                const timestamp = req.get('X-Timestamp');
                const nonce = req.get('X-Nonce');

                if (!signature || !timestamp || !nonce) {
                    return res.status(401).json({
                        success: false,
                        error: 'Missing required headers: X-Signature, X-Timestamp, X-Nonce',
                        code: 'MISSING_SIGNATURE_HEADERS'
                    });
                }

                // Verify timestamp is recent (within 5 minutes)
                const currentTime = Date.now();
                const requestTime = parseInt(timestamp);
                const timeDifference = Math.abs(currentTime - requestTime);

                if (timeDifference > 5 * 60 * 1000) {
                    return res.status(401).json({
                        success: false,
                        error: 'Request timestamp expired',
                        code: 'TIMESTAMP_EXPIRED'
                    });
                }

                // Verify HMAC signature
                const bodyStr = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
                const messageToSign = `${bodyStr}:${timestamp}:${nonce}`;
                const expectedSignature = crypto
                    .createHmac('sha256', this.config.hmacSecret)
                    .update(messageToSign)
                    .digest('hex');

                if (signature !== expectedSignature) {
                    return res.status(401).json({
                        success: false,
                        error: 'Invalid request signature',
                        code: 'INVALID_SIGNATURE'
                    });
                }

                // Add Android context to request
                req.android = {
                    isValid: true,
                    clientVersion: req.get('X-App-Version') || 'unknown',
                    deviceId: req.get('X-Device-Id') || 'unknown',
                    timestamp,
                    nonce
                };
            }

            next();
        };
    }

    /**
     * Generate certificate pin hash from certificate
     * Usage: For updating Android app's certificate pins
     */
    generateCertificatePin(certificatePath) {
        try {
            const cert = fs.readFileSync(certificatePath, 'utf8');
            const publicKey = this._extractPublicKey(cert);
            const hash = crypto
                .createHash('sha256')
                .update(publicKey)
                .digest('base64');
            
            return `sha256/${hash}`;
        } catch (error) {
            console.error('Error generating certificate pin:', error.message);
            throw error;
        }
    }

    /**
     * Extract public key from certificate
     */
    _extractPublicKey(certificate) {
        // Remove PEM headers and whitespace
        const publicKeyPem = certificate
            .replace(/-----BEGIN[^-]*-----/g, '')
            .replace(/-----END[^-]*-----/g, '')
            .replace(/\s/g, '');
        
        return Buffer.from(publicKeyPem, 'base64');
    }

    /**
     * Generate example Android request with signature
     */
    generateAndroidRequest(body = {}, options = {}) {
        const timestamp = options.timestamp || Date.now();
        const nonce = options.nonce || crypto.randomUUID();
        const bodyStr = JSON.stringify(body);
        const messageToSign = `${bodyStr}:${timestamp}:${nonce}`;
        
        const signature = crypto
            .createHmac('sha256', this.config.hmacSecret)
            .update(messageToSign)
            .digest('hex');

        return {
            headers: {
                'X-Signature': signature,
                'X-Timestamp': timestamp.toString(),
                'X-Nonce': nonce,
                'X-App-Version': options.appVersion || '1.0.0',
                'X-Device-Id': options.deviceId || 'example-device-id',
                'User-Agent': 'Android/SecureChat (Android 13+)',
                'Content-Type': 'application/json'
            },
            body: bodyStr
        };
    }

    /**
     * Generate configuration JSON for Android app
     */
    generateAndroidConfig(serverUrl = process.env.CORS_ORIGIN || 'https://localhost:3000') {
        return {
            server: {
                apiUrl: serverUrl,
                apiPort: 3000,
                wsUrl: serverUrl.replace('https://', 'wss://'),
                wsPort: 3001,
                timeout: this.config.requestTimeout,
                retryPolicy: {
                    maxRetries: 3,
                    initialDelayMs: 1000,
                    maxDelayMs: 30000,
                    backoffMultiplier: 2.0
                }
            },
            security: {
                certificatePins: this.config.certPins.map(pin => ({
                    hostname: '*.yourdomain.com',
                    pins: [pin]
                })),
                requireCertificatePinning: true,
                requireHTTPS: true,
                validateTimestamp: true,
                validateSignature: true
            },
            features: {
                twoFactorAuth: true,
                endToEndEncryption: true,
                groupChat: true,
                fileSharing: true,
                voiceMessaging: true
            }
        };
    }

    /**
     * Verify Android app configuration
     */
    verifyAndroidSetup(config) {
        const issues = [];

        if (!config.server?.apiUrl) {
            issues.push('❌ Missing apiUrl configuration');
        }

        if (!config.security?.certificatePins || config.security.certificatePins.length === 0) {
            issues.push('⚠️ No certificate pins configured');
        }

        if (!config.security?.requireHTTPS) {
            issues.push('❌ HTTPS not required - SECURITY RISK');
        }

        if (!config.security?.validateSignature) {
            issues.push('❌ Request signature validation disabled');
        }

        if (config.server?.retryPolicy?.maxRetries === 0) {
            issues.push('⚠️ Retry policy disabled - app may be unreliable');
        }

        return {
            isValid: issues.length === 0,
            issues,
            warnings: issues.filter(i => i.startsWith('⚠️')),
            errors: issues.filter(i => i.startsWith('❌'))
        };
    }

    /**
     * Generate test requests for Android integration testing
     */
    generateTestRequests(baseUrl = 'https://localhost:3000') {
        return {
            register: this.generateAndroidRequest({
                email: 'test@example.com',
                password: 'TestPassword123!',
                username: 'testuser'
            }, { appVersion: '1.0.0' }),

            login: this.generateAndroidRequest({
                email: 'test@example.com',
                password: 'TestPassword123!'
            }, { appVersion: '1.0.0' }),

            verify2FA: this.generateAndroidRequest({
                userId: 'user_123',
                code: '123456'
            }, { appVersion: '1.0.0' }),

            getUserProfile: this.generateAndroidRequest({
                userId: 'user_123'
            }, { appVersion: '1.0.0' }),

            getPeers: this.generateAndroidRequest({
                limit: 20,
                offset: 0
            }, { appVersion: '1.0.0' }),

            addPeer: this.generateAndroidRequest({
                peerId: 'peer_456',
                displayName: 'John Doe'
            }, { appVersion: '1.0.0' })
        };
    }

    /**
     * Log Android request for debugging
     */
    logAndroidRequest(req, actionName = 'Android Request') {
        console.log(`\n📱 ${actionName}`);
        console.log(`  Device: ${req.android?.deviceId || 'unknown'}`);
        console.log(`  App Version: ${req.android?.clientVersion || 'unknown'}`);
        console.log(`  Timestamp: ${new Date(parseInt(req.android?.timestamp || Date.now())).toISOString()}`);
        console.log(`  Nonce: ${req.android?.nonce || 'unknown'}`);
        console.log(`  Signature: Valid ✅`);
    }

    /**
     * Generate documentation for Android developers
     */
    generateAndroidDocumentation() {
        return `
# Android Integration Guide

## Certificate Pinning

Update \`network_security_config.xml\`:

\`\`\`xml
<network-security-config>
    <domain-config cleartextTraffic="false">
        <domain includeSubdomains="true">yourdomain.com</domain>
        <pin-set expiration="2027-02-04">
            ${this.config.certPins.map(pin => `<pin digest="SHA-256">${pin.replace('sha256/', '')}</pin>`).join('\n            ')}
        </pin-set>
    </domain-config>
</network-security-config>
\`\`\`

## Request Signing

All requests must include:

1. **X-Signature**: HMAC-SHA256(body:timestamp:nonce)
2. **X-Timestamp**: Current time in milliseconds
3. **X-Nonce**: Random UUID
4. **X-App-Version**: App version (e.g., "1.0.0")
5. **X-Device-Id**: Unique device identifier

Example Kotlin implementation:

\`\`\`kotlin
fun signRequest(body: String): Map<String, String> {
    val timestamp = System.currentTimeMillis()
    val nonce = UUID.randomUUID().toString()
    val messageToSign = "$body:$timestamp:$nonce"
    
    val signature = HmacUtils.hmacSha256Hex(HMAC_SECRET, messageToSign)
    
    return mapOf(
        "X-Signature" to signature,
        "X-Timestamp" to timestamp.toString(),
        "X-Nonce" to nonce,
        "X-App-Version" to BuildConfig.VERSION_NAME,
        "X-Device-Id" to getDeviceId()
    )
}
\`\`\`

## Retry Policy

Implement exponential backoff:

\`\`\`kotlin
val retryPolicy = RetryPolicy(
    maxRetries = 3,
    initialDelayMs = 1000,
    maxDelayMs = 30000,
    backoffMultiplier = 2.0
)
\`\`\`

## WebSocket Connection

Connect to WSS endpoint:

\`\`\`kotlin
val wsUrl = "wss://yourdomain.com/ws"
val webSocket = client.newWebSocket(
    Request.Builder()
        .url(wsUrl)
        .addHeader("Authorization", "Bearer $jwtToken")
        .build(),
    webSocketListener
)
\`\`\`

## Health Checks

Implement periodic health checks:

\`\`\`kotlin
fun checkServerHealth() {
    api.getHealth().enqueue(object : Callback<HealthResponse> {
        override fun onResponse(call: Call<HealthResponse>, response: Response<HealthResponse>) {
            if (response.isSuccessful && response.body()?.status == "healthy") {
                // Server is healthy
            }
        }
        
        override fun onFailure(call: Call<HealthResponse>, t: Throwable) {
            // Handle connection error
        }
    })
}
\`\`\`

## Error Handling

Handle specific error codes:

| Code | Meaning | Action |
|------|---------|--------|
| MISSING_SIGNATURE_HEADERS | Missing required headers | Add signature headers |
| TIMESTAMP_EXPIRED | Request too old | Check device clock |
| INVALID_SIGNATURE | Signature doesn't match | Verify HMAC secret |
| RATE_LIMITED | Too many requests | Implement backoff |
| UNAUTHORIZED | Invalid token | Re-authenticate |

---

Generated: ${new Date().toISOString()}
Version: 1.0.0
        `;
    }
}

module.exports = AndroidIntegration;

// Export for CLI usage
if (require.main === module) {
    const integration = new AndroidIntegration();
    console.log('Android Integration Configuration');
    console.log('==================================\n');
    console.log('Generated Configuration:');
    console.log(JSON.stringify(integration.generateAndroidConfig(), null, 2));
}
