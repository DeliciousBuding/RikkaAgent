package io.rikka.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class SshProfile(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = 22,
  val username: String,
  val authType: AuthType = AuthType.PublicKey,
  val keyRef: String? = null,
  val hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.TrustFirstUse,
  val keepaliveIntervalSec: Int = 60,
  val codexMode: Boolean = false,
  val codexWorkDir: String? = null,
  val codexApiKey: String? = null,
)

@Serializable
enum class AuthType {
  PublicKey,
  Password,
}

@Serializable
enum class HostKeyPolicy {
  TrustFirstUse,
  RejectUnknown,
  AcceptAll,
}

