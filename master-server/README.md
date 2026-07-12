# FreeTime Master-Server - Startup Guide

## ⚡ Quick Start

```bash
# Navigate to master-server directory
cd master-server

# Start all services
./start-all.sh

# In another terminal, monitor logs
tail -f logs/api-service.log
```

**That's it!** All 4 services will start automatically.

---

## 📋 What Gets Started

| Service | Port | Purpose | Access |
|---------|------|---------|--------|
| **API Server** | 80 | User auth, messaging, files | Worldwide |
| **WebSocket** | 8080 | Real-time chat & updates | Worldwide |
| **Peer Network** | 9080 | P2P peer discovery | Worldwide |
| **Admin Panel** | 3001 | Management & monitoring | LAN only |

---

## ✅ How to Verify Everything Works

### Option 1: Quick Health Check
```bash
# Test all services at once
for port in 80 3001 8080 9080; do
    echo "Port $port:"
    curl -s http://localhost:$port/health | head -c 50
    echo ""
done
```

### Option 2: Access Admin Panel
1. Open browser: `http://192.168.1.100:3001`
2. Login with: `admin` / `heHefuzzy89`
3. See all services and their status

### Option 3: Check Logs
```bash
# View any service logs
tail -f logs/api-service.log
tail -f logs/websocket.log
tail -f logs/peer-network.log
tail -f logs/admin-panel.log
```

---

## 🛑 How to Stop Services

### Graceful Shutdown
```bash
./stop-all.sh
```

### Emergency Kill (if stop-all.sh fails)
```bash
pkill -9 node
```

---

## 🔧 Setup Requirements

### Minimum Requirements
- ✅ Linux/macOS (or Windows with Git Bash/WSL)
- ✅ Node.js v14+ (`node --version`)
- ✅ npm v6+ (comes with Node.js)
- ✅ MongoDB running locally (`sudo systemctl start mongod`)
- ✅ Ports 80, 3001, 8080, 9080 available

### One-Time Setup
1. **Install MongoDB** (if not already installed)
   ```bash
   # Ubuntu/Debian
   sudo apt-get install mongodb
   sudo systemctl start mongod
   
   # macOS
   brew install mongodb-community
   brew services start mongodb-community
   ```

2. **Install Node.js** (if not already installed)
   ```bash
   # Visit https://nodejs.org/ or use a package manager
   ```

3. **Configure .env** (already done, but verify)
   ```bash
   # File: config/.env
   # Must have all these settings:
   PORT_API=80
   PORT_ADMIN_PANEL=3001
   PORT_WEBSOCKET=8080
   PORT_PEER=9080
   JWT_SECRET=a7f3b9d2e8c5f1a4b6d9e2c5f8b1e4a7d0c3f6b9e2a5c8d1e4f7a0b3c6e9f2
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=heHefuzzy89
   ```

---

## 📡 Access Points

### For Android App
The app is already configured to use:
- **API:** `http://example.com/api`
- **WebSocket:** `ws://example.com:8080/ws`
- **Peer:** `ws://example.com:9080`

### For Admin
- **Admin Panel:** `http://192.168.1.100:3001`
- **Default Login:** admin / heHefuzzy89

### For Development/Testing
- **API Health:** `http://localhost/health`
- **WebSocket Health:** `http://localhost:8080/health`
- **Peer Health:** `http://localhost:9080/health`
- **Admin Health:** `http://localhost:3001/health`

---

## 📊 What's Running

### API Server (Port 80)
- User authentication & registration
- 2FA setup with authenticator app
- Chat messaging
- File uploads/downloads
- Voice/video call signaling
- Friend system
- User profiles
- Admin endpoints

### WebSocket Server (Port 8080)
- Real-time messaging
- Online/offline status
- Typing indicators
- Connection queue management
- Supports 35,000 concurrent users

### Peer Network (Port 9080)
- P2P peer discovery
- Peer health checking
- Peer-to-peer routing

### Admin Panel (Port 3001)
- User management
- Service monitoring
- Statistics dashboard
- System health checks
- Log viewing

---

## 🐛 Troubleshooting

### "start-all.sh: Permission denied"
```bash
# Fix permissions
chmod +x start-all.sh
chmod +x stop-all.sh

# Try again
./start-all.sh
```

### "Port 80 already in use"
```bash
# Find what's using port 80
lsof -i :80

# Kill it
kill -9 PID

# Then restart
./start-all.sh
```

### "MongoDB connection failed"
```bash
# Start MongoDB
sudo systemctl start mongod

# Verify it's running
mongo --version

# or with newer MongoDB
mongosh --version
```

### Services don't respond after 30 seconds
```bash
# Check logs for actual errors
tail -f logs/api-service.log | head -50

# Check if services actually started
ps aux | grep node

# Check if ports are binding
lsof -i :80 -i :3001 -i :8080 -i :9080
```

### "curl: command not found"
```bash
# Use a different method to test
# Option 1: Use wget
wget -qO- http://localhost/health

# Option 2: Use Node.js
node -e "require('http').get('http://localhost/health', r => console.log(r.statusCode))"

# Option 3: Just check services are running
ps aux | grep "node api/master-server-api"
```

---

## 📝 Important Files

### Configuration
- **`config/.env`** - All settings (required)

### Core Services
- **`api/master-server-api.js`** - Main API (6,071 lines)
- **`websocket/securechat-websocket-server.js`** - Real-time (769 lines)
- **`peer-network/peer-master-server.js`** - P2P network (637 lines)
- **`admin-panel/admin-panel-server.js`** - Management console

### Startup Scripts
- **`start-all.sh`** - Start all services ← USE THIS
- **`stop-all.sh`** - Stop all services

### Documentation
- **`DEBUG_MASTER_SERVER.md`** - Detailed troubleshooting
- **`MASTER_SERVER_SUMMARY.md`** - Full technical details

---

## 📊 Monitoring Commands

```bash
# Watch services run in real-time
watch -n 1 'ps aux | grep node'

# See which ports are listening
watch -n 1 'lsof -i :80 -i :3001 -i :8080 -i :9080'

# Monitor system resources
htop

# See active connections to API
netstat -an | grep :80 | wc -l

# Follow all logs
tail -f logs/*.log

# Follow specific service
tail -f logs/api-service.log
```

---

## 🚀 Production Deployment

For production, consider:

1. **Use a Process Manager**
   ```bash
   npm install -g pm2
   pm2 start start-all.sh --name freetime-master
   pm2 save
   pm2 startup
   ```

2. **Enable HTTPS**
   - Use nginx reverse proxy with SSL
   - Or use Node.js with ssl certificates

3. **Database Backups**
   ```bash
   # Backup MongoDB
   mongodump --db freetime --out /backups/freetime
   ```

4. **Monitoring**
   - Keep logs rotating with logrotate
   - Set up monitoring alerts
   - Monitor TCP connections and resources

5. **Scaling**
   - API server is stateless - can run multiple instances
   - Use load balancer (nginx, HAProxy)
   - Keep WebSocket on separate instance per region

---

## 📞 Common Issues & Fixes

### Issue: "Error: EACCES: permission denied"
**Fix:** Use `sudo` or change permissions
```bash
sudo ./start-all.sh
# OR
sudo chown -R $(whoami) .
chmod +x start-all.sh
./start-all.sh
```

### Issue: "Cannot find module 'express'"
**Fix:** Install dependencies
```bash
npm install
./start-all.sh
```

### Issue: "EADDRINUSE: address already in use :::80"
**Fix:** Kill process using port
```bash
sudo lsof -i :80
sudo kill -9 PID
./start-all.sh
```

### Issue: "JWT_SECRET not set in .env file"
**Fix:** Verify .env has JWT_SECRET
```bash
grep JWT_SECRET config/.env
# Should output the secret. If not, add it:
echo "JWT_SECRET=a7f3b9d2e8c5f1a4b6d9e2c5f8b1e4a7d0c3f6b9e2a5c8d1e4f7a0b3c6e9f2" >> config/.env
```

---

## 📞 Getting Help

### Check Logs First
```bash
# Errors usually in logs
tail -f logs/api-service.log

# Search for error keyword
grep -i error logs/*.log

# See last 20 lines of all logs
tail -20 logs/*.log
```

### Run Debug Mode
```bash
# See what's happening
./start-all.sh
# Watch output for errors

# Don't let it background
# Press Ctrl+C to stop, read the errors
```

### Check System
```bash
# Is MongoDB running?
sudo systemctl status mongod

# Do we have Node.js?
node --version

# Do we have npm?
npm --version

# Are ports free?
lsof -i :80 -i :3001 -i :8080 -i :9080
```

---

## 🎯 Success Indicators

You'll know everything is working when:

1. ✅ `./start-all.sh` completes without errors
2. ✅ You can access admin panel at `http://192.168.1.100:3001`
3. ✅ All health endpoints return online status
4. ✅ Services show "Online" in admin monitoring tab
5. ✅ No error messages in logs

---

## 🔒 Security Notes

- **Port 80 requires sudo** - The script handles this
- **Default password** - Change ADMIN_PASSWORD in .env
- **JWT_SECRET** - Should be unique per deployment
- **MongoDB** - Keep isolated to localhost only
- **Firewall** - Only expose ports 80, 8080, 9080 to internet

---

## 📚 For More Information

- **Detailed guide:** `DEBUG_MASTER_SERVER.md`
- **Technical specs:** `MASTER_SERVER_SUMMARY.md`
- **API endpoints:** See comments in `api/master-server-api.js`
- **Code:** All services are well-commented for reference

---

## 🎉 You're Ready!

Run this command and you're good to go:

```bash
./start-all.sh
```

Then monitor with:

```bash
tail -f logs/api-service.log
```

If you run into any issues, check the logs first - they'll tell you exactly what's wrong!

---

**Last Updated:** January 2024  
**Status:** ✅ Production Ready
