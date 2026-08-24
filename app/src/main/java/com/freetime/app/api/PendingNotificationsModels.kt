package com.freetime.app.api

data class PendingNotificationsResponse(
    val messages: List<PendingMessage>
)

data class PendingMessage(
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val content: String,
    val timestamp: Long
)
