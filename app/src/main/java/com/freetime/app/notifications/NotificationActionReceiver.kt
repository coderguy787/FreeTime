package com.freetime.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.notifications.InAppNotificationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles direct actions taken on system notifications (from the notification shade / lock screen).
 * Lets users Accept or Decline friend requests WITHOUT opening the app.
 *
 * Registered in AndroidManifest with android:exported="false" — only the system can send these intents.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACCEPT_FRIEND_REQUEST  = "com.freetime.app.ACTION_ACCEPT_FRIEND_REQUEST"
        const val ACTION_DECLINE_FRIEND_REQUEST = "com.freetime.app.ACTION_DECLINE_FRIEND_REQUEST"

        const val EXTRA_SENDER_ID       = "senderId"
        const val EXTRA_REQUEST_ID      = "requestId"  // CRITICAL FIX: Track request ID
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        private const val TAG = "NotifAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // goAsync() extends the BroadcastReceiver lifetime so we can do network I/O
        val pendingResult = goAsync()
        InAppNotificationStore.init(context)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ACCEPT_FRIEND_REQUEST -> {
                        val senderId = intent.getStringExtra(EXTRA_SENDER_ID)
                        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: ""
                        if (senderId.isNullOrEmpty()) {
                            Log.e(TAG, "ACCEPT action missing senderId — ignored")
                            return@launch
                        }
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                        Log.d(TAG, "Accepting friend request from $senderId (requestId: $requestId) via notification")
                        val result = FreeTimeApiService(context).acceptFriendRequest(senderId)
                        if (result.isSuccess) {
                            Log.d(TAG, "✓ Friend request from $senderId accepted")
                            InAppNotificationStore.removeByTypeAndSender("friendRequest", senderId)
                            cancelNotification(context, notifId)
                        } else {
                            Log.e(TAG, "✗ Accept failed: ${result.exceptionOrNull()?.message}")
                        }
                    }

                    ACTION_DECLINE_FRIEND_REQUEST -> {
                        val senderId = intent.getStringExtra(EXTRA_SENDER_ID)
                        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: ""
                        if (senderId.isNullOrEmpty()) {
                            Log.e(TAG, "DECLINE action missing senderId — ignored")
                            return@launch
                        }
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

                        Log.d(TAG, "Declining friend request from $senderId (requestId: $requestId) via notification")
                        val result = FreeTimeApiService(context).declineFriendRequest(senderId)
                        if (result.isSuccess) {
                            Log.d(TAG, "✓ Friend request from $senderId declined")
                            InAppNotificationStore.removeByTypeAndSender("friendRequest", senderId)
                            cancelNotification(context, notifId)
                        } else {
                            Log.e(TAG, "✗ Decline failed: ${result.exceptionOrNull()?.message}")
                        }
                    }

                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, id: Int) {
        if (id == -1) return
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between notification display and action — ignore
        }
    }
}
