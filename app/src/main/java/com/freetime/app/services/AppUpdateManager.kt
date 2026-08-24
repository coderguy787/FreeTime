package com.freetime.app.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.freetime.app.BuildConfig
import com.freetime.app.data.network.ApiClient
import com.freetime.app.data.network.VersionInfoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppUpdateManager {
    private const val TAG = "FREETIME_UPDATE"
    private var downloadReceiverRegistered = false
    private var currentReceiver: BroadcastReceiver? = null
    private var currentContext: Context? = null

    suspend fun checkForUpdate(context: Context): VersionInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val api = ApiClient.getInstance()
                val response = api.getVersionInfo()
                if (response.isSuccessful) {
                    response.body()
                } else {
                    android.util.Log.w(TAG, "Version check failed: ${response.code()}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Version check error: ${e.message}")
                null
            }
        }
    }

    fun isUpdateAvailable(info: VersionInfoResponse): Boolean {
        return info.latestVersionCode > BuildConfig.VERSION_CODE
    }

    // downloads the new apk via the system download manager
    fun downloadApk(context: Context, info: VersionInfoResponse, onComplete: (downloadId: Long) -> Unit): Long {
        if (info.downloadUrl.isNullOrBlank()) {
            android.util.Log.e(TAG, "Download URL is empty")
            Toast.makeText(context, "Update not ready yet. Try again later.", Toast.LENGTH_LONG).show()
            return -1L
        }
        val cleanVersion = info.latestVersion.removePrefix("v")
        val fileName = "FreeTimeApp-v$cleanVersion.apk"
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("FreeTime Update")
                .setDescription("Downloading v$cleanVersion...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setRequiresCharging(false)
                .setMimeType("application/vnd.android.package-archive")

            val downloadId = try {
                request.setDestinationInExternalFilesDir(context, null, fileName)
                downloadManager.enqueue(request)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "App dir download failed, falling back to Downloads: ${e.message}")
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                downloadManager.enqueue(request)
            }

            unregisterReceiver()
            val receiver = DownloadReceiver(onComplete)
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(receiver, filter)
            currentReceiver = receiver
            currentContext = context
            downloadReceiverRegistered = true

            android.util.Log.d(TAG, "Download started: id=$downloadId, url=${info.downloadUrl}")
            return downloadId
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Download error: ${e.message}")
            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    private fun unregisterReceiver() {
        if (downloadReceiverRegistered && currentReceiver != null) {
            try {
                currentContext?.unregisterReceiver(currentReceiver!!)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            currentReceiver = null
            currentContext = null
            downloadReceiverRegistered = false
        }
    }

    fun installApk(context: Context, downloadId: Long) {
        var cursor: android.database.Cursor? = null
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            cursor = downloadManager.query(query)
            if (cursor == null || !cursor.moveToFirst()) {
                android.util.Log.e(TAG, "Download not found: id=$downloadId")
                return
            }

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (statusIndex == -1 || uriIndex == -1) {
                android.util.Log.e(TAG, "Download cursor missing required columns")
                return
            }

            val status = cursor.getInt(statusIndex)
            val localUri = cursor.getString(uriIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL && !localUri.isNullOrEmpty()) {
                val apkUri = Uri.parse(localUri)
                val path = apkUri.path
                if (path.isNullOrEmpty()) {
                    android.util.Log.e(TAG, "APK URI path is null")
                    return
                }
                val fileProviderUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(path)
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileProviderUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } else {
                android.util.Log.e(TAG, "Download not successful: status=$status")
                Toast.makeText(context, "Download failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Install error: ${e.message}")
            Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try { cursor?.close() } catch (_: Exception) {}
        }
    }

    private class DownloadReceiver(
        private val onComplete: (downloadId: Long) -> Unit
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                onComplete(downloadId)
            }
        }
    }
}
