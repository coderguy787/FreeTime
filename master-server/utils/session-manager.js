const crypto = require('crypto');
const { ObjectId } = require('mongodb');

class SessionManager {
  constructor(dbConnection, socketIoServer, jwtSecret) {
    this.db = dbConnection;
    this.socketIoServer = socketIoServer;
    this.jwtSecret = jwtSecret;
    this.activeSessions = new Map();

    this.startCleanupJob();

    console.log('[OK] SessionManager initialized');
  }

  async isUserConnected(userId, excludeDeviceId = null) {
    if (this.socketIoServer) {
        try {
            const sockets = await this.socketIoServer.in(`user:${userId}`).fetchSockets();
            if (sockets && sockets.length > 0) {
                if (excludeDeviceId) {
                    const otherDeviceSockets = sockets.filter(s => {
                        const socketDeviceId = s.data?.deviceId;
                        if (socketDeviceId && socketDeviceId !== excludeDeviceId) return true;
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

    if (global.wsUserMap) {
        const ws = global.wsUserMap.get(userId);
        if (ws) {
            if (Array.isArray(ws)) {
                if (ws.length > 0) return true;
            } else if (ws.readyState === 1) {
                return true;
            }
        }
    }

    return false;
  }

  async createSession(userId, deviceId, deviceInfo, force = false) {
    try {
      const sessionId = crypto.randomBytes(16).toString('hex');
      // only token hashes are stored
      const sessionToken = crypto.randomBytes(32).toString('hex');
      const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');

      const existingSession = await this.db.collection('activeSessions').findOne({
        userId: userId,
        deviceId: deviceId
      });

      if (existingSession) {
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

      const otherSessions = await this.db.collection('activeSessions')
        .find({
          userId: userId,
          deviceId: { $ne: deviceId }
        })
        .toArray();

      if (otherSessions.length > 0) {
        const isConnected = await this.isUserConnected(userId);

        let hasActiveSession = isConnected;
        if (!hasActiveSession) {
            const now = new Date();
            const activityThreshold = 5 * 60 * 1000;

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
          if (force) {
            console.log(`[FORCE] Logging out ${otherSessions.length} other sessions for user ${userId}`);
          } else {
            console.log(`[AUTO-TAKEOVER] Logging out ${otherSessions.length} inactive sessions for user ${userId}`);
          }

          for (const session of otherSessions) {
            await this.db.collection('activeSessions').deleteOne({ _id: session._id });
            this.activeSessions.delete(`${userId}:${session.deviceId}`);

            await this.notifySessionTerminated(userId, session.deviceId, {
              reason: force ? 'concurrent_login_override' : 'inactive_session_takeover',
              newDeviceInfo: deviceInfo,
              newLoginTime: new Date()
            });
          }
        }
      }

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

  async verifySession(userId, sessionToken, deviceId) {
    try {
      const cached = this.activeSessions.get(`${userId}:${deviceId}`);
      if (cached && cached.expiresAt > Date.now()) {
        const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');
        if (cached.sessionToken === tokenHash) {
          return { valid: true, cached: true };
        }
      }

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

  async logout(userId, sessionToken, deviceId) {
    try {
      const tokenHash = crypto.createHash('sha256').update(sessionToken).digest('hex');

      await this.db.collection('activeSessions').deleteOne({
        userId: userId,
        deviceId: deviceId,
        sessionToken: tokenHash
      });

      this.activeSessions.delete(`${userId}:${deviceId}`);

      console.log(`[OK] Session logged out for user ${userId} on device ${deviceId}`);
      return true;
    } catch (err) {
      console.error(`[ERROR] Failed to logout session: ${err.message}`);
      return false;
    }
  }

  async notifySessionTerminated(userId, deviceId, details) {
    try {
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

      await this.db.collection('notifications').insertOne({
        userId: userId,
        type: 'security.session_terminated',
        title: ' Account Accessed Elsewhere',
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

  async terminateSession(userId, sessionId) {
    try {
      const session = await this.db.collection('activeSessions').findOne({
        _id: new ObjectId(sessionId),
        userId: userId
      });

      if (!session) {
        return { success: false, reason: 'session_not_found' };
      }

      await this.notifySessionTerminated(userId, session.deviceId, {
        reason: 'manual_logout',
        newDeviceInfo: { deviceName: 'Admin' }
      });

      await this.db.collection('activeSessions').deleteOne({ _id: new ObjectId(sessionId) });

      console.log(`[OK] Session ${sessionId} terminated for user ${userId}`);
      return { success: true };
    } catch (err) {
      console.error(`[ERROR] Failed to terminate session: ${err.message}`);
      return { success: false, reason: err.message };
    }
  }

  async cleanupExpiredSessions() {
    try {
      const result = await this.db.collection('activeSessions').deleteMany({
        expiresAt: { $lt: new Date() }
      });

      if (result.deletedCount > 0) {
        console.log(`[OK] Cleaned up ${result.deletedCount} expired sessions from DB`);
      }

      const now = Date.now();
      let evicted = 0;
      for (const [key, value] of this.activeSessions.entries()) {
        if (value.expiresAt < now) {
          this.activeSessions.delete(key);
          evicted++;
        }
      }
      if (evicted > 0) {
        console.log(`[OK] Evicted ${evicted} expired sessions from memory cache`);
      }

      return result.deletedCount;
    } catch (err) {
      console.error(`[ERROR] Failed to cleanup sessions: ${err.message}`);
    }
  }

  startCleanupJob() {
    setInterval(async () => {
      await this.cleanupExpiredSessions();
    }, 60 * 60 * 1000);

    console.log('[OK] Session cleanup job started (runs hourly)');
  }
}

module.exports = SessionManager;
