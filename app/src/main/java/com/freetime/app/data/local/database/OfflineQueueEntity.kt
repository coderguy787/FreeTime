package com.freetime.app.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// messages written while offline, sent once back online
@Entity(tableName = "offline_queue")
data class OfflineQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String = "",
    val recipientId: String = "",
    val content: String = "",
    val messageType: String = "text",
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val fileName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val status: String = "pending"
)
