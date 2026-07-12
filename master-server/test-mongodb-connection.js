#!/usr/bin/env node
/**
 * MongoDB Connection Test
 * Run this on your Debian server to verify MongoDB is accessible
 */

const mongoose = require('mongoose');
require('dotenv').config({ path: './config/.env' });

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/freetime';

console.log('🔍 Testing MongoDB Connection...');
console.log(`📍 Connection String: ${MONGODB_URI}`);
console.log('');

mongoose
  .connect(MONGODB_URI, {
    serverSelectionTimeoutMS: 5000,
    connectTimeoutMS: 10000,
  })
  .then(() => {
    console.log('✅ MongoDB Connected Successfully!');
    console.log(`📊 Database: ${mongoose.connection.name}`);
    console.log(`🖥️  Host: ${mongoose.connection.host}`);
    console.log(`🔌 Port: ${mongoose.connection.port}`);
    console.log('');
    console.log('✨ You can now start the master-server with: node api/master-server-api.js');
    process.exit(0);
  })
  .catch((err) => {
    console.error('❌ MongoDB Connection Failed!');
    console.error(`Error: ${err.message}`);
    console.error('');
    console.error('Troubleshooting:');
    console.error('1. Is MongoDB running? Check: sudo systemctl status mongod');
    console.error('2. Is port 27017 open? Check: sudo netstat -tulnp | grep 27017');
    console.error('3. Update MONGODB_URI in config/.env if needed');
    process.exit(1);
  });
