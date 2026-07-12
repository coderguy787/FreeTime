# FreeTime Peer Server

Distributed peer-to-peer messaging, calling, and user verification system capable of handling 80,000 concurrent connections.

## Architecture

- **Multi-core clustering**: Automatically spans workers across CPU cores
- **Connection pooling**: Efficient WebSocket management with Socket.io
- **Redis backend**: Distributed session storage and pub/sub
- **Master synchronization**: All security checks routed through master server
- **2FA verification**: All calls require TOTP verification
- **Message persistence**: Guaranteed message delivery with Redis backup
- **Real-time monitoring**: Terminal control panel for status tracking

## System Requirements

### Debian/Ubuntu Linux
```bash
# Automatically installed by setup.sh
- Node.js 18+ LTS
- Redis Server
- Build tools for native modules
```

### Minimum Resources (1 Peer, 10k users)
- CPU: 4 cores
- RAM: 4GB
- Network: 100Mbps

### Recommended (1 Peer, 80k users)
- CPU: 16+ cores
- RAM: 32GB+
- Network: 1Gbps
- Storage: 50GB (message queue + logs)

## Installation

### 1. First-time setup (requires sudo)
```bash
cd peer
sudo bash setup.sh
```

This installs:
- Node.js 18 LTS
- Redis Server
- Build tools
- npm dependencies

### 2. Configure environment
```bash
nano config/.env
```

Key settings:
- `MASTER_API_KEY`: Unique API key for this peer (get from master admin)
- `PEER_NAME`: Logical name (e.g., peer-us-east-1)
- `PEER_REGION`: Geographic region for routing
- `JWT_SECRET`: Change to secure random value (32+ chars)

### 3. Start peer server
```bash
node peer-server.js
```

Output:
```
╔════════════════════════════════════════════════════╗
║    FreeTime Peer Server - Starting Master          ║
║    Region: us-east                                 ║
║    Max Connections: 80000                          ║
║    Workers: 16                                     ║
╚════════════════════════════════════════════════════╝

Master process started
Worker 1 forked (pid 12345)
Worker 2 forked (pid 12346)
...
```

## Usage

### Terminal Control Panel

In another terminal, launch the monitoring dashboard:

```bash
node peer-controller.js
```

**Commands:**
- `1` - View live dashboard (metrics, connections, memory)
- `2` - Export detailed JSON metrics
- `3` - Live connection counter
- `4` - Help
- `5` - Exit

**Dashboard displays:**
- Active connections / max capacity
- CPU and memory usage
- Heap utilization
- System uptime
- Alert status

### API Endpoints

#### Health check (no auth required)
```bash
curl http://localhost:9090/health
```

Response:
```json
{
  "status": "healthy",
  "peer": "peer-us-east-1",
  "activeConnections": 15234,
  "maxCapacity": 80000,
  "utilizationPercent": 19,
  "uptime": 3600.5,
  "memory": {
    "heapUsed": 256000000,
    "heapTotal": 2147483648
  }
}
```

#### Statistics endpoint
```bash
curl http://localhost:9090/api/peer/stats
```

#### Route message (from master server)
```bash
curl -X POST http://localhost:9090/api/peer/route-message \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": "user123",
    "message": {
      "id": "msg001",
      "from": "user456",
      "content": "Hello!",
      "timestamp": 1680000000
    }
  }'
```

## Connection Flow

### Client Authentication
```
1. Client connects via WebSocket
2. Sends JWT token + deviceId
3. Peer verifies with master server
4. Master confirms deviceId matches token
5. Session created in Redis
6. Connection accepted
```

### Message Routing
```
1. Client sends message to recipient
2. Peer stores in Redis for persistence
3. Routes to recipient if online
4. Notifies via WebSocket
5. Recipient confirms delivery
```

### Call Signaling
```
1. Caller initiates call (voice/video)
2. Callee receives invitation
3. Callee verifies 2FA with master
4. Call state updated to "answered"
5. Caller receives answer
6. WebRTC ICE candidates exchanged
7. Direct P2P connection established
8. Peer server only routes signaling
```

## Capacity & Performance

### Per-peer capacity
- **80,000** concurrent connections
- **~52,000-65,000** messages/second
- **~8,000-10,000** concurrent calls
- **4-8GB** RAM usage (depending on message queue)

### Scaling horizontally
Add more peers to increase capacity:
- 2 peers = 160,000 connections
- 3 peers = 240,000 connections
- etc.

Master server load-balances new connections across healthy peers.

## Monitoring

### Logs
```bash
# Real-time logs
tail -f logs/combined.log

# Error logs only
tail -f logs/error.log

# Archived logs
ls logs/archive/
```

### Metrics reporting
Peer automatically reports every 10 seconds to master:
- Active connection count
- Memory utilization
- CPU usage
- Uptime
- Error rates

Master uses this to decide:
- Whether to route new connections here
- When to trigger alerts
- Capacity planning

## Production Deployment

### 1. Supervisor auto-restart (recommended)
```bash
sudo cp config/supervisor/peer-server.conf /etc/supervisor/conf.d/
sudo supervisorctl reread
sudo supervisorctl update
sudo supervisorctl start freetime-peer
```

Check status:
```bash
sudo supervisorctl status freetime-peer
```

### 2. Systemd service (alternative)
```bash
sudo cp config/systemd/freetime-peer.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable freetime-peer
sudo systemctl start freetime-peer
```

Check logs:
```bash
sudo journalctl -u freetime-peer -f
```

### 3. Environment variables
Always set in production:
```bash
NODE_ENV=production
ALLOW_INSECURE=false
LOG_LEVEL=warn
```

### 4. Security
- Keep `config/.env` secure (600 permissions)
- Rotate `JWT_SECRET` regularly
- Use firewall to restrict peer port to master server only
- Enable Redis password authentication
- Run as non-root user

## Troubleshooting

### High memory usage
```bash
# Check heap dump
node --expose-gc peer-server.js

# Restart peer to free memory
sudo supervisorctl restart freetime-peer
```

### Connection refused errors
1. Check Redis is running: `redis-cli ping`
2. Verify firewall allows peer port
3. Check master server is accessible

### Slow message delivery
1. Check network latency to master
2. Monitor Redis response times
3. Check disk I/O for message persistence

### Worker crashes
Workers are auto-respawned by supervisor/systemd. Check logs:
```bash
grep "Worker.*died" logs/error.log
```

## Development

### Install dev dependencies
```bash
npm install --save-dev nodemon jest supertest
```

### Run with hot-reload
```bash
npm run start:dev
```

### Run tests
```bash
npm test
```

### Format code
```bash
npm run lint
```

## Contributing

- Follow Node.js best practices
- Use async/await (no callbacks)
- Log important events
- Test at 80k+ connection scale
- Document new handlers

## License

MIT - FreeTime Project
