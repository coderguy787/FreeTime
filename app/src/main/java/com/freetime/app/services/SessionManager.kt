package com.freetime.app.services

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import org.json.JSONObject
import java.util.*

/**
 * SessionManager - Handle device tracking and session management
 * Prevents multiple concurrent logins to the same account
 */
class SessionManager(private val context: Context) {
    private val TAG = "SessionManager"
    private val prefs = SharedPreferencesHelper(context)
    
    /**
     * Generate or retrieve unique device ID
     */
    fun getDeviceId(): String {
        // Generate a stable ID based on ANDROID_ID
        // This persists across re-installs on the same device
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = if (androidId != null && androidId.isNotEmpty()) {
            androidId
        } else {
            // Fallback to stored ID or new UUID if ANDROID_ID is unavailable
            val stored = prefs.getString("DEVICE_ID", "")
            if (stored != null && stored.isNotEmpty()) {
                stored
            } else {
                val newId = UUID.randomUUID().toString()
                prefs.saveString("DEVICE_ID", newId)
                newId
            }
        }

        Log.d(TAG, "✅ Retrieved device ID: $deviceId")
        return deviceId
    }

    /**
     * Collect device information for session creation
     */
    fun getDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("platform", "Android")
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".take(50))
            put("osVersion", Build.VERSION.RELEASE)
            put("appVersion", "1.0.0")  // TODO: Get from BuildConfig.VERSION_NAME
            put("androidId", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
            put("buildId", Build.DISPLAY)
        }
    }

    /**
     * Save session info locally after successful login
     */
    fun saveSession(sessionId: String, deviceId: String, token: String) {
        prefs.saveString("SESSION_ID", sessionId)
        prefs.saveString("DEVICE_ID", deviceId)
        prefs.saveLong("SESSION_LOGIN_TIME", System.currentTimeMillis())
        Log.d(TAG, "✅ Session saved - ID: $sessionId, Device: $deviceId")
    }

    /**
     * Get stored session info
     */
    fun getSession(): Map<String, Any> {
        return mapOf(
            "sessionId" to (prefs.getString("SESSION_ID", "") ?: ""),
            "deviceId" to getDeviceId(),
            "loginTime" to (prefs.getLong("SESSION_LOGIN_TIME", 0L))
        )
    }

    /**
     * Check if current session is still valid
     */
    fun isSessionValid(): Boolean {
        val sessionId = prefs.getString("SESSION_ID", "") ?: ""
        val loginTime = prefs.getLong("SESSION_LOGIN_TIME", 0L)
        
        // Session is valid if:
        // 1. SessionId exists (user logged in)
        // 2. Login was within 30 days
        if (sessionId.isEmpty() || loginTime == 0L) {
            return false
        }

        val daysSinceLogin = (System.currentTimeMillis() - loginTime) / (1000 * 60 * 60 * 24)
        return daysSinceLogin < 30
    }

    /**
     * Clear session on logout
     */
    fun clearSession() {
        prefs.saveString("SESSION_ID", "")
        prefs.saveLong("SESSION_LOGIN_TIME", 0L)
        Log.d(TAG, "🔓 Session cleared")
    }

    /**
     * Handle session terminated notification
     * Called when user logs in from another device
     */
    fun handleSessionTerminated(reason: String, newDeviceInfo: JSONObject? = null, message: String = "") {
        Log.w(TAG, "🔐 SESSION TERMINATED - Reason: $reason")
        Log.w(TAG, "   Message: $message")
        if (newDeviceInfo != null) {
            Log.w(TAG, "   New device: ${newDeviceInfo.optString("deviceName", "Unknown")}")
        }
        
        // Clear current session
        clearSession()
        
        // Store termination info for UI
        prefs.saveString("SESSION_TERMINATION_REASON", reason)
        prefs.saveString("SESSION_TERMINATION_MESSAGE", message)
        if (newDeviceInfo != null) {
            prefs.saveString("SESSION_TERMINATION_DEVICE", newDeviceInfo.toString())
        }
    }

    /**
     * Get termination info (for showing dialog to user)
     */
    fun getTerminationInfo(): Map<String, String> {
        return mapOf(
            "reason" to (prefs.getString("SESSION_TERMINATION_REASON", "") ?: ""),
            "message" to (prefs.getString("SESSION_TERMINATION_MESSAGE", "") ?: ""),
            "device" to (prefs.getString("SESSION_TERMINATION_DEVICE", "") ?: "")
        )
    }

    /**
     * Clear termination info after showing it to user
     */
    fun clearTerminationInfo() {
        prefs.saveString("SESSION_TERMINATION_REASON", "")
        prefs.saveString("SESSION_TERMINATION_MESSAGE", "")
        prefs.saveString("SESSION_TERMINATION_DEVICE", "")
    }
}
