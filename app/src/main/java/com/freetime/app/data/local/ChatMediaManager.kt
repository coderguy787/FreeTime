@file:Suppress("UNUSED_PARAMETER")

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.freetime.app.security.MediaCache
import com.freetime.app.security.MediaEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class ChatMediaManager(private val context: Context) {
    private val encryption = MediaEncryption(context)
    private val mediaCache = MediaCache(context)

    companion object {
        private const val TAG = "ChatMediaManager"
        private const val CACHE_CLEANUP_INTERVAL = 3600000L
        private var lastCleanupTime = 0L
    }

    suspend fun encryptAndStoreChatMedia(
        fileData: ByteArray,
        fileName: String,
        mimeType: String,
        recipientId: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            // clean old cache files once an hour
            if (System.currentTimeMillis() - lastCleanupTime > CACHE_CLEANUP_INTERVAL) {
                mediaCache.clearExpiredEntries()
                lastCleanupTime = System.currentTimeMillis()
            }

            val mediaKey = encryption.generateMediaKey()
            val mediaId = mediaCache.addMediaWithKey(fileData, fileName, mimeType, mediaKey)

            Log.d(TAG, "Media E2E encrypted and cached locally: $mediaId")
            Pair(mediaId, mediaKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and store media: ${e.message}", e)
            null
        }
    }

    suspend fun storeReceivedMedia(
        encryptedData: ByteArray,
        mediaKey: String,
        fileName: String,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val mediaId = mediaCache.addMediaWithKey(encryptedData, fileName, mimeType, mediaKey)

            if (mediaId != null) {
                saveMediaToGallery(encryptedData, mediaKey, fileName, mimeType)
            }

            mediaId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store received media: ${e.message}")
            null
        }
    }

    suspend fun saveMediaToGallery(
        encryptedData: ByteArray,
        mediaKey: String,
        fileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val decryptedData = encryption.decryptMedia(encryptedData, mediaKey)
            if (decryptedData == null) {
                Log.e(TAG, "Failed to decrypt media for gallery save")
                return@withContext false
            }

            val relativePath = if (mimeType.startsWith("video/")) {
                "${Environment.DIRECTORY_MOVIES}/FreeTime"
            } else {
                "${Environment.DIRECTORY_PICTURES}/FreeTime"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    // mark pending so galleries don't pick it up mid-write
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (mimeType.startsWith("video/")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val uri = context.contentResolver.insert(collection, contentValues)
            if (uri == null) {
                Log.e(TAG, "Failed to create MediaStore entry")
                return@withContext false
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(decryptedData)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, " Media successfully saved to gallery: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media to gallery: ${e.message}")
            false
        }
    }

    fun getDecryptedMedia(mediaId: String): ByteArray? {
        return mediaCache.getMedia(mediaId)
    }
}
