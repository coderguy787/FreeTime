package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_4_5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='MessageEntity'")
        cursor.moveToFirst()
        val hasMessageEntityTable = cursor.getInt(0) > 0
        cursor.close()
        val tableName = if (hasMessageEntityTable) "MessageEntity" else "messages"
        db.execSQL("ALTER TABLE $tableName ADD COLUMN reactions TEXT NOT NULL DEFAULT ''")
    }
}
