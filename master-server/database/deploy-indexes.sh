#!/bin/bash

echo "SecureChat Database Index Deployment"
echo "======================================"
echo ""

echo "Checking MongoDB connection..."
if ! command -v mongosh &> /dev/null; then
    echo "[WARN] mongosh not found. Make sure MongoDB is installed."
    echo " https://docs.mongodb.com/manual/installation/"
    exit 1
fi

if mongosh --eval "db.version()" > /dev/null 2>&1; then
    echo "[OK] MongoDB is running"
else
    echo "[ERROR] Cannot connect to MongoDB"
    echo " Start MongoDB with: mongod --dbpath /data/db"
    exit 1
fi

echo ""
echo "Creating indexes in 'freetime' database..."
echo ""

node create-indexes.js

if [ $? -eq 0 ]; then
    echo ""
    echo "[OK] Database indexes created successfully!"
    echo ""
    echo "Index Summary:"
    echo " • mediaDownloadRequests: 6 indexes (with TTL auto-cleanup)"
    echo " • groupDeletionVotes: 5 indexes"
    echo " • channelMessages: 4 indexes"
    echo " • channels: 3 indexes"
    echo " • users: text search index"
    echo " • friendRequests: TTL auto-cleanup"
    echo ""
    echo " Ready to start the server!"
    echo " npm start"
    exit 0
else
    echo ""
    echo "[ERROR] Failed to create indexes"
    echo " Check the error messages above"
    exit 1
fi
