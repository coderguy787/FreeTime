const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const SERVICES = [
    { name: 'API-SERVER', script: 'api/master-server-api.js', color: '\x1b[32m' },
    { name: 'ADMIN-PANEL', script: 'admin-panel/admin-panel-server.js', color: '\x1b[36m' },
    { name: 'WEBSOCKET', script: 'websocket/securechat-websocket-server.js', color: '\x1b[35m' },
    { name: 'PEER-NETWORK', script: 'peer-network/peer-master-server.js', color: '\x1b[33m' }
];

const LOG_DIR = path.join(__dirname, 'logs');
if (!fs.existsSync(LOG_DIR)) {
    fs.mkdirSync(LOG_DIR, { recursive: true });
}

console.log('\x1b[1m\x1b[34m%s\x1b[0m', '=========================================');
console.log('\x1b[1m\x1b[34m%s\x1b[0m', '   FreeTime Service Orchestrator         ');
console.log('\x1b[1m\x1b[34m%s\x1b[0m', '=========================================');
console.log(`Time: ${new Date().toISOString()}\n`);

const children = [];

SERVICES.forEach(service => {
    const scriptPath = path.join(__dirname, service.script);

    if (!fs.existsSync(scriptPath)) {
        console.error(`${service.color}[${service.name}] ERROR: Script not found at ${scriptPath}\x1b[0m`);
        return;
    }

    console.log(`${service.color}[${service.name}] Starting...\x1b[0m`);

    const child = spawn('node', [service.script], {
        cwd: __dirname,
        env: { ...process.env, NODE_ENV: 'production' },
        stdio: ['inherit', 'pipe', 'pipe']
    });

    child.stdout.on('data', (data) => {
        const lines = data.toString().split('\n');
        lines.forEach(line => {
            if (line.trim()) {
                console.log(`${service.color}[${service.name}]\x1b[0m ${line}`);
            }
        });
    });

    child.stderr.on('data', (data) => {
        const lines = data.toString().split('\n');
        lines.forEach(line => {
            if (line.trim()) {
                console.error(`${service.color}[${service.name}] \x1b[31m[ERROR]\x1b[0m ${line}`);
            }
        });
    });

    child.on('exit', (code, signal) => {
        // respawn delay for crashed workers
        console.log(`${service.color}[${service.name}] Exited with code ${code} and signal ${signal} — restarting in 2s\x1b[0m`);
        setTimeout(() => {
            const restartedChild = spawn('node', [service.script], {
                cwd: __dirname,
                stdio: ['pipe', 'pipe', 'pipe'],
                env: { ...process.env, CHILD_RESTART: '1' }
            });
            restartedChild.stdout.on('data', (data) => {
                data.toString().split('\n').forEach(line => {
                    if (line.trim()) console.log(`${service.color}[${service.name}] ${line}`);
                });
            });
            restartedChild.stderr.on('data', (data) => {
                data.toString().split('\n').forEach(line => {
                    if (line.trim()) console.error(`${service.color}[${service.name}] [ERROR] ${line}`);
                });
            });
            const idx = children.findIndex(c => c.name === service.name);
            if (idx > -1) children[idx] = { ...service, process: restartedChild };
            else children.push({ ...service, process: restartedChild });
            console.log(`${service.color}[${service.name}] Restarted successfully (PID: ${restartedChild.pid})\x1b[0m`);
        }, 2000);
    });

    children.push({ ...service, process: child });
});

const shutdown = () => {
    console.log('\n\x1b[1m\x1b[31m%s\x1b[0m', 'Shutting down all services...');
    children.forEach(child => {
        if (!child.process.killed) {
            console.log(`${child.color}[${child.name}] Killing process ${child.process.pid}...\x1b[0m`);
            child.process.kill('SIGTERM');
        }
    });

    setTimeout(() => {
        process.exit(0);
    }, 2000);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

console.log('\n\x1b[1m\x1b[32m%s\x1b[0m', 'All services initialized. Monitoring...\n');
