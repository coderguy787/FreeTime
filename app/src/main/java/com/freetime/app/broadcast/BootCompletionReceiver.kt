package com.freetime.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootCompletion", " Device boot completed - re-registering FCM token")

            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d("BootCompletion", " FCM token refreshed on boot: ${token.take(20)}...")

                        val prefs = context.getSharedPreferences("fcm", Context.MODE_PRIVATE)
                        // save token locally first
                        prefs.edit().putString("fcm_token", token).apply()

                        val authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                        val jwtToken = authPrefs.getString("jwt_token", "")

                        // only re-register if we still have a valid session
                        if (jwtToken?.isNotEmpty() == true) {
                            Log.d("BootCompletion", " User logged in - registering FCM token with backend")
                            CoroutineScope(Dispatchers.Default).launch {
                                try {
                                    val apiService = com.freetime.app.api.FreeTimeApiService(context)
                                    apiService.registerDeviceFcmToken(token)
                                    Log.d("BootCompletion", " FCM token registered with backend after boot")
                                } catch (e: Exception) {
                                    Log.e("BootCompletion", " Failed to register FCM token: ${e.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BootCompletion", " Error refreshing FCM token: ${e.message}")
            }
        }
    }
}
