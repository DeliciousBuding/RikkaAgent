package io.rikka.agent.storage.db

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SshProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val partJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

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
)

fun ChatMessageEntity.toModel(): ChatMessage = ChatMessage(
  id = id,
  role = ChatRole.valueOf(role),
  content = content,
  parts = if (partsJson.isNotBlank()) {
    try { partJson.decodeFromString(partsJson) } catch (_: Exception) { emptyList() }
  } else emptyList(),
  timestampMs = timestampMs,
  status = MessageStatus.valueOf(status),
)

fun ChatMessage.toEntity(threadId: String): ChatMessageEntity = ChatMessageEntity(
  id = id,
  threadId = threadId,
  role = role.name,
  content = content,
  partsJson = if (parts.isNotEmpty()) partJson.encodeToString(parts) else "[]",
  timestampMs = timestampMs,
  status = status.name,
)
