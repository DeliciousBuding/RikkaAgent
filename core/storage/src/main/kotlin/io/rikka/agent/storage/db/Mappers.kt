package io.rikka.agent.storage.db

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.SshProfile

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
)
