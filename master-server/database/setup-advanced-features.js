db = db.getSiblingDB('freetime');

print(" Setting up FreeTime Master-Server Advanced Features Database...");

print("\n Creating Files collection...");
db.Files.createIndex({ "fileId": 1 }, { unique: true });
db.Files.createIndex({ "uploadedBy": 1 });
db.Files.createIndex({ "chatWithUser": 1 });
db.Files.createIndex({ "uploadedAt": 1 });
// ttl cleanup driven by expiresAt
db.Files.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });
db.Files.createIndex({ "messageId": 1 });

print("\n Creating FCMTokens collection...");
db.FCMTokens.createIndex({ "userId": 1 });
db.FCMTokens.createIndex({ "fcmToken": 1 }, { unique: true });
db.FCMTokens.createIndex({ "registeredAt": 1 });
db.FCMTokens.createIndex({ "lastUsedAt": 1 });

print("\n Creating PublicKeys collection...");
db.PublicKeys.createIndex({ "userId": 1 }, { unique: true });
db.PublicKeys.createIndex({ "createdAt": 1 });

print("\n Creating file storage directories...");

print("\n Setting up file upload configuration...");
db.systemConfig.updateOne(
    { configType: "file_upload" },
    {
        $set: {
            configType: "file_upload",
            settings: {
                maxFileSize: 104857600,
                allowedTypes: [
                    "image/jpeg", "image/png", "image/gif", "image/webp",
                    "video/mp4", "video/webm", "video/3gpp",
                    "audio/mp3", "audio/wav", "audio/ogg", "audio/m4a",
                    "application/pdf", "text/plain", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ],
                storagePath: "/var/freetime/uploads/files/",
                retentionDays: 30,
                maxFilesPerUser: 1000,
                virusScanning: false
            },
            createdAt: new Date(),
            updatedAt: new Date()
        }
    },
    { upsert: true }
);

print("\n Setting up FCM configuration...");
db.systemConfig.updateOne(
    { configType: "fcm" },
    {
        $set: {
            configType: "fcm",
            settings: {
                maxTokensPerUser: 5,
                tokenCleanupDays: 90,
                rateLimitPerMinute: 10,
                enableTopics: true,
                defaultTopics: ["messages", "system"]
            },
            createdAt: new Date(),
            updatedAt: new Date()
        }
    },
    { upsert: true }
);

print("\n Setting up encryption configuration...");
db.systemConfig.updateOne(
    { configType: "encryption" },
    {
        $set: {
            configType: "encryption",
            settings: {
                keyAlgorithm: "RSA",
                keySize: 2048,
                keyRotationDays: 365,
                enableE2E: true,
                backupKeys: false
            },
            createdAt: new Date(),
            updatedAt: new Date()
        }
    },
    { upsert: true }
);

print("\n Updating existing users with new fields...");
db.users.updateMany(
    {},
    {
        $set: {
            "profile": {
                bio: "",
                status: "Available",
                privacyLevel: "public",
                profileImageUrl: "",
                lastUpdated: new Date()
            },
            "encryption": {
                publicKey: "",
                keyType: "RSA",
                keySize: 2048,
                createdAt: null
            },
            "notifications": {
                fcmTokens: [],
                pushEnabled: true,
                emailEnabled: true,
                lastNotificationRead: new Date()
            },
            updatedAt: new Date()
        }
    }
);

print("\n Database setup completed successfully!");
print("\n Collections created:");
print(" - Files (file management)");
print(" - FCMTokens (push notifications)");
print(" - PublicKeys (E2E encryption)");

print("\n Configurations added:");
print(" - file_upload (file upload limits and settings)");
print(" - fcm (push notification settings)");
print(" - encryption (key management)");

print("\n Users updated with new fields:");
print(" - profile (user profiles)");
print(" - encryption (public keys)");
print(" - notifications (FCM tokens)");

print("\n Ready for advanced features implementation!");
