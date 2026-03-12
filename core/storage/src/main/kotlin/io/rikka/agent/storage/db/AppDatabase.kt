package io.rikka.agent.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [SshProfileEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun sshProfileDao(): SshProfileDao
}
