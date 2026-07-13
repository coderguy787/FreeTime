package com.freetime.app.data.local

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("freetime_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CUSTOM_SERVER_IP = "custom_server_ip"
        private const val KEY_TEMP_TOKEN = "temp_token_2fa"
        private const val KEY_TEMP_TOKEN_EXPIRY = "temp_token_expiry"
        private const val KEY_2FA_SETUP_REQUIRED = "2fa_setup_required"
        private const val KEY_REMEMBER_ME_TOKEN = "remember_me_token"
        private const val KEY_REMEMBER_ME_EXPIRY = "remember_me_expiry"
        private const val KEY_REMEMBER_ME_ENABLED = "remember_me_enabled"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ADMIN_ACTION = "admin_action"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_FONT_SIZE_INDEX = "font_size_index"
        private const val KEY_ANIMATION_SPEED_INDEX = "animation_speed_index"
        private const val KEY_ACCENT_COLOR_INDEX = "accent_color_index"
        private const val KEY_COMPACT_MODE_ENABLED = "compact_mode_enabled"
        private const val KEY_LANGUAGE_INDEX = "language_index"
        private const val KEY_APP_VERSION = "app_version"
        private const val KEY_NOTIFY_FRIEND_REQUESTS = "notify_friend_requests"
        private const val KEY_NOTIFY_MESSAGES = "notify_messages"
        private const val KEY_NOTIFY_SOUND = "notify_sound"
        private const val KEY_NOTIFY_GROUP_UPDATES = "notify_group_updates"
        private const val KEY_NOTIFY_MENTION_ALERTS = "notify_mention_alerts"
        private const val KEY_NOTIFY_VIBRATION = "notify_vibration"
        private const val KEY_PROFILE_IMAGE = "profile_image"
        private const val KEY_BANNER_IMAGE = "banner_image"
        private const val KEY_READ_RECEIPTS = "read_receipts"
        private const val KEY_LAST_SEEN_VISIBILITY = "last_seen_visibility"
        private const val KEY_ONLINE_STATUS_VISIBILITY = "online_status_visibility"
        private const val KEY_PROFILE_PHOTO_VISIBILITY = "profile_photo_visibility"
        private const val KEY_MUTED_USERS = "muted_users_set"
        private const val CURRENT_APP_VERSION = 2
    }
    
    fun saveAuthData(token: String, userId: String, username: String, deviceId: String) {
        android.util.Log.d("SharedPrefsHelper", "Saving auth data for user: $username")
        sharedPreferences.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_DEVICE_ID, deviceId)
            putBoolean(KEY_IS_LOGGED_IN, true)
            // Use commit() for synchronous write
            commit()
        }
        android.util.Log.d("SharedPrefsHelper", "Auth data saved successfully")
    }
    
    fun getToken(): String? = sharedPreferences.getString(KEY_TOKEN, null)

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_TOKEN, null)
    
    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)
    
    fun getUsername(): String? = sharedPreferences.getString(KEY_USERNAME, null)
    
    fun setUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }
    
    fun saveUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }
    
    fun getDeviceId(): String? = sharedPreferences.getString(KEY_DEVICE_ID, null)
    
    fun isLoggedIn(): Boolean = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    
    fun clearAuthData() {
        android.util.Log.d("SharedPrefsHelper", "Clearing auth data")
        sharedPreferences.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_IS_LOGGED_IN)
            commit()
        }
    }
    
    fun setServerUrl(url: String) {
        sharedPreferences.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getServerUrl(): String = 
        sharedPreferences.getString(KEY_SERVER_URL, "https://example.com/") ?: "https://example.com/"
    
    // 2FA Token Management
    fun saveTempToken(token: String, setupRequired: Boolean = false) {
        sharedPreferences.edit().apply {
            putString(KEY_TEMP_TOKEN, token)
            putLong(KEY_TEMP_TOKEN_EXPIRY, System.currentTimeMillis() + 30 * 60 * 1000) // 30 min expiry
            putBoolean(KEY_2FA_SETUP_REQUIRED, setupRequired)
            apply()
        }
    }
    
    fun getTempToken(): String? = sharedPreferences.getString(KEY_TEMP_TOKEN, null)
    
    fun isTempTokenValid(): Boolean {
        val token = getTempToken() ?: return false
        val expiry = sharedPreferences.getLong(KEY_TEMP_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() < expiry && token.isNotEmpty()
    }
    
    fun is2FASetupRequired(): Boolean = 
        sharedPreferences.getBoolean(KEY_2FA_SETUP_REQUIRED, false)
    
    fun clearTempToken() {
        sharedPreferences.edit().apply {
            remove(KEY_TEMP_TOKEN)
            remove(KEY_TEMP_TOKEN_EXPIRY)
            remove(KEY_2FA_SETUP_REQUIRED)
            apply()
        }
    }

    // Remember Me Token Management (30 day auto-login)
    fun saveRememberMeToken(token: String, userId: String, username: String) {
        val expiryTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 // 30 days from now
        android.util.Log.d("SharedPrefsHelper", "✓ Saving remember me token for: $username")
        android.util.Log.d("SharedPrefsHelper", "  Auto-login enabled for 30 days")
        
        sharedPreferences.edit().apply {
            putString(KEY_REMEMBER_ME_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putLong(KEY_REMEMBER_ME_EXPIRY, expiryTime)
            putBoolean(KEY_REMEMBER_ME_ENABLED, true)
            // Use commit() instead of apply() to ensure synchronous write
            commit()
        }
        android.util.Log.d("SharedPrefsHelper", "✓ Remember Me token saved successfully - will auto-login until ~30 days from now")
    }

    fun getRememberMeToken(): String? = sharedPreferences.getString(KEY_REMEMBER_ME_TOKEN, null)

    fun getUserIdFromRememberMe(): String? = sharedPreferences.getString(KEY_USER_ID, null)

    fun getUsernameFromRememberMe(): String? = sharedPreferences.getString(KEY_USERNAME, null)

    fun isRememberMeTokenValid(): Boolean {
        val token = getRememberMeToken() ?: return false.also { 
            android.util.Log.d("SharedPrefsHelper", "isRememberMeTokenValid: No token found") 
        }
        val expiry = sharedPreferences.getLong(KEY_REMEMBER_ME_EXPIRY, 0)
        val currentTime = System.currentTimeMillis()
        val isValid = currentTime < expiry && token.isNotEmpty()
        
        if (isValid) {
            val daysRemaining = (expiry - currentTime) / (24 * 60 * 60 * 1000)
            android.util.Log.d("SharedPrefsHelper", "isRememberMeTokenValid: ✓ Token valid, $daysRemaining days remaining")
        } else {
            android.util.Log.d("SharedPrefsHelper", "isRememberMeTokenValid: ✗ Token invalid/expired (currentTime=$currentTime, expiry=$expiry)")
        }
        return isValid
    }

    fun getRemainingRememberMeDays(): Long {
        val expiry = sharedPreferences.getLong(KEY_REMEMBER_ME_EXPIRY, 0)
        val currentTime = System.currentTimeMillis()
        return if (expiry > currentTime) {
            (expiry - currentTime) / (24 * 60 * 60 * 1000)
        } else {
            0
        }
    }

    fun isRememberMeEnabled(): Boolean = sharedPreferences.getBoolean(KEY_REMEMBER_ME_ENABLED, false)

    fun clearRememberMeToken() {
        android.util.Log.d("SharedPrefsHelper", "Clearing remember me token")
        sharedPreferences.edit().apply {
            remove(KEY_REMEMBER_ME_TOKEN)
            remove(KEY_REMEMBER_ME_EXPIRY)
            putBoolean(KEY_REMEMBER_ME_ENABLED, false)
            commit()
        }
    }

    // Additional token methods for backward compatibility
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun saveRefreshToken(refreshToken: String) {
        sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    
    // Server IP configuration methods
    fun saveCustomServerIp(ip: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_SERVER_IP, ip).apply()
    }
    
    fun getCustomServerIp(): String? = sharedPreferences.getString(KEY_CUSTOM_SERVER_IP, null)
    
    fun clearCustomServerIp() {
        sharedPreferences.edit().remove(KEY_CUSTOM_SERVER_IP).apply()
    }
    
    // Additional profile/email methods
    fun getEmail(): String? = sharedPreferences.getString(KEY_EMAIL, null)
    
    fun saveEmail(email: String) {
        sharedPreferences.edit().putString(KEY_EMAIL, email).apply()
    }
    
    // Admin action tracking
    fun saveAdminAction(action: String) {
        sharedPreferences.edit().putString(KEY_ADMIN_ACTION, action).apply()
    }
    
    fun getAdminAction(): String? = sharedPreferences.getString(KEY_ADMIN_ACTION, null)

    // Dark mode preference
    fun setDarkModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply()
    }

    fun isDarkModeEnabled(): Boolean = sharedPreferences.getBoolean(KEY_DARK_MODE_ENABLED, true)

    // Display settings persistence
    fun setFontSizeIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_FONT_SIZE_INDEX, index).apply()
    }

    fun getFontSizeIndex(): Int = sharedPreferences.getInt(KEY_FONT_SIZE_INDEX, 1) // Default: Medium

    fun setAnimationSpeedIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_ANIMATION_SPEED_INDEX, index).apply()
    }

    fun getAnimationSpeedIndex(): Int = sharedPreferences.getInt(KEY_ANIMATION_SPEED_INDEX, 1) // Default: Normal

    fun setAccentColorIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_ACCENT_COLOR_INDEX, index).apply()
    }

    fun getAccentColorIndex(): Int = sharedPreferences.getInt(KEY_ACCENT_COLOR_INDEX, 0) // Default: Purple

    fun setCompactModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_COMPACT_MODE_ENABLED, enabled).apply()
    }

    fun isCompactModeEnabled(): Boolean = sharedPreferences.getBoolean(KEY_COMPACT_MODE_ENABLED, false)

    fun setLanguageIndex(index: Int) {
        sharedPreferences.edit().putInt(KEY_LANGUAGE_INDEX, index).apply()
    }

    fun getLanguageIndex(): Int = sharedPreferences.getInt(KEY_LANGUAGE_INDEX, 0) // Default: English

    // First-run detection and initialization
    fun isFirstRun(): Boolean {
        val savedVersion = sharedPreferences.getInt(KEY_APP_VERSION, 0)
        val isFirst = savedVersion < CURRENT_APP_VERSION
        android.util.Log.d("SharedPrefsHelper", "isFirstRun: savedVersion=$savedVersion, currentVersion=$CURRENT_APP_VERSION, isFirst=$isFirst")
        return isFirst
    }

    fun markAppInitialized() {
        android.util.Log.d("SharedPrefsHelper", "Marking app as initialized (version=$CURRENT_APP_VERSION)")
        sharedPreferences.edit().putInt(KEY_APP_VERSION, CURRENT_APP_VERSION).apply()
    }

    fun clearAllAuthenticationData() {
        android.util.Log.d("SharedPrefsHelper", "Clearing ALL authentication data for fresh start")
        sharedPreferences.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            remove(KEY_IS_LOGGED_IN)
            remove(KEY_REMEMBER_ME_TOKEN)
            remove(KEY_REMEMBER_ME_EXPIRY)
            remove(KEY_REMEMBER_ME_ENABLED)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TEMP_TOKEN)
            remove(KEY_TEMP_TOKEN_EXPIRY)
            remove(KEY_2FA_SETUP_REQUIRED)
            // Keep settings but remove auth
            commit()
        }
        android.util.Log.d("SharedPrefsHelper", "All authentication data cleared")
    }

    // Notification Type Settings
    fun setNotifyFriendRequests(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_FRIEND_REQUESTS, enabled).apply()
    }

    fun isNotifyFriendRequestsEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_FRIEND_REQUESTS, true)

    fun setNotifyMessages(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_MESSAGES, enabled).apply()
    }

    fun isNotifyMessagesEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_MESSAGES, true)

    fun setNotifySound(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_SOUND, enabled).apply()
    }

    fun isNotifySoundEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_SOUND, true)

    fun setNotifyGroupUpdates(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_GROUP_UPDATES, enabled).apply()
    }

    fun isNotifyGroupUpdatesEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_GROUP_UPDATES, true)

    fun setNotifyMentionAlerts(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_MENTION_ALERTS, enabled).apply()
    }

    fun isNotifyMentionAlertsEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_MENTION_ALERTS, false)

    fun setNotifyVibration(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_VIBRATION, enabled).apply()
    }

    fun isNotifyVibrationEnabled(): Boolean = sharedPreferences.getBoolean(KEY_NOTIFY_VIBRATION, true)

    // Per-user notification muting — silences all notifications from a specific user
    fun muteUser(userId: String) {
        val muted = sharedPreferences.getStringSet(KEY_MUTED_USERS, emptySet())!!.toMutableSet()
        muted.add(userId)
        sharedPreferences.edit().putStringSet(KEY_MUTED_USERS, muted).apply()
    }

    fun unmuteUser(userId: String) {
        val muted = sharedPreferences.getStringSet(KEY_MUTED_USERS, emptySet())!!.toMutableSet()
        muted.remove(userId)
        sharedPreferences.edit().putStringSet(KEY_MUTED_USERS, muted).apply()
    }

    fun isUserMuted(userId: String): Boolean =
        sharedPreferences.getStringSet(KEY_MUTED_USERS, emptySet())!!.contains(userId)

    fun getMutedUsers(): Set<String> =
        sharedPreferences.getStringSet(KEY_MUTED_USERS, emptySet())!!.toSet()

    // Notification channel upgrade tracking
    fun areNotificationChannelsUpgraded(): Boolean = sharedPreferences.getBoolean("notification_channels_v2", false)

    fun setNotificationChannelsUpgraded(upgraded: Boolean) {
        sharedPreferences.edit().putBoolean("notification_channels_v2", upgraded).apply()
    }

    // Language settings
    fun setLanguage(languageCode: String) {
        sharedPreferences.edit().putString("app_language", languageCode).apply()
    }

    fun getLanguage(): String = sharedPreferences.getString("app_language", "en") ?: "en"

    // Generic preference methods
    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun saveBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun saveInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    // Profile image methods
    fun saveProfileImage(imageUrl: String) {
        sharedPreferences.edit().putString(KEY_PROFILE_IMAGE, imageUrl).apply()
    }

    fun getProfileImage(): String? = sharedPreferences.getString(KEY_PROFILE_IMAGE, null)

    fun clearProfileImage() {
        sharedPreferences.edit().remove(KEY_PROFILE_IMAGE).apply()
    }

    fun saveBannerImage(imageUrl: String) {
        sharedPreferences.edit().putString(KEY_BANNER_IMAGE, imageUrl).apply()
    }

    fun getBannerImage(): String? = sharedPreferences.getString(KEY_BANNER_IMAGE, null)

    fun clearBannerImage() {
        sharedPreferences.edit().remove(KEY_BANNER_IMAGE).apply()
    }

    // Privacy & WhatsApp-like settings
    fun setReadReceipts(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_READ_RECEIPTS, enabled).apply()
    }

    fun isReadReceiptsEnabled(): Boolean = sharedPreferences.getBoolean(KEY_READ_RECEIPTS, true)

    fun setLastSeenVisibility(visibility: String) {
        sharedPreferences.edit().putString(KEY_LAST_SEEN_VISIBILITY, visibility).apply()
    }

    fun getLastSeenVisibility(): String = sharedPreferences.getString(KEY_LAST_SEEN_VISIBILITY, "everyone") ?: "everyone"

    fun setOnlineStatusVisibility(visibility: String) {
        sharedPreferences.edit().putString(KEY_ONLINE_STATUS_VISIBILITY, visibility).apply()
    }

    fun getOnlineStatusVisibility(): String = sharedPreferences.getString(KEY_ONLINE_STATUS_VISIBILITY, "everyone") ?: "everyone"

    fun setProfilePhotoVisibility(visibility: String) {
        sharedPreferences.edit().putString(KEY_PROFILE_PHOTO_VISIBILITY, visibility).apply()
    }

    fun getProfilePhotoVisibility(): String = sharedPreferences.getString(KEY_PROFILE_PHOTO_VISIBILITY, "everyone") ?: "everyone"

    // FCM token methods
    fun saveFcmToken(token: String) {
        sharedPreferences.edit().putString("fcm_token", token).apply()
    }

    fun getFcmToken(): String? = sharedPreferences.getString("fcm_token", null)
    
    // ✅ OFFLINE SUPPORT: Cache friends list for when Socket.IO is disconnected
    fun saveFriendsCache(friendsJson: String) {
        try {
            sharedPreferences.edit().putString("cached_friends", friendsJson).apply()
            sharedPreferences.edit().putLong("cached_friends_timestamp", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            android.util.Log.w("SharedPrefsHelper", "Failed to cache friends: ${e.message}")
        }
    }
    
    fun getCachedFriends(): String? {
        return try {
            sharedPreferences.getString("cached_friends", null)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCachedFriendsTimestamp(): Long {
        return sharedPreferences.getLong("cached_friends_timestamp", 0L)
    }

    // In-app update: skip version
    fun setSkippedVersion(versionCode: Int) {
        sharedPreferences.edit().putInt("skipped_update_version", versionCode).apply()
    }

    fun getSkippedVersion(): Int {
        return sharedPreferences.getInt("skipped_update_version", 0)
    }

    // In-app update: pending update ID (from admin-launched update)
    fun setPendingUpdateId(updateId: String) {
        sharedPreferences.edit().putString("pending_update_id", updateId).apply()
    }

    fun getPendingUpdateId(): String {
        return sharedPreferences.getString("pending_update_id", "") ?: ""
    }

    fun clearPendingUpdateId() {
        sharedPreferences.edit().remove("pending_update_id").apply()
    }
}