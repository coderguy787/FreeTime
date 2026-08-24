class PoolMonitor {
    constructor(mongoClient, options = {}) {
        this.client = mongoClient;
        this.sampleInterval = options.sampleInterval || 10000;
        this.maxMetrics = options.maxMetrics || 1440;
        this.metrics = [];
        this.isRunning = false;
    }

    start() {
        if (this.isRunning) return;

        this.isRunning = true;
        console.log(' Connection pool monitoring started');

        this.sampleInterval = setInterval(() => {
            this.collectMetrics();
        }, this.sampleInterval);
    }

    stop() {
        if (!this.isRunning) return;

        this.isRunning = false;
        if (this.sampleInterval) {
            clearInterval(this.sampleInterval);
        }
        console.log(' Connection pool monitoring stopped');
    }

    collectMetrics() {
        try {
            if (!this.client || !this.client.topology) {
                return;
            }

            const topology = this.client.topology;
            const servers = topology.description.servers || [];

            let totalConnections = 0;
            let availableConnections = 0;
            let checkedOutConnections = 0;

            servers.forEach(server => {
                if (server.connectionPool) {
                    const pool = server.connectionPool;
                    totalConnections += pool.totalConnectionCount || 0;
                    availableConnections += pool.availableConnectionCount || 0;
                    checkedOutConnections += (pool.totalConnectionCount || 0) - (pool.availableConnectionCount || 0);
                }
            });

            const metric = {
                timestamp: new Date(),
                totalConnections,
                availableConnections,
                checkedOutConnections,
                utilization: totalConnections > 0 ?
                    ((checkedOutConnections / totalConnections) * 100).toFixed(2) + '%' : '0%',
                serverCount: servers.length,
                topology: topology.description.type
            };

            this.metrics.push(metric);

            // keep about 4h of samples
            if (this.metrics.length > this.maxMetrics) {
                this.metrics.shift();
            }

            const utilization = checkedOutConnections / (totalConnections || 1);
            if (utilization > 0.8) {
                console.warn(
                    ` High pool utilization: ${(utilization * 100).toFixed(1)}% ` +
                    `(${checkedOutConnections}/${totalConnections} connections)`
                );
            }
        } catch (err) {
            console.error('Pool monitoring error:', err);
        }
    }

    getStatus() {
        if (this.metrics.length === 0) {
            return { status: 'no_data' };
        }

        const latest = this.metrics[this.metrics.length - 1];
        return latest;
    }

    getStats() {
        if (this.metrics.length === 0) {
            return { status: 'no_data' };
        }

        const utilizations = this.metrics.map(m => {
            const total = m.totalConnections || 1;
            return (m.checkedOutConnections / total) * 100;
        });

        const avgUtil = utilizations.reduce((a, b) => a + b, 0) / utilizations.length;
        const maxUtil = Math.max(...utilizations);
        const minUtil = Math.min(...utilizations);

        return {
            sampleCount: this.metrics.length,
            avgUtilization: avgUtil.toFixed(2) + '%',
            maxUtilization: maxUtil.toFixed(2) + '%',
            minUtilization: minUtil.toFixed(2) + '%',
            currentStatus: this.getStatus(),
            lastUpdated: this.metrics[this.metrics.length - 1]?.timestamp
        };
    }

    getHistory(count = 60) {
        return this.metrics.slice(-count);
    }

    checkHealth() {
        const status = this.getStatus();

        const health = {
            healthy: true,
            warnings: [],
            errors: []
        };

        if (status.status === 'no_data') {
            health.healthy = false;
            health.errors.push('No connection pool data available');
            return health;
        }

        const utilPercent = parseFloat(status.utilization);
        if (utilPercent > 90) {
            health.healthy = false;
            health.errors.push(`Critical pool utilization: ${status.utilization}`);
        } else if (utilPercent > 75) {
            health.warnings.push(`High pool utilization: ${status.utilization}`);
        }

        if (status.availableConnections === 0 && status.checkedOutConnections > 0) {
            health.warnings.push('No available connections in pool');
        }

        return health;
    }
}

module.exports = {
    PoolMonitor
};
