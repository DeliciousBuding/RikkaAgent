package io.rikka.agent.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    SshProfileEntity::class,
    ChatThreadEntity::class,
    ChatMessageEntity::class,
  ],
  version = 5,
  exportSchema = false,
)
@TypeConverters(MessagePartConverter::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun sshProfileDao(): SshProfileDao
  abstract fun chatMessageDao(): ChatMessageDao

  companion object {
    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'")
      }
    }
  }
}
