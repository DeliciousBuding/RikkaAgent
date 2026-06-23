package io.rikka.agent.storage

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A user-configurable quick message (shortcut command) shown in the chat input.
 *
 * @property id Unique identifier for the quick message.
 * @property label Short display label shown in the quick message list.
 * @property command The full command text to insert/send.
 */
@Serializable
data class QuickMessage(
  val id: String = UUID.randomUUID().toString(),
  val label: String,
  val command: String,
)
