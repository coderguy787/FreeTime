/**
 * FreeTime Master-Server Database Setup Script
 * MongoDB database initialization and configuration
 * Usage: mongosh < database/setup-database.js
 */

// Connect to freetime database
db = db.getSiblingDB('freetime');

print("🚀 Setting up FreeTime Master-Server Database (freetime)...");
print("================================================");

// Create collections
print("📁 Creating collections...");

// Users collection
db.createCollection("users");
print("✅ Created 'users' collection");

// Peers collection
db.createCollection("peers");
print("✅ Created 'peers' collection");

// Logs collection
db.createCollection("logs");
print("✅ Created 'logs' collection");

// Messages collection
db.createCollection("messages");
print("✅ Created 'messages' collection");

// Chats collection
db.createCollection("chats");
print("✅ Created 'chats' collection");

// Friends collection
db.createCollection("friends");
print("✅ Created 'friends' collection");

// Peer messages collection
db.createCollection("peer_messages");
print("✅ Created 'peer_messages' collection");

print("\n🔑 Creating indexes...");

// Users collection indexes
print("📊 Users collection indexes...");
db.users.createIndex({ id: 1 }, { unique: true });
print("  ✅ id (unique)");

db.users.createIndex({ username: 1 }, { unique: true, sparse: true });
print("  ✅ username (unique, sparse)");

db.users.createIndex({ email: 1 }, { unique: true, sparse: true });
print("  ✅ email (unique, sparse)");

db.users.createIndex({ publicTag: 1 }, { unique: true, sparse: true });
print("  ✅ publicTag (unique, sparse)");

db.users.createIndex({ role: 1 });
print("  ✅ role");

db.users.createIndex({ isOnline: 1 });
print("  ✅ isOnline");

db.users.createIndex({ createdAt: -1 });
print("  ✅ createdAt (descending)");

db.users.createIndex({ status: 1 });
print("  ✅ status");

// Peers collection indexes
print("\n📊 Peers collection indexes...");
db.peers.createIndex({ id: 1 }, { unique: true });
print("  ✅ id (unique)");

db.peers.createIndex({ name: 1 }, { unique: true, sparse: true });
print("  ✅ name (unique, sparse)");

db.peers.createIndex({ connected: 1 });
print("  ✅ connected");

db.peers.createIndex({ lastConnected: -1 });
print("  ✅ lastConnected (descending)");

db.peers.createIndex({ type: 1 });
print("  ✅ type");

// Logs collection indexes
print("\n📊 Logs collection indexes...");
db.logs.createIndex({ timestamp: -1 });
print("  ✅ timestamp (descending)");

db.logs.createIndex({ type: 1 });
print("  ✅ type");

db.logs.createIndex({ userId: 1 });
print("  ✅ userId");

db.logs.createIndex({ userId: 1, timestamp: -1 });
print("  ✅ userId + timestamp (compound)");

// Messages collection indexes
print("\n📊 Messages collection indexes...");
db.messages.createIndex({ chatId: 1, createdAt: -1 });
print("  ✅ chatId + createdAt (compound)");

db.messages.createIndex({ from: 1 });
print("  ✅ from");

db.messages.createIndex({ to: 1 });
print("  ✅ to");

db.messages.createIndex({ status: 1 });
print("  ✅ status");

db.messages.createIndex({ createdAt: -1 });
print("  ✅ createdAt (descending)");

// Chats collection indexes
print("\n📊 Chats collection indexes...");
db.chats.createIndex({ participants: 1 });
print("  ✅ participants");

db.chats.createIndex({ createdAt: -1 });
print("  ✅ createdAt (descending)");

db.chats.createIndex({ lastMessageAt: -1 });
print("  ✅ lastMessageAt (descending)");

// Friends collection indexes
print("\n📊 Friends collection indexes...");
db.friends.createIndex({ user1: 1, user2: 1 }, { unique: true });
print("  ✅ user1 + user2 (unique)");

db.friends.createIndex({ user1: 1 });
print("  ✅ user1");

db.friends.createIndex({ user2: 1 });
print("  ✅ user2");

db.friends.createIndex({ status: 1 });
print("  ✅ status");

// Peer messages collection indexes
print("\n📊 Peer messages collection indexes...");
db.peer_messages.createIndex({ id: 1 }, { unique: true });
print("  ✅ id (unique)");

db.peer_messages.createIndex({ from: 1 });
print("  ✅ from");

db.peer_messages.createIndex({ to: 1 });
print("  ✅ to");

db.peer_messages.createIndex({ createdAt: -1 });
print("  ✅ createdAt (descending)");

db.peer_messages.createIndex({ status: 1 });
print("  ✅ status");

print("\n👤 Creating default admin user...");

// Create default admin user (if not exists)
// Note: In a real setup, you would use bcrypt in Node.js, but for mongosh we'll use a simple hash
const adminPassword = "CHANGE_ADMIN_PASSWORD";
// Simple hash for demonstration - use proper bcrypt in production
const hashedPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMye.IY4QKqKqTmCqJfK5qKqTmCqJfK5qKq";

const defaultAdmin = {
    id: "admin-001",
    username: "admin",
    email: "admin@freetime.local",
    password: hashedPassword,
    name: "System Administrator",
    publicTag: "@admin",
    role: "ADMIN",
    tags: ["system", "administrator"],
    status: "active",
    isOnline: false,
    avatarUrl: null,
    phoneNumber: null,
    lastSeen: new Date(),
    createdAt: new Date(),
    updatedAt: new Date()
};

// Check if admin already exists
const existingAdmin = db.users.findOne({ username: "admin" });
if (!existingAdmin) {
    db.users.insertOne(defaultAdmin);
    print("✅ Default admin user created");
    print("   Username: admin");
    print("   Password: CHANGE_ADMIN_PASSWORD");
    print("   ⚠️  CHANGE THIS PASSWORD IMMEDIATELY!");
} else {
    print("ℹ️  Admin user already exists");
}

print("\n📊 Creating sample data...");

// Create sample users for testing
const sampleUsers = [
    {
        id: "user-001",
        username: "john_doe",
        email: "john@example.com",
        password: "$2a$10$N9qo8uLOickgx2ZMRZoMye.IY4QKqKqTmCqJfK5qKqTmCqJfK5qKq",
        name: "John Doe",
        publicTag: "@john_doe",
        role: "USER",
        tags: ["verified"],
        status: "active",
        isOnline: false,
        avatarUrl: null,
        phoneNumber: null,
        lastSeen: new Date(),
        createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000), // 7 days ago
        updatedAt: new Date()
    },
    {
        id: "user-002",
        username: "jane_smith",
        email: "jane@example.com",
        password: "$2a$10$N9qo8uLOickgx2ZMRZoMye.IY4QKqKqTmCqJfK5qKqTmCqJfK5qKq",
        name: "Jane Smith",
        publicTag: "@jane_smith",
        role: "VERIFIED",
        tags: ["verified", "premium"],
        status: "active",
        isOnline: true,
        avatarUrl: null,
        phoneNumber: null,
        lastSeen: new Date(),
        createdAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000), // 5 days ago
        updatedAt: new Date()
    },
    {
        id: "user-003",
        username: "bob_wilson",
        email: "bob@example.com",
        password: "$2a$10$N9qo8uLOickgx2ZMRZoMye.IY4QKqKqTmCqJfK5qKqTmCqJfK5qKq",
        name: "Bob Wilson",
        publicTag: "@bob_wilson",
        role: "MODERATOR",
        tags: ["moderator", "verified"],
        status: "active",
        isOnline: false,
        avatarUrl: null,
        phoneNumber: null,
        lastSeen: new Date(Date.now() - 2 * 60 * 60 * 1000), // 2 hours ago
        createdAt: new Date(Date.now() - 14 * 24 * 60 * 60 * 1000), // 14 days ago
        updatedAt: new Date()
    }
];

// Insert sample users if they don't exist
sampleUsers.forEach(user => {
    const existing = db.users.findOne({ username: user.username });
    if (!existing) {
        db.users.insertOne(user);
        print(`✅ Created sample user: ${user.username}`);
    }
});

// Create sample peer
const samplePeer = {
    id: "peer-001",
    name: "Local-Peer-1",
    type: "LOCAL_IP",
    address: "127.0.0.1",
    port: 9080,
    apiKey: "$2a$10$N9qo8uLOickgx2ZMRZoMye.IY4QKqKqTmCqJfK5qKqTmCqJfK5qKq",
    region: "Local",
    connected: false,
    lastConnected: new Date(),
    latency: null,
    uptime: 0,
    messageCount: 0,
    capabilities: ["message_routing", "user_sync"],
    createdAt: new Date(),
    updatedAt: new Date()
};

const existingPeer = db.peers.findOne({ name: samplePeer.name });
if (!existingPeer) {
    db.peers.insertOne(samplePeer);
    print("✅ Created sample peer: Local-Peer-1");
}

// Create sample chat
const johnUser = db.users.findOne({ username: "john_doe" });
const janeUser = db.users.findOne({ username: "jane_smith" });

if (johnUser && janeUser) {
    const sampleChat = {
        id: "chat-001",
        type: "direct",
        participants: [johnUser.id, janeUser.id],
        participantNames: ["john_doe", "jane_smith"],
        lastMessageAt: new Date(),
        messageCount: 0,
        isActive: true,
        createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000), // 3 days ago
        updatedAt: new Date()
    };

    const existingChat = db.chats.findOne({
        participants: { $all: [johnUser.id, janeUser.id], $size: 2 }
    });

    if (!existingChat) {
        db.chats.insertOne(sampleChat);
        print("✅ Created sample chat between john_doe and jane_smith");
    }
}

print("\n📈 Database statistics...");
print("========================");

const stats = {
    users: db.users.countDocuments(),
    peers: db.peers.countDocuments(),
    chats: db.chats.countDocuments(),
    messages: db.messages.countDocuments(),
    logs: db.logs.countDocuments()
};

print(`👥 Users: ${stats.users}`);
print(`🌐 Peers: ${stats.peers}`);
print(`💬 Chats: ${stats.chats}`);
print(`📨 Messages: ${stats.messages}`);
print(`📋 Logs: ${stats.logs}`);

print("\n🔍 Verifying indexes...");
print("========================");

// List all indexes for verification
const collections = ["users", "peers", "logs", "messages", "chats", "friends", "peer_messages"];

collections.forEach(collectionName => {
    const indexes = db.getCollection(collectionName).getIndexes();
    print(`\n📊 ${collectionName} indexes:`);
    indexes.forEach(index => {
        const key = Object.keys(index.key).map(k => `${k}: ${index.key[k]}`).join(", ");
        const unique = index.unique ? " (unique)" : "";
        const sparse = index.sparse ? " (sparse)" : "";
        print(`  - ${key}${unique}${sparse}`);
    });
});

print("\n✅ Database setup completed successfully!");
print("========================================");
print("\n📋 Summary:");
print("- Database: securechat");
print("- Collections: 7 created");
print("- Indexes: Optimized for performance");
print("- Default admin: admin / CHANGE_ADMIN_PASSWORD");
print("- Sample data: Created for testing");
print("\n⚠️  IMPORTANT:");
print("1. Change the default admin password immediately");
print("2. Update JWT_SECRET in config/.env");
print("3. Configure proper security settings");
print("4. Set up backup procedures");
print("\n🚀 FreeTime Master-Server is ready!");
