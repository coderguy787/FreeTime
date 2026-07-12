#!/bin/bash

# FreeTime Peer Server Setup Script - Debian/Ubuntu Linux
# Handles distributed messaging, calls, and user verification (80k concurrent capacity)
# Architecture: Node.js cluster with multi-core support

set -e

echo "FreeTime Peer Server Setup - Debian/Ubuntu"
echo "=========================================="
echo ""

# Utility functions
print_status() {
    echo "[OK] $1"
}

print_warning() {
    echo "[WARN] $1"
}

print_error() {
    echo "[ERROR] $1"
}

# Check if running as root for system-level setup
if [[ $EUID -eq 0 ]]; then
    print_status "Running as root - installing system dependencies..."
    
    # Update package manager
    apt-get update
    
    # Install Node.js 18+ LTS if not present
    if ! command -v node &> /dev/null; then
        print_status "Installing Node.js 18 LTS..."
        curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
        apt-get install -y nodejs
    fi
    
    # Install build tools for native modules
    apt-get install -y build-essential python3 pkg-config
    
    # Install Redis (for distributed session management)
    if ! command -v redis-server &> /dev/null; then
        print_status "Installing Redis..."
        apt-get install -y redis-server redis-tools
        systemctl enable redis-server
        systemctl start redis-server
    fi
    
    # Install supervisor for process management
    if ! command -v supervisord &> /dev/null; then
        print_status "Installing supervisord..."
        apt-get install -y supervisor
    fi
    
    print_status "System dependencies installed"
    echo "Please run setup.sh again as non-root user: ./setup.sh"
    exit 0
fi

# From here: non-root context
print_status "Running setup as non-root user"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

print_status "Working directory: $SCRIPT_DIR"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
echo ""

if ! command -v node &> /dev/null; then
    print_error "Node.js not installed. Run: sudo ./setup.sh"
    exit 1
fi
print_status "Node.js: $(node --version)"

if ! command -v npm &> /dev/null; then
    print_error "npm not installed"
    exit 1
fi
print_status "npm: $(npm --version)"

if ! command -v redis-cli &> /dev/null; then
    print_warning "Redis not available - distributed sessions may be limited"
fi

# Validate package.json
if [ ! -f "package.json" ]; then
    print_error "package.json not found"
    exit 1
fi
print_status "package.json found"

echo ""
echo "Installing npm dependencies..."
echo ""

npm install --legacy-peer-deps 2>&1

if [ $? -ne 0 ]; then
    print_warning "Some dependencies may have failed - continuing setup"
fi

print_status "Dependencies installed"

# Create directory structure
echo ""
print_status "Creating directory structure..."

mkdir -p logs logs/archive
mkdir -p tmp/uploads tmp/cache
mkdir -p config
mkdir -p lib
mkdir -p data

chmod 755 logs tmp config lib data

print_status "Directories created"

# Create default config if not exists
if [ ! -f "config/.env" ]; then
    print_status "Creating default configuration..."
    cat > config/.env << 'EOF'
# Peer Server Configuration
NODE_ENV=production
PEER_PORT=9090
PEER_NAME=peer-1
PEER_REGION=us-east

# Master Server Connection
MASTER_SERVER_URL=https://example.com
MASTER_SERVER_PORT=80
MASTER_API_KEY=your-peer-api-key-here

# Performance & Scaling
MAX_CONNECTIONS=80000
WORKER_PROCESSES=0
CONNECTION_TIMEOUT=30000
KEEP_ALIVE_INTERVAL=60000

# Redis Session Storage
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DB=1
SESSION_TTL=86400

# Security
JWT_SECRET=your-jwt-secret-here
VERIFY_TOKENS=true
ALLOW_INSECURE=false

# Logging
LOG_LEVEL=info
LOG_MAX_SIZE=10485760
LOG_MAX_FILES=10

# Monitoring
ENABLE_METRICS=true
METRICS_INTERVAL=10000
HEALTH_CHECK_INTERVAL=5000
EOF
    
    print_warning "Configuration file created at config/.env"
    print_warning "IMPORTANT: Update API keys and secrets before starting!"
else
    print_status "Configuration file exists"
fi

# Test Redis connection if available
if command -v redis-cli &> /dev/null; then
    if redis-cli ping > /dev/null 2>&1; then
        print_status "Redis connection: OK"
    else
        print_warning "Redis not responding - starting Redis..."
        sudo systemctl start redis-server
        sleep 1
        if redis-cli ping > /dev/null 2>&1; then
            print_status "Redis started successfully"
        else
            print_warning "Could not start Redis - check installation"
        fi
    fi
fi

echo ""
echo "=========================================="
echo "Setup completed successfully!"
echo "=========================================="
echo ""
echo "Peer Server Architecture:"
echo "  * Max concurrent users: 80,000"
echo "  * Default port: 9090"
echo "  * Worker processes: Auto-detect (one per core)"
echo "  * Session backend: Redis"
echo "  * Master verification: Enabled"
echo ""
echo "=========================================="
echo ""
echo "Next Steps:"
echo ""
echo "1. Update configuration:"
echo "   nano config/.env"
echo ""
echo "2. Start peer server:"
echo "   node peer-server.js"
echo ""
echo "3. In another terminal, launch control panel:"
echo "   node peer-controller.js"
echo ""
echo "4. (Optional) Setup supervisor for auto-restart:"
echo "   sudo cp supervisor/peer-server.conf /etc/supervisor/conf.d/"
echo "   sudo supervisorctl reread && reloadecho ""
echo "Peer Server is ready! Run: node peer-server.js"
echo ""
