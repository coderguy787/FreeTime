package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // added reply columns
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToMessageId TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToUsername TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN replyToText TEXT")
    }
}
