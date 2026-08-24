const http = require('http');
const subprocess = require('child_process');
const fs = require('fs');
const path = require('path');

class AutoRecoveryDaemon {
    constructor(serviceManager) {
        this.serviceManager = serviceManager;

        this.config = {
            healthCheckInterval: 30000,
            healthCheckTimeout: 5000,
            maxFailuresBeforePause: 3,
            pauseDuration: 300000,
            maxRestarts: 3,
            restartWindow: 3600000,
        };

        this.serviceState = new Map();
        this.healthCheckLoop = null;
        this.logger = console;

        this.log(` Auto-Recovery Daemon Initialized`);
        this.log(` Health Check Interval: ${this.config.healthCheckInterval}ms`);
        this.log(` Max Failures: ${this.config.maxFailuresBeforePause}`);
        this.log(` Pause Duration: ${this.config.pauseDuration / 1000}s`);
    }

    start() {
        if (this.healthCheckLoop) {
            this.log(` Auto-recovery already running`);
            return;
        }

        this.log(` Starting Auto-Recovery Daemon...`);

        this.checkAllServices();

        this.healthCheckLoop = setInterval(
            () => this.checkAllServices(),
            this.config.healthCheckInterval
        );

        this.log(` Auto-Recovery Daemon Started`);
    }

    stop() {
        if (this.healthCheckLoop) {
            clearInterval(this.healthCheckLoop);
            this.healthCheckLoop = null;
            this.log(` Auto-Recovery Daemon Stopped`);
        }
    }

    async checkAllServices() {
        const services = this.serviceManager.getAllServices();

        for (const [serviceId, serviceConfig] of Object.entries(services)) {
            try {
                await this.checkServiceHealth(serviceId, serviceConfig);
            } catch (error) {
                this.logError(`Error checking ${serviceId}`, error);
            }
        }
    }

    async checkServiceHealth(serviceId, serviceConfig) {
        const port = serviceConfig.port;
        const state = this.getServiceState(serviceId);

        if (state.paused) {
            this.log(` ${serviceId} (port ${port}): Paused (cooldown)`);
            return;
        }

        return new Promise((resolve) => {
            const healthUrl = `http://127.0.0.1:${port}/health`;

            const request = http.get(healthUrl, { timeout: this.config.healthCheckTimeout }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        const health = JSON.parse(data);
                        if (res.statusCode === 200 && health.status === 'healthy') {
                            this.onServiceHealthy(serviceId, state);
                            resolve(true);
                        } else {
                            this.onServiceUnhealthy(serviceId, serviceConfig, state, 'Invalid health response');
                            resolve(false);
                        }
                    } catch (e) {
                        this.onServiceUnhealthy(serviceId, serviceConfig, state, 'JSON parse error');
                        resolve(false);
                    }
                });
            });

            request.on('timeout', () => {
                request.destroy();
                this.onServiceUnhealthy(serviceId, serviceConfig, state, 'Health check timeout');
                resolve(false);
            });

            request.on('error', (error) => {
                this.onServiceUnhealthy(serviceId, serviceConfig, state, error.message);
                resolve(false);
            });
        });
    }

    onServiceHealthy(serviceId, state) {
        const wasUnhealthy = state.failures > 0;

        state.failures = 0;
        state.consecutiveRestarts = 0;

        if (wasUnhealthy) {
            this.log(` ${serviceId}: RECOVERED (failures reset)`);
        }
    }

    async onServiceUnhealthy(serviceId, serviceConfig, state, reason) {
        state.failures++;

        this.log(` ${serviceId} (port ${serviceConfig.port}): UNHEALTHY`);
        this.log(` Reason: ${reason}`);
        this.log(` Consecutive Failures: ${state.failures}/${this.config.maxFailuresBeforePause}`);

        if (state.failures >= this.config.maxFailuresBeforePause) {
            await this.onMaxFailuresReached(serviceId, state);
        } else if (state.failures === 1) {
            await this.attemptServiceRestart(serviceId, serviceConfig, state);
        }
    }

    async attemptServiceRestart(serviceId, serviceConfig, state) {
        const now = Date.now();
        // max restarts per hour per service
        const restartsInWindow = state.restarts.filter(
            time => (now - time) < this.config.restartWindow
        ).length;

        if (restartsInWindow >= this.config.maxRestarts) {
            this.log(` ${serviceId}: Too many restarts (${restartsInWindow}/${this.config.maxRestarts} in 1 hour)`);
            this.log(` Entering pause mode to prevent restart loop`);
            await this.pauseServiceRecovery(serviceId, state);
            return;
        }

        this.log(` ${serviceId}: Attempting restart...`);

        try {
            const result = await this.serviceManager.restartService(serviceId);

            if (result.success) {
                this.log(` ${serviceId}: Restart SUCCESSFUL`);
                state.restarts.push(Date.now());
                state.lastRestart = Date.now();

                await this.sleep(2000);
                state.failures = 0;
            } else {
                this.log(` ${serviceId}: Restart FAILED - ${result.error}`);
            }
        } catch (error) {
            this.logError(`${serviceId}: Restart exception`, error);
        }
    }

    async onMaxFailuresReached(serviceId, state) {
        this.log(` ${serviceId}: Max failures reached - entering PAUSE MODE`);
        await this.pauseServiceRecovery(serviceId, state);
    }

    async pauseServiceRecovery(serviceId, state) {
        state.paused = true;
        state.pauseUntil = Date.now() + this.config.pauseDuration;

        this.log(` ${serviceId}: Recovery paused for ${this.config.pauseDuration / 1000}s`);

        setTimeout(() => {
            state.paused = false;
            state.failures = 0;
            state.restarts = [];
            this.log(` ${serviceId}: Recovery pause lifted - will retry soon`);
        }, this.config.pauseDuration);
    }

    getServiceState(serviceId) {
        if (!this.serviceState.has(serviceId)) {
            this.serviceState.set(serviceId, {
                failures: 0,
                restarts: [],
                lastRestart: null,
                paused: false,
                pauseUntil: null,
                consecutiveRestarts: 0,
            });
        }
        return this.serviceState.get(serviceId);
    }

    clearServiceState(serviceId) {
        this.serviceState.delete(serviceId);
        this.log(` ${serviceId}: Recovery state cleared`);
    }

    getRecoveryStatus() {
        const status = {};

        for (const [serviceId, state] of this.serviceState.entries()) {
            status[serviceId] = {
                failures: state.failures,
                paused: state.paused,
                lastRestart: state.lastRestart ? new Date(state.lastRestart).toISOString() : null,
                restartCount: state.restarts.length,
                pauseUntil: state.pauseUntil ? new Date(state.pauseUntil).toISOString() : null,
            };
        }

        return status;
    }

    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    log(message) {
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] ${message}`);
    }

    logError(message, error) {
        const timestamp = new Date().toISOString();
        console.error(`[${timestamp}] ${message}:`, error.message);
    }
}

module.exports = AutoRecoveryDaemon;
