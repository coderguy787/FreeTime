package com.freetime.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.work.*
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.notifications.NotificationHelper
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * ✅ Background Polling Service - Works when app is completely closed
 * 
 * This service polls the backend every 2-3 minutes to fetch pending messages/calls
 * Uses AlarmManager with SET_EXACT_AND_ALLOW_WHILE_IDLE to work even in Doze mode
 * Mimics Firebase behavior without external dependency
 */

class BackgroundPollingService : Service() {
    private lateinit var apiService: FreeTimeApiService
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "BackgroundPollingService"
        const val POLL_INTERVAL_MINUTES = 1  // Poll every 1 minute (no Firebase needed!)
        const val ACTION_POLL = "com.freetime.app.action.POLL_NOTIFICATIONS"
        
        /**
         * Start the background polling service
         */
        fun startPolling(context: Context) {
            Log.d(TAG, "🚀 Starting background polling service")
            
            val intent = Intent(context, BackgroundPollingService::class.java)
            context.startService(intent)
            
            // Also schedule WorkManager for reliability
            schedulePeriodicPolling(context)
        }
        
        /**
         * Stop the background polling
         */
        fun stopPolling(context: Context) {
            Log.d(TAG, "⛔ Stopping background polling")
            val intent = Intent(context, BackgroundPollingService::class.java)
            context.stopService(intent)
            
            // Cancel WorkManager jobs
            WorkManager.getInstance(context).cancelUniqueWork("notification_polling")
        }
        
        /**
         * Schedule reliable periodic polling using WorkManager
         * Ensures polling continues even if service is killed
         */
        private fun schedulePeriodicPolling(context: Context) {
            val pollingWork = PeriodicWorkRequestBuilder<NotificationPollingWorker>(
                POLL_INTERVAL_MINUTES.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    15000L,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "notification_polling",
                ExistingPeriodicWorkPolicy.KEEP,
                pollingWork
            )
            
            Log.d(TAG, "✅ WorkManager periodic polling scheduled every $POLL_INTERVAL_MINUTES minutes")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "📱 BackgroundPollingService created")
        apiService = FreeTimeApiService(this)
        
        // Start periodic alarm
        startPeriodicAlarm()
    }
    
    // Removed startForegroundServiceWithNotification() to satisfy user request
    // to remove the persistent "FreeTime Security Service" notification.
    // The service will now run as a regular background service, with 
    // WorkManager and AlarmManager providing reliability.
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📱 BackgroundPollingService started with action: ${intent?.action}")
        
        if (intent?.action == ACTION_POLL) {
            // Poll for new notifications
            scope.launch {
                pollNotifications()
            }
        }
        
        return START_STICKY  // Service restarts if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "💀 BackgroundPollingService destroyed")
        scope.cancel()
        super.onDestroy()
    }
    
    /**
     * Set up periodic alarm to poll every 3 minutes
     * Uses SET_EXACT_AND_ALLOW_WHILE_IDLE to work in Doze mode
     */
    private fun startPeriodicAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationPollingReceiver::class.java)
        intent.action = ACTION_POLL
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Schedule alarm every 3 minutes
            // SET_EXACT_AND_ALLOW_WHILE_IDLE works even in Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + (POLL_INTERVAL_MINUTES * 60 * 1000),
                pendingIntent
            )
            Log.d(TAG, "⏰ Periodic alarm set for every $POLL_INTERVAL_MINUTES minutes")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Cannot set exact alarm (permission denied): ${e.message}")
            // Fallback to inexact alarm
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + (POLL_INTERVAL_MINUTES * 60 * 1000),
                pendingIntent
            )
        }
    }
    
    /**
     * Fetch pending notifications from backend
     * Called every 3 minutes by AlarmManager
     */
    private suspend fun pollNotifications() {
        try {
            Log.d(TAG, "🔍 Polling for pending notifications...")
            
            val prefs = SharedPreferencesHelper(this)
            val userId = prefs.getUserId() ?: run {
                Log.w(TAG, "⚠️ User not logged in, skipping poll")
                return
            }
            
            val lastFetchTime = prefs.getString("lastNotificationFetch", "") ?: ""
            
            // Call backend endpoint
            val response = apiService.getPendingNotifications(userId, lastFetchTime)
            
            if (response.isSuccess) {
                val data = response.getOrNull() ?: return
                
                Log.d(TAG, "📬 Got ${data.messages.size} messages, ${data.calls.size} calls")
                
                // Process messages
                for (message in data.messages) {
                    Log.d(TAG, "💬 Showing notification for message from ${message.senderName}")
                    
                    NotificationHelper.showMessageNotification(
                        this,
                        message.senderName,
                        message.content.take(100),  // Preview
                        message.senderId
                    )
                }
                
                // Process calls
                for (call in data.calls) {
                    Log.d(TAG, "📞 Showing notification for missed call from ${call.callerName}")
                    
                    NotificationHelper.showMissedCallNotification(
                        this,
                        call.callerName,
                        call.callerId
                    )
                }
                
                // Update last fetch time
                prefs.saveString("lastNotificationFetch", System.currentTimeMillis().toString())
                Log.d(TAG, "✅ Poll completed successfully")
            } else {
                Log.e(TAG, "❌ Poll failed: ${response.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error polling notifications: ${e.message}", e)
        }
    }
}

/**
 * Broadcast Receiver - Triggers polling when alarm fires
 * Ensures polling works even if service is killed
 */
class NotificationPollingReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BackgroundPollingService.ACTION_POLL) {
            Log.d("NotificationPollingReceiver", "⏰ Alarm triggered, starting poll...")
            
            // Start service to perform poll
            val serviceIntent = Intent(context, BackgroundPollingService::class.java)
            serviceIntent.action = BackgroundPollingService.ACTION_POLL
            try {
                // We use startService instead of startForegroundService because 
                // the user requested to remove the persistent notification.
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e("NotificationPollingReceiver", "Failed to start service: ${e.message}")
            }
            
            // Re-schedule next alarm
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + (BackgroundPollingService.POLL_INTERVAL_MINUTES * 60 * 1000),
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.e("NotificationPollingReceiver", "Cannot reschedule alarm: ${e.message}")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + (BackgroundPollingService.POLL_INTERVAL_MINUTES * 60 * 1000),
                    pendingIntent
                )
            }
        }
    }
}

/**
 * WorkManager Worker - Reliable background polling
 * Executes periodically and handles retries
 */
class NotificationPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("NotificationPollingWorker", "🔄 WorkManager polling triggered")
            
            val prefs = SharedPreferencesHelper(applicationContext)
            val userId = prefs.getUserId() ?: return@withContext Result.retry()
            
            val apiService = FreeTimeApiService(applicationContext)
            val lastFetchTime = prefs.getString("lastNotificationFetch", "") ?: ""
            
            val response = apiService.getPendingNotifications(userId, lastFetchTime)
            
            if (response.isSuccess) {
                val data = response.getOrNull()
                if (data != null) {
                    Log.d("NotificationPollingWorker", "📬 Got ${data.messages.size} messages, ${data.calls.size} calls")
                    
                    // Process messages
                    for (message in data.messages) {
                        NotificationHelper.showMessageNotification(
                            applicationContext,
                            message.senderName,
                            message.content.take(100),
                            message.senderId
                        )
                    }
                    
                    // Process calls
                    for (call in data.calls) {
                        NotificationHelper.showMissedCallNotification(
                            applicationContext,
                            call.callerName,
                            call.callerId
                        )
                    }
                    
                    // Update last fetch time
                    prefs.saveString("lastNotificationFetch", System.currentTimeMillis().toString())
                }
                Log.d("NotificationPollingWorker", "✅ WorkManager poll succeeded")
                Result.success()
            } else {
                Log.e("NotificationPollingWorker", "❌ WorkManager poll failed, retrying")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("NotificationPollingWorker", "❌ WorkManager poll error: ${e.message}")
            Result.retry()
        }
    }
}
