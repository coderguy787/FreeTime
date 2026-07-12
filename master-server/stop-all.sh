#!/bin/bash

# Disable bash job control output
set +m

# FreeTime Master-Server STOP (graceful shutdown)
# Stops: API Server, Admin Panel, WebSocket Server, Peer Network
# Stops: MongoDB & Redis Docker containers
# Closes all database connections before shutdown

echo "Stopping FreeTime Master-Server..."
echo ""

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SCRIPT_DIR" || exit 1

# Wait before killing to allow graceful MongoDB connection close
GRACEFUL_SHUTDOWN_TIMEOUT=5

# Function to stop service by PID file
stop_service() {
    local pid_file=$1
    local service_name=$2
    
    if [ -f "$pid_file" ]; then
        PID=$(cat "$pid_file")
        if ps -p $PID > /dev/null 2>&1; then
            # Try graceful kill first
            kill $PID 2>/dev/null
            # Wait for graceful shutdown (allows MongoDB to close connections)
            sleep $GRACEFUL_SHUTDOWN_TIMEOUT
            # Force kill if still running
            if ps -p $PID > /dev/null 2>&1; then
                echo "[WARN] $service_name did not stop gracefully, force killing..."
                kill -9 $PID 2>/dev/null
            fi
        fi
        rm -f "$pid_file"
        echo "[OK] $service_name stopped"
    fi
}

# ============================================================================
# STOP NODEJS SERVICES
# ============================================================================

echo "================================"
echo "Stopping Node.js Services"
echo "================================"
echo ""

# Stop services by PID files
stop_service ".api-pid" "API Server"
stop_service ".admin-pid" "Admin Panel"
stop_service ".ws-pid" "WebSocket Server"
stop_service ".peer-pid" "Peer Network"

# Cleanup stragglers
echo ""
echo "Cleaning up stray Node.js processes..."
(sudo pkill -9 -f "master-server-api" && sudo pkill -9 -f "admin-panel" && sudo pkill -9 -f "websocket-server" && sudo pkill -9 -f "peer-master-server") >/dev/null 2>&1 || true

# AGGRESSIVE: Kill ANY process listening on FreeTime ports
echo "Force-killing ALL processes on FreeTime ports..."
for port in 443 3001 8080 9080; do
    pids=$(sudo lsof -ti:$port 2>/dev/null)
    if [ -n "$pids" ]; then
        echo "  Killing processes on port $port: $pids"
        echo "$pids" | xargs -r sudo kill -9 2>/dev/null || true
    fi
done

# Wait for cleanup
sleep 2

echo "[OK] All Node.js services stopped"
echo ""

# ============================================================================
# STOP MONGODB SERVICE (via systemd)
# ============================================================================

echo "================================"
echo "Stopping MongoDB Service"
echo "================================"
echo ""

echo "Stopping MongoDB (systemd service)..."
sudo systemctl stop mongod
sleep 2

if ! sudo systemctl is-active --quiet mongod; then
    echo "[OK] MongoDB service stopped successfully"
else
    echo "[WARN] MongoDB may still be running, forcing shutdown..."
    sudo systemctl kill -s 9 mongod 2>/dev/null || true
    sleep 1
fi

echo "[OK] MongoDB stopped"
echo ""


# ============================================================================
# FINAL STATUS
# ============================================================================

echo "================================"
echo "Shutdown Complete"
echo "================================"
echo ""
echo "[OK] All database connections closed"
echo "[OK] All services stopped"
echo ""

# ============================================================================
# RESTART SERVICES (Optional)
# ============================================================================
# If called with 'restart' argument, automatically restart services

if [ "$1" = "restart" ]; then
    echo ""
    echo "================================"
    echo "Restarting Services..."
    echo "================================"
    echo ""
    
    sleep 3
    
    # Execute the start-all.sh script to restart everything
    if [ -f "$SCRIPT_DIR/start-all.sh" ]; then
        bash "$SCRIPT_DIR/start-all.sh"
    else
        echo "[ERROR] start-all.sh not found"
        exit 1
    fi
elif [ "$1" = "restart-admin" ]; then
    echo ""
    echo "================================"
    echo "Restarting Admin Panel Only"
    echo "================================"
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
    nohup node admin-panel/admin-panel-server.js > logs/admin-panel.log 2>&1 &
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
    echo "================================"
    echo "Restarting API Server Only"
    echo "================================"
    echo ""
    
    sleep 2
    
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
    nohup node api/master-server-api.js > logs/api-service.log 2>&1 &
    API_PID=$!
    echo $API_PID > .api-pid
    
    sleep 3
    
    if lsof -i :443 > /dev/null 2>&1; then
        echo "✅ API server restarted successfully on port 443"
    else
        echo "❌ API server failed to start"
        echo "Check logs:"
        tail -20 logs/api-service.log
        exit 1
    fi
fi



