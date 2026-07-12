/**
 * FreeTime Session Manager
 * Handles session tracking, concurrent login prevention, and device management
 * 
 * Features:
 * - One active session per device per user
 * - Auto-invalidate previous sessions on new login
 * - Notify terminated devices via Socket.IO
 * - Track device info (OS, model, version)
 * - Auto-cleanup expired sessions (30 days)
 */

const crypto = require('crypto');
const { ObjectId } = require('mongodb');

class SessionManager {
  constructor(dbConnection, socketIoServer, jwtSecret) {
    this.db = dbConnection;
    this.socketIoServer = socketIoServer;
    this.jwtSecret = jwtSecret;
    this.activeSessions = new Map(); // In-memory cache
    
    // Start cleanup job every hour
    this.startCleanupJob();
    
    console.log('[OK] SessionManager initialized');
  }

  /**
   * Check if a user has any active WebSocket or Socket.IO connections
   * @param {string} userId User ID to check
   * @param {string} [excludeDeviceId] Optional device ID to ignore (check if OTHER devices are connected)
   */
  async isUserConnected(userId, excludeDeviceId = null) {
    // 1. Check Socket.IO (primary transport for Android)
    if (this.socketIoServer) {
        try {
            // Room user:userId is joined by all sockets of that user
            const sockets = await this.socketIoServer.in(`user:${userId}`).fetchSockets();
            if (sockets && sockets.length > 0) {
                // If excludeDeviceId is provided, we need to check if any of these sockets 
                // belong to a DIFFERENT device. We store deviceId in socket.data during handshake.
                if (excludeDeviceId) {
                    // Check if any socket belongs to a DIFFERENT device.
                    // If a socket has NO deviceId, we treat it as potentially "unknown" 
                    // and only count it if we're NOT in auto-takeover mode.
                    const otherDeviceSockets = sockets.filter(s => {
                        const socketDeviceId = s.data?.deviceId;
                        // If socket has a deviceId and it's different, it's definitely another device
                        if (socketDeviceId && socketDeviceId !== excludeDeviceId) return true;
                        // If socket has NO deviceId, we assume it might be a legacy connection
                        // and we only consider it "another device" if it's NOT the same IP as our excludeDeviceId?
                        // For now, let's be lenient: if no deviceId, assume it's NOT another device 
                        // to allow smooth "Zero-Touch" recovery.
                        return false;
                    });
                    if (otherDeviceSockets.length > 0) return true;
                } else {
                    return true;
                }
            }
        } catch (err) {
            console.warn(`[WARN] Failed to fetch Socket.IO sockets for ${userId}: ${err.message}`);
        }
    }

    // 2. Check global wsUserMap (legacy raw WebSockets)
    if (global.wsUserMap) {
        const ws = global.wsUserMap.get(userId);
        if (ws) {
            // Handle both Array (Socket.IO IDs) and raw WebSocket object formats
            if (Array.isArray(ws)) {
                if (ws.length > 0) return true;
            } else if (ws.readyState === 1) { // 1 = OPEN for raw WebSocket
                return true;
            }
        }
    }

    return false;
  }

  /**
   * Create a new session when user logs in
   */
  async createSession(userId, deviceId, deviceInfo, force = false) {
    try {
      const sessionId = crypto.randomBytes(16).toString('hex');
      const sessionToken = crypto.randomBytes(32).toString('hex');
      const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');

      // Check for existing session on same device
      const existingSession = await this.db.collection('activeSessions').findOne({
        userId: userId,
        deviceId: deviceId
      });

      if (existingSession) {
        // Same device, same user - update existing session
        await this.db.collection('activeSessions').updateOne(
          { _id: existingSession._id },
          {
            $set: {
              sessionToken: tokenHash,
              loginTime: new Date(),
              lastActivityTime: new Date(),
              expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
              deviceInfo: deviceInfo
            }
          }
        );
        
        console.log(`[OK] Session updated for user ${userId} on device ${deviceId}`);
        return { sessionId: existingSession._id.toString(), sessionToken };
      }

      // New device - check if user has existing sessions on other devices
      const otherSessions = await this.db.collection('activeSessions')
        .find({ 
          userId: userId,
          deviceId: { $ne: deviceId } 
        })
        .toArray();

      if (otherSessions.length > 0) {
        // Check if ANY of these sessions are "Live"
        // A session is Live if it's currently connected via socket OR has recent activity
        const isConnected = await this.isUserConnected(userId);
        
        let hasActiveSession = isConnected;
        if (!hasActiveSession) {
            const now = new Date();
            const activityThreshold = 5 * 60 * 1000; // 5 minutes
            
            for (const session of otherSessions) {
                if (session.lastActivityTime && (now - new Date(session.lastActivityTime)) < activityThreshold) {
                    hasActiveSession = true;
                    break;
                }
            }
        }

        if (hasActiveSession && !force) {
          console.log(`[BLOCKED] Concurrent login attempt for user ${userId} from new device ${deviceId} (Active session found)`);
          const error = new Error('Account already in use by another device');
          error.code = 'CONCURRENT_LOGIN';
          error.existingDevice = otherSessions[0].deviceInfo?.deviceName || 'Unknown Device';
          throw error;
        } else {
          // Auto-force logout of dead sessions OR requested force logout
          if (force) {
            console.log(`[FORCE] Logging out ${otherSessions.length} other sessions for user ${userId}`);
          } else {
            console.log(`[AUTO-TAKEOVER] Logging out ${otherSessions.length} inactive sessions for user ${userId}`);
          }

          for (const session of otherSessions) {
            await this.db.collection('activeSessions').deleteOne({ _id: session._id });
            this.activeSessions.delete(`${userId}:${session.deviceId}`);
            
            // Notify the other device if it might still be partially alive
            await this.notifySessionTerminated(userId, session.deviceId, {
              reason: force ? 'concurrent_login_override' : 'inactive_session_takeover',
              newDeviceInfo: deviceInfo,
              newLoginTime: new Date()
            });
          }
        }
      }

      // Create new session
      const newSessionDoc = {
        sessionId: sessionId,
        userId: userId,
        deviceId: deviceId,
        sessionToken: tokenHash,
        deviceInfo: deviceInfo,
        loginTime: new Date(),
        lastActivityTime: new Date(),
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
      };

      const insertResult = await this.db.collection('activeSessions').insertOne(newSessionDoc);

      // Update user's activeSessions array and currentDeviceId for token verification
      await this.db.collection('users').updateOne(
        { id: userId },
        {
          $set: {
            currentDeviceId: deviceId,
            lastLogin: {
              deviceId: deviceId,
              deviceName: deviceInfo.deviceName,
              platform: deviceInfo.platform,
              loginTime: new Date(),
              ipAddress: deviceInfo.ipAddress
            }
          },
          $push: {
            activeSessions: {
              sessionId: sessionId,
              deviceId: deviceId,
              loginTime: new Date()
            }
          }
        }
      );

      // Cache in memory
      this.activeSessions.set(`${userId}:${deviceId}`, {
        sessionToken: tokenHash,
        expiresAt: newSessionDoc.expiresAt
      });

      console.log(`[OK] New session created for user ${userId} on device ${deviceId}`);
      return { sessionId: sessionId, sessionToken: sessionToken };
    } catch (err) {
      console.error(`[ERROR] Failed to create session: ${err.message}`);
      throw err;
    }
  }

  /**
   * Verify if a session token is still valid
   */
  async verifySession(userId, sessionToken, deviceId) {
    try {
      // Check memory cache first (fast path)
      const cached = this.activeSessions.get(`${userId}:${deviceId}`);
      if (cached && cached.expiresAt > Date.now()) {
        const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');
        if (cached.sessionToken === tokenHash) {
          return { valid: true, cached: true };
        }
      }

      // Query database
      const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');
      const session = await this.db.collection('activeSessions').findOne({
        userId: userId,
        deviceId: deviceId,
        sessionToken: tokenHash,
        expiresAt: { $gt: new Date() }
      });

      if (!session) {
        return { valid: false, reason: 'session_not_found_or_expired' };
      }

      // Update last activity
      await this.db.collection('activeSessions').updateOne(
        { _id: session._id },
        { $set: { lastActivityTime: new Date() } }
      );

      return { valid: true, cached: false };
    } catch (err) {
      console.error(`[ERROR] Failed to verify session: ${err.message}`);
      return { valid: false, reason: 'verification_error' };
    }
  }

  /**
   * Logout user by invalidating session
   */
  async logout(userId, sessionToken, deviceId) {
    try {
      const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');
      
      await this.db.collection('activeSessions').deleteOne({
        userId: userId,
        deviceId: deviceId,
        sessionToken: tokenHash
      });

      // Remove from cache
      this.activeSessions.delete(`${userId}:${deviceId}`);

      console.log(`[OK] Session logged out for user ${userId} on device ${deviceId}`);
      return true;
    } catch (err) {
      console.error(`[ERROR] Failed to logout session: ${err.message}`);
      return false;
    }
  }

  /**
   * Notify a device that its session was terminated
   */
  async notifySessionTerminated(userId, deviceId, details) {
    try {
      // Try Socket.IO broadcast
      try {
        const { broadcastToUser } = require('./broadcast-utils.js');
        
        broadcastToUser(global.wsClients, userId, 'session:terminated', {
          reason: details.reason,
          timestamp: new Date().getTime(),
          newDeviceInfo: details.newDeviceInfo,
          newLoginTime: details.newLoginTime,
          message: `Your account was accessed from another device (${details.newDeviceInfo?.deviceName || 'Unknown Device'})`
        });

        console.log(`[OK] Session termination notification sent to user ${userId} via Socket.IO`);
      } catch (wsErr) {
        console.warn(`[WARN] Socket.IO broadcast failed: ${wsErr.message}`);
      }

      // Create persistent notification
      await this.db.collection('notifications').insertOne({
        userId: userId,
        type: 'security.session_terminated',
        title: '🔐 Account Accessed Elsewhere',
        body: `Your account was accessed from another device (${details.newDeviceInfo?.deviceName || 'Unknown Device'})`,
        data: {
          reason: details.reason,
          newDeviceInfo: details.newDeviceInfo,
          newLoginTime: details.newLoginTime
        },
        isRead: false,
        createdAt: new Date(),
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
      });

      console.log(`[OK] Persistent notification created for user ${userId}`);
      return true;
    } catch (err) {
      console.error(`[ERROR] Failed to notify session termination: ${err.message}`);
      return false;
    }
  }

  /**
   * Get all active sessions for a user
   */
  async getActiveSessions(userId) {
    try {
      const sessions = await this.db.collection('activeSessions')
        .find({
          userId: userId,
          expiresAt: { $gt: new Date() }
        })
        .toArray();

      return sessions.map(s => ({
        sessionId: s._id.toString(),
        deviceId: s.deviceId,
        deviceName: s.deviceInfo?.deviceName || 'Unknown',
        platform: s.deviceInfo?.platform || 'Unknown',
        osVersion: s.deviceInfo?.osVersion || 'Unknown',
        appVersion: s.deviceInfo?.appVersion || 'Unknown',
        ipAddress: s.deviceInfo?.ipAddress || 'Unknown',
        loginTime: s.loginTime,
        lastActivityTime: s.lastActivityTime,
        expiresAt: s.expiresAt
      }));
    } catch (err) {
      console.error(`[ERROR] Failed to get active sessions: ${err.message}`);
      return [];
    }
  }

  /**
   * Terminate a specific session manually
   */
  async terminateSession(userId, sessionId) {
    try {
      const session = await this.db.collection('activeSessions').findOne({
        _id: new ObjectId(sessionId),
        userId: userId
      });

      if (!session) {
        return { success: false, reason: 'session_not_found' };
      }

      // Notify the device
      await this.notifySessionTerminated(userId, session.deviceId, {
        reason: 'manual_logout',
        newDeviceInfo: { deviceName: 'Admin' }
      });

      // Delete the session
      await this.db.collection('activeSessions').deleteOne({ _id: new ObjectId(sessionId) });

      console.log(`[OK] Session ${sessionId} terminated for user ${userId}`);
      return { success: true };
    } catch (err) {
      console.error(`[ERROR] Failed to terminate session: ${err.message}`);
      return { success: false, reason: err.message };
    }
  }

  /**
   * Clean up expired sessions (runs hourly)
   */
  async cleanupExpiredSessions() {
    try {
      const result = await this.db.collection('activeSessions').deleteMany({
        expiresAt: { $lt: new Date() }
      });

      if (result.deletedCount > 0) {
        console.log(`[OK] Cleaned up ${result.deletedCount} expired sessions`);
      }
      return result.deletedCount;
    } catch (err) {
      console.error(`[ERROR] Failed to cleanup sessions: ${err.message}`);
    }
  }

  /**
   * Start hourly cleanup job
   */
  startCleanupJob() {
    setInterval(async () => {
      await this.cleanupExpiredSessions();
    }, 60 * 60 * 1000); // Every hour

    console.log('[OK] Session cleanup job started (runs hourly)');
  }
}

module.exports = SessionManager;
