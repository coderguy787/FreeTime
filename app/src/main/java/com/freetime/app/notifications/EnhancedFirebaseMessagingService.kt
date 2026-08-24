package com.freetime.app.notifications

import android.util.Log
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.database.MessageEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// receives incoming push notifications
class EnhancedFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, " FCM Message received from: ${remoteMessage.from}")
        Log.d(TAG, " - Message ID: ${remoteMessage.messageId}")
        Log.d(TAG, " - Data: ${remoteMessage.data}")
        Log.d(TAG, " - Priority: ${remoteMessage.priority}")
        if (remoteMessage.notification != null) {
            Log.d(TAG, " - Notification: ${remoteMessage.notification?.title} / ${remoteMessage.notification?.body}")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, " - Has POST_NOTIFICATIONS permission: $hasPermission")
            if (!hasPermission) {
                Log.w(TAG, " POST_NOTIFICATIONS permission not granted - notifications may not display")
            }
        }

        try {
            if (!ensureNotificationChannelsExist()) {
                Log.e(TAG, " Failed to create notification channels - notifications may fail")
            }

            InAppNotificationStore.init(this)

            if (remoteMessage.data.isNotEmpty()) {
                try {
                    handleDataMessage(remoteMessage.data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling data message: ${e.message}", e)
                }
            } else if (remoteMessage.notification != null) {
                try {
                    handleNotificationMessage(remoteMessage)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling notification message: ${e.message}", e)
                }
            } else {
                Log.w(TAG, " Received FCM message with NO data and NO notification block")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onMessageReceived: ${e.message}", e)
        }
    }

    private fun ensureNotificationChannelsExist(): Boolean {
        return try {
            NotificationHelper.createNotificationChannels(this)
            true
        } catch (e: Exception) {
            Log.e(TAG, " Failed to create notification channels: ${e.message}", e)
            false
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        try {
            val messageType = data["type"] ?: return

            Log.d(TAG, "Processing message type: $messageType")

            when (messageType) {
                "message", "newMessage", "chatMessage", "chat_message" -> handleNewMessage(data)
                "groupMessage", "group_message" -> handleGroupMessage(data)
                "channel_message", "channelMessage" -> handleChannelMessage(data)

                "friendRequest", "friend_request" -> handleFriendRequest(data)
                "friendAccepted", "friend_accepted", "friendRequestAccepted" -> handleFriendAccepted(data)
                "groupInvite", "group_invite", "groupVote", "group_vote" -> handleGroupInvite(data)
                "groupVoting", "group_voting" -> handleGroupVoting(data)

                "groupUpdated", "group_updated" -> handleGroupUpdated(data)
                "groupPictureUpdated", "group_picture_updated" -> handleGroupPictureUpdated(data)
                "group.member.promoted", "groupMemberPromoted", "member_promoted" -> handleGroupMemberPromoted(data)
                "group.member.joined", "groupMemberJoined", "member_joined" -> handleGroupMemberJoined(data)

                "server_shutdown" -> handleServerShutdown(data)

                "mediaDownloadRequest", "media_download_request", "media_req", "media.download.requested" -> handleMediaDownloadRequest(data)
                "mediaApproved", "media_approved", "media.download.approved" -> handleMediaApproved(data)
                "mediaDenied", "media_denied", "media.download.denied" -> handleMediaDenied(data)

                else -> {
                    Log.w(TAG, "Unknown message type: $messageType — using basic notification")
                    showBasicNotification(data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleDataMessage: ${e.message}", e)
            try {
                showBasicNotification(data)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to show basic notification: ${e2.message}")
            }
        }
    }

    private fun handleNotificationMessage(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification ?: return
        val title = notification.title ?: "FreeTime"
        val body = notification.body ?: "New notification"

        Log.d(TAG, "Standard Notification: $title - $body")

        NotificationHelper.showMessageNotification(
            context = this,
            senderName = title,
            messagePreview = body,
            senderId = "system"
        )
    }

    private fun handleNewMessage(data: Map<String, String>) {
        val senderId = data["senderId"] ?: data["sender_id"] ?: return
        val chatId = data["chatId"] ?: senderId
        val senderName = data["senderName"] ?: data["sender_name"] ?: "Unknown"
        val messageText = data["messageContent"] ?: data["message_preview"] ?: data["message"] ?: data["content"] ?: "[Message]"
        val messageId = data["messageId"] ?: data["message_id"] ?: java.util.UUID.randomUUID().toString()
        val senderAvatar = data["senderAvatar"] ?: data["sender_avatar"] ?: data["avatar"]

        Log.d(TAG, " New message from $senderName")

        if (isDuplicateMessage(messageId)) {
            Log.d(TAG, " Skipping duplicate message: $messageId")
            return
        }

        scope.launch {
            try {
                val database = com.freetime.app.data.local.database.FreeTimeDatabase.getInstance(this@EnhancedFirebaseMessagingService)
                val encryptionManager = com.freetime.app.data.local.encryption.EncryptionManager(this@EnhancedFirebaseMessagingService)

                val encryptedContent = encryptionManager.encrypt(messageText, "${chatId}:${senderId}")

                val entity = com.freetime.app.data.local.database.MessageEntity(
                    messageId = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    contentEncrypted = encryptedContent,
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    syncState = "synced"
                )
                database.messageDao().insertMessage(entity)
                Log.d(TAG, " Message persisted to DB: $messageId")

                val prefs = com.freetime.app.data.local.SharedPreferencesHelper(this@EnhancedFirebaseMessagingService)

                if (prefs.isUserMuted(senderId)) {
                    Log.d(TAG, " Muted user $senderId - skipping message notification")
                    return@launch
                }

                if (!prefs.isNotifyMessagesEnabled()) {
                    Log.d(TAG, " Message notifications disabled - skipping")
                    return@launch
                }

                if (NotificationHelper.currentActiveChatId == senderId) {
                    Log.d(TAG, " Skipping message notification - user viewing this chat")
                    return@launch
                }

                try {
                    NotificationHelper.showMessageNotification(
                        context = this@EnhancedFirebaseMessagingService,
                        senderName = senderName,
                        messagePreview = messageText,
                        senderId = senderId,
                        senderAvatarUrl = senderAvatar
                    )
                    Log.d(TAG, " Message notification shown for $senderName")
                } catch (e: Exception) {
                    Log.e(TAG, " Failed to show message notification: ${e.message}", e)
                }

                trackMessageAsProcessed(messageId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle message notification", e)
            }
        }
    }

    private fun handleGroupMessage(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val senderId = data["senderId"] ?: data["sender_id"] ?: return
        val senderName = data["senderName"] ?: data["sender_name"] ?: "Unknown"
        val messageText = data["messageContent"] ?: data["message_preview"] ?: data["message"] ?: data["content"] ?: "[Message]"
        val messageId = data["messageId"] ?: data["message_id"]
        val senderAvatar = data["senderAvatar"] ?: data["sender_avatar"] ?: data["avatar"]

        Log.d(TAG, " Group message from $senderName in $groupName")

        if (isDuplicateMessage(messageId)) return

        scope.launch {
            try {
                val prefs = com.freetime.app.data.local.SharedPreferencesHelper(this@EnhancedFirebaseMessagingService)

                if (!prefs.isNotifyGroupUpdatesEnabled()) {
                    Log.d(TAG, " Group notifications disabled - skipping")
                    return@launch
                }

                if (prefs.isGroupMuted(groupId)) {
                    Log.d(TAG, " Group $groupId is muted - skipping")
                    return@launch
                }

                if (senderId.isNotEmpty() && prefs.isUserMuted(senderId)) {
                    Log.d(TAG, " Sender $senderId is muted - skipping group message")
                    return@launch
                }

                if (NotificationHelper.currentActiveChatId == groupId) {
                    Log.d(TAG, " Skipping group message notification - user viewing this group")
                    return@launch
                }

                NotificationHelper.showGroupMessageNotification(
                    context = this@EnhancedFirebaseMessagingService,
                    groupName = groupName,
                    senderName = senderName,
                    messagePreview = messageText,
                    groupId = groupId,
                    senderId = senderId,
                    senderAvatarUrl = senderAvatar
                )

                InAppNotificationStore.addNotification(
                    InAppNotification(
                        type = "groupMessage",
                        title = "$senderName in $groupName",
                        description = messageText.take(100),
                        senderId = senderId
                    )
                )

                trackMessageAsProcessed(messageId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle group message", e)
            }
        }
    }

    private fun handleChannelMessage(data: Map<String, String>) {
        val channelId = data["channelId"] ?: data["channel_id"] ?: return
        val channelName = data["channelName"] ?: data["channel_name"] ?: "Channel"
        val senderName = data["senderName"] ?: data["sender_name"] ?: "Unknown"
        val messageText = data["messageContent"] ?: data["message_preview"] ?: data["message"] ?: "[Message]"
        val messageId = data["messageId"] ?: data["message_id"]

        Log.d(TAG, " Channel message in $channelName from $senderName")

        if (isDuplicateMessage(messageId)) return

        scope.launch {
            NotificationHelper.showChannelMessageNotification(
                context = this@EnhancedFirebaseMessagingService,
                channelName = channelName,
                senderName = senderName,
                messagePreview = messageText,
                channelId = channelId
            )

            InAppNotificationStore.addNotification(
                InAppNotification(
                    type = "channelMessage",
                    title = channelName,
                    description = "$senderName: ${messageText.take(100)}",
                    senderId = channelId
                )
            )

            trackMessageAsProcessed(messageId)
        }
    }

    private fun handleFriendRequest(data: Map<String, String>) {
        val userId = data["senderId"] ?: data["sender_id"] ?: data["fromUserId"] ?: return
        val userName = data["senderName"] ?: data["sender_name"] ?: data["fromUsername"] ?: "Unknown"
        val requestId = data["requestId"] ?: data["request_id"] ?: ""

        val prefs = com.freetime.app.data.local.SharedPreferencesHelper(this)
        if (!prefs.isNotifyFriendRequestsEnabled()) return

        NotificationHelper.showFriendRequestNotification(this, userName, userId, requestId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "friendRequest",
                title = userName,
                description = "Sent you a friend request",
                senderId = userId
            )
        )
    }

    private fun handleFriendAccepted(data: Map<String, String>) {
        val userId = data["userId"] ?: data["friendId"] ?: data["friend_id"] ?: return
        val userName = data["username"] ?: data["friendName"] ?: data["friend_name"] ?: "Unknown"

        NotificationHelper.showFriendRequestAcceptedNotification(this, userName, userId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "friendAccepted",
                title = userName,
                description = "Accepted your friend request!",
                senderId = userId
            )
        )
    }

    private fun handleGroupInvite(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val inviterName = data["inviterName"] ?: data["inviter_name"] ?: data["senderName"] ?: "Someone"
        val message = data["message"] ?: data["event_message"] ?: "$inviterName invited you to $groupName"
        val inviteId = data["inviteId"] ?: data["invite_id"] ?: ""

        NotificationHelper.showGroupInviteNotification(this, groupName, inviterName, groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupInvite",
                title = groupName,
                description = message,
                senderId = groupId,
                inviteId = inviteId
            )
        )
    }

    private fun handleGroupVoting(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val message = data["message"] ?: data["event_message"] ?: "New vote initiated"

        NotificationHelper.showGroupVotingNotification(this, groupName, message, groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupVote",
                title = groupName,
                description = message,
                senderId = groupId
            )
        )
    }

    private fun handleGroupUpdated(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val updateType = data["updateType"] ?: data["update_type"] ?: "updated"
        val message = data["message"] ?: data["event_message"] ?: "Group $updateType"

        Log.d(TAG, " Group updated: $groupName - $message")

        NotificationHelper.showGroupVotingNotification(this, groupName, message, groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupUpdated",
                title = groupName,
                description = message,
                senderId = groupId
            )
        )
    }

    private fun handleGroupPictureUpdated(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val updatedByName = data["updatedByName"] ?: data["updated_by_name"] ?: "Someone"
        val message = "$updatedByName updated the group picture"

        Log.d(TAG, " Group picture updated: $groupName")

        NotificationHelper.showGroupVotingNotification(this, groupName, message, groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupPictureUpdated",
                title = groupName,
                description = message,
                senderId = groupId
            )
        )
    }

    private fun handleServerShutdown(data: Map<String, String>) {
        val title = data["title"] ?: "FreeTime servers are offline"
        val body = data["body"] ?: "Only private text messages are available. Group chats and media are temporarily disabled."

        Log.d(TAG, " Server shutdown notification received: $title")

        NotificationHelper.showServerDownNotification(this)
    }

    private fun handleGroupMemberPromoted(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val memberName = data["memberName"] ?: data["member_name"] ?: "Member"

        Log.d(TAG, " Group member promoted: $memberName in $groupName")

        NotificationHelper.showGroupMemberActionNotification(this, groupName, memberName, "promoted", groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupMemberPromoted",
                title = groupName,
                description = "$memberName was promoted to group admin",
                senderId = groupId
            )
        )
    }

    private fun handleGroupMemberJoined(data: Map<String, String>) {
        val groupId = data["groupId"] ?: data["group_id"] ?: return
        val groupName = data["groupName"] ?: data["group_name"] ?: "Group"
        val memberName = data["memberName"] ?: data["member_name"] ?: "Member"

        Log.d(TAG, " Group member joined: $memberName in $groupName")

        NotificationHelper.showGroupMemberActionNotification(this, groupName, memberName, "joined", groupId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupMemberJoined",
                title = groupName,
                description = "$memberName joined the group",
                senderId = groupId
            )
        )
    }

    private fun handleMediaDownloadRequest(data: Map<String, String>) {
        val mediaId = data["mediaId"] ?: data["media_id"] ?: return
        val requesterId = data["requesterId"] ?: data["requester_id"] ?: return
        val requesterName = data["requesterName"] ?: data["requester_name"] ?: "Someone"

        NotificationHelper.showMediaDownloadRequestNotification(this, requesterName, mediaId)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "mediaDownloadRequest",
                title = "Download Request",
                description = "$requesterName wants to download your media",
                senderId = requesterId
            )
        )
    }

    private fun handleMediaApproved(data: Map<String, String>) {
        val mediaId = data["mediaId"] ?: data["media_id"] ?: return
        val mediaName = data["mediaName"] ?: data["media_name"] ?: "Media"
        val mediaKey = data["mediaKey"] ?: data["media_key"] ?: ""

        NotificationHelper.showMediaDownloadApprovedNotification(this, mediaName, mediaKey)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "mediaApproved",
                title = "Download Approved",
                description = "Your request for \"$mediaName\" was approved",
                senderId = mediaId
            )
        )
    }

    private fun handleMediaDenied(data: Map<String, String>) {
        val mediaName = data["mediaName"] ?: data["media_name"] ?: "Media"
        val reason = data["reason"]

        NotificationHelper.showMediaDownloadDeniedNotification(this, mediaName, reason)

        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "mediaDenied",
                title = "Download Denied",
                description = "Your request for \"$mediaName\" was denied",
                senderId = ""
            )
        )
    }

    private fun showBasicNotification(data: Map<String, String>) {
        val title = data["title"] ?: "FreeTime"
        val message = data["message"] ?: data["body"] ?: "New notification"
        NotificationHelper.showMessageNotification(this, title, message, "system")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, " FCM token refreshed")
        scope.launch {
            val prefs = getSharedPreferences("freetime_fcm", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("backup_fcm_token", token).apply()
            Log.d(TAG, " FCM token saved to local backup")

            registerFcmTokenWithRetry(token, maxRetries = 3)
        }
    }

    private suspend fun registerFcmTokenWithRetry(token: String, maxRetries: Int = 3) {
        var retryCount = 0
        while (retryCount < maxRetries) {
            try {
                val apiService = com.freetime.app.api.FreeTimeApiService(this)
                apiService.registerDeviceFcmToken(token)
                Log.d(TAG, " FCM token registered successfully")
                return
            } catch (e: Exception) {
                retryCount++
                if (retryCount < maxRetries) {
                    val delayMs = (Math.pow(2.0, (retryCount - 1).toDouble()) * 1000).toLong()
                    Log.w(TAG, " FCM token registration attempt $retryCount failed, retrying in ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                } else {
                    Log.e(TAG, " FCM token registration failed after $maxRetries attempts. Token saved in local backup: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "EnhancedFCM"
        private val recentMessageIds = mutableSetOf<String>()
        private val messageIdLock = ReentrantReadWriteLock()
        private const val MESSAGE_ID_RETENTION_MS = 30000L

        private fun isDuplicateMessage(messageId: String?): Boolean {
            if (messageId.isNullOrBlank()) return false
            return messageIdLock.read { messageId in recentMessageIds }
        }

        private fun trackMessageAsProcessed(messageId: String?) {
            if (messageId.isNullOrBlank()) return
            messageIdLock.write {
                recentMessageIds.add(messageId)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    messageIdLock.write { recentMessageIds.remove(messageId) }
                }, MESSAGE_ID_RETENTION_MS)
            }
        }

        fun getToken(onToken: (String) -> Unit) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Token: $token")
                    onToken(token)
                } else {
                    Log.e(TAG, "Failed to get FCM token", task.exception)
                }
            }
        }
    }
}
