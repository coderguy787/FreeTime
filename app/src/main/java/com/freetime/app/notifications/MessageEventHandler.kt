package com.freetime.app.notifications

import android.content.Context
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// handles incoming websocket events
class MessageEventHandler(private val context: Context) {
    companion object {
        private const val TAG = "MessageEventHandler"
    }

    private val prefs = SharedPreferencesHelper(context)

    fun handleWebSocketEvent(event: WebSocketEvent) {
        try {
            when (event.type) {
                "friend.request.received" -> {
                    val data = event.data as? FriendRequestEventData ?: return
                    handleFriendRequestReceived(data)
                }

                "friend.request.accepted" -> {
                    val data = event.data as? FriendAcceptedEventData ?: return
                    handleFriendRequestAccepted(data)
                }

                "group.invite.pending" -> {
                    val data = event.data as? GroupInvitePendingEventData ?: return
                    handleGroupInvitePending(data)
                }

                "channel.message.received" -> {
                    val data = event.data as? ChannelMessageEventData ?: return
                    handleChannelMessageReceived(data)
                }

                "group.vote.initiated" -> {
                    val data = event.data as? GroupVoteInitiatedEventData ?: return
                    handleGroupVoteInitiated(data)
                }

                "group.vote.cast" -> {
                    val data = event.data as? GroupVoteCastEventData ?: return
                    handleGroupVoteCast(data)
                }

                "media.download.approved" -> {
                    val data = event.data as? MediaDownloadApprovedEventData ?: return
                    handleMediaDownloadApproved(data)
                }

                "media.download.denied" -> {
                    val data = event.data as? MediaDownloadDeniedEventData ?: return
                    handleMediaDownloadDenied(data)
                }

                "message.received", "direct.message.received", "chat.message.received" -> {
                    handleDirectMessageReceived(event)
                }

                else -> {
                    Log.d(TAG, "Unhandled event type: ${event.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WebSocket event: ${e.message}", e)
        }
    }

    private fun handleDirectMessageReceived(event: WebSocketEvent) {
        try {
            if (!prefs.isNotifyMessagesEnabled()) {
                Log.d(TAG, "Message notifications disabled - skipping")
                return
            }
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

    private fun handleFriendRequestAccepted(data: FriendAcceptedEventData) {
        Log.d(TAG, "Friend request accepted by ${data.username}")
        NotificationHelper.showFriendRequestAcceptedNotification(
            context = context,
            friendName = data.username,
            friendId = data.userId
        )
    }

    private fun handleChannelMessageReceived(data: ChannelMessageEventData) {
        if (!prefs.isNotifyMessagesEnabled()) {
            Log.d(TAG, "Message notifications disabled - skipping channel message")
            return
        }
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

    private fun handleMediaDownloadApproved(data: MediaDownloadApprovedEventData) {
        Log.d(TAG, " Media download approved: ${data.mediaId}, encrypted: ${data.encrypted}")

        NotificationHelper.showMediaDownloadApprovedNotification(
            context = context,
            mediaName = data.fileName ?: "Media File"
        )

        if (data.encrypted && !data.encryptionKey.isNullOrEmpty() && !data.fileName.isNullOrEmpty()) {
            Log.d(TAG, "Starting automatic download and decryption for ${data.fileName}")

            val approval = com.freetime.app.api.MediaDownloadApproval(
                downloadLink = data.downloadUrl,
                mediaId = data.mediaId,
                fileName = data.fileName,
                mimeType = data.mimeType ?: "application/octet-stream",
                encrypted = true,
                encryptionKey = data.encryptionKey,
                iv = data.iv
            )

            val apiService = com.freetime.app.api.FreeTimeApiService(context)
            GlobalScope.launch {
                try {
                    val result = apiService.downloadAndDecryptApprovedMedia(approval)
                    result.onSuccess {
                        Log.d(TAG, " Media saved successfully to gallery: ${data.fileName}")
                        NotificationHelper.showMediaDownloadSuccessNotification(
                            context = context,
                            fileName = data.fileName
                        )
                    }.onFailure { error ->
                        Log.e(TAG, " Failed to download/decrypt media: ${error.message}")
                        NotificationHelper.showMediaDownloadErrorNotification(
                            context = context,
                            fileName = data.fileName,
                            error = error.message ?: "Unknown error"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, " Error during media download: ${e.message}")
                }
            }
        }
    }

    private fun handleMediaDownloadDenied(data: MediaDownloadDeniedEventData) {
        Log.d(TAG, "Media download denied: ${data.mediaId}")
        NotificationHelper.showMediaDownloadDeniedNotification(
            context = context,
            mediaName = "Media File",
            reason = data.reason
        )
    }
}
