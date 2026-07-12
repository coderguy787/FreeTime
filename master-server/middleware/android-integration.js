/**
 * Android Integration Module
 * Provides Android-specific integration features for the FreeTime API
 */

class AndroidIntegration {
    constructor(config = {}) {
        this.config = {
            minClientVersion: config.minClientVersion || '1.0.0',
            maxClientVersion: config.maxClientVersion || '99.99.99',
            ...config
        };
    }

    /**
     * Request validator middleware
     */
    requestValidator() {
        return (req, res, next) => {
            // Validate Android client headers if present
            const clientVersion = req.headers['x-client-version'];
            const clientType = req.headers['x-client-type'];

            if (clientType === 'android' && clientVersion) {
                req.android = {
                    version: clientVersion,
                    type: 'android'
                };
            }

            next();
        };
    }

    /**
     * Health check endpoint for Android clients
     */
    healthCheckEndpoint() {
        return (req, res) => {
            res.json({
                status: 'healthy',
                service: 'android-api',
                timestamp: new Date().toISOString()
            });
        };
    }

    /**
     * Get Android client configuration
     */
    getClientConfig() {
        return {
            minVersion: this.config.minClientVersion,
            maxVersion: this.config.maxClientVersion,
            apiVersion: '1.0',
            features: ['authentication', 'messaging', 'peer-discovery']
        };
    }
}

module.exports = AndroidIntegration;
