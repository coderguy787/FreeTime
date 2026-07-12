package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Migration from v3 to v4
 *
 * Changes:
 * - MessageEntity: Added mediaType, mediaName for media message support.
 */
object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN mediaType TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN mediaName TEXT")
    }
}
