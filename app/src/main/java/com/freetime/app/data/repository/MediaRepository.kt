package com.freetime.app.data.repository

import android.util.Log
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.ApiService
import com.freetime.app.data.models.MediaDownloadRequestDto
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.local.encryption.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

/**
 * Media Repository - Handles media downloads, decryption, and request management
 * Integrates with API for download request workflow and WebSocket for approvals
 */
class MediaRepository(
    private val apiService: ApiService,
    private val encryptionManager: EncryptionManager,
    private val prefs: SharedPreferencesHelper
) {
    companion object {
        private const val TAG = "MediaRepository"
    }

    /**
     * Request media download - send request to media owner
     * Triggers: media.download.requested WebSocket event to owner
     */
    suspend fun requestMediaDownload(
        mediaId: String,
        reason: String = "User requested download"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            
            val request = MediaDownloadRequestDto(
                mediaId = mediaId,
                reason = reason
            )
            
            val response = apiService.requestMediaDownload(
                mediaId = mediaId,
                request = request,
                token = "Bearer $token"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Media download requested: $mediaId")
                val requestId = response.body()?.downloadRequest?.id ?: ""
                Result.success(requestId)
            } else {
                val errorMsg = "Failed to request download: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting media download: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Approve media download - owner approves requester's download
     * Triggers: media.download.approved WebSocket event to requester
     * Returns download link with expiration time (7 days)
     */
    suspend fun approveMediaDownload(
        requestId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            
            val response = apiService.approveMediaDownload(
                requestId = requestId,
                token = "Bearer $token"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Media download approved: $requestId")
                val downloadUrl = response.body()?.downloadUrl ?: ""
                Result.success(downloadUrl)
            } else {
                val errorMsg = "Failed to approve download: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error approving media download: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Deny media download - owner rejects requester's download
     * Triggers: media.download.denied WebSocket event to requester
     */
    suspend fun denyMediaDownload(
        requestId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext Result.failure(Exception("No auth token"))
            
            val response = apiService.denyMediaDownload(
                requestId = requestId,
                token = "Bearer $token"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Media download denied: $requestId")
                Result.success(Unit)
            } else {
                val errorMsg = "Failed to deny download: ${response.code()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error denying media download: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Download encrypted media file from server
     * Server provides encrypted file which will be decrypted locally
     */
    suspend fun downloadMediaFile(
        downloadUrl: String
    ): Result<ResponseBody> = withContext(Dispatchers.IO) {
        try {
            val finalUrl = if (downloadUrl.startsWith("http")) downloadUrl else {
                val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                if (downloadUrl.startsWith("/")) "$baseUrl$downloadUrl" else "$baseUrl/$downloadUrl"
            }
            
            val response = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder()
                    .url(finalUrl)
                    .addHeader("Authorization", "Bearer ${prefs.getToken() ?: ""}")
                    .build()
            ).execute()
            
            if (response.isSuccessful && response.body != null) {
                Log.d(TAG, "Media file downloaded successfully from $finalUrl")
                Result.success(response.body!!)
            } else {
                val errorMsg = "Failed to download file from $finalUrl: ${response.code}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading media from $downloadUrl: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Decrypt downloaded media file
     * Removes encryption layer for local storage/display
     */
    suspend fun decryptMediaFile(
        encryptedFileBytes: ByteArray,
        encryptionKey: String?,
        iv: String?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (encryptionKey.isNullOrEmpty() || iv.isNullOrEmpty()) {
                // Fallback to local decryption if no server key (though this shouldn't happen for server files)
                Log.w(TAG, "No server encryption key provided, attempting local decryption fallback")
                val decryptedBytes = encryptionManager.decryptBytes(encryptedFileBytes)
                return@withContext Result.success(decryptedBytes)
            }

            // Use the specialized media decryption method
            val decryptedBytes = encryptionManager.decryptMediaBytes(
                encryptedFileBytes,
                encryptionKey,
                iv
            )

            Log.d(TAG, "Media file decrypted successfully using server keys")
            Result.success(decryptedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting media: ${e.message}")
            Result.failure(Exception("Decryption failed: ${e.message}"))
        }
    }

    /**
     * Save decrypted media to device storage
     * Returns file path for display/sharing
     */
    suspend fun saveMediaLocally(
        fileBytes: ByteArray,
        fileName: String,
        cacheDir: File
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Create cache file for temporary storage
            val mediaFile = File(cacheDir, fileName)
            mediaFile.writeBytes(fileBytes)
            
            Log.d(TAG, "Media saved to: ${mediaFile.absolutePath}")
            Result.success(mediaFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all active download requests for media owned by user
     * Used to populate approval/denial UI
     */
    suspend fun getPendingDownloadRequests(
    ): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            // Note: This endpoint would need to be added to backend
            // GET /api/media/download-requests/pending
            // For now, return empty list - implement when backend adds endpoint
            Log.d(TAG, "Getting pending download requests")
            Result.success(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending requests: ${e.message}")
            Result.failure(e)
        }
    }
}
