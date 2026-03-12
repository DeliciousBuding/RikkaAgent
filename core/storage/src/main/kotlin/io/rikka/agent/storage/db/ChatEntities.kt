package io.rikka.agent.storage.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
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
  val content: String,
  val timestampMs: Long,
  val status: String,
)
