module.exports = {
    environment: process.env.NODE_ENV || 'production',
    isDevelopment: process.env.NODE_ENV === 'development',
    isProduction: process.env.NODE_ENV === 'production',

    services: {
        admin: {
            port: process.env.ADMIN_PANEL_PORT || 3006,
            name: 'admin-panel-service',
            enabled: true,
            description: 'Admin control center',
        },
        call: {
            port: process.env.CALL_SERVICE_PORT || 3001,
            name: 'call-service',
            enabled: true,
            description: 'Call signaling service',
        },
        messaging: {
            port: process.env.MESSAGING_SERVICE_PORT || 3002,
            name: 'messaging-service',
            enabled: true,
            description: 'Messaging and chat service',
        },
        websocket: {
            port: process.env.WEBSOCKET_SERVICE_PORT || 3003,
            name: 'websocket-service',
            enabled: true,
            description: 'WebSocket real-time service',
        },
        polling: {
            port: process.env.POLLING_SERVICE_PORT || 3004,
            name: 'polling-service',
            enabled: true,
            description: 'HTTP polling service (reliable)',
        },
        peerManager: {
            port: process.env.PEER_MANAGER_PORT || 3005,
            name: 'peer-manager-service',
            enabled: true,
            description: 'Peer connection management',
        },
    },

    socketIO: {
        // transport order
        transports: ['polling', 'websocket'],

        pingInterval: 30000,
        pingTimeout: 60000,
        upgradeTimeout: 10000,

        reconnection: true,
        reconnectionDelay: 1000,
        reconnectionDelayMax: 5000,
        reconnectionAttempts: Infinity,

        cors: {
            origin: process.env.ALLOWED_ORIGINS?.split(',') || ['*'],
            methods: ['GET', 'POST'],
            credentials: true,
        },

        serializationMethod: 'default',

        // max json body size
        maxHttpBufferSize: 1e6,
    },

    database: {
        mongodb: {
            url: process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime',
            options: {
                useNewUrlParser: true,
                useUnifiedTopology: true,
                serverSelectionTimeoutMS: 5000,
                socketTimeoutMS: 45000,
                maxPoolSize: 10,
                minPoolSize: 2,
            },
        },
        redis: {
            host: process.env.REDIS_HOST || 'localhost',
            port: process.env.REDIS_PORT || 6379,
            password: process.env.REDIS_PASSWORD || undefined,
            db: process.env.REDIS_DB || 0,
        },
    },

    androidApp: {
        endpoints: {
            polling: {
                protocol: process.env.POLLING_PROTOCOL || 'https',
                host: process.env.POLLING_HOST || process.env.DOMAIN || 'example.com',
                port: process.env.POLLING_PORT || 443,
                path: process.env.POLLING_PATH || '/socket.io',
            },
            websocket: {
                protocol: process.env.WEBSOCKET_PROTOCOL || 'wss',
                host: process.env.WEBSOCKET_HOST || process.env.DOMAIN || 'example.com',
                port: process.env.WEBSOCKET_PORT || 443,
                path: process.env.WEBSOCKET_PATH || '/ws',
            },
        },

        auth: {
            tokenHeader: 'Authorization',
            tokenPrefix: 'Bearer',
            jwtSecret: process.env.JWT_SECRET || 'your-secret-key',
            jwtExpiry: '7d',
        },

        timeouts: {
            connectionTimeout: 15000,
            requestTimeout: 30000,
            callSetupTimeout: 60000,
        },
    },

    healthCheck: {
        interval: 5000,

        timeout: 15000,

        endpoints: {
            call: 'http://localhost:3001/health',
            messaging: 'http://localhost:3002/health',
            websocket: 'http://localhost:3003/health',
            polling: 'http://localhost:3004/health',
            peerManager: 'http://localhost:3005/health',
        },
    },

    logging: {
        level: process.env.LOG_LEVEL || 'info',
        format: 'json',

        maxSize: '100m',
        maxFiles: 10,

        errorFile: 'logs/error.log',
        combinedFile: 'logs/combined.log',
    },

    debian: {
        runAsUser: process.env.RUN_AS_USER || 'node',

        maxOpenFiles: 65536,
        maxConnections: 10000,

        pidDirectory: process.env.PID_DIR || './.pids',
        logDirectory: process.env.LOG_DIR || './logs',

        serviceRestartOnFailure: true,
        serviceRestartDelay: 3000,

        systemd: {
            enabled: process.env.SYSTEMD_ENABLED === 'true',
            unitFile: '/etc/systemd/system/freetime.service',
        },
    },

    autoRecovery: {
        enabled: true,

        maxRestartAttempts: 5,
        restartDelay: 5000,

        autoRestartOnCrash: true,

        circuitBreakerThreshold: 5,
        circuitBreakerResetTime: 60000,
    },

    performance: {
        connectionPool: {
            min: 2,
            max: 10,
        },

        messageQueueSize: 1000,
        messageFlushInterval: 100,

        workerThreads: process.env.WORKER_THREADS || 4,

        maxMemoryMB: process.env.MAX_MEMORY_MB || 512,
        gcInterval: 60000,
    },

    security: {
        allowedOrigins: process.env.ALLOWED_ORIGINS?.split(',') || [
            'http://localhost:*',
            'https://localhost:*',
        ],

        rateLimit: {
            enabled: true,
            windowMs: 15 * 60 * 1000,
            maxRequests: 1000,
        },

        ssl: {
            enabled: process.env.SSL_ENABLED === 'true',
            keyFile: process.env.SSL_KEY_FILE,
            certFile: process.env.SSL_CERT_FILE,
        },

        requireAuth: true,
        tokenExpiry: '7d',
    },

    getAndroidConfig: function() {
        return {
            services: {
                polling: {
                    url: `${this.androidApp.endpoints.polling.protocol}://${this.androidApp.endpoints.polling.host}:${this.androidApp.endpoints.polling.port}${this.androidApp.endpoints.polling.path}`,
                    type: 'polling',
                    priority: 1,
                },
                websocket: {
                    url: `${this.androidApp.endpoints.websocket.protocol}://${this.androidApp.endpoints.websocket.host}:${this.androidApp.endpoints.websocket.port}${this.androidApp.endpoints.websocket.path}`,
                    type: 'websocket',
                    priority: 2,
                },
            },
            timeouts: this.androidApp.timeouts,
            auth: this.androidApp.auth,
        };
    },

    validate: function() {
        const errors = [];

        const requiredServices = ['admin', 'call', 'messaging', 'websocket', 'polling', 'peerManager'];
        for (const service of requiredServices) {
            if (!this.services[service]) {
                errors.push(`Service configuration missing: ${service}`);
            }
        }

        const ports = Object.values(this.services).map(s => s.port);
        const uniquePorts = new Set(ports);
        if (ports.length !== uniquePorts.size) {
            errors.push('Service ports must be unique');
        }

        for (const service of Object.values(this.services)) {
            if (service.port < 1024 || service.port > 65535) {
                errors.push(`Invalid port for ${service.name}: ${service.port}`);
            }
        }

        if (errors.length > 0) {
            console.error('Configuration validation errors:');
            errors.forEach(err => console.error(`  - ${err}`));
            process.exit(1);
        }

        return true;
    },
};
