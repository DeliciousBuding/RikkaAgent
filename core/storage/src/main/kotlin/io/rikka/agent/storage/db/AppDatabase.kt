package io.rikka.agent.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
}
