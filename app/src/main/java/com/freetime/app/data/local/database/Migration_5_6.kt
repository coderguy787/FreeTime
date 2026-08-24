package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_5_6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // new table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS offline_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId TEXT NOT NULL DEFAULT '',
                recipientId TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                messageType TEXT NOT NULL DEFAULT 'text',
                mediaPath TEXT DEFAULT NULL,
                mediaType TEXT DEFAULT NULL,
                fileName TEXT DEFAULT NULL,
                createdAt INTEGER NOT NULL DEFAULT 0,
                retryCount INTEGER NOT NULL DEFAULT 0,
                lastError TEXT DEFAULT NULL,
                status TEXT NOT NULL DEFAULT 'pending'
            )
        """)
    }
}
