package com.freetime.app.security

import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * Enhanced Media Cache with E2E encryption support.
 * Stores media along with its specific encryption key.
 */
class MediaCache(private val context: Context) {

    private val encryption = MediaEncryption(context)
    
    data class CachedMedia(
        val mediaId: String,
        val fileName: String,
        val mimeType: String,
        val encryptedData: ByteArray,
        val mediaKey: String, // E2E key for this specific file
        val timestamp: Long = System.currentTimeMillis(),
        var isApproved: Boolean = false
    )

    companion object {
        private val cache = mutableMapOf<String, CachedMedia>()
        private const val CACHE_EXPIRY_MS = 3600000L // 1 hour
    }

    /**
     * Add media to cache with a specific key.
     */
    fun addMediaWithKey(
        fileData: ByteArray, 
        fileName: String, 
        mimeType: String, 
        mediaKey: String
    ): String {
        val mediaId = UUID.randomUUID().toString()
        val encrypted = encryption.encryptMedia(fileData, mediaKey)
        
        cache[mediaId] = CachedMedia(
            mediaId = mediaId,
            fileName = fileName,
            mimeType = mimeType,
            encryptedData = encrypted,
            mediaKey = mediaKey,
            isApproved = true // Sender's own media is always approved
        )
        
        return mediaId
    }

    /**
     * Get decrypted media from cache.
     */
    fun getMedia(mediaId: String): ByteArray? {
        val entry = cache[mediaId] ?: return null
        
        // Check expiry
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            cache.remove(mediaId)
            return null
        }
        
        return try {
            encryption.decryptMedia(entry.encryptedData, entry.mediaKey)
        } catch (e: Exception) {
            Log.e("MediaCache", "Failed to decrypt cached media: ${e.message}")
            null
        }
    }

    fun approveMedia(mediaId: String) {
        cache[mediaId]?.isApproved = true
    }

    fun isMediaApproved(mediaId: String): Boolean {
        return cache[mediaId]?.isApproved == true
    }

    fun clearExpiredEntries() {
        val now = System.currentTimeMillis()
        val toRemove = cache.filter { now - it.value.timestamp > CACHE_EXPIRY_MS }.keys
        toRemove.forEach { cache.remove(it) }
    }
}
