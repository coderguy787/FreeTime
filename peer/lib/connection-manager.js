const { EventEmitter } = require('events');

// active connections registry
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

    getConnection(connectionId) {
        return this.connections.get(connectionId);
    }

    getUserConnections(userId) {
        return this.connectionsByUser.get(userId) || [];
    }

    hasUserConnections(userId) {
        return this.getUserConnections(userId).length > 0;
    }

    broadcastToUser(userId, event, data) {
        const connections = this.getUserConnections(userId);
        connections.forEach(conn => {
            if (conn.socket) {
                conn.socket.emit(event, data);
            }
        });
        return connections.length;
    }

    getStats() {
        return {
            ...this.connectionStats,
            active: this.connections.size,
            utilizationPercent: Math.round((this.connections.size / this.maxConnections) * 100),
            totalUsers: this.connectionsByUser.size
        };
    }

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
