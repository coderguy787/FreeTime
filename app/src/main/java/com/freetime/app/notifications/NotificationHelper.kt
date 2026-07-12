package com.freetime.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.freetime.app.R
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.services.WebSocketManager
import java.net.URL

/**
 * NotificationHelper - Centralized utility for creating and displaying system notifications.
 * Handles different channels for calls, messages, social updates, and media transfers.
 */
object NotificationHelper {

    const val CALL_CHANNEL_ID = "calls"
    const val CALL_SILENT_CHANNEL_ID = "calls_silent"
    const val MESSAGE_CHANNEL_ID = "messages"
    const val MESSAGE_SILENT_CHANNEL_ID = "messages_silent"
    const val SOCIAL_CHANNEL_ID = "social"
    const val SOCIAL_SILENT_CHANNEL_ID = "social_silent"
    const val MEDIA_CHANNEL_ID = "media"
    const val CHAT_CHANNEL_ID = "chat"

    const val MESSAGE_GROUP_KEY = "com.freetime.app.MESSAGE_GROUP"
    const val MESSAGE_SUMMARY_ID = 9999

    private var notificationIdCounter = 10000

    @Volatile
    var currentActiveChatId: String? = null

    @Volatile
    var isAppInForeground: Boolean = false

    private val recentNotificationKeys = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun isDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        val lastShown = recentNotificationKeys[key] ?: return false
        return (now - lastShown) < 5000
    }

    private fun markShown(key: String) {
        recentNotificationKeys[key] = System.currentTimeMillis()
        if (recentNotificationKeys.size > 200) {
            val cutoff = System.currentTimeMillis() - 10000
            recentNotificationKeys.entries.removeAll { it.value < cutoff }
        }
    }

    private fun nextNotificationId(): Int = notificationIdCounter++

    private fun isSoundEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifySoundEnabled()
        } catch (e: Exception) {
            true
        }
    }

    private fun isVibrationEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifyVibrationEnabled()
        } catch (e: Exception) {
            true
        }
    }

    private fun shouldSuppressNotification(context: Context, dedupKey: String): Boolean {
        if (isDuplicate(dedupKey)) {
            android.util.Log.d("NotificationHelper", "Suppressed duplicate notification: $dedupKey")
            return true
        }
        if (isAppInForeground) {
            android.util.Log.d("NotificationHelper", "Suppressed notification while app in foreground: $dedupKey")
            return true
        }
        return false
    }

    private fun isMessagesNotifyEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifyMessagesEnabled()
        } catch (e: Exception) {
            true
        }
    }

    private fun isFriendRequestsNotifyEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifyFriendRequestsEnabled()
        } catch (e: Exception) {
            true
        }
    }

    private fun isGroupUpdatesNotifyEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifyGroupUpdatesEnabled()
        } catch (e: Exception) {
            true
        }
    }

    private fun isMentionAlertsNotifyEnabled(context: Context): Boolean {
        return try {
            SharedPreferencesHelper(context).isNotifyMentionAlertsEnabled()
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Refresh channel sound/vibration properties when the user toggles preferences.
     * Channels created on Android O+ cannot change importance; sound/vibration can be updated
     * by recreating the channels after deleting the old ones.
     */
    fun recreateNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannel(CALL_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(CALL_SILENT_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(MESSAGE_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(MESSAGE_SILENT_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(SOCIAL_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(SOCIAL_SILENT_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(MEDIA_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(CHAT_CHANNEL_ID)
        }
        createNotificationChannels(context)
    }

    private fun getMessageChannel(context: Context): String {
        return if (isSoundEnabled(context)) MESSAGE_CHANNEL_ID else MESSAGE_SILENT_CHANNEL_ID
    }

    private fun getSocialChannel(context: Context): String {
        return if (isSoundEnabled(context)) SOCIAL_CHANNEL_ID else SOCIAL_SILENT_CHANNEL_ID
    }

    private fun getCallChannel(context: Context): String {
        return if (isSoundEnabled(context)) CALL_CHANNEL_ID else CALL_SILENT_CHANNEL_ID
    }

    private fun buildDefaults(context: Context): Int {
        var defaults = NotificationCompat.DEFAULT_LIGHTS
        if (isSoundEnabled(context)) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (isVibrationEnabled(context)) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        return defaults
    }

    /**
     * Create an "FT" bitmap icon for notifications (128x128 with white text on transparent background)
     * Used as large icon in notifications to show app branding
     */
    private fun createFTBitmap(): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background circle
        val paint = Paint().apply {
            color = Color.parseColor("#00C9FF")  // Cyan/turquoise color
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Draw "FT" text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 50f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val x = size / 2f
        val y = size / 2f + 15f  // Slight offset for vertical centering
        canvas.drawText("FT", x, y, textPaint)
        
        return bitmap
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val prefs = SharedPreferencesHelper(context)
            if (!prefs.areNotificationChannelsUpgraded()) {
                notificationManager.deleteNotificationChannel(CALL_CHANNEL_ID)
                notificationManager.deleteNotificationChannel(MESSAGE_CHANNEL_ID)
                notificationManager.deleteNotificationChannel(SOCIAL_CHANNEL_ID)
                notificationManager.deleteNotificationChannel(MEDIA_CHANNEL_ID)
                prefs.setNotificationChannelsUpgraded(true)
            }

            // Silent channels (no sound/vibration) - created first so they exist when needed
            val callSilentChannel = NotificationChannel(
                CALL_SILENT_CHANNEL_ID,
                "Incoming Calls (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Silent notifications for incoming calls (sound/vibration disabled)"
                setShowBadge(true)
                enableVibration(false)
            }

            val messageSilentChannel = NotificationChannel(
                MESSAGE_SILENT_CHANNEL_ID,
                "New Messages (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Silent notifications for new messages (sound/vibration disabled)"
                setShowBadge(true)
                enableVibration(false)
            }

            val socialSilentChannel = NotificationChannel(
                SOCIAL_SILENT_CHANNEL_ID,
                "Friend Requests (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Silent notifications for friend requests (sound/vibration disabled)"
                setShowBadge(true)
                enableVibration(false)
            }

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val socialChannel = NotificationChannel(
                SOCIAL_CHANNEL_ID,
                "Friend Requests & Social",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Friend requests, acceptances, and social updates"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val mediaChannel = NotificationChannel(
                MEDIA_CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Media download approvals and denials"
                setShowBadge(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                "Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles background message synchronization"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(callSilentChannel)
            notificationManager.createNotificationChannel(messageSilentChannel)
            notificationManager.createNotificationChannel(socialSilentChannel)
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(socialChannel)
            notificationManager.createNotificationChannel(mediaChannel)
            notificationManager.createNotificationChannel(chatChannel)
        }
    }

    fun showIncomingCallNotification(context: Context, callerName: String, callerId: String, callType: String, callId: String = "", callerAvatarUrl: String? = null, offerSdp: String = "") {  // ✅ CRITICAL: Add offerSdp parameter
        if (currentActiveChatId == callerId) return

        // ✅ CRITICAL: Ensure notification channel exists BEFORE showing notification
        createNotificationChannels(context)
        
        // ✅ CRITICAL: Acquire wake-lock with proper timeout and release mechanism
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "FreeTimeApp:CallWakeLock"
            ).apply { acquire(15000) } // 15 seconds to ensure call is answered or declined
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to acquire wake-lock: ${e.message}")
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val fullScreenIntent = Intent(context, com.freetime.app.IncomingCallActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_ID", callerId)
            putExtra("CALL_TYPE", callType)
            putExtra("CALL_ID", callId)
            if (!callerAvatarUrl.isNullOrEmpty()) putExtra("CALLER_AVATAR", callerAvatarUrl)
            if (offerSdp.isNotEmpty()) putExtra("OFFER_SDP", offerSdp)  // ✅ CRITICAL: Pass offer to activity
            // ✅ FIXED: Add flags to wake up and show over lock screen
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(context, callerId.hashCode() + 9000, fullScreenIntent, pendingIntentFlags)

        val acceptIntent = Intent(context, com.freetime.app.IncomingCallActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_ID", callerId)
            putExtra("CALL_TYPE", callType)
            putExtra("CALL_ID", callId)
            if (!callerAvatarUrl.isNullOrEmpty()) putExtra("CALLER_AVATAR", callerAvatarUrl)
            if (offerSdp.isNotEmpty()) putExtra("OFFER_SDP", offerSdp)  // ✅ CRITICAL: Pass offer to activity from action button
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(context, callerId.hashCode() + 9001, acceptIntent, pendingIntentFlags)

        val declineIntent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            putExtra("DECLINE_CALL", callId)
            putExtra("CALLER_ID", callerId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val declinePendingIntent = PendingIntent.getActivity(context, callerId.hashCode() + 9002, declineIntent, pendingIntentFlags)

        val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Use specialized monochrome white logo
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Incoming $callType Call")
            .setContentText("$callerName is calling you")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent) // ✅ Fallback if fullScreenIntent is blocked
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_call, "Answer", acceptPendingIntent)
            .addAction(R.mipmap.ic_launcher_foreground, "Decline", declinePendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLights(android.graphics.Color.CYAN, 500, 500)

        if (!callerAvatarUrl.isNullOrEmpty()) {
            try {
                val finalUrl = if (callerAvatarUrl.startsWith("http")) callerAvatarUrl else {
                    val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                    if (callerAvatarUrl.startsWith("/")) "$baseUrl$callerAvatarUrl" else "$baseUrl/$callerAvatarUrl"
                }
                val url = URL(finalUrl)
                val conn = url.openConnection()
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val bitmap = BitmapFactory.decodeStream(conn.getInputStream())
                if (bitmap != null) notificationBuilder.setLargeIcon(bitmap)
            } catch (e: Exception) {}
        }

        // Ensure CallForegroundService is running to play ringtone/vibration when app is not active
        try {
            val foregroundIntent = Intent(context, com.freetime.app.services.CallForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(foregroundIntent)
            } else {
                context.startService(foregroundIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to start CallForegroundService: ${e.message}")
        }

        try {
            NotificationManagerCompat.from(context).notify(callerId.hashCode() + 1000, notificationBuilder.build())
            android.util.Log.d("NotificationHelper", "✅ Incoming call notification shown for $callerName")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "❌ SecurityException showing call notification: ${e.message}")
        } finally {
            // ✅ CRITICAL: Release wake-lock after notification is shown
            try { 
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to release wake-lock: ${e.message}")
            }
        }
    }

    fun showMessageNotification(context: Context, senderName: String, messagePreview: String, senderId: String, senderAvatarUrl: String? = null) {
        val dedupKey = "msg:$senderId:$messagePreview"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (currentActiveChatId == senderId) return
        if (!isMessagesNotifyEnabled(context)) return
        markShown(dedupKey)

        // ✅ Ensure notification channel exists
        createNotificationChannels(context)

        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FreeTimeApp:MsgWakeLock")
            wakeLock.acquire(5000)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to acquire message wake-lock: ${e.message}")
        }

        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHAT_ID", senderId)
        }
        val pendingIntent = PendingIntent.getActivity(context, senderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ✅ Use dynamic channel based on sound preference
        val channelId = getMessageChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Use specialized monochrome white logo
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messagePreview))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(MESSAGE_GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // ✅ NEW: Show user's avatar as large icon if available
        if (!senderAvatarUrl.isNullOrEmpty()) {
            try {
                val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                val finalUrl = if (senderAvatarUrl.startsWith("http")) senderAvatarUrl else "$baseUrl/${senderAvatarUrl.removePrefix("/")}"

                // Loading bitmap synchronously for notification (run in background is better but simple for now)
                val url = URL(finalUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap)
                } else {
                    notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
                }
            } catch (e: Exception) {
                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
            }
        } else {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
        }

        // Sound and vibration handled by channel, but still apply for consistency
        if (isSoundEnabled(context)) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            notificationBuilder.setSilent(true)
        }

        if (isVibrationEnabled(context)) {
            notificationBuilder.setVibrate(longArrayOf(0, 250, 100, 250))
        } else {
            notificationBuilder.setVibrate(longArrayOf(0))
        }

        val summaryBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("FreeTime")
            .setContentText("New messages")
            .setGroup(MESSAGE_GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)

        try {
            val mgr = NotificationManagerCompat.from(context)
            mgr.notify(senderId.hashCode(), notificationBuilder.build())
            mgr.notify(MESSAGE_SUMMARY_ID, summaryBuilder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to show message notification: ${e.message}")
        } finally {
            // ✅ CRITICAL: Release wake-lock after notification is shown
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to release wake-lock: ${e.message}")
            }
        }
    }

    fun showFriendRequestNotification(context: Context, senderName: String, senderId: String, requestId: String = "") {
        val dedupKey = "friend_req:$senderId"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (!isFriendRequestsNotifyEnabled(context)) return
        markShown(dedupKey)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FreeTimeApp:SocialWakeLock")
        try { wakeLock.acquire(5000) } catch (e: Exception) {}

        val notifId = senderId.hashCode() + 2000

        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "friendRequests")
        }
        val pendingIntent = PendingIntent.getActivity(context, notifId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Accept action
        val acceptIntent = Intent(context, com.freetime.app.notifications.NotificationActionReceiver::class.java).apply {
            action = com.freetime.app.notifications.NotificationActionReceiver.ACTION_ACCEPT_FRIEND_REQUEST
            putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_SENDER_ID, senderId)
            putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            if (requestId.isNotEmpty()) {
                putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            }
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(context, notifId + 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Decline action
        val declineIntent = Intent(context, com.freetime.app.notifications.NotificationActionReceiver::class.java).apply {
            action = com.freetime.app.notifications.NotificationActionReceiver.ACTION_DECLINE_FRIEND_REQUEST
            putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_SENDER_ID, senderId)
            putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            if (requestId.isNotEmpty()) {
                putExtra(com.freetime.app.notifications.NotificationActionReceiver.EXTRA_REQUEST_ID, requestId)
            }
        }
        val declinePendingIntent = PendingIntent.getBroadcast(context, notifId + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ✅ Use dynamic channel based on sound preference
        val channelId = getSocialChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Friend Request")
            .setContentText("$senderName sent you a friend request")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_add, "Accept", acceptPendingIntent)

        if (isSoundEnabled(context)) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            notificationBuilder.setSilent(true)
        }

        if (isVibrationEnabled(context)) {
            notificationBuilder.setVibrate(longArrayOf(0, 300, 100, 300))
        } else {
            notificationBuilder.setVibrate(longArrayOf(0))
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, notificationBuilder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to show message notification: ${e.message}")
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {}
        }
    }

    fun showGroupMessageNotification(context: Context, groupName: String, senderName: String, messagePreview: String, groupId: String, senderId: String? = null, senderAvatarUrl: String? = null) {
        val dedupKey = "grp_msg:$groupId:$senderName:$messagePreview"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (currentActiveChatId == groupId) return
        markShown(dedupKey)

        val isGroupEnabled = isGroupUpdatesNotifyEnabled(context)
        if (!isGroupEnabled) return

        // ✅ Acquire wake-lock to ensure notification is shown (for background delivery)
        createNotificationChannels(context)

        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FreeTimeApp:GroupMsgWakeLock")
            wakeLock.acquire(5000)
            android.util.Log.d("NotificationHelper", "✅ Group message wake-lock acquired for $groupName")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to acquire group message wake-lock: ${e.message}")
        }

        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "groupChat")
            putExtra("GROUP_ID", groupId)
        }

        val notificationId = if (senderId != null) (groupId + senderId).hashCode() else groupId.hashCode()
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ✅ Use dynamic channel based on sound preference
        val channelId = getMessageChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("$senderName in $groupName")
            .setContentText(messagePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messagePreview))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(MESSAGE_GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // ✅ NEW: Show sender's avatar as large icon if available
        if (!senderAvatarUrl.isNullOrEmpty()) {
            try {
                val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                val finalUrl = if (senderAvatarUrl.startsWith("http")) senderAvatarUrl else "$baseUrl/${senderAvatarUrl.removePrefix("/")}"

                val url = URL(finalUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap)
                } else {
                    notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
                }
            } catch (e: Exception) {
                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
            }
        } else {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
        }

        if (isSoundEnabled(context)) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            notificationBuilder.setSilent(true)
        }

        if (isVibrationEnabled(context)) {
            notificationBuilder.setVibrate(longArrayOf(0, 250, 100, 250))
        } else {
            notificationBuilder.setVibrate(longArrayOf(0))
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
            android.util.Log.d("NotificationHelper", "✅ Group message notification shown: $senderName in $groupName")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "❌ SecurityException showing group notification: ${e.message}")
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                    android.util.Log.d("NotificationHelper", "✅ Group message wake-lock released")
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to release wake-lock: ${e.message}")
            }
        }
    }


    fun showFriendRequestAcceptedNotification(context: Context, friendName: String, friendId: String) {
        val dedupKey = "friend_accepted:$friendId"
        if (shouldSuppressNotification(context, dedupKey)) return
        markShown(dedupKey)
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHAT_ID", friendId)
        }
        val pendingIntent = PendingIntent.getActivity(context, friendId.hashCode() + 3000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ✅ Use dynamic channel based on sound preference
        val channelId = getSocialChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Friend Request Accepted")
            .setContentText("$friendName accepted your request!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(friendId.hashCode() + 3000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMediaDownloadRequestNotification(context: Context, requesterName: String, mediaId: String) {
        val dedupKey = "media_request:$mediaId"
        if (shouldSuppressNotification(context, dedupKey)) return
        markShown(dedupKey)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FreeTimeApp:MediaWakeLock")
        try { wakeLock.acquire(5000) } catch (e: Exception) {}

        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "mediaRequests")
        }
        val pendingIntent = PendingIntent.getActivity(context, mediaId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Download Request")
            .setContentText("$requesterName wants to download your media")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(mediaId.hashCode(), notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMediaDownloadApprovedNotification(context: Context, mediaName: String, mediaKey: String = "") {
        val dedupKey = "media_approved:$mediaName"
        if (shouldSuppressNotification(context, dedupKey)) return
        markShown(dedupKey)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "FreeTimeApp:MediaWakeLock")
        try { wakeLock.acquire(5000) } catch (e: Exception) {}

        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (mediaKey.isNotEmpty()) putExtra("MEDIA_KEY", mediaKey)
        }
        val pendingIntent = PendingIntent.getActivity(context, mediaName.hashCode() + 4000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Download Approved")
            .setContentText("Your request for \"$mediaName\" was approved")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(mediaName.hashCode() + 4000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMediaDownloadDeniedNotification(context: Context, mediaName: String, reason: String?) {
        val notificationBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))

            .setContentTitle("Download Denied")
            .setContentText("Request for \"$mediaName\" denied${if (reason != null) ": $reason" else ""}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(mediaName.hashCode() + 5000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMediaDownloadSuccessNotification(context: Context, fileName: String) {
        val notificationBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Download Complete")
            .setContentText("\"$fileName\" saved to gallery")
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(fileName.hashCode() + 6000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMediaDownloadErrorNotification(context: Context, fileName: String, error: String) {
        val notificationBuilder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Download Failed")
            .setContentText("Failed to save \"$fileName\": $error")
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(fileName.hashCode() + 7000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showGroupMemberActionNotification(context: Context, groupName: String, memberName: String, action: String, groupId: String) {
        val dedupKey = "group_action:$groupId:$memberName:$action"
        if (shouldSuppressNotification(context, dedupKey)) return
        markShown(dedupKey)
        val actionText = "$memberName $action $groupName"
        val notificationBuilder = NotificationCompat.Builder(context, SOCIAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle(groupName)
            .setContentText(actionText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify("group_action_${groupId}_${memberName}".hashCode(), notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showGroupInviteNotification(context: Context, groupName: String, inviterName: String, groupId: String) {
        val dedupKey = "group_invite:$groupId:$inviterName"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (!isGroupUpdatesNotifyEnabled(context)) return
        markShown(dedupKey)
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "groups")
        }
        val pendingIntent = PendingIntent.getActivity(context, groupId.hashCode() + 8000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, SOCIAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Group Invite")
            .setContentText("$inviterName invited you to $groupName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(groupId.hashCode() + 8000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showGroupVotingNotification(context: Context, groupName: String, message: String, groupId: String) {
        val dedupKey = "group_vote:$groupId:$message"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (!isGroupUpdatesNotifyEnabled(context)) return
        markShown(dedupKey)
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "groupVoting")
        }
        val pendingIntent = PendingIntent.getActivity(context, groupId.hashCode() + 9000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, SOCIAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Group Vote: $groupName")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(groupId.hashCode() + 9000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showInternalNotification(context: Context, data: WebSocketManager.InternalNotificationData) {
        val dedupKey = "internal:${data.title}:${data.body}"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (!isMessagesNotifyEnabled(context)) return
        markShown(dedupKey)
        val notificationBuilder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        try {
            NotificationManagerCompat.from(context).notify(data.timestamp.toInt(), notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showMissedCallNotification(context: Context, callerName: String, callerId: String) {
        if (!isMessagesNotifyEnabled(context)) return
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHAT_ID", callerId)
        }
        val pendingIntent = PendingIntent.getActivity(context, callerId.hashCode() + 10000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setContentTitle("Missed Call")
            .setContentText("You missed a call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(callerId.hashCode() + 10000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showCallEndedNotification(context: Context, callerName: String, duration: String) {
        val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(createFTBitmap())
            .setColor(context.getColor(R.color.notification_color))
            .setSubText("FreeTime")
            .setContentTitle("Call ended")
            .setContentText("Call with $callerName lasted $duration")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify("call_ended_$callerName".hashCode(), notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun showChannelMessageNotification(context: Context, channelName: String, senderName: String, messagePreview: String, channelId: String) {
        val dedupKey = "channel_msg:$channelId:$senderName:$messagePreview"
        if (shouldSuppressNotification(context, dedupKey)) return
        if (!isMessagesNotifyEnabled(context)) return
        markShown(dedupKey)
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "channelMessages")
            putExtra("CHANNEL_ID", channelId)
        }
        val pendingIntent = PendingIntent.getActivity(context, channelId.hashCode() + 11000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ✅ Use dynamic channel based on sound preference
        val channel = getMessageChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
            .setColor(context.getColor(R.color.notification_color))
            .setSubText("FreeTime")
            .setContentTitle(channelName)
            .setContentText("$senderName: $messagePreview")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$senderName: $messagePreview"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (isSoundEnabled(context)) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            notificationBuilder.setSilent(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(channelId.hashCode() + 11000, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun cancelCallNotification(context: Context, callerId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(callerId.hashCode() + 1000)
            try {
                val stopIntent = Intent(context, com.freetime.app.services.CallForegroundService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {
                android.util.Log.w("NotificationHelper", "Failed to stop CallForegroundService: ${e.message}")
            }
        } catch (e: Exception) {}
    }

    fun cancelMessageNotification(context: Context, senderId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(senderId.hashCode())
        } catch (e: Exception) {}
    }

    private const val ONGOING_CALL_NOTIFICATION_ID = 88888

    fun showOngoingCallNotification(context: Context, recipientName: String, recipientId: String) {
        val intent = Intent(context, com.freetime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CHAT_ID", recipientId)
        }
        val pendingIntent = PendingIntent.getActivity(context, ONGOING_CALL_NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.freetime_logo))
            .setColor(context.getColor(R.color.notification_color))
            .setSubText("FreeTime")
            .setContentTitle("Ongoing Call")
            .setContentText("In call with $recipientName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)

        try {
            NotificationManagerCompat.from(context).notify(ONGOING_CALL_NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: SecurityException) {}
    }

    fun cancelOngoingCallNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ONGOING_CALL_NOTIFICATION_ID)
        } catch (e: Exception) {}
    }
}
