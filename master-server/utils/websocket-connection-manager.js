/**
 * WebSocket Connection Manager with Heartbeat & Auto-Reconnection
 * Provides resilient WebSocket connections with automatic recovery
 * 
 * Features:
 * - Automatic heartbeat/ping-pong
 * - Exponential backoff reconnection
 * - Connection state tracking
 * - Message queue during disconnect
 * - Graceful connection lifecycle
 */

const WebSocket = require('ws');
const { getLogger } = require('./logger');
const logger = getLogger('WebSocketConnectionManager');

class WebSocketConnectionManager {
    constructor(url, options = {}) {
        this.url = url;
        this.options = {
            heartbeatIntervalMs: options.heartbeatIntervalMs || 30000,      // 30 seconds
            heartbeatTimeoutMs: options.heartbeatTimeoutMs || 5000,         // 5 second response timeout
            maxReconnectAttempts: options.maxReconnectAttempts || 10,
            reconnectDelayMs: options.reconnectDelayMs || 1000,
            maxReconnectDelayMs: options.maxReconnectDelayMs || 30000,
            messageQueueSize: options.messageQueueSize || 1000,             // Max queued messages
            ...options
        };

        this.websocket = null;
        this.isConnected = false;
        this.connectionState = 'disconnected'; // disconnected, connecting, connected, failed
        this.reconnectAttempts = 0;
        this.reconnectTimeout = null;
        this.heartbeatInterval = null;
        this.messageQueue = [];
        this.handlers = {};
        this.lastHeartbeatTime = null;
        this.isAlive = true;
    }

    /**
     * Connect to WebSocket server with retry logic
     */
    async connect() {
        return new Promise((resolve, reject) => {
            for (let attempt = 1; attempt <= this.options.maxReconnectAttempts; attempt++) {
                setTimeout(() => {
                    this._attemptConnection(attempt, resolve, reject);
                }, attempt === 1 ? 0 : this._getBackoffDelay(attempt - 1));
            }
        });
    }

    /**
     * Internal method to attempt single connection
     */
    _attemptConnection(attempt, resolve, reject) {
        try {
            this.connectionState = 'connecting';
            logger.debug(`WebSocket connection attempt ${attempt}/${this.options.maxReconnectAttempts}`);

            this.websocket = new WebSocket(this.url);

            this.websocket.on('open', () => {
                logger.info('WebSocket connected successfully', {
                    url: this.url,
                    attempt: attempt
                });

                this.isConnected = true;
                this.connectionState = 'connected';
                this.reconnectAttempts = 0;

                // Start heartbeat monitoring
                this._startHeartbeat();

                // Flush message queue
                this._flushQueue();

                this._registerEventHandlers();
                resolve({ success: true, attempt });
            });

            this.websocket.on('error', (err) => {
                logger.error(`WebSocket connection error: ${err.message}`, {
                    url: this.url,
                    attempt: attempt
                });

                if (this.isConnected) {
                    // Was connected, now errored
                    this.isConnected = false;
                    this.connectionState = 'failed';
                }

                if (attempt === this.options.maxReconnectAttempts) {
                    this.connectionState = 'failed';
                    reject(new Error(`WebSocket connection failed after ${this.options.maxReconnectAttempts} attempts`));
                }
            });

            this.websocket.on('close', () => {
                logger.warn('WebSocket connection closed', {
                    url: this.url,
                    wasConnected: this.isConnected
                });

                this.isConnected = false;
                this.connectionState = 'disconnected';
                this._stopHeartbeat();

                if (!this.isConnected && this.reconnectAttempts < this.options.maxReconnectAttempts) {
                    this._scheduleReconnect();
                }
            });

        } catch (err) {
            logger.error(`WebSocket connection exception: ${err.message}`);
            if (attempt === this.options.maxReconnectAttempts) {
                reject(err);
            }
        }
    }

    /**
     * Register WebSocket event handlers
     */
    _registerEventHandlers() {
        if (!this.websocket) return;

        this.websocket.on('message', (data) => {
            try {
                const message = JSON.parse(data);
                this._handleMessage(message);
            } catch (err) {
                logger.error('Failed to parse WebSocket message:', err.message);
            }
        });

        this.websocket.on('pong', () => {
            this.isAlive = true;
            this.lastHeartbeatTime = Date.now();
            logger.debug('WebSocket heartbeat: pong received');
        });
    }

    /**
     * Handle incoming WebSocket messages
     */
    _handleMessage(message) {
        const { type, data } = message;

        if (type && this.handlers[type]) {
            logger.debug(`WebSocket message received: ${type}`);
            this.handlers[type](data);
        }
    }

    /**
     * Start heartbeat monitoring
     */
    _startHeartbeat() {
        if (this.heartbeatInterval) return;

        this.heartbeatInterval = setInterval(() => {
            if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
                logger.warn('WebSocket not open during heartbeat check');
                return;
            }

            if (!this.isAlive) {
                logger.warn('WebSocket missed heartbeat - terminating connection');
                this.websocket.terminate();
                this.isConnected = false;
                this.connectionState = 'disconnected';
                return;
            }

            this.isAlive = false;
            logger.debug('Sending WebSocket heartbeat...');

            try {
                this.websocket.ping(() => {
                    // Ping sent
                });
            } catch (err) {
                logger.error('Failed to send WebSocket heartbeat:', err.message);
            }
        }, this.options.heartbeatIntervalMs);

        logger.info(`WebSocket heartbeat started (interval: ${this.options.heartbeatIntervalMs}ms)`);
    }

    /**
     * Stop heartbeat monitoring
     */
    _stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
            logger.debug('WebSocket heartbeat stopped');
        }
    }

    /**
     * Schedule automatic reconnection
     */
    _scheduleReconnect() {
        if (this.reconnectTimeout) return;

        this.reconnectAttempts++;
        const delay = this._getBackoffDelay(this.reconnectAttempts - 1);

        logger.info(`WebSocket reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

        this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this._attemptConnection(
                this.reconnectAttempts,
                () => logger.info('WebSocket reconnected'),
                (err) => logger.error('WebSocket reconnection failed:', err.message)
            );
        }, delay);
    }

    /**
     * Calculate exponential backoff delay
     */
    _getBackoffDelay(attemptNumber) {
        const delay = Math.min(
            this.options.reconnectDelayMs * Math.pow(2, attemptNumber),
            this.options.maxReconnectDelayMs
        );
        return delay;
    }

    /**
     * Queue message if disconnected
     */
    _queueMessage(message) {
        if (this.messageQueue.length >= this.options.messageQueueSize) {
            const dropped = this.messageQueue.shift();
            logger.warn('Message queue full, dropping oldest message');
        }
        this.messageQueue.push(message);
    }

    /**
     * Flush queued messages
     */
    _flushQueue() {
        if (this.messageQueue.length === 0) return;

        logger.info(`Flushing ${this.messageQueue.length} queued messages`);

        while (this.messageQueue.length > 0 && this.isConnected) {
            const message = this.messageQueue.shift();
            try {
                this.websocket.send(JSON.stringify(message));
            } catch (err) {
                logger.error('Failed to send queued message:', err.message);
                this._queueMessage(message); // Re-queue on failure
                break;
            }
        }
    }

    /**
     * Send message
     */
    send(type, data = {}) {
        const message = { type, data, timestamp: Date.now() };

        if (!this.isConnected || !this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
            logger.warn(`WebSocket not connected, queueing message: ${type}`);
            this._queueMessage(message);
            return false;
        }

        try {
            this.websocket.send(JSON.stringify(message));
            logger.debug(`WebSocket message sent: ${type}`);
            return true;
        } catch (err) {
            logger.error(`Failed to send WebSocket message: ${err.message}`);
            this._queueMessage(message);
            return false;
        }
    }

    /**
     * Register message handler
     */
    on(type, handler) {
        this.handlers[type] = handler;
        logger.debug(`WebSocket handler registered for: ${type}`);
    }

    /**
     * Remove message handler
     */
    off(type) {
        delete this.handlers[type];
        logger.debug(`WebSocket handler removed for: ${type}`);
    }

    /**
     * Get connection status
     */
    getStatus() {
        const readyState = this.websocket?.readyState;
        const readyStateMap = {
            0: 'connecting',
            1: 'open',
            2: 'closing',
            3: 'closed'
        };

        return {
            connected: this.isConnected,
            state: this.connectionState,
            readyState: readyStateMap[readyState] || 'unknown',
            reconnectAttempts: this.reconnectAttempts,
            messageQueueLength: this.messageQueue.length,
            lastHeartbeat: this.lastHeartbeatTime,
            isAlive: this.isAlive,
            timestamp: new Date().toISOString()
        };
    }

    /**
     * Gracefully close connection
     */
    async close() {
        logger.info('WebSocket Connection Manager: Closing...');

        // Cancel pending reconnect
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        // Stop heartbeat
        this._stopHeartbeat();

        // Close WebSocket
        if (this.websocket) {
            try {
                this.websocket.close(1000, 'Normal closure');
                this.isConnected = false;
                this.connectionState = 'disconnected';
                logger.info('WebSocket closed successfully');
            } catch (err) {
                logger.error('Error closing WebSocket:', err.message);
            }
        }
    }

    /**
     * Get statistics
     */
    getStats() {
        return {
            ...this.getStatus(),
            url: this.url,
            options: {
                heartbeatIntervalMs: this.options.heartbeatIntervalMs,
                maxReconnectAttempts: this.options.maxReconnectAttempts,
                reconnectDelayMs: this.options.reconnectDelayMs
            }
        };
    }
}

module.exports = WebSocketConnectionManager;
