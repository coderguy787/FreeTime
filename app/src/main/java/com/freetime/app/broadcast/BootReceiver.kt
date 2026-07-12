package com.freetime.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.freetime.app.api.FreeTimeApiService
import com.freetime.app.data.local.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ✅ NEW: Boot Receiver to ensure FCM token is registered after device restart
 * This ensures notifications work even if app is force-killed and device reboots
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "📱 Device boot detected or app startup")
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "🔄 Registering FCM token after boot...")
            registerFcmTokenAfterBoot(context)
        }
    }
    
    private fun registerFcmTokenAfterBoot(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        scope.launch {
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (token.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val apiService = FreeTimeApiService(context)
                                    val result = apiService.registerDeviceFcmToken(token)
                                    if (result.isSuccess) {
                                        Log.d("BootReceiver", "✅ FCM token re-registered after boot")
                                    }
                                } catch (e: Exception) {
                                    Log.e("BootReceiver", "❌ Failed to register token: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.e("BootReceiver", "❌ Failed to get FCM token after boot")
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in token registration: ${e.message}", e)
            }
        }
    }
}
