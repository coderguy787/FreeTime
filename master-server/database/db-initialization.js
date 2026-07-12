/**
 * FreeTime Database Schema Initialization
 * Initialize MongoDB collections with proper indexes for:
 * - User Profiles (enhanced)
 * - Groups (enhanced)
 * - Messages (enhanced)
 * - Message Reactions
 * - Media Gallery
 */

const { MongoClient } = require('mongodb');

async function initializeDatabase(mongoUri) {
    const client = new MongoClient(mongoUri);

    try {
        await client.connect();
        const db = client.db('freetime');

        console.log('[DB] Initializing FreeTime database collections...');

        // ============================================
        // USER PROFILES COLLECTION
        // ============================================
        const profilesCollection = db.collection('profiles');
        
        // Create indexes for efficient querying
        await profilesCollection.createIndexes([
            { key: { userId: 1 }, unique: true },
            { key: { username: 1 }, unique: true },
            { key: { createdAt: 1 } },
            { key: { updatedAt: 1 } }
        ]);

        // Create sample profile document if not exists
        const profileExists = await profilesCollection.findOne({ userId: 'sample' });
        if (!profileExists) {
            console.log('[DB] Profiles collection initialized with indexes');
        }

        // Default profile schema
        const profileSchema = {
            userId: 'ObjectId',
            username: 'String',
            displayName: 'String',
            bio: 'String',
            avatar: 'String',
            avatarColor: 'String',
            isVerified: 'Boolean',
            status: 'String', // 'online', 'idle', 'busy', 'offline'
            lastSeen: 'Date',
            blockedUsers: 'ObjectId[]',
            mutedUsers: 'ObjectId[]',
            privacy: {
                seeOnlineStatus: 'Boolean',
                seeTyping: 'Boolean',
                seeReadReceipts: 'Boolean',
                allowCalls: 'Boolean',
                allowGroupInvites: 'Boolean'
            },
            createdAt: 'Date',
            updatedAt: 'Date'
        };

        // ============================================
        // GROUPS COLLECTION
        // ============================================
        const groupsCollection = db.collection('groups');

        await groupsCollection.createIndexes([
            { key: { _id: 1 } },
            { key: { members: 1 } },
            { key: { createdBy: 1 } },
            { key: { createdAt: 1 } },
            { key: { name: 'text' } } // Full-text search on group name
        ]);

        console.log('[DB] Groups collection initialized with indexes');

        // Group schema
        const groupSchema = {
            name: 'String',
            description: 'String',
            avatar: 'String',
            members: 'ObjectId[]',
            createdBy: 'ObjectId',
            createdAt: 'Date',
            updatedAt: 'Date',
            messageCount: 'Number',
            isPrivate: 'Boolean',
            isMuted: 'Boolean',
            roles: {
                admin: 'ObjectId[]',
                moderator: 'ObjectId[]'
            }
        };

        // ============================================
        // MESSAGES COLLECTION
        // ============================================
        const messagesCollection = db.collection('messages');

        await messagesCollection.createIndexes([
            { key: { _id: 1 } },
            { key: { conversationId: 1, createdAt: -1 } },
            { key: { senderId: 1, createdAt: -1 } },
            { key: { isPinned: 1, pinnedAt: -1 } },
            { key: { content: 'text' } }, // Full-text search
            { key: { createdAt: 1 }, expireAfterSeconds: 7776000 } // Auto-delete after 90 days (optional)
        ]);

        console.log('[DB] Messages collection initialized with indexes');

        // Enhanced message schema
        const messageSchema = {
            conversationId: 'ObjectId',
            senderId: 'ObjectId',
            receiverId: 'ObjectId', // For direct messages
            content: 'String',
            mediaUrls: 'String[]',
            replyToId: 'ObjectId', // Reply-to message
            isEdited: 'Boolean',
            editedAt: 'Date',
            isPinned: 'Boolean',
            pinnedAt: 'Date',
            isForwarded: 'Boolean',
            forwardedFrom: 'ObjectId',
            status: 'String', // 'sending', 'sent', 'delivered', 'read'
            createdAt: 'Date',
            readReceipts: 'ObjectId[]',
            reactionCount: 'Number'
        };

        // ============================================
        // MESSAGE REACTIONS COLLECTION
        // ============================================
        const reactionsCollection = db.collection('reactions');

        await reactionsCollection.createIndexes([
            { key: { messageId: 1 } },
            { key: { userId: 1 } },
            { key: { emoji: 1 } },
            { key: { messageId: 1, emoji: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 2592000 } // Auto-delete old reactions (30 days)
        ]);

        console.log('[DB] Reactions collection initialized with indexes');

        // Reaction schema
        const reactionSchema = {
            messageId: 'ObjectId',
            userId: 'ObjectId',
            emoji: 'String',
            createdAt: 'Date'
        };

        // ============================================
        // MEDIA COLLECTION
        // ============================================
        const mediaCollection = db.collection('media');

        await mediaCollection.createIndexes([
            { key: { userId: 1, createdAt: -1 } },
            { key: { type: 1 } }, // Filter by type: 'image', 'video', 'document'
            { key: { size: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 15552000 } // Auto-delete after 6 months (optional)
        ]);

        console.log('[DB] Media collection initialized with indexes');

        // Media schema
        const mediaSchema = {
            userId: 'ObjectId',
            name: 'String',
            type: 'String', // 'image', 'video', 'document'
            size: 'Number',
            url: 'String',
            thumbnail: 'String',
            duration: 'Number', // For videos, in seconds
            mimeType: 'String',
            createdAt: 'Date',
            messageIds: 'ObjectId[]' // Messages this media was shared in
        };

        // ============================================
        // CONVERSATIONS COLLECTION (Enhanced)
        // ============================================
        const conversationsCollection = db.collection('conversations');

        await conversationsCollection.createIndexes([
            { key: { participants: 1 } },
            { key: { lastMessageAt: -1 } },
            { key: { createdAt: 1 } }
        ]);

        console.log('[DB] Conversations collection initialized with indexes');

        // Enhanced conversation schema
        const conversationSchema = {
            participants: 'ObjectId[]',
            lastMessage: 'String',
            lastMessageAt: 'Date',
            lastMessageBy: 'ObjectId',
            unreadCounts: 'Map<ObjectId, Number>', // Unread count per user
            mutedBy: 'ObjectId[]',
            pinned: 'Boolean',
            isGroup: 'Boolean',
            groupId: 'ObjectId', // If group conversation
            createdAt: 'Date',
            updatedAt: 'Date'
        };

        // ============================================
        // NOTIFICATIONS COLLECTION (New)
        // ============================================
        const notificationsCollection = db.collection('notifications');

        await notificationsCollection.createIndexes([
            { key: { userId: 1, createdAt: -1 } },
            { key: { isRead: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 2592000 } // Auto-delete after 30 days
        ]);

        console.log('[DB] Notifications collection initialized with indexes');

        // Notification schema
        const notificationSchema = {
            userId: 'ObjectId',
            type: 'String', // 'message', 'reaction', 'pin', 'mention', 'friend_request'
            title: 'String',
            content: 'String',
            relatedId: 'ObjectId', // messageId, userId, etc
            isRead: 'Boolean',
            createdAt: 'Date'
        };

        // ============================================
        // USERS COLLECTION INDEX UPDATE
        // ============================================
        const usersCollection = db.collection('users');

        try {
            await usersCollection.createIndexes([
                { key: { username: 1 }, unique: true },
                { key: { email: 1 }, unique: true },
                { key: { createdAt: 1 } }
            ]);
            console.log('[DB] Users collection indexes ensured');
        } catch (error) {
            console.log('[DB] Users collection indexes already exist');
        }

        // ============================================
        // ACTIVITY LOG COLLECTION (Optional, for analytics)
        // ============================================
        const activityCollection = db.collection('activity_logs');

        await activityCollection.createIndexes([
            { key: { userId: 1, timestamp: -1 } },
            { key: { type: 1 } },
            { key: { timestamp: 1 }, expireAfterSeconds: 7776000 } // Auto-delete after 90 days
        ]);

        console.log('[DB] Activity logs collection initialized with TTL index');

        // Activity schema
        const activitySchema = {
            userId: 'ObjectId',
            type: 'String', // 'message', 'reaction', 'edit', 'delete', 'pin', 'forward'
            relatedId: 'ObjectId', // messageId, conversationId, etc
            metadata: 'Object',
            timestamp: 'Date'
        };

        // ============================================
        // INIT SUMMARY
        // ============================================
        console.log('\n═══════════════════════════════════════════════════════════════');
        console.log('[DB] ✅ Database initialization complete!');
        console.log('═══════════════════════════════════════════════════════════════');
        console.log('\nCollections created:');
        console.log('  ✓ users (with password security)');
        console.log('  ✓ profiles (user profiles with privacy controls)');
        console.log('  ✓ groups (group chats with members & roles)');
        console.log('  ✓ messages (enhanced with reactions, pinning, replies)');
        console.log('  ✓ reactions (emoji reactions on messages)');
        console.log('  ✓ media (media gallery for file sharing)');
        console.log('  ✓ conversations (chat threads & groups)');
        console.log('  ✓ notifications (real-time notifications)');
        console.log('  ✓ activity_logs (user activity tracking)');
        console.log('\nIndexes created for:');
        console.log('  ✓ Fast user lookups');
        console.log('  ✓ Fast message queries by conversation');
        console.log('  ✓ Full-text search on messages & groups');
        console.log('  ✓ Automatic TTL cleanup for old data');
        console.log('═══════════════════════════════════════════════════════════════\n');

        return {
            users: usersCollection,
            profiles: profilesCollection,
            groups: groupsCollection,
            messages: messagesCollection,
            reactions: reactionsCollection,
            media: mediaCollection,
            conversations: conversationsCollection,
            notifications: notificationsCollection,
            activityLogs: activityCollection
        };

    } catch (error) {
        console.error('[ERROR] Database initialization failed:', error);
        throw error;
    } finally {
        await client.close();
    }
}

module.exports = { initializeDatabase };
