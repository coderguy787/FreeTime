package com.freetime.app.security

import android.content.Context
import android.util.Log
import java.util.UUID

class MediaCache(private val context: Context) {
    private val encryption = MediaEncryption(context)

    data class CachedMedia(
        val mediaId: String,
        val fileName: String,
        val mimeType: String,
        val encryptedData: ByteArray,
        val mediaKey: String,
        val timestamp: Long = System.currentTimeMillis(),
        var isApproved: Boolean = false
    )

    companion object {
        private val cache = mutableMapOf<String, CachedMedia>()
        // decrypted media cached in memory only
        private const val CACHE_EXPIRY_MS = 3600000L
    }

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
            isApproved = true
        )

        return mediaId
    }

    fun getMedia(mediaId: String): ByteArray? {
        val entry = cache[mediaId] ?: return null

        // expired entries are dropped when accessed
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
