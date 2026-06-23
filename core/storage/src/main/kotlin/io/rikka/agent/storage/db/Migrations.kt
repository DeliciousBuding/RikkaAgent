package io.rikka.agent.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for [AppDatabase].
 */
object Migrations {

  /**
   * v4 → v5: Add `partsJson` column to `chat_messages` and migrate existing content.
   *
   * Changes:
   * - ALTER TABLE chat_messages ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'
   * - UPDATE chat_messages SET partsJson = JSON array wrapping content as MessagePart.Text
   *
   * The `content` column is preserved for FTS / search.
   */
  val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 1. Add the new column with default '[]'
      db.execSQL("ALTER TABLE chat_messages ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'")

      // 2. Migrate existing content → partsJson as [{"type":"text","text":"..."}]
      //    Use REPLACE for JSON-safe escaping of double quotes and newlines.
      //    Single quotes in content do not need escaping for JSON.
      db.execSQL(
        """
        UPDATE chat_messages
        SET partsJson = '[{"type":"text","text":"' ||
          replace(replace(replace(content, '"', '\"'), char(13), ''), char(10), '\n') ||
          '"}]'
        WHERE content != ''
        """.trimIndent()
      )
    }
  }

  /** All migrations in order. */
  val ALL = arrayOf(MIGRATION_4_5)
}
