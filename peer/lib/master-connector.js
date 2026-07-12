/**
 * Master Server Connector
 * Handles all communication with the master server
 * Including account verification, 2FA, and metrics reporting
 */

const axios = require('axios');

class MasterConnector {
    constructor(config, logger) {
        this.config = config;
        this.logger = logger;
        
        this.baseURL = `${config.MASTER_URL}:${config.MASTER_PORT}`;
        this.apiKey = config.MASTER_API_KEY;
        
        this.client = axios.create({
            baseURL: this.baseURL,
            timeout: 10000,
            headers: {
                'X-Peer-Token': this.apiKey,
                'X-Peer-ID': config.PEER_NAME
            }
        });

        this.stats = {
            verifications: 0,
            verificationFailures: 0,
            twoFAChecks: 0,
            twoFAFailures: 0,
            metricsReports: 0
        };
    }

    /**
     * Verify JWT token with master server
     */
    async verifyToken(token) {
        try {
            const response = await this.client.get('/api/verify-token', {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            this.stats.verifications++;
            return response.data;
        } catch (error) {
            this.stats.verificationFailures++;
            this.logger.error('Token verification failed', {
                error: error.message,
                code: error.response?.status
            });
            return null;
        }
    }

    /**
     * Verify 2FA TOTP code
     */
    async verify2FA(userId, totpCode, deviceId) {
        try {
            const response = await this.client.post('/api/verify-peer-2fa', {
                userId,
                totpCode,
                deviceId
            });

            this.stats.twoFAChecks++;
            return response.data;
        } catch (error) {
            this.stats.twoFAFailures++;
            this.logger.error('2FA verification failed', {
                userId,
                error: error.message
            });
            return null;
        }
    }

    /**
     * Report metrics to master server
     */
    async reportMetrics(metrics) {
        try {
            await this.client.post('/api/peer/metrics', {
                peerId: this.config.PEER_NAME,
                region: this.config.PEER_REGION,
                timestamp: new Date(),
                ...metrics
            });

            this.stats.metricsReports++;
        } catch (error) {
            this.logger.warn('Metrics report failed', {
                error: error.message
            });
        }
    }

    /**
     * Notify master of peer status change
     */
    async notifyPeerStatus(status, details = {}) {
        try {
            await this.client.post('/api/peer/status', {
                status,
                peerId: this.config.PEER_NAME,
                details,
                timestamp: new Date()
            });
        } catch (error) {
            this.logger.warn('Status notification failed', {
                error: error.message
            });
        }
    }

    /**
     * Get user account info
     */
    async getUserAccount(userId, token) {
        try {
            const response = await this.client.get(`/api/users/${userId}`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            return response.data;
        } catch (error) {
            this.logger.error('User account fetch failed', {
                userId,
                error: error.message
            });
            return null;
        }
    }

    /**
     * Check if user exists
     */
    async userExists(username) {
        try {
            const response = await this.client.get('/api/users/check', {
                params: { username }
            });

            return response.data.exists;
        } catch (error) {
            this.logger.warn('User existence check failed', {
                username,
                error: error.message
            });
            return false;
        }
    }

    /**
     * Register new peer with master (on startup)
     */
    async registerPeer(capabilities = {}) {
        try {
            const response = await this.client.post('/api/peer/register', {
                peerId: this.config.PEER_NAME,
                region: this.config.PEER_REGION,
                port: this.config.PEER_PORT,
                maxCapacity: this.config.MAX_CONNECTIONS,
                capabilities,
                timestamp: new Date()
            });

            this.logger.info('Peer registered with master', {
                peerId: this.config.PEER_NAME
            });

            return response.data;
        } catch (error) {
            this.logger.error('Peer registration failed', {
                error: error.message
            });
            throw error;
        }
    }

    /**
     * Deregister peer from master (on shutdown)
     */
    async deregisterPeer() {
        try {
            await this.client.post('/api/peer/deregister', {
                peerId: this.config.PEER_NAME,
                timestamp: new Date()
            });

            this.logger.info('Peer deregistered from master');
        } catch (error) {
            this.logger.warn('Peer deregistration failed', {
                error: error.message
            });
        }
    }

    /**
     * Get peer statistics
     */
    getStats() {
        return {
            ...this.stats,
            uptime: process.uptime()
        };
    }

    /**
     * Health check with master
     */
    async healthCheck() {
        try {
            const response = await this.client.get('/health');
            return response.data;
        } catch (error) {
            this.logger.warn('Master health check failed', {
                error: error.message
            });
            return null;
        }
    }
}

module.exports = MasterConnector;
