package io.rikka.agent.ssh

data class KnownHostEndpoint(
  val host: String,
  val port: Int,
)

object KnownHostEndpointParser {

  fun parse(key: String): KnownHostEndpoint? {
    if (!key.startsWith("[") || !key.contains("]:")) return null

    val closingIndex = key.indexOf("]:")
    if (closingIndex <= 1 || closingIndex + 2 >= key.length) return null

    val host = key.substring(1, closingIndex)
    val port = key.substring(closingIndex + 2).toIntOrNull() ?: return null
    if (host.isBlank()) return null

    return KnownHostEndpoint(host = host, port = port)
  }
}
