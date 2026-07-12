package com.freetime.app.utils

import com.freetime.app.BuildConfig

object VersionCheckUtil {
    private const val TAG = "FREETIME_VERSION"
    
    /**
     * Get the current app version from BuildConfig
     */
    fun getCurrentAppVersion(): String = BuildConfig.VERSION_NAME
    
    fun getCurrentAppVersionCode(): Int = BuildConfig.VERSION_CODE
    
    /**
     * Check if the app version matches the server requirements
     * @param serverRequiredVersion The minimum required version from server
     * @return true if current version >= required version
     */
    fun isVersionCompatible(serverRequiredVersion: String?): Boolean {
        if (serverRequiredVersion == null || serverRequiredVersion.isEmpty()) {
            return true // No version requirement
        }
        
        try {
            val currentVersion = getCurrentAppVersion()
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val requiredParts = serverRequiredVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            // Pad with zeros to match lengths
            val maxLength = maxOf(currentParts.size, requiredParts.size)
            val currentPadded = currentParts + List(maxLength - currentParts.size) { 0 }
            val requiredPadded = requiredParts + List(maxLength - requiredParts.size) { 0 }
            
            // Compare versions lexicographically (1.2.3 > 1.2.2)
            for (i in 0 until maxLength) {
                when {
                    currentPadded[i] > requiredPadded[i] -> return true
                    currentPadded[i] < requiredPadded[i] -> return false
                }
            }
            return true // Equal versions
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error comparing versions: ${e.message}")
            return true // Allow if comparison fails
        }
    }
    
    /**
     * Create version header for API requests
     * Include app version in every request for server validation
     */
    fun getVersionHeaders(): Map<String, String> = mapOf(
        "X-App-Version" to getCurrentAppVersion(),
        "X-App-Version-Code" to getCurrentAppVersionCode().toString(),
        "X-Client-Type" to "android"
    )
}
