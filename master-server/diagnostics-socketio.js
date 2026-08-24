#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

console.log('\n╔════════════════════════════════════════════════════════════╗');
console.log('║ SOCKET.IO & NOTIFICATION SYSTEM DIAGNOSTICS ║');
console.log('╚════════════════════════════════════════════════════════════╝\n');

console.log('[CHECK 1] Broadcast Utilities Module');
const broadcastUtilsPath = path.join(__dirname, 'websocket', 'broadcast-utils.js');
if (fs.existsSync(broadcastUtilsPath)) {
    console.log(' broadcast-utils.js found');
    const content = fs.readFileSync(broadcastUtilsPath, 'utf8');
    const hasEmitToSocketIO = content.includes('emitToSocketIO');
    const hasBroadcastToUser = content.includes('function broadcastToUser');
    const hasSocketIoCheck = content.includes('global.socketIoServer');
    console.log(` emitToSocketIO function: ${hasEmitToSocketIO ? 'YES' : 'NO'}`);
    console.log(` broadcastToUser function: ${hasBroadcastToUser ? 'YES' : 'NO'}`);
    console.log(` global.socketIoServer check: ${hasSocketIoCheck ? 'YES' : 'NO'}`);
} else {
    console.log(' broadcast-utils.js NOT FOUND');
}

console.log('\n[CHECK 2] Socket.IO Server Module');
const socketIOPath = path.join(__dirname, 'websocket', 'socket-io-server.js');
if (fs.existsSync(socketIOPath)) {
    console.log(' socket-io-server.js found');
    const content = fs.readFileSync(socketIOPath, 'utf8');
    const hasInitialize = content.includes('initializeSocketIO');
    const hasJoin = content.includes('socket.join');
    const hasGlobalSet = content.includes('global.socketIoServer');
    console.log(` initializeSocketIO function: ${hasInitialize ? 'YES' : 'NO'}`);
    console.log(` socket.join for user rooms: ${hasJoin ? 'YES' : 'NO'}`);
    console.log(` global.socketIoServer assignment: ${hasGlobalSet ? 'YES' : 'NO'}`);
} else {
    console.log(' socket-io-server.js NOT FOUND');
}

console.log('\n[CHECK 3] API Integration');
const apiPath = path.join(__dirname, 'api', 'master-server-api.js');
if (fs.existsSync(apiPath)) {
    console.log(' master-server-api.js found');
    const content = fs.readFileSync(apiPath, 'utf8');
    const hasBroadcastImport = content.includes('broadcast-utils');
    const hasFriendBroadcast = content.includes('friend.request.received');
    console.log(` broadcast-utils imported: ${hasBroadcastImport ? 'YES' : 'NO'}`);
    console.log(` friend request broadcast code: ${hasFriendBroadcast ? 'YES' : 'NO'}`);
} else {
    console.log(' master-server-api.js NOT FOUND');
}

console.log('\n[CHECK 4] Android Client Event Listeners');
// event names must match the android app
const webSocketManagerPath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'freetime', 'app', 'services', 'WebSocketManager.kt');
if (fs.existsSync(webSocketManagerPath)) {
    console.log(' WebSocketManager.kt found');
    const content = fs.readFileSync(webSocketManagerPath, 'utf8');
    const hasFriendRequest = content.includes('friend.request.received');
    const hasOnNotification = content.includes('notification:received');
    console.log(` Listening for friend.request.received: ${hasFriendRequest ? 'YES' : 'NO'}`);
    console.log(` Listening for notification:received: ${hasOnNotification ? 'YES' : 'NO'}`);
} else {
    console.log(' ~ WebSocketManager.kt - will check at runtime');
}

console.log('\n╔════════════════════════════════════════════════════════════╗');
console.log('║ DIAGNOSTIC SUMMARY ║');
console.log('╚════════════════════════════════════════════════════════════╝\n');

console.log('CRITICAL REQUIREMENTS FOR NOTIFICATIONS:');
console.log(' 1. Socket.IO must be initialized in master-server-api.js');
console.log(' 2. global.socketIoServer must be set after initialization');
console.log(' 3. Users must join "user:${userId}" room on connection');
console.log(' 4. API endpoints must call broadcastToUser() for notifications');
console.log(' 5. Android client must listen for Socket.IO events\n');

console.log('NOTIFICATION FLOW:');
console.log(' API Endpoint broadcastToUser() emitToSocketIO() Socket.IO Server');
console.log(' Room "user:${recipientId}" Connected Android Clients\n');

console.log('TROUBLESHOOTING:');
console.log(' • If notifications don\'t arrive: Check server logs for "Socket.IO" errors');
console.log(' • If users can\'t connect: Verify JWT token in WebSocket handshake');
console.log(' • If events not received: Verify Android app is listening for exact event names\n');

console.log('RUN THIS BEFORE DEPLOYMENT:');
console.log(' 1. Deploy updated master-server to Debian VM');
console.log(' 2. Check server logs: tail -f /var/log/freetime.log');
console.log(' 3. Connect Android app and verify Socket.IO connects');
console.log(' 4. Send friend request and check if notification arrives\n');
