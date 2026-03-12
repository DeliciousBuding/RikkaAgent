package io.rikka.agent.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_profiles")
data class SshProfileEntity(
  @PrimaryKey val id: String,
  val name: String,
  val host: String,
  val port: Int,
  val username: String,
  val authType: String,
  val keyRef: String?,
  val hostKeyPolicy: String,
  val keepaliveIntervalSec: Int,
  val codexMode: Boolean = false,
  val codexWorkDir: String? = null,
  val codexApiKey: String? = null,
)
