package com.freetime.app.data.storage

import android.content.Context
import androidx.room.*
import com.freetime.app.data.local.SharedPreferencesHelper
import java.io.File
import java.util.*
import kotlin.math.roundToLong

class StorageManager(private val context: Context) {
    companion object {
        const val WARNING_THRESHOLD = 500L * 1024 * 1024
        const val CRITICAL_THRESHOLD = 900L * 1024 * 1024
        const val MAX_STORAGE_LIMIT = 1024L * 1024 * 1024

        const val OLD_MEDIA_AGE_DAYS = 30
        const val ARCHIVE_AGE_DAYS = 90

        const val IMAGE_COMPRESSION_QUALITY = 75
        const val VIDEO_COMPRESSION_BITRATE = "2M"
    }

    private val context_ = context
    private val prefs = SharedPreferencesHelper(context)
    private val storageDir = File(context.filesDir, "encrypted_storage")
    private val mediaDir = File(context.cacheDir, "encrypted_media")
    private val archiveDir = File(context.filesDir, "archived_messages")

    init {
        storageDir.mkdirs()
        mediaDir.mkdirs()
        archiveDir.mkdirs()
    }

    fun getTotalStorageUsage(): Long {
        return storageDir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        } + mediaDir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }
    }

    fun getStorageBreakdown(): StorageBreakdown {
        val messagesSize = getDirectorySize(File(storageDir, "messages"))
        val mediaSize = getDirectorySize(mediaDir)
        val archiveSize = getDirectorySize(archiveDir)
        val databaseSize = context.getDatabasePath("freetime_db").length()

        return StorageBreakdown(
            messagesSize = messagesSize,
            mediaSize = mediaSize,
            archiveSize = archiveSize,
            databaseSize = databaseSize,
            totalSize = messagesSize + mediaSize + archiveSize + databaseSize
        )
    }

    fun getStorageStatus(): StorageStatus {
        val total = getTotalStorageUsage()
        return when {
            total >= CRITICAL_THRESHOLD -> StorageStatus.CRITICAL
            total >= WARNING_THRESHOLD -> StorageStatus.WARNING
            else -> StorageStatus.HEALTHY
        }
    }

    fun getStoragePercentage(): Int {
        val total = getTotalStorageUsage()
        return ((total.toDouble() / MAX_STORAGE_LIMIT.toDouble()) * 100).roundToLong().toInt()
    }

    // not implemented yet, returns 0
    fun archiveOldMessages(daysOld: Int = ARCHIVE_AGE_DAYS): Long {
        var archivedSize = 0L

        return archivedSize
    }

    fun cleanupOldMedia(daysOld: Int = OLD_MEDIA_AGE_DAYS): Long {
        val cutoffTime = System.currentTimeMillis() - (daysOld.toLong() * 86400000)
        var freedSize = 0L

        mediaDir.walkTopDown().forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                freedSize += file.length()
                file.delete()
            }
        }

        return freedSize
    }

    fun clearArchive(): Long {
        var freedSize = 0L
        archiveDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                freedSize += file.length()
                file.delete()
            }
        }
        return freedSize
    }

    fun getLargeMediaFiles(minSizeBytes: Long = 5L * 1024 * 1024): List<MediaFileInfo> {
        val largeFiles = mutableListOf<MediaFileInfo>()

        mediaDir.walkTopDown().forEach { file ->
            if (file.isFile && file.length() >= minSizeBytes) {
                largeFiles.add(MediaFileInfo(
                    name = file.name,
                    size = file.length(),
                    lastModified = Date(file.lastModified()),
                    path = file.absolutePath
                ))
            }
        }

        return largeFiles.sortedByDescending { it.size }
    }

    // rough estimate only
    fun estimateCompressionSavings(): Long {
        val breakdown = getStorageBreakdown()
        return (breakdown.mediaSize.toDouble() * 0.4).toLong()
    }

    fun getCleanupRecommendations(): List<CleanupRecommendation> {
        val recommendations = mutableListOf<CleanupRecommendation>()
        val breakdown = getStorageBreakdown()

        recommendations.add(CleanupRecommendation(
            action = "Archive old messages (90+ days)",
            estimatedFreed = (breakdown.databaseSize.toDouble() * 0.1).toLong(),
            description = "Move old message data to archive for long-term storage",
            severity = CleanupSeverity.LOW
        ))

        recommendations.add(CleanupRecommendation(
            action = "Remove old media (30+ days)",
            estimatedFreed = (breakdown.mediaSize.toDouble() * 0.3).toLong(),
            description = "Delete old images/videos (can be re-downloaded if needed)",
            severity = CleanupSeverity.MEDIUM
        ))

        recommendations.add(CleanupRecommendation(
            action = "Compress media files",
            estimatedFreed = estimateCompressionSavings(),
            description = "Reduce file size while maintaining quality (75% JPEG, 2Mbps video)",
            severity = CleanupSeverity.MEDIUM
        ))

        return recommendations.filter { it.estimatedFreed > 0 }
    }

    fun executeCleanup(recommendationAction: String): Long {
        return when {
            recommendationAction.contains("Archive") -> archiveOldMessages()
            recommendationAction.contains("Remove") -> cleanupOldMedia()
            recommendationAction.contains("Compress") -> compressAllMedia()
            else -> 0L
        }
    }

    fun compressAllMedia(): Long {
        var freedSize = 0L

        return freedSize
    }

    fun recordStorageMetric() {
    }

    private fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L

        return directory.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }
    }
}

data class StorageBreakdown(
    val messagesSize: Long,
    val mediaSize: Long,
    val archiveSize: Long,
    val databaseSize: Long,
    val totalSize: Long
) {
    fun getFormattedBreakdown(): String {
        return """
            Messages: ${formatBytes(messagesSize)}
            Media: ${formatBytes(mediaSize)}
            Archive: ${formatBytes(archiveSize)}
            Database: ${formatBytes(databaseSize)}
            ─────────────────────
            Total: ${formatBytes(totalSize)}
        """.trimIndent()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

enum class StorageStatus {
    HEALTHY,
    WARNING,
    CRITICAL
}

data class CleanupRecommendation(
    val action: String,
    val estimatedFreed: Long,
    val description: String,
    val severity: CleanupSeverity
)

enum class CleanupSeverity {
    LOW,
    MEDIUM,
    HIGH
}

data class MediaFileInfo(
    val name: String,
    val size: Long,
    val lastModified: Date,
    val path: String
) {
    fun getFormattedSize(): String {
        return when {
            size >= 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024))
            size >= 1024 -> "%.2f KB".format(size / 1024.0)
            else -> "$size B"
        }
    }

    fun getDaysOld(): Int {
        return ((System.currentTimeMillis() - lastModified.time) / 86400000).toInt()
    }
}
