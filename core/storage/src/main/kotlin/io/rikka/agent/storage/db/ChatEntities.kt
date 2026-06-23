package io.rikka.agent.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
  @ColumnInfo(defaultValue = "0")
  val isPinned: Boolean = false,
  @ColumnInfo(defaultValue = "0")
  val isArchived: Boolean = false,
)

@Entity(
  tableName = "chat_messages",
  foreignKeys = [
    ForeignKey(
      entity = ChatThreadEntity::class,
      parentColumns = ["id"],
      childColumns = ["threadId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("threadId")],
)
data class ChatMessageEntity(
  @PrimaryKey val id: String,
  val threadId: String,
  val role: String,
  /** Plain-text content kept for FTS / search. */
  val content: String,
  /** JSON-serialized List&lt;MessagePart&gt;. Default '[]' for backward compat. */
  val partsJson: String = "[]",
  val timestampMs: Long,
  val status: String,
)

@Entity(
  tableName = "thread_tags",
  foreignKeys = [
    ForeignKey(
      entity = ChatThreadEntity::class,
      parentColumns = ["id"],
      childColumns = ["threadId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("threadId")],
)
data class ThreadTagEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val threadId: String,
  val name: String,
)

/**
 * FTS4 virtual table shadowing [ChatMessageEntity.content] for full-text search.
 *
 * Room manages INSERT/UPDATE/DELETE triggers automatically when [contentEntity]
 * is specified. During migration the triggers are created manually.
 */
@Fts4(contentEntity = ChatMessageEntity::class)
@Entity(tableName = "chat_messages_fts")
data class ChatMessageFtsEntity(
  val content: String,
)
