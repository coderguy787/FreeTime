package com.freetime.app.data.network

// Auth Models
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String = "",
    val rememberMe: Boolean = false,
    val force: Boolean = false,
    // ✅ NEW: Device info for session tracking
    val deviceInfo: DeviceInfo? = null
)

data class DeviceInfo(
    val platform: String,
    val deviceName: String,
    val osVersion: String,
    val appVersion: String,
    val androidId: String = "",
    val buildId: String = ""
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userId: String = "",
    val token: String = "",
    val user: UserResponse? = null,
    val requiresTwoFactor: Boolean = false,
    val tempToken: String? = null,
    val setupRequired: Boolean = false,
    // ✅ NEW: Session info for concurrent login prevention
    val sessionId: String? = null,
    val deviceId: String? = null
)

data class SignUpRequest(
    val username: String,
    val email: String,
    val displayName: String? = null,
    val password: String,
    val confirmPassword: String,
    // Device fingerprinting for bot detection
    val deviceFingerprint: DeviceFingerprint? = null
)

data class DeviceFingerprint(
    val deviceId: String,
    val deviceModel: String,
    val deviceBrand: String,
    val osVersion: String,
    val appVersion: String,
    val buildFingerprint: String,
    val androidId: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Enhanced security fields for rate limiting
    val hardwareSerial: String = "unknown",
    val deviceName: String = "unknown",
    val product: String = "unknown",
    val fingerprintHash: String = ""
)

data class SignUpResponse(
    val success: Boolean,
    val message: String = "",
    val error: String? = null,
    val token: String = "",
    val user: SignUpUserInfo? = null,
    val tempToken: String? = null,
    val userId: String? = null
)

data class SignUpUserInfo(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "USER",
    val createdAt: String = ""
)

data class DeviceRegisterRequest(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "android",
    val osVersion: String = "",
    val appVersion: String = ""
)

data class DeviceRegisterResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String = ""
)

data class VerifyResponse(
    val success: Boolean,
    val valid: Boolean,
    val userId: String = "",
    val username: String = ""
)

// User Models
data class UserResponse(
    val _id: String,
    val userId: String,
    val username: String,
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profileImageUrl: String? = null,
    val avatar: String? = null,
    val tags: List<String> = emptyList(),
    val status: String = "offline",
    val privacyLevel: String = "public",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class UpdateUserRequest(
    val email: String? = null,
    val status: String? = null,
    val bio: String? = null,
    val displayName: String? = null,
    val profilePicture: String? = null
)

// Message Models
data class MessageResponse(
    val _id: String,
    val id: String? = null,  // UUID stored as 'id' – matches socket event message ID
    val senderId: String,
    val recipientId: String,
    val content: String? = null,  // Made nullable with fallback
    val timestamp: Long,
    val read: Boolean = false,
    val type: String = "text",
    val senderAvatar: String? = null,
    val senderName: String? = null,
    val senderDisplayName: String? = null,
    val mediaId: String? = null,
    val mediaType: String? = null,
    val mediaName: String? = null,
    val mediaShareMode: String? = null, // ✅ NEW: "public" (unencrypted) or "protected" (encrypted)
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> list of userIds
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    // ✅ NEW: Color-coding fields from backend
    val senderTags: List<String> = emptyList(),
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false,
    // ✅ NEW: Announcement metadata
    val subject: String? = null,
    val isAdminAnnouncement: Boolean = false
)

data class SendMessageRequest(
    val recipientId: String,
    val content: String,
    val type: String = "text",
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null
)

// Call Models
data class InitiateCallRequest(
    val recipientId: String,
    val callType: String = "voice",
    val offer: String = ""
)

data class CallResponse(
    val _id: String,
    val callId: String,
    val callerId: String,
    val recipientId: String,
    val callType: String,
    val status: String,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val offer: String? = null,
    val answer: String? = null,
    val candidates: List<String> = emptyList()
)

data class AnswerCallRequest(
    val answer: String = "",
    val sdpAnswer: String = ""  // WebRTC SDP answer
)

data class UpdateCallStatusRequest(
    val status: String
)

data class AddIceCandidateRequest(
    val candidate: String
)

data class EndCallRequest(
    val duration: Int = 0  // Call duration in seconds
)

// FCM Models
data class FcmTokenRequest(
    val fcmToken: String,
    val deviceId: String = ""
)

data class FcmTokenResponse(
    val success: Boolean,
    val message: String
)

// File Models
data class FileUploadRequest(
    val fileName: String,
    val fileData: String, // Base64 encoded
    val fileType: String,
    val fileSize: Long
)

data class FileUploadResponse(
    val success: Boolean,
    val message: String,
    val fileId: String = "",
    val url: String = ""
)

data class FileResponse(
    val _id: String,
    val fileId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val uploadedBy: String,
    val uploadedAt: Long,
    val url: String = ""
)

data class DeleteFileResponse(
    val success: Boolean,
    val message: String
)

// Profile Image Models
data class ProfileImageUploadRequest(
    val imageData: String, // Base64 encoded
    val imageType: String = "image/jpeg"
)

data class ProfileImageResponse(
    val success: Boolean,
    val message: String = "",
    val imageUrl: String = ""
)

data class DeleteProfileImageResponse(
    val success: Boolean,
    val message: String
)

// User Profile Update Models
data class UpdateUserProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val status: String? = null,
    val privacyLevel: String? = null,
    val tags: List<String>? = null,
    val profilePicture: String? = null
)

data class PrivacySettingsRequest(
    val allowMessages: Boolean = true,
    val allowCalls: Boolean = true,
    val lastSeenVisible: Boolean = true,
    val blockedUsers: List<String> = emptyList()
)

data class PrivacySettingsResponse(
    val success: Boolean,
    val message: String,
    val settings: PrivacySettings? = null
)

data class PrivacySettings(
    val allowMessages: Boolean = true,
    val allowCalls: Boolean = true,
    val lastSeenVisible: Boolean = true,
    val blockedUsers: List<String> = emptyList()
)

// Call Models Extensions
data class CallCandidateRequest(
    val candidate: String,
    val sdpMLineIndex: Int = 0,
    val sdpMid: String = ""
)

data class RejectCallRequest(
    val reason: String = "declined"
)

data class DeleteCallResponse(
    val success: Boolean,
    val message: String
)

// Notification Models
data class SendFcmNotificationRequest(
    val recipientId: String,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap()
)

data class NotificationResponse(
    val success: Boolean,
    val message: String,
    val notificationId: String = ""
)

data class NotificationSubscriptionRequest(
    val topics: List<String> = emptyList(),
    val enabled: Boolean = true
)

data class NotificationUnsubscriptionRequest(
    val topics: List<String> = emptyList()
)

// Encryption Key Models
data class KeyExchangeRequest(
    val publicKey: String,
    val keyType: String = "RSA"
)

data class KeyExchangeResponse(
    val success: Boolean,
    val message: String,
    val peerPublicKey: String = ""
)

data class PublicKeyResponse(
    val success: Boolean,
    val message: String = "",
    val userId: String = "",
    val publicKey: String = ""
)

// Health Check Models
data class HealthCheckResponse(
    val status: String,
    val timestamp: Long = 0,
    val uptime: Long = 0,
    val version: String = ""
)
// Refresh Token Models for 30-Day Remember Me
data class RefreshTokenRequest(
    val refreshToken: String
)

data class RefreshTokenResponse(
    val success: Boolean,
    val message: String = "",
    val token: String = "",
    val refreshToken: String = "",
    val rememberMeEnabled: Boolean = false,
    val tokenExpiry: String = "24h",
    val refreshTokenExpiry: String = "30d"
)

// Channel Models
data class CreateChannelRequestDto(
    val channelName: String,
    val channelDescription: String? = null
)

data class SubscribeChannelRequestDto(
    val channelId: String = ""
)

data class UnsubscribeChannelRequestDto(
    val channelId: String = ""
)

data class PostAnnouncementRequestDto(
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PinMessageRequestDto(
    val messageId: String,
    val channelId: String
)

data class ChannelResponse(
    val channelId: String,
    val channelName: String,
    val channelDescription: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val subscriberCount: Int = 0
)

data class ChannelMessageResponse(
    val messageId: String,
    val channelId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isPinned: Boolean = false
)

// Delete History Models
data class DeleteHistoryRequestDto(
    val targetUserId: String,
    val chatId: String,
    val deletionType: String // "one_side" or "both_sides"
)

data class DeleteHistoryResponseDto(
    val success: Boolean,
    val message: String,
    val deletedCount: Int = 0
)

// Group Models
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val members: List<String> = emptyList()
)

data class AddGroupMemberRequest(
    val userId: String,
    val username: String
)

data class LeaveGroupRequest(
    val groupId: String,
    val userId: String
)

data class SendGroupMessageRequest(
    val messageId: String,
    val groupId: String,
    val content: String,
    val timestamp: Long
)

data class GroupResponse(
    val success: Boolean = false,
    val groupId: String = "",
    val inviteLink: String? = null
)

// Media Models
data class MediaUploadRequest(
    val fileName: String,
    val mediaType: String,
    val mimeType: String,
    val messageId: String,
    val chatId: String,
    val fileData: String // Base64 encoded
)

data class MediaUploadResponse(
    val success: Boolean,
    val mediaId: String = "",
    val mediaType: String = "",
    val uploadUrl: String = "",
    val fileName: String = "",
    val message: String = "",
    val error: String? = null
)

// Friend System Models
data class SendFriendRequestRequest(
    val recipientId: String
)

data class FriendRequest(
    val id: String,
    val senderId: String,
    val senderName: String,
    val avatar: String? = null,
    val timestamp: Long,
    // Compatibility with UI
    val requestId: String = id,
    val senderUsername: String = senderName,
    val avatarUrl: String? = avatar
)

data class Friend(
    val userId: String,
    val username: String,
    val displayName: String = "",
    val bio: String = "",
    val avatar: String? = null,
    val tags: List<String> = emptyList(),
    val status: String = "offline",
    val privacyLevel: String = "public",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0
)

// Media Download Models
data class MediaDownloadRequestDto(
    val mediaId: String,
    val reason: String = "User requested download"
)

data class MediaDownloadResponse(
    val success: Boolean,
    val downloadRequest: DownloadRequestData? = null,
    val message: String = ""
)

data class DownloadRequestData(
    val id: String,
    val mediaId: String,
    val requesterId: String,
    val ownerId: String,
    val reason: String,
    val status: String,
    val createdAt: String
)

data class MediaDownloadApprovalResponse(
    val success: Boolean,
    val downloadUrl: String = "",
    val expiresAt: String = "",
    val approvedAt: String = "",
    val message: String = ""
)

data class MediaDownloadDenialResponse(
    val success: Boolean,
    val message: String = ""
)

// Media Attachment Model
data class MediaAttachment(
    val id: String,
    val mediaId: String,
    val fileName: String,
    val fileType: String,  // "image", "video", "document", "audio"
    val mimeType: String,
    val fileSize: Long,
    val encryptedHash: String,
    val uploadedAt: Long,
    val uploadedBy: String
)

// Reaction Models
data class AddReactionRequest(
    val emoji: String
)

data class ReactionResponse(
    val success: Boolean,
    val messageId: String = "",
    val emoji: String = "",
    val reactions: Map<String, List<String>> = emptyMap()
)

// ✅ NEW: Group Picture Models
data class GroupPictureResponse(
    val success: Boolean,
    val message: String,
    val pictureUrl: String? = null,
    val fileId: String? = null
)

// ✅ NEW: Democratic Voting Models
data class ClearHistoryVoteResponse(
    val success: Boolean,
    val voteId: String,
    val requiredVotes: Int,
    val memberCount: Int,
    val message: String
)

data class CastVoteRequest(
    val vote: String  // "yes" or "no"
)

data class CastVoteResponse(
    val success: Boolean,
    val voteRecorded: Boolean,
    val yesVotes: Int,
    val noVotes: Int,
    val requiredVotes: Int,
    val result: String? = null,  // "passed", "failed", or null if still voting
    val message: String
)

// Version Check Models
data class VersionInfoResponse(
    val latestVersion: String,
    val latestVersionCode: Int,
    val minimumVersion: String,
    val forceUpdate: Boolean,
    val downloadUrl: String,
    val releaseNotes: String,
    val message: String? = null,
    val shouldUpdate: Boolean? = null,
    val compatible: Boolean? = null,
    val updateId: String? = null
)