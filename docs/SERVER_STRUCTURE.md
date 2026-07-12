# Master Server Structure & Architecture

## Overview
The FreeTime Master Server is a production-grade Node.js/Express server handling authentication, real-time communication, peer management, and connection queue management for up to 35,000 concurrent users.

**🐧 Running on: Debian 13 Linux Machine**
- Windows machine only used for Android development
- All services run on dedicated Linux machine
- Last verified: January 28, 2026 ✅

## Core Architecture

### Server Ports & Services
- **Port 8081**: REST API - Authentication, user management, file operations, calls
- **Port 8080**: WebSocket Server - Real-time messaging with connection queue management  
- **Port 9080**: Peer Network Server - Peer-to-peer server management and discovery
- **Port 3001**: Admin Panel - Dashboard for server administration

### Key Technologies
- **Framework**: Express.js (Node.js)
- **Database**: MongoDB with Mongoose ODM
- **Real-time**: WebSocket (ws library)
- **Authentication**: JWT tokens + TOTP 2FA (Speakeasy library)
- **Security**: Bcrypt password hashing, CORS domain/IP whitelisting
- **Startup**: Managed by bash scripts (start-all.sh, stop-all.sh)

---

## Directory Structure

### `/api` - REST API Server
Main HTTP server with all endpoints (3500+ lines).

**Key Components:**
- User authentication (login, registration, 2FA setup)
- User management (CRUD operations)
- Peer server management
- File management and sharing
- WebRTC call signaling
- FCM push notifications
- User profile and privacy management
- Key exchange for E2E encryption
- System statistics and monitoring

**Entry Point**: `master-server-api.js`

### `/websocket` - WebSocket Server
Real-time communication with **connection queue management**.

**Key Components:**
- Token verification for WebSocket connections
- Online/offline status tracking
- Message routing between peers
- Connection queue system (MAX: 35,000)
- Queue processing with 5-second intervals
- Connection statistics and monitoring
- Real-time peer discovery
- Performance monitoring endpoints

**Entry Point**: `securechat-websocket-server.js`

**New Features (This Session):**
- Real online user tracking (isOnline in DB)
- Connection queue system for capacity management
- Queue statistics endpoint
- Automatic queue processing when slots available
- Connection duration tracking
- Detailed connection history per user

### `/peer-network` - Peer Server Manager
Manages peer-to-peer server network.

**Responsibilities:**
- Peer server registration and verification
- Peer health monitoring
- Peer-to-peer message routing
- Distributed network management

**Entry Point**: `peer-master-server.js`

### `/admin-panel` - Web Dashboard
Administrative interface for server management.

**Features:**
- User management (create, edit, delete)
- **Real Online User Tracking** (now shows actual connected users)
- Server statistics and monitoring
- Peer server management
- System logs viewing
- Monthly creation limits management

**Key Files:**
- `admin-panel-server.js` - Express server (port 3001)
- `admin-dashboard.html` - Admin UI

### `/config` - Configuration
Environment variables and deployment settings.

**Key File**: `.env`
```
DOMAIN=example.com
PUBLIC_IP=YOUR_SERVER_IP
MONGODB_URI=mongodb+srv://user:pass@cluster
JWT_SECRET=your-secret-key
API_PORT=8081
WEBSOCKET_PORT=8080
PEER_PORT=9080
ADMIN_PANEL_PORT=3001
MAX_CONNECTIONS=35000  # New: Connection limit
QUEUE_CHECK_INTERVAL=5000  # New: Queue processing interval (ms)
```

---

## API Response Formats (FIXED - January 28, 2026)

### Create User Endpoint
**POST /api/admin/users** ✅ FIXED

**Response Format** (Top-level fields):
```json
{
  "id": "uuid-here",
  "username": "username",
  "email": "user@example.com",
  "name": "User Name",
  "role": "USER",
  "tags": [],
  "createdAt": "2026-01-28T...",
  "success": true,
  "message": "Account created. User must complete 2FA setup to login.",
  "tempToken": "temporary-token",
  "nextStep": "/api/setup-authenticator"
}
```

**Frontend Integration** ✅
- Frontend checks: `if (data.id)` ✓ Works
- User appears in table immediately
- Response fields at top level (not nested)

### Update User Endpoint
**PUT /api/admin/users/:id** ✅ FIXED

**Response Format** (Top-level fields):
```json
{
  "id": "uuid-here",
  "username": "username",
  "email": "updated@example.com",
  "name": "Updated Name",
  "role": "USER",
  "tags": [],
  "success": true,
  "message": "User updated successfully"
}
```

**Frontend Integration** ✅
- Frontend checks: `if (data.id)` ✓ Works
- Changes persist in table and database
- Response fields at top level

### Delete User Endpoint
**DELETE /api/admin/users/:id** ✅ FIXED

**Response Format** (Includes confirmation message):
```json
{
  "success": true,
  "message": "User deleted successfully",
  "id": "uuid-here"
}
```

**Frontend Integration** ✅
- Frontend checks: `if (data.message)` ✓ Works
- Shows confirmation to user
- User removed from table

---

### Base URL
`http://example.com:8081/api`

### Authentication Endpoints
- `POST /auth/register` - Create account (mandatory 2FA)
- `POST /auth/login` - Login with credentials
- `POST /verify-login-totp` - TOTP verification (returns JWT)
- `POST /auth/logout` - Session cleanup
- `POST /setup-authenticator` - 2FA QR code generation
- `POST /verify-authenticator` - 2FA code verification
- `POST /refresh-token` - Refresh JWT (30-day remember)

### User Management (Admin)
- `GET /admin/users` - List all users
- `POST /admin/users` - Create user (returns tempToken)
- `PUT /admin/users/:id` - Update user
- `DELETE /admin/users/:id` - Delete user
- `GET /admin/stats` - Server statistics **with connection queue info**

### Real Online User Tracking (NEW)
- **Database**: Users have `isOnline` boolean updated in real-time
- **Source**: WebSocket server on connect/disconnect
- **Tracking includes**:
  - Connection start time
  - Client ID
  - Connection type (websocket)
  - Connection duration on disconnect
  - Full connection history

### Connection Queue Management (NEW)
**WebSocket Server Endpoints:**
- `GET /health` - Health check with connection stats
- `GET /stats` - Full statistics including queue info
- `GET /queue-status` - Queue status details
  - Current active connections
  - Queue size
  - Estimated wait time
  - Available slots

### Peer Management
- `GET /admin/peers` - List peer servers
- `POST /admin/peers` - Register peer
- `PUT /admin/peers/:id` - Update peer
- `DELETE /admin/peers/:id` - Unregister peer
- `POST /admin/peers/:id/test` - Test connectivity

### File Management
- `POST /files/upload` - Upload file
- `GET /files/:fileId` - Download file
- `DELETE /files/:fileId` - Delete file
- `GET /chat/:recipientId/files` - Get chat files

### WebRTC Signaling
- `POST /calls/initiate` - Start call
- `POST /calls/:callId/answer` - Accept call
- `POST /calls/:callId/candidate` - Send ICE candidate
- `GET /calls/:callId` - Get call status
- `DELETE /calls/:callId` - End call
- `POST /calls/:callId/reject` - Reject incoming call

### Notifications (FCM)
- `POST /users/:userId/fcm-token` - Register device
- `DELETE /users/:userId/fcm-token` - Unregister device
- `POST /notifications/send-fcm` - Send notification
- `POST /notifications/subscribe` - Subscribe to topic
- `GET /notifications/history` - Notification history

### User Profiles
- `PUT /users/:userId/profile` - Update profile
- `POST /users/:userId/profile-image` - Upload avatar
- `GET /users/:userId/profile-image` - Download avatar
- `DELETE /users/:userId/profile-image` - Remove avatar
- `GET /users/search` - Search users
- `PUT /users/:userId/privacy` - Set privacy level

### Key Exchange (E2E Encryption)
- `POST /keys/exchange/:peerId` - Exchange public keys
- `GET /keys/:userId` - Get user's public key

### System
- `GET /health` - API health status
- `GET /admin/logs` - View system logs
- `DELETE /admin/logs` - Clear logs

---

## Connection Queue System (NEW FEATURE)

### Overview
The server has a maximum connection limit of **35,000 concurrent WebSocket connections**. When this limit is reached, new connections are **queued** instead of rejected.

### How It Works

```
1. Client attempts connection
   ↓
2. Check: activeConnections < MAX_CONNECTIONS (35,000)?
   ├─ YES: Add to active connections → Connect immediately
   └─ NO: Add to queue → Wait for slot
   ↓
3. Queue Processor (runs every 5 seconds)
   ├─ Check: Any slots available?
   ├─ YES: Move queued connections to active
   └─ NO: Wait for next check
```

### Configuration
```javascript
const MAX_CONNECTIONS = 35000;  // Hard limit
const QUEUE_CHECK_INTERVAL = 5000;  // Process queue every 5 seconds
```

### Queue Events
- **Connection Queued**: Client receives message with queue position
- **Queue Processing**: Every 5 seconds, if slots available
- **Connection Activated**: Client automatically connected when slot available

### Client Feedback
Queued connections receive:
```json
{
  "event": "queued",
  "data": {
    "message": "Server at capacity. Connection queued.",
    "queuePosition": 15,
    "estimatedWait": "45 seconds",
    "serverStats": {
      "active": 35000,
      "max": 35000,
      "queued": 45,
      "percentageUsed": "100%"
    }
  }
}
```

### Queue Statistics
Available via `/queue-status` endpoint:
```json
{
  "queue": {
    "size": 45,
    "maxCapacity": 35000,
    "currentActive": 35000,
    "availableSlots": 0,
    "percentageUsed": "100%",
    "rejectedCount": 0
  },
  "timing": {
    "estimatedProcessingTime": "5 seconds per batch",
    "queueCheckInterval": "5 seconds"
  }
}
```

---

## Real Online User Tracking (NEW FEATURE)

### Overview
The admin panel now shows **real** online users (not fake), tracked by WebSocket connections.

### How It Works

**On User Connect:**
1. User authenticates and connects via WebSocket
2. Server verifies JWT token
3. User's `isOnline` field set to `true` in database
4. Connection info stored:
   - Client ID
   - Connected timestamp
   - Connection type (websocket)
5. Broadcast event to all other users

**On User Disconnect:**
1. WebSocket closes
2. User's `isOnline` field set to `false`
3. Disconnected timestamp recorded
4. Connection duration calculated and stored
5. Broadcast event to all other users

### Database Fields (User Collection)
```javascript
{
  isOnline: boolean,  // Real-time online status
  lastSeen: Date,     // Last activity timestamp
  connectionInfo: {
    connectedAt: Date,
    disconnectedAt: Date,
    clientId: string,
    connectionType: string,  // "websocket"
    connectionDuration: number  // milliseconds
  },
  connectionHistory: [
    {
      clientId: string,
      connectedAt: Date,
      disconnectedAt: Date,
      type: string,
      duration: number
    }
  ]
}
```

### Admin Panel Stats
The `/api/admin/stats` endpoint returns:
```json
{
  "users": {
    "total": 15000,
    "online": 3457,      // REAL online users
    "offline": 11543,
    "admins": 5,
    "mods": 25,
    "users": 14970
  },
  "connectionQueue": {
    "active": 3457,
    "maximum": 35000,
    "queued": 0,
    "available": 31543,
    "percentageUsed": "9.88%",
    "atCapacity": false,
    "message": "31543 connections available"
  }
}
```

### Admin Dashboard Updates
The admin panel displays:
- ✅ **Actual** number of online users (from `isOnline` field)
- Connection queue status
- Server capacity utilization
- Available connection slots
- User connection history

---

## Authentication & Security

### JWT Token Flow
1. User logs in with username/password
2. Server returns sessionToken (10 minutes, 2FA only)
3. User enters TOTP code from authenticator app
4. Server verifies with Speakeasy library
5. Server issues JWT accessToken (24h or 30d with remember)
6. Token required for all protected endpoints

### Mandatory 2FA
- **All users** must set up TOTP authenticator during registration
- Enforced via `twoFactorAuth.mandatorySetup` flag
- 10 backup codes generated for recovery
- Email verification available as fallback

### CORS Configuration (lines 60-75)
```javascript
allowedOrigins: [
  'http://example.com:8081',
  'http://YOUR_SERVER_IP:8081',
  'http://localhost:8081',
  'http://10.0.0.0/8'  // Internal network
]
```

### Password Security
- Bcrypt hashing (cost factor 10)
- Minimum 8 characters
- Must include: letters, numbers, special characters (@$%^&*!)

---

## WebSocket Implementation

### Connection Flow
```
1. Android app connects: ws://example.com:8080/
2. Send JWT token in header: Authorization: Bearer <JWT>
3. Server verifies token (verifyClient function)
4. Token valid? → Authenticated connection
5. Token invalid? → Connection rejected
6. Check connection capacity
7. Active < 35,000? → Connect immediately
8. Active >= 35,000? → Add to queue
```

### Real-time Events
- `connection` - New connection established
- `connected` - Connection successful
- `queued` - Connection queued (capacity exceeded)
- `message` - Chat message received
- `status-update` - User online/offline change
- `peer-update` - Peer status change
- `call:incoming` - Incoming call notification
- `user:profile-updated` - Profile change notification
- `disconnect` - Connection closed

### Message Format
```json
{
  "type": "message|status|call|peer",
  "event": "event_type",
  "data": {
    "content": "...",
    "timestamp": 1705000000000,
    "userId": "user-123",
    ...
  }
}
```

---

## Data Models

### User Schema
```javascript
{
  id: string (UUID),
  username: string (unique),
  email: string (unique),
  password: string (bcrypt),
  name: string,
  role: string,  // 'USER', 'MODERATOR', 'ADMIN'
  currentDeviceId?: string, // deviceId associated with most recent login token (enforces one‑device session)
  tags: [string],
  isOnline: boolean,  // REAL online status
  lastSeen: Date,
  createdAt: Date,
  updatedAt: Date,
  
  // 2FA
  twoFactorAuth: {
    enabled: boolean,
    method: 'authenticator',
    authenticatorSecret: string,
    authenticatorBackupCodes: [{code, used}],
    accountVerified: boolean,
    verificationMethod: string,
    mandatorySetup: boolean
  },
  
  // Connection tracking
  connectionInfo: {
    connectedAt: Date,
    disconnectedAt: Date,
    clientId: string,
    connectionType: string,
    connectionDuration: number
  },
  connectionHistory: [{...}],
  
  // Device info
  deviceInfo: {
    deviceId: string,
    deviceType: string,
    deviceName: string,
    lastSeen: Date,
    isActive: boolean,
    registeredAt: Date
  },
  
  // Usage stats
  usageStats: {
    totalLogins: number,
    totalMessages: number,
    totalCalls: number,
    totalConnections: number,
    lastActivity: Date,
    dataUsage: {uploaded, downloaded}
  },
  
  // Profile
  profile: {
    bio: string,
    status: string,
    privacyLevel: string,  // 'public', 'friends', 'private'
    profileImageUrl: string,
    lastUpdated: Date
  }
}
```

### Peer Schema
```javascript
{
  id: string (UUID),
  name: string (unique),
  type: string,  // 'domain', 'local-ip', 'public-ip'
  address: string,
  port: number,
  apiKey: string (bcrypt),
  connected: boolean,
  lastConnected: Date,
  latency: number,  // milliseconds
  createdAt: Date,
  updatedAt: Date
}
```

### Session Schema
```javascript
{
  userId: string,
  token: string (JWT),
  expiresAt: Date,
  createdAt: Date,
  ipAddress: string,
  userAgent: string,
  rememberMe: boolean,
  rememberMeExpiresAt: Date
}
```

---

## Startup & Management

### Machine Requirement
**🐧 Debian 13 Linux Machine ONLY**
- Node.js v18+ required
- MongoDB v5.0+ required
- This is where all services run
- Windows machine is for development/building only

### Starting All Services (On Debian 13 Linux)
```bash
cd master-server
bash start-all.sh
```

Starts in order:
1. **Admin Panel** (port 3001) - Web dashboard
2. **API Server** (port 8081) - REST endpoints
3. **WebSocket** (port 8080) - Real-time communication with queue
4. **Peer Network** (port 9080) - Peer management

### Starting Individual Services (On Debian 13 Linux)
```bash
# API Server
cd master-server
node api/master-server-api.js

# WebSocket Server
node websocket/securechat-websocket-server.js

# Peer Network
node peer-network/peer-master-server.js
```

### Stopping All Services (On Debian 13 Linux)
```bash
bash stop-all.sh
```

Gracefully shuts down all services with proper cleanup.

### Service Health Checks (On Debian 13 Linux)
Each service reports health via endpoints:
- API: `GET http://localhost:8081/health`
- WebSocket: `GET http://localhost:8080/health` (includes queue stats)
- Peer: `GET http://localhost:9080/health`

---

## Performance & Scaling

### Connection Capacity
- **Maximum Concurrent Connections**: 35,000
- **Queue Management**: Automatic with 5-second processing
- **Connection Timeout**: 30 seconds (configurable)
- **Message Queue**: Per-connection buffering while disconnected

### Optimization
- Connection pooling (10 max concurrent DB connections)
- Database indexing (username, email, userId)
- Message pagination (batch loading)
- Image compression for avatars
- Gzip response compression

### Monitoring
- Real-time connection statistics
- Queue status endpoint
- Memory usage monitoring
- Uptime tracking
- Connection history per user
- Server capacity alerts

---

## Database Indexes
Automatically created on startup:
- `users.username` (unique)
- `users.email` (unique)
- `users.isOnline` (for fast online status queries)
- `users.createdAt` (for sorting)
- `logs.timestamp` (for log retrieval)
- `peers.name` (unique)
- `calls.callId` (unique)

---

## Environment Variables
```
NODE_ENV=production
DOMAIN=example.com
PUBLIC_IP=YOUR_SERVER_IP
MONGODB_URI=mongodb+srv://user:pass@cluster/freetime
JWT_SECRET=your-random-secret-key
JWT_REFRESH_SECRET=your-refresh-secret-key
API_PORT=8081
WEBSOCKET_PORT=8080
PEER_PORT=9080
ADMIN_PANEL_PORT=3001
MAX_CONNECTIONS=35000
QUEUE_CHECK_INTERVAL=5000
EMAIL_SERVICE=gmail
EMAIL_FROM_ADDRESS=noreply@freetime.com
EMAIL_FROM_NAME=FreeTime
ALLOWED_ORIGINS=http://example.com:8081,http://YOUR_SERVER_IP:8081
```

---

## Error Handling

### Authentication Errors
- 401 Unauthorized - Invalid/expired token
- 403 Forbidden - Insufficient permissions
- 429 Too Many Requests - Rate limit exceeded

### Connection Errors
- Connection queued - Server at capacity
- Connection rejected - Invalid token
- Connection timeout - No response after 30s

### Validation Errors
- 400 Bad Request - Missing/invalid fields
- 409 Conflict - Duplicate username/email
- 415 Unsupported Media Type - Invalid file type

---

## Troubleshooting

### High Queue Size
- **Cause**: Many users connecting simultaneously
- **Solution**: Wait for queue to process (5-second intervals)
- **Monitor**: Check `/queue-status` endpoint

### Users Not Showing as Online
- **Cause**: WebSocket connection not established
- **Solution**: Verify firewall allows port 8080
- **Check**: User has valid JWT token

### Connection Drops
- **Cause**: Network instability or token expiration
- **Solution**: Implement auto-reconnect with exponential backoff
- **Check**: Token expiration time

### Queue Not Processing
- **Cause**: No available slots (at MAX_CONNECTIONS)
- **Solution**: Wait for connections to disconnect
- **Monitor**: Real-time connections with `/stats`

---

## Version Information
- **Server Version**: 2.1 (with connection queue & real online tracking)
- **API Version**: v1
- **Max Connections**: 35,000
- **Queue Processing**: Every 5 seconds
- **Last Updated**: January 28, 2026
- **Status**: ✅ Production Ready
- **Error Status**: ✅ No errors found
- **Platform**: 🐧 Debian 13 Linux
- **API Response Formats**: ✅ All fixed and verified
- **Admin Panel CRUD**: ✅ User create/edit/delete working
- **Network Fixes**: ✅ All verified (January 28, 2026)

