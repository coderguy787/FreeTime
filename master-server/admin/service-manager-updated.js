const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const MetricsTracker = require('./metrics');
const AutoRecoveryDaemon = require('./auto-recovery-daemon');

class ServiceManager {
    constructor() {
        this.services = new Map();
        this.metrics = new MetricsTracker();
        this.logsDir = path.join(process.cwd(), 'logs');
        this.pidsDir = path.join(process.cwd(), '.pids');

        [this.logsDir, this.pidsDir].forEach(dir => {
            if (!fs.existsSync(dir)) {
                fs.mkdirSync(dir, { recursive: true });
            }
        });

        this.autoRecovery = new AutoRecoveryDaemon(this);

        this.log(' ServiceManager Initialized');
    }

    registerService(id, config) {
        if (this.services.has(id)) {
            this.log(` Service ${id} already registered`);
            return;
        }

        this.services.set(id, {
            id,
            config,
            process: null,
            instance: null,
            status: 'stopped',
            metrics: {
                connections: 0,
                messagesPerMin: 0,
                callsPerMin: 0,
                errorsPerMin: 0,
                uptime: 0,
                startTime: null,
                lastHealthCheck: null,
                healthStatus: 'unknown',
            },
            startAttempts: 0,
            lastStartTime: null,
        });

        this.log(` Registered service: ${id} (port ${config.port})`);
    }

    async startService(serviceId) {
        const service = this.services.get(serviceId);
        if (!service) {
            return { success: false, error: `Service ${serviceId} not found` };
        }

        if (service.status === 'running') {
            return { success: false, error: `Service ${serviceId} already running` };
        }

        try {
            this.log(` Starting service: ${serviceId}...`);

            const scriptPath = path.join(process.cwd(), service.config.path);
            const logFile = path.join(this.logsDir, `${serviceId}.log`);
            const pidFile = path.join(this.pidsDir, `${serviceId}.pid`);

            // detach services so they survive a manager restart
            const process = spawn('node', [scriptPath], {
                cwd: process.cwd(),
                detached: process.platform !== 'win32',
                stdio: ['ignore', 'pipe', 'pipe'],
            });

            process.stdout.on('data', (data) => {
                fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${data}`);
            });

            process.stderr.on('data', (data) => {
                fs.appendFileSync(logFile, `[${new Date().toISOString()}] ERROR: ${data}`);
            });

            process.on('exit', (code, signal) => {
                this.log(` Service ${serviceId} exited (code ${code}, signal ${signal})`);
                service.status = 'stopped';
                service.process = null;
                if (this.autoRecovery.serviceState.has(serviceId)) {
                    this.autoRecovery.clearServiceState(serviceId);
                }
            });

            fs.writeFileSync(pidFile, process.pid.toString());

            service.process = process;
            service.status = 'starting';
            service.startAttempts++;
            service.lastStartTime = Date.now();
            service.metrics.startTime = new Date();

            const ready = await this.waitForServiceReady(serviceId, 10000);

            if (ready) {
                service.status = 'running';
                this.log(` Service ${serviceId} started successfully (PID: ${process.pid})`);
                this.autoRecovery.clearServiceState(serviceId);
                return { success: true, processId: process.pid };
            } else {
                this.log(` Service ${serviceId} did not respond to health check`);
                service.status = 'unhealthy';
                return { success: false, error: 'Health check timeout' };
            }

        } catch (error) {
            service.status = 'failed';
            this.log(` Failed to start ${serviceId}: ${error.message}`);
            return { success: false, error: error.message };
        }
    }

    async stopService(serviceId) {
        const service = this.services.get(serviceId);
        if (!service) {
            return { success: false, error: `Service ${serviceId} not found` };
        }

        if (serviceId === 'admin-panel') {
            return { success: false, error: 'Cannot stop Admin Panel Service (system service)' };
        }

        if (service.status === 'stopped') {
            return { success: false, error: `Service ${serviceId} already stopped` };
        }

        try {
            this.log(` Stopping service: ${serviceId}...`);

            if (service.process) {
                service.process.kill('SIGTERM');

                // wait 5s then force kill
                const shutdownTimeout = 5000;
                const shutdownStart = Date.now();

                while (service.process && (Date.now() - shutdownStart) < shutdownTimeout) {
                    await this.sleep(100);
                }

                if (service.process) {
                    this.log(` ${serviceId} did not stop gracefully, force killing...`);
                    service.process.kill('SIGKILL');
                }
            }

            const pidFile = path.join(this.pidsDir, `${serviceId}.pid`);
            if (fs.existsSync(pidFile)) {
                fs.unlinkSync(pidFile);
            }

            service.status = 'stopped';
            service.process = null;
            this.log(` Service ${serviceId} stopped`);

            return { success: true };

        } catch (error) {
            this.log(` Failed to stop ${serviceId}: ${error.message}`);
            return { success: false, error: error.message };
        }
    }

    async restartService(serviceId) {
        const service = this.services.get(serviceId);
        if (!service) {
            return { success: false, error: `Service ${serviceId} not found` };
        }

        this.log(` Restarting service: ${serviceId}...`);

        await this.stopService(serviceId);
        await this.sleep(1000);

        const result = await this.startService(serviceId);

        return result;
    }

    async waitForServiceReady(serviceId, timeout = 10000) {
        const service = this.services.get(serviceId);
        const port = service.config.port;
        const startTime = Date.now();

        while ((Date.now() - startTime) < timeout) {
            try {
                const response = await this.checkServiceHealth(serviceId);
                if (response && response.status === 'healthy') {
                    return true;
                }
            } catch (e) {
            }

            await this.sleep(500);
        }

        return false;
    }

    async checkServiceHealth(serviceId) {
        return new Promise((resolve) => {
            const service = this.services.get(serviceId);
            if (!service) {
                resolve(null);
                return;
            }

            const port = service.config.port;
            const http = require('http');

            const request = http.get(
                `http://127.0.0.1:${port}/health`,
                { timeout: 3000 },
                (res) => {
                    let data = '';
                    res.on('data', chunk => data += chunk);
                    res.on('end', () => {
                        try {
                            resolve(JSON.parse(data));
                        } catch (e) {
                            resolve(null);
                        }
                    });
                }
            );

            request.on('timeout', () => {
                request.destroy();
                resolve(null);
            });

            request.on('error', () => {
                resolve(null);
            });
        });
    }

    serviceStarted(serviceId, instance) {
        const service = this.services.get(serviceId);
        if (service) {
            service.instance = instance;
            service.status = 'running';
            service.metrics.startTime = new Date();
            this.log(` Service ${serviceId} instance registered`);
        }
    }

    serviceFailed(serviceId, error) {
        const service = this.services.get(serviceId);
        if (service) {
            service.status = 'failed';
            this.log(` Service ${serviceId} failed: ${error}`);
        }
    }

    getAllServices() {
        const servicesData = {};

        for (const [id, service] of this.services.entries()) {
            servicesData[id] = {
                id: service.id,
                port: service.config.port,
                path: service.config.path,
                status: service.status,
                metrics: service.metrics,
                uptime: service.metrics.startTime
                    ? Math.floor((Date.now() - service.metrics.startTime.getTime()) / 1000)
                    : 0,
            };
        }

        return servicesData;
    }

    getDashboard() {
        const services = [];
        let totalConnections = 0;
        let healthyServices = 0;

        for (const [id, service] of this.services.entries()) {
            const isHealthy = service.status === 'running';
            if (isHealthy) healthyServices++;

            totalConnections += service.metrics.connections || 0;

            services.push({
                id,
                name: this.getServiceName(id),
                port: service.config.port,
                status: service.status,
                connections: service.metrics.connections || 0,
                messagesPerMin: service.metrics.messagesPerMin || 0,
                callsPerMin: service.metrics.callsPerMin || 0,
                errorsPerMin: service.metrics.errorsPerMin || 0,
                uptime: service.metrics.startTime
                    ? Math.floor((Date.now() - service.metrics.startTime.getTime()) / 1000)
                    : 0,
            });
        }

        const healthPercent = Math.round((healthyServices / this.services.size) * 100);

        return {
            timestamp: new Date().toISOString(),
            services,
            summary: {
                totalServices: this.services.size,
                healthyServices,
                healthPercent,
                totalConnections,
                recoveryStatus: this.autoRecovery?.getRecoveryStatus() || {},
            },
        };
    }

    startAutoRecovery() {
        this.log(` Starting Auto-Recovery Daemon...`);
        if (this.autoRecovery) {
            this.autoRecovery.start();
        }
    }

    stopAutoRecovery() {
        this.log(` Stopping Auto-Recovery Daemon...`);
        if (this.autoRecovery) {
            this.autoRecovery.stop();
        }
    }

    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    log(message) {
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] ${message}`);
    }

    getServiceName(serviceId) {
        const names = {
            'call-service': 'Call Service (3001)',
            'messaging-service': 'Messaging Service (3002)',
            'websocket-service': 'WebSocket Service (3003)',
            'polling-service': 'Polling Service (3004)',
            'peer-manager-service': 'Peer Manager Service (3005)',
            'admin-panel': 'Admin Panel Service (3006)',
        };
        return names[serviceId] || serviceId;
    }

    async startAllServices(selectiveServices = null) {
        const servicesInOrder = [
            'admin-panel',
            'call-service',
            'websocket-service',
            'polling-service',
            'peer-manager-service',
            'messaging-service',
        ];

        this.log('═══════════════════════════════════════════════━═══════');
        this.log(' Starting All Microservices');
        this.log('═══════════════════════════════════════════════━═══════');

        for (const serviceId of servicesInOrder) {
            if (selectiveServices && !selectiveServices.includes(serviceId)) {
                this.log(` Skipping ${serviceId} (not in startup list)`);
                continue;
            }

            const result = await this.startService(serviceId);
            if (!result.success) {
                this.log(` Warning: ${serviceId} failed to start`);
            }

            await this.sleep(1000);
        }

        this.log('═══════════════════════════════════════════════━═══════');
        this.log(' Service Startup Complete');
        this.log('═══════════════════════════════════════════════━═══════');

        this.startAutoRecovery();
    }

    async stopAllServices() {
        this.log('═══════════════════════════════════════════════━═══════');
        this.log(' Stopping All Microservices');
        this.log('═══════════════════════════════════════════════━═══════');

        this.stopAutoRecovery();
        await this.sleep(500);

        const servicesInOrder = [
            'messaging-service',
            'peer-manager-service',
            'polling-service',
            'websocket-service',
            'call-service',
            'admin-panel',
        ];

        for (const serviceId of servicesInOrder) {
            const result = await this.stopService(serviceId);
            if (!result.success) {
                this.log(` Warning: ${serviceId} did not stop cleanly`);
            }
            await this.sleep(500);
        }

        this.log('═══════════════════════════════════════════════━═══════');
        this.log(' All Services Stopped');
        this.log('═══════════════════════════════════════════════━═══════');
    }
}

module.exports = ServiceManager;
