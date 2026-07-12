#!/bin/bash

# FreeTime Master-Server Health Check Script
# Based on AI instructions for Debian 13 deployment

echo "Master-Server Health Check - $(date)"
echo "=================================="

# Check API service
if sudo systemctl is-active --quiet master-server-api; then
  echo "✓ API Service: RUNNING"
else
  echo "✗ API Service: STOPPED"
fi

# Check WebSocket service
if sudo systemctl is-active --quiet master-server-websocket; then
  echo "✓ WebSocket Service: RUNNING"
else
  echo "✗ WebSocket Service: STOPPED"
fi

# Check Peer Network service
if sudo systemctl is-active --quiet master-server-peer; then
  echo "✓ Peer Network Service: RUNNING"
else
  echo "✗ Peer Network Service: STOPPED"
fi

# Check MongoDB
if sudo systemctl is-active --quiet mongod; then
  echo "✓ MongoDB: RUNNING"
else
  echo "✗ MongoDB: STOPPED"
fi

# Check API health endpoint
API_STATUS=$(curl -s http://localhost/api/health 2>/dev/null | grep -o '"status":"[^"]*"')
echo "API Status: $API_STATUS"

# Check WebSocket health endpoint
WS_STATUS=$(curl -s http://localhost:8080/health 2>/dev/null | grep -o '"status":"[^"]*"')
echo "WebSocket Status: $WS_STATUS"

# Check Peer Network health endpoint
PEER_STATUS=$(curl -s http://localhost:9080/health 2>/dev/null | grep -o '"status":"[^"]*"')
echo "Peer Network Status: $PEER_STATUS"

# Check port availability
echo ""
echo "Port Status:"
netstat -tlnp 2>/dev/null | grep -E '80|8080|9080|27017|3001' || echo "Unable to check ports"

echo ""
echo "Disk Usage:"
df -h / | tail -1

echo ""
echo "Memory Usage:"
free -h | grep Mem

echo ""
echo "System Uptime:"
uptime
