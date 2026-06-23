package io.rikka.agent.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertThread(thread: ChatThreadEntity)

  // ── Thread observation ──────────────────────────────────────────────────

  /** All threads for a profile, ordered by most recent activity. */
  @Query("SELECT * FROM chat_threads WHERE profileId = :profileId ORDER BY updatedAtMs DESC")
  fun observeThreadsForProfile(profileId: String): Flow<List<ChatThreadEntity>>

  /** Active (non-archived) threads for a profile, pinned threads first. */
  @Query(
    "SELECT * FROM chat_threads WHERE profileId = :profileId AND isArchived = 0 " +
      "ORDER BY isPinned DESC, updatedAtMs DESC"
  )
  fun observeActiveThreads(profileId: String): Flow<List<ChatThreadEntity>>

  /** Archived threads for a profile. */
  @Query(
    "SELECT * FROM chat_threads WHERE profileId = :profileId AND isArchived = 1 " +
      "ORDER BY updatedAtMs DESC"
  )
  fun observeArchivedThreads(profileId: String): Flow<List<ChatThreadEntity>>

  @Query("SELECT * FROM chat_threads WHERE id = :threadId")
  suspend fun getThread(threadId: String): ChatThreadEntity?

  @Query("DELETE FROM chat_threads WHERE id = :threadId")
  suspend fun deleteThread(threadId: String)

  // ── Full-text search ────────────────────────────────────────────────────

  /**
   * Search threads whose messages match the given FTS4 query.
   *
   * Joins through the FTS virtual table to find messages matching [query],
   * then returns the distinct parent threads. Supports FTS4 syntax:
   * - Simple terms: `hello`
   * - Phrases: `"hello world"`
   * - Prefix: `hel*`
   * - Boolean: `hello AND world`
   */
  @Query(
    """
    SELECT DISTINCT t.* FROM chat_threads t
    INNER JOIN chat_messages m ON m.threadId = t.id
    WHERE m.rowid IN (
        SELECT rowid FROM chat_messages_fts WHERE chat_messages_fts MATCH :query
    )
    AND t.profileId = :profileId
    ORDER BY t.isPinned DESC, t.updatedAtMs DESC
    """
  )
  fun searchThreads(profileId: String, query: String): Flow<List<ChatThreadEntity>>

  // ── Pin / Archive ───────────────────────────────────────────────────────

  @Query("UPDATE chat_threads SET isPinned = :isPinned WHERE id = :threadId")
  suspend fun setThreadPinned(threadId: String, isPinned: Boolean)

  @Query("UPDATE chat_threads SET isArchived = :isArchived WHERE id = :threadId")
  suspend fun setThreadArchived(threadId: String, isArchived: Boolean)

  // ── Tags ────────────────────────────────────────────────────────────────

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTag(tag: ThreadTagEntity): Long

  @Query("DELETE FROM thread_tags WHERE threadId = :threadId AND name = :name")
  suspend fun deleteTag(threadId: String, name: String)

  @Query("DELETE FROM thread_tags WHERE threadId = :threadId")
  suspend fun deleteAllTags(threadId: String)

  @Query("SELECT name FROM thread_tags WHERE threadId = :threadId ORDER BY name")
  suspend fun getTagsForThread(threadId: String): List<String>

  @Query("SELECT name FROM thread_tags WHERE threadId = :threadId ORDER BY name")
  fun observeTagsForThread(threadId: String): Flow<List<String>>

  // ── Messages ────────────────────────────────────────────────────────────

  /**
   * Insert a message. IGNORE strategy prevents overwriting existing messages
   * (which would clear fields not present in the new entity).
   * Use [updateMessageParts] to update an existing message.
   */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertMessage(message: ChatMessageEntity): Long

  /**
   * Update message content, partsJson, and status.
   *
   * @return number of rows affected (0 means message doesn't exist).
   */
  @Query("UPDATE chat_messages SET content = :content, partsJson = :partsJson, status = :status WHERE id = :id")
  suspend fun updateMessageParts(id: String, content: String, partsJson: String, status: String): Int

  @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMs ASC")
  fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMs ASC")
  suspend fun getMessages(threadId: String): List<ChatMessageEntity>

  /** Update only the thread's last-activity timestamp. */
  @Query("UPDATE chat_threads SET updatedAtMs = :updatedAtMs WHERE id = :id")
  suspend fun updateThreadTimestamp(id: String, updatedAtMs: Long)

  /** Update thread title and last-activity timestamp. */
  @Query("UPDATE chat_threads SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :id")
  suspend fun updateThreadTitle(id: String, title: String, updatedAtMs: Long)
}
