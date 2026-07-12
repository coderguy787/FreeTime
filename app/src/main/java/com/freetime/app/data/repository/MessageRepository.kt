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

/**
 * Repository for message operations
 * Handles real API calls for messages with encryption/decryption
 */
class MessageRepository(
    private val database: FreeTimeDatabase,
    private val encryptionManager: EncryptionManager,
    private val context: Context? = null
) {
    // Get the message DAO from the database
    private val messageDao = database.messageDao()
    private val apiService = ApiClient.getInstance()

    /**
     * Get all messages for a specific chat
     */
    fun getMessagesForChat(chatId: String, limit: Int = 100): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByChatId(chatId, limit)
    }

    /**
     * Get unread messages for a chat
     */
    fun getUnreadMessages(chatId: String): Flow<List<MessageEntity>> {
        return messageDao.getUnreadMessages(chatId)
    }

    /**
     * Get the latest message in a chat
     */
    suspend fun getLatestMessage(chatId: String): MessageEntity? {
        return messageDao.getLatestMessage(chatId)
    }

    /**
     * Get a specific message by ID
     */
    suspend fun getMessageById(messageId: String): MessageEntity? {
        return messageDao.getMessageById(messageId)
    }

    /**
     * Send a new message with encryption
     */
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
        
        // Encrypt message content
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

    /**
     * Decrypt a message for display
     */
    fun decryptMessage(message: MessageEntity): String {
        return try {
            encryptionManager.decrypt(
                message.contentEncrypted,
                associatedData = "${message.chatId}:${message.senderId}"
            )
        } catch (e: Exception) {
            "[Message decryption failed]"
        }
    }

    /**
     * Mark message as read
     */
    suspend fun markAsRead(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(isRead = true))
    }

    /**
     * Delete a message locally (soft delete for current user)
     */
    suspend fun deleteMessageLocally(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(deletedLocally = true))
    }

    /**
     * Delete message for all users
     */
    suspend fun deleteMessage(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.deleteMessage(message)
    }

    /**
     * Update local message ID to server-assigned ID to prevent poll-duplicates
     */
    suspend fun updateMessageId(oldId: String, newId: String) {
        val message = messageDao.getMessageById(oldId) ?: return
        messageDao.deleteMessage(message)
        messageDao.insertMessage(message.copy(messageId = newId))
    }

    /**
     * Get all pending messages to sync
     */
    suspend fun getAllPendingMessages(): List<MessageEntity> {
        return messageDao.getMessagesBySyncState("pending").first()
    }

    /**
     * Get all messages that failed to sync
     */
    suspend fun getAllFailedMessages(): List<MessageEntity> {
        return messageDao.getMessagesBySyncState("failed").first()
    }

    /**
     * Fetch messages from API for a specific recipient/chat.
     * Announcements are stored server-side as plaintext and are not encrypted with local keys,
     * so we preserve their content as-is by encrypting them locally with the correct associated data.
     */
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
                        // Announcements and other server-side plaintext messages must be
                        // encrypted with the local key so decryptMessage() can display them.
                        val encryptedContent = if (content.isNotEmpty()) {
                            encryptionManager.encrypt(content, "$recipientId:$senderId")
                        } else ""

                        MessageEntity(
                            messageId = messageResponse._id,
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

    /**
     * Send message to API (real endpoint call)
     */
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

    /**
     * Delete all messages for a chat
     */
    suspend fun deleteAllMessagesForChat(chatId: String) {
        messageDao.deleteAllMessagesForChat(chatId)
    }

    /**
     * Get message count for a chat
     */
    suspend fun getMessageCount(chatId: String): Int {
        return messageDao.getMessageCountInChat(chatId)
    }

    /**
     * Update message sync state
     */
    suspend fun updateSyncState(messageId: String, syncState: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        messageDao.updateMessage(message.copy(syncState = syncState))
    }
}
