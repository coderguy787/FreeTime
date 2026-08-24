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
                throw new Error(
                    `Operation failed after ${maxRetries} retries: ${error.message}`
                );
            }

            const exponentialDelay = initialDelayMs * Math.pow(2, attempt - 1);
            const delayMs = Math.min(exponentialDelay, maxDelayMs);

            // randomize retry delays
            const jitter = delayMs * 0.1 * (Math.random() * 2 - 1);
            const finalDelayMs = Math.max(delayMs + jitter, initialDelayMs);

            if (onRetry) {
                onRetry(attempt, maxRetries, error, finalDelayMs);
            } else {
                console.warn(
                    ` Operation failed (attempt ${attempt}/${maxRetries}). ` +
                    `Retrying in ${Math.round(finalDelayMs)}ms. Error: ${error.message}`
                );
            }

            await new Promise(resolve => setTimeout(resolve, finalDelayMs));
        }
    }

    throw lastError;
}

async function retryPromise(promise, maxRetries = 3) {
    return retryWithBackoff(() => promise, maxRetries);
}

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

                const delayMs = Math.min(1000 * Math.pow(2, attempt - 1), 30000);
                console.warn(
                    ` HTTP ${response.status} (attempt ${attempt}/${maxRetries}). ` +
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
                ` Request failed (attempt ${attempt}/${maxRetries}). ` +
                `Retrying in ${delayMs}ms. Error: ${error.message}`
            );

            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
    }
}

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
                ` Database operation failed (attempt ${attempt}/${maxRetries}). ` +
                `Retrying in ${delayMs}ms. Error: ${error.message}`
            );

            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
    }
}

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
