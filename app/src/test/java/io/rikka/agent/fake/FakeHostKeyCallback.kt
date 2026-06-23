package io.rikka.agent.fake

import io.rikka.agent.ssh.HostKeyCallback

/**
 * Configurable [HostKeyCallback] for tests.
 *
 * Default behavior: accept all unknown hosts and all mismatches.
 * Override [unknownHostDecision] and [mismatchDecision] for specific scenarios.
 *
 * ## Invocation tracking
 *
 * [unknownHostCalls] and [mismatchCalls] record every invocation for assertions.
 */
class FakeHostKeyCallback : HostKeyCallback {

  /** Decides the return value for [onUnknownHost]. */
  var unknownHostDecision: suspend (host: String, port: Int, fingerprint: String, keyType: String) -> Boolean =
    { _, _, _, _ -> true }

  /** Decides the return value for [onHostKeyMismatch]. */
  var mismatchDecision: suspend (
    host: String, port: Int,
    expectedFingerprint: String, actualFingerprint: String,
    keyType: String,
  ) -> Boolean = { _, _, _, _, _ -> true }

  // ── Invocation records ─────────────────────────────────────────────────────

  data class UnknownHostCall(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
    val accepted: Boolean,
  )

  data class MismatchCall(
    val host: String,
    val port: Int,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    val keyType: String,
    val accepted: Boolean,
  )

  val unknownHostCalls = mutableListOf<UnknownHostCall>()
  val mismatchCalls = mutableListOf<MismatchCall>()

  // ── HostKeyCallback ────────────────────────────────────────────────────────

  override suspend fun onUnknownHost(
    host: String,
    port: Int,
    fingerprint: String,
    keyType: String,
  ): Boolean {
    val accepted = unknownHostDecision(host, port, fingerprint, keyType)
    unknownHostCalls += UnknownHostCall(host, port, fingerprint, keyType, accepted)
    return accepted
  }

  override suspend fun onHostKeyMismatch(
    host: String,
    port: Int,
    expectedFingerprint: String,
    actualFingerprint: String,
    keyType: String,
  ): Boolean {
    val accepted = mismatchDecision(host, port, expectedFingerprint, actualFingerprint, keyType)
    mismatchCalls += MismatchCall(host, port, expectedFingerprint, actualFingerprint, keyType, accepted)
    return accepted
  }

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Reset all invocation records. */
  fun resetTracking() {
    unknownHostCalls.clear()
    mismatchCalls.clear()
  }

  /** Reset everything including decisions. */
  fun reset() {
    resetTracking()
    unknownHostDecision = { _, _, _, _ -> true }
    mismatchDecision = { _, _, _, _, _ -> true }
  }

  companion object {
    /** A callback that always accepts. */
    fun alwaysAccept(): FakeHostKeyCallback = FakeHostKeyCallback()

    /** A callback that always rejects. */
    fun alwaysReject(): FakeHostKeyCallback = FakeHostKeyCallback().apply {
      unknownHostDecision = { _, _, _, _ -> false }
      mismatchDecision = { _, _, _, _, _ -> false }
    }
  }
}
