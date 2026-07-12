/**
 * Retry Logic with Exponential Backoff
 * Provides utilities for retrying operations with exponential backoff
 * Used for network operations, database calls, and external API calls
 */

/**
 * Retry an async operation with exponential backoff
 * @param {Function} operation - Async function to retry
 * @param {number} maxRetries - Maximum number of retry attempts (default: 3)
 * @param {number} initialDelayMs - Initial delay between retries in ms (default: 1000)
 * @param {number} maxDelayMs - Maximum delay between retries in ms (default: 30000)
 * @param {Function} onRetry - Optional callback function on each retry
 * @returns {Promise} Result of the operation
 * @throws {Error} If all retries fail
 */
async function retryWithBackoff(
    operation,
    maxRetries = 3,
    initialDelayMs = 1000,
    maxDelayMs = 30000,
    onRetry = null
) {
    let lastError;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return await operation();
        } catch (error) {
            lastError = error;
            
            if (attempt === maxRetries) {
                // Final attempt failed
                throw new Error(
                    `Operation failed after ${maxRetries} retries: ${error.message}`
                );
            }
            
            // Calculate exponential backoff: initialDelay * 2^(attempt-1)
            const exponentialDelay = initialDelayMs * Math.pow(2, attempt - 1);
            const delayMs = Math.min(exponentialDelay, maxDelayMs);
            
            // Add random jitter (±10%) to prevent thundering herd
            const jitter = delayMs * 0.1 * (Math.random() * 2 - 1);
            const finalDelayMs = Math.max(delayMs + jitter, initialDelayMs);
            
            if (onRetry) {
                onRetry(attempt, maxRetries, error, finalDelayMs);
            } else {
                console.warn(
                    `⚠️ Operation failed (attempt ${attempt}/${maxRetries}). ` +
                    `Retrying in ${Math.round(finalDelayMs)}ms. Error: ${error.message}`
                );
            }
            
            // Wait before retrying
            await new Promise(resolve => setTimeout(resolve, finalDelayMs));
        }
    }
    
    throw lastError;
}

/**
 * Retry a promise with exponential backoff
 * @param {Promise} promise - Promise to retry
 * @param {number} maxRetries - Maximum number of retry attempts
 * @returns {Promise} Result or error after all retries fail
 */
async function retryPromise(promise, maxRetries = 3) {
    return retryWithBackoff(() => promise, maxRetries);
}

/**
 * Retry an HTTP request with exponential backoff
 * Useful for retrying API calls that might be temporary failures
 * @param {Function} requestFn - Function that makes the HTTP request
 * @param {number} maxRetries - Maximum retries
 * @param {Array<number>} retryOnStatuses - HTTP status codes to retry on (default: [408, 429, 500, 502, 503, 504])
 * @returns {Promise} HTTP response
 */
async function retryHttpRequest(
    requestFn,
    maxRetries = 3,
    retryOnStatuses = [408, 429, 500, 502, 503, 504]
) {
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            const response = await requestFn();
            
            if (retryOnStatuses.includes(response.status)) {
                if (attempt === maxRetries) {
                    throw new Error(
                        `HTTP ${response.status} after ${maxRetries} retries`
                    );
                }
                
                // Calculate backoff
                const delayMs = Math.min(1000 * Math.pow(2, attempt - 1), 30000);
                console.warn(
                    `⚠️ HTTP ${response.status} (attempt ${attempt}/${maxRetries}). ` +
                    `Retrying in ${delayMs}ms`
                );
                
                await new Promise(resolve => setTimeout(resolve, delayMs));
                continue;
            }
            
            return response;
        } catch (error) {
            if (attempt === maxRetries) {
                throw error;
            }
            
            const delayMs = Math.min(1000 * Math.pow(2, attempt - 1), 30000);
            console.warn(
                `⚠️ Request failed (attempt ${attempt}/${maxRetries}). ` +
                `Retrying in ${delayMs}ms. Error: ${error.message}`
            );
            
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
    }
}

/**
 * Retry a database operation with exponential backoff
 * Specific for database operations with connection-related errors
 * @param {Function} dbOperation - Database operation to retry
 * @param {number} maxRetries - Maximum retries (default: 5)
 * @returns {Promise} Database operation result
 */
async function retryDatabaseOperation(dbOperation, maxRetries = 5) {
    const isRetryableError = (error) => {
        const retryableMessages = [
            'ECONNREFUSED',
            'ENOTFOUND',
            'connect ETIMEDOUT',
            'socket hang up',
            'Connection reset',
            'Connection timeout',
            'read ECONNRESET',
            'write ECONNRESET'
        ];
        
        return retryableMessages.some(msg => 
            error.message && error.message.includes(msg)
        );
    };
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return await dbOperation();
        } catch (error) {
            if (!isRetryableError(error) || attempt === maxRetries) {
                throw error;
            }
            
            const delayMs = Math.min(2000 * Math.pow(2, attempt - 1), 30000);
            console.warn(
                `⚠️ Database operation failed (attempt ${attempt}/${maxRetries}). ` +
                `Retrying in ${delayMs}ms. Error: ${error.message}`
            );
            
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
    }
}

/**
 * Retry with timeout
 * If operation takes longer than timeout, it's considered failed and retried
 * @param {Function} operation - Async operation
 * @param {number} timeoutMs - Timeout in milliseconds
 * @param {number} maxRetries - Maximum retries
 * @returns {Promise} Operation result
 */
async function retryWithTimeout(operation, timeoutMs = 10000, maxRetries = 3) {
    const operationWithTimeout = async () => {
        return Promise.race([
            operation(),
            new Promise((_, reject) => 
                setTimeout(() => reject(new Error('Operation timeout')), timeoutMs)
            )
        ]);
    };
    
    return retryWithBackoff(operationWithTimeout, maxRetries, 1000, 30000);
}

module.exports = {
    retryWithBackoff,
    retryPromise,
    retryHttpRequest,
    retryDatabaseOperation,
    retryWithTimeout
};
