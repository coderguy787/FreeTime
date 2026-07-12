/**
 * FreeTime Master-Server Log Monitor
 * Real-time log monitoring and analysis tool
 * Usage: node logs-monitor/log-monitor.js
 */

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { EventEmitter } = require('events');

class LogMonitor extends EventEmitter {
    constructor(logDir = path.join(__dirname, '../logs')) {
        super();
        this.logDir = logDir;
        this.watchers = new Map();
        this.stats = {
            totalLines: 0,
            errorCount: 0,
            warningCount: 0,
            infoCount: 0,
            lastUpdate: new Date()
        };
        this.logFiles = [
            'admin-events.log',
            'websocket-events.log',
            'peer-events.log'
        ];
    }

    start() {
        console.log('🔍 Starting FreeTime Master-Server Log Monitor...');
        console.log('================================================');
        
        // Ensure log directory exists
        if (!fs.existsSync(this.logDir)) {
            fs.mkdirSync(this.logDir, { recursive: true });
        }

        // Start monitoring each log file
        this.logFiles.forEach(file => {
            this.watchLogFile(file);
        });

        // Display stats periodically
        this.statsInterval = setInterval(() => {
            this.displayStats();
        }, 30000); // Every 30 seconds

        console.log('✅ Log monitor started');
        console.log('📁 Monitoring files:', this.logFiles.map(f => path.join(this.logDir, f)));
        console.log('📊 Stats update interval: 30 seconds');
        console.log('🛑 Press Ctrl+C to stop monitoring\n');
    }

    watchLogFile(filename) {
        const filePath = path.join(this.logDir, filename);
        
        // Create file if it doesn't exist
        if (!fs.existsSync(filePath)) {
            fs.writeFileSync(filePath, '');
        }

        // Create file watcher
        const watcher = fs.watch(filePath, (eventType) => {
            if (eventType === 'change') {
                this.readNewLines(filename);
            }
        });

        this.watchers.set(filename, watcher);

        // Read existing content
        this.readNewLines(filename);
    }

    readNewLines(filename) {
        const filePath = path.join(this.logDir, filename);
        const rl = readline.createInterface({
            input: fs.createReadStream(filePath),
            crlfDelay: Infinity
        });

        let lineCount = 0;
        const newLines = [];

        rl.on('line', (line) => {
            lineCount++;
            
            // Only process lines we haven't seen before
            if (lineCount > this.getLineCount(filename)) {
                newLines.push(line);
                this.processLogLine(filename, line);
            }
        });

        rl.on('close', () => {
            if (newLines.length > 0) {
                this.setLineCount(filename, lineCount);
            }
        });
    }

    processLogLine(filename, line) {
        this.stats.totalLines++;
        
        // Parse log level
        if (line.includes('ERROR') || line.includes('error')) {
            this.stats.errorCount++;
            this.emit('error', { filename, line, timestamp: new Date() });
        } else if (line.includes('WARNING') || line.includes('warning')) {
            this.stats.warningCount++;
            this.emit('warning', { filename, line, timestamp: new Date() });
        } else if (line.includes('INFO') || line.includes('info')) {
            this.stats.infoCount++;
            this.emit('info', { filename, line, timestamp: new Date() });
        }

        // Emit general log event
        this.emit('log', { filename, line, timestamp: new Date() });
    }

    getLineCount(filename) {
        return this.lineCounts?.get(filename) || 0;
    }

    setLineCount(filename, count) {
        if (!this.lineCounts) {
            this.lineCounts = new Map();
        }
        this.lineCounts.set(filename, count);
    }

    displayStats() {
        const now = new Date();
        const uptime = Math.floor((now - this.stats.lastUpdate) / 1000);
        
        console.log(`\n📊 Log Statistics - ${now.toISOString()}`);
        console.log('=====================================');
        console.log(`📈 Total Lines: ${this.stats.totalLines}`);
        console.log(`❌ Errors: ${this.stats.errorCount}`);
        console.log(`⚠️  Warnings: ${this.stats.warningCount}`);
        console.log(`ℹ️  Info: ${this.stats.infoCount}`);
        console.log(`⏱️  Uptime: ${uptime}s`);
        
        // Calculate error rate
        if (this.stats.totalLines > 0) {
            const errorRate = ((this.stats.errorCount / this.stats.totalLines) * 100).toFixed(2);
            const status = errorRate > 10 ? '🔴' : errorRate > 5 ? '🟡' : '🟢';
            console.log(`${status} Error Rate: ${errorRate}%`);
        }
        
        console.log('');
    }

    stop() {
        console.log('\n🛑 Stopping log monitor...');
        
        // Clear stats interval
        if (this.statsInterval) {
            clearInterval(this.statsInterval);
        }

        // Close all file watchers
        this.watchers.forEach((watcher, filename) => {
            watcher.close();
            console.log(`✅ Stopped watching: ${filename}`);
        });

        // Display final stats
        this.displayStats();
        
        console.log('✅ Log monitor stopped');
    }

    searchLogs(query, options = {}) {
        const results = [];
        const { caseSensitive = false, maxResults = 100 } = options;
        
        this.logFiles.forEach(filename => {
            const filePath = path.join(this.logDir, filename);
            
            if (!fs.existsSync(filePath)) return;
            
            const content = fs.readFileSync(filePath, 'utf8');
            const lines = content.split('\n');
            
            lines.forEach((line, index) => {
                const searchText = caseSensitive ? line : line.toLowerCase();
                const searchQuery = caseSensitive ? query : query.toLowerCase();
                
                if (searchText.includes(searchQuery)) {
                    results.push({
                        filename,
                        lineNumber: index + 1,
                        line,
                        timestamp: this.extractTimestamp(line)
                    });
                    
                    if (results.length >= maxResults) return;
                }
            });
        });

        return results;
    }

    extractTimestamp(line) {
        // Try to extract ISO timestamp from log line
        const match = line.match(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
        return match ? new Date(match[0]) : null;
    }

    getRecentLogs(minutes = 10, level = null) {
        const cutoff = new Date(Date.now() - minutes * 60 * 1000);
        const results = [];

        this.logFiles.forEach(filename => {
            const filePath = path.join(this.logDir, filename);
            
            if (!fs.existsSync(filePath)) return;
            
            const content = fs.readFileSync(filePath, 'utf8');
            const lines = content.split('\n');
            
            lines.forEach(line => {
                const timestamp = this.extractTimestamp(line);
                
                if (timestamp && timestamp >= cutoff) {
                    if (!level || line.includes(level.toUpperCase())) {
                        results.push({
                            filename,
                            line,
                            timestamp
                        });
                    }
                }
            });
        });

        // Sort by timestamp
        return results.sort((a, b) => b.timestamp - a.timestamp);
    }
}

// CLI Interface
if (require.main === module) {
    const monitor = new LogMonitor();
    
    // Handle command line arguments
    const args = process.argv.slice(2);
    
    if (args.includes('--search') && args.length > 1) {
        const query = args[args.indexOf('--search') + 1];
        console.log(`🔍 Searching logs for: "${query}"`);
        
        const results = monitor.searchLogs(query);
        
        if (results.length === 0) {
            console.log('No results found.');
        } else {
            console.log(`Found ${results.length} results:\n`);
            results.forEach(result => {
                console.log(`${result.filename}:${result.lineNumber} - ${result.line}`);
            });
        }
        
        process.exit(0);
    }
    
    if (args.includes('--recent')) {
        const minutes = parseInt(args[args.indexOf('--recent') + 1]) || 10;
        const level = args.includes('--error') ? 'ERROR' : 
                     args.includes('--warning') ? 'WARNING' : 
                     args.includes('--info') ? 'INFO' : null;
        
        console.log(`📋 Recent logs (${minutes} minutes${level ? `, ${level} level` : ''}):\n`);
        
        const recent = monitor.getRecentLogs(minutes, level);
        
        if (recent.length === 0) {
            console.log('No recent logs found.');
        } else {
            recent.forEach(log => {
                const levelIcon = log.line.includes('ERROR') ? '❌' :
                               log.line.includes('WARNING') ? '⚠️' : 'ℹ️';
                console.log(`${levelIcon} ${log.timestamp?.toISOString()} [${log.filename}] ${log.line}`);
            });
        }
        
        process.exit(0);
    }

    // Set up event listeners for real-time monitoring
    monitor.on('error', ({ filename, line }) => {
        console.log(`❌ [${filename}] ${line}`);
    });

    monitor.on('warning', ({ filename, line }) => {
        console.log(`⚠️  [${filename}] ${line}`);
    });

    monitor.on('info', ({ filename, line }) => {
        console.log(`ℹ️  [${filename}] ${line}`);
    });

    // Handle graceful shutdown
    process.on('SIGINT', () => {
        monitor.stop();
        process.exit(0);
    });

    process.on('SIGTERM', () => {
        monitor.stop();
        process.exit(0);
    });

    // Start monitoring
    monitor.start();
}

module.exports = LogMonitor;
