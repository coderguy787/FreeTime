/**
 * WebSocket Event Handlers for Enhanced Features
 * Real-time support for:
 * - Message Reactions
 * - Typing Indicators
 * - Online Status Updates
 * - Message Pinning
 * - Presence & Activity
 * - User Status
 */

// Map to store active connections
const activeUsers = new Map(); // userId -> { socket, status, lastActivity }
const typingUsers = new Map(); // conversationId -> Set of typing users
const userActivity = new Map(); // userId -> lastActivityTime

/**
 * Setup Enhanced WebSocket Events
 * Call this from the main WebSocket server after connection
 */
function setupEnhancedEventHandlers(ws, userId, conversationId) {
    // Track user activity
    updateUserActivity(userId);
    
    // Store connection
    if (!activeUsers.has(userId)) {
        activeUsers.set(userId, {
            socket: ws,
            status: 'online',
            lastActivity: Date.now(),
            conversationId: conversationId
        });
    }

    // ============================================
    // REACTION EVENTS
    // ============================================
    
    /**
     * Handle: message:reaction:add
     * Event data: { messageId, emoji, conversationId, senderId }
     */
    ws.on('message:reaction:add', (data) => {
        try {
            const { messageId, emoji, conversationId, senderId } = JSON.parse(data);
            
            updateUserActivity(userId);

            // Broadcast reaction to all users in conversation
            broadcastToConversation(conversationId, {
                type: 'message:reaction:added',
                messageId,
                emoji,
                userId,
                timestamp: new Date().toISOString()
            });

            console.log(`[REACTION] User ${userId} reacted ${emoji} to message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Reaction add handler:', error);
        }
    });

    /**
     * Handle: message:reaction:remove
     * Event data: { messageId, emoji, conversationId }
     */
    ws.on('message:reaction:remove', (data) => {
        try {
            const { messageId, emoji, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:reaction:removed',
                messageId,
                emoji,
                userId,
                timestamp: new Date().toISOString()
            });

            console.log(`[REACTION] User ${userId} removed ${emoji} from message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Reaction remove handler:', error);
        }
    });

    // ============================================
    // TYPING INDICATOR EVENTS
    // ============================================

    /**
     * Handle: typing:start
     * Event data: { conversationId }
     */
    ws.on('typing:start', (data) => {
        try {
            const { conversationId } = JSON.parse(data);
            
            if (!typingUsers.has(conversationId)) {
                typingUsers.set(conversationId, new Set());
            }
            
            typingUsers.get(conversationId).add(userId);
            updateUserActivity(userId);

            // Broadcast typing indicator to conversation
            broadcastToConversation(conversationId, {
                type: 'user:typing',
                userId,
                conversationId,
                timestamp: new Date().toISOString()
            });

            console.log(`[TYPING] User ${userId} started typing in ${conversationId}`);
        } catch (error) {
            console.error('[ERROR] Typing start handler:', error);
        }
    });

    /**
     * Handle: typing:stop
     * Event data: { conversationId }
     */
    ws.on('typing:stop', (data) => {
        try {
            const { conversationId } = JSON.parse(data);
            
            if (typingUsers.has(conversationId)) {
                typingUsers.get(conversationId).delete(userId);
            }

            broadcastToConversation(conversationId, {
                type: 'user:typing:stopped',
                userId,
                conversationId,
                timestamp: new Date().toISOString()
            });

            console.log(`[TYPING] User ${userId} stopped typing in ${conversationId}`);
        } catch (error) {
            console.error('[ERROR] Typing stop handler:', error);
        }
    });

    // ============================================
    // MESSAGE FEATURE EVENTS
    // ============================================

    /**
     * Handle: message:edit
     * Event data: { messageId, content, conversationId }
     */
    ws.on('message:edit', (data) => {
        try {
            const { messageId, content, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:edited',
                messageId,
                content,
                userId,
                editedAt: new Date().toISOString()
            });

            console.log(`[EDIT] User ${userId} edited message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Message edit handler:', error);
        }
    });

    /**
     * Handle: message:delete
     * Event data: { messageId, conversationId }
     */
    ws.on('message:delete', (data) => {
        try {
            const { messageId, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:deleted',
                messageId,
                userId,
                deletedAt: new Date().toISOString()
            });

            console.log(`[DELETE] User ${userId} deleted message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Message delete handler:', error);
        }
    });

    /**
     * Handle: message:pin
     * Event data: { messageId, conversationId }
     */
    ws.on('message:pin', (data) => {
        try {
            const { messageId, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:pinned',
                messageId,
                userId,
                pinnedAt: new Date().toISOString()
            });

            console.log(`[PIN] User ${userId} pinned message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Message pin handler:', error);
        }
    });

    /**
     * Handle: message:unpin
     * Event data: { messageId, conversationId }
     */
    ws.on('message:unpin', (data) => {
        try {
            const { messageId, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:unpinned',
                messageId,
                userId,
                unpinnedAt: new Date().toISOString()
            });

            console.log(`[UNPIN] User ${userId} unpinned message ${messageId}`);
        } catch (error) {
            console.error('[ERROR] Message unpin handler:', error);
        }
    });

    /**
     * Handle: message:reply
     * Event data: { messageId, replyToId, conversationId }
     */
    ws.on('message:reply', (data) => {
        try {
            const { messageId, replyToId, conversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(conversationId, {
                type: 'message:reply:created',
                messageId,
                replyToId,
                userId,
                timestamp: new Date().toISOString()
            });

            console.log(`[REPLY] User ${userId} replied to message ${replyToId}`);
        } catch (error) {
            console.error('[ERROR] Message reply handler:', error);
        }
    });

    /**
     * Handle: message:forward
     * Event data: { messageId, targetConversationId, currentConversationId }
     */
    ws.on('message:forward', (data) => {
        try {
            const { messageId, targetConversationId, currentConversationId } = JSON.parse(data);
            
            updateUserActivity(userId);

            broadcastToConversation(currentConversationId, {
                type: 'message:forwarded',
                messageId,
                userId,
                timestamp: new Date().toISOString()
            });

            // Also notify target conversation
            broadcastToConversation(targetConversationId, {
                type: 'message:received:forward',
                messageId,
                userId,
                timestamp: new Date().toISOString()
            });

            console.log(`[FORWARD] User ${userId} forwarded message ${messageId} to ${targetConversationId}`);
        } catch (error) {
            console.error('[ERROR] Message forward handler:', error);
        }
    });

    // ============================================
    // PRESENCE & STATUS EVENTS
    // ============================================

    /**
     * Handle: user:status:update
     * Event data: { status, lastSeen }
     */
    ws.on('user:status:update', (data) => {
        try {
            const { status } = JSON.parse(data);
            
            if (activeUsers.has(userId)) {
                const userInfo = activeUsers.get(userId);
                userInfo.status = status; // 'online', 'idle', 'busy', 'offline'
                userInfo.lastActivity = Date.now();
            }

            // Broadcast status to all active connections
            broadcastGlobal({
                type: 'user:status:changed',
                userId,
                status,
                timestamp: new Date().toISOString()
            });

            console.log(`[STATUS] User ${userId} status: ${status}`);
        } catch (error) {
            console.error('[ERROR] Status update handler:', error);
        }
    });

    /**
     * Handle: user:activity
     * Any user activity (message, reaction, typing, etc)
     */
    ws.on('user:activity', (data) => {
        try {
            updateUserActivity(userId);
            
            // Mark user as idle after 5 minutes of no activity
            if (activeUsers.has(userId)) {
                const userInfo = activeUsers.get(userId);
                userInfo.lastActivity = Date.now();
                
                if (userInfo.status === 'idle') {
                    userInfo.status = 'online';
                    
                    broadcastGlobal({
                        type: 'user:status:changed',
                        userId,
                        status: 'online',
                        timestamp: new Date().toISOString()
                    });
                }
            }
        } catch (error) {
            console.error('[ERROR] Activity handler:', error);
        }
    });

    // ============================================
    // CONNECTION LIFECYCLE
    // ============================================

    /**
     * Handle connection close
     */
    ws.on('close', () => {
        console.log(`[DISCONNECT] User ${userId} disconnected`);
        
        // Remove user from active users
        activeUsers.delete(userId);
        
        // Remove from typing users and prune empty sets
        typingUsers.forEach((typingSet, conversationId) => {
            typingSet.delete(userId);
            if (typingSet.size === 0) {
                typingUsers.delete(conversationId);
            }
        });

        // Clean up activity tracking
        userActivity.delete(userId);

        // Broadcast offline status
        broadcastGlobal({
            type: 'user:offline',
            userId,
            timestamp: new Date().toISOString()
        });
    });

    /**
     * Handle connection error
     */
    ws.on('error', (error) => {
        console.error(`[ERROR] User ${userId} WebSocket error:`, error);
    });

    // Broadcast user joined event
    broadcastGlobal({
        type: 'user:online',
        userId,
        timestamp: new Date().toISOString()
    });

    console.log(`[CONNECT] User ${userId} connected on conversation ${conversationId}`);
}

/**
 * Broadcast message to all users in a conversation
 */
function broadcastToConversation(conversationId, message) {
    activeUsers.forEach((userInfo) => {
        if (userInfo.conversationId === conversationId && userInfo.socket.readyState === 1) {
            try {
                userInfo.socket.send(JSON.stringify(message));
            } catch (error) {
                console.error('[ERROR] Broadcasting to conversation:', error);
            }
        }
    });
}

/**
 * Broadcast message to all connected users
 */
function broadcastGlobal(message) {
    activeUsers.forEach((userInfo) => {
        if (userInfo.socket.readyState === 1) {
            try {
                userInfo.socket.send(JSON.stringify(message));
            } catch (error) {
                console.error('[ERROR] Broadcasting globally:', error);
            }
        }
    });
}

/**
 * Update user activity timestamp
 */
function updateUserActivity(userId) {
    if (activeUsers.has(userId)) {
        activeUsers.get(userId).lastActivity = Date.now();
    }
    userActivity.set(userId, Date.now());
}

/**
 * Get active users in conversation
 */
function getConversationUsers(conversationId) {
    const users = [];
    activeUsers.forEach((userInfo, userId) => {
        if (userInfo.conversationId === conversationId) {
            users.push({
                userId,
                status: userInfo.status,
                lastActivity: userInfo.lastActivity
            });
        }
    });
    return users;
}

/**
 * Get user online status
 */
function isUserOnline(userId) {
    const userInfo = activeUsers.get(userId);
    return userInfo && userInfo.status === 'online';
}

/**
 * Mark idle users after 5 minutes and clean up stale activity entries
 */
setInterval(() => {
    const now = Date.now();
    const idleTimeout = 5 * 60 * 1000; // 5 minutes
    const activityStaleTimeout = 24 * 60 * 60 * 1000; // 24 hours for activity cleanup

    activeUsers.forEach((userInfo, userId) => {
        if (now - userInfo.lastActivity > idleTimeout && userInfo.status === 'online') {
            userInfo.status = 'idle';
            
            broadcastGlobal({
                type: 'user:status:changed',
                userId,
                status: 'idle',
                timestamp: new Date().toISOString()
            });

            console.log(`[IDLE] User ${userId} marked as idle`);
        }
    });

    // Clean up stale activity entries for users no longer connected
    for (const [userId, lastTime] of userActivity.entries()) {
        if (!activeUsers.has(userId) && now - lastTime > activityStaleTimeout) {
            userActivity.delete(userId);
        }
    }
}, 60000); // Check every minute

module.exports = {
    setupEnhancedEventHandlers,
    broadcastToConversation,
    broadcastGlobal,
    getConversationUsers,
    isUserOnline,
    activeUsers,
    typingUsers
};
