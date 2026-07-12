package com.freetime.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Migration from v1 to v2
 * 
 * Changes:
 * - friends table: Added 8 new columns to match API response structure
 *   - displayName, bio, tags, status, privacyLevel, email, isOnline
 * - Purpose: Ensure local data matches server API response structure
 */
object Migration_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Alter friends table to add new columns
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN displayName TEXT
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN bio TEXT
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN status TEXT
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN privacyLevel TEXT NOT NULL DEFAULT 'public'
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN email TEXT
        """.trimIndent())
        
        db.execSQL("""
            ALTER TABLE friends ADD COLUMN isOnline INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}
