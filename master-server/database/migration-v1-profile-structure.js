print(" FreeTime Database Migration - Profile Structure v1");
print("======================================================\n");

db = db.getSiblingDB('freetime');

print(" Analyzing existing user documents...");
const userCount = db.users.countDocuments({});
const usersWithBio = db.users.countDocuments({ bio: { $exists: true } });
const usersWithTags = db.users.countDocuments({ tags: { $exists: true } });
const usersWithStatus = db.users.countDocuments({ status: { $exists: true } });

print(` Total users: ${userCount}`);
print(` Users with bio field: ${usersWithBio}`);
print(` Users with tags field: ${usersWithTags}`);
print(` Users with status field: ${usersWithStatus}\n`);

print(" Creating profile structure for users...\n");

// v1 profile structure migration
db.users.updateMany(
    { profile: { $exists: false } },
    { $set: { profile: {} } },
    { multi: true }
);
print(" Initialized profile objects\n");

if (usersWithBio > 0) {
    print(" Migrating bio field profile.bio...");
    const bioResult = db.users.updateMany(
        { bio: { $exists: true, $ne: null } },
        [
            {
                $set: {
                    "profile.bio": "$bio"
                }
            }
        ]
    );
    print(` Updated ${bioResult.modifiedCount} documents\n`);
}

if (usersWithTags > 0) {
    print(" Migrating tags field profile.tags...");
    const tagsResult = db.users.updateMany(
        { tags: { $exists: true, $ne: null } },
        [
            {
                $set: {
                    "profile.tags": "$tags"
                }
            }
        ]
    );
    print(` Updated ${tagsResult.modifiedCount} documents\n`);
}

if (usersWithStatus > 0) {
    print(" Migrating status field profile.status...");
    const statusResult = db.users.updateMany(
        { status: { $exists: true, $ne: null } },
        [
            {
                $set: {
                    "profile.status": "$status"
                }
            }
        ]
    );
    print(` Updated ${statusResult.modifiedCount} documents\n`);
}

const usersWithAvatar = db.users.countDocuments({ avatarUrl: { $exists: true } });
if (usersWithAvatar > 0) {
    print(" Migrating avatarUrl field profile.profileImageUrl...");
    const avatarResult = db.users.updateMany(
        { avatarUrl: { $exists: true, $ne: null } },
        [
            {
                $set: {
                    "profile.profileImageUrl": "$avatarUrl"
                }
            }
        ]
    );
    print(` Updated ${avatarResult.modifiedCount} documents\n`);
}

print(" Ensuring profile.displayName exists...");
const displayNameResult = db.users.updateMany(
    { "profile.displayName": { $exists: false } },
    [
        {
            $set: {
                "profile.displayName": {
                    $cond: [
                        { $gt: ["$displayName", null] },
                        "$displayName",
                        "$username"
                    ]
                }
            }
        }
    ]
);
print(` Updated ${displayNameResult.modifiedCount} documents\n`);

print(" Ensuring profile.tags array exists...");
const tagsArrayResult = db.users.updateMany(
    { "profile.tags": { $exists: false } },
    { $set: { "profile.tags": [] } }
);
print(` Updated ${tagsArrayResult.modifiedCount} documents\n`);

print(" Setting default profile.privacyLevel...");
const privacyResult = db.users.updateMany(
    { "profile.privacyLevel": { $exists: false } },
    { $set: { "profile.privacyLevel": "public" } }
);
print(` Updated ${privacyResult.modifiedCount} documents\n`);

print(" OLD FIELDS CLEANUP (optional - do only after verification):");
print(" To remove old root-level fields after migrating to profile.*:");
print("");
print(" db.users.updateMany({}, { $unset: { bio: 1, tags: 1, status: 1, avatarUrl: 1 } });");
print("\n");

print(" Creating/updating indexes for profile structure...\n");

db.users.createIndex({ "profile.tags": 1 });
print(" Created index on profile.tags");

db.users.createIndex({ "profile.displayName": 1 });
print(" Created index on profile.displayName");

db.users.createIndex({ "profile.privacyLevel": 1 });
print(" Created index on profile.privacyLevel");

db.users.createIndex({ "profile.status": 1 });
print(" Created index on profile.status\n");

db.users.createIndex({ "profile.privacyLevel": 1, "createdAt": -1 });
print(" Created compound index on profile.privacyLevel + createdAt");

db.users.createIndex({ username: "text", "profile.displayName": "text", "profile.bio": "text" });
print(" Created full-text search index\n");

print(" Verifying migration...\n");

const verifyCount = db.users.countDocuments({ profile: { $exists: true } });
const withProfileBio = db.users.countDocuments({ "profile.bio": { $exists: true } });
const withProfileTags = db.users.countDocuments({ "profile.tags": { $exists: true } });
const withProfileStatus = db.users.countDocuments({ "profile.status": { $exists: true } });

print(` Users with profile object: ${verifyCount}`);
print(` Users with profile.bio: ${withProfileBio}`);
print(` Users with profile.tags: ${withProfileTags}`);
print(` Users with profile.status: ${withProfileStatus}\n`);

print(" Migration complete!");
print("\n IMPORTANT: After verifying data in profile.*, optionally run:");
print(" db.users.updateMany({}, { $unset: { bio: 1, tags: 1, status: 1, avatarUrl: 1 } });");
print("\n This removes the old root-level fields to save space.\n");
