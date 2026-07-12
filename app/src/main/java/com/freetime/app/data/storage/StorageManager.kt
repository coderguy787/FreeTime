package com.freetime.app.data.storage

import android.content.Context
import androidx.room.*
import com.freetime.app.data.local.SharedPreferencesHelper
import java.io.File
import java.util.*
import kotlin.math.roundToLong

/**
 * Storage Manager - Monitors and manages encrypted app storage
 * Handles caching, compression, and automatic cleanup
 */
class StorageManager(private val context: Context) {
    
    companion object {
        // Storage limits (configurable)
        const val WARNING_THRESHOLD = 500L * 1024 * 1024    // 500 MB
        const val CRITICAL_THRESHOLD = 900L * 1024 * 1024   // 900 MB
        const val MAX_STORAGE_LIMIT = 1024L * 1024 * 1024   // 1 GB
        
        // Media age thresholds for cleanup
        const val OLD_MEDIA_AGE_DAYS = 30
        const val ARCHIVE_AGE_DAYS = 90
        
        // Compression settings
        const val IMAGE_COMPRESSION_QUALITY = 75
        const val VIDEO_COMPRESSION_BITRATE = "2M"          // 2 Mbps
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
    
    /**
     * Get total storage usage (all encrypted data)
     */
    fun getTotalStorageUsage(): Long {
        return storageDir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        } + mediaDir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }
    }
    
    /**
     * Get storage usage by category
     */
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
    
    /**
     * Check if storage is getting full
     */
    fun getStorageStatus(): StorageStatus {
        val total = getTotalStorageUsage()
        return when {
            total >= CRITICAL_THRESHOLD -> StorageStatus.CRITICAL
            total >= WARNING_THRESHOLD -> StorageStatus.WARNING
            else -> StorageStatus.HEALTHY
        }
    }
    
    /**
     * Get percentage of storage used
     */
    fun getStoragePercentage(): Int {
        val total = getTotalStorageUsage()
        return ((total.toDouble() / MAX_STORAGE_LIMIT.toDouble()) * 100).roundToLong().toInt()
    }
    
    /**
     * Archive old messages (older than ARCHIVE_AGE_DAYS)
     * Keeps original but moves to separate archived folder
     */
    fun archiveOldMessages(daysOld: Int = ARCHIVE_AGE_DAYS): Long {
        // val cutoffTime = System.currentTimeMillis() - (daysOld.toLong() * 86400000)
        var archivedSize = 0L
        
        // In a real implementation, this would:
        // 1. Query database for messages older than cutoffTime
        // 2. Move encrypted files to archive folder
        // 3. Update database records
        // 4. Return size freed up
        
        return archivedSize
    }
    
    /**
     * Cleanup old media (images/videos older than OLD_MEDIA_AGE_DAYS)
     * Keeps database record but removes actual files (can be re-downloaded)
     */
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
    
    /**
     * Clear all archived messages
     */
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
    
    /**
     * Get list of large media files (>5MB) for user review
     */
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
    
    /**
     * Estimate compression savings
     */
    fun estimateCompressionSavings(): Long {
        // In a real implementation, sample compress a few files
        // and extrapolate savings across all media
        val breakdown = getStorageBreakdown()
        // Estimate 40% savings on media through compression
        return (breakdown.mediaSize.toDouble() * 0.4).toLong()
    }
    
    /**
     * Get cleanup recommendations
     */
    fun getCleanupRecommendations(): List<CleanupRecommendation> {
        val recommendations = mutableListOf<CleanupRecommendation>()
        val breakdown = getStorageBreakdown()
        
        // Check for archival opportunity
        recommendations.add(CleanupRecommendation(
            action = "Archive old messages (90+ days)",
            estimatedFreed = (breakdown.databaseSize.toDouble() * 0.1).toLong(),
            description = "Move old message data to archive for long-term storage",
            severity = CleanupSeverity.LOW
        ))
        
        // Check for media cleanup
        recommendations.add(CleanupRecommendation(
            action = "Remove old media (30+ days)",
            estimatedFreed = (breakdown.mediaSize.toDouble() * 0.3).toLong(),
            description = "Delete old images/videos (can be re-downloaded if needed)",
            severity = CleanupSeverity.MEDIUM
        ))
        
        // Check for compression
        recommendations.add(CleanupRecommendation(
            action = "Compress media files",
            estimatedFreed = estimateCompressionSavings(),
            description = "Reduce file size while maintaining quality (75% JPEG, 2Mbps video)",
            severity = CleanupSeverity.MEDIUM
        ))
        
        return recommendations.filter { it.estimatedFreed > 0 }
    }
    
    /**
     * Accept cleanup recommendation and execute
     */
    fun executeCleanup(recommendationAction: String): Long {
        return when {
            recommendationAction.contains("Archive") -> archiveOldMessages()
            recommendationAction.contains("Remove") -> cleanupOldMedia()
            recommendationAction.contains("Compress") -> compressAllMedia()
            else -> 0L
        }
    }
    
    /**
     * Compress all media in storage
     */
    fun compressAllMedia(): Long {
        var freedSize = 0L
        
        // In a real implementation, this would:
        // 1. Use Android's Image/Video compression libraries
        // 2. Process all media files
        // 3. Replace original with compressed version
        // 4. Return size savings
        
        return freedSize
    }
    
    /**
     * Monitor ongoing storage growth
     */
    fun recordStorageMetric() {
        // val timestamp = System.currentTimeMillis()
        // val usage = getTotalStorageUsage()
        // val breakdown = getStorageBreakdown()
        
        // Would save to analytics database
        // prefs.saveStorageMetric(StorageMetric(timestamp, usage, breakdown))
    }
    
    private fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        
        return directory.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }
    }
}

/**
 * Storage breakdown by category
 */
data class StorageBreakdown(
    val messagesSize: Long,     // Encrypted message text
    val mediaSize: Long,        // Images and videos
    val archiveSize: Long,      // Archived old data
    val databaseSize: Long,     // SQLite database
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

/**
 * Storage status levels
 */
enum class StorageStatus {
    HEALTHY,        // < 500 MB
    WARNING,        // 500 MB - 900 MB
    CRITICAL        // > 900 MB (action required)
}

/**
 * Cleanup recommendation with severity
 */
data class CleanupRecommendation(
    val action: String,
    val estimatedFreed: Long,
    val description: String,
    val severity: CleanupSeverity
)

/**
 * Severity levels for cleanup recommendations
 */
enum class CleanupSeverity {
    LOW,        // Nice to free up space
    MEDIUM,     // Should clean up soon
    HIGH        // Must clean up immediately
}

/**
 * Media file info for user review
 */
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
