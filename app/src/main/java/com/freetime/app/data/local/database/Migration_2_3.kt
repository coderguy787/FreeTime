package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Migration from v2 to v3
 * 
 * Changes:
 * - MessageEntity: Added replyToMessageId, replyToUsername, replyToText for reply feature in private chats.
 */
object Migration_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToMessageId TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToUsername TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToText TEXT")
    }
}
