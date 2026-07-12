package com.freetime.app.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class InAppNotification(
    val id: String = System.currentTimeMillis().toString(),
    val type: String, // "message", "call", "missedCall", "friendRequest", "friendAccepted", "groupInvite"
    val title: String,
    val description: String,
    val time: Long = System.currentTimeMillis(),
    val senderId: String = "",
    val inviteId: String = "",
    val isRead: Boolean = false
)

/**
 * Notification store with SharedPreferences persistence.
 * FCM handlers add notifications here, and the NotificationCenter UI reads them.
 */
object InAppNotificationStore {
    private val _notifications = mutableStateListOf<InAppNotification>()
    val notifications: List<InAppNotification> get() = _notifications
    @Volatile
    private var prefs: SharedPreferences? = null
    private const val PREFS_NAME = "freetime_notifications"
    private const val KEY_NOTIFICATIONS = "notifications_json"
    private const val MAX_NOTIFICATIONS = 100

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromDisk()
        }
    }

    @Synchronized
    fun addNotification(notification: InAppNotification) {
        // UI operations on SnapshotStateList should ideally happen on Main thread,
        // but SnapshotStateList is thread-safe for reading/writing.
        _notifications.add(0, notification) // newest first
        while (_notifications.size > MAX_NOTIFICATIONS) {
            _notifications.removeAt(_notifications.size - 1)
        }
        saveToDisk()
    }

    @Synchronized
    fun markAllRead() {
        for (i in _notifications.indices) {
            _notifications[i] = _notifications[i].copy(isRead = true)
        }
        saveToDisk()
    }

    @Synchronized
    fun clearAll() {
        _notifications.clear()
        saveToDisk()
    }

    @Synchronized
    fun removeById(id: String) {
        _notifications.removeAll { it.id == id }
        saveToDisk()
    }

    @Synchronized
    fun removeByType(type: String) {
        _notifications.removeAll { it.type == type }
        saveToDisk()
    }

    @Synchronized
    fun removeByTypeAndSender(type: String, senderId: String) {
        _notifications.removeAll { it.type == type && it.senderId == senderId }
        saveToDisk()
    }

    val unreadCount: Int get() = _notifications.count { !it.isRead }

    @Synchronized
    private fun saveToDisk() {
        val arr = JSONArray()
        for (n in _notifications) {
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("type", n.type)
                put("title", n.title)
                put("description", n.description)
                put("time", n.time)
                put("senderId", n.senderId)
                put("inviteId", n.inviteId)
                put("isRead", n.isRead)
            })
        }
        // Use commit() instead of apply() for synchronous write to prevent race conditions
        // Critical data should be written synchronously before returning
        prefs?.edit()?.putString(KEY_NOTIFICATIONS, arr.toString())?.commit()
    }

    @Synchronized
    private fun loadFromDisk() {
        val json = prefs?.getString(KEY_NOTIFICATIONS, null) ?: return
        try {
            val arr = JSONArray(json)
            val loaded = mutableListOf<InAppNotification>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                loaded.add(InAppNotification(
                    id = obj.optString("id", System.currentTimeMillis().toString()),
                    type = obj.optString("type", "message"),
                    title = obj.optString("title", ""),
                    description = obj.optString("description", ""),
                    time = obj.optLong("time", System.currentTimeMillis()),
                    senderId = obj.optString("senderId", ""),
                    inviteId = obj.optString("inviteId", ""),
                    isRead = obj.optBoolean("isRead", false)
                ))
            }
            _notifications.clear()
            _notifications.addAll(loaded)
        } catch (_: Exception) {}
    }
}
