#!/usr/bin/env node

const { MongoClient } = require('mongodb');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');

const MONGODB_URI = 'mongodb://127.0.0.1:27017/freetime';

async function cleanupAndSetup() {
    const client = new MongoClient(MONGODB_URI);

    try {
        await client.connect();
        const db = client.db('freetime');
        const usersCollection = db.collection('users');

        console.log(' Starting database cleanup...');
        console.log('================================================');

        console.log('\n Current users:');
        const currentUsers = await usersCollection.find({}).toArray();
        currentUsers.forEach(u => {
            console.log(` - ${u.username} (${u.email})`);
        });

        console.log('\n Deleting all users...');
        // dev reset script
        const deleteResult = await usersCollection.deleteMany({});
        console.log(` Deleted ${deleteResult.deletedCount} users`);

        console.log('\n Creating admin account (admin@freetime.local)...');
        const adminPassword = await bcrypt.hash('Bubufuz42', 10);
        const adminUser = {
            id: uuidv4(),
            username: 'admin',
            email: 'admin@freetime.local',
            password: adminPassword,
            displayName: 'System Administrator',
            role: 'ADMIN',
            tags: ['admin', 'owner'],
            status: 'ONLINE',
            isOnline: true,
            phoneNumber: null,
            publicTag: '@admin',
            avatarUrl: null,
            createdAt: new Date(),
            updatedAt: new Date(),
            isActive: true,
            isVerified: true,
            twoFactorEnabled: false,
            twoFactorSecret: null,
            lastLogin: new Date(),
            lastSeen: new Date(),
            isVisible: true
        };
        await usersCollection.insertOne(adminUser);
        console.log(` Created admin user`);

        console.log('\n Creating developer account (coder.dev@hotmail.com)...');
        const devPassword = await bcrypt.hash('DevPass123!', 10);
        const devUser = {
            id: uuidv4(),
            username: 'developer',
            email: 'coder.dev@hotmail.com',
            password: devPassword,
            displayName: 'Developer',
            role: 'ADMIN',
            tags: ['developer', 'support'],
            status: 'ONLINE',
            isOnline: true,
            phoneNumber: null,
            publicTag: '@developer',
            avatarUrl: null,
            createdAt: new Date(),
            updatedAt: new Date(),
            isActive: true,
            isVerified: true,
            twoFactorEnabled: false,
            twoFactorSecret: null,
            lastLogin: new Date(),
            lastSeen: new Date(),
            isVisible: true
        };
        await usersCollection.insertOne(devUser);
        console.log(` Created developer user`);

        console.log('\n Creating test account (test / heythered3d@gmail.com)...');
        const testPassword = await bcrypt.hash('123456', 10);
        const testUser = {
            id: uuidv4(),
            username: 'test',
            email: 'heythered3d@gmail.com',
            password: testPassword,
            displayName: 'Test User',
            role: 'USER',
            tags: ['bot', 'verified'],
            status: 'ONLINE',
            isOnline: true,
            phoneNumber: null,
            publicTag: '@test',
            avatarUrl: null,
            createdAt: new Date(),
            updatedAt: new Date(),
            isActive: true,
            isVerified: true,
            twoFactorEnabled: false,
            twoFactorSecret: null,
            lastLogin: new Date(),
            lastSeen: new Date(),
            isVisible: true
        };
        await usersCollection.insertOne(testUser);
        console.log(` Created test user`);

        console.log('\n Verifying database:');
        const finalUsers = await usersCollection.find({}).toArray();
        console.log(`Total users: ${finalUsers.length}`);
        finalUsers.forEach(u => {
            console.log(` - ${u.username} (${u.email}) [${u.role}]`);
        });

        console.log('\n================================================');
        console.log(' Database cleanup and setup complete!');
        console.log('\nTest credentials:');
        console.log(' Username: test');
        console.log(' Email: heythered3d@gmail.com');
        console.log(' Password: 123456');

    } catch (error) {
        console.error(' Error:', error.message);
    } finally {
        await client.close();
    }
}

cleanupAndSetup();
