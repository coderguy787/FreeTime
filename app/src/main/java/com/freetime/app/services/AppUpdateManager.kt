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

    fun downloadApk(context: Context, info: VersionInfoResponse, onComplete: (downloadId: Long) -> Unit): Long {
        val fileName = "FreeTimeApp-v${info.latestVersion}.apk"
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("FreeTime Update")
            .setDescription("Downloading v${info.latestVersion}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)

        if (!downloadReceiverRegistered) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(DownloadReceiver(onComplete), filter)
            downloadReceiverRegistered = true
        }

        android.util.Log.d(TAG, "Download started: id=$downloadId, url=${info.downloadUrl}")
        return downloadId
    }

    fun installApk(context: Context, downloadId: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val status = cursor.getInt(statusIndex)
                val localUri = cursor.getString(uriIndex)
                cursor.close()

                if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                    val apkFile = Uri.parse(localUri)
                    val fileProviderUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(apkFile.path!!)
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
            } else {
                cursor.close()
                android.util.Log.e(TAG, "Download not found: id=$downloadId")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Install error: ${e.message}")
            Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_LONG).show()
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
