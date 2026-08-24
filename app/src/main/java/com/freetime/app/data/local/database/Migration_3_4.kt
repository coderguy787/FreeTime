package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // added media columns
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN mediaType TEXT")
        db.execSQL("ALTER TABLE MessageEntity ADD COLUMN mediaName TEXT")
    }
}
