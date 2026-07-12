/**
 * Connection Manager
 * Handles peer connection lifecycle and pooling
 * Supports 80k concurrent connections with connection limits
 */

const { EventEmitter } = require('events');

class ConnectionManager extends EventEmitter {
    constructor(options = {}) {
        super();
        
        this.maxConnections = options.maxConnections || 80000;
        this.connectionTimeout = options.connectionTimeout || 30000;
        this.connections = new Map();
        this.connectionsByUser = new Map();
        this.connectionStats = {
            created: 0,
            destroyed: 0,
            active: 0,
            peak: 0
        };
    }

    /**
     * Register a new connection
     */
    registerConnection(connection) {
        if (this.connections.size >= this.maxConnections) {
            throw new Error('Max connections reached');
        }

        this.connections.set(connection.id, connection);

        if (connection.userId) {
            if (!this.connectionsByUser.has(connection.userId)) {
                this.connectionsByUser.set(connection.userId, []);
            }
            this.connectionsByUser.get(connection.userId).push(connection);
        }

        this.connectionStats.created++;
        this.connectionStats.active = this.connections.size;
        
        if (this.connectionStats.active > this.connectionStats.peak) {
            this.connectionStats.peak = this.connectionStats.active;
        }

        this.emit('connection:registered', connection);
        return connection;
    }

    /**
     * Unregister a connection
     */
    unregisterConnection(connectionId) {
        const connection = this.connections.get(connectionId);
        
        if (!connection) return null;

        this.connections.delete(connectionId);

        if (connection.userId) {
            const userConnections = this.connectionsByUser.get(connection.userId) || [];
            const index = userConnections.indexOf(connection);
            if (index > -1) {
                userConnections.splice(index, 1);
            }
        }

        this.connectionStats.destroyed++;
        this.connectionStats.active = this.connections.size;

        this.emit('connection:unregistered', connection);
        return connection;
    }

    /**
     * Get connection by ID
     */
    getConnection(connectionId) {
        return this.connections.get(connectionId);
    }

    /**
     * Get all connections for a user
     */
    getUserConnections(userId) {
        return this.connectionsByUser.get(userId) || [];
    }

    /**
     * Check if user has active connections
     */
    hasUserConnections(userId) {
        return this.getUserConnections(userId).length > 0;
    }

    /**
     * Broadcast message to all user connections
     */
    broadcastToUser(userId, event, data) {
        const connections = this.getUserConnections(userId);
        connections.forEach(conn => {
            if (conn.socket) {
                conn.socket.emit(event, data);
            }
        });
        return connections.length;
    }

    /**
     * Get connection stats
     */
    getStats() {
        return {
            ...this.connectionStats,
            active: this.connections.size,
            utilizationPercent: Math.round((this.connections.size / this.maxConnections) * 100),
            totalUsers: this.connectionsByUser.size
        };
    }

    /**
     * Cleanup stale connections
     */
    cleanupStaleConnections(timeout = this.connectionTimeout) {
        const now = Date.now();
        let cleaned = 0;

        for (const [id, connection] of this.connections.entries()) {
            if (now - connection.lastActivity > timeout) {
                this.unregisterConnection(id);
                cleaned++;
            }
        }

        return cleaned;
    }

    /**
     * Get utilization level (0-5)
     * 0: <20%, 1: 20-40%, 2: 40-60%, 3: 60-80%, 4: 80-95%, 5: >95%
     */
    getUtilizationLevel() {
        const percent = (this.connections.size / this.maxConnections) * 100;
        if (percent < 20) return 0;
        if (percent < 40) return 1;
        if (percent < 60) return 2;
        if (percent < 80) return 3;
        if (percent < 95) return 4;
        return 5;
    }
}

module.exports = ConnectionManager;
