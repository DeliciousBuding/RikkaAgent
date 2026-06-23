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

  @Query("SELECT * FROM chat_threads WHERE profileId = :profileId ORDER BY updatedAtMs DESC")
  fun observeThreadsForProfile(profileId: String): Flow<List<ChatThreadEntity>>

  @Query("SELECT * FROM chat_threads WHERE id = :threadId")
  suspend fun getThread(threadId: String): ChatThreadEntity?

  @Query("DELETE FROM chat_threads WHERE id = :threadId")
  suspend fun deleteThread(threadId: String)

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
