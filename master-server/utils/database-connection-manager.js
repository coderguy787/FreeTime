const { MongoClient } = require('mongodb');
const { getLogger } = require('./logger');
const logger = getLogger('DatabaseConnectionManager');

class DatabaseConnectionManager {
    constructor(mongoUri, options = {}) {
        this.mongoUri = mongoUri;
        this.options = {
            maxPoolSize: options.maxPoolSize || 50,
            minPoolSize: options.minPoolSize || 10,
            maxIdleTimeMS: options.maxIdleTimeMS || 60000,
            socketTimeoutMS: options.socketTimeoutMS || 30000,
            serverSelectionTimeoutMS: options.serverSelectionTimeoutMS || 5000,
            connectTimeoutMS: options.connectTimeoutMS || 10000,
            maxRetries: options.maxRetries || 3,
            retryDelayMs: options.retryDelayMs || 100,
            maxBackoffMs: options.maxBackoffMs || 5000,
            enableHealthCheck: options.enableHealthCheck !== false,
            healthCheckIntervalMs: options.healthCheckIntervalMs || 30000,
            ...options
        };

        this.client = null;
        this.db = null;
        this.isConnected = false;
        this.connectionRetries = 0;
        this.lastHealthCheckTime = null;
        this.healthCheckInterval = null;
        this.connectionState = 'disconnected';
    }

    async initialize() {
        logger.info('Database Connection Manager: Initializing...', {
            maxPoolSize: this.options.maxPoolSize,
            minPoolSize: this.options.minPoolSize,
            connectTimeoutMS: this.options.connectTimeoutMS
        });

        for (let attempt = 1; attempt <= this.options.maxRetries; attempt++) {
            try {
                this.connectionState = 'connecting';
                logger.debug(`Attempting MongoDB connection (${attempt}/${this.options.maxRetries})...`);

                this.client = new MongoClient(this.mongoUri, {
                    maxPoolSize: this.options.maxPoolSize,
                    minPoolSize: this.options.minPoolSize,
                    maxIdleTimeMS: this.options.maxIdleTimeMS,
                    socketTimeoutMS: this.options.socketTimeoutMS,
                    serverSelectionTimeoutMS: this.options.serverSelectionTimeoutMS,
                    connectTimeoutMS: this.options.connectTimeoutMS,
                    retryWrites: true,
                    retryReads: true,
                    w: 'majority',
                    j: true,
                    maxStalenessSeconds: 120,
                    useNewUrlParser: true,
                    useUnifiedTopology: true
                });

                await this.client.connect();

                this.db = this.client.db('freetime');
                // ping to verify the connection actually works
                await this.db.command({ ping: 1 });

                this.isConnected = true;
                this.connectionState = 'connected';
                this.connectionRetries = 0;

                logger.info('Database Connection Manager: Connected successfully', {
                    poolSize: this.options.maxPoolSize,
                    attempt: attempt
                });

                if (this.options.enableHealthCheck) {
                    this.startHealthCheck();
                }

                this.setupEventHandlers();

                return { success: true, connected: true };

            } catch (err) {
                logger.error(`MongoDB connection attempt ${attempt} failed: ${err.message}`, {
                    attempt,
                    maxRetries: this.options.maxRetries,
                    error: err.code || err.name
                });

                this.connectionRetries = attempt;

                if (attempt < this.options.maxRetries) {
                    const backoffMs = Math.min(
                        this.options.retryDelayMs * Math.pow(2, attempt - 1),
                        this.options.maxBackoffMs
                    );

                    logger.warn(`Retrying MongoDB connection in ${backoffMs}ms...`, {
                        backoffMs,
                        nextAttempt: attempt + 1
                    });

                    await new Promise(resolve => setTimeout(resolve, backoffMs));
                } else {
                    this.connectionState = 'failed';
                    logger.error('[FATAL] MongoDB connection failed after all retries', {
                        mongoUri: this.sanitizeUri(this.mongoUri),
                        totalAttempts: attempt
                    });

                    throw err;
                }
            }
        }
    }

    setupEventHandlers() {
        if (!this.client) return;

        this.client.on('error', (err) => {
            logger.error('MongoDB client error', {
                error: err.message,
                code: err.code,
                state: this.connectionState
            });
        });

        this.client.on('serverOpening', () => {
            logger.debug('MongoDB: Server connection opening...');
        });

        this.client.on('serverClosed', () => {
            logger.warn('MongoDB: Server connection closed');
            this.isConnected = false;
            this.connectionState = 'disconnected';
        });

        this.client.on('topologyOpening', () => {
            logger.debug('MongoDB: Topology opening...');
        });

        this.client.on('topologyClosed', () => {
            logger.warn('MongoDB: Topology closed');
            this.connectionState = 'disconnected';
        });

        this.client.on('connectionCheckedOut', () => {
            logger.debug('MongoDB: Connection checked out from pool');
        });

        this.client.on('connectionCheckedIn', () => {
            logger.debug('MongoDB: Connection returned to pool');
        });
    }

    startHealthCheck() {
        if (this.healthCheckInterval) return;

        this.healthCheckInterval = setInterval(async () => {
            try {
                if (!this.db) throw new Error('Database instance missing during health check');

                const result = await this.db.command({ ping: 1 });

                if (result.ok === 1) {
                    this.lastHealthCheckTime = Date.now();
                    this.isConnected = true;
                    this.connectionState = 'connected';
                    logger.debug('Database health check: OK');
                } else {
                    throw new Error('Unexpected ping response');
                }
            } catch (err) {
                logger.error('Database health check failed', {
                    error: err.message,
                    timestamp: new Date().toISOString()
                });
                this.isConnected = false;
                this.connectionState = 'disconnected';
            }
        }, this.options.healthCheckIntervalMs);

        logger.info(`Database health check started (interval: ${this.options.healthCheckIntervalMs}ms)`);
    }

    stopHealthCheck() {
        if (this.healthCheckInterval) {
            clearInterval(this.healthCheckInterval);
            this.healthCheckInterval = null;
            logger.info('Database health check stopped');
        }
    }

    getDatabase() {
        if (!this.isConnected || !this.db) {
            throw new Error('Database not connected. Call initialize() first.');
        }
        return this.db;
    }

    getCollection(collectionName) {
        const db = this.getDatabase();
        return db.collection(collectionName);
    }

    async executeWithRetry(operation, operationName = 'Operation', maxRetries = 2) {
        let lastError = null;

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug(`Executing ${operationName} (attempt ${attempt}/${maxRetries})`);
                const result = await operation();
                return result;
            } catch (err) {
                lastError = err;
                logger.warn(`${operationName} failed on attempt ${attempt}: ${err.message}`, {
                    attempt,
                    maxRetries,
                    errorCode: err.code
                });

                if (err.code === 13 || err.name === 'MongoAuthenticationError') {
                    throw err;
                }

                if (attempt === maxRetries) {
                    break;
                }

                const backoffMs = Math.min(100 * Math.pow(2, attempt - 1), 2000);
                await new Promise(resolve => setTimeout(resolve, backoffMs));
            }
        }

        throw lastError || new Error(`${operationName} failed after ${maxRetries} attempts`);
    }

    getConnectionStatus() {
        return {
            connected: this.isConnected,
            state: this.connectionState,
            connectionRetries: this.connectionRetries,
            lastHealthCheck: this.lastHealthCheckTime,
            poolSize: this.options.maxPoolSize,
            timestamp: new Date().toISOString()
        };
    }

    async shutdown() {
        logger.info('Database Connection Manager: Shutting down...');

        this.stopHealthCheck();

        if (this.client) {
            try {
                await this.client.close();
                this.isConnected = false;
                this.connectionState = 'disconnected';
                this.db = null;
                logger.info('Database Connection Manager: Shutdown complete');
            } catch (err) {
                logger.error('Error during shutdown:', err.message);
            }
        }
    }

    sanitizeUri(uri) {
        try {
            return uri.replace(/:[^:@]+@/, ':***@');
        } catch (e) {
            return 'mongodb://***';
        }
    }

    getStats() {
        return {
            ...this.getConnectionStatus(),
            uri: this.sanitizeUri(this.mongoUri),
            options: {
                maxPoolSize: this.options.maxPoolSize,
                minPoolSize: this.options.minPoolSize,
                maxIdleTimeMS: this.options.maxIdleTimeMS,
                connectTimeoutMS: this.options.connectTimeoutMS
            }
        };
    }
}

module.exports = DatabaseConnectionManager;
