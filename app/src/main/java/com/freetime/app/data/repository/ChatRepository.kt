package com.freetime.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.local.SharedPreferencesHelper
import android.content.Context
import com.freetime.app.data.network.Friend

/**
 * Chat Repository - Uses real API calls to fetch chats/friends
 */
class ChatRepository(private val context: Context? = null) {
    private val apiService = ApiClient.getInstance()
    
    fun getChats(): Flow<List<Friend>> = flow {
        try {
            val prefs = context?.let { SharedPreferencesHelper(it) }
            val token = prefs?.getToken()
            
            if (!token.isNullOrEmpty()) {
                val response = apiService.getFriendsList("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    emit(response.body() ?: emptyList())
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
    
    suspend fun getChat(chatId: String) = null
    suspend fun createChat(participantId: String) = Unit
    suspend fun deleteChat(chatId: String) = Unit
}
