/**
 * Call Handler
 * Routes call signaling and manages call sessions
 * Handles call initiation, answer, ICE candidates, and termination
 * Includes 2FA verification for security
 */

const { v4: uuidv4 } = require('uuid');

class CallHandler {
    constructor(redis, io, masterConnector, logger) {
        this.redis = redis;
        this.io = io;
        this.masterConnector = masterConnector;
        this.logger = logger;

        this.activeCalls = new Map();
        this.stats = {
            initiated: 0,
            answered: 0,
            rejected: 0,
            completed: 0,
            failed: 0
        };
    }

    /**
     * Initiate a call
     */
    async initiateCall(callerId, calleeId, callType = 'voice', sdp = null) {
        try {
            const call = {
                id: uuidv4(),
                caller: callerId,
                callee: calleeId,
                callType,
                sdp,
                status: 'initiating',
                initiatedAt: Date.now(),
                ICECandidates: []
            };

            this.activeCalls.set(call.id, call);

            await this.redis.setex(
                `call:${call.id}`,
                3600, // 1 hour TTL
                JSON.stringify(call)
            );

            // ✅ FETCH CALLER USERNAME from master server instead of hardcoding 'Unknown'
            let callerUsername = 'Someone';
            try {
                const callerProfile = await this.masterConnector.getUserProfile(callerId);
                if (callerProfile && callerProfile.profile && callerProfile.profile.displayName) {
                    callerUsername = callerProfile.profile.displayName;
                } else if (callerProfile && callerProfile.username) {
                    callerUsername = callerProfile.username;
                }
                this.logger.info('Fetched caller username', { callerId, callerUsername });
            } catch (err) {
                this.logger.warn('Could not fetch caller username, using default', { callerId, error: err.message });
                // Fall back to 'Someone' if we can't fetch the profile
            }

            // Send invitation to callee using a client-friendly event name
            this.io.to(`user:${calleeId}`).emit('incomingCall', {
                callId: call.id,
                callerId: call.caller,
                callerUsername: callerUsername,  // ✅ FIXED: Now fetches actual username
                callType: call.callType,
                sdp: call.sdp,
                timestamp: call.initiatedAt
            });

            this.stats.initiated++;
            this.logger.info('Call initiated', { callId: call.id, caller: callerId, callee: calleeId });
            return call;
        } catch (error) {
            this.stats.failed++;
            this.logger.error('Call initiation failed', { error: error.message });
            throw error;
        }
    }

    /**
     * Answer a call (requires 2FA verification)
     */
    async answerCall(callId, answererId, sdp, totpCode, deviceId) {
        try {
            const callString = await this.redis.get(`call:${callId}`);
            const call = callString ? JSON.parse(callString) : this.activeCalls.get(callId);

            if (!call) throw new Error('Call not found');

            const twoFAResult = await this.masterConnector.verify2FA(answererId, totpCode, deviceId);
            if (!twoFAResult || !twoFAResult.verified) {
                this.logger.warn('2FA verification failed for call answer', { callId, userId: answererId });
                throw new Error('2FA verification failed');
            }

            call.status = 'answered';
            call.answeredBy = answererId;
            call.answeredAt = Date.now();
            call.sdp = sdp;

            this.activeCalls.set(callId, call);
            await this.redis.setex(`call:${callId}`, 3600, JSON.stringify(call));

            this.io.to(`user:${call.caller}`).emit('callAnswered', {
                callId: call.id,
                sdp: call.sdp,
                answeredBy: call.answeredBy,
                answeredAt: call.answeredAt
            });

            this.stats.answered++;
            this.logger.info('Call answered', { callId, answeredBy: answererId });
            return call;
        } catch (error) {
            this.stats.failed++;
            this.logger.error('Call answer failed', { error: error.message, callId });
            throw error;
        }
    }

    /**
     * Add ICE candidate
     */
    async addICECandidate(callId, userId, candidate) {
        try {
            const callString = await this.redis.get(`call:${callId}`);
            const call = callString ? JSON.parse(callString) : this.activeCalls.get(callId);

            if (!call) return false;

            call.ICECandidates.push({ from: userId, candidate, timestamp: Date.now() });
            await this.redis.setex(`call:${callId}`, 3600, JSON.stringify(call));

            const otherUser = call.caller === userId ? call.callee : call.caller;
            this.io.to(`user:${otherUser}`).emit('iceCandidate', {
                callId: call.id,
                from: userId,
                candidate: candidate
            });

            this.logger.debug('ICE candidate added', { callId });
            return true;
        } catch (error) {
            this.logger.error('Failed to add ICE candidate', { error: error.message });
            return false;
        }
    }

    /**
     * Reject a call
     */
    async rejectCall(callId, rejecterId, reason = 'user_rejected') {
        try {
            const callString = await this.redis.get(`call:${callId}`);
            const call = callString ? JSON.parse(callString) : this.activeCalls.get(callId);

            if (!call) throw new Error('Call not found');

            call.status = 'rejected';
            call.rejectedBy = rejecterId;
            call.rejectedAt = Date.now();
            call.rejectionReason = reason;

            this.io.to(`user:${call.caller}`).emit('callRejected', {
                callId: call.id,
                reason: reason,
                rejectedBy: rejecterId
            });

            this.cleanupCall(callId);
            this.stats.rejected++;
            this.logger.info('Call rejected', { callId, reason });
            return true;
        } catch (error) {
            this.logger.error('Failed to reject call', { error: error.message });
            return false;
        }
    }

    /**
     * End a call
     */
    async endCall(callId, endingUserId) {
        try {
            const callString = await this.redis.get(`call:${callId}`);
            const call = callString ? JSON.parse(callString) : this.activeCalls.get(callId);
            
            if (!call) return null;

            call.status = 'ended';
            call.endedAt = Date.now();
            call.duration = call.endedAt - (call.answeredAt || call.initiatedAt);

            const finalCallRecord = JSON.stringify(call);
            await this.redis.lpush(`call_history:${call.caller}`, finalCallRecord);
            if (call.callee) {
                await this.redis.lpush(`call_history:${call.callee}`, finalCallRecord);
            }

            const payload = {
                callId: call.id,
                endedBy: endingUserId,
                duration: call.duration,
                endedAt: call.endedAt
            };
            this.io.to(`user:${call.caller}`).emit('callEnded', payload);
            if (call.callee) {
                this.io.to(`user:${call.callee}`).emit('callEnded', payload);
            }

            this.cleanupCall(callId);
            this.stats.completed++;
            this.logger.info('Call ended', { callId, duration: call.duration });
            return call;
        } catch (error) {
            this.logger.error('Failed to end call', { error: error.message });
            return null;
        }
    }

    /**
     * Cleanup call
     */
    cleanupCall(callId) {
        this.activeCalls.delete(callId);
        this.redis.del(`call:${callId}`);
    }

    /**
     * Get call history for user
     */
    async getCallHistory(userId, limit = 50) {
        try {
            const callHistory = await this.redis.lrange(`call_history:${userId}`, 0, limit - 1);
            return callHistory.map(item => JSON.parse(item));
        } catch (error) {
            this.logger.error('Failed to get call history', { error: error.message });
            return [];
        }
    }

    /**
     * Get active calls
     */
    getActiveCalls() {
        return Array.from(this.activeCalls.values());
    }

    /**
     * Get handler statistics
     */
    getStats() {
        return {
            ...this.stats,
            activeCalls: this.activeCalls.size
        };
    }
}

module.exports = CallHandler;
