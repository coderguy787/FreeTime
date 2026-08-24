package com.freetime.app.services

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import org.json.JSONObject
import java.util.*

class SessionManager(private val context: Context) {
    private val TAG = "SessionManager"
    private val prefs = SharedPreferencesHelper(context)

    // device id used for the session
    fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val deviceId = if (androidId != null && androidId.isNotEmpty()) {
            androidId
        } else {
            val stored = prefs.getString("DEVICE_ID", "")
            if (stored != null && stored.isNotEmpty()) {
                stored
            } else {
                val newId = UUID.randomUUID().toString()
                prefs.saveString("DEVICE_ID", newId)
                newId
            }
        }

        Log.d(TAG, " Retrieved device ID: $deviceId")
        return deviceId
    }

    fun getDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("platform", "Android")
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".take(50))
            put("osVersion", Build.VERSION.RELEASE)
            put("appVersion", "1.0.0")
            put("androidId", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
            put("buildId", Build.DISPLAY)
        }
    }

    fun saveSession(sessionId: String, deviceId: String, token: String) {
        prefs.saveString("SESSION_ID", sessionId)
        prefs.saveString("DEVICE_ID", deviceId)
        prefs.saveLong("SESSION_LOGIN_TIME", System.currentTimeMillis())
        Log.d(TAG, " Session saved - ID: $sessionId, Device: $deviceId")
    }

    fun getSession(): Map<String, Any> {
        return mapOf(
            "sessionId" to (prefs.getString("SESSION_ID", "") ?: ""),
            "deviceId" to getDeviceId(),
            "loginTime" to (prefs.getLong("SESSION_LOGIN_TIME", 0L))
        )
    }

    fun isSessionValid(): Boolean {
        val sessionId = prefs.getString("SESSION_ID", "") ?: ""
        val loginTime = prefs.getLong("SESSION_LOGIN_TIME", 0L)

        if (sessionId.isEmpty() || loginTime == 0L) {
            return false
        }

        val daysSinceLogin = (System.currentTimeMillis() - loginTime) / (1000 * 60 * 60 * 24)
        return daysSinceLogin < 30
    }

    fun clearSession() {
        prefs.saveString("SESSION_ID", "")
        prefs.saveLong("SESSION_LOGIN_TIME", 0L)
        Log.d(TAG, " Session cleared")
    }

    fun handleSessionTerminated(reason: String, newDeviceInfo: JSONObject? = null, message: String = "") {
        Log.w(TAG, " SESSION TERMINATED - Reason: $reason")
        Log.w(TAG, " Message: $message")
        if (newDeviceInfo != null) {
            Log.w(TAG, " New device: ${newDeviceInfo.optString("deviceName", "Unknown")}")
        }

        clearSession()

        prefs.saveString("SESSION_TERMINATION_REASON", reason)
        prefs.saveString("SESSION_TERMINATION_MESSAGE", message)
        if (newDeviceInfo != null) {
            prefs.saveString("SESSION_TERMINATION_DEVICE", newDeviceInfo.toString())
        }
    }

    fun getTerminationInfo(): Map<String, String> {
        return mapOf(
            "reason" to (prefs.getString("SESSION_TERMINATION_REASON", "") ?: ""),
            "message" to (prefs.getString("SESSION_TERMINATION_MESSAGE", "") ?: ""),
            "device" to (prefs.getString("SESSION_TERMINATION_DEVICE", "") ?: "")
        )
    }

    fun clearTerminationInfo() {
        prefs.saveString("SESSION_TERMINATION_REASON", "")
        prefs.saveString("SESSION_TERMINATION_MESSAGE", "")
        prefs.saveString("SESSION_TERMINATION_DEVICE", "")
    }
}
