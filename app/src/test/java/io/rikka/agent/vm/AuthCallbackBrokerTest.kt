package io.rikka.agent.vm

import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.PasswordProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCallbackBrokerTest {

  private val dispatcher: TestDispatcher = StandardTestDispatcher()
  private lateinit var broker: AuthCallbackBroker
  private lateinit var profile: SshProfile

  @Before
  fun setUp() {
    broker = AuthCallbackBroker()
    profile = SshProfile(
      id = "p1",
      name = "Test",
      host = "example.com",
      port = 22,
      username = "user",
    )
  }

  // ── Host key callback ─────────────────────────────────────────────────────

  @Test
  fun `unknown host emits event and unblocks on accept`() = runTest(dispatcher) {
    val events = mutableListOf<HostKeyEvent>()
    val eventJob = launch { broker.hostKeyEvent.collect { events += it } }

    val result = async {
      broker.hostKeyCallback.onUnknownHost(
        host = "example.com",
        port = 22,
        fingerprint = "SHA256:abc123",
        keyType = "ED25519",
      )
    }
    advanceUntilIdle()

    assertEquals(1, events.size)
    val event = events[0] as HostKeyEvent.UnknownHost
    assertEquals("example.com", event.host)
    assertEquals(22, event.port)
    assertEquals("SHA256:abc123", event.fingerprint)
    assertEquals("ED25519", event.keyType)

    broker.respondToHostKey(true)
    advanceUntilIdle()

    assertTrue(result.await())
    eventJob.cancel()
  }

  @Test
  fun `unknown host emits event and unblocks on reject`() = runTest(dispatcher) {
    val result = async {
      broker.hostKeyCallback.onUnknownHost("bad.host", 2222, "SHA256:xyz", "RSA")
    }
    advanceUntilIdle()

    broker.respondToHostKey(false)
    advanceUntilIdle()

    assertTrue(!result.await())
  }

  @Test
  fun `host key mismatch emits Mismatch event and passes decision through`() = runTest(dispatcher) {
    val events = mutableListOf<HostKeyEvent>()
    val eventJob = launch { broker.hostKeyEvent.collect { events += it } }

    val result = async {
      broker.hostKeyCallback.onHostKeyMismatch(
        host = "example.com",
        port = 22,
        expectedFingerprint = "SHA256:old",
        actualFingerprint = "SHA256:new",
        keyType = "ED25519",
      )
    }
    advanceUntilIdle()

    assertEquals(1, events.size)
    val event = events[0] as HostKeyEvent.Mismatch
    assertEquals("example.com", event.host)
    assertEquals(22, event.port)
    assertEquals("SHA256:old", event.expectedFingerprint)
    assertEquals("SHA256:new", event.actualFingerprint)
    assertEquals("ED25519", event.keyType)

    broker.respondToHostKey(true)
    advanceUntilIdle()

    assertTrue(result.await())
    eventJob.cancel()
  }

  @Test
  fun `host key mismatch passes false when user rejects`() = runTest(dispatcher) {
    val result = async {
      broker.hostKeyCallback.onHostKeyMismatch("host", 22, "SHA256:old", "SHA256:new", "RSA")
    }
    advanceUntilIdle()

    broker.respondToHostKey(false)
    advanceUntilIdle()

    assertTrue(!result.await())
  }

  // ── Password callback ─────────────────────────────────────────────────────

  @Test
  fun `password provider emits request and returns password on response`() = runTest(dispatcher) {
    val requests = mutableListOf<String>()
    val requestJob = launch { broker.passwordRequest.collect { requests += it } }

    val provider = broker.createPasswordProvider()
    val result = async { provider.getPassword(profile) }
    advanceUntilIdle()

    assertEquals(1, requests.size)
    assertEquals("user@example.com:22", requests[0])

    broker.respondToPassword("s3cr3t")
    advanceUntilIdle()

    assertEquals("s3cr3t", result.await())
    requestJob.cancel()
  }

  @Test
  fun `password provider throws on null response (auth cancelled)`() = runTest(dispatcher) {
    val provider = broker.createPasswordProvider()
    val result = async {
      try {
        provider.getPassword(profile)
        null
      } catch (e: IllegalStateException) {
        e.message
      }
    }
    advanceUntilIdle()

    broker.respondToPassword(null)
    advanceUntilIdle()

    assertEquals("Authentication cancelled", result.await())
  }

  // ── Passphrase callback ───────────────────────────────────────────────────

  @Test
  fun `passphrase provider emits request and returns passphrase on response`() = runTest(dispatcher) {
    val requests = mutableListOf<String>()
    val requestJob = launch { broker.passphraseRequest.collect { requests += it } }

    val provider = broker.createPassphraseProvider()
    val result = async { provider.getPassphrase(profile) }
    advanceUntilIdle()

    assertEquals(1, requests.size)
    assertEquals("user@example.com:22", requests[0])

    broker.respondToPassphrase("letmein")
    advanceUntilIdle()

    assertEquals("letmein", result.await())
    requestJob.cancel()
  }

  @Test
  fun `passphrase provider returns null when user cancels`() = runTest(dispatcher) {
    val provider = broker.createPassphraseProvider()
    val result = async { provider.getPassphrase(profile) }
    advanceUntilIdle()

    broker.respondToPassphrase(null)
    advanceUntilIdle()

    assertEquals(null, result.await())
  }

  // ── Multiple sequential requests ──────────────────────────────────────────

  @Test
  fun `broker handles sequential password requests independently`() = runTest(dispatcher) {
    val provider = broker.createPasswordProvider()

    // First request
    val r1 = async { provider.getPassword(profile) }
    advanceUntilIdle()
    broker.respondToPassword("pass1")
    advanceUntilIdle()
    assertEquals("pass1", r1.await())

    // Second request
    val r2 = async { provider.getPassword(profile) }
    advanceUntilIdle()
    broker.respondToPassword("pass2")
    advanceUntilIdle()
    assertEquals("pass2", r2.await())
  }

  @Test
  fun `broker handles interleaved host key and password requests`() = runTest(dispatcher) {
    val events = mutableListOf<HostKeyEvent>()
    val eventJob = launch { broker.hostKeyEvent.collect { events += it } }

    // Start host key request
    val hostResult = async {
      broker.hostKeyCallback.onUnknownHost("host", 22, "SHA256:abc", "ED25519")
    }
    advanceUntilIdle()

    // Respond to host key
    broker.respondToHostKey(true)
    advanceUntilIdle()
    assertTrue(hostResult.await())
    assertEquals(1, events.size)

    // Now start password request
    val provider = broker.createPasswordProvider()
    val passResult = async { provider.getPassword(profile) }
    advanceUntilIdle()

    broker.respondToPassword("pw")
    advanceUntilIdle()
    assertEquals("pw", passResult.await())

    eventJob.cancel()
  }
}
