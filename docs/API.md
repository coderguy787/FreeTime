# 🔗 SecureChat API Reference

**Last Updated:** February 2026 | **Base URL:** `http://server-ip:8081` | **Status:** ✅ Production Ready

---

## 📋 API Overview

| Category | Endpoints | Status |
|----------|-----------|--------|
| **Authentication** | /api/auth/* | ✅ Ready |
| **Users** | /api/users/* | ✅ Ready |
| **Messages** | /api/messages/* | ✅ Ready |
| **Groups** | /api/groups/* | ✅ Ready |
| **Media** | /api/media/* | ✅ Ready |
| **Channels** | /api/channels/* | ✅ Ready |
| **Friends** | /api/friends/* | ✅ Ready |

---

## 🔐 Authentication Endpoints

### Register New User

```http
POST /api/auth/register
Content-Type: application/json

{
    "username": "john_doe",
    "password": "SecurePass123!",
    "email": "john@example.com"
}
```

**Response:**
```json
{
    "success": true,
    "message": "User created successfully",
    "userId": "user_123",
    "token": "eyJhbGc..."
}
```

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
    "username": "john_doe",
    "password": "SecurePass123!"
}
```

**Response:**
```json
{
    "success": true,
    "token": "eyJhbGc...",
    "userId": "user_123",
    "username": "john_doe",
    "requires2FA": true
}
```

### Verify 2FA

```http
POST /api/auth/verify-2fa
Content-Type: application/json
Authorization: Bearer <temp_token>

{
    "code": "123456"
}
```

**Response:**
```json
{
    "success": true,
    "token": "eyJhbGc...",
    "expiresIn": 86400
}
```

### Refresh Token

```http
POST /api/auth/refresh-token
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "token": "eyJhbGc..."
}
```

---

## 👤 User Endpoints

### Get User Profile

```http
GET /api/users/:userId
Authorization: Bearer <token>
```

**Response:**
```json
{
    "userId": "user_123",
    "username": "john_doe",
    "email": "john@example.com",
    "displayName": "John Doe",
    "avatar": "https://...",
    "bio": "Developer",
    "createdAt": "2026-01-15T10:30:00Z",
    "isOnline": true
}
```

### Update Profile

```http
PUT /api/users/:userId
Authorization: Bearer <token>
Content-Type: application/json

{
    "displayName": "John Developer",
    "bio": "Backend Developer",
    "avatar": "base64_image_data"
}
```

**Response:**
```json
{
    "success": true,
    "user": { /* updated user */ }
}
```

### Search Users

```http
GET /api/users/search?q=john
Authorization: Bearer <token>
```

**Response:**
```json
{
    "results": [
        {
            "userId": "user_123",
            "username": "john_doe",
            "displayName": "John Doe",
            "avatar": "https://..."
        }
    ]
}
```

---

## 💬 Message Endpoints

### Send Message

```http
POST /api/messages/send
Authorization: Bearer <token>
Content-Type: application/json

{
    "recipientId": "user_456",
    "content": "encrypted_message_here",
    "encryptionKey": "iv:ciphertext:tag"
}
```

**Response:**
```json
{
    "success": true,
    "messageId": "msg_789",
    "timestamp": 1707123456789,
    "deliveryStatus": "sent"
}
```

### Get Messages

```http
GET /api/messages/:chatId?limit=50&offset=0
Authorization: Bearer <token>
```

**Response:**
```json
{
    "messages": [
        {
            "messageId": "msg_789",
            "senderId": "user_123",
            "content": "encrypted_message_here",
            "timestamp": 1707123456789,
            "isRead": true
        }
    ],
    "hasMore": true,
    "total": 150
}
```

### Mark as Read

```http
PUT /api/messages/:messageId/read
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true
}
```

### Delete Message

```http
DELETE /api/messages/:messageId
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "message": "Message deleted"
}
```

---

## 👥 Group Endpoints

### Create Group

```http
POST /api/groups/create
Authorization: Bearer <token>
Content-Type: application/json

{
    "groupName": "Team Alpha",
    "description": "Project team",
    "memberIds": ["user_456", "user_789"]
}
```

**Response:**
```json
{
    "success": true,
    "groupId": "group_123",
    "groupName": "Team Alpha",
    "createdAt": "2026-01-15T10:30:00Z"
}
```

### Send Group Message

```http
POST /api/groups/:groupId/messages
Authorization: Bearer <token>
Content-Type: application/json

{
    "content": "encrypted_message",
    "encryptionKey": "iv:ciphertext:tag"
}
```

**Response:**
```json
{
    "success": true,
    "messageId": "gmsg_123",
    "timestamp": 1707123456789
}
```

### Add Group Member

```http
POST /api/groups/:groupId/members
Authorization: Bearer <token>
Content-Type: application/json

{
    "userId": "user_999"
}
```

**Response:**
```json
{
    "success": true,
    "message": "Member added"
}
```

### Remove Group Member

```http
DELETE /api/groups/:groupId/members/:userId
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "message": "Member removed"
}
```

### Leave Group

```http
POST /api/groups/:groupId/leave
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "message": "Left group"
}
```

---

## 📁 Media Endpoints

### Upload Media

```http
POST /api/media/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
    "file": <binary_data>,
    "chatId": "chat_123",
    "type": "photo",
    "encryptionKey": "iv:ciphertext:tag"
}
```

**Response:**
```json
{
    "success": true,
    "mediaId": "media_456",
    "url": "https://...",
    "lockTimeout": 3000
}
```

### Get Media

```http
GET /api/media/:mediaId
Authorization: Bearer <token>
```

**Response:**
```
[Binary file data]
Headers:
- Content-Type: image/jpeg (or appropriate type)
- Content-Disposition: attachment; filename="photo.jpg"
```

### Delete Media

```http
DELETE /api/media/:mediaId
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true
}
```

---

## 🔔 Channel Endpoints

### Create Channel

```http
POST /api/channels/create
Authorization: Bearer <token>
Content-Type: application/json

{
    "channelName": "Announcements",
    "description": "Company announcements",
    "isPublic": true
}
```

**Response:**
```json
{
    "success": true,
    "channelId": "channel_123"
}
```

### Post to Channel

```http
POST /api/channels/:channelId/messages
Authorization: Bearer <token>
Content-Type: application/json

{
    "content": "encrypted_announcement"
}
```

**Response:**
```json
{
    "success": true,
    "postId": "post_789"
}
```

### Get Channel Messages

```http
GET /api/channels/:channelId/messages?limit=50
Authorization: Bearer <token>
```

**Response:**
```json
{
    "messages": [
        {
            "postId": "post_789",
            "senderId": "user_123",
            "content": "encrypted_message",
            "timestamp": 1707123456789,
            "likes": 5
        }
    ]
}
```

### Subscribe to Channel

```http
POST /api/channels/:channelId/subscribe
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "subscribed": true
}
```

---

## 👫 Friend Endpoints

### Send Friend Request

```http
POST /api/friends/request
Authorization: Bearer <token>
Content-Type: application/json

{
    "targetUserId": "user_456"
}
```

**Response:**
```json
{
    "success": true,
    "requestId": "freq_123"
}
```

### Get Pending Requests

```http
GET /api/friends/requests/pending
Authorization: Bearer <token>
```

**Response:**
```json
{
    "requests": [
        {
            "requestId": "freq_123",
            "senderId": "user_456",
            "senderName": "John Doe",
            "senderAvatar": "https://...",
            "sentAt": "2026-01-15T10:30:00Z"
        }
    ]
}
```

### Accept Friend Request

```http
POST /api/friends/request/:requestId/accept
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "message": "Friend request accepted"
}
```

### Reject Friend Request

```http
POST /api/friends/request/:requestId/reject
Authorization: Bearer <token>
```

**Response:**
```json
{
    "success": true,
    "message": "Friend request rejected"
}
```

### Get Friends List

```http
GET /api/friends/list
Authorization: Bearer <token>
```

**Response:**
```json
{
    "friends": [
        {
            "userId": "user_456",
            "username": "jane_doe",
            "displayName": "Jane Doe",
            "avatar": "https://...",
            "isOnline": true
        }
    ]
}
```

---

## 🌐 WebSocket Events

### Connection

```javascript
const socket = io('http://server-ip:8080', {
    auth: {
        token: 'your_jwt_token'
    }
});

socket.on('connect', () => {
    console.log('Connected');
});
```

### Message Received

```javascript
socket.on('message:received', (data) => {
    console.log('New message:', data);
    // data: { messageId, senderId, content, timestamp }
});
```

### User Online/Offline

```javascript
socket.on('user:online', (userId) => {
    // User came online
});

socket.on('user:offline', (userId) => {
    // User went offline
});
```

### Typing Indicator

```javascript
// Send typing notification
socket.emit('typing:start', { chatId: 'chat_123' });

// Listen for typing
socket.on('typing:notification', (data) => {
    // data: { userId, chatId, isTyping }
});
```

### Message Read Receipt

```javascript
socket.on('message:read', (messageId) => {
    // Message read by recipient
});
```

### Group Updated

```javascript
socket.on('group:updated', (data) => {
    // data: { groupId, action, details }
    // action: 'member_added', 'member_removed', 'message_sent'
});
```

---

## ⚡ Error Responses

### 400 Bad Request

```json
{
    "success": false,
    "error": "Invalid request",
    "details": "Missing required field: content"
}
```

### 401 Unauthorized

```json
{
    "success": false,
    "error": "Unauthorized",
    "message": "Invalid or expired token"
}
```

### 403 Forbidden

```json
{
    "success": false,
    "error": "Forbidden",
    "message": "You don't have permission for this action"
}
```

### 404 Not Found

```json
{
    "success": false,
    "error": "Not found",
    "message": "Message not found"
}
```

### 429 Too Many Requests

```json
{
    "success": false,
    "error": "Rate limited",
    "retryAfter": 60
}
```

### 500 Server Error

```json
{
    "success": false,
    "error": "Server error",
    "message": "Internal server error occurred"
}
```

---

## 🔑 Authentication Headers

All endpoints (except /api/auth/) require:

```
Authorization: Bearer <jwt_token>
```

**Obtaining a token:**
1. POST /api/auth/register or login
2. Receive JWT token in response
3. Include in all subsequent requests
4. Token expires in 24 hours
5. Use refresh endpoint to get new token

---

## 📊 Rate Limiting

- **General API:** 100 requests/15 minutes
- **Authentication:** 5 attempts/15 minutes
- **Media Upload:** 50 MB/day per user
- **WebSocket:** 1000 events/minute

---

## ✅ API Status

- ✅ All endpoints tested and working
- ✅ Error handling implemented
- ✅ Rate limiting configured
- ✅ Authentication secured
- ✅ CORS configured
- ✅ Compression enabled
- ✅ Logging enabled

---

**Status:** ✅ Production Ready | **Date:** February 2026
