package com.freetime.app.services

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.freetime.app.R
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.RingtoneManager

/**
 * Call Foreground Service - Keeps incoming call notification active
 * Ensures Android doesn't kill the process while displaying the call UI
 */
class CallForegroundService : Service() {
    companion object {
        private const val TAG = "CallForegroundService"
        private const val CALL_NOTIFICATION_ID = 1337
        private const val CALL_CHANNEL_ID = "call_foreground_channel"
    }

    private var mediaPlayer: MediaPlayer? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔔 CallForegroundService started")
        
        try {
            // Create notification channel for Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Foreground service for active calls"
                    setShowBadge(true)
                    enableVibration(true)
                }
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }

            // Create the foreground notification
            val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setContentTitle("FreeTime Call")
                .setContentText("Call in progress...")
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.freetime_logo))
                .setColor(getColor(R.color.notification_color))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .build()

            // Start foreground service with the notification
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    CALL_NOTIFICATION_ID, 
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(CALL_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "✅ Foreground service notification displayed")

            // Start playing ringtone and vibration to ensure device rings when app is not active
            try {
                val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(this@CallForegroundService, ringtoneUri)
                    isLooping = true
                    prepare()
                    start()
                }

                // Vibrate pattern while ringing
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(longArrayOf(0, 500, 200, 500), 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ringtone/vibration: ${e.message}", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting foreground service: ${e.message}", e)
        }

        // Return sticky so service restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔚 CallForegroundService destroyed")
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone/vibration: ${e.message}", e)
        }
    }
}
