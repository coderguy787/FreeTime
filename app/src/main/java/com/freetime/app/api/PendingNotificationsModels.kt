package com.freetime.app.api

/**
 * Response data for pending notifications endpoint
 */
data class PendingNotificationsResponse(
    val messages: List<PendingMessage>,
    val calls: List<PendingCall>
)

/**
 * Pending message notification data
 */
data class PendingMessage(
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val content: String,
    val timestamp: Long
)

/**
 * Pending call notification data
 */
data class PendingCall(
    val callerId: String,
    val callerName: String,
    val callerAvatar: String?,
    val callType: String,  // "audio" or "video"
    val timestamp: Long
)
