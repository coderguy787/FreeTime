/**
 * Circuit Breaker Pattern Implementation
 * Prevents cascading failures by failing fast when a service is down
 * States: CLOSED (normal) → OPEN (failing fast) → HALF_OPEN (testing recovery)
 */

class CircuitBreaker {
    constructor(options = {}) {
        this.failureThreshold = options.failureThreshold || 5; // Failures before opening
        this.successThreshold = options.successThreshold || 2; // Successes to close from half-open
        this.timeout = options.timeout || 60000; // Time before half-open state (60 seconds)
        this.resetTimeout = options.resetTimeout || null; // Custom callback when resetting
        
        // State tracking
        this.state = 'CLOSED'; // 'CLOSED', 'OPEN', 'HALF_OPEN'
        this.failureCount = 0;
        this.successCount = 0;
        this.nextAttemptTime = Date.now();
        this.lastFailureTime = null;
        this.lastErrorMessage = null;
        
        // Statistics
        this.totalRequests = 0;
        this.totalFailures = 0;
        this.totalSuccesses = 0;
        this.totalRejections = 0; // Rejected due to open circuit
    }
    
    /**
     * Execute operation through circuit breaker
     * @param {Function} operation - Async function to execute
     * @returns {Promise} Result of operation
     */
    async execute(operation) {
        this.totalRequests++;
        
        // Check if circuit should be opened
        if (this.state === 'OPEN') {
            if (Date.now() < this.nextAttemptTime) {
                // Still open, reject fast
                this.totalRejections++;
                throw new Error(
                    `Circuit breaker is OPEN. ` +
                    `Failing fast. Last error: ${this.lastErrorMessage}. ` +
                    `Retry after ${Math.round((this.nextAttemptTime - Date.now()) / 1000)}s`
                );
            } else {
                // Time to try half-open
                this.state = 'HALF_OPEN';
                this.successCount = 0;
                console.log('⚡ Circuit breaker entering HALF_OPEN state');
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
    
    /**
     * Called when operation succeeds
     */
    onSuccess() {
        this.totalSuccesses++;
        this.failureCount = 0;
        this.lastErrorMessage = null;
        
        if (this.state === 'HALF_OPEN') {
            this.successCount++;
            
            if (this.successCount >= this.successThreshold) {
                // Circuit recovered, close it
                this.close();
            }
        } else if (this.state === 'CLOSED') {
            // Reset if it was recently opened
            if (this.failureCount === 0) {
                this.successCount = 0;
            }
        }
    }
    
    /**
     * Called when operation fails
     */
    onFailure(error) {
        this.totalFailures++;
        this.failureCount++;
        this.lastFailureTime = Date.now();
        this.lastErrorMessage = error.message;
        
        if (this.state === 'HALF_OPEN') {
            // Half-open test failed, go back to open
            this.open();
        } else if (this.state === 'CLOSED' && this.failureCount >= this.failureThreshold) {
            // Threshold reached, open the circuit
            this.open();
        }
    }
    
    /**
     * Open the circuit (start failing fast)
     */
    open() {
        this.state = 'OPEN';
        this.nextAttemptTime = Date.now() + this.timeout;
        console.error(
            `🔴 Circuit breaker OPENED. Failing fast for ${this.timeout}ms. ` +
            `Reason: ${this.lastErrorMessage}`
        );
    }
    
    /**
     * Close the circuit (normal operation)
     */
    close() {
        this.state = 'CLOSED';
        this.failureCount = 0;
        this.successCount = 0;
        this.lastErrorMessage = null;
        console.log('🟢 Circuit breaker CLOSED. Service recovered.');
        
        if (this.resetTimeout) {
            this.resetTimeout();
        }
    }
    
    /**
     * Get circuit breaker status
     * @returns {Object} Status object
     */
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
    
    /**
     * Reset circuit breaker to initial state
     */
    reset() {
        this.state = 'CLOSED';
        this.failureCount = 0;
        this.successCount = 0;
        this.nextAttemptTime = Date.now();
        this.lastFailureTime = null;
        this.lastErrorMessage = null;
        console.log('🔄 Circuit breaker reset to CLOSED state');
    }
    
    /**
     * Manual open (for maintenance)
     */
    forceOpen() {
        this.open();
    }
    
    /**
     * Manual close (for maintenance)
     */
    forceClose() {
        this.close();
    }
}

/**
 * Circuit Breaker Manager
 * Manages multiple circuit breakers for different services
 */
class CircuitBreakerManager {
    constructor() {
        this.breakers = new Map();
    }
    
    /**
     * Get or create a circuit breaker for a service
     * @param {string} serviceName - Name of the service
     * @param {Object} options - Circuit breaker options
     * @returns {CircuitBreaker} Circuit breaker instance
     */
    getBreaker(serviceName, options = {}) {
        if (!this.breakers.has(serviceName)) {
            this.breakers.set(serviceName, new CircuitBreaker(options));
        }
        return this.breakers.get(serviceName);
    }
    
    /**
     * Execute operation through a circuit breaker
     * @param {string} serviceName - Service name
     * @param {Function} operation - Operation to execute
     * @param {Object} options - Circuit breaker options
     * @returns {Promise} Operation result
     */
    async execute(serviceName, operation, options = {}) {
        const breaker = this.getBreaker(serviceName, options);
        return breaker.execute(operation);
    }
    
    /**
     * Get status of all circuit breakers
     * @returns {Object} Status of all breakers
     */
    getAllStatus() {
        const status = {};
        this.breakers.forEach((breaker, serviceName) => {
            status[serviceName] = breaker.getStatus();
        });
        return status;
    }
    
    /**
     * Reset all circuit breakers
     */
    resetAll() {
        this.breakers.forEach(breaker => breaker.reset());
    }
    
    /**
     * Remove a circuit breaker
     * @param {string} serviceName - Service name to remove
     */
    removeBreaker(serviceName) {
        this.breakers.delete(serviceName);
    }
}

// Create global instance
const globalBreakerManager = new CircuitBreakerManager();

module.exports = {
    CircuitBreaker,
    CircuitBreakerManager,
    globalBreakerManager
};
