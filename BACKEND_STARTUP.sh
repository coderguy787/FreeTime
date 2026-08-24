#!/bin/bash

set -e # Exit on error

echo "=========================================="
echo "FreeTime Backend Fix & Startup"
echo "=========================================="
echo ""

SERVER_USER="${1:-furrmin}"
SERVER_IP="${2:-192.168.1.7}"
BACKEND_PATH="/home/${SERVER_USER}/master-server/master-server"

echo "Target: ${SERVER_USER}@${SERVER_IP}"
echo "Path: ${BACKEND_PATH}"
echo ""

echo "[1/6] Deploying fixed backend code..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_BACKEND="${SCRIPT_DIR}/master-server/api/master-server-api.js"
LOCAL_ENHANCED="${SCRIPT_DIR}/master-server/api/enhanced-features-routes.js"
LOCAL_ADMIN="${SCRIPT_DIR}/master-server/api/securechat-admin-api.js"

if [ ! -f "$LOCAL_BACKEND" ]; then
    echo "[ERROR] Local backend file not found at: $LOCAL_BACKEND"
    exit 1
fi

echo "Copying from: $LOCAL_BACKEND"
scp -o StrictHostKeyChecking=no "$LOCAL_BACKEND" "${SERVER_USER}@${SERVER_IP}:${BACKEND_PATH}/api/master-server-api.js.backup" && \
scp -o StrictHostKeyChecking=no "$LOCAL_BACKEND" "${SERVER_USER}@${SERVER_IP}:${BACKEND_PATH}/api/master-server-api.js" && \
scp -o StrictHostKeyChecking=no "$LOCAL_ENHANCED" "${SERVER_USER}@${SERVER_IP}:${BACKEND_PATH}/api/enhanced-features-routes.js" 2>/dev/null || true && \
scp -o StrictHostKeyChecking=no "$LOCAL_ADMIN" "${SERVER_USER}@${SERVER_IP}:${BACKEND_PATH}/api/securechat-admin-api.js" 2>/dev/null || true

if [ $? -eq 0 ]; then
    echo " Backend deployed successfully"
else
    echo " SCP failed - check SSH connection and credentials"
    exit 1
fi
echo ""

echo "[2/6] Stopping old services and Docker containers..."

ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" << 'STOP_SCRIPT'
cd /home/furrmin/master-server/master-server

echo "Stopping Docker containers..."
if command -v docker &> /dev/null; then
    docker stop securechat-mongodb 2>/dev/null && echo "[OK] MongoDB container stopped" || true
    docker stop securechat-redis 2>/dev/null && echo "[OK] Redis container stopped" || true
    docker rm securechat-mongodb 2>/dev/null || true
    docker rm securechat-redis 2>/dev/null || true
fi

echo "Stopping Node.js services..."
pkill -f "node.*master-server-api" 2>/dev/null || true
pkill -f "node.*securechat-websocket" 2>/dev/null || true
pkill -f "node.*peer-master-server" 2>/dev/null || true
pkill -f "node.*admin-panel" 2>/dev/null || true

sleep 2

pcount=$(pgrep -f "node.*master" | wc -l)
if [ $pcount -gt 0 ]; then
    echo "[WARN] Some services still running, forcing kill..."
    pkill -9 -f "node.*master" || true
    sleep 1
fi

echo "[OK] All services stopped"
STOP_SCRIPT

echo " Old services and containers stopped"
echo ""

echo "[3/6] Starting Docker containers..."

ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" << 'DOCKER_SCRIPT'
cd /home/furrmin/master-server

if command -v docker &> /dev/null; then
    if command -v docker-compose &> /dev/null; then
        echo "Starting MongoDB and Redis via docker-compose..."
        docker-compose up -d mongodb redis

        if [ $? -eq 0 ]; then
            echo "[OK] Docker containers started"
            sleep 5 # Wait for containers to initialize
        else
            echo "[WARN] docker-compose failed, trying manual start..."
            docker run -d --name securechat-mongodb -p 27017:27017 \
                -e MONGO_INITDB_ROOT_USERNAME=admin \
                -e MONGO_INITDB_ROOT_PASSWORD=changeme \
                mongo:6.0-alpine mongod --auth || true

            docker run -d --name securechat-redis -p 6379:6379 \
                redis:7-alpine redis-server || true
            sleep 3
        fi
    else
        echo "[WARN] docker-compose not found, starting containers manually..."

        docker run -d --name securechat-mongodb -p 27017:27017 \
            -e MONGO_INITDB_ROOT_USERNAME=admin \
            -e MONGO_INITDB_ROOT_PASSWORD=changeme \
            mongo:6.0-alpine mongod --auth || true

        docker run -d --name securechat-redis -p 6379:6379 \
            redis:7-alpine redis-server || true

        sleep 3
    fi
else
    echo "[WARN] Docker not available - ensure MongoDB and Redis are running"
fi
DOCKER_SCRIPT

echo " Docker containers ready"
echo ""

echo "[4/6] Verifying backend syntax..."

ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" << 'SYNTAX_CHECK'
cd /home/furrmin/master-server/master-server

if node -c api/master-server-api.js 2>&1 | grep -q "SyntaxError"; then
    echo "[ERROR] Syntax error in master-server-api.js"
    node -c api/master-server-api.js
    exit 1
fi

echo "[OK] Backend syntax valid"
SYNTAX_CHECK

if [ $? -eq 0 ]; then
    echo " Backend syntax verified"
else
    echo " Syntax error detected - check the file"
    exit 1
fi
echo ""

echo "[5/6] Starting backend services..."

ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" << 'START_SCRIPT'
cd /home/furrmin/master-server/master-server

echo "Starting services..."

echo " - Starting API Server on port 443..."
nohup node api/master-server-api.js > logs/api-service.log 2>&1 &
API_PID=$!
echo " API PID: $API_PID"
sleep 2

echo " - Starting WebSocket Server on port 8080..."
nohup node websocket/securechat-websocket-server.js > logs/websocket.log 2>&1 &
WS_PID=$!
echo " WebSocket PID: $WS_PID"
sleep 2

echo " - Starting Peer Network on port 9080..."
nohup node peer-network/peer-master-server.js > logs/peer-network.log 2>&1 &
PEER_PID=$!
echo " Peer PID: $PEER_PID"
sleep 2

echo " - Starting Admin Panel on port 3001..."
nohup node admin-panel/admin-panel-server.js > logs/admin-panel.log 2>&1 &
ADMIN_PID=$!
echo " Admin PID: $ADMIN_PID"
sleep 3

echo ""
echo "All services started. Waiting for initialization..."
sleep 3

START_SCRIPT

echo " Services started"
echo ""

echo "[6/6] Verifying services..."

ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_IP}" << 'VERIFY_SCRIPT'
echo ""
echo "Container Status:"
echo "================="

if command -v docker &> /dev/null; then
    echo ""
    echo "Docker containers:"
    docker ps --filter "name=securechat" --format "table {{.Names}}\t{{.Status}}" || echo " (No containers found)"
else
    echo " (Docker not available)"
fi

echo ""
echo "Service Ports:"
netstat -tlnp 2>/dev/null | grep -E ":(443|8080|9080|3001)" || echo " (Checking processes...)"

echo ""
echo "Running Node services:"
ps aux | grep "node" | grep -v grep || echo " (Checking logs...)"

echo ""
echo "Recent service logs:"
echo ""
echo "API Server (last 5 lines):"
tail -5 logs/api-service.log 2>/dev/null || echo " (No log yet)"

echo ""
echo "WebSocket (last 5 lines):"
tail -5 logs/websocket.log 2>/dev/null || echo " (No log yet)"

echo ""
echo "Peer Network (last 5 lines):"
tail -5 logs/peer-network.log 2>/dev/null || echo " (No log yet)"

VERIFY_SCRIPT

echo ""
echo "=========================================="
echo "Backend Deployment Complete!"
echo "=========================================="
echo ""
echo "Services should be accessible at:"
echo " - API Server: https://192.168.1.7/ (port 443)"
echo " - WebSocket: ws://192.168.1.7:8080 (critical for real-time)"
echo " - Peer Network: 192.168.1.7:9080"
echo " - Admin Panel: http://192.168.1.7:3001"
echo ""
echo "Database:"
echo " - MongoDB: 192.168.1.7:27017 (via Docker)"
echo " - Redis: 192.168.1.7:6379 (via Docker)"
echo ""
echo "To check logs on the server:"
echo " ssh ${SERVER_USER}@${SERVER_IP}"
echo " cd /home/${SERVER_USER}/master-server/master-server/logs"
echo " tail -f api-service.log"
echo " tail -f websocket.log"
echo ""
echo "To view Docker containers:"
echo " ssh ${SERVER_USER}@${SERVER_IP}"
echo " docker ps"
echo " docker logs -f securechat-mongodb"
echo ""
echo "To stop services:"
echo " ssh ${SERVER_USER}@${SERVER_IP}"
echo " cd /home/${SERVER_USER}/master-server/master-server"
echo " ./stop-all.sh"
echo ""
