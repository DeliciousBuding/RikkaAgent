package io.rikka.agent.storage.db

import androidx.room.TypeConverter
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.MessagePart
import kotlinx.serialization.encodeToString

/**
 * Room TypeConverter for [MessagePart] list serialization.
 *
 * Uses [ChatMessage.json] which is configured with:
 * - classDiscriminator = "type" (matches @SerialName on MessagePart subtypes)
 * - ignoreUnknownKeys = true (forward-compatible)
 * - isLenient = true
 * - encodeDefaults = true
 */
class MessagePartConverter {

  @TypeConverter
  fun fromPartsJson(json: String): List<MessagePart> {
    if (json.isBlank() || json == "[]") return emptyList()
    return ChatMessage.json.decodeFromString(json)
  }

  @TypeConverter
  fun toPartsJson(parts: List<MessagePart>): String {
    if (parts.isEmpty()) return "[]"
    return ChatMessage.json.encodeToString(parts)
  }
}
