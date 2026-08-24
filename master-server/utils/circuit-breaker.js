class CircuitBreaker {
    constructor(options = {}) {
        this.failureThreshold = options.failureThreshold || 5;
        this.successThreshold = options.successThreshold || 2;
        this.timeout = options.timeout || 60000;
        this.resetTimeout = options.resetTimeout || null;

        this.state = 'CLOSED';
        this.failureCount = 0;
        this.successCount = 0;
        this.nextAttemptTime = Date.now();
        this.lastFailureTime = null;
        this.lastErrorMessage = null;

        this.totalRequests = 0;
        this.totalFailures = 0;
        this.totalSuccesses = 0;
        this.totalRejections = 0;
    }

    async execute(operation) {
        this.totalRequests++;

        if (this.state === 'OPEN') {
            if (Date.now() < this.nextAttemptTime) {
                this.totalRejections++;
                // trip open when the service is down
                throw new Error(
                    `Circuit breaker is OPEN. ` +
                    `Failing fast. Last error: ${this.lastErrorMessage}. ` +
                    `Retry after ${Math.round((this.nextAttemptTime - Date.now()) / 1000)}s`
                );
            } else {
                this.state = 'HALF_OPEN';
                this.successCount = 0;
                console.log(' Circuit breaker entering HALF_OPEN state');
            }
        }

        try {
            const result = await operation();
            this.onSuccess();
            return result;
        } catch (error) {
            this.onFailure(error);
            throw error;
        }
    }

    onSuccess() {
        this.totalSuccesses++;
        this.failureCount = 0;
        this.lastErrorMessage = null;

        if (this.state === 'HALF_OPEN') {
            this.successCount++;

            if (this.successCount >= this.successThreshold) {
                this.close();
            }
        } else if (this.state === 'CLOSED') {
            if (this.failureCount === 0) {
                this.successCount = 0;
            }
        }
    }

    onFailure(error) {
        this.totalFailures++;
        this.failureCount++;
        this.lastFailureTime = Date.now();
        this.lastErrorMessage = error.message;

        if (this.state === 'HALF_OPEN') {
            this.open();
        } else if (this.state === 'CLOSED' && this.failureCount >= this.failureThreshold) {
            this.open();
        }
    }

    open() {
        this.state = 'OPEN';
        this.nextAttemptTime = Date.now() + this.timeout;
        console.error(
            ` Circuit breaker OPENED. Failing fast for ${this.timeout}ms. ` +
            `Reason: ${this.lastErrorMessage}`
        );
    }

    close() {
        this.state = 'CLOSED';
        this.failureCount = 0;
        this.successCount = 0;
        this.lastErrorMessage = null;
        console.log(' Circuit breaker CLOSED. Service recovered.');

        if (this.resetTimeout) {
            this.resetTimeout();
        }
    }

    getStatus() {
        return {
            state: this.state,
            failureCount: this.failureCount,
            successCount: this.successCount,
            totalRequests: this.totalRequests,
            totalFailures: this.totalFailures,
            totalSuccesses: this.totalSuccesses,
            totalRejections: this.totalRejections,
            successRate: this.totalRequests > 0 ?
                ((this.totalSuccesses / this.totalRequests) * 100).toFixed(2) + '%' : '0%',
            lastErrorMessage: this.lastErrorMessage,
            lastFailureTime: this.lastFailureTime,
            nextAttemptTime: this.state === 'OPEN' ? this.nextAttemptTime : null
        };
    }

    reset() {
        this.state = 'CLOSED';
        this.failureCount = 0;
        this.successCount = 0;
        this.nextAttemptTime = Date.now();
        this.lastFailureTime = null;
        this.lastErrorMessage = null;
        console.log(' Circuit breaker reset to CLOSED state');
    }

    forceOpen() {
        this.open();
    }

    forceClose() {
        this.close();
    }
}

class CircuitBreakerManager {
    constructor() {
        this.breakers = new Map();
    }

    getBreaker(serviceName, options = {}) {
        if (!this.breakers.has(serviceName)) {
            this.breakers.set(serviceName, new CircuitBreaker(options));
        }
        return this.breakers.get(serviceName);
    }

    async execute(serviceName, operation, options = {}) {
        const breaker = this.getBreaker(serviceName, options);
        return breaker.execute(operation);
    }

    getAllStatus() {
        const status = {};
        this.breakers.forEach((breaker, serviceName) => {
            status[serviceName] = breaker.getStatus();
        });
        return status;
    }

    resetAll() {
        this.breakers.forEach(breaker => breaker.reset());
    }

    removeBreaker(serviceName) {
        this.breakers.delete(serviceName);
    }
}

const globalBreakerManager = new CircuitBreakerManager();

module.exports = {
    CircuitBreaker,
    CircuitBreakerManager,
    globalBreakerManager
};
