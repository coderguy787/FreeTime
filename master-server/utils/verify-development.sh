#!/bin/bash

# FreeTime Master-Server Development Verification Script
# Checks project configuration against AI instructions

echo "Master-Server Development Verification"
echo "======================================="
echo ""

PASSED=0
FAILED=0

# 1. Check Node.js
if command -v node &> /dev/null; then
  NODE_VERSION=$(node --version)
  echo "✓ Node.js installed: $NODE_VERSION"
  ((PASSED++))
else
  echo "✗ Node.js not found"
  ((FAILED++))
fi

# 2. Check MongoDB
if sudo systemctl is-active --quiet mongod; then
  echo "✓ MongoDB running"
  ((PASSED++))
else
  echo "✗ MongoDB not running"
  ((FAILED++))
fi

# 3. Check master-server directory
if [ -d . ]; then
  echo "✓ Master-server directory exists"
  ((PASSED++))
else
  echo "✗ Master-server directory not found"
  ((FAILED++))
fi

# 4. Check .env file
if [ -f config/.env ]; then
  echo "✓ Environment file exists"
  ((PASSED++))
else
  echo "✗ Environment file not found"
  ((FAILED++))
fi

# 5. Check package.json
if [ -f package.json ]; then
  echo "✓ Package configuration exists"
  ((PASSED++))
else
  echo "✗ Package configuration not found"
  ((FAILED++))
fi

# 6. Check dependencies installed
if [ -d node_modules ]; then
  echo "✓ Node.js dependencies installed"
  ((PASSED++))
else
  echo "✗ Node.js dependencies not installed"
  ((FAILED++))
fi

# 7. Check API server file
if [ -f api/master-server-api.js ]; then
  echo "✓ API server file exists"
  ((PASSED++))
else
  echo "✗ API server file not found"
  ((FAILED++))
fi

# 8. Check WebSocket server file
if [ -f websocket/securechat-websocket-server.js ]; then
  echo "✓ WebSocket server file exists"
  ((PASSED++))
else
  echo "✗ WebSocket server file not found"
  ((FAILED++))
fi

# 9. Check Peer Network server file
if [ -f peer-network/peer-master-server.js ]; then
  echo "✓ Peer Network server file exists"
  ((PASSED++))
else
  echo "✗ Peer Network server file not found"
  ((FAILED++))
fi

# 10. Check database setup script
if [ -f database/setup-database.js ]; then
  echo "✓ Database setup script exists"
  ((PASSED++))
else
  echo "✗ Database setup script not found"
  ((FAILED++))
fi

# 11. Check systemd service files
if [ -f config/master-server-api.service ]; then
  echo "✓ API service file exists"
  ((PASSED++))
else
  echo "✗ API service file not found"
  ((FAILED++))
fi

if [ -f config/master-server-websocket.service ]; then
  echo "✓ WebSocket service file exists"
  ((PASSED++))
else
  echo "✗ WebSocket service file not found"
  ((FAILED++))
fi

if [ -f config/master-server-peer.service ]; then
  echo "✓ Peer Network service file exists"
  ((PASSED++))
else
  echo "✗ Peer Network service file not found"
  ((FAILED++))
fi

# 12. Check utility files
if [ -f utils/health-checker.js ]; then
  echo "✓ Health checker utility exists"
  ((PASSED++))
else
  echo "✗ Health checker utility not found"
  ((FAILED++))
fi

if [ -f utils/database-utils.js ]; then
  echo "✓ Database utility exists"
  ((PASSED++))
else
  echo "✗ Database utility not found"
  ((FAILED++))
fi

if [ -f peer-manager/peer-manager.js ]; then
  echo "✓ Peer manager exists"
  ((PASSED++))
else
  echo "✗ Peer manager not found"
  ((FAILED++))
fi

# 13. Check Android app configuration
if [ -f app/gradle.properties ]; then
  echo "✓ Android app gradle.properties exists"
  ((PASSED++))
else
  echo "✗ Android app gradle.properties not found"
  ((FAILED++))
fi

if [ -f app/build.gradle ]; then
  echo "✓ Android app build.gradle exists"
  ((PASSED++))
else
  echo "✗ Android app build.gradle not found"
  ((FAILED++))
fi

# 14. Check AI instructions compliance
echo ""
echo "🔍 Checking AI Instructions Compliance..."

# Check database name
if grep -q "MONGO_DB=freetime" config/.env; then
  echo "✓ Database name set to 'freetime'"
  ((PASSED++))
else
  echo "✗ Database name not set to 'freetime'"
  ((FAILED++))
fi

# Check ports configuration
if grep -q "PORT_API=80" config/.env && grep -q "PORT_WEBSOCKET=8080" config/.env && grep -q "PORT_PEER=9080" config/.env; then
  echo "✓ Ports configured correctly (API:80, WS:8080, Peer:9080)"
  ((PASSED++))
else
  echo "✗ Ports not configured correctly"
  ((FAILED++))
fi

# Check Android app ports
if grep -q "SERVER_PORT=8000" app/gradle.properties && grep -q "PEER_PORT=9080" app/gradle.properties; then
  echo "✓ Android app ports configured correctly"
  ((PASSED++))
else
  echo "✗ Android app ports not configured correctly"
  ((FAILED++))
fi

# Check API health endpoint
if grep -q "/api/health" api/master-server-api.js; then
  echo "✓ API health endpoint exists"
  ((PASSED++))
else
  echo "✗ API health endpoint missing"
  ((FAILED++))
fi

# Check CORS configuration
if grep -q "ALLOWED_ORIGINS" config/.env; then
  echo "✓ CORS configuration exists"
  ((PASSED++))
else
  echo "✗ CORS configuration missing"
  ((FAILED++))
fi

echo ""
echo "Results: $PASSED passed, $FAILED failed"

if [ $FAILED -eq 0 ]; then
  echo "✓✓✓ Development Setup Complete - Ready for Testing ✓✓✓"
  echo ""
  echo "🚀 Next Steps:"
  echo "   1. Start services: ./start-all.sh"
  echo "   2. Test endpoints: curl http://localhost/api/health"
  echo "   3. Build Android app: cd app && ./gradlew assembleDebug"
  exit 0
else
  echo "✗✗✗ Development Setup Incomplete - Fix issues above ✗✗✗"
  exit 1
fi
