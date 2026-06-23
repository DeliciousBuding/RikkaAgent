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

  /**
   * v5 → v6: Session management enhancements.
   *
   * Changes:
   * - ALTER TABLE chat_threads ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0
   * - ALTER TABLE chat_threads ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0
   * - CREATE TABLE thread_tags (for session tagging)
   * - CREATE VIRTUAL TABLE chat_messages_fts (FTS4 for full-text search)
   * - Populate FTS table from existing chat_messages content
   * - Create triggers to keep FTS table in sync with chat_messages
   */
  val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      // 1. Add pin/archive columns to chat_threads
      db.execSQL("ALTER TABLE chat_threads ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE chat_threads ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")

      // 2. Create thread_tags table
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS thread_tags (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          threadId TEXT NOT NULL,
          name TEXT NOT NULL,
          FOREIGN KEY (threadId) REFERENCES chat_threads(id) ON DELETE CASCADE
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS index_thread_tags_threadId ON thread_tags(threadId)")

      // 3. Create FTS4 virtual table for full-text message search
      db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS chat_messages_fts USING fts4(content)")

      // 4. Populate FTS table from existing chat_messages data
      db.execSQL(
        """
        INSERT INTO chat_messages_fts(docid, content)
        SELECT rowid, content FROM chat_messages
        WHERE content IS NOT NULL AND content != ''
        """.trimIndent()
      )

      // 5. Create triggers to keep FTS table in sync with chat_messages
      db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS chat_messages_fts_insert AFTER INSERT ON chat_messages
        BEGIN
          INSERT INTO chat_messages_fts(docid, content) VALUES (new.rowid, new.content);
        END
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS chat_messages_fts_delete AFTER DELETE ON chat_messages
        BEGIN
          INSERT INTO chat_messages_fts(chat_messages_fts, docid, content)
          VALUES ('delete', old.rowid, old.content);
        END
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS chat_messages_fts_update AFTER UPDATE ON chat_messages
        BEGIN
          INSERT INTO chat_messages_fts(chat_messages_fts, docid, content)
          VALUES ('delete', old.rowid, old.content);
          INSERT INTO chat_messages_fts(docid, content) VALUES (new.rowid, new.content);
        END
        """.trimIndent()
      )
    }
  }

  /**
   * v6 → v7: Add `group` and `tags` columns to `ssh_profiles`.
   *
   * Changes:
   * - ALTER TABLE ssh_profiles ADD COLUMN "group" TEXT NOT NULL DEFAULT 'None'
   * - ALTER TABLE ssh_profiles ADD COLUMN tags TEXT NOT NULL DEFAULT ''
   *
   * These columns support the new profile grouping and tagging features.
   */
  val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE ssh_profiles ADD COLUMN \"group\" TEXT NOT NULL DEFAULT 'None'")
      db.execSQL("ALTER TABLE ssh_profiles ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
  }

  /** All migrations in order. */
  val ALL = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
