package io.rikka.agent.storage.db

import androidx.room.TypeConverter
import io.rikka.agent.model.MessagePart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MessagePartConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @TypeConverter
    fun fromParts(parts: List<MessagePart>): String = json.encodeToString(parts)

    @TypeConverter
    fun toParts(value: String): List<MessagePart> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(value)
}
