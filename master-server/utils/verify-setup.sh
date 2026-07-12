#!/bin/bash

# FreeTime Master-Server Installation Verification Script
# Based on AI instructions for Debian 13 deployment

echo "Master-Server Installation Verification"
echo "========================================"
echo ""

PASSED=0
FAILED=0

# 1. Check Debian version
if [ -f /etc/os-release ] && grep -q 'VERSION_ID="13"' /etc/os-release; then
  echo "✓ Debian 13 verified"
  ((PASSED++))
else
  echo "✗ Debian version incorrect"
  ((FAILED++))
fi

# 2. Check Node.js
if command -v node &> /dev/null; then
  NODE_VERSION=$(node --version)
  echo "✓ Node.js installed: $NODE_VERSION"
  ((PASSED++))
else
  echo "✗ Node.js not found"
  ((FAILED++))
fi

# 3. Check MongoDB
if sudo systemctl is-active --quiet mongod; then
  echo "✓ MongoDB running"
  ((PASSED++))
else
  echo "✗ MongoDB not running"
  ((FAILED++))
fi

# 4. Check freetime user
if id freetime &> /dev/null; then
  echo "✓ FreeTime user created"
  ((PASSED++))
else
  echo "✗ FreeTime user not found"
  ((FAILED++))
fi

# 5. Check master-server directory
if [ -d /home/freetime/master-server ]; then
  echo "✓ Master-server directory exists"
  ((PASSED++))
else
  echo "✗ Master-server directory not found"
  ((FAILED++))
fi

# 6. Check .env file
if [ -f /home/freetime/master-server/config/.env ]; then
  echo "✓ Environment file exists"
  ((PASSED++))
else
  echo "✗ Environment file not found"
  ((FAILED++))
fi

# 7. Check API service
if sudo systemctl is-active --quiet master-server-api; then
  echo "✓ API service running"
  ((PASSED++))
else
  echo "✗ API service not running"
  ((FAILED++))
fi

# 8. Check WebSocket service
if sudo systemctl is-active --quiet master-server-websocket; then
  echo "✓ WebSocket service running"
  ((PASSED++))
else
  echo "✗ WebSocket service not running"
  ((FAILED++))
fi

# 9. Check Peer Network service
if sudo systemctl is-active --quiet master-server-peer; then
  echo "✓ Peer Network service running"
  ((PASSED++))
else
  echo "✗ Peer Network service not running"
  ((FAILED++))
fi

# 10. Check port 8081
if sudo netstat -tlnp 2>/dev/null | grep -q :8081; then
  echo "✓ Port 8081 (API) listening"
  ((PASSED++))
else
  echo "✗ Port 8081 not listening"
  ((FAILED++))
fi

# 11. Check port 8080
if sudo netstat -tlnp 2>/dev/null | grep -q :8080; then
  echo "✓ Port 8080 (WebSocket) listening"
  ((PASSED++))
else
  echo "✗ Port 8080 not listening"
  ((FAILED++))
fi

# 12. Check port 9080
if sudo netstat -tlnp 2>/dev/null | grep -q :9080; then
  echo "✓ Port 9080 (Peer Network) listening"
  ((PASSED++))
else
  echo "✗ Port 9080 not listening"
  ((FAILED++))
fi

# 13. Check API health
if curl -s http://localhost:8000/health &> /dev/null; then
  echo "✓ API endpoint responding"
  ((PASSED++))
else
  echo "✗ API endpoint not responding"
  ((FAILED++))
fi

# 14. Check WebSocket health
if curl -s http://localhost:8080/health &> /dev/null; then
  echo "✓ WebSocket endpoint responding"
  ((PASSED++))
else
  echo "✗ WebSocket endpoint not responding"
  ((FAILED++))
fi

# 15. Check Peer Network health
if curl -s http://localhost:9080/health &> /dev/null; then
  echo "✓ Peer Network endpoint responding"
  ((PASSED++))
else
  echo "✗ Peer Network endpoint not responding"
  ((FAILED++))
fi

# 16. Check database connectivity
if mongosh --eval "db.adminCommand('ping')" &> /dev/null; then
  echo "✓ Database connectivity working"
  ((PASSED++))
else
  echo "✗ Database connectivity failed"
  ((FAILED++))
fi

# 17. Check firewall enabled
if sudo ufw status | grep -q "Status: active"; then
  echo "✓ Firewall enabled"
  ((PASSED++))
else
  echo "✗ Firewall not active"
  ((FAILED++))
fi

# 18. Check log directory
if [ -d /home/freetime/master-server/logs ]; then
  echo "✓ Log directory exists"
  ((PASSED++))
else
  echo "✗ Log directory not found"
  ((FAILED++))
fi

# 19. Check package.json
if [ -f /home/freetime/master-server/package.json ]; then
  echo "✓ Package configuration exists"
  ((PASSED++))
else
  echo "✗ Package configuration not found"
  ((FAILED++))
fi

# 20. Check dependencies installed
if [ -d /home/freetime/master-server/node_modules ]; then
  echo "✓ Node.js dependencies installed"
  ((PASSED++))
else
  echo "✗ Node.js dependencies not installed"
  ((FAILED++))
fi

echo ""
echo "Results: $PASSED passed, $FAILED failed"

if [ $FAILED -eq 0 ]; then
  echo "✓✓✓ Setup Complete - System Ready ✓✓✓"
  exit 0
else
  echo "✗✗✗ Setup Incomplete - Fix issues above ✗✗✗"
  exit 1
fi
