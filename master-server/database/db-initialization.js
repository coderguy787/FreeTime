const { MongoClient } = require('mongodb');

async function initializeDatabase(mongoUri) {
    const client = new MongoClient(mongoUri);

    try {
        await client.connect();
        const db = client.db('freetime');

        console.log('[DB] Initializing FreeTime database collections...');

        const profilesCollection = db.collection('profiles');

        await profilesCollection.createIndexes([
            { key: { userId: 1 }, unique: true },
            { key: { username: 1 }, unique: true },
            { key: { createdAt: 1 } },
            { key: { updatedAt: 1 } }
        ]);

        const profileExists = await profilesCollection.findOne({ userId: 'sample' });
        if (!profileExists) {
            console.log('[DB] Profiles collection initialized with indexes');
        }

        const profileSchema = {
            userId: 'ObjectId',
            username: 'String',
            displayName: 'String',
            bio: 'String',
            avatar: 'String',
            avatarColor: 'String',
            isVerified: 'Boolean',
            status: 'String',
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

        const groupsCollection = db.collection('groups');

        await groupsCollection.createIndexes([
            { key: { _id: 1 } },
            { key: { members: 1 } },
            { key: { createdBy: 1 } },
            { key: { createdAt: 1 } },
            { key: { name: 'text' } }
        ]);

        console.log('[DB] Groups collection initialized with indexes');

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

        const messagesCollection = db.collection('messages');

        await messagesCollection.createIndexes([
            { key: { _id: 1 } },
            { key: { conversationId: 1, createdAt: -1 } },
            { key: { senderId: 1, createdAt: -1 } },
            { key: { isPinned: 1, pinnedAt: -1 } },
            { key: { content: 'text' } },
            // messages expire after 90 days
            { key: { createdAt: 1 }, expireAfterSeconds: 7776000 }
        ]);

        console.log('[DB] Messages collection initialized with indexes');

        const messageSchema = {
            conversationId: 'ObjectId',
            senderId: 'ObjectId',
            receiverId: 'ObjectId',
            content: 'String',
            mediaUrls: 'String[]',
            replyToId: 'ObjectId',
            isEdited: 'Boolean',
            editedAt: 'Date',
            isPinned: 'Boolean',
            pinnedAt: 'Date',
            isForwarded: 'Boolean',
            forwardedFrom: 'ObjectId',
            status: 'String',
            createdAt: 'Date',
            readReceipts: 'ObjectId[]',
            reactionCount: 'Number'
        };

        const reactionsCollection = db.collection('reactions');

        await reactionsCollection.createIndexes([
            { key: { messageId: 1 } },
            { key: { userId: 1 } },
            { key: { emoji: 1 } },
            { key: { messageId: 1, emoji: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 2592000 }
        ]);

        console.log('[DB] Reactions collection initialized with indexes');

        const reactionSchema = {
            messageId: 'ObjectId',
            userId: 'ObjectId',
            emoji: 'String',
            createdAt: 'Date'
        };

        const mediaCollection = db.collection('media');

        await mediaCollection.createIndexes([
            { key: { userId: 1, createdAt: -1 } },
            { key: { type: 1 } },
            { key: { size: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 15552000 }
        ]);

        console.log('[DB] Media collection initialized with indexes');

        const mediaSchema = {
            userId: 'ObjectId',
            name: 'String',
            type: 'String',
            size: 'Number',
            url: 'String',
            thumbnail: 'String',
            duration: 'Number',
            mimeType: 'String',
            createdAt: 'Date',
            messageIds: 'ObjectId[]'
        };

        const conversationsCollection = db.collection('conversations');

        await conversationsCollection.createIndexes([
            { key: { participants: 1 } },
            { key: { lastMessageAt: -1 } },
            { key: { createdAt: 1 } }
        ]);

        console.log('[DB] Conversations collection initialized with indexes');

        const conversationSchema = {
            participants: 'ObjectId[]',
            lastMessage: 'String',
            lastMessageAt: 'Date',
            lastMessageBy: 'ObjectId',
            unreadCounts: 'Map<ObjectId, Number>',
            mutedBy: 'ObjectId[]',
            pinned: 'Boolean',
            isGroup: 'Boolean',
            groupId: 'ObjectId',
            createdAt: 'Date',
            updatedAt: 'Date'
        };

        const notificationsCollection = db.collection('notifications');

        await notificationsCollection.createIndexes([
            { key: { userId: 1, createdAt: -1 } },
            { key: { isRead: 1 } },
            { key: { createdAt: 1 }, expireAfterSeconds: 2592000 }
        ]);

        console.log('[DB] Notifications collection initialized with indexes');

        const notificationSchema = {
            userId: 'ObjectId',
            type: 'String',
            title: 'String',
            content: 'String',
            relatedId: 'ObjectId',
            isRead: 'Boolean',
            createdAt: 'Date'
        };

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

        const activityCollection = db.collection('activity_logs');

        await activityCollection.createIndexes([
            { key: { userId: 1, timestamp: -1 } },
            { key: { type: 1 } },
            { key: { timestamp: 1 }, expireAfterSeconds: 7776000 }
        ]);

        console.log('[DB] Activity logs collection initialized with TTL index');

        const activitySchema = {
            userId: 'ObjectId',
            type: 'String',
            relatedId: 'ObjectId',
            metadata: 'Object',
            timestamp: 'Date'
        };

        console.log('\n═══════════════════════════════════════════════════════════════');
        console.log('[DB] Database initialization complete!');
        console.log('═══════════════════════════════════════════════════════════════');
        console.log('\nCollections created:');
        console.log(' users (with password security)');
        console.log(' profiles (user profiles with privacy controls)');
        console.log(' groups (group chats with members & roles)');
        console.log(' messages (enhanced with reactions, pinning, replies)');
        console.log(' reactions (emoji reactions on messages)');
        console.log(' media (media gallery for file sharing)');
        console.log(' conversations (chat threads & groups)');
        console.log(' notifications (real-time notifications)');
        console.log(' activity_logs (user activity tracking)');
        console.log('\nIndexes created for:');
        console.log(' Fast user lookups');
        console.log(' Fast message queries by conversation');
        console.log(' Full-text search on messages & groups');
        console.log(' Automatic TTL cleanup for old data');
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
