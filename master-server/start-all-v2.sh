#!/bin/bash

# FreeTime Master-Server Service Starter (v2 - Updated)
# Starts all required services with proper HTTPS support and error handling
# - MongoDB (via Docker container on port 27017)
# - Redis (via Docker container on port 6379)
# - API Server with Socket.IO (HTTPS Port 443)
# - Admin Panel (Port 3001)
# - Peer Network (Port 9080)

# Disable bash job control output
set +m

# Service ports
API_PORT=443
ADMIN_PORT=3001
PEER_PORT=9080
MONGODB_PORT=27017
REDIS_PORT=6379

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR" || exit 1

# ============================================================================
# STARTUP BANNER
# ============================================================================

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║         FreeTime Master-Server v2 - Enhanced Startup               ║"
echo "║     With HTTPS/Let's Encrypt Support & Improved Error Handling    ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

# ============================================================================
# PRE-FLIGHT CHECKS
# ============================================================================

echo "[STEP 1/7] Checking prerequisites..."
echo ""

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "❌ [ERROR] Node.js is not installed"
    exit 1
fi
NODE_VERSION=$(node --version)
echo "✓ Node.js: $NODE_VERSION"

# Check npm modules
if [ ! -d "node_modules" ] || [ ! -d "node_modules/express" ]; then
    echo "⏳ Dependencies missing, installing..."
    npm install --production >/dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo "❌ [ERROR] Failed to install dependencies"
        exit 1
    fi
fi
echo "✓ npm dependencies available"

# Check config file
if [ ! -f "config/.env" ]; then
    echo "❌ [ERROR] config/.env not found"
    echo "   Please create config/.env with MongoDB and JWT configuration"
    exit 1
fi
echo "✓ Configuration file found"

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

echo "✓ Environment variables loaded"

# Verify critical environment variables
if [ -z "$JWT_SECRET" ]; then
    echo "❌ [ERROR] JWT_SECRET not set in config/.env"
    echo "   Add: JWT_SECRET=your-secret-key-here"
    exit 1
fi
echo "✓ JWT_SECRET is configured"

if [ -z "$MONGODB_URI" ]; then
    echo "⚠️  MONGODB_URI not set, using default: mongodb://admin:changeme@localhost:27017/freetime?authSource=admin"
    MONGODB_URI="mongodb://admin:changeme@localhost:27017/freetime?authSource=admin"
fi
echo "✓ MongoDB URI: $MONGODB_URI"

# Check for HTTPS certificates (Let's Encrypt)
echo ""
echo "[STEP 2/7] Checking HTTPS certificate configuration..."
SSL_CERT_PATH="${SSL_CERT:-/etc/letsencrypt/live/example.com/fullchain.pem}"
SSL_KEY_PATH="${SSL_KEY:-/etc/letsencrypt/live/example.com/privkey.pem}"
LOCAL_SSL_CERT_PATH="./certs/fullchain.pem"
LOCAL_SSL_KEY_PATH="./certs/privkey.pem"
if [ -f "$SSL_CERT_PATH" ] && [ -f "$SSL_KEY_PATH" ]; then
    echo "✓ Let's Encrypt certificates found - running in HTTPS mode"
    export SSL_CERT="$SSL_CERT_PATH"
    export SSL_KEY="$SSL_KEY_PATH"
    HTTPS_ENABLED=true
elif [ -f "$LOCAL_SSL_CERT_PATH" ] && [ -f "$LOCAL_SSL_KEY_PATH" ]; then
    echo "✓ Local self-signed certificates found - running in HTTPS mode"
    export SSL_CERT="$LOCAL_SSL_CERT_PATH"
    export SSL_KEY="$LOCAL_SSL_KEY_PATH"
    HTTPS_ENABLED=true
else
    echo "⚠️  HTTPS certificates not found - HTTP fallback will be used"
    echo "   Expected paths:"
    echo "   - $SSL_CERT_PATH"
    echo "   - $SSL_KEY_PATH"
    echo "   - $LOCAL_SSL_CERT_PATH"
    echo "   - $LOCAL_SSL_KEY_PATH"
    HTTPS_ENABLED=false
fi
echo ""

# Check Docker installation
echo "[STEP 3/7] Checking Docker installation..."
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    echo "✓ Docker: $DOCKER_VERSION"
    DOCKER_AVAILABLE=true
else
    echo "⚠️  Docker is not installed or not in PATH"
    DOCKER_AVAILABLE=false
fi
echo ""

# Parse MongoDB connection details
MONGODB_CONN=$(echo "$MONGODB_URI" | sed -E 's|mongodb://([^@/]*@)?||')
MONGODB_HOST=$(echo "$MONGODB_CONN" | sed -E 's|([^/:]+).*|\1|')
[ -z "$MONGODB_HOST" ] && MONGODB_HOST="127.0.0.1"
MONGODB_PORT=$(echo "$MONGODB_CONN" | sed -E 's|^[^/:]+:([0-9]+).*|\1|')
if [ -z "$MONGODB_PORT" ] || [ "$MONGODB_PORT" = "$MONGODB_CONN" ]; then
    MONGODB_PORT="27017"
fi

export MONGODB_URI

# ============================================================================
# DOCKER CONTAINER MANAGEMENT
# ============================================================================

if [ "$DOCKER_AVAILABLE" = true ]; then
    echo "[STEP 4/7] Starting Docker containers (MongoDB & Redis)..."
    
    if [ -f "$PARENT_DIR/docker-compose.yml" ]; then
        cd "$PARENT_DIR" || exit 1
        docker-compose up -d mongodb redis >/dev/null 2>&1
        DOCKER_START_EXIT=$?
        cd "$SCRIPT_DIR" || exit 1
        
        if [ $DOCKER_START_EXIT -eq 0 ]; then
            echo "✓ Docker containers started successfully"
            sleep 5
        else
            echo "⚠️  Docker containers may need adjustment"
        fi
    else
        echo "⚠️  docker-compose.yml not found - skipping container management"
    fi
else
    echo "[STEP 4/7] Docker not available - ensure MongoDB/Redis are running externally"
fi
echo ""

# Validate MongoDB connectivity
echo "[STEP 5/7] Validating MongoDB connectivity..."
if timeout 5 bash -c "cat < /dev/null > /dev/tcp/$MONGODB_HOST/$MONGODB_PORT" 2>/dev/null; then
    echo "✓ MongoDB is reachable at $MONGODB_HOST:$MONGODB_PORT"
else
    echo "⚠️  Cannot connect to MongoDB at $MONGODB_HOST:$MONGODB_PORT"
    echo "   Services will retry MongoDB connection automatically"
fi
echo ""

# ============================================================================
# CLEANUP OLD PROCESSES
# ============================================================================

echo "[STEP 6/7] Cleaning up old processes..."

# Kill any remaining Node.js processes
(pkill -f "node api/master-server-api" && pkill -f "node admin-panel/admin-panel-server" && pkill -f "node peer-network/peer-master-server") >/dev/null 2>&1 || true

# Kill processes listening on target ports
for port in $API_PORT $ADMIN_PORT $PEER_PORT; do
    pids=$(sudo lsof -ti:$port 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "  Port $port: Releasing..."
        echo "$pids" | xargs -r sudo kill -9 2>/dev/null || true
    fi
done

# Wait for cleanup
sleep 2

# Verify ports are free
for port in $API_PORT $ADMIN_PORT $PEER_PORT; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "❌ [ERROR] Port $port is still in use"
        exit 1
    fi
done
echo "✓ All ports are free"
echo ""

# Prepare log files
mkdir -p logs
rm -f logs/api-service.log logs/admin-panel.log logs/peer-network.log
touch logs/api-service.log logs/admin-panel.log logs/peer-network.log
echo "✓ Log files prepared"
echo ""

# ============================================================================
# START SERVICES
# ============================================================================

echo "[STEP 7/7] Starting services..."
echo ""

# API Server (Port 443)
echo "  🌐 Starting API Server..."
PORT_API=$API_PORT NODE_ENV=production MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=${MONGO_TIMEOUT:-5000} MONGO_POOL_SIZE=${MONGO_POOL_SIZE:-10} node api/master-server-api.js >> logs/api-service.log 2>&1 &
API_PID=$!
echo $API_PID > .api-pid
echo "    - PID: $API_PID"
echo "    - Port: $API_PORT"
echo "    - Protocol: $([ "$HTTPS_ENABLED" = true ] && echo "HTTPS/WSS" || echo "HTTP/Polling")"
sleep 3

# Admin Panel (Port 3001)
echo "  💬 Starting Admin Panel..."
PORT_ADMIN_PANEL=$ADMIN_PORT BIND_IP="0.0.0.0" MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=${MONGO_TIMEOUT:-5000} MONGO_POOL_SIZE=${MONGO_POOL_SIZE:-10} node admin-panel/admin-panel-server.js >> logs/admin-panel.log 2>&1 &
ADMIN_PID=$!
echo $ADMIN_PID > .admin-pid
echo "    - PID: $ADMIN_PID"
echo "    - Port: $ADMIN_PORT"
echo "    - Access: LAN/Localhost only"
sleep 2

# Peer Network (Port 9080)
echo "  🔗 Starting Peer Network..."
PORT_PEER=$PEER_PORT NODE_ENV=production MONGODB_URI="$MONGODB_URI" MONGO_TIMEOUT=${MONGO_TIMEOUT:-5000} MONGO_POOL_SIZE=${MONGO_POOL_SIZE:-10} node peer-network/peer-master-server.js >> logs/peer-network.log 2>&1 &
PEER_PID=$!
echo $PEER_PID > .peer-pid
echo "    - PID: $PEER_PID"
echo "    - Port: $PEER_PORT"
sleep 2

echo ""

# ============================================================================
# HEALTH CHECK
# ============================================================================

echo "════════════════════════════════════════════════════════════════════════"
echo "HEALTH CHECK - Waiting for services to initialize..."
echo "════════════════════════════════════════════════════════════════════════"
echo ""

check_health() {
    local port=$1
    local name=$2
    local scheme=$3
    local attempt=0
    local max_attempts=20

    [ -z "$scheme" ] && scheme="http"

    while [ $attempt -lt $max_attempts ]; do
        if [ "$scheme" = "https" ]; then
            response=$(timeout 3 curl -k -s -w "\n%{http_code}" "https://localhost:$port/health" 2>/dev/null)
            curl_exit=$?
            if [ $curl_exit -eq 0 ]; then
                status_code=$(echo "$response" | tail -n1)
                if [ "$status_code" = "200" ]; then
                    echo "✓ $name: Running (HTTPS, port $port)"
                    return 0
                fi
            elif [ $curl_exit -eq 35 ] || [ $curl_exit -eq 60 ]; then
                # Try HTTP fallback for SSL issues
                response=$(timeout 3 curl -s -w "\n%{http_code}" "http://localhost:$port/health" 2>/dev/null)
                if [ $? -eq 0 ]; then
                    status_code=$(echo "$response" | tail -n1)
                    if [ "$status_code" = "200" ]; then
                        echo "✓ $name: Running (HTTP fallback, port $port)"
                        return 0
                    fi
                fi
            fi
        else
            response=$(timeout 3 curl -s -w "\n%{http_code}" "http://localhost:$port/health" 2>/dev/null)
            if [ $? -eq 0 ]; then
                status_code=$(echo "$response" | tail -n1)
                if [ "$status_code" = "200" ]; then
                    echo "✓ $name: Running (HTTP, port $port)"
                    return 0
                fi
            fi
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    echo "⚠️  $name: Not responding (port $port)"
    return 1
}

ALL_HEALTHY=true
check_health $API_PORT "API Server" "https" || ALL_HEALTHY=false
check_health $ADMIN_PORT "Admin Panel" "http" || ALL_HEALTHY=false
check_health $PEER_PORT "Peer Network" "https" || ALL_HEALTHY=false

echo ""

# ============================================================================
# FINAL STATUS
# ============================================================================

if [ "$ALL_HEALTHY" = true ]; then
    echo "════════════════════════════════════════════════════════════════════════"
    echo "✅ ALL SERVICES STARTED SUCCESSFULLY"
    echo "════════════════════════════════════════════════════════════════════════"
else
    echo "════════════════════════════════════════════════════════════════════════"
    echo "⚠️  SERVICES STARTING - Some may need more time to initialize"
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    echo "Services are starting in background. Check logs if issues occur:"
    echo "  • tail -f logs/api-service.log"
    echo "  • tail -f logs/admin-panel.log"
    echo "  • tail -f logs/peer-network.log"
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════"
echo "SERVICE ENDPOINTS"
echo "════════════════════════════════════════════════════════════════════════"
echo ""
echo "API Server:      https://example.com (or http://localhost:$API_PORT)"
echo "Admin Panel:     http://localhost:$ADMIN_PORT (LAN/Localhost only)"
echo "Peer Network:    https://localhost:$PEER_PORT"
echo "Database:        $MONGODB_URI"
echo ""
echo "════════════════════════════════════════════════════════════════════════"
echo "MONITORING"
echo "════════════════════════════════════════════════════════════════════════"
echo ""
echo "View logs:"
echo "  • tail -f logs/api-service.log          (API + Socket.IO + FCM)"
echo "  • tail -f logs/admin-panel.log          (Admin dashboard)"
echo "  • tail -f logs/peer-network.log         (Peer-to-peer)"
echo "  • tail -f logs/*.log                    (All logs)"
echo ""
echo "Check processes:"
echo "  • ps aux | grep 'node '"
echo "  • sudo lsof -i -P -n | grep LISTEN | grep node"
echo ""
echo "════════════════════════════════════════════════════════════════════════"
echo "SERVICE MANAGEMENT"
echo "════════════════════════════════════════════════════════════════════════"
echo ""
echo "Stop all services:           ./stop-all.sh"
echo "Restart all services:        ./stop-all.sh restart"
echo "Restart API only:            ./stop-all.sh restart-api"
echo "Restart Admin Panel only:    ./stop-all.sh restart-admin"
echo ""
echo "════════════════════════════════════════════════════════════════════════"
echo ""

# ============================================================================
# SIGNAL HANDLING
# ============================================================================

trap 'echo ""; echo "Services are still running in background"; echo "To stop: ./stop-all.sh"; echo ""; exit 0' SIGINT

# Keep script running
wait
