package com.freetime.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

object FileUtils {
    /**
     * Extracts the filename from a URI, ensuring it has the correct extension based on its MIME type.
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        
        // Try to get filename from MediaStore/ContentResolver
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // Try OpenableColumns.DISPLAY_NAME first
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Error getting filename from cursor: ${e.message}")
        }

        // Fallback to URI path
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }

        // Ensure extension is present if we can determine it from MIME type
        if (fileName != null) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType != null) {
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (extension != null && !fileName!!.endsWith(".$extension", ignoreCase = true)) {
                        // Check if it already has SOME extension, if not, add the one from MIME type
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
