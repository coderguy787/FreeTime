#!/bin/bash

set +m

set -e

echo "FreeTime Master-Server Setup (One-time initialization)"
echo "====================================================="
echo ""
echo "Note: Implements deviceId-based session pinning to enforce one-device-per-token"
echo ""

print_status() {
    echo "[OK] $1"
}

print_warning() {
    echo "[WARN] $1"
}

print_error() {
    echo "[ERROR] $1"
}

if [[ $EUID -eq 0 ]]; then
   print_error "This script should NOT be run as root!"
   print_error "Run as: ./setup.sh (without sudo)"
   exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

print_status "Working directory: $SCRIPT_DIR"
echo ""

echo "Checking prerequisites..."
echo ""

if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed. Please install Node.js 18+ first."
    exit 1
fi
print_status "Node.js: $(node --version)"

if ! command -v npm &> /dev/null; then
    print_error "npm is not installed"
    exit 1
fi
print_status "npm: $(npm --version)"

if [ ! -f "package.json" ]; then
    print_error "package.json not found"
    exit 1
fi
print_status "package.json found"

echo ""
echo "Installing dependencies..."
echo ""

npm install --legacy-peer-deps 2>&1

if [ $? -eq 0 ]; then
    print_status "Dependencies installed successfully"
else
    print_warning "Initial npm install had issues, trying with no-optional..."
    npm install --legacy-peer-deps --no-optional 2>&1

    if [ $? -eq 0 ]; then
        print_status "Dependencies installed with --no-optional flag"
    else
        print_error "Failed to install dependencies after retries"
        print_error "Try running: npm install --legacy-peer-deps manually"
        exit 1
    fi
fi

print_status "Creating necessary directories..."
mkdir -p logs
mkdir -p database/backups
mkdir -p logs-monitor

chmod 755 logs
chmod 755 database
chmod 755 logs-monitor

mkdir -p logs/archive
mkdir -p logs/reports

print_status "Directory structure created"

if pgrep -x "mongod" > /dev/null; then
    print_status "MongoDB: Running (API will auto-initialize on first start)"
else
    print_warning "MongoDB not running - start with: sudo systemctl start mongod"
fi

if [ ! -f "config/.env" ]; then
    print_error "config/.env not found!"
    exit 1
fi

print_status "Configuration: config/.env found"

echo ""
echo "Setup completed successfully!"
echo ""
echo "=========================================="
echo "FreeTime Master-Server - Ready to Start"
echo "=========================================="
echo ""
echo "Architecture: Node.js directly on port 80"
echo "(No nginx reverse proxy needed)"
echo ""
echo "Services to be started:"
echo "  * API Server:      Port 80   (requires sudo)"
echo "  * Admin Panel:     Port 3001 (LAN-only)"
echo "  * WebSocket:       Port 8080 (internal)"
echo "  * Peer Network:    Port 9090 (internal)"
echo "  * MongoDB:         Port 27017 (should be running)"
echo ""
echo "=========================================="
echo ""
echo "Next Steps:"
echo ""
echo "1. Verify MongoDB is running:"
echo "   sudo systemctl status mongod"
echo ""
echo "2. Start services:"
echo "   sudo ./start-all.sh"
echo ""
echo "3. Verify services are running:"
echo "   curl http://localhost/api/health"
echo ""
echo "4. Check logs:"
echo "   tail -f logs/api-service.log"
echo ""
echo "5. Stop services:"
echo "   sudo ./stop-all.sh"
echo ""
print_status "Ready! Run: sudo ./start-all.sh"
echo ""

