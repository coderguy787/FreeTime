const AndroidIntegration = require('./android-integration');

class AndroidClientMiddleware {
    constructor(androidIntegration) {
        this.android = androidIntegration;
    }

    initialize(app) {
        app.use(this.android.requestValidator());

        app.get('/health/android', (req, res) => {
            res.status(200).json({
                status: 'healthy',
                service: 'master-server',
                version: process.env.npm_package_version || '1.0.0',
                timestamp: new Date().toISOString(),
                uptime: process.uptime(),
                android: {
                    certificatePinningRequired: true,
                    requestSigningRequired: true,
                    features: ['2FA', 'E2E', 'GroupChat', 'FileSharing', 'VoiceMessaging'],
                    minAppVersion: '1.0.0',
                    maxAppVersion: '999.0.0'
                }
            });
        });

        app.get('/config/android', (req, res) => {
            const config = this.android.generateAndroidConfig(process.env.CORS_ORIGIN);
            res.status(200).json(config);
        });

        app.post('/admin/android-diagnostics', this._requireAdmin, (req, res) => {
            const { config } = req.body;
            const verification = this.android.verifyAndroidSetup(config);

            res.status(200).json({
                timestamp: new Date().toISOString(),
                verification,
                suggestions: this._generateSuggestions(verification)
            });
        });

        app.get('/test/android-request', (req, res) => {
            const testRequests = this.android.generateTestRequests();

            res.status(200).json({
                message: 'Android Integration Test Requests',
                timestamp: new Date().toISOString(),
                instructions: 'Use these test requests with your Android app for verification',
                testCases: {
                    register: {
                        endpoint: 'POST /auth/register',
                        ...testRequests.register
                    },
                    login: {
                        endpoint: 'POST /auth/login',
                        ...testRequests.login
                    },
                    verify2FA: {
                        endpoint: 'POST /auth/verify-2fa',
                        ...testRequests.verify2FA
                    },
                    getProfile: {
                        endpoint: 'GET /users/profile',
                        ...testRequests.getUserProfile
                    },
                    getPeers: {
                        endpoint: 'GET /peers',
                        ...testRequests.getPeers
                    },
                    addPeer: {
                        endpoint: 'POST /peers/add',
                        ...testRequests.addPeer
                    }
                }
            });
        });

        app.get('/metrics/android', (req, res) => {
            res.status(200).json({
                timestamp: new Date().toISOString(),
                metrics: {
                    activeAndroidClients: this._getActiveAndroidClients(),
                    averageRequestTime: this._getAverageAndroidRequestTime(),
                    errorRate: this._getAndroidErrorRate(),
                    topErrors: this._getTopAndroidErrors(),
                    certificatePinningFailures: this._getCertificatePinningFailures(),
                    signatureValidationFailures: this._getSignatureValidationFailures()
                }
            });
        });

        console.log(' Android Client Middleware initialized');
    }

    // bind _requireAdmin so express gets the right this
    _requireAdmin = (req, res, next) => {
        const authHeader = req.get('Authorization');
        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            return res.status(401).json({ error: 'Unauthorized' });
        }

        next();
    }

    _generateSuggestions(verification) {
        const suggestions = [];

        if (verification.errors.length > 0) {
            suggestions.push({
                severity: 'CRITICAL',
                message: 'Fix all critical errors before deployment',
                items: verification.errors
            });
        }

        if (verification.warnings.length > 0) {
            suggestions.push({
                severity: 'WARNING',
                message: 'Address warnings to improve reliability',
                items: verification.warnings
            });
        }

        return suggestions;
    }

    _getActiveAndroidClients() {
        return 0;
    }

    _getAverageAndroidRequestTime() {
        return 0;
    }

    _getAndroidErrorRate() {
        return 0;
    }

    _getTopAndroidErrors() {
        return [];
    }

    _getCertificatePinningFailures() {
        return 0;
    }

    _getSignatureValidationFailures() {
        return 0;
    }

    enhanceErrorResponse(error, req) {
        const isAndroidClient = req.get('User-Agent')?.includes('Android');

        if (!isAndroidClient) {
            return error;
        }

        return {
            ...error,
            android: {
                timestamp: Date.now(),
                deviceId: req.android?.deviceId,
                appVersion: req.android?.clientVersion,
                recoveryActions: this._getRecoveryActions(error.code)
            }
        };
    }

    _getRecoveryActions(errorCode) {
        const actions = {
            'INVALID_SIGNATURE': [
                'Verify HMAC secret matches server configuration',
                'Check system clock is synchronized',
                'Ensure request body encoding is UTF-8'
            ],
            'TIMESTAMP_EXPIRED': [
                'Synchronize device time with NTP server',
                'Reduce network latency',
                'Check for proxy/firewall delays'
            ],
            'RATE_LIMITED': [
                'Reduce request frequency',
                'Implement exponential backoff',
                'Wait 15 minutes before retrying'
            ],
            'UNAUTHORIZED': [
                'Re-authenticate with credentials',
                'Refresh authentication token',
                'Check token expiration time'
            ],
            'CERTIFICATE_PIN_MISMATCH': [
                'Update certificate pins in app',
                'Verify SSL certificate is valid',
                'Check certificate expiration date'
            ]
        };

        return actions[errorCode] || [
            'Check server status',
            'Verify network connectivity',
            'Review server logs for details'
        ];
    }
}

module.exports = AndroidClientMiddleware;
