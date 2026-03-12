package io.rikka.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class SshProfile(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = 22,
  val username: String,
)

