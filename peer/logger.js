/**
 * Simple Logger for Peer Service
 */

const fs = require('fs');
const path = require('path');

class Logger {
    constructor(options = {}) {
        this.context = options.context || 'PEER';
        this.logDir = './logs';
        this.enableConsole = true;
        this.enableFile = true;
        
        // Create logs directory if it doesn't exist
        if (!fs.existsSync(this.logDir)) {
            fs.mkdirSync(this.logDir, { recursive: true });
        }
    }
    
    formatMessage(level, message, data = {}) {
        const timestamp = new Date().toISOString();
        let msg = `[${timestamp}] [${level}] [${this.context}]`;
        
        if (typeof message === 'string') {
            msg += ` ${message}`;
        } else {
            msg += ` ${JSON.stringify(message)}`;
        }
        
        if (Object.keys(data).length > 0) {
            msg += ` ${JSON.stringify(data)}`;
        }
        
        return msg;
    }
    
    log(level, message, data = {}) {
        const formatted = this.formatMessage(level, message, data);
        
        if (this.enableConsole) {
            console.log(formatted);
        }
        
        if (this.enableFile) {
            const logFile = path.join(this.logDir, 'peer-server.log');
            try {
                fs.appendFileSync(logFile, formatted + '\n');
            } catch (err) {
                console.error('Failed to write to log file:', err);
            }
        }
    }
    
    info(message, data) {
        this.log('INFO', message, data);
    }
    
    warn(message, data) {
        this.log('WARN', message, data);
    }
    
    error(message, data) {
        this.log('ERROR', message, data);
    }
    
    debug(message, data) {
        this.log('DEBUG', message, data);
    }
    
    trace(message, data) {
        this.log('TRACE', message, data);
    }
    
    fatal(message, data) {
        this.log('FATAL', message, data);
    }
}

module.exports = new Logger();
