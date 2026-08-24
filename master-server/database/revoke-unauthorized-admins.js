#!/usr/bin/env node

const { MongoClient } = require('mongodb');

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/freetime';

const UNAUTHORIZED_ADMINS = ['retr0', 'retro'];

async function revokeAdminRoles() {
    console.log('Connecting to MongoDB at:', MONGODB_URI);
    const client = new MongoClient(MONGODB_URI, {
        connectTimeoutMS: 5000,
        serverSelectionTimeoutMS: 5000
    });

    try {
        await client.connect();
        const db = client.db();
        const usersCollection = db.collection('users');

        console.log('\n Revoking unauthorized admin roles...\n');

        for (const username of UNAUTHORIZED_ADMINS) {
            try {
                const user = await usersCollection.findOne({
                    // match usernames case-insensitively
                    username: { $regex: new RegExp(`^${username}$`, 'i') }
                });

                if (!user) {
                    console.log(` User '${username}' not found in database, skipping...`);
                    continue;
                }

                if (user.role !== 'ADMIN') {
                    console.log(` User '${user.username}' is NOT an admin (Current role: ${user.role || 'USER'})`);
                    continue;
                }

                const result = await usersCollection.updateOne(
                    { id: user.id },
                    {
                        $set: {
                            role: 'USER',
                            updatedAt: new Date(),
                            revokedAt: new Date(),
                            revocationReason: 'Unauthorized admin privilege'
                        }
                    }
                );

                if (result.modifiedCount > 0) {
                    console.log(` SUCCESS: Revoked ADMIN role from user '${user.username}'. Changed to USER.`);
                } else {
                    console.log(` User '${user.username}' is an ADMIN but the update failed.`);
                }
            } catch (err) {
                console.error(` Error processing user '${username}':`, err.message);
            }
        }

        console.log('\n Revocation complete!\n');

    } catch (err) {
        console.error(' Database connection failed:', err.message);
        console.error(' Ensure MongoDB is running on', MONGODB_URI);
        process.exit(1);
    } finally {
        await client.close();
    }
}

if (require.main === module) {
    revokeAdminRoles().catch(err => {
        console.error('Fatal error:', err);
        process.exit(1);
    });
}
