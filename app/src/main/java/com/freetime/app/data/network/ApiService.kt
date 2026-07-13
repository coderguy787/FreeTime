package com.freetime.app.data.network

import retrofit2.Response
import retrofit2.http.*
import com.freetime.app.data.models.SetupAuthenticatorResponse
import com.freetime.app.data.models.VerifyAuthenticatorRequest
import com.freetime.app.data.models.VerifyAuthenticatorResponse
import com.freetime.app.data.models.VerifyLoginTotpRequest
import com.freetime.app.data.models.VerifyLoginTotpResponse
import com.freetime.app.data.models.VerifyLoginEmailCodeRequest
import com.freetime.app.data.models.VerifyLoginEmailCodeResponse
import com.freetime.app.data.models.SendVerificationEmailResponse
import com.freetime.app.data.models.MediaDownloadResponse
import com.freetime.app.data.models.MediaDownloadApprovalResponse
import com.freetime.app.data.models.MediaDownloadDenialResponse
import com.freetime.app.data.models.MediaDownloadRequestDto
import com.freetime.app.data.models.MediaUploadResponse
import okhttp3.MultipartBody

interface ApiService {
    
    // Authentication
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("api/signup")
    suspend fun signup(@Body request: SignUpRequest): Response<SignUpResponse>
    
    // 2FA Authentication
    @POST("api/setup-authenticator")
    suspend fun setupAuthenticator(
        @Header("Authorization") token: String
    ): Response<SetupAuthenticatorResponse>
    
    @POST("api/verify-authenticator")
    suspend fun verifyAuthenticator(
        @Body request: VerifyAuthenticatorRequest,
        @Header("Authorization") token: String
    ): Response<VerifyAuthenticatorResponse>
    
    @POST("api/verify-login-totp")
    suspend fun verifyLoginTotp(
        @Header("Authorization") token: String,
        @Body request: VerifyLoginTotpRequest
    ): Response<VerifyLoginTotpResponse>
    
    @POST("api/verify-login-email-code")
    suspend fun verifyLoginEmailCode(
        @Body request: VerifyLoginEmailCodeRequest,
        @Header("Authorization") token: String
    ): Response<VerifyLoginEmailCodeResponse>
    
    @POST("api/send-login-verification-email")
    suspend fun sendLoginVerificationEmail(
        @Header("Authorization") token: String
    ): Response<SendVerificationEmailResponse>
    
    @POST("api/refresh-token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<RefreshTokenResponse>
    
    @POST("api/device/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<DeviceRegisterResponse>
    
    @GET("api/verify")
    suspend fun verifyToken(@Header("Authorization") token: String): Response<VerifyResponse>
    
    // User Management
    @GET("api/users")
    suspend fun getUsers(@Header("Authorization") token: String): Response<List<UserResponse>>
    
    @GET("api/users/{userId}")
    suspend fun getUser(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<UserResponse>
    
    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body request: UpdateUserRequest,
        @Header("Authorization") token: String
    ): Response<UserResponse>
    
    @PUT("api/users/{userId}/profile")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body request: UpdateUserProfileRequest,
        @Header("Authorization") token: String
    ): Response<UserResponse>
    
    @PUT("api/users/{userId}/privacy")
    suspend fun updatePrivacySettings(
        @Path("userId") userId: String,
        @Body request: PrivacySettingsRequest,
        @Header("Authorization") token: String
    ): Response<PrivacySettingsResponse>
    
    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Header("Authorization") token: String
    ): Response<List<UserResponse>>
    
    // Messages
    @GET("api/messages/{recipientId}")
    suspend fun getMessages(
        @Path("recipientId") recipientId: String,
        @Header("Authorization") token: String
    ): Response<List<MessageResponse>>
    
    @POST("api/messages")
    suspend fun sendMessage(
        @Body request: SendMessageRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>
    
    // Calls
    @POST("api/calls/initiate")
    suspend fun initiateCall(
        @Body request: InitiateCallRequest,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @POST("api/calls/{callId}/answer")
    suspend fun answerCall(
        @Path("callId") callId: String,
        @Body request: AnswerCallRequest,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @POST("api/calls/{callId}/candidate")
    suspend fun addCallCandidate(
        @Path("callId") callId: String,
        @Body request: CallCandidateRequest,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @PUT("api/calls/{callId}/status")
    suspend fun updateCallStatus(
        @Path("callId") callId: String,
        @Body request: UpdateCallStatusRequest,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @GET("api/calls/{callId}")
    suspend fun getCall(
        @Path("callId") callId: String,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @POST("api/calls/{callId}/reject")
    suspend fun rejectCall(
        @Path("callId") callId: String,
        @Body request: RejectCallRequest,
        @Header("Authorization") token: String
    ): Response<CallResponse>
    
    @DELETE("api/calls/{callId}")
    suspend fun deleteCall(
        @Path("callId") callId: String,
        @Header("Authorization") token: String
    ): Response<DeleteCallResponse>
    
    @GET("api/calls/history")
    suspend fun getCallHistory(
        @Header("Authorization") token: String
    ): Response<List<CallResponse>>
    
    // Files
    @POST("api/files/upload")
    suspend fun uploadFile(
        @Body request: FileUploadRequest,
        @Header("Authorization") token: String
    ): Response<FileUploadResponse>
    
    @GET("api/files/{fileId}")
    suspend fun getFile(
        @Path("fileId") fileId: String,
        @Header("Authorization") token: String
    ): Response<FileResponse>
    
    @DELETE("api/files/{fileId}")
    suspend fun deleteFile(
        @Path("fileId") fileId: String,
        @Header("Authorization") token: String
    ): Response<DeleteFileResponse>
    
    @GET("api/chat/{recipientId}/files")
    suspend fun getChatFiles(
        @Path("recipientId") recipientId: String,
        @Header("Authorization") token: String
    ): Response<List<FileResponse>>
    
    // Profile Image
    @POST("api/users/{userId}/profile-image")
    suspend fun uploadProfileImage(
        @Path("userId") userId: String,
        @Body request: ProfileImageUploadRequest,
        @Header("Authorization") token: String
    ): Response<ProfileImageResponse>
    
    @GET("api/users/{userId}/profile-image")
    suspend fun getProfileImage(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<ProfileImageResponse>
    
    @DELETE("api/users/{userId}/profile-image")
    suspend fun deleteProfileImage(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<DeleteProfileImageResponse>
    
    // FCM Token
    @POST("api/users/{userId}/fcm-token")
    suspend fun registerFcmToken(
        @Path("userId") userId: String,
        @Body request: FcmTokenRequest,
        @Header("Authorization") token: String
    ): Response<FcmTokenResponse>
    
    @DELETE("api/users/{userId}/fcm-token")
    suspend fun removeFcmToken(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<FcmTokenResponse>
    
    // Notifications
    @POST("api/notifications/send-fcm")
    suspend fun sendFcmNotification(
        @Body request: SendFcmNotificationRequest,
        @Header("Authorization") token: String
    ): Response<NotificationResponse>
    
    @POST("api/notifications/subscribe")
    suspend fun subscribeToNotifications(
        @Body request: NotificationSubscriptionRequest,
        @Header("Authorization") token: String
    ): Response<NotificationResponse>
    
    @POST("api/notifications/unsubscribe")
    suspend fun unsubscribeFromNotifications(
        @Body request: NotificationUnsubscriptionRequest,
        @Header("Authorization") token: String
    ): Response<NotificationResponse>
    
    @GET("api/notifications/history")
    suspend fun getNotificationHistory(
        @Header("Authorization") token: String
    ): Response<List<NotificationResponse>>
    
    // Encryption Keys
    @POST("api/keys/exchange/{peerId}")
    suspend fun exchangeEncryptionKeys(
        @Path("peerId") peerId: String,
        @Body request: KeyExchangeRequest,
        @Header("Authorization") token: String
    ): Response<KeyExchangeResponse>
    
    @GET("api/keys/{userId}")
    suspend fun getUserPublicKey(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<PublicKeyResponse>
    
    // Health Check
    @GET("api/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

    // Channels
    @POST("api/channels")
    suspend fun createChannel(
        @Body request: CreateChannelRequestDto,
        @Header("Authorization") token: String
    ): Response<ChannelResponse>

    @POST("api/channels/{channelId}/subscribe")
    suspend fun subscribeToChannel(
        @Path("channelId") channelId: String,
        @Body request: SubscribeChannelRequestDto,
        @Header("Authorization") token: String
    ): Response<ChannelResponse>

    @POST("api/channels/{channelId}/unsubscribe")
    suspend fun unsubscribeFromChannel(
        @Path("channelId") channelId: String,
        @Body request: UnsubscribeChannelRequestDto,
        @Header("Authorization") token: String
    ): Response<ChannelResponse>

    @POST("api/channels/{channelId}/messages")
    suspend fun postChannelAnnouncement(
        @Path("channelId") channelId: String,
        @Body request: PostAnnouncementRequestDto,
        @Header("Authorization") token: String
    ): Response<ChannelMessageResponse>

    @POST("api/channels/{channelId}/messages/{messageId}/pin")
    suspend fun pinChannelMessage(
        @Path("channelId") channelId: String,
        @Path("messageId") messageId: String,
        @Body request: PinMessageRequestDto,
        @Header("Authorization") token: String
    ): Response<ChannelMessageResponse>

    @GET("api/channels")
    suspend fun searchChannels(
        @Query("query") query: String,
        @Header("Authorization") token: String
    ): Response<List<ChannelResponse>>

    @GET("api/channels/featured")
    suspend fun getFeaturedChannels(
        @Header("Authorization") token: String
    ): Response<List<ChannelResponse>>

    // Delete History
    @DELETE("api/chat/{recipientId}/delete-history")
    suspend fun deleteHistoryWithUser(
        @Path("recipientId") recipientId: String,
        @Body request: DeleteHistoryRequestDto,
        @Header("Authorization") token: String
    ): Response<DeleteHistoryResponseDto>

    // Groups
    @POST("api/groups")
    suspend fun createGroup(
        @Body request: CreateGroupRequest,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    @POST("api/groups/{groupId}/members")
    suspend fun addGroupMember(
        @Path("groupId") groupId: String,
        @Body request: AddGroupMemberRequest,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    @DELETE("api/groups/{groupId}/members/{userId}")
    suspend fun removeGroupMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    @POST("api/groups/{groupId}/leave")
    suspend fun leaveGroup(
        @Path("groupId") groupId: String,
        @Body request: LeaveGroupRequest,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    @POST("api/groups/{groupId}/messages")
    suspend fun sendGroupMessage(
        @Path("groupId") groupId: String,
        @Body request: SendGroupMessageRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @DELETE("api/groups/{groupId}")
    suspend fun deleteGroup(
        @Path("groupId") groupId: String,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    @DELETE("api/groups/{groupId}/messages")
    suspend fun deleteGroupMessages(
        @Path("groupId") groupId: String,
        @Header("Authorization") token: String
    ): Response<GroupResponse>

    // ✅ NEW: Group picture upload
    @Multipart
    @POST("api/groups/{groupId}/picture")
    suspend fun uploadGroupPicture(
        @Path("groupId") groupId: String,
        @Part picture: MultipartBody.Part,
        @Header("Authorization") token: String
    ): Response<GroupPictureResponse>

    // ✅ NEW: Democratic vote to clear history
    @POST("api/groups/{groupId}/clear-history-vote")
    suspend fun initiateClearHistoryVote(
        @Path("groupId") groupId: String,
        @Header("Authorization") token: String
    ): Response<ClearHistoryVoteResponse>

    // ✅ NEW: Cast vote on clear history
    @POST("api/groups/{groupId}/clear-history-vote/{voteId}/vote")
    suspend fun castClearHistoryVote(
        @Path("groupId") groupId: String,
        @Path("voteId") voteId: String,
        @Body request: CastVoteRequest,
        @Header("Authorization") token: String
    ): Response<CastVoteResponse>

    // Message Reactions
    @POST("api/messages/{messageId}/reactions")
    suspend fun addReaction(
        @Path("messageId") messageId: String,
        @Body request: AddReactionRequest,
        @Header("Authorization") token: String
    ): Response<ReactionResponse>

    @DELETE("api/messages/{messageId}/reactions/{emoji}")
    suspend fun removeReaction(
        @Path("messageId") messageId: String,
        @Path("emoji") emoji: String,
        @Header("Authorization") token: String
    ): Response<ReactionResponse>

    // Friend System
    @POST("api/friends/request")
    suspend fun sendFriendRequest(
        @Body request: SendFriendRequestRequest,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("api/friends/requests/pending")
    suspend fun getPendingFriendRequests(
        @Header("Authorization") token: String
    ): Response<List<FriendRequest>>

    @POST("api/friends/requests/{senderId}/accept")
    suspend fun acceptFriendRequest(
        @Header("Authorization") token: String,
        @Path("senderId") senderId: String
    ): Response<Unit>

    @DELETE("api/friends/requests/{senderId}")
    suspend fun rejectFriendRequest(
        @Header("Authorization") token: String,
        @Path("senderId") senderId: String
    ): Response<Unit>

    @GET("api/friends")
    suspend fun getFriendsList(
        @Header("Authorization") token: String
    ): Response<List<Friend>>

    @DELETE("api/friends/{friendId}")
    suspend fun removeFriend(
        @Header("Authorization") token: String,
        @Path("friendId") friendId: String
    ): Response<Unit>

    @POST("api/friends/{userId}/block")
    suspend fun blockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): Response<Unit>

    // Media Download Endpoints
    @POST("api/media/{mediaId}/download-request")
    suspend fun requestMediaDownload(
        @Path("mediaId") mediaId: String,
        @Body request: MediaDownloadRequestDto,
        @Header("Authorization") token: String
    ): Response<MediaDownloadResponse>

    @PUT("api/media/download-request/{requestId}/approve")
    suspend fun approveMediaDownload(
        @Path("requestId") requestId: String,
        @Header("Authorization") token: String
    ): Response<MediaDownloadApprovalResponse>

    @PUT("api/media/download-request/{requestId}/deny")
    suspend fun denyMediaDownload(
        @Path("requestId") requestId: String,
        @Header("Authorization") token: String
    ): Response<MediaDownloadDenialResponse>

    // Media Upload for Chat (private and group)
    @POST("api/media/upload")
    @Multipart
    suspend fun uploadMediaToChat(
        @Part("recipientId") recipientId: String? = null,
        @Part("groupId") groupId: String? = null,
        @Part media: MultipartBody.Part,
        @Header("Authorization") token: String
    ): Response<MediaUploadResponse>

    // Download media file
    @GET("api/media/{mediaId}/download")
    suspend fun downloadMedia(
        @Path("mediaId") mediaId: String,
        @Header("Authorization") token: String
    ): Response<okhttp3.ResponseBody>

    // Persistent Media Download Requests
    @POST("api/media/request-download")
    suspend fun requestMediaDownload(
        @Header("Authorization") token: String,
        @Body request: BatchMediaDownloadRequest
    ): Response<Unit>

    @POST("api/media/approve-download")
    suspend fun approveMediaDownload(
        @Header("Authorization") token: String,
        @Body request: MediaApprovalRequest
    ): Response<Unit>

    // Version Check
    @GET("api/app/version-info")
    suspend fun getVersionInfo(): Response<VersionInfoResponse>

    // App Update Acknowledgement
    @POST("api/app/update/acknowledge")
    suspend fun acknowledgeUpdate(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Unit>
}

// Data Transfer Objects for new endpoints
data class BatchMediaDownloadRequest(
    val mediaIds: List<String>, 
    val recipientId: String,
    val mediaKeys: Map<String, String>? = null // mediaId -> encryptionKey
)
data class MediaApprovalRequest(val mediaId: String, val requesterId: String, val status: String)
