package io.rikka.agent.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity)

  @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMs ASC")
  fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMs ASC")
  suspend fun getMessages(threadId: String): List<ChatMessageEntity>

  @Query("UPDATE chat_messages SET content = :content, status = :status WHERE id = :id")
  suspend fun updateMessageContent(id: String, content: String, status: String)

  @Query("UPDATE chat_threads SET updatedAtMs = :updatedAtMs, title = :title WHERE id = :id")
  suspend fun updateThread(id: String, title: String, updatedAtMs: Long)
}
