package com.freetime.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ChannelEntity::class,
        FriendRequestEntity::class,
        FriendEntity::class,
        MediaEntity::class,
        DeleteRequestEntity::class,
        DeleteApprovalEntity::class,
        OfflineQueueEntity::class
    ],
    version = 7
)
abstract class FreeTimeDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun channelDao(): ChannelDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun friendDao(): FriendDao
    abstract fun mediaDao(): MediaDao
    abstract fun deleteRequestDao(): DeleteRequestDao
    abstract fun deleteApprovalDao(): DeleteApprovalDao
    abstract fun offlineQueueDao(): OfflineQueueDao

    companion object {
        // single shared database instance
        @Volatile
        private var instance: FreeTimeDatabase? = null

        fun getInstance(context: Context): FreeTimeDatabase {
            return instance ?: synchronized(this) {
                instance ?: createDatabase(context).also { instance = it }
            }
        }

        private fun createDatabase(context: Context): FreeTimeDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FreeTimeDatabase::class.java,
                "freetime_local_db"
            )
                .addMigrations(Migration_1_2, Migration_2_3, Migration_3_4, Migration_4_5, Migration_5_6)
                // fallback migration, drops local data
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
