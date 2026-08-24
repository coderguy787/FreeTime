package com.freetime.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

object FileUtils {
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Error getting filename from cursor: ${e.message}")
        }

        if (fileName == null) {
            // some providers return no display name, parse the path
            fileName = uri.path?.substringAfterLast('/')
        }

        // add an extension from the mime type if missing
        if (fileName != null) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType != null) {
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (extension != null && !fileName!!.endsWith(".$extension", ignoreCase = true)) {
                        if (!fileName!!.contains(".")) {
                            fileName = "$fileName.$extension"
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FileUtils", "Error ensuring extension: ${e.message}")
            }
        }

        return fileName
    }
}
