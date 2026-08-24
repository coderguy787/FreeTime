package com.freetime.app.utils

import com.freetime.app.BuildConfig

object VersionCheckUtil {
    private const val TAG = "FREETIME_VERSION"

    fun getCurrentAppVersion(): String = BuildConfig.VERSION_NAME

    fun getCurrentAppVersionCode(): Int = BuildConfig.VERSION_CODE

    fun isVersionCompatible(serverRequiredVersion: String?): Boolean {
        if (serverRequiredVersion == null || serverRequiredVersion.isEmpty()) {
            return true
        }

        try {
            val currentVersion = getCurrentAppVersion()
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val requiredParts = serverRequiredVersion.split(".").map { it.toIntOrNull() ?: 0 }

            // compare version strings like 1.2 vs 1.2.5
            val maxLength = maxOf(currentParts.size, requiredParts.size)
            val currentPadded = currentParts + List(maxLength - currentParts.size) { 0 }
            val requiredPadded = requiredParts + List(maxLength - requiredParts.size) { 0 }

            for (i in 0 until maxLength) {
                when {
                    currentPadded[i] > requiredPadded[i] -> return true
                    currentPadded[i] < requiredPadded[i] -> return false
                }
            }
            return true
        } catch (e: Exception) {
            // unparseable versions are treated as up to date
            android.util.Log.e(TAG, "Error comparing versions: ${e.message}")
            return true
        }
    }

    fun getVersionHeaders(): Map<String, String> = mapOf(
        "X-App-Version" to getCurrentAppVersion(),
        "X-App-Version-Code" to getCurrentAppVersionCode().toString(),
        "X-Client-Type" to "android"
    )
}
