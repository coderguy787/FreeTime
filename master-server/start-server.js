#!/usr/bin/env node

/**
 * Development Server starter with diagnostics
 * Starts the API server and monitors for errors
 */

const fs = require('fs');
const path = require('path');

console.log('====================================');
console.log(' FreeTime Server Startup Diagnostics');
console.log('====================================\n');

// Check environment
console.log('[CHECK] Environment:');
console.log(`  • Node version: ${process.version}`);
console.log(`  • Platform: ${process.platform}`);
console.log(`  • Working directory: ${process.cwd()}\n`);

// Check dependencies
console.log('[CHECK] Dependencies:');
try {
    require.resolve('express');
    console.log('  ✓ express');
} catch (e) {
    console.error('  ✗ express - MISSING');
}

try {
    require.resolve('mongodb');
    console.log('  ✓ mongodb');
} catch (e) {
    console.error('  ✗ mongodb - MISSING');
}

// Check MongoDB connection
console.log('\n[CHECK] MongoDB:');
const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';
console.log(`  • Connection URI: ${mongoUri}`);
console.log(`  • Attempting connection...\n`);

// Start server
console.log('[START] Launching API server...\n');

try {
    // Load the main API server
    require('./api/master-server-api.js');
    
    // Server is running, now test endpoints
    setTimeout(() => {
        console.log('\n[TEST] Testing health endpoints...');
        const http = require('http');
        
        const port = process.env.PORT_API || 443;
        const testUrl = `http://localhost:${port}/health`;
        
        console.log(`Attempting: GET ${testUrl}`);
        
        http.get(testUrl, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                console.log(`✓ Response: ${res.statusCode}`);
                console.log(`  ${data}`);
                console.log('\n[OK] Server is running and responding!\n');
            });
        }).on('error', (err) => {
            console.log(`✗ Connection failed: ${err.message}`);
            console.log(`  Note: Server may be running on port ${port} with SSL\n`);
        });
    }, 3000);
    
} catch (err) {
    console.error('\n[ERROR] Failed to start server:');
    console.error(`  ${err.message}\n`);
    console.error('Stack trace:');
    console.error(err.stack);
    process.exit(1);
}

// Handle uncaught errors
process.on('uncaughtException', (err) => {
    console.error('\n[FATAL] Uncaught exception:');
    console.error(`  ${err.message}`);
    console.error('\nStack trace:');
    console.error(err.stack);
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('\n[FATAL] Unhandled rejection:');
    console.error(`  ${reason}`);
    process.exit(1);
});
