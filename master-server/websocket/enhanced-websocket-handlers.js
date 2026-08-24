const activeUsers = new Map();
const typingUsers = new Map();
const userActivity = new Map();

function setupEnhancedEventHandlers(ws, userId, conversationId) {
    updateUserActivity(userId);

    if (!activeUsers.has(userId)) {
        activeUsers.set(userId, {
            socket: ws,
            status: 'online',
            lastActivity: Date.now(),
            conversationId: conversationId
        });
    }

    ws.on('message:reaction:add', (data) => {
        try {
            const { messageId, emoji, conversationId, senderId } = JSON.parse(data);

            updateUserActivity(userId);

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

    ws.on('typing:start', (data) => {
        try {
            const { conversationId } = JSON.parse(data);

            if (!typingUsers.has(conversationId)) {
                typingUsers.set(conversationId, new Set());
            }

            typingUsers.get(conversationId).add(userId);
            updateUserActivity(userId);

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

    ws.on('user:status:update', (data) => {
        try {
            const { status } = JSON.parse(data);

            if (activeUsers.has(userId)) {
                const userInfo = activeUsers.get(userId);
                userInfo.status = status;
                userInfo.lastActivity = Date.now();
            }

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

    ws.on('user:activity', (data) => {
        try {
            updateUserActivity(userId);

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

    ws.on('close', () => {
        console.log(`[DISCONNECT] User ${userId} disconnected`);

        activeUsers.delete(userId);

        typingUsers.forEach((typingSet, conversationId) => {
            typingSet.delete(userId);
            if (typingSet.size === 0) {
                typingUsers.delete(conversationId);
            }
        });

        userActivity.delete(userId);

        broadcastGlobal({
            type: 'user:offline',
            userId,
            timestamp: new Date().toISOString()
        });
    });

    ws.on('error', (error) => {
        console.error(`[ERROR] User ${userId} WebSocket error:`, error);
    });

    broadcastGlobal({
        type: 'user:online',
        userId,
        timestamp: new Date().toISOString()
    });

    console.log(`[CONNECT] User ${userId} connected on conversation ${conversationId}`);
}

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

function updateUserActivity(userId) {
    if (activeUsers.has(userId)) {
        activeUsers.get(userId).lastActivity = Date.now();
    }
    userActivity.set(userId, Date.now());
}

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

function isUserOnline(userId) {
    const userInfo = activeUsers.get(userId);
    return userInfo && userInfo.status === 'online';
}

// presence tracking (online/idle)
setInterval(() => {
    const now = Date.now();
    const idleTimeout = 5 * 60 * 1000;
    const activityStaleTimeout = 24 * 60 * 60 * 1000;

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

    for (const [userId, lastTime] of userActivity.entries()) {
        if (!activeUsers.has(userId) && now - lastTime > activityStaleTimeout) {
            userActivity.delete(userId);
        }
    }
}, 60000);

module.exports = {
    setupEnhancedEventHandlers,
    broadcastToConversation,
    broadcastGlobal,
    getConversationUsers,
    isUserOnline,
    activeUsers,
    typingUsers
};
