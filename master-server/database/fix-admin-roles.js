#!/usr/bin/env node

const { MongoClient } = require('mongodb');

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';

const ADMIN_USERNAMES = ['admin'];

// one-time fix for missing role fields
async function fixAdminRoles() {
    const client = new MongoClient(MONGODB_URI);

    try {
        await client.connect();
        const db = client.db();
        const usersCollection = db.collection('users');

        console.log('\n Fixing missing admin roles...\n');

        for (const username of ADMIN_USERNAMES) {
            try {
                const user = await usersCollection.findOne({
                    $or: [
                        { username: username },
                        { username: username.toLowerCase() }
                    ]
                });

                if (!user) {
                    console.log(` User '${username}' not found, skipping...`);
                    continue;
                }

                if (user.role === 'ADMIN') {
                    console.log(` User '${user.username}' already has ADMIN role`);
                    continue;
                }

                const result = await usersCollection.updateOne(
                    { id: user.id },
                    {
                        $set: {
                            role: 'ADMIN',
                            updatedAt: new Date()
                        }
                    }
                );

                if (result.modifiedCount > 0) {
                    console.log(` Updated user '${user.username}' to ADMIN role`);
                } else {
                    console.log(` User '${user.username}' exists but update failed`);
                }
            } catch (err) {
                console.error(` Error processing user '${username}':`, err.message);
            }
        }

        console.log('\n Admin role migration complete!\n');

    } catch (err) {
        console.error(' Migration failed:', err.message);
        process.exit(1);
    } finally {
        await client.close();
    }
}

if (require.main === module) {
    fixAdminRoles().catch(err => {
        console.error('Fatal error:', err);
        process.exit(1);
    });
}

module.exports = { fixAdminRoles };
