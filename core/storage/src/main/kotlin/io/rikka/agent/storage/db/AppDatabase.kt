package io.rikka.agent.storage.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    SshProfileEntity::class,
    ChatThreadEntity::class,
    ChatMessageEntity::class,
  ],
  version = 3,
  exportSchema = false,
  autoMigrations = [],
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun sshProfileDao(): SshProfileDao
  abstract fun chatMessageDao(): ChatMessageDao
}
