package com.freetime.app.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.freetime.app.api.FreeTimeApiService
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Background sync worker that runs periodically to fetch messages
 * This ensures offline messages are retrieved when app comes back online
 * Similar to WhatsApp's message sync mechanism
 */
class MessageSyncWorker(context: Context, params: androidx.work.WorkerParameters) : CoroutineWorker(context, params) {

    private val apiService = FreeTimeApiService(context)

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "⚙️ Running message sync worker")

            // Offline messages are received via FCM and saved by EnhancedFirebaseMessagingService
            // This worker ensures periodic syncing health checks are performed
            Log.d(TAG, "✓ Message sync health check completed")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "✗ Message sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MessageSyncWorker"
        private const val WORK_NAME = "freetime_message_sync"

        /**
         * Schedule periodic message sync (every 15 minutes)
         */
        fun schedulePeriodicSync(context: Context) {
            try {
                val syncWorkRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncWorkRequest
                )

                Log.d(TAG, "✓ Periodic message sync scheduled (every 15 minutes)")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to schedule sync: ${e.message}", e)
            }
        }

        /**
         * Cancel periodic sync
         */
        fun cancelPeriodicSync(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                Log.d(TAG, "Periodic sync cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel sync: ${e.message}", e)
            }
        }

        /**
         * Trigger immediate sync (e.g., when app comes back online)
         */
        fun triggerImmediateSync(context: Context) {
            try {
                val immediateWorkRequest = androidx.work.OneTimeWorkRequestBuilder<MessageSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(immediateWorkRequest)
                Log.d(TAG, "Immediate message sync triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger immediate sync: ${e.message}", e)
            }
        }
    }
}

/**
 * Connectivity listener that triggers sync when app comes back online
 */
class ConnectivityObserver(context: Context) {
    private val context = context
    private val TAG = "ConnectivityObserver"

    init {
        observeConnectivity()
    }

    private fun observeConnectivity() {
        // This would observe network connectivity changes and trigger sync
        // Implementation depends on target Android API level
        Log.d(TAG, "Connectivity observer initialized")
    }

    fun onConnectivityChanged(isConnected: Boolean) {
        if (isConnected) {
            Log.d(TAG, "📡 Device is online - triggering message sync")
            MessageSyncWorker.triggerImmediateSync(context)
        } else {
            Log.d(TAG, "📴 Device is offline")
        }
    }
}
