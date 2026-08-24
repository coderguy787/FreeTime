const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');

db = db.getSiblingDB('freetime');

console.log(" Starting database cleanup and user setup...");
console.log("================================================");

console.log("\n Current users in database:");
let currentUsers = db.users.find({}, { username: 1, email: 1 }).toArray();
currentUsers.forEach(u => {
    console.log(` - ${u.username} (${u.email})`);
});

console.log("\n Removing all users...");
// dev only, deletes all users
let deletedCount = db.users.deleteMany({}).deletedCount;
console.log(` Deleted ${deletedCount} users`);

console.log("\n Creating admin account (admin@freetime.local)...");
const adminPassword = bcrypt.hashSync('Bubufuz42', 10);
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
db.users.insertOne(adminUser);
console.log(` Created admin user`);

console.log("\n Creating developer account (coder.dev@hotmail.com)...");
const devPassword = bcrypt.hashSync('DevPass123!', 10);
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
db.users.insertOne(devUser);
console.log(` Created developer user`);

console.log("\n Creating test account (test / heythered3d@gmail.com)...");
const testPassword = bcrypt.hashSync('123456', 10);
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
db.users.insertOne(testUser);
console.log(` Created test user`);

console.log("\n Verifying database setup:");
let finalUsers = db.users.find({}, { username: 1, email: 1, role: 1 }).toArray();
console.log(`Total users: ${finalUsers.length}`);
finalUsers.forEach(u => {
    console.log(` - ${u.username} (${u.email}) [${u.role}]`);
});

console.log("\n================================================");
console.log(" Database cleanup and setup complete!");
console.log("\nTest credentials:");
console.log(" Username: test");
console.log(" Email: heythered3d@gmail.com");
console.log(" Password: 123456");
