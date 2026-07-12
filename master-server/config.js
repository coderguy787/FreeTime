/**
 * Production Configuration for FreeTime Microservices
 * Optimized for Debian 13 and Android integration
 */

module.exports = {
    // =========================================================================
    // ENVIRONMENT
    // =========================================================================
    environment: process.env.NODE_ENV || 'production',
    isDevelopment: process.env.NODE_ENV === 'development',
    isProduction: process.env.NODE_ENV === 'production',

    // =========================================================================
    // SERVICES CONFIGURATION
    // =========================================================================
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

    // =========================================================================
    // SOCKET.IO CONFIGURATION
    // =========================================================================
    socketIO: {
        // Transport configuration - polling FIRST for reliability
        transports: ['polling', 'websocket'],
        
        // Connection timeouts
        pingInterval: 30000,      // Send ping every 30 seconds
        pingTimeout: 60000,       // Wait 60 seconds for pong
        upgradeTimeout: 10000,    // Timeout for WebSocket upgrade
        
        // Reconnection
        reconnection: true,
        reconnectionDelay: 1000,
        reconnectionDelayMax: 5000,
        reconnectionAttempts: Infinity,
        
        // CORS for Android app
        cors: {
            origin: process.env.ALLOWED_ORIGINS?.split(',') || ['*'],
            methods: ['GET', 'POST'],
            credentials: true,
        },

        // Parser configuration
        serializationMethod: 'default',
        
        // Buffer management
        maxHttpBufferSize: 1e6,  // 1MB max
    },

    // =========================================================================
    // DATABASE CONFIGURATION
    // =========================================================================
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

    // =========================================================================
    // ANDROID APP CONFIGURATION
    // =========================================================================
    androidApp: {
        // Service endpoints for Android app connection
        endpoints: {
            // Polling service (primary, always works)
            polling: {
                protocol: process.env.POLLING_PROTOCOL || 'https',
                host: process.env.POLLING_HOST || process.env.DOMAIN || 'example.com',
                port: process.env.POLLING_PORT || 443,
                path: process.env.POLLING_PATH || '/socket.io',
            },
            // WebSocket service (secondary, low-latency)
            websocket: {
                protocol: process.env.WEBSOCKET_PROTOCOL || 'wss',
                host: process.env.WEBSOCKET_HOST || process.env.DOMAIN || 'example.com',
                port: process.env.WEBSOCKET_PORT || 443,
                path: process.env.WEBSOCKET_PATH || '/ws',
            },
        },
        
        // Android authentication
        auth: {
            tokenHeader: 'Authorization',
            tokenPrefix: 'Bearer',
            jwtSecret: process.env.JWT_SECRET || 'your-secret-key',
            jwtExpiry: '7d',
        },

        // Timeout configuration for Android requests
        timeouts: {
            connectionTimeout: 15000,  // 15 seconds
            requestTimeout: 30000,     // 30 seconds
            callSetupTimeout: 60000,   // 60 seconds for calls
        },
    },

    // =========================================================================
    // HEALTH CHECK CONFIGURATION
    // =========================================================================
    healthCheck: {
        // Health check interval
        interval: 5000,           // Check every 5 seconds
        
        // Service is considered unhealthy if no heartbeat in this time
        timeout: 15000,           // 15 seconds
        
        // Check endpoints
        endpoints: {
            call: 'http://localhost:3001/health',
            messaging: 'http://localhost:3002/health',
            websocket: 'http://localhost:3003/health',
            polling: 'http://localhost:3004/health',
            peerManager: 'http://localhost:3005/health',
        },
    },

    // =========================================================================
    // LOGGING CONFIGURATION
    // =========================================================================
    logging: {
        level: process.env.LOG_LEVEL || 'info',
        format: 'json',
        
        // Log rotation
        maxSize: '100m',
        maxFiles: 10,
        
        // Files
        errorFile: 'logs/error.log',
        combinedFile: 'logs/combined.log',
    },

    // =========================================================================
    // DEBIAN 13 SPECIFIC SETTINGS
    // =========================================================================
    debian: {
        // User to run services as (optional)
        runAsUser: process.env.RUN_AS_USER || 'node',
        
        // System limits
        maxOpenFiles: 65536,
        maxConnections: 10000,
        
        // Process management
        pidDirectory: process.env.PID_DIR || './.pids',
        logDirectory: process.env.LOG_DIR || './logs',
        
        // Service startup
        serviceRestartOnFailure: true,
        serviceRestartDelay: 3000,
        
        // Systemd integration (if running as service)
        systemd: {
            enabled: process.env.SYSTEMD_ENABLED === 'true',
            unitFile: '/etc/systemd/system/freetime.service',
        },
    },

    // =========================================================================
    // AUTO-RECOVERY CONFIGURATION
    // =========================================================================
    autoRecovery: {
        // Enable autonomous recovery
        enabled: true,
        
        // Health check and restart strategy
        maxRestartAttempts: 5,
        restartDelay: 5000,
        
        // Auto-restart failed services
        autoRestartOnCrash: true,
        
        // Circuit breaker (stop restarting if repeated failures)
        circuitBreakerThreshold: 5,
        circuitBreakerResetTime: 60000,
    },

    // =========================================================================
    // PERFORMANCE OPTIMIZATION
    // =========================================================================
    performance: {
        // Connection pooling
        connectionPool: {
            min: 2,
            max: 10,
        },
        
        // Message queue settings
        messageQueueSize: 1000,
        messageFlushInterval: 100,
        
        // Worker threads (for CPU-intensive tasks)
        workerThreads: process.env.WORKER_THREADS || 4,
        
        // Memory management
        maxMemoryMB: process.env.MAX_MEMORY_MB || 512,
        gcInterval: 60000,  // Garbage collection every 60 seconds
    },

    // =========================================================================
    // SECURITY CONFIGURATION
    // =========================================================================
    security: {
        // CORS origins
        allowedOrigins: process.env.ALLOWED_ORIGINS?.split(',') || [
            'http://localhost:*',
            'https://localhost:*',
        ],
        
        // Rate limiting
        rateLimit: {
            enabled: true,
            windowMs: 15 * 60 * 1000,  // 15 minutes
            maxRequests: 1000,
        },
        
        // HTTPS/SSL
        ssl: {
            enabled: process.env.SSL_ENABLED === 'true',
            keyFile: process.env.SSL_KEY_FILE,
            certFile: process.env.SSL_CERT_FILE,
        },
        
        // Authentication
        requireAuth: true,
        tokenExpiry: '7d',
    },

    // =========================================================================
    // GETTING ANDROID APP CONFIGURATION
    // =========================================================================
    getAndroidConfig: function() {
        return {
            services: {
                polling: {
                    url: `${this.androidApp.endpoints.polling.protocol}://${this.androidApp.endpoints.polling.host}:${this.androidApp.endpoints.polling.port}${this.androidApp.endpoints.polling.path}`,
                    type: 'polling',
                    priority: 1,  // Primary
                },
                websocket: {
                    url: `${this.androidApp.endpoints.websocket.protocol}://${this.androidApp.endpoints.websocket.host}:${this.androidApp.endpoints.websocket.port}${this.androidApp.endpoints.websocket.path}`,
                    type: 'websocket',
                    priority: 2,  // Secondary
                },
            },
            timeouts: this.androidApp.timeouts,
            auth: this.androidApp.auth,
        };
    },

    // =========================================================================
    // VALIDATE CONFIGURATION
    // =========================================================================
    validate: function() {
        const errors = [];

        // Check required services
        const requiredServices = ['admin', 'call', 'messaging', 'websocket', 'polling', 'peerManager'];
        for (const service of requiredServices) {
            if (!this.services[service]) {
                errors.push(`Service configuration missing: ${service}`);
            }
        }

        // Check port uniqueness
        const ports = Object.values(this.services).map(s => s.port);
        const uniquePorts = new Set(ports);
        if (ports.length !== uniquePorts.size) {
            errors.push('Service ports must be unique');
        }

        // Check port range
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
