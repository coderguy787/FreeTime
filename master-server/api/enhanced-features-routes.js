/**
 * FreeTime Enhanced API Routes
 * New endpoints for latest features:
 * - User Profiles with profiles endpoint
 * - Group Management
 * - Message Reactions
 * - Media Gallery & Management
 * - Advanced Messaging Features
 */

const express = require('express');
const router = express.Router();
const { ObjectId } = require('mongodb');

// ✅ NEW: Import authentication middleware
const { verifyToken } = require('../middleware/auth-middleware');

// Database collections (initialized in main server)
let usersCollection;
let groupsCollection;
let messagesCollection;
let reactionsCollection;
let mediaCollection;
let profilesCollection;

// Set collections (called from main API file)
function setCollections(users, groups, messages, reactions, media, profiles) {
    usersCollection = users;
    groupsCollection = groups;
    messagesCollection = messages;
    reactionsCollection = reactions;
    mediaCollection = media;
    profilesCollection = profiles;
}

// Security middleware: intercept all 500 responses and replace raw error.message
// with a generic text to prevent internal details from leaking to clients (OWASP A3)
router.use((req, res, next) => {
    const originalJson = res.json.bind(res);
    res.json = function(data) {
        if (res.statusCode === 500 && data && typeof data.error === 'string') {
            console.error('[Enhanced API] Internal error detail (suppressed from client):', data.error);
            data = { error: 'An internal server error occurred. Please try again.' };
        }
        return originalJson(data);
    };
    next();
});

// ============================================
// USER PROFILE ENDPOINTS
// ============================================

/**
 * GET /api/profiles/:userId
 * Get user profile with all details
 */
router.get('/profiles/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        
        const profile = await profilesCollection.findOne({
            userId: new ObjectId(userId)
        });

        if (!profile) {
            return res.status(404).json({ error: 'Profile not found' });
        }

        // Calculate statistics
        const messageCount = await messagesCollection.countDocuments({
            senderId: new ObjectId(userId)
        });

        const sharedChats = await messagesCollection.distinct('conversationId', {
            $or: [
                { senderId: new ObjectId(userId) },
                { receiverId: new ObjectId(userId) }
            ]
        });

        const groups = await groupsCollection.countDocuments({
            members: new ObjectId(userId)
        });

        res.json({
            ...profile,
            messageCount,
            sharedChats: sharedChats.length,
            commonGroups: groups
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * PUT /api/profiles/:userId
 * Update user profile
 */
router.put('/profiles/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const updates = req.body;

        // ✅ CRITICAL FIX: Validate profile update succeeded
        const result = await profilesCollection.updateOne(
            { userId: new ObjectId(userId) },
            { $set: { ...updates, updatedAt: new Date() } }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'User profile not found' });
        }

        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/profiles/:userId/privacy
 * Get user privacy settings
 */
router.get('/profiles/:userId/privacy', async (req, res) => {
    try {
        const { userId } = req.params;

        // ✅ CRITICAL FIX: Validate user exists
        const profile = await profilesCollection.findOne(
            { userId: new ObjectId(userId) },
            { projection: { privacy: 1 } }
        );

        if (!profile) {
            return res.status(404).json({ error: 'User not found' });
        }

        res.json(profile.privacy || {});
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/profiles/:userId/block/:targetUserId
 * Block a user
 */
router.post('/profiles/:userId/block/:targetUserId', async (req, res) => {
    try {
        const { userId, targetUserId } = req.params;

        // ✅ CRITICAL FIX: Validate block operation succeeded
        const result = await profilesCollection.updateOne(
            { userId: new ObjectId(userId) },
            { $addToSet: { blockedUsers: new ObjectId(targetUserId) } }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'User not found' });
        }

        res.json({ success: true, modified: result.modifiedCount > 0 });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * DELETE /api/profiles/:userId/block/:targetUserId
 * Unblock a user
 */
router.delete('/profiles/:userId/block/:targetUserId', async (req, res) => {
    try {
        const { userId, targetUserId } = req.params;

        const result = await profilesCollection.updateOne(
            { userId: new ObjectId(userId) },
            { $pull: { blockedUsers: new ObjectId(targetUserId) } }
        );

        // CRITICAL FIX: Validate that unblock actually succeeded
        if (result.modifiedCount === 0) {
            return res.status(400).json({ error: 'User not found or target user was not blocked' });
        }

        res.json({ success: true, message: 'User unblocked' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ============================================
// GROUP CHAT ENDPOINTS
// ============================================

/**
 * GET /api/groups
 * Get all groups for a user
 */
router.get('/groups', async (req, res) => {
    try {
        const userId = req.user.id; // From JWT middleware
        
        const groups = await groupsCollection.find({
            members: new ObjectId(userId)
        }).toArray();

        res.json(groups);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/groups
 * Create new group
 */
router.post('/groups', async (req, res) => {
    try {
        const { name, description, members, avatar } = req.body;
        const userId = req.user.id;

        const group = {
            name,
            description,
            members: [new ObjectId(userId), ...members.map(m => new ObjectId(m))],
            avatar,
            createdBy: new ObjectId(userId),
            createdAt: new Date(),
            updatedAt: new Date(),
            messageCount: 0,
            isPrivate: false,
            isMuted: false
        };

        // ✅ CRITICAL FIX: Validate group creation succeeded
        const result = await groupsCollection.insertOne(group);
        
        if (!result.insertedId) {
            return res.status(500).json({ error: 'Failed to create group' });
        }
        
        res.json({ _id: result.insertedId, ...group });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/groups/:groupId
 * Get group details
 */
router.get('/groups/:groupId', async (req, res) => {
    try {
        const { groupId } = req.params;

        const group = await groupsCollection.findOne({
            _id: new ObjectId(groupId)
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        // Get member details
        const members = await usersCollection.find({
            _id: { $in: group.members }
        }).project({ password: 0 }).toArray();

        res.json({ ...group, members });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * PUT /api/groups/:groupId
 * Update group info  
 * ✅ FIXED: Added authentication and admin permission check
 */
router.put('/groups/:groupId', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const userId = req.user.userId;
        const updates = req.body;

        // ✅ NEW: Verify group exists and user is admin
        const group = await groupsCollection.findOne({ 
            $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] 
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        const isAdmin = group.ownerId === userId || 
                       (group.admins && group.admins.includes(userId)) ||
                       req.user.isAdmin === true;

        if (!isAdmin) {
            return res.status(403).json({ error: 'Only group admin can update group info' });
        }

        const result = await groupsCollection.updateOne(
            { $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] },
            { $set: { ...updates, updatedAt: new Date() } }
        );

        if (result.modifiedCount === 0) {
            return res.status(404).json({ error: 'Group not found' });
        }

        console.log(`✓ Group ${groupId} updated by ${userId}`);
        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        console.error('Update group error:', error);
        res.status(500).json({ error: 'Failed to update group: ' + error.message });
    }
});

/**
 * POST /api/groups/:groupId/members
 * Add member to group
 * ✅ FIXED: Added authentication and member addition logic with error handling
 */
router.post('/groups/:groupId/members', verifyToken, async (req, res) => {
    try {
        const { groupId } = req.params;
        const { memberId } = req.body;
        const userId = req.user.userId;

        // ✅ NEW: Validate input
        if (!memberId) {
            return res.status(400).json({ error: 'Member ID is required' });
        }

        // ✅ NEW: Verify group exists
        const group = await groupsCollection.findOne({ 
            $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] 
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        // ✅ NEW: Verify user has permission to add members (group admin only)
        const isAdmin = group.ownerId === userId || 
                       (group.admins && group.admins.includes(userId)) ||
                       req.user.isAdmin === true;

        if (!isAdmin && memberId !== userId) {
            // Users can only add themselves; admins can add anyone
            return res.status(403).json({ error: 'Only group admin can add members' });
        }

        // ✅ NEW: Check if member already exists
        if (group.members && group.members.includes(memberId)) {
            return res.status(400).json({ error: 'User is already a member of this group' });
        }

        const result = await groupsCollection.updateOne(
            { $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] },
            { $addToSet: { members: memberId } }
        );

        if (result.modifiedCount === 0) {
            return res.status(404).json({ error: 'Failed to add member' });
        }

        console.log(`✓ Member ${memberId} added to group ${groupId} by ${userId}`);
        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        console.error('Add member error:', error);
        res.status(500).json({ error: 'Failed to add member: ' + error.message });
    }
});

/**
 * DELETE /api/groups/:groupId/members/:memberId
 * Remove member from group
 * ✅ FIXED: Added permission check - only group admin can remove members
 */
router.delete('/groups/:groupId/members/:memberId', verifyToken, async (req, res) => {
    try {
        const { groupId, memberId } = req.params;
        const userId = req.user.userId;

        // ✅ NEW: Get group and verify requester is admin
        const group = await groupsCollection.findOne({ 
            $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] 
        });

        if (!group) {
            return res.status(404).json({ error: 'Group not found' });
        }

        // Check if requester is group admin or owner
        const isAdmin = group.ownerId === userId || 
                       group.createdBy === userId ||
                       (group.admins && (group.admins.includes(userId) || group.admins.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
                       (group.adminIds && (group.adminIds.includes(userId) || group.adminIds.some(a => a && typeof a === 'object' && (a.userId === userId || a.id === userId)))) ||
                       req.user.isAdmin === true;  // System admin can always remove

        if (!isAdmin && userId !== memberId) {
            return res.status(403).json({ 
                error: 'Only group admin can remove members' 
            });
        }

        // Prevent last admin from being removed
        const allAdmins = new Set([
            ...(Array.isArray(group.adminIds) ? group.adminIds.filter(a => typeof a === 'string') : []),
            ...(Array.isArray(group.admins) ? group.admins.filter(a => typeof a === 'string') : []),
            group.createdBy,
            group.ownerId
        ].filter(Boolean));
        if (userId === memberId && allAdmins.size === 1 && allAdmins.has(memberId)) {
            return res.status(400).json({ error: 'Cannot remove last administrator' });
        }

        // Try object format first (members stored as { userId: "...", ... })
        let result = await groupsCollection.updateOne(
            { $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] },
            { $pull: { 
                members: { userId: memberId },
                admins: memberId,
                adminIds: memberId
            } }
        );
        // If no match, try string format (members stored as plain userId strings)
        if (result.modifiedCount === 0) {
            result = await groupsCollection.updateOne(
                { $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] },
                { $pull: { 
                    members: memberId,
                    admins: memberId,
                    adminIds: memberId
                } }
            );
        }
        // Also try pulling object-format admins
        await groupsCollection.updateOne(
            { $or: [{ groupId: groupId }, { _id: new ObjectId(groupId) }] },
            { $pull: { 
                admins: { userId: memberId },
                adminIds: { userId: memberId }
            } }
        );

        if (result.modifiedCount === 0) {
            return res.status(404).json({ error: 'Member not found in group' });
        }

        console.log(`✓ Member ${memberId} removed from group ${groupId} by ${userId}`);
        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        console.error('Remove member error:', error);
        res.status(500).json({ error: 'Failed to remove member: ' + error.message });
    }
});

// ============================================
// MESSAGE REACTION ENDPOINTS
// ============================================

/**
 * POST /api/messages/:messageId/reactions
 * Add reaction to message
 */
router.post('/messages/:messageId/reactions', async (req, res) => {
    try {
        const { messageId } = req.params;
        const { emoji } = req.body;
        const userId = req.user.id;

        const reaction = {
            messageId: new ObjectId(messageId),
            userId: new ObjectId(userId),
            emoji,
            createdAt: new Date()
        };

        // Check if already reacted with this emoji
        const existing = await reactionsCollection.findOne({
            messageId: new ObjectId(messageId),
            userId: new ObjectId(userId),
            emoji
        });

        if (existing) {
            // Remove reaction if already exists (toggle)
            const deleteResult = await reactionsCollection.deleteOne({ _id: existing._id });
            if (deleteResult.deletedCount > 0) {
                // Also decrement message reaction count
                await messagesCollection.updateOne(
                    { _id: new ObjectId(messageId) },
                    { $inc: { reactionCount: -1 } }
                );
            }
            return res.json({ success: true, action: 'removed' });
        }

        const insertResult = await reactionsCollection.insertOne(reaction);
        if (!insertResult.insertedId) {
            return res.status(500).json({ error: 'Failed to add reaction' });
        }

        // Update message reaction count
        const updateResult = await messagesCollection.updateOne(
            { _id: new ObjectId(messageId) },
            { $inc: { reactionCount: 1 } }
        );
        if (updateResult.matchedCount === 0) {
            console.warn(`Message ${messageId} not found for reaction update`);
        }

        res.json({ success: true, action: 'added' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/messages/:messageId/reactions
 * Get all reactions for a message
 */
router.get('/messages/:messageId/reactions', async (req, res) => {
    try {
        const { messageId } = req.params;

        const reactions = await reactionsCollection.find({
            messageId: new ObjectId(messageId)
        }).toArray();

        // Group by emoji
        const grouped = reactions.reduce((acc, reaction) => {
            if (!acc[reaction.emoji]) {
                acc[reaction.emoji] = [];
            }
            acc[reaction.emoji].push(reaction.userId);
            return acc;
        }, {});

        res.json(grouped);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * DELETE /api/messages/:messageId/reactions/:emoji
 * Remove reaction
 */
router.delete('/messages/:messageId/reactions/:emoji', async (req, res) => {
    try {
        const { messageId, emoji } = req.params;
        const userId = req.user.id;

        const result = await reactionsCollection.deleteOne({
            messageId: new ObjectId(messageId),
            userId: new ObjectId(userId),
            emoji
        });

        if (result.deletedCount > 0) {
            await messagesCollection.updateOne(
                { _id: new ObjectId(messageId) },
                { $inc: { reactionCount: -1 } }
            );
        }

        res.json({ success: true });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ============================================
// MEDIA GALLERY ENDPOINTS
// ============================================

/**
 * GET /api/media
 * Get user's media files
 */
router.get('/media', async (req, res) => {
    try {
        const userId = req.user.id;
        const { type } = req.query; // 'image', 'video', 'document'

        let query = { userId: new ObjectId(userId) };
        if (type) {
            query.type = type;
        }

        const media = await mediaCollection.find(query)
            .sort({ createdAt: -1 })
            .toArray();

        res.json(media);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/media
 * Upload media file
 */
router.post('/media', async (req, res) => {
    try {
        const userId = req.user.id;
        const { name, type, size, url, thumbnail, duration } = req.body;

        const mediaItem = {
            userId: new ObjectId(userId),
            name,
            type,
            size,
            url,
            thumbnail,
            duration: duration || null,
            createdAt: new Date()
        };

        const result = await mediaCollection.insertOne(mediaItem);
        
        // CRITICAL FIX: Validate that insertOne succeeded before returning ID
        if (!result.insertedId) {
            return res.status(500).json({ error: 'Failed to create media record in database' });
        }
        
        res.json({ _id: result.insertedId, ...mediaItem });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * DELETE /api/media/:mediaId
 * Delete media file
 */
router.delete('/media/:mediaId', async (req, res) => {
    try {
        const { mediaId } = req.params;
        const userId = req.user.id;

        const result = await mediaCollection.deleteOne({
            _id: new ObjectId(mediaId),
            userId: new ObjectId(userId)
        });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'Media not found' });
        }

        res.json({ success: true });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * GET /api/media/stats
 * Get storage statistics
 */
router.get('/media/stats', async (req, res) => {
    try {
        const userId = req.user.id;

        const stats = await mediaCollection.aggregate([
            { $match: { userId: new ObjectId(userId) } },
            {
                $group: {
                    _id: '$type',
                    count: { $sum: 1 },
                    totalSize: { $sum: '$size' }
                }
            }
        ]).toArray();

        const totalSize = stats.reduce((sum, stat) => sum + stat.totalSize, 0);

        res.json({
            stats,
            totalSize,
            usagePercent: (totalSize / (5 * 1024 * 1024 * 1024)) * 100 // 5GB quota
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ============================================
// MESSAGE FEATURES ENDPOINTS
// ============================================

/**
 * PUT /api/messages/:messageId
 * Edit message
 */
router.put('/messages/:messageId', async (req, res) => {
    try {
        const { messageId } = req.params;
        const { content } = req.body;
        const userId = req.user.id;

        const result = await messagesCollection.updateOne(
            {
                _id: new ObjectId(messageId),
                senderId: new ObjectId(userId)
            },
            {
                $set: {
                    content,
                    editedAt: new Date(),
                    isEdited: true
                }
            }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'Message not found or not yours' });
        }
        if (result.modifiedCount === 0) {
            return res.status(500).json({ error: 'Failed to update message' });
        }

        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * DELETE /api/messages/:messageId
 * Delete message
 */
router.delete('/messages/:messageId', async (req, res) => {
    try {
        const { messageId } = req.params;
        const userId = req.user.id;

        const result = await messagesCollection.deleteOne({
            _id: new ObjectId(messageId),
            senderId: new ObjectId(userId)
        });

        if (result.deletedCount === 0) {
            return res.status(404).json({ error: 'Message not found' });
        }

        // Clear reactions for this message
        await reactionsCollection.deleteMany({
            messageId: new ObjectId(messageId)
        });

        res.json({ success: true });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/messages/:messageId/pin
 * Pin message
 */
router.post('/messages/:messageId/pin', async (req, res) => {
    try {
        const { messageId } = req.params;

        const result = await messagesCollection.updateOne(
            { _id: new ObjectId(messageId) },
            { $set: { isPinned: true, pinnedAt: new Date() } }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'Message not found' });
        }

        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * DELETE /api/messages/:messageId/pin
 * Unpin message
 */
router.delete('/messages/:messageId/pin', async (req, res) => {
    try {
        const { messageId } = req.params;

        const result = await messagesCollection.updateOne(
            { _id: new ObjectId(messageId) },
            { $set: { isPinned: false, pinnedAt: null } }
        );

        if (result.matchedCount === 0) {
            return res.status(404).json({ error: 'Message not found' });
        }

        res.json({ success: true, modified: result.modifiedCount });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

/**
 * POST /api/messages/:messageId/forward
 * Forward message
 */
router.post('/messages/:messageId/forward', async (req, res) => {
    try {
        const { messageId } = req.params;
        const { targetConversationId } = req.body;
        const userId = req.user.id;

        const originalMessage = await messagesCollection.findOne({
            _id: new ObjectId(messageId)
        });

        const forwardedMessage = {
            ...originalMessage,
            _id: undefined,
            conversationId: new ObjectId(targetConversationId),
            senderId: new ObjectId(userId),
            createdAt: new Date(),
            forwardedFrom: messageId,
            isForwarded: true
        };

        const result = await messagesCollection.insertOne(forwardedMessage);
        if (!result.insertedId) {
            return res.status(500).json({ error: 'Failed to forward message' });
        }
        res.json({ _id: result.insertedId, ...forwardedMessage });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ============================================
// SEARCH ENDPOINTS
// ============================================

/**
 * GET /api/messages/search
 * Search messages
 */
router.get('/messages/search', async (req, res) => {
    try {
        const { query, conversationId } = req.query;

        let searchQuery = { content: { $regex: query, $options: 'i' } };
        if (conversationId) {
            searchQuery.conversationId = new ObjectId(conversationId);
        }

        const results = await messagesCollection.find(searchQuery)
            .sort({ createdAt: -1 })
            .limit(50)
            .toArray();

        res.json(results);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
module.exports.setCollections = setCollections;
