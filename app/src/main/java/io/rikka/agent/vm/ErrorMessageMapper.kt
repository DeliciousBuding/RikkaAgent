package io.rikka.agent.vm

object ErrorMessageMapper {
  fun friendlyErrorMessage(
    category: String,
    raw: String,
    connectionRefused: String,
    timeout: String,
    unknownHost: String,
    authFailed: String,
    generic: (String) -> String,
  ): String = when (category) {
    "connection_refused" -> connectionRefused
    "timeout" -> timeout
    "unknown_host" -> unknownHost
    "auth_failed" -> authFailed
    else -> generic(raw)
  }
}
