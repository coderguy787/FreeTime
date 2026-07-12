#!/bin/bash

##############################################################################
# FreeTime Master-Server Connection Verification & Diagnostic Tool
# Tests all 4 services and their connections to the Android app
# 
# Usage: ./verify-master-server-connections.sh
##############################################################################

set -e

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "\n${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   FreeTime Master-Server Services & Connections Verification${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}\n"

# Function to check if a port is listening
check_port() {
    local port=$1
    local name=$2
    
    if netstat -tlnp 2>/dev/null | grep -q ":$port "; then
        echo -e "${GREEN}✓${NC} $name is listening on port $port"
        return 0
    else
        echo -e "${RED}✗${NC} $name is NOT listening on port $port"
        return 1
    fi
}

# Function to test HTTP endpoint
test_endpoint() {
    local url=$1
    local name=$2
    local protocol=$3
    
    # Use curl with appropriate flags
    if [ "$protocol" = "https" ]; then
        response=$(curl -s -k -w "\n%{http_code}" "$url" 2>&1 || true)
    else
        response=$(curl -s -w "\n%{http_code}" "$url" 2>&1 || true)
    fi
    
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✓${NC} $name: Health status OK (HTTP $http_code)"
        return 0
    else
        echo -e "${RED}✗${NC} $name: Health check failed (HTTP $http_code)"
        return 1
    fi
}

# Function to test Socket.IO connection
test_socket_io() {
    local url=$1
    local name=$2
    
    # Try to get Socket.IO client info
    response=$(curl -s -k "$url/socket.io/?EIO=4&transport=polling" 2>&1 || true)
    
    if echo "$response" | grep -q "EIO"; then
        echo -e "${GREEN}✓${NC} $name: Socket.IO accessible"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} $name: Socket.IO may not be accessible"
        return 1
    fi
}

# ============================================================================
# 1. SERVICE PORT VERIFICATION
# ============================================================================
echo -e "${BLUE}[1/5]${NC} Checking Service Ports..."
echo ""

PORTS_OK=0
PORTS_TOTAL=4

check_port 443 "API Server (polling)" && ((PORTS_OK++)) || true
check_port 3001 "Admin Panel" && ((PORTS_OK++)) || true
check_port 8080 "WebSocket Service" && ((PORTS_OK++)) || true
check_port 9080 "Peer Network" && ((PORTS_OK++)) || true

echo -e "\n${YELLOW}Port Status: $PORTS_OK/$PORTS_TOTAL services running${NC}\n"

# ============================================================================
# 2. SERVICE HEALTH CHECKS
# ============================================================================
echo -e "${BLUE}[2/5]${NC} Testing Service Health Endpoints..."
echo ""

HEALTH_OK=0
HEALTH_TOTAL=4

test_endpoint "https://localhost/health" "API Server (HTTPS, port 443)" "https" && ((HEALTH_OK++)) || true
test_endpoint "http://localhost:3001/health" "Admin Panel (HTTP, port 3001)" "http" && ((HEALTH_OK++)) || true
test_endpoint "https://localhost:8080/health" "WebSocket Service (HTTPS, port 8080)" "https" && ((HEALTH_OK++)) || true
test_endpoint "https://localhost:9080/health" "Peer Network (HTTPS, port 9080)" "https" && ((HEALTH_OK++)) || true

echo -e "\n${YELLOW}Health Status: $HEALTH_OK/$HEALTH_TOTAL services healthy${NC}\n"

# ============================================================================
# 3. SOCKET.IO CONNECTION TESTS
# ============================================================================
echo -e "${BLUE}[3/5]${NC} Testing Socket.IO Accessibility..."
echo ""

SOCKET_OK=0
SOCKET_TOTAL=2

test_socket_io "https://localhost" "API Server Socket.IO (Port 443)" && ((SOCKET_OK++)) || true
test_socket_io "https://localhost:8080" "WebSocket Service Socket.IO (Port 8080)" && ((SOCKET_OK++)) || true

echo -e "\n${YELLOW}Socket.IO Status: $SOCKET_OK/$SOCKET_TOTAL services accessible${NC}\n"

# ============================================================================
# 4. DATABASE & CACHE CONNECTIVITY
# ============================================================================
echo -e "${BLUE}[4/5]${NC} Testing MongoDB & Redis Connectivity..."
echo ""

# MongoDB
if command -v mongosh &> /dev/null; then
    if mongosh "mongodb://admin:changeme@localhost:27017/freetime?authSource=admin" --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} MongoDB: Connected successfully"
        DB_OK=1
    else
        echo -e "${RED}✗${NC} MongoDB: Connection failed"
        DB_OK=0
    fi
else
    echo -e "${YELLOW}⚠${NC} MongoDB: mongosh not found (skipping test)"
    DB_OK=0
fi

# Redis
if command -v redis-cli &> /dev/null; then
    if redis-cli -p 6379 PING >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Redis: Connected successfully (PING response)"
        REDIS_OK=1
    else
        echo -e "${RED}✗${NC} Redis: Connection failed or not running"
        REDIS_OK=0
    fi
else
    echo -e "${YELLOW}⚠${NC} Redis: redis-cli not found (skipping test)"
    REDIS_OK=0
fi

echo ""

# ============================================================================
# 5. NETWORK & FIREWALL DIAGNOSTICS
# ============================================================================
echo -e "${BLUE}[5/5]${NC} Network & Firewall Diagnostics..."
echo ""

# Check if localhost resolves
if ping -c 1 localhost >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Localhost: Resolves correctly"
else
    echo -e "${RED}✗${NC} Localhost: Cannot ping localhost"
fi

# Check if domain resolves
if ping -c 1 example.com >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Domain: example.com resolves correctly"
else
    echo -e "${YELLOW}⚠${NC} Domain: example.com cannot be reached from here"
fi

# Check firewall (UFW)
if command -v ufw &> /dev/null; then
    if sudo ufw status | grep -q "active"; then
        echo -e "${GREEN}✓${NC} Firewall: UFW is active and protecting system"
        
        # Check specific port rules
        if sudo ufw status | grep -q "3001.*ALLOW.*Anywhere"; then
            echo -e "${GREEN}  ✓${NC} Admin Panel (3001): Firewall rule configured"
        else
            echo -e "${YELLOW}  ⚠${NC} Admin Panel (3001): Check firewall rules"
        fi
    else
        echo -e "${YELLOW}⚠${NC} Firewall: UFW is not active"
    fi
fi

echo ""

# ============================================================================
# SUMMARY & RECOMMENDATIONS
# ============================================================================
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                            SUMMARY${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════════${NC}\n"

TOTAL_CHECKS=$((PORTS_OK + HEALTH_OK + SOCKET_OK))
TOTAL_POSSIBLE=$((PORTS_TOTAL + HEALTH_TOTAL + SOCKET_TOTAL))

echo -e "${YELLOW}Overall Status:${NC} $TOTAL_CHECKS/$TOTAL_POSSIBLE checks passed\n"

# Recommendations based on results
if [ $HEALTH_OK -lt $HEALTH_TOTAL ]; then
    echo -e "\n${YELLOW}⚠️  ACTION REQUIRED:${NC}"
    echo -e "   Some health checks failed. Run: tail -f logs/*.log"
    echo -e "   to see service error messages."
fi

if [ $SOCKET_OK -lt $SOCKET_TOTAL ]; then
    echo -e "\n${YELLOW}⚠️  WebSocket Issues Detected:${NC}"
    echo -e "   1. Verify SSL certificates exist: ls -la certs/"
    echo -e "   2. Check WebSocket service logs: tail logs/websocket-only.log"
    echo -e "   3. Ensure CORS is configured correctly in config/.env"
fi

# Connection recommendation
echo -e "\n${GREEN}🔗 Android App Connection Strategy:${NC}"
echo -e "   PRIMARY  (Polling):  https://server:443 (HTTP long-polling)"
echo -e "   SECONDARY (WebSocket): https://server:8080 (WSS/WS low-latency)"
echo -e "   FALLBACK (REST API):  Uses polling connection as guaranteed fallback"

if [ $HEALTH_OK -eq $HEALTH_TOTAL ]; then
    echo -e "\n${GREEN}✅ All services are running and healthy!${NC}"
    echo -e "${GREEN}   Android app can connect successfully.${NC}"
elif [ $HEALTH_OK -ge 2 ]; then
    echo -e "\n${YELLOW}⚠️  Partial Service Health${NC}"
    echo -e "   ${HEALTH_OK} out of ${HEALTH_TOTAL} services are healthy."
    echo -e "   Check failing services before deployment."
else
    echo -e "\n${RED}❌ Critical Service Failures${NC}"
    echo -e "   Most services are not responding."
    echo -e "   ${RED}DO NOT DEPLOY - Fix services first${NC}"
fi

echo -e "\n${BLUE}═══════════════════════════════════════════════════════════════════${NC}\n"

# Exit with appropriate code
if [ $HEALTH_OK -eq $HEALTH_TOTAL ] && [ $PORTS_OK -eq $PORTS_TOTAL ]; then
    exit 0
else
    exit 1
fi
