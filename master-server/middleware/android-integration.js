class AndroidIntegration {
    constructor(config = {}) {
        this.config = {
            minClientVersion: config.minClientVersion || '1.0.0',
            maxClientVersion: config.maxClientVersion || '99.99.99',
            ...config
        };
    }

    requestValidator() {
        return (req, res, next) => {
            // identify android clients by headers
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

    healthCheckEndpoint() {
        return (req, res) => {
            res.json({
                status: 'healthy',
                service: 'android-api',
                timestamp: new Date().toISOString()
            });
        };
    }

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
