package com.freetime.app.data.models

// HTTP Response Wrapper
data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

// Auth Models
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String = "",
    val force: Boolean = false
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userId: String = "",
    val token: String = "",
    val user: UserResponse? = null,
    val requiresTwoFactor: Boolean = false,
    val tempToken: String? = null,
    val twoFaMethod: String? = null,
    val nextStep: String? = null,
    val setupRequired: Boolean = false,
    val error: String? = null
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
data class UpdateUserRequest(
    val email: String? = null,
    val status: String? = null,
    val profilePicture: String? = null
)

// Message Models
data class MessageResponse(
    val _id: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long,
    val read: Boolean = false,
    val type: String = "text",
    val attachments: List<Map<String, Any>> = emptyList(),
    val senderAvatar: String? = null,
    val senderName: String? = null,
    val mediaId: String? = null,
    val mediaType: String? = null,
    val mediaName: String? = null,
    // ✅ NEW: Color-coding fields from backend
    val senderDisplayName: String? = null,
    val senderTags: List<String> = emptyList(),
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false,
    val senderRole: String? = null,
    // ✅ NEW: Reply support fields
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    // ✅ NEW: Reaction support
    val reactions: Map<String, List<String>> = emptyMap(),
    // ✅ NEW: Message ID alternatives
    val id: String? = null
)

data class SendMessageRequest(
    val recipientId: String,
    val content: String,
    val type: String = "text"
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
    val answer: String
)

data class UpdateCallStatusRequest(
    val status: String
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

// Signup Models
data class SignUpRequest(
    val username: String,
    val email: String,
    val displayName: String,
    val password: String,
    val confirmPassword: String,
    val twoFaMethod: String = "authenticator"
)

data class SignUpResponse(
    val success: Boolean,
    val message: String,
    val userId: String? = null,
    val tempToken: String? = null,
    val twoFaMethod: String? = null,
    val nextStep: String? = null,
    val instructions: String? = null,
    val error: String? = null
)

// 2FA Models
data class SetupAuthenticatorResponse(
    val success: Boolean,
    val message: String,
    val qrCode: String? = null,
    val secret: String? = null,
    val backupCodes: List<String> = emptyList(),
    val nextStep: String? = null,
    val error: String? = null
)

data class VerifyAuthenticatorRequest(
    val totpCode: String
)

data class VerifyAuthenticatorResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserResponse? = null,
    val error: String? = null
)

data class VerifyLoginTotpRequest(
    val totpCode: String,
    val rememberMe: Boolean = false,
    val force: Boolean = false
)

data class VerifyLoginTotpResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val refreshToken: String? = null,
    val rememberMeEnabled: Boolean = false,
    val tokenExpiry: String = "24h",
    val user: UserResponse? = null,
    val error: String? = null,
    val attemptsRemaining: Int = 5
)

data class VerifyLoginEmailCodeRequest(
    val emailCode: String
)

data class VerifyLoginEmailCodeResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserResponse? = null,
    val error: String? = null
)

data class SendVerificationEmailResponse(
    val success: Boolean,
    val message: String,
    val email: String? = null,
    val error: String? = null
)

// Updated UserResponse with 2FA fields
data class UserResponse(
    val _id: String = "",
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val name: String = "",
    val displayName: String = "",
    val status: String = "offline",
    val lastSeen: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val role: String = "user",
    val twoFactorAuth: TwoFactorAuth? = null,
    val accountVerified: Boolean = false
)

data class TwoFactorAuth(
    val enabled: Boolean = false,
    val method: String = "",
    val mandatorySetup: Boolean = false,
    val setupRequired: Boolean = false,
    val secret: String? = null,
    val verificationMethod: String? = null,
    val verificationCompletedAt: Long? = null,
    val backupCodesUsed: List<String> = emptyList()
)

// Channel-related Models
data class ChannelMember(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String, // "admin", "moderator", "member"
    val isAdmin: Boolean = false,
    val isMuted: Boolean = false,
    val isActive: Boolean = false,
    val joinedAt: Long = 0
)

data class Channel(
    val id: String,
    val name: String,
    val description: String = "",
    val memberCount: Int = 0,
    val createdAt: Long = 0,
    val isPrivate: Boolean = false
)

// Voting Models
data class GroupVote(
    val id: String = "",
    val voteId: String = "",
    val groupId: String = "",
    val question: String = "",
    val voterId: String = "",
    val voteType: String = "", // "approve", "reject"
    val timestamp: Long = 0L,
    val reason: String? = null,
    val voteCount: Int = 0,
    val totalMembers: Int = 0,
    val options: List<VoteOption> = emptyList(),
    val completedAt: Long? = null
)

data class VoteOption(
    val id: String,
    val text: String,
    val voteCount: Int = 0
)

// Media Models
data class MediaEntity(
    val id: String,
    val type: String, // "image", "video", "document"
    val url: String,
    val thumbnailUrl: String? = null,
    val uploadedAt: Long = 0,
    val uploadedBy: String = "",
    val fileSize: Long = 0,
    val fileName: String = ""
)

data class MediaDownloadRequestDto(
    val mediaId: String = "",
    val reason: String? = null
)

data class DownloadRequest(
    val id: String = "",
    val mediaId: String = "",
    val status: String = ""
)

data class MediaDownloadResponse(
    val success: Boolean = false,
    val requestId: String = "",
    val downloadRequest: DownloadRequest? = null,
    val status: String = "",
    val message: String = ""
)

data class MediaDownloadApprovalResponse(
    val success: Boolean = false,
    val downloadUrl: String = "",
    val downloadLink: String = "",
    val message: String = ""
)

data class MediaDownloadDenialResponse(
    val success: Boolean = false,
    val message: String = ""
)

data class MediaUploadResponse(
    val success: Boolean = false,
    val mediaId: String = "",
    val message: String = "",
    val error: String? = null
)
