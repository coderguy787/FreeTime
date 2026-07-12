#!/bin/bash

# Disable bash job control output
set +m

# FreeTime Master-Server Setup Script
# One-time initialization: Install dependencies, create directories, validate configuration
# Architecture: Node.js directly on port 80 (no reverse proxy)

set -e

echo "FreeTime Master-Server Setup (One-time initialization)"
echo "====================================================="
echo ""
echo "Note: Implements deviceId-based session pinning to enforce one-device-per-token"
echo ""

# Function to print status messages (WITHOUT ANSI color codes)
print_status() {
    echo "[OK] $1"
}

print_warning() {
    echo "[WARN] $1"
}

print_error() {
    echo "[ERROR] $1"
}

# Never run setup as root
if [[ $EUID -eq 0 ]]; then
   print_error "This script should NOT be run as root!"
   print_error "Run as: ./setup.sh (without sudo)"
   exit 1
fi

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

print_status "Working directory: $SCRIPT_DIR"
echo ""

# ============================================
# CHECK PREREQUISITES
# ============================================
echo "Checking prerequisites..."
echo ""

# Check Node.js installation
if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed. Please install Node.js 18+ first."
    exit 1
fi
print_status "Node.js: $(node --version)"

# Check npm installation
if ! command -v npm &> /dev/null; then
    print_error "npm is not installed"
    exit 1
fi
print_status "npm: $(npm --version)"

# Validate package.json exists
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

# Create necessary directories
print_status "Creating necessary directories..."
mkdir -p logs
mkdir -p database/backups
mkdir -p logs-monitor

# Set permissions
chmod 755 logs
chmod 755 database
chmod 755 logs-monitor

# Create logs directory structure
mkdir -p logs/archive
mkdir -p logs/reports

print_status "Directory structure created"

# MongoDB - API handles database initialization automatically
if pgrep -x "mongod" > /dev/null; then
    print_status "MongoDB: Running (API will auto-initialize on first start)"
else
    print_warning "MongoDB not running - start with: sudo systemctl start mongod"
fi

# Validate configuration exists
if [ ! -f "config/.env" ]; then
    print_error "config/.env not found!"
    exit 1
fi

print_status "Configuration: config/.env found"

# ============================================
# FINAL SUMMARY
# ============================================
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

