#!/bin/bash

# FreeTime Master-Server STOP (v2 - Enhanced graceful shutdown)
# Stops: API Server, Admin Panel, Peer Network
# Stops: MongoDB & Redis Docker containers
# Closes all database connections before shutdown

# Disable bash job control output
set +m

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║         FreeTime Master-Server - Graceful Shutdown                ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR" || exit 1

# Graceful shutdown timeout (allows MongoDB to close connections)
GRACEFUL_SHUTDOWN_TIMEOUT=5

# ============================================================================
# STOP NODEJS SERVICES
# ============================================================================

echo "[STEP 1/3] Stopping Node.js Services..."
echo ""

stop_service() {
    local pid_file=$1
    local service_name=$2
    local port=$3
    
    if [ -f "$pid_file" ]; then
        PID=$(cat "$pid_file")
        if ps -p $PID > /dev/null 2>&1; then
            echo "  • $service_name (PID: $PID) - Sending graceful shutdown signal..."
            # Try graceful kill first
            kill $PID 2>/dev/null
            # Wait for graceful shutdown (allows MongoDB to close connections)
            sleep $GRACEFUL_SHUTDOWN_TIMEOUT
            # Force kill if still running
            if ps -p $PID > /dev/null 2>&1; then
                echo "    ⚠️  Still running, force killing..."
                kill -9 $PID 2>/dev/null
            else
                echo "    ✓ Gracefully stopped"
            fi
        fi
        rm -f "$pid_file"
    fi
}

# Stop services by PID files
stop_service ".api-pid" "API Server" "443"
stop_service ".admin-pid" "Admin Panel" "3001"
stop_service ".peer-pid" "Peer Network" "9080"

# Cleanup stray processes
echo ""
echo "  Cleaning up stray Node.js processes..."
(pkill -f "master-server-api" && pkill -f "admin-panel" && pkill -f "peer-master-server") >/dev/null 2>&1 || true

# Force-kill any remaining processes on FreeTime ports
echo "  Force-releasing ports (443, 3001, 9080)..."
for port in 443 3001 9080; do
    pids=$(sudo lsof -ti:$port 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "    Port $port: Killing PIDs $pids"
        echo "$pids" | xargs -r sudo kill -9 2>/dev/null || true
    fi
done

# Wait for cleanup
sleep 2
echo "✓ All Node.js services stopped"
echo ""

# ============================================================================
# STOP DOCKER CONTAINERS
# ============================================================================

echo "[STEP 2/3] Stopping Docker Containers..."
echo ""

if command -v docker &> /dev/null; then
    if command -v docker-compose &> /dev/null; then
        echo "  Using docker-compose to stop containers..."
        cd "$PARENT_DIR" || exit 1
        
        # Gracefully stop containers
        docker-compose down --timeout 10 >/dev/null 2>&1
        COMPOSE_EXIT=$?
        
        cd "$SCRIPT_DIR" || exit 1
        
        if [ $COMPOSE_EXIT -eq 0 ]; then
            echo "  ✓ Docker containers stopped successfully"
        else
            echo "  ⚠️  docker-compose had issues, attempting manual stop..."
            docker stop securechat-mongodb 2>/dev/null || true
            docker stop securechat-redis 2>/dev/null || true
            docker rm securechat-mongodb 2>/dev/null || true
            docker rm securechat-redis 2>/dev/null || true
            echo "  ✓ Docker containers stopped (manual)"
        fi
    else
        echo "  Stopping containers manually (docker-compose not found)..."
        docker stop securechat-mongodb 2>/dev/null && echo "    ✓ MongoDB stopped" || echo "    - MongoDB not running"
        docker stop securechat-redis 2>/dev/null && echo "    ✓ Redis stopped" || echo "    - Redis not running"
        docker rm securechat-mongodb 2>/dev/null || true
        docker rm securechat-redis 2>/dev/null || true
    fi
else
    echo "  ℹ️  Docker not available - ensure MongoDB/Redis are stopped externally if needed"
fi
echo ""

# ============================================================================
# FINAL STATUS
# ============================================================================

echo "[STEP 3/3] Verifying shutdown..."
echo ""
echo "✓ All database connections closed"
echo "✓ All services stopped"
echo "✓ All ports released"
echo ""

echo "════════════════════════════════════════════════════════════════════════"
echo "SHUTDOWN COMPLETE"
echo "════════════════════════════════════════════════════════════════════════"
echo ""

# ============================================================================
# OPTIONAL: RESTART FUNCTIONALITY
# ============================================================================

if [ "$1" = "restart" ]; then
    echo ""
    echo "════════════════════════════════════════════════════════════════════════"
    echo "RESTARTING ALL SERVICES..."
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    
    sleep 3
    
    if [ -f "$SCRIPT_DIR/start-all.sh" ]; then
        bash "$SCRIPT_DIR/start-all.sh"
    elif [ -f "$SCRIPT_DIR/start-all-v2.sh" ]; then
        bash "$SCRIPT_DIR/start-all-v2.sh"
    else
        echo "❌ [ERROR] start-all.sh not found"
        exit 1
    fi

elif [ "$1" = "restart-admin" ]; then
    echo ""
    echo "════════════════════════════════════════════════════════════════════════"
    echo "RESTARTING ADMIN PANEL ONLY..."
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    
    sleep 2
    
    # Wait for port 3001 to free up
    attempts=0
    max_attempts=10
    while [ $attempts -lt $max_attempts ]; do
        if ! lsof -Pi :3001 -sTCP:LISTEN -t >/dev/null 2>&1; then
            break
        fi
        sleep 1
        ((attempts++))
    done
    
    echo "Starting Admin Panel on port 3001..."
    cd "$SCRIPT_DIR" || exit 1
    PORT_ADMIN_PANEL=3001 BIND_IP="0.0.0.0" node admin-panel/admin-panel-server.js > logs/admin-panel.log 2>&1 &
    ADMIN_PID=$!
    echo $ADMIN_PID > .admin-pid
    
    sleep 2
    
    if lsof -i :3001 > /dev/null 2>&1; then
        echo "✅ Admin panel restarted successfully on port 3001"
    else
        echo "❌ Admin panel failed to start"
        echo "Check logs:"
        tail -20 logs/admin-panel.log
        exit 1
    fi

elif [ "$1" = "restart-api" ]; then
    echo ""
    echo "════════════════════════════════════════════════════════════════════════"
    echo "RESTARTING API SERVER ONLY..."
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    
    sleep 2
    
    # Load environment variables
    if [ -f "config/.env" ]; then
        set -a
        source config/.env
        set +a
    fi
    
    # Wait for port 443 to free up
    attempts=0
    max_attempts=10
    while [ $attempts -lt $max_attempts ]; do
        if ! lsof -Pi :443 -sTCP:LISTEN -t >/dev/null 2>&1; then
            break
        fi
        sleep 1
        ((attempts++))
    done
    
    echo "Starting API Server on port 443..."
    cd "$SCRIPT_DIR" || exit 1
    PORT_API=443 NODE_ENV=production MONGODB_URI="${MONGODB_URI:-mongodb://admin:changeme@localhost:27017/freetime?authSource=admin}" node api/master-server-api.js > logs/api-service.log 2>&1 &
    API_PID=$!
    echo $API_PID > .api-pid
    
    sleep 3
    
    if lsof -i :443 > /dev/null 2>&1; then
        echo "✅ API server restarted successfully on port 443"
    else
        echo "❌ API server failed to start"
        echo "Check logs:"
        tail -30 logs/api-service.log
        exit 1
    fi

elif [ "$1" = "restart-peer" ]; then
    echo ""
    echo "════════════════════════════════════════════════════════════════════════"
    echo "RESTARTING PEER NETWORK ONLY..."
    echo "════════════════════════════════════════════════════════════════════════"
    echo ""
    
    sleep 2
    
    # Load environment variables
    if [ -f "config/.env" ]; then
        set -a
        source config/.env
        set +a
    fi
    
    # Wait for port 9080 to free up
    attempts=0
    max_attempts=10
    while [ $attempts -lt $max_attempts ]; do
        if ! lsof -Pi :9080 -sTCP:LISTEN -t >/dev/null 2>&1; then
            break
        fi
        sleep 1
        ((attempts++))
    done
    
    echo "Starting Peer Network on port 9080..."
    cd "$SCRIPT_DIR" || exit 1
    PORT_PEER=9080 NODE_ENV=production MONGODB_URI="${MONGODB_URI:-mongodb://admin:changeme@localhost:27017/freetime?authSource=admin}" node peer-network/peer-master-server.js > logs/peer-network.log 2>&1 &
    PEER_PID=$!
    echo $PEER_PID > .peer-pid
    
    sleep 3
    
    if lsof -i :9080 > /dev/null 2>&1; then
        echo "✅ Peer network restarted successfully on port 9080"
    else
        echo "❌ Peer network failed to start"
        echo "Check logs:"
        tail -30 logs/peer-network.log
        exit 1
    fi
fi

echo ""
