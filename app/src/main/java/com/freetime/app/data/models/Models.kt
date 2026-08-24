package com.freetime.app.data.models

data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

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

data class UpdateUserRequest(
    val email: String? = null,
    val status: String? = null,
    val profilePicture: String? = null
)

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
    val senderDisplayName: String? = null,
    val senderTags: List<String> = emptyList(),
    val senderIsAdmin: Boolean = false,
    val senderIsModerator: Boolean = false,
    val senderRole: String? = null,
    val replyToMessageId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val id: String? = null
)

data class SendMessageRequest(
    val recipientId: String,
    val content: String,
    val type: String = "text"
)

data class FcmTokenRequest(
    val fcmToken: String,
    val deviceId: String = ""
)

data class FcmTokenResponse(
    val success: Boolean,
    val message: String
)

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

// accept all id field spellings from the api
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

data class ChannelMember(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
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

data class GroupVote(
    val id: String = "",
    val voteId: String = "",
    val groupId: String = "",
    val question: String = "",
    val voterId: String = "",
    val voteType: String = "",
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

data class MediaEntity(
    val id: String,
    val type: String,
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
