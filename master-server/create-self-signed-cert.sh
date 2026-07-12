#!/bin/bash

################################################################################
# Self-Signed SSL Certificate Generator
# Creates a self-signed certificate for immediate HTTPS use
# No Let's Encrypt, DNS, or port 80 needed
################################################################################

set -e

DOMAIN="example.com"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="$SCRIPT_DIR/certs"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}     Self-Signed SSL Certificate Generator${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ ERROR: This script must be run as root (use: sudo ./create-self-signed-cert.sh)${NC}"
    exit 1
fi

echo -e "${BLUE}[1/3] Creating certificates directory...${NC}"

if [ ! -d "$CERTS_DIR" ]; then
    mkdir -p "$CERTS_DIR"
    echo -e "${GREEN}✅ Created $CERTS_DIR${NC}"
else
    echo -e "${GREEN}✅ Certs directory already exists${NC}"
fi

echo ""
echo -e "${BLUE}[2/3] Removing old certificate files...${NC}"

OLD_FILES=(
    "$CERTS_DIR/fullchain.pem"
    "$CERTS_DIR/privkey.pem"
    "$CERTS_DIR/cert.pem"
    "$CERTS_DIR/key.pem"
    "$CERTS_DIR/certificate.pem"
)

for file in "${OLD_FILES[@]}"; do
    if [ -f "$file" ]; then
        rm -f "$file"
        echo -e "${GREEN}✅ Removed $file${NC}"
    fi
done

echo ""
echo -e "${BLUE}[3/3] Generating self-signed certificate for $DOMAIN...${NC}"

cd "$CERTS_DIR"

openssl req -x509 -newkey rsa:4096 -keyout privkey.pem -out fullchain.pem -days 365 -nodes -subj "/CN=$DOMAIN"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Certificate generated${NC}"
else
    echo -e "${RED}❌ Failed to generate certificate${NC}"
    exit 1
fi

# Set permissions
chmod 644 privkey.pem fullchain.pem
echo -e "${GREEN}✅ Set permissions (644)${NC}"

# Verify
if [ -f "fullchain.pem" ] && [ -f "privkey.pem" ]; then
    echo -e "${GREEN}✅ Both certificate files verified${NC}"
else
    echo -e "${RED}❌ Certificate files missing${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}        ✅ Self-Signed Certificate Created!${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}Certificate Information:${NC}"
echo "  Domain:       $DOMAIN"
echo "  Type:         Self-Signed (for development/testing)"
echo "  Validity:     365 days"
echo "  Cert Path:    $CERTS_DIR/fullchain.pem"
echo "  Key Path:     $CERTS_DIR/privkey.pem"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo "  1. Start the Node.js server with HTTPS:"
echo "     cd $SCRIPT_DIR"
echo "     sudo node api/master-server-api.js"
echo ""
echo "  2. You should see:"
echo "     [OK] SSL Certificates loaded"
echo "     [OK] FreeTime Master-Server HTTPS Started"
echo "     [INFO] Listening on port 443 (Secure - HTTPS)"
echo ""
echo -e "${YELLOW}⚠️  Important${NC}"
echo "  - Self-signed certificates will trigger browser warnings"
echo "  - Android app will accept self-signed certs"
echo "  - Valid for 365 days, then regenerate with: sudo ./create-self-signed-cert.sh"
echo "  - For production, use Let's Encrypt: sudo ./certs.sh"
echo ""
