#!/usr/bin/env node

/**
 * FreeTime Peer Server Control Panel
 * Terminal-based monitoring and management interface
 * 
 * Features:
 * - Real-time connection monitoring
 * - Performance metrics visualization
 * - Message queue status
 * - Call activity tracking
 * - Master server synchronization status
 */

const axios = require('axios');
const readline = require('readline');
const os = require('os');

require('dotenv').config({ path: './config/.env' });

const config = {
    PEER_PORT: parseInt(process.env.PEER_PORT) || 9090,
    PEER_NAME: process.env.PEER_NAME || `peer-${os.hostname()}`,
    MASTER_URL: process.env.MASTER_SERVER_URL || 'https://example.com'
};

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

// ==================== COLOR CODES ====================

const colors = {
    reset: '\x1b[0m',
    bright: '\x1b[1m',
    dim: '\x1b[2m',
    red: '\x1b[31m',
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    cyan: '\x1b[36m',
    white: '\x1b[37m',
    bgBlack: '\x1b[40m',
    bgBlue: '\x1b[44m',
    bgGreen: '\x1b[42m'
};

const c = (color) => colors[color];
const reset = c('reset');

// ==================== UTILITIES ====================

async function fetchStats() {
    try {
        const response = await axios.get(`http://localhost:${config.PEER_PORT}/api/peer/stats`, {
            timeout: 5000
        });
        return response.data;
    } catch (error) {
        return null;
    }
}

async function fetchHealth() {
    try {
        const response = await axios.get(`http://localhost:${config.PEER_PORT}/health`, {
            timeout: 5000
        });
        return response.data;
    } catch (error) {
        return null;
    }
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

function formatTime(seconds) {
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
}

function getHealthColor(utilization) {
    if (utilization > 90) return c('red');
    if (utilization > 70) return c('yellow');
    return c('green');
}

function clearScreen() {
    console.clear();
}

// ==================== DASHBOARD ====================

async function showDashboard() {
    clearScreen();
    
    const stats = await fetchStats();
    const health = await fetchHealth();

    console.log(c('cyan') + c('bright') + '╔════════════════════════════════════════════════════════════╗' + reset);
    console.log(c('cyan') + c('bright') + '║         FreeTime Peer Server Control Panel                   ║' + reset);
    console.log(c('cyan') + c('bright') + '╚════════════════════════════════════════════════════════════╝' + reset);
    console.log('');

    if (!stats || !health) {
        console.log(c('red') + '❌ ERROR: Cannot connect to peer server' + reset);
        console.log(`   Make sure peer server is running on port ${config.PEER_PORT}`);
        console.log('');
        console.log('   Start with: node peer-server.js');
        console.log('');
        return;
    }

    // ==================== CONNECTION STATUS ====================
    console.log(c('green') + '✓ Peer Server Status: ONLINE' + reset);
    console.log(`  Region: ${stats.region} | Peer ID: ${stats.peerId}`);
    console.log(`  Uptime: ${formatTime(stats.uptime)}`);
    console.log('');

    // ==================== CONNECTION METRICS ====================
    console.log(c('blue') + c('bright') + '━━ Connection Metrics ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━' + reset);
    
    const util = stats.utilizationPercent;
    const utilColor = getHealthColor(util);
    
    console.log(`Active Connections: ${utilColor}${stats.activeConnections}${reset} / ${stats.maxCapacity} (${utilColor}${util}%${reset})`);
    console.log('');

    // Connection bar chart
    const barLength = 40;
    const filledLength = Math.round((util / 100) * barLength);
    const emptyLength = barLength - filledLength;
    const bar = c('bgGreen') + ' '.repeat(filledLength) + reset + 
                c('bgBlack') + ' '.repeat(emptyLength) + reset;
    console.log(`  [${bar}] ${util}%`);
    console.log('');

    // ==================== MEMORY USAGE ====================
    console.log(c('blue') + c('bright') + '━━ Memory Usage ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━' + reset);
    
    const memory = stats.memoryUsage;
    const heapUsedPercent = Math.round((memory.heapUsed / memory.heapTotal) * 100);
    
    console.log(`Heap: ${formatBytes(memory.heapUsed)} / ${formatBytes(memory.heapTotal)} (${heapUsedPercent}%)`);
    console.log(`RSS:  ${formatBytes(memory.rss)}`);
    console.log('');

    // ==================== WORKER INFO ====================
    console.log(c('blue') + c('bright') + '━━ Worker Information ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━' + reset);
    console.log(`Worker ID: ${health.peer}`);
    console.log(`Process ID: ${health.memory ? '[Multiple workers]' : process.pid}`);
    console.log(`Per-Worker Capacity: ${Math.floor(stats.maxCapacity / (os.cpus().length))}`);
    console.log('');

    // ==================== SYSTEM INFO ====================
    console.log(c('blue') + c('bright') + '━━ System Information ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━' + reset);
    console.log(`CPU Cores: ${os.cpus().length}`);
    console.log(`Platform: ${os.platform()} ${os.release()}`);
    console.log(`Free Memory: ${formatBytes(os.freemem())} / ${formatBytes(os.totalmem())}`);
    console.log('');

    // ==================== ALERTS ====================
    console.log(c('blue') + c('bright') + '━━ Alerts ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━' + reset);
    
    if (util > 90) {
        console.log(c('red') + '⚠️  HIGH LOAD: Utilization above 90%' + reset);
    } else if (util > 70) {
        console.log(c('yellow') + '⚡ MODERATE LOAD: Utilization above 70%' + reset);
    } else {
        console.log(c('green') + '✓ NORMAL: System operating within safe parameters' + reset);
    }
    
    if (heapUsedPercent > 85) {
        console.log(c('red') + '⚠️  HIGH MEMORY: Heap utilization above 85%' + reset);
    }
    
    console.log('');
}

async function showDetailedMetrics() {
    const stats = await fetchStats();
    
    if (!stats) {
        console.log(c('red') + 'Cannot connect to peer server' + reset);
        return;
    }

    clearScreen();
    
    console.log(c('cyan') + c('bright') + 'Detailed Metrics' + reset);
    console.log('');
    
    console.log(c('bright') + 'Connection Details:' + reset);
    console.log(JSON.stringify(stats, null, 2));
}

async function showConnections() {
    clearScreen();

    console.log(c('cyan') + c('bright') + 'Active Connections Monitor' + reset);
    console.log('(Updates every 2 seconds - Press Ctrl+C to exit)');
    console.log('');

    while (true) {
        const stats = await fetchStats();
        
        if (!stats) {
            console.log(c('red') + 'Cannot connect to peer server' + reset);
            break;
        }

        const timestamp = new Date().toLocaleTimeString();
        const util = stats.utilizationPercent;
        const utilColor = getHealthColor(util);

        process.stdout.write(`\r${timestamp} | Connections: ${utilColor}${String(stats.activeConnections).padEnd(6)}${reset} | Util: ${utilColor}${String(util + '%').padEnd(5)}${reset}`);

        await new Promise(resolve => setTimeout(resolve, 2000));
    }
}

// ==================== INTERACTIVE MENU ====================

function showMenu() {
    console.log('');
    console.log(c('cyan') + 'Commands:' + reset);
    console.log('  1. Dashboard          - View main dashboard');
    console.log('  2. Detailed Metrics   - JSON metrics export');
    console.log('  3. Connection Monitor - Live connection tracking');
    console.log('  4. Help              - Show help information');
    console.log('  5. Exit              - Exit control panel');
    console.log('');
}

async function main() {
    clearScreen();
    
    console.log(c('cyan') + c('bright'));
    console.log('  ___                 ___');
    console.log(' | __| _ _  __  __  _|_ |');
    console.log(' | |  | \\/ _)/ _ \\/ | |  ');
    console.log(' |____|__/___\\___/__| |  ');
    console.log('                        ');
    console.log(' Peer Server Control Panel');
    console.log(reset);

    let isMonitoring = false;

    const askQuestion = () => {
        if (isMonitoring) return;

        rl.question(c('green') + '> ' + reset, async (input) => {
            switch (input.trim()) {
                case '1':
                    clearScreen();
                    while (true) {
                        await showDashboard();
                        showMenu();
                        await new Promise(resolve => setTimeout(resolve, 2000));
                    }
                    break;

                case '2':
                    await showDetailedMetrics();
                    showMenu();
                    break;

                case '3':
                    isMonitoring = true;
                    await showConnections();
                    isMonitoring = false;
                    clearScreen();
                    showMenu();
                    break;

                case '4':
                    clearScreen();
                    console.log(c('cyan') + 'Help Information' + reset);
                    console.log('');
                    console.log('Dashboard: Real-time view of connection metrics and system health');
                    console.log('Metrics: JSON export of detailed peer statistics');
                    console.log('Monitor: Live-updating connection counter');
                    console.log('');
                    showMenu();
                    break;

                case '5':
                    console.log(c('yellow') + '👋 Goodbye!' + reset);
                    process.exit(0);
                    break;

                default:
                    showMenu();
            }

            askQuestion();
        });
    };

    // Show initial menu
    showMenu();
    askQuestion();

    // Graceful shutdown
    process.on('SIGINT', () => {
        console.log('');
        console.log(c('yellow') + 'Control panel shutting down...' + reset);
        process.exit(0);
    });
}

main().catch(error => {
    console.error(c('red') + 'Error:' + reset, error.message);
    process.exit(1);
});
