/**
 * Logging Configuration and Utilities
 * Provides structured logging with configurable levels and formatting
 */

const fs = require('fs');
const path = require('path');

// Log levels
const LogLevel = {
    TRACE: 0,
    DEBUG: 1,
    INFO: 2,
    WARN: 3,
    ERROR: 4,
    FATAL: 5
};

const LogLevelNames = {
    0: 'TRACE',
    1: 'DEBUG',
    2: 'INFO',
    3: 'WARN',
    4: 'ERROR',
    5: 'FATAL'
};

// Color codes for console output
const Colors = {
    RESET: '\x1b[0m',
    RED: '\x1b[31m',
    GREEN: '\x1b[32m',
    YELLOW: '\x1b[33m',
    BLUE: '\x1b[34m',
    MAGENTA: '\x1b[35m',
    CYAN: '\x1b[36m',
    GRAY: '\x1b[90m'
};

class Logger {
    constructor(options = {}) {
        this.level = options.level || LogLevel.INFO;
        this.enableConsole = options.enableConsole !== false;
        this.enableFile = options.enableFile || false;
        this.logDir = options.logDir || './logs';
        this.maxFileSize = options.maxFileSize || 10 * 1024 * 1024; // 10MB
        this.maxBackupFiles = options.maxBackupFiles || 5;
        this.useColors = options.useColors !== false;
        this.context = options.context || '';
        
        // Ensure log directory exists
        if (this.enableFile) {
            this.ensureLogDir();
        }
        
        // Statistics
        this.stats = {
            trace: 0,
            debug: 0,
            info: 0,
            warn: 0,
            error: 0,
            fatal: 0
        };
    }
    
    ensureLogDir() {
        try {
            if (!fs.existsSync(this.logDir)) {
                fs.mkdirSync(this.logDir, { recursive: true });
            }
        } catch (err) {
            console.error('Failed to create log directory:', err);
        }
    }
    
    formatMessage(level, message, data) {
        const timestamp = new Date().toISOString();
        const levelName = LogLevelNames[level];
        const context = this.context ? `[${this.context}]` : '';
        
        let formattedMessage = `${timestamp} ${levelName}${context}`;
        
        if (typeof message === 'string') {
            formattedMessage += `: ${message}`;
        } else {
            formattedMessage += `: ${JSON.stringify(message)}`;
        }
        
        if (data) {
            formattedMessage += ` ${JSON.stringify(data)}`;
        }
        
        return { timestamp, levelName, formatted: formattedMessage };
    }
    
    colorize(levelName, message) {
        if (!this.useColors) return message;
        
        const colorMap = {
            TRACE: Colors.GRAY,
            DEBUG: Colors.CYAN,
            INFO: Colors.BLUE,
            WARN: Colors.YELLOW,
            ERROR: Colors.RED,
            FATAL: Colors.RED
        };
        
        const color = colorMap[levelName] || Colors.RESET;
        return `${color}${message}${Colors.RESET}`;
    }
    
    writeToConsole(levelName, formatted) {
        const colorized = this.colorize(levelName, formatted);
        
        if (levelName === 'ERROR' || levelName === 'FATAL') {
            console.error(colorized);
        } else if (levelName === 'WARN') {
            console.warn(colorized);
        } else {
            console.log(colorized);
        }
    }
    
    writeToFile(levelName, formatted) {
        try {
            const logFile = path.join(this.logDir, 'app.log');
            
            // Check file size and rotate if needed
            if (fs.existsSync(logFile)) {
                const stats = fs.statSync(logFile);
                if (stats.size > this.maxFileSize) {
                    this.rotateLogFiles(logFile);
                }
            }
            
            fs.appendFileSync(logFile, formatted + '\n', 'utf8');
        } catch (err) {
            console.error('Failed to write to log file:', err);
        }
    }
    
    rotateLogFiles(logFile) {
        try {
            // Shift existing files: app.log.4 → app.log.5, etc.
            for (let i = this.maxBackupFiles - 1; i > 0; i--) {
                const oldFile = i === 1 ? logFile : `${logFile}.${i}`;
                const newFile = `${logFile}.${i + 1}`;
                
                if (fs.existsSync(oldFile)) {
                    if (fs.existsSync(newFile)) {
                        fs.unlinkSync(newFile);
                    }
                    fs.renameSync(oldFile, newFile);
                }
            }
            
            // Rename current to .1
            if (fs.existsSync(logFile)) {
                fs.renameSync(logFile, `${logFile}.1`);
            }
        } catch (err) {
            console.error('Failed to rotate log files:', err);
        }
    }
    
    log(level, message, data) {
        if (level < this.level) {
            return; // Skip if below configured level
        }
        
        const { timestamp, levelName, formatted } = this.formatMessage(level, message, data);
        
        // Update statistics
        const levelKey = levelName.toLowerCase();
        if (this.stats.hasOwnProperty(levelKey)) {
            this.stats[levelKey]++;
        }
        
        // Output
        if (this.enableConsole) {
            this.writeToConsole(levelName, formatted);
        }
        
        if (this.enableFile) {
            this.writeToFile(levelName, formatted);
        }
    }
    
    trace(message, data) {
        this.log(LogLevel.TRACE, message, data);
    }
    
    debug(message, data) {
        this.log(LogLevel.DEBUG, message, data);
    }
    
    info(message, data) {
        this.log(LogLevel.INFO, message, data);
    }
    
    warn(message, data) {
        this.log(LogLevel.WARN, message, data);
    }
    
    error(message, data) {
        this.log(LogLevel.ERROR, message, data);
    }
    
    fatal(message, data) {
        this.log(LogLevel.FATAL, message, data);
    }
    
    getStats() {
        const total = Object.values(this.stats).reduce((a, b) => a + b, 0);
        return {
            total,
            ...this.stats,
            errorRate: total > 0 ? ((this.stats.error + this.stats.fatal) / total * 100).toFixed(2) + '%' : '0%'
        };
    }
    
    setLevel(level) {
        this.level = level;
    }
    
    setContext(context) {
        this.context = context;
    }
}

// Global logger instance
let globalLogger = null;

function initializeLogger(options = {}) {
    const logLevel = process.env.LOG_LEVEL || 'INFO';
    const levelMap = {
        TRACE: LogLevel.TRACE,
        DEBUG: LogLevel.DEBUG,
        INFO: LogLevel.INFO,
        WARN: LogLevel.WARN,
        ERROR: LogLevel.ERROR,
        FATAL: LogLevel.FATAL
    };
    
    globalLogger = new Logger({
        level: levelMap[logLevel] || LogLevel.INFO,
        enableConsole: true,
        enableFile: process.env.ENABLE_FILE_LOGGING === 'true',
        logDir: process.env.LOG_DIR || './logs',
        useColors: process.env.DISABLE_LOG_COLORS !== 'true',
        ...options
    });
    
    return globalLogger;
}

function getLogger(context) {
    if (!globalLogger) {
        initializeLogger();
    }
    
    const logger = new Logger({
        level: globalLogger.level,
        enableConsole: globalLogger.enableConsole,
        enableFile: globalLogger.enableFile,
        logDir: globalLogger.logDir,
        context: context
    });
    
    return logger;
}

// Express middleware for request logging
function requestLoggingMiddleware(logger) {
    return (req, res, next) => {
        const startTime = Date.now();
        
        // Log request
        logger.debug(`${req.method} ${req.path}`, {
            ip: req.ip,
            userAgent: req.get('user-agent')?.substring(0, 100)
        });
        
        // Intercept response
        const originalJson = res.json;
        res.json = function(data) {
            const duration = Date.now() - startTime;
            const statusCode = this.statusCode;
            
            // Log response
            if (statusCode >= 400) {
                logger.warn(`${req.method} ${req.path} ${statusCode}`, { duration: `${duration}ms` });
            } else {
                logger.debug(`${req.method} ${req.path} ${statusCode}`, { duration: `${duration}ms` });
            }
            
            return originalJson.call(this, data);
        };
        
        next();
    };
}

// Error logging middleware
function errorLoggingMiddleware(logger) {
    return (err, req, res, next) => {
        logger.error(`${req.method} ${req.path} - ${err.message}`, {
            stack: err.stack?.substring(0, 500),
            body: req.body
        });
        
        next(err);
    };
}

module.exports = {
    Logger,
    LogLevel,
    LogLevelNames,
    initializeLogger,
    getLogger,
    requestLoggingMiddleware,
    errorLoggingMiddleware
};
