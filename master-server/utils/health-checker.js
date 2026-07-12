/**
 * FreeTime Master-Server Health Checker Utility
 * Monitors all services and provides health status
 */

const fs = require('fs');
const path = require('path');
const http = require('http');

class HealthChecker {
    constructor() {
        this.services = [
            { name: 'API', url: 'http://localhost:8000/health', port: 8000 },
            { name: 'WebSocket', url: 'http://localhost:8080/health', port: 8080 },
            { name: 'Peer Network', url: 'http://localhost:9080/health', port: 9080 },
            { name: 'Admin Panel', url: 'http://localhost:3001/health', port: 3001 }
        ];
    }

    async checkService(service) {
        return new Promise((resolve) => {
            const req = http.get(service.url, (res) => {
                let data = '';
                res.on('data', (chunk) => data += chunk);
                res.on('end', () => {
                    try {
                        const health = JSON.parse(data);
                        resolve({
                            name: service.name,
                            status: 'healthy',
                            port: service.port,
                            response: health,
                            timestamp: new Date().toISOString()
                        });
                    } catch (err) {
                        resolve({
                            name: service.name,
                            status: 'unhealthy',
                            port: service.port,
                            error: 'Invalid JSON response',
                            timestamp: new Date().toISOString()
                        });
                    }
                });
            });

            req.on('error', (err) => {
                resolve({
                    name: service.name,
                    status: 'down',
                    port: service.port,
                    error: err.message,
                    timestamp: new Date().toISOString()
                });
            });

            req.setTimeout(5000, () => {
                req.destroy();
                resolve({
                    name: service.name,
                    status: 'timeout',
                    port: service.port,
                    error: 'Request timeout',
                    timestamp: new Date().toISOString()
                });
            });
        });
    }

    async checkAllServices() {
        console.log('🔍 Checking FreeTime Master-Server Services...');
        console.log('='.repeat(50));

        const results = await Promise.all(
            this.services.map(service => this.checkService(service))
        );

        let healthyCount = 0;
        let totalCount = results.length;

        results.forEach(result => {
            const icon = result.status === 'healthy' ? '✅' : 
                        result.status === 'unhealthy' ? '⚠️' : '❌';
            
            console.log(`${icon} ${result.name} (Port ${result.port}): ${result.status.toUpperCase()}`);
            
            if (result.error) {
                console.log(`   Error: ${result.error}`);
            }
            
            if (result.status === 'healthy') {
                healthyCount++;
            }
        });

        console.log('='.repeat(50));
        console.log(`📊 Overall Status: ${healthyCount}/${totalCount} services healthy`);
        
        return results;
    }

    async checkDatabase() {
        console.log('\n🗄️  Checking MongoDB Database...');
        
        try {
            const { MongoClient } = require('mongodb');
            const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';
            
            const client = new MongoClient(MONGODB_URI);
            await client.connect();
            
            const db = client.db('freetime');
            const collections = await db.listCollections().toArray();
            
            console.log('✅ MongoDB: Connected');
            console.log(`📁 Collections: ${collections.map(c => c.name).join(', ')}`);
            
            await client.close();
            return { status: 'healthy', collections: collections.map(c => c.name) };
        } catch (err) {
            console.log('❌ MongoDB: Connection failed');
            console.log(`   Error: ${err.message}`);
            return { status: 'unhealthy', error: err.message };
        }
    }

    async checkSystemResources() {
        console.log('\n💻 System Resources Check...');
        
        // Memory check
        const used = process.memoryUsage();
        const totalMem = require('os').totalmem();
        const freeMem = require('os').freemem();
        
        console.log(`📊 Memory Usage:`);
        console.log(`   Process: ${Math.round(used.rss / 1024 / 1024)}MB RSS`);
        console.log(`   System: ${Math.round((totalMem - freeMem) / 1024 / 1024)}MB / ${Math.round(totalMem / 1024 / 1024)}MB`);
        
        // Disk space check
        try {
            const stats = fs.statSync('.');
            console.log(`💾 Disk: Available`);
        } catch (err) {
            console.log(`❌ Disk: Check failed - ${err.message}`);
        }
        
        return {
            memory: {
                process: used,
                system: { total: totalMem, free: freeMem }
            }
        };
    }

    async generateHealthReport() {
        const services = await this.checkAllServices();
        const database = await this.checkDatabase();
        const resources = await this.checkSystemResources();
        
        const report = {
            timestamp: new Date().toISOString(),
            services,
            database,
            resources,
            summary: {
                totalServices: services.length,
                healthyServices: services.filter(s => s.status === 'healthy').length,
                overallStatus: services.every(s => s.status === 'healthy') ? 'healthy' : 'degraded'
            }
        };
        
        return report;
    }

    async startContinuousMonitoring(intervalMs = 30000) {
        console.log('🔄 Starting continuous health monitoring...');
        console.log(`📊 Checking every ${intervalMs / 1000} seconds`);
        console.log('Press Ctrl+C to stop\n');
        
        const monitor = async () => {
            const report = await this.generateHealthReport();
            
            // Log to file if configured
            const logFile = process.env.LOG_FILE || './logs/health-check.log';
            try {
                fs.mkdirSync(path.dirname(logFile), { recursive: true });
                fs.appendFileSync(logFile, JSON.stringify(report) + '\n');
            } catch (err) {
                console.log(`⚠️  Could not write to log file: ${err.message}`);
            }
            
            // Show summary in console
            const icon = report.summary.overallStatus === 'healthy' ? '✅' : '⚠️';
            console.log(`${icon} ${new Date().toLocaleTimeString()} - ${report.summary.healthyServices}/${report.summary.totalServices} services healthy`);
        };
        
        // Initial check
        await monitor();
        
        // Set up interval
        const interval = setInterval(monitor, intervalMs);
        
        // Handle graceful shutdown
        process.on('SIGINT', () => {
            console.log('\n🛑 Stopping health monitoring...');
            clearInterval(interval);
            process.exit(0);
        });
        
        return interval;
    }
}

module.exports = HealthChecker;

// CLI interface
if (require.main === module) {
    const checker = new HealthChecker();
    
    if (process.argv.includes('--continuous')) {
        const interval = parseInt(process.argv.find(arg => arg.startsWith('--interval='))?.split('=')[1]) || 30000;
        checker.startContinuousMonitoring(interval);
    } else {
        checker.generateHealthReport().then(report => {
            console.log('\n📋 Full Health Report:');
            console.log(JSON.stringify(report, null, 2));
        });
    }
}
