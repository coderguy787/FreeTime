#!/bin/bash

# FreeTime Master-Server Service Starter
# Starts all required services on Linux production server
# - MongoDB (systemd service on port 27017)
# - API Server (HTTPS Port 443)
# - Admin Panel (Port 3001)
# - WebSocket Server (Port 8080)
# - Peer Network (Port 9080)

# Disable bash job control output
set +m

# Service ports (API runs on HTTPS port 443 directly)
API_PORT=443
ADMIN_PORT=3001
WEBSOCKET_PORT=8080
PEER_PORT=9080
MONGODB_PORT=27017
REDIS_PORT=6379

# Service flags (default to enabled)
SKIP_PEER=${SKIP_PEER:-0}

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR" || exit 1

# ============================================================================
# STARTUP BANNER
# ============================================================================

echo ""
echo "================================"
echo "FreeTime Master-Server Startup"
echo "================================"
echo ""

# ============================================================================
# PRE-FLIGHT CHECKS
# ============================================================================

echo "Checking prerequisites..."
echo ""

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "[ERROR] Node.js is not installed"
    exit 1
fi
NODE_VERSION=$(node --version)
echo "[OK] Node.js: $NODE_VERSION"

# Check npm modules
if [ ! -d "node_modules" ] || [ ! -d "node_modules/express" ]; then
    echo "[WARN] Dependencies missing, installing..."
    npm install --production >/dev/null 2>&1
fi
echo "[OK] npm dependencies found"

# Check config file
if [ ! -f "config/.env" ]; then
    echo "[ERROR] config/.env not found"
    echo "[ERROR] Please create config/.env with MongoDB and JWT configuration"
    exit 1
fi
echo "[OK] Configuration file found"

# Load environment variables (strip Windows CR characters if present)
echo "Loading environment variables from config/.env..."
TEMP_ENV_FILE="$(mktemp)"
tr -d '\r' < config/.env > "$TEMP_ENV_FILE"
set -a
source "$TEMP_ENV_FILE"
set +a
rm -f "$TEMP_ENV_FILE"

echo "[INFO] Applying port overrides from config/.env (if present)"
if [ -n "$PORT_API" ]; then
    API_PORT="$PORT_API"
    echo "[INFO] Using API port from config/.env: $API_PORT"
fi
if [ -n "$PORT_ADMIN_PANEL" ]; then
    ADMIN_PORT="$PORT_ADMIN_PANEL"
    echo "[INFO] Using Admin panel port from config/.env: $ADMIN_PORT"
fi
if [ -n "$PORT_WEBSOCKET" ]; then
    WEBSOCKET_PORT="$PORT_WEBSOCKET"
    echo "[INFO] Using WebSocket port from config/.env: $WEBSOCKET_PORT"
fi
if [ -n "$PORT_PEER" ]; then
    PEER_PORT="$PORT_PEER"
    echo "[INFO] Using Peer port from config/.env: $PEER_PORT"
fi
export PORT_API ADMIN_PORT PORT_WEBSOCKET PORT_PEER

echo "[OK] Environment variables loaded"

# Verify critical environment variables
echo "Verifying environment variables..."
if [ -z "$JWT_SECRET" ]; then
    echo "[ERROR] JWT_SECRET not set in config/.env"
    echo "[ERROR] Add: JWT_SECRET=your-secret-key-here"
    exit 1
fi
echo "[OK] JWT_SECRET is configured"

if [ -z "$MONGODB_URI" ]; then
    echo "[WARN] MONGODB_URI not set, will use default: mongodb://admin:changeme@127.0.0.1:27017/freetime?authSource=admin"
fi

# Check Docker installation
echo "Checking Docker installation..."
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    echo "[OK] Docker: $DOCKER_VERSION"
    DOCKER_AVAILABLE=true
else
    echo "[WARN] Docker is not installed or not in PATH"
    DOCKER_AVAILABLE=false
fi

# Verify MongoDB connection details
echo "Verifying MongoDB configuration..."
if [ -z "$MONGODB_URI" ]; then
    echo "[WARN] MONGODB_URI not set, defaulting to mongodb://admin:changeme@127.0.0.1:27017/freetime?authSource=admin"
    MONGODB_URI="mongodb://admin:changeme@127.0.0.1:27017/freetime?authSource=admin"
fi
echo "[OK] MongoDB URI: $MONGODB_URI"

# Get the absolute path of this script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check for HTTPS certificates (Let's Encrypt)
echo "Verifying HTTPS certificate configuration..."
SSL_CERT_PATH="${SSL_CERT:-/etc/letsencrypt/live/example.com/fullchain.pem}"
SSL_KEY_PATH="${SSL_KEY:-/etc/letsencrypt/live/example.com/privkey.pem}"
LOCAL_SSL_CERT_PATH="$SCRIPT_DIR/certs/fullchain.pem"
LOCAL_SSL_KEY_PATH="$SCRIPT_DIR/certs/privkey.pem"

# Try Let's Encrypt certificates first
if [ -f "$SSL_CERT_PATH" ] && [ -f "$SSL_KEY_PATH" ]; then
    echo "[OK] Let's Encrypt certificates found - running in HTTPS/WSS mode"
    export SSL_CERT="$SSL_CERT_PATH"
    export SSL_KEY="$SSL_KEY_PATH"
# Try local self-signed certificates second
elif [ -f "$LOCAL_SSL_CERT_PATH" ] && [ -f "$LOCAL_SSL_KEY_PATH" ]; then
    echo "[OK] Local self-signed certificates found - running in HTTPS/WSS mode"
    export SSL_CERT="$LOCAL_SSL_CERT_PATH"
    export SSL_KEY="$LOCAL_SSL_KEY_PATH"
# Default to paths anyway (service will handle if missing)
else
    echo "[WARN] HTTPS certificates not found at:"
    echo "   - Let's Encrypt: $SSL_CERT_PATH"
    echo "   - Local fallback: $LOCAL_SSL_CERT_PATH"
    echo "[WARN] Setting defaults anyway - services will attempt to use them"
    export SSL_CERT="$LOCAL_SSL_CERT_PATH"
    export SSL_KEY="$LOCAL_SSL_KEY_PATH"
fi

# Parse MongoDB connection details for validation
# More robust extraction that handles both auth and non-auth URIs
# Examples: mongodb://127.0.0.1:27017/db or mongodb://user:pass@host:27017/db
# Remove protocol and auth part first
MONGODB_CONN=$(echo "$MONGODB_URI" | sed -E 's|mongodb://([^@/]*@)?||')
# Extract host (everything up to first colon or slash, whichever comes first)
MONGODB_HOST=$(echo "$MONGODB_CONN" | sed -E 's|([^/:]+).*|\1|')
[ -z "$MONGODB_HOST" ] && MONGODB_HOST="127.0.0.1"
# Extract port (digits after the colon, if present)
MONGODB_PORT=$(echo "$MONGODB_CONN" | sed -E 's|^[^/:]+:([0-9]+).*|\1|')
if [ -z "$MONGODB_PORT" ] || [ "$MONGODB_PORT" = "$MONGODB_CONN" ]; then
    MONGODB_PORT="27017"
fi

export MONGODB_URI
echo ""

# ============================================================================
# MONGODB SERVICE MANAGEMENT (via systemd)
# ============================================================================

echo "================================"
echo "Starting MongoDB Service"
echo "================================"
echo ""

# Start MongoDB via systemd
echo "Starting MongoDB (systemd service)..."
sudo systemctl start mongod
sleep 2

# Verify MongoDB is running
if sudo systemctl is-active --quiet mongod; then
    echo "[OK] MongoDB service started successfully"
else
    echo "[ERROR] Failed to start MongoDB service"
    echo "[ERROR] Check systemd status with: sudo systemctl status mongod"
    echo "[ERROR] Check logs with: sudo journalctl -u mongod -n 50"
    exit 1
fi

echo ""

# ============================================================================
# MONGODB CONNECTIVITY VERIFICATION
# ============================================================================

echo "Validating MongoDB connectivity (attempting to connect to $MONGODB_HOST:$MONGODB_PORT)..."
if timeout 5 bash -c "cat < /dev/null > /dev/tcp/$MONGODB_HOST/$MONGODB_PORT" 2>/dev/null; then
    echo "[OK] MongoDB is reachable at $MONGODB_HOST:$MONGODB_PORT"
else
    echo "[WARN] Cannot connect to MongoDB at $MONGODB_HOST:$MONGODB_PORT"
    echo "[WARN] Services will retry MongoDB connection automatically on startup"
fi
echo ""

# ============================================================================
# CLEANUP OLD PROCESSES
# ============================================================================

echo "Cleaning up old processes..."
echo ""

# ============================================================================
# CLEANUP OLD PROCESSES (AGGRESSIVE)
# ============================================================================

echo "Cleaning up old processes..."
echo ""

# Kill any remaining Node.js processes
(sudo pkill -9 -f "node api/master-server-api" && sudo pkill -9 -f "node admin-panel/admin-panel-server" && sudo pkill -9 -f "node websocket/securechat-websocket-server" && sudo pkill -9 -f "node peer-network/peer-master-server") >/dev/null 2>&1 || true

# Kill ANY Node process listening on our target ports (aggressive fallback)
echo "Killing processes on target ports (443, 3001, 8080, 9080)..."
for port in $API_PORT $ADMIN_PORT $WEBSOCKET_PORT $PEER_PORT; do
    # Get all PIDs listening on this port
    pids=$(sudo lsof -ti:$port 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "  Port $port: killing PIDs $pids"
        echo "$pids" | xargs -r sudo kill -9 2>/dev/null || true
    fi
done

# Wait for ports to be released
echo "Waiting for ports to be released..."
for port in $API_PORT $ADMIN_PORT $WEBSOCKET_PORT $PEER_PORT; do
    attempts=0
    max_attempts=10
    while [ $attempts -lt $max_attempts ]; do
        if ! lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo "  ✓ Port $port is free"
            break
        fi
        echo "  ⏳ Port $port still in use, waiting... (attempt $((attempts+1))/$max_attempts)"
        ((attempts++))
        sleep 1
    done
    
    if [ $attempts -ge $max_attempts ]; then
        echo "  ⚠️  Port $port still in use after 10 attempts - trying harder kill"
        # Get PIDs again and kill more aggressively
        pids=$(sudo lsof -ti:$port 2>/dev/null)
        if [ -n "$pids" ]; then
            echo "  Force killing PIDs: $pids"
            echo "$pids" | xargs -r sudo kill -9 2>/dev/null || true
            sleep 2
        fi
    fi
done

# Give system time to fully clean up
sleep 2

echo "[OK] Cleanup complete"
echo ""

# ============================================================================
# VERIFY PORTS AVAILABLE
# ============================================================================

echo "Verifying ports are available..."
echo ""

for port in $API_PORT $ADMIN_PORT $WEBSOCKET_PORT $PEER_PORT; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "[ERROR] Port $port is still in use"
        exit 1
    fi
done
echo "[OK] All ports available"
echo ""

# ============================================================================
# PREPARE LOG FILES
# ============================================================================

mkdir -p logs
rm -f logs/api-service.log logs/admin-panel.log logs/websocket.log logs/peer-network.log
touch logs/api-service.log logs/admin-panel.log logs/websocket.log logs/peer-network.log

echo "[OK] Log files prepared"
echo ""

# ============================================================================
# START SERVICES
# ============================================================================

echo "================================"
echo "Starting Services"
echo "================================"
echo ""

# API Server (Port 443) - With Built-in Socket.IO Support
echo "Starting API Server with HTTPS + Socket.IO Support (port $API_PORT)..."
echo "  • Provides: REST API + Socket.IO (HTTPS/WSS primary, HTTP/Polling fallback)"
echo "  • HTTPS: Uses Let's Encrypt certificate (or falls back to HTTP)"
echo "  • Socket.IO: Polling-first strategy for maximum compatibility"
echo "  • Reliability: Automatic fallback if HTTPS unavailable"
sudo PORT_API=$API_PORT NODE_ENV=production MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=$MONGO_TIMEOUT MONGO_POOL_SIZE=$MONGO_POOL_SIZE node api/master-server-api.js >> logs/api-service.log 2>&1 &
API_PID=$!
echo $API_PID > .api-pid
echo "[OK] API Server started (PID: $API_PID)"
sleep 3

# Admin Panel (Port 3001) - Accessible locally and on LAN (HTTP only for local admin use)
echo "Starting Admin Panel (port $ADMIN_PORT)..."
echo "  • Provides: Admin dashboard for user/server management"
echo "  • Access: Restricted to localhost and LAN only"
echo "  • Protocol: HTTP (not HTTPS, as this is for local admin use)"
PORT_ADMIN_PANEL=$ADMIN_PORT BIND_IP="0.0.0.0" MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=$MONGO_TIMEOUT MONGO_POOL_SIZE=$MONGO_POOL_SIZE node admin-panel/admin-panel-server.js >> logs/admin-panel.log 2>&1 &
ADMIN_PID=$!
echo $ADMIN_PID > .admin-pid
echo "[OK] Admin Panel started (PID: $ADMIN_PID)"
sleep 2

# Setup firewall rules to restrict admin panel to LAN only
echo "Configuring firewall rules for Admin Panel..."
if command -v ufw &> /dev/null; then
    # UFW firewall
    sudo ufw allow from 127.0.0.1 to any port $ADMIN_PORT comment 'Admin Panel - Localhost' 2>/dev/null || true
    sudo ufw allow from 192.168.0.0/16 to any port $ADMIN_PORT comment 'Admin Panel - LAN (192.168.x.x)' 2>/dev/null || true
    sudo ufw allow from 10.0.0.0/8 to any port $ADMIN_PORT comment 'Admin Panel - LAN (10.x.x.x)' 2>/dev/null || true
    sudo ufw allow from 172.16.0.0/12 to any port $ADMIN_PORT comment 'Admin Panel - LAN (172.16-31.x.x)' 2>/dev/null || true
    echo "[OK] UFW firewall rules applied"
elif command -v iptables &> /dev/null; then
    # iptables firewall
    sudo iptables -A INPUT -p tcp --dport $ADMIN_PORT -s 127.0.0.1 -j ACCEPT 2>/dev/null || true
    sudo iptables -A INPUT -p tcp --dport $ADMIN_PORT -s 192.168.0.0/16 -j ACCEPT 2>/dev/null || true
    sudo iptables -A INPUT -p tcp --dport $ADMIN_PORT -s 10.0.0.0/8 -j ACCEPT 2>/dev/null || true
    sudo iptables -A INPUT -p tcp --dport $ADMIN_PORT -s 172.16.0.0/12 -j ACCEPT 2>/dev/null || true
    sudo iptables -A INPUT -p tcp --dport $ADMIN_PORT -j DROP 2>/dev/null || true
    echo "[OK] iptables firewall rules applied"
else
    echo "[WARN] No firewall detected - configure manually to restrict port $ADMIN_PORT to LAN only"
fi
sleep 2

# WebSocket Server (Port 8080) - Real-time Socket.IO communication
echo "Starting WebSocket Server (port $WEBSOCKET_PORT)..."
echo "  • Provides: Real-time bidirectional communication via Socket.IO"
echo "  • Protocol: HTTP with WebSocket upgrade support"
echo "  • Features: Message delivery, typing indicators, presence updates"
PORT_WEBSOCKET=$WEBSOCKET_PORT NODE_ENV=production MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=$MONGO_TIMEOUT MONGO_POOL_SIZE=$MONGO_POOL_SIZE node websocket/securechat-websocket-server.js >> logs/websocket.log 2>&1 &
WEBSOCKET_PID=$!
echo $WEBSOCKET_PID > .websocket-pid
echo "[OK] WebSocket Server started (PID: $WEBSOCKET_PID)"
sleep 2

# Note: WebSocket server on port 8080 handles real-time Socket.IO communication
# The API Server on port 443 provides REST endpoints and also includes Socket.IO backup
echo ""

# Peer Network (Port 9080)
if [ "$SKIP_PEER" = "0" ]; then
    echo "Starting Peer Network (port $PEER_PORT)..."
    nohup env PORT_PEER="$PEER_PORT" NODE_ENV="production" MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT="$MONGO_TIMEOUT" MONGO_POOL_SIZE="$MONGO_POOL_SIZE" SSL_CERT="$SSL_CERT" SSL_KEY="$SSL_KEY" node peer-network/peer-master-server.js >> logs/peer-network.log 2>&1 &
    PEER_PID=$!
    echo $PEER_PID > .peer-pid
    echo "[OK] Peer Network started (PID: $PEER_PID)"
    sleep 2
else
    echo "[SKIP] Peer Network disabled (set SKIP_PEER=0 to enable)"
    PEER_PID=0
fi

echo ""

# ============================================================================
# HEALTH CHECK
# ============================================================================

echo "Checking service status..."
echo ""

check_health() {
    local port=$1
    local name=$2
    local scheme=$3
    local attempt=0
    local max_attempts=15

    # Default scheme is http if not provided
    if [ -z "$scheme" ]; then
        scheme="http"
    fi

    while [ $attempt -lt $max_attempts ]; do
        if [ "$scheme" = "https" ]; then
            response=$(timeout 2 curl -k -s -w "\n%{http_code}" "https://localhost:$port/health" 2>/dev/null)
            if [ $? -eq 0 ]; then
                status_code=$(echo "$response" | tail -n1)
                body=$(echo "$response" | head -n-1)
                if [ "$status_code" = "200" ]; then
                    echo "✓ $name: Running ($scheme, port $port) - Status $status_code"
                    return 0
                fi
            fi
        else
            response=$(timeout 2 curl -s -w "\n%{http_code}" "http://localhost:$port/health" 2>/dev/null)
            if [ $? -eq 0 ]; then
                status_code=$(echo "$response" | tail -n1)
                body=$(echo "$response" | head -n-1)
                if [ "$status_code" = "200" ]; then
                    echo "✓ $name: Running ($scheme, port $port) - Status $status_code"
                    return 0
                fi
            fi
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    echo "✗ $name: FAILED ($scheme, port $port) - No response after ${max_attempts}s"
    echo "  → Check logs/$(echo $name | tr '[:upper:]' '[:lower:]' | sed 's/ /-/g')-*.log for errors"
    return 1
}

verify_process() {
    local pid=$1
    local name=$2
    if kill -0 $pid 2>/dev/null; then
        return 0
    else
        echo "✗ $name (PID $pid) is NOT running"
        return 1
    fi
}

echo ""
echo "================================"
echo "Service Health Status"
echo "================================"
echo ""

# Track if all services are healthy
ALL_HEALTHY=true

# Determine whether to probe services via HTTPS or HTTP. Use HTTPS only when
# the service is running on port 443 or when HTTPS_ENABLED is explicitly set.
API_SCHEME="http"
if [ "$API_PORT" -eq 443 ] || [ "${HTTPS_ENABLED:-}" = "true" ]; then
    API_SCHEME="https"
fi

echo "API Server Status:"
check_health $API_PORT "🌐 API Server" "$API_SCHEME" || ALL_HEALTHY=false

echo "Admin Panel Status:"
check_health $ADMIN_PORT "💬 Admin Panel" "http" || ALL_HEALTHY=false

# Peer network ALWAYS runs with SSL/TLS (WSS) enabled since it loads SSL certificates
# Always use HTTPS for health checks regardless of port
echo "Peer Network Status:"
check_health $PEER_PORT "🔗 Peer Network" "https" || ALL_HEALTHY=false

echo ""

# ============================================================================
# API ENDPOINT VERIFICATION
# ============================================================================

echo "================================"
echo "Verifying Critical API Endpoints"
echo "================================"
echo ""

# Test API endpoints
verify_endpoint() {
    local method=$1
    local endpoint=$2
    local port=$3
    local description=$4
    
    url="https://localhost:$port$endpoint"
    response=$(timeout 3 curl -k -s -w "%{http_code}" -X $method "$url" 2>/dev/null)
    status_code=$(echo "$response" | tail -c 4)
    
    if [ "$status_code" = "000" ]; then
        echo "✗ $description: NO RESPONSE (endpoint: $endpoint)"
        return 1
    elif echo "$status_code" | grep -qE "^(200|201|400|401|404)"; then
        echo "✓ $description: Responded with HTTP $status_code"
        return 0
    else
        echo "⚠ $description: HTTP $status_code"
        return 0
    fi
}

# Verify core endpoints exist and respond
echo "Checking critical API endpoints:"
verify_endpoint "GET" "/health" "$API_PORT" "Health Check" || true
verify_endpoint "GET" "/api/health" "$API_PORT" "API Health endpoint" || true
verify_endpoint "GET" "/api/socket.io/test" "$API_PORT" "Socket.IO test endpoint" || true

echo ""

# Final status summary
if [ "$ALL_HEALTHY" = true ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✓ ALL SERVICES STARTED SUCCESSFULLY"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "⚠ WARNING: Some services may not be fully ready yet"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Services are starting in the background. They may need a few moments to initialize."
    echo ""
    echo "Troubleshooting:"
    echo "1. Check MongoDB is running and accessible at: $MONGODB_HOST:$MONGODB_PORT"
    echo "2. View detailed logs:"
    echo "   - tail -f logs/api-service.log"
    echo "   - tail -f logs/admin-panel.log"
    echo "   - tail -f logs/peer-network.log"
    echo "3. Verify ports 443, 3001, 9080 are available: sudo lsof -i -P -n | grep LISTEN"
    echo "4. For HTTPS: Ensure Let's Encrypt certificates exist at:"
    echo "   - /etc/letsencrypt/live/example.com/fullchain.pem"
    echo "   - /etc/letsencrypt/live/example.com/privkey.pem"
fi

echo ""

# ============================================================================
# STARTUP SUMMARY
# ============================================================================

echo "================================"
echo "Startup Complete"
echo "================================"
echo ""
echo "════════════════════════════════════════════════════════════════"
echo "SERVICE ENDPOINTS"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "API Server:      https://example.com/health"
echo "                  (or http://localhost:$API_PORT/health for local)"
echo ""
echo "Admin Panel:     http://localhost:$ADMIN_PORT/health"
echo "                  (LAN/Localhost only - restricted access)"
echo ""
echo "Peer Network:    https://localhost:$PEER_PORT/health"
echo ""
echo "Database:        $MONGODB_URI"
echo ""
echo "════════════════════════════════════════════════════════════════"
echo "MONITORING & LOGS"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "View specific logs:"
echo "  tail -f logs/api-service.log         (API + Socket.IO)"
echo "  tail -f logs/admin-panel.log         (Admin dashboard)"
echo "  tail -f logs/peer-network.log        (Peer-to-peer)"
echo ""
echo "Monitor all logs:"
echo "  tail -f logs/*.log"
echo ""
echo "Check process status:"
echo "  ps aux | grep 'node '"
echo ""
echo "Check port usage:"
echo "  sudo lsof -i -P -n | grep LISTEN | grep node"
echo ""
echo "════════════════════════════════════════════════════════════════"
echo "SERVICE MANAGEMENT"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Stop all services:"
echo "  ./stop-all.sh"
echo ""
echo "Restart all services:"
echo "  ./stop-all.sh restart"
echo ""
echo "Restart individual services:"
echo "  ./stop-all.sh restart-api"
echo "  ./stop-all.sh restart-admin"
echo ""

# ============================================================================
# SIGNAL HANDLING
# ============================================================================

trap 'echo ""; echo "Received interrupt signal - services still running"; echo "To stop: ./stop-all.sh"; echo ""; exit 0' SIGINT

# Keep script running
wait



