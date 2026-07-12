#!/bin/bash

# Quick restart script for FreeTime services
# Run this on the server: bash restart-admin.sh

echo "🔄 Restarting FreeTime Admin Panel..."

# Kill any existing admin panel processes
pkill -f "admin-panel" 2>/dev/null || true
pkill -9 -f "node.*3001" 2>/dev/null || true

# Wait for port to free up
sleep 2

# Kill any process on port 3001
fuser -k 3001/tcp 2>/dev/null || lsof -ti :3001 | xargs kill -9 2>/dev/null || true

sleep 1

# Go to master-server directory  
cd ~/master-server || exit 1

# Start admin panel in background
echo "Starting admin panel on port 3001..."
nohup node admin-panel/admin-panel-server.js > logs/admin-panel.log 2>&1 &

sleep 2

# Verify
if lsof -i :3001 > /dev/null 2>&1; then
    echo "✅ Admin panel started successfully on port 3001"
    curl -s http://localhost:3001/ | head -20
else
    echo "❌ Admin panel failed to start"
    tail -50 logs/admin-panel.log
fi

echo ""
echo "🔄 Restarting API Server..."
pkill -f "master-server-api" 2>/dev/null || true
sleep 2

echo "Starting API server on port 443..."
nohup node api/master-server-api.js > logs/api-service.log 2>&1 &

sleep 3

if lsof -i :443 > /dev/null 2>&1; then
    echo "✅ API server started on port 443"
else
    echo "❌ API server failed to start"  
    tail -50 logs/api-service.log
fi

echo ""
echo "✅ Services restarting complete"
echo "Check admin panel: http://192.168.1.100:3001/"
echo ""
