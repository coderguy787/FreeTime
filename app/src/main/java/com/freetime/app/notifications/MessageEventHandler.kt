package com.freetime.app.notifications

import android.content.Context
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Message Event Handler
 * Listens to WebSocket events and triggers notifications
 * Integrates real-time messages with push notifications
 */
class MessageEventHandler(private val context: Context) {

    companion object {
        private const val TAG = "MessageEventHandler"
    }

    private val prefs = SharedPreferencesHelper(context)

    /**
     * Handle WebSocket events and show appropriate notifications
     */
    fun handleWebSocketEvent(event: WebSocketEvent) {
        try {
            when (event.type) {
                // Friend request events
                "friend.request.received" -> {
                    val data = event.data as? FriendRequestEventData ?: return
                    handleFriendRequestReceived(data)
                }

                "friend.request.accepted" -> {
                    val data = event.data as? FriendAcceptedEventData ?: return
                    handleFriendRequestAccepted(data)
                }

                // Group invitation events
                "group.invite.pending" -> {
                    val data = event.data as? GroupInvitePendingEventData ?: return
                    handleGroupInvitePending(data)
                }

                // Channel message event
                "channel.message.received" -> {
                    val data = event.data as? ChannelMessageEventData ?: return
                    handleChannelMessageReceived(data)
                }

                // Group voting events
                "group.vote.initiated" -> {
                    val data = event.data as? GroupVoteInitiatedEventData ?: return
                    handleGroupVoteInitiated(data)
                }

                "group.vote.cast" -> {
                    val data = event.data as? GroupVoteCastEventData ?: return
                    handleGroupVoteCast(data)
                }

                // Media events
                "media.download.approved" -> {
                    val data = event.data as? MediaDownloadApprovedEventData ?: return
                    handleMediaDownloadApproved(data)
                }

                "media.download.denied" -> {
                    val data = event.data as? MediaDownloadDeniedEventData ?: return
                    handleMediaDownloadDenied(data)
                }

                // Direct message event (if implemented on server)
                "message.received", "direct.message.received", "chat.message.received" -> {
                    handleDirectMessageReceived(event)
                }

                // Call events
                "call.incoming" -> {
                    val data = event.data as? CallIncomingEventData ?: return
                    handleIncomingCall(data.callerId, data.callerName, data.callType)
                }

                "call.missed" -> {
                    val data = event.data as? CallMissedEventData ?: return
                    handleMissedCall(data.callerId, data.callerName)
                }

                "call.ended" -> {
                    val data = event.data as? CallEndedEventData ?: return
                    handleCallEnded(data.duration)
                }

                "call.rejected" -> {
                    val data = event.data as? CallRejectedEventData ?: return
                    Log.d(TAG, "Call rejected by ${data.rejectedBy}")
                }

                "call.accepted" -> {
                    Log.d(TAG, "Call accepted")
                }

                else -> {
                    Log.d(TAG, "Unhandled event type: ${event.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WebSocket event: ${e.message}", e)
        }
    }

    /**
     * Handle direct message received - show notification
     */
    private fun handleDirectMessageReceived(event: WebSocketEvent) {
        try {
            if (!prefs.isNotifyMessagesEnabled()) {
                Log.d(TAG, "Message notifications disabled - skipping")
                return
            }
            // Extract data based on structure
            val senderId = (event.data as? Map<*, *>)?.get("senderId") as? String ?: return
            val senderName = (event.data as? Map<*, *>)?.get("senderName") as? String ?: "Unknown User"
            val messageContent = (event.data as? Map<*, *>)?.get("content") as? String ?: ""
            
            val messagePreview = if (messageContent.length > 100) {
                messageContent.take(97) + "..."
            } else {
                messageContent
            }

            Log.d(TAG, "Showing message notification from $senderName")
            NotificationHelper.showMessageNotification(
                context = context,
                senderName = senderName,
                messagePreview = messagePreview,
                senderId = senderId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling direct message: ${e.message}")
        }
    }

    /**
     * Handle friend request received
     */
    private fun handleFriendRequestReceived(data: FriendRequestEventData) {
        if (!prefs.isNotifyFriendRequestsEnabled()) {
            Log.d(TAG, "Friend request notifications disabled - skipping")
            return
        }
        Log.d(TAG, "Friend request received from ${data.senderUsername}")
        NotificationHelper.showFriendRequestNotification(
            context = context,
            senderName = data.senderUsername,
            senderId = data.senderId,
            requestId = data.requestId
        )
        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "friendRequest",
                title = data.senderUsername,
                description = "Sent you a friend request",
                senderId = data.senderId
            )
        )
    }

    /**
     * Handle group invitation pending
     */
    private fun handleGroupInvitePending(data: GroupInvitePendingEventData) {
        Log.d(TAG, "Group invite pending: ${data.inviterUsername} invited you to ${data.groupName}")
        val displayName = data.inviterDisplayName.ifEmpty { data.inviterUsername }
        NotificationHelper.showGroupInviteNotification(
            context = context,
            groupName = data.groupName,
            inviterName = displayName,
            groupId = data.groupId
        )
        InAppNotificationStore.addNotification(
            InAppNotification(
                type = "groupInvite",
                title = data.groupName,
                description = "$displayName invited you to join",
                senderId = data.groupId,
                inviteId = data.inviteId
            )
        )
    }

    /**
     * Handle friend request accepted
     */
    private fun handleFriendRequestAccepted(data: FriendAcceptedEventData) {
        Log.d(TAG, "Friend request accepted by ${data.username}")
        NotificationHelper.showFriendRequestAcceptedNotification(
            context = context,
            friendName = data.username,
            friendId = data.userId
        )
    }

    /**
     * Handle channel message received
     */
    private fun handleChannelMessageReceived(data: ChannelMessageEventData) {
        if (!prefs.isNotifyMessagesEnabled()) {
            Log.d(TAG, "Message notifications disabled - skipping channel message")
            return
        }
        // Use channel ID as name (in production, would fetch from cache)
        val channelName = "Channel #${data.channelId.take(8)}"

        val messagePreview = if (data.content.length > 100) {
            data.content.take(97) + "..."
        } else {
            data.content
        }

        Log.d(TAG, "Channel message received in $channelName from ${data.senderUsername}")
        NotificationHelper.showChannelMessageNotification(
            context = context,
            channelName = channelName,
            senderName = data.senderUsername,
            messagePreview = messagePreview,
            channelId = data.channelId
        )
    }

    /**
     * Handle group voting initiated
     */
    private fun handleGroupVoteInitiated(data: GroupVoteInitiatedEventData) {
        if (!prefs.isNotifyGroupUpdatesEnabled()) {
            Log.d(TAG, "Group notifications disabled - skipping vote notification")
            return
        }
        val message = "Voting has started. ${data.totalMembers} members need to vote."
        val displayGroupName = if (data.groupName.isNotEmpty()) data.groupName else "Group"

        Log.d(TAG, "Group vote initiated in group ${data.groupId}")
        NotificationHelper.showGroupVotingNotification(
            context = context,
            groupName = "$displayGroupName - Deletion Vote",
            message = message,
            groupId = data.groupId
        )
    }

    /**
     * Handle group voting cast
     */
    private fun handleGroupVoteCast(data: GroupVoteCastEventData) {
        if (!prefs.isNotifyGroupUpdatesEnabled()) {
            Log.d(TAG, "Group notifications disabled - skipping vote cast notification")
            return
        }
        val message = "${data.votedBy} voted: ${data.vote} (${data.approvalCount} approve, ${data.rejectionCount} reject)"

        Log.d(TAG, "Vote cast in group ${data.groupId}")
        NotificationHelper.showGroupVotingNotification(
            context = context,
            groupName = "Group Voting Update",
            message = message,
            groupId = data.groupId
        )
    }

    /**
     * ✅ UPDATED: Handle media download approved - trigger automatic download and decryption
     */
    private fun handleMediaDownloadApproved(data: MediaDownloadApprovedEventData) {
        Log.d(TAG, "✅ Media download approved: ${data.mediaId}, encrypted: ${data.encrypted}")
        
        // Show notification first
        NotificationHelper.showMediaDownloadApprovedNotification(
            context = context,
            mediaName = data.fileName ?: "Media File"
        )
        
        // ✅ NEW: Trigger automatic download and decryption if encrypted details are available
        if (data.encrypted && !data.encryptionKey.isNullOrEmpty() && !data.fileName.isNullOrEmpty()) {
            Log.d(TAG, "Starting automatic download and decryption for ${data.fileName}")
            
            // Create approval response object for the download handler
            val approval = com.freetime.app.api.MediaDownloadApproval(
                downloadLink = data.downloadUrl,
                mediaId = data.mediaId,
                fileName = data.fileName,
                mimeType = data.mimeType ?: "application/octet-stream",
                encrypted = true,
                encryptionKey = data.encryptionKey,
                iv = data.iv
            )
            
            // Launch coroutine to handle download and decryption
            val apiService = com.freetime.app.api.FreeTimeApiService(context)
            GlobalScope.launch {
                try {
                    val result = apiService.downloadAndDecryptApprovedMedia(approval)
                    result.onSuccess {
                        Log.d(TAG, "✅ Media saved successfully to gallery: ${data.fileName}")
                        NotificationHelper.showMediaDownloadSuccessNotification(
                            context = context,
                            fileName = data.fileName
                        )
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Failed to download/decrypt media: ${error.message}")
                        NotificationHelper.showMediaDownloadErrorNotification(
                            context = context,
                            fileName = data.fileName,
                            error = error.message ?: "Unknown error"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error during media download: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle media download denied
     */
    private fun handleMediaDownloadDenied(data: MediaDownloadDeniedEventData) {
        Log.d(TAG, "Media download denied: ${data.mediaId}")
        NotificationHelper.showMediaDownloadDeniedNotification(
            context = context,
            mediaName = "Media File",
            reason = data.reason
        )
    }

    /**
     * Handle incoming call
     */
    private fun handleIncomingCall(callerId: String, callerName: String, callType: String) {
        Log.d(TAG, "Incoming $callType call from $callerName ($callerId)")
        NotificationHelper.showIncomingCallNotification(
            context = context,
            callerName = callerName,
            callerId = callerId,
            callType = callType
        )
    }

    /**
     * Handle missed call
     */
    private fun handleMissedCall(callerId: String, callerName: String) {
        Log.d(TAG, "Missed call from $callerName ($callerId)")
        NotificationHelper.showMissedCallNotification(
            context = context,
            callerName = callerName,
            callerId = callerId
        )
    }

    /**
     * Handle call ended
     */
    private fun handleCallEnded(duration: String) {
        Log.d(TAG, "Call ended, duration: $duration")
        // Notification shown by NotificationHelper.showCallEndedNotification
        // This is mostly for logging purposes; the actual notification
        // would be triggered from the call screen or FCM
    }}