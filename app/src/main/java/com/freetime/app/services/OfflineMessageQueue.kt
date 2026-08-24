package com.freetime.app.services

import android.content.Context
import android.util.Log
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.database.OfflineQueueEntity
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.SendMessageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object OfflineMessageQueue {
    private const val TAG = "OfflineMessageQueue"
    private const val MAX_RETRIES = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var db: FreeTimeDatabase? = null

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _isFlushing = MutableStateFlow(false)
    val isFlushing: StateFlow<Boolean> = _isFlushing.asStateFlow()

    fun init(context: Context) {
        db = FreeTimeDatabase.getInstance(context.applicationContext)
        scope.launch {
            db!!.offlineQueueDao().getPendingMessagesFlow().collect { list ->
                _pendingCount.value = list.size
            }
        }
    }

    // queue for messages composed while offline
    suspend fun enqueue(
        chatId: String,
        content: String,
        recipientId: String = chatId,
        mediaPath: String? = null,
        mediaType: String? = null,
        fileName: String? = null
    ): Long {
        val dao = db?.offlineQueueDao() ?: return -1
        val entry = OfflineQueueEntity(
            chatId = chatId,
            recipientId = recipientId,
            content = content,
            mediaPath = mediaPath,
            mediaType = mediaType,
            fileName = fileName
        )
        val id = dao.insert(entry)
        Log.i(TAG, "Enqueued offline message #$id for chat=$chatId")
        return id
    }

    suspend fun remove(id: Long) {
        db?.offlineQueueDao()?.deleteById(id)
    }

    suspend fun flush(context: Context) {
        val dao = db?.offlineQueueDao() ?: return
        val pending = dao.getPendingMessages()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending messages to flush")
            return
        }
        Log.i(TAG, "Flushing ${pending.size} pending message(s)")
        val token = SharedPreferencesHelper(context.applicationContext).getToken()
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No auth token — cannot flush")
            return
        }

        _isFlushing.value = true
        try {
            for (entry in pending) {
                try {
                    val sendRequest = SendMessageRequest(
                        recipientId = entry.recipientId,
                        content = entry.content
                    )
                    val response = ApiClient.getInstance().sendMessage(sendRequest, "Bearer $token")
                    if (response.isSuccessful) {
                        dao.markSent(entry.id)
                        Log.i(TAG, "Flushed message #${entry.id} successfully")
                    } else {
                        dao.markFailed(entry.id, "HTTP ${response.code()}")
                        Log.w(TAG, "Flush HTTP error #${entry.id}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Flush failed for #${entry.id}: ${e.message}")
                    dao.markFailed(entry.id, e.message ?: "Unknown error")
                }
            }
        } finally {
            _isFlushing.value = false
        }

        try {
            dao.cleanupOldSent(System.currentTimeMillis() - 86_400_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed: ${e.message}")
        }
    }
}
