package io.rikka.agent.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessageMapperTest {

  @Test
  fun `maps known categories`() {
    assertEquals(
      "connection refused",
      ErrorMessageMapper.friendlyErrorMessage(
        category = "connection_refused",
        raw = "raw",
        connectionRefused = "connection refused",
        timeout = "timeout",
        unknownHost = "unknown host",
        authFailed = "auth failed",
        generic = { "generic: $it" },
      )
    )

    assertEquals(
      "timeout",
      ErrorMessageMapper.friendlyErrorMessage(
        category = "timeout",
        raw = "raw",
        connectionRefused = "connection refused",
        timeout = "timeout",
        unknownHost = "unknown host",
        authFailed = "auth failed",
        generic = { "generic: $it" },
      )
    )

    assertEquals(
      "unknown host",
      ErrorMessageMapper.friendlyErrorMessage(
        category = "unknown_host",
        raw = "raw",
        connectionRefused = "connection refused",
        timeout = "timeout",
        unknownHost = "unknown host",
        authFailed = "auth failed",
        generic = { "generic: $it" },
      )
    )

    assertEquals(
      "auth failed",
      ErrorMessageMapper.friendlyErrorMessage(
        category = "auth_failed",
        raw = "raw",
        connectionRefused = "connection refused",
        timeout = "timeout",
        unknownHost = "unknown host",
        authFailed = "auth failed",
        generic = { "generic: $it" },
      )
    )
  }

  @Test
  fun `falls back to generic message for unknown category`() {
    val msg = ErrorMessageMapper.friendlyErrorMessage(
      category = "random_failure",
      raw = "boom",
      connectionRefused = "connection refused",
      timeout = "timeout",
      unknownHost = "unknown host",
      authFailed = "auth failed",
      generic = { "generic: $it" },
    )
    assertEquals("generic: boom", msg)
  }
}
