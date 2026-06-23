package io.rikka.agent.storage.db

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.SshProfile
import kotlinx.serialization.encodeToString

fun SshProfileEntity.toModel(): SshProfile = SshProfile(
  id = id,
  name = name,
  host = host,
  port = port,
  username = username,
  authType = AuthType.valueOf(authType),
  keyRef = keyRef,
  hostKeyPolicy = HostKeyPolicy.valueOf(hostKeyPolicy),
  keepaliveIntervalSec = keepaliveIntervalSec,
  codexMode = codexMode,
  codexWorkDir = codexWorkDir,
  codexApiKey = codexApiKey,
  group = try { ProfileGroup.valueOf(group) } catch (_: Exception) { ProfileGroup.None },
  tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
)

fun SshProfile.toEntity(): SshProfileEntity = SshProfileEntity(
  id = id,
  name = name,
  host = host,
  port = port,
  username = username,
  authType = authType.name,
  keyRef = keyRef,
  hostKeyPolicy = hostKeyPolicy.name,
  keepaliveIntervalSec = keepaliveIntervalSec,
  codexMode = codexMode,
  codexWorkDir = codexWorkDir,
  codexApiKey = codexApiKey,
  group = group.name,
  tags = tags.joinToString(","),
)

// ── ChatMessage ↔ Entity ──────────────────────────────────────────────────────

/**
 * Convert [ChatMessageEntity] to domain [ChatMessage].
 *
 * Prefers [ChatMessageEntity.partsJson] for structured parts.
 * Falls back to wrapping [ChatMessageEntity.content] in [MessagePart.Text]
 * for rows migrated from older schemas where partsJson is '[]'.
 */
fun ChatMessageEntity.toModel(): ChatMessage {
  val parsedParts: List<MessagePart> = if (partsJson.isNotBlank() && partsJson != "[]") {
    ChatMessage.json.decodeFromString(partsJson)
  } else {
    emptyList()
  }

  val finalParts = if (parsedParts.isEmpty() && content.isNotEmpty()) {
    listOf(MessagePart.Text(content))
  } else {
    parsedParts
  }

  return ChatMessage(
    id = id,
    role = ChatRole.valueOf(role),
    parts = finalParts,
    timestampMs = timestampMs,
    status = MessageStatus.valueOf(status),
  )
}

/**
 * Convert domain [ChatMessage] to [ChatMessageEntity] for persistence.
 *
 * @param threadId The thread this message belongs to.
 */
fun ChatMessage.toEntity(threadId: String): ChatMessageEntity {
  val partsList = parts.ifEmpty { listOf(MessagePart.Text(content)) }
  return ChatMessageEntity(
    id = id,
    threadId = threadId,
    role = role.name,
    content = content,
    partsJson = ChatMessage.json.encodeToString(partsList),
    timestampMs = timestampMs,
    status = status.name,
  )
}

// ── ChatThreadEntity → ChatThread ─────────────────────────────────────────────

/**
 * Convert [ChatThreadEntity] to domain [ChatThread].
 *
 * Maps the persisted entity fields (including [ChatThreadEntity.isPinned] and
 * [ChatThreadEntity.isArchived]) to the domain model. Tags and stats are
 * populated separately by the repository layer.
 */
fun ChatThreadEntity.toModel(
  tags: List<String> = emptyList(),
  stats: io.rikka.agent.model.SessionStats = io.rikka.agent.model.SessionStats(),
): io.rikka.agent.model.ChatThread = io.rikka.agent.model.ChatThread(
  id = id,
  title = title,
  messages = emptyList(),
  isPinned = isPinned,
  isArchived = isArchived,
  tags = tags,
  stats = stats,
)

// ── Thread statistics computation ─────────────────────────────────────────────

/**
 * Compute [io.rikka.agent.model.SessionStats] from a list of [ChatMessageEntity].
 *
 * - [io.rikka.agent.model.SessionStats.commandCount]: number of User messages.
 * - [io.rikka.agent.model.SessionStats.outputLineCount]: total lines in
 *   non-empty Assistant message content.
 * - [io.rikka.agent.model.SessionStats.totalExecutionTimeMs]: sum of
 *   (assistant.timestamp - preceding.user.timestamp) for each consecutive
 *   user->assistant pair.
 */
fun List<ChatMessageEntity>.computeStats(): io.rikka.agent.model.SessionStats {
  val commandCount = count { it.role == "User" }
  val outputLineCount = filter { it.role == "Assistant" && it.content.isNotEmpty() }
    .sumOf { msg -> msg.content.lines().size }
  var totalTime = 0L
  for (i in 0 until size - 1) {
    if (this[i].role == "User" && this[i + 1].role == "Assistant") {
      val delta = this[i + 1].timestampMs - this[i].timestampMs
      if (delta > 0) totalTime += delta
    }
  }
  return io.rikka.agent.model.SessionStats(
    commandCount = commandCount,
    totalExecutionTimeMs = totalTime,
    outputLineCount = outputLineCount,
  )
}
