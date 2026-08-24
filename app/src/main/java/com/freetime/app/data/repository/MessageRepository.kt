package com.freetime.app.data.repository

import com.freetime.app.data.local.database.MessageEntity
import com.freetime.app.data.local.database.FreeTimeDatabase
import com.freetime.app.data.local.encryption.EncryptionManager
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.SendMessageRequest
import com.freetime.app.data.local.SharedPreferencesHelper
import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MessageRepository(
    private val database: FreeTimeDatabase,
    private val encryptionManager: EncryptionManager,
    private val context: Context? = null
) {
    private val messageDao = database.messageDao()
    private val apiService = ApiClient.getInstance()

    fun getMessagesForChat(chatId: String, limit: Int = 100): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByChatId(chatId, limit)
    }

    fun getUnreadMessages(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.getUnreadMessages(chatId)
    }

    suspend fun getLatestMessage(chatId: String): MessageEntity? {
        return messageDao.getLatestMessage(chatId)
    }

    suspend fun getMessageById(messageId: String): MessageEntity? {
        return messageDao.getMessageById(messageId)
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        content: String,
        mediaUrl: String? = null,
        replyToMessageId: String? = null,
        replyToUsername: String? = null,
        replyToText: String? = null
    ): String {
        val messageId = "msg_${System.currentTimeMillis()}"

        // encrypted per chat and sender
        val encryptedContent = encryptionManager.encrypt(
            content,
            associatedData = "$chatId:$senderId"
        )

        val message = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = senderId,
            contentEncrypted = encryptedContent,
            mediaUrl = mediaUrl,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            deletedLocally = false,
            deletedByRecipient = false,
            syncState = "pending",
            replyToMessageId = replyToMessageId,
            replyToUsername = replyToUsername,
            replyToText = replyToText
        )

        messageDao.insertMessage(message)
        return messageId
    }

    fun decryptMessage(message: MessageEntity): String {
        return try {
            encryptionManager.decrypt(
                message.contentEncrypted,
                associatedData = "${message.chatId}:${message.senderId}"
            )
        } catch (e: Exception) {
            // show a placeholder instead of crashing on bad data
            "[Message decryption failed]"
        }
    }

    suspend fun markAsRead(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(isRead = true))
    }

    suspend fun deleteMessageLocally(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(deletedLocally = true))
    }

    suspend fun deleteMessage(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.deleteMessage(message)
    }

    suspend fun updateMessageId(oldId: String, newId: String) {
        messageDao.updateMessageId(oldId, newId)
    }

    suspend fun getAllPendingMessages(): List<MessageEntity> {
        return messageDao.getMessagesBySyncState("pending").first()
    }

    suspend fun getAllFailedMessages(): List<MessageEntity> {
        return messageDao.getMessagesBySyncState("failed").first()
    }

    suspend fun fetchMessagesFromAPI(recipientId: String, limit: Int = 100): List<MessageEntity> {
        return try {
            val prefs = context?.let { SharedPreferencesHelper(it) }
            val token = prefs?.getToken() ?: return emptyList()

            val response = apiService.getMessages(recipientId, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                (response.body() ?: emptyList()).mapNotNull { messageResponse ->
                    try {
                        val senderId = messageResponse.senderId ?: ""
                        val content = messageResponse.content ?: ""
                        val encryptedContent = if (content.isNotEmpty()) {
                            encryptionManager.encrypt(content, "$recipientId:$senderId")
                        } else ""

                        val serverId = messageResponse._id
                        if (serverId.isNotEmpty()) {
                            consolidateLocalMessageId(recipientId, senderId, content, serverId)
                        }

                        MessageEntity(
                            messageId = serverId,
                            chatId = recipientId,
                            senderId = senderId,
                            contentEncrypted = encryptedContent,
                            mediaUrl = null,
                            timestamp = messageResponse.timestamp,
                            isRead = messageResponse.read,
                            deletedLocally = false,
                            deletedByRecipient = false,
                            syncState = "synced",
                            replyToMessageId = messageResponse.replyToMessageId,
                            replyToUsername = messageResponse.replyToUsername,
                            replyToText = messageResponse.replyToText,
                            reactions = Gson().toJson(messageResponse.reactions.keys.toList())
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun consolidateLocalMessageId(
        chatId: String,
        senderId: String,
        plaintextContent: String,
        serverMessageId: String
    ) {
        if (plaintextContent.isEmpty()) return
        try {
            val localMessages = messageDao.getMessagesByChatIdSync(chatId, 100)
            for (local in localMessages) {
                if (local.messageId == serverMessageId) continue
                if (local.senderId != senderId) continue
                if (local.messageId.length == 24 && local.messageId.all { it.isDigit() || it in 'a'..'f' }) continue
                val decrypted = try {
                    encryptionManager.decrypt(local.contentEncrypted, "${local.chatId}:${local.senderId}")
                } catch (e: Exception) {
                    continue
                }
                if (decrypted == plaintextContent) {
                    messageDao.updateMessageId(local.messageId, serverMessageId)
                    return
                }
            }
        } catch (e: Exception) {
        }
    }

    suspend fun sendMessageToAPI(
        recipientId: String,
        content: String
    ): String {
        return try {
            val prefs = context?.let { SharedPreferencesHelper(it) }
            val token = prefs?.getToken() ?: throw Exception("User not authenticated")

            val request = SendMessageRequest(
                recipientId = recipientId,
                content = content
            )

            val response = apiService.sendMessage(request, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                response.body()?._id ?: throw Exception("Message ID is null in response")
            } else {
                throw Exception("Failed to send message")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteAllMessagesForChat(chatId: String) {
        messageDao.deleteAllMessagesForChat(chatId)
    }

    suspend fun getMessageCount(chatId: String): Int {
        return messageDao.getMessageCountInChat(chatId)
    }

    suspend fun updateSyncState(messageId: String, syncState: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(syncState = syncState))
    }
}
