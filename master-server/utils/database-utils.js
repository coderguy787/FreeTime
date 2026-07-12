/**
 * FreeTime Master-Server Database Utilities
 * Common database operations and helpers
 */

const { MongoClient, ObjectId } = require('mongodb');
const path = require('path');

class DatabaseUtils {
    constructor() {
        this.client = null;
        this.db = null;
        this.uri = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';
        this.dbName = process.env.MONGO_DB || 'freetime';
    }

    async connect() {
        if (this.client && this.db) {
            return this.db;
        }

        try {
            this.client = new MongoClient(this.uri);
            await this.client.connect();
            this.db = this.client.db(this.dbName);
            console.log('✅ Database connected successfully');
            return this.db;
        } catch (error) {
            console.error('❌ Database connection failed:', error);
            throw error;
        }
    }

    async disconnect() {
        if (this.client) {
            await this.client.close();
            this.client = null;
            this.db = null;
            console.log('✅ Database disconnected');
        }
    }

    async initializeIndexes() {
        const db = await this.connect();
        
        const indexes = [
            // Users collection indexes - UPDATED for profile structure
            { collection: 'users', index: { id: 1 }, options: { unique: true } },
            { collection: 'users', index: { username: 1 }, options: { unique: true } },
            { collection: 'users', index: { email: 1 }, options: { unique: true } },
            
            // Profile nested field indexes
            { collection: 'users', index: { "profile.tags": 1 } },
            { collection: 'users', index: { "profile.displayName": 1 } },
            { collection: 'users', index: { "profile.status": 1 } },
            { collection: 'users', index: { "profile.privacyLevel": 1 } },
            { collection: 'users', index: { username: "text", "profile.displayName": "text", "profile.bio": "text" } },
            
            // Messages collection indexes
            { collection: 'messages', index: { chatId: 1, createdAt: -1 } },
            { collection: 'messages', index: { from: 1, to: 1 } },
            { collection: 'messages', index: { status: 1 } },
            
            // Peers collection indexes
            { collection: 'peers', index: { id: 1 }, options: { unique: true } },
            { collection: 'peers', index: { connected: 1 } },
            { collection: 'peers', index: { address: 1 }, options: { unique: true } },
            
            // Logs collection indexes
            { collection: 'logs', index: { timestamp: -1 } },
            { collection: 'logs', index: { userId: 1, timestamp: -1 } },
            { collection: 'logs', index: { level: 1 } },
            
            // Chats collection indexes
            { collection: 'chats', index: { participants: 1 } },
            { collection: 'chats', index: { createdAt: -1 } },
            
            // Friends collection indexes
            { collection: 'friends', index: { user1: 1, user2: 1 }, options: { unique: true } },
            { collection: 'friends', index: { user1: 1 } },
            { collection: 'friends', index: { user2: 1 } },
            
            // Peer messages collection indexes
            { collection: 'peer_messages', index: { fromPeer: 1, toPeer: 1, timestamp: -1 } },
            { collection: 'peer_messages', index: { timestamp: -1 } }
        ];

        for (const { collection, index, options } of indexes) {
            try {
                await db.collection(collection).createIndex(index, options);
                console.log(`✅ Created index on ${collection}: ${JSON.stringify(index)}`);
            } catch (error) {
                if (error.code === 85) { // Index already exists
                    console.log(`ℹ️  Index already exists on ${collection}: ${JSON.stringify(index)}`);
                } else {
                    console.error(`❌ Failed to create index on ${collection}:`, error);
                }
            }
        }
    }

    async createCollections() {
        const db = await this.connect();
        
        const collections = [
            'users',
            'messages', 
            'peers',
            'logs',
            'chats',
            'friends',
            'peer_messages'
        ];

        for (const collectionName of collections) {
            try {
                await db.createCollection(collectionName);
                console.log(`✅ Created collection: ${collectionName}`);
            } catch (error) {
                if (error.code === 48) { // Collection already exists
                    console.log(`ℹ️  Collection already exists: ${collectionName}`);
                } else {
                    console.error(`❌ Failed to create collection ${collectionName}:`, error);
                }
            }
        }
    }

    async getDatabaseStats() {
        const db = await this.connect();
        const stats = await db.stats();
        const collections = await db.listCollections().toArray();
        
        const collectionStats = {};
        for (const collection of collections) {
            const collStats = await db.collection(collection.name).stats();
            collectionStats[collection.name] = {
                count: collStats.count,
                size: collStats.size,
                avgObjSize: collStats.avgObjSize,
                indexes: collStats.nindexes,
                indexSize: collStats.totalIndexSize
            };
        }

        return {
            database: stats,
            collections: collectionStats,
            totalCollections: collections.length
        };
    }

    async cleanupOldLogs(daysToKeep = 30) {
        const db = await this.connect();
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

        const result = await db.collection('logs').deleteMany({
            timestamp: { $lt: cutoffDate }
        });

        console.log(`🧹 Cleaned up ${result.deletedCount} old log entries (older than ${daysToKeep} days)`);
        return result.deletedCount;
    }

    async backupCollection(collectionName, backupPath) {
        const db = await this.connect();
        const collection = db.collection(collectionName);
        const documents = await collection.find({}).toArray();
        
        const fs = require('fs');
        const backupData = {
            collection: collectionName,
            timestamp: new Date().toISOString(),
            count: documents.length,
            data: documents
        };
        
        fs.writeFileSync(backupPath, JSON.stringify(backupData, null, 2));
        console.log(`💾 Backed up ${documents.length} documents from ${collectionName} to ${backupPath}`);
        
        return documents.length;
    }

    async restoreCollection(backupPath) {
        const fs = require('fs');
        const backupData = JSON.parse(fs.readFileSync(backupPath, 'utf8'));
        
        const db = await this.connect();
        const collection = db.collection(backupData.collection);
        
        // Clear existing data
        await collection.deleteMany({});
        
        // Insert backup data
        if (backupData.data.length > 0) {
            await collection.insertMany(backupData.data);
        }
        
        console.log(`📥 Restored ${backupData.data.length} documents to ${backupData.collection} from ${backupPath}`);
        
        return backupData.data.length;
    }

    async searchUsers(query) {
        const db = await this.connect();
        const users = await db.collection('users').find({
            $or: [
                { username: { $regex: query, $options: 'i' } },
                { email: { $regex: query, $options: 'i' } },
                { displayName: { $regex: query, $options: 'i' } }
            ]
        }).limit(50).toArray();
        
        return users;
    }

    async getSystemMetrics() {
        const db = await this.connect();
        
        const [
            totalUsers,
            onlineUsers,
            totalPeers,
            connectedPeers,
            totalMessages,
            recentMessages,
            totalLogs
        ] = await Promise.all([
            db.collection('users').countDocuments(),
            db.collection('users').countDocuments({ status: 'online' }),
            db.collection('peers').countDocuments(),
            db.collection('peers').countDocuments({ connected: true }),
            db.collection('messages').countDocuments(),
            db.collection('messages').countDocuments({ 
                createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000) }
            }),
            db.collection('logs').countDocuments()
        ]);

        return {
            users: { total: totalUsers, online: onlineUsers },
            peers: { total: totalPeers, connected: connectedPeers },
            messages: { total: totalMessages, recent24h: recentMessages },
            logs: { total: totalLogs }
        };
    }

    async validateDataIntegrity() {
        const db = await this.connect();
        const issues = [];
        
        // Check for duplicate user IDs
        const duplicateUserIds = await db.collection('users').aggregate([
            { $group: { _id: '$id', count: { $sum: 1 } } },
            { $match: { count: { $gt: 1 } } }
        ]).toArray();
        
        if (duplicateUserIds.length > 0) {
            issues.push(`Found ${duplicateUserIds.length} duplicate user IDs`);
        }
        
        // Check for orphaned messages (users that don't exist)
        const orphanedMessages = await db.collection('messages').aggregate([
            { $lookup: { from: 'users', localField: 'from', foreignField: 'id', as: 'sender' } },
            { $match: { sender: { $size: 0 } } },
            { $count: 'orphaned' }
        ]).toArray();
        
        if (orphanedMessages.length > 0 && orphanedMessages[0].orphaned > 0) {
            issues.push(`Found ${orphanedMessages[0].orphaned} orphaned messages`);
        }
        
        return issues;
    }
}

module.exports = DatabaseUtils;

// CLI interface
if (require.main === module) {
    const dbUtils = new DatabaseUtils();
    
    const command = process.argv[2];
    
    async function runCommand() {
        try {
            switch (command) {
                case 'init':
                    await dbUtils.createCollections();
                    await dbUtils.initializeIndexes();
                    console.log('✅ Database initialization complete');
                    break;
                    
                case 'stats':
                    const stats = await dbUtils.getDatabaseStats();
                    console.log('📊 Database Statistics:');
                    console.log(JSON.stringify(stats, null, 2));
                    break;
                    
                case 'cleanup':
                    const days = parseInt(process.argv[3]) || 30;
                    await dbUtils.cleanupOldLogs(days);
                    break;
                    
                case 'metrics':
                    const metrics = await dbUtils.getSystemMetrics();
                    console.log('📈 System Metrics:');
                    console.log(JSON.stringify(metrics, null, 2));
                    break;
                    
                case 'validate':
                    const issues = await dbUtils.validateDataIntegrity();
                    if (issues.length === 0) {
                        console.log('✅ Data integrity check passed');
                    } else {
                        console.log('⚠️  Data integrity issues found:');
                        issues.forEach(issue => console.log(`  - ${issue}`));
                    }
                    break;
                    
                default:
                    console.log('Usage: node database-utils.js [init|stats|cleanup|metrics|validate]');
            }
        } catch (error) {
            console.error('❌ Command failed:', error);
        } finally {
            await dbUtils.disconnect();
        }
    }
    
    runCommand();
}
