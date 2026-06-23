package io.rikka.agent.ssh

import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Observable connection lifecycle state with diagnostic context.
 *
 * ## State machine
 *
 * ```
 *           loadProfile()
 *  Idle ──────────────────► Ready
 *    ▲                         │
 *    │  pool.close()           │ execute()
 *    │                         ▼
 *    │                     Executing
 *    │                         │
 *    │          ┌──────────────┼──────────────┐
 *    │          ▼              ▼              ▼
 *    │    Reconnecting     Disconnected    Failed
 *    │     (backoff)       (recoverable)   (fatal)
 *    │          │              │
 *    │          └──────┬───────┘
 *    │                 ▼
 *    └────────────  Ready  ◄── success
 * ```
 */
sealed class ConnectionState {
  /** No profile loaded; initial state. */
  data object Idle : ConnectionState()

  /** Profile loaded, connection available, no command running. */
  data object Ready : ConnectionState()

  /** A command is actively executing over SSH. */
  data object Executing : ConnectionState()

  /**
   * Connection lost; automatic reconnection in progress with exponential backoff.
   *
   * @property attempt Current reconnection attempt number (1-based).
   * @property nextRetryMs Delay before the next retry attempt, in milliseconds.
   */
  data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : ConnectionState()

  /**
   * Connection dropped but no command was running; the pool will reconnect
   * lazily on the next [SshConnectionPool.acquire] call.
   *
   * @property reason Human-readable reason for the disconnection.
   */
  data class Disconnected(val reason: String) : ConnectionState()

  /**
   * Fatal error; automatic recovery is not possible (e.g. auth failure,
   * host key rejected, exhausted retry budget).
   *
   * @property message Human-readable error description.
   * @property category Error category for programmatic handling.
   */
  data class Failed(val message: String, val category: String) : ConnectionState()
}

/**
 * Configuration for the SSH connection pool.
 *
 * @property maxConnections        Maximum number of pooled SSH connections across all profiles.
 * @property maxConcurrentExec     Maximum number of concurrent command executions (semaphore-bounded).
 * @property ttlMs                 Time-to-live for idle connections before eviction (milliseconds).
 * @property healthCheckIntervalMs Interval between periodic health check probes (milliseconds).
 * @property maxReconnectAttempts  Maximum number of reconnection attempts before giving up.
 * @property reconnectBaseDelayMs  Base delay for exponential backoff on reconnection (milliseconds).
 * @property reconnectMaxDelayMs   Maximum delay cap for exponential backoff (milliseconds).
 */
data class ConnectionPoolConfig(
  val maxConnections: Int = 8,
  val maxConcurrentExec: Int = 4,
  val ttlMs: Long = 5 * 60 * 1000L,          // 5 minutes
  val healthCheckIntervalMs: Long = 30_000L,  // 30 seconds
  val maxReconnectAttempts: Int = 5,
  val reconnectBaseDelayMs: Long = 1_000L,    // 1 second
  val reconnectMaxDelayMs: Long = 30_000L,    // 30 seconds
)

/**
 * A pooled connection entry wrapping a [SshjExecRunner] and its connection metadata.
 *
 * @property runner      The exec runner managing the underlying SSH client.
 * @property profile     The profile this entry is connected to.
 * @property profileKey  Composite key: `[host]:port:username`.
 * @property createdAt   Epoch millis when this entry was created.
 * @property lastUsedAt  Epoch millis when this entry was last used for a command.
 */
private class PooledEntry(
  val runner: SshjExecRunner,
  val profile: SshProfile,
  val profileKey: String,
  val createdAt: Long,
) {
  @Volatile var lastUsedAt: Long = createdAt
  val state: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Ready)
}

/**
 * Multi-profile SSH connection pool with TTL-based eviction, health checking,
 * exponential-backoff reconnection, and bounded concurrency.
 *
 * ## Architecture
 *
 * The pool sits between [CommandExecutor] (application layer) and [SshjExecRunner]
 * (transport layer). It manages the lifecycle of SSH connections independently from
 * individual command executions:
 *
 * - **Multiple profiles** connected simultaneously (up to [ConnectionPoolConfig.maxConnections]).
 * - **TTL-based idle eviction** — connections unused for [ConnectionPoolConfig.ttlMs] are closed.
 * - **Periodic health checks** — lightweight probes every [ConnectionPoolConfig.healthCheckIntervalMs].
 * - **Exponential backoff reconnection** — on connection loss, retries with jittered exponential
 *   delays up to [ConnectionPoolConfig.maxReconnectAttempts] attempts.
 * - **Bounded concurrency** — a [Semaphore] limits concurrent command executions to
 *   [ConnectionPoolConfig.maxConcurrentExec], preventing resource exhaustion on the
 *   Android device and the remote server.
 *
 * ## Thread safety
 * - [entries] is a [ConcurrentHashMap].
 * - [ConnectionState] is held in [MutableStateFlow] per entry, safe for concurrent reads.
 * - The [execSemaphore] serializes execution permits across all profiles.
 * - Each [PooledEntry]'s runner manages its own SSH connection internally.
 *
 * ## Lifecycle
 * - Create a pool via the constructor.
 * - Call [acquire] to obtain a connection state holder for a profile.
 * - Use the runner from the returned [AcquiredConnection] to execute commands.
 * - Call [release] after command execution completes.
 * - Call [close] to shut down all connections and cancel the health-check job.
 */
class SshConnectionPool(
  private val config: ConnectionPoolConfig,
  private val knownHostsStore: KnownHostsStore,
  private val hostKeyCallback: HostKeyCallback,
  private val passwordProvider: PasswordProvider? = null,
  private val keyContentProvider: KeyContentProvider? = null,
  private val passphraseProvider: PassphraseProvider? = null,
  private val scope: CoroutineScope,
) {

  private val entries = ConcurrentHashMap<String, PooledEntry>()
  private val execSemaphore = Semaphore(config.maxConcurrentExec)
  private val healthCheckJob: Job

  init {
    healthCheckJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        delay(config.healthCheckIntervalMs)
        runHealthChecks()
      }
    }
  }

  // ── Public API ────────────────────────────────────────────────────────

  /**
   * Get the observable [ConnectionState] for a given profile.
   *
   * Returns a [StateFlow] that emits state transitions for the connection to
   * [profile]. If the profile has no pooled entry, emits [ConnectionState.Idle].
   */
  fun getConnectionState(profile: SshProfile): StateFlow<ConnectionState> {
    val key = profileKey(profile)
    return entries[key]?.state?.asStateFlow()
      ?: MutableStateFlow<ConnectionState>(ConnectionState.Idle).asStateFlow()
  }

  /**
   * Acquire a connection for [profile].
   *
   * - If a healthy connection already exists for this profile, reuses it.
   * - If no connection exists or the existing one is stale, creates a new one.
   * - If a reconnection is needed, performs it with exponential backoff.
   *
   * The returned [AcquiredConnection] holds a [Semaphore] permit; call [release]
   * after command execution completes (whether successful or not) to return the permit.
   *
   * @throws IllegalStateException if the pool has been closed.
   * @throws SshHostKeyRejectedException if host key verification is rejected.
   */
  suspend fun acquire(profile: SshProfile): AcquiredConnection {
    val key = profileKey(profile)

    evictExpired()

    if (entries.size >= config.maxConnections && !entries.containsKey(key)) {
      evictLruIdle()
    }

    val entry = getOrCreateEntry(key, profile)

    // Acquire execution permit (blocks if at maxConcurrentExec).
    execSemaphore.acquire()

    return AcquiredConnection(entry.runner, entry, key)
  }

  /**
   * Release an [AcquiredConnection] after command execution.
   *
   * Returns the semaphore permit and updates the last-used timestamp.
   */
  fun release(acquired: AcquiredConnection) {
    if (!acquired.permitReleased) {
      acquired.permitReleased = true
      acquired.entry.lastUsedAt = System.currentTimeMillis()
      acquired.entry.state.compareAndSet(ConnectionState.Executing, ConnectionState.Ready)
      execSemaphore.release()
    }
  }

  /**
   * Notify the pool that a command execution failed due to a connection error.
   *
   * Marks the entry for reconnection on the next [acquire] call.
   */
  fun notifyConnectionError(profile: SshProfile, error: Exception) {
    val key = profileKey(profile)
    val entry = entries[key] ?: return

    val category = categorizeError(error)
    if (category == "auth_failed" || category == "host_key_rejected") {
      // Fatal — do not attempt reconnection.
      entry.state.value = ConnectionState.Failed(
        message = error.message ?: "Authentication failed",
        category = category,
      )
    } else {
      entry.state.value = ConnectionState.Disconnected(
        reason = error.message ?: "Connection lost",
      )
    }
  }

  /**
   * Close all pooled connections and cancel the health-check job.
   *
   * After calling this, [acquire] will create new connections (the pool is not
   * permanently disabled; it simply clears the cache).
   */
  fun close() {
    healthCheckJob.cancel()
    for ((key, entry) in entries) {
      disconnectEntry(entry)
      entries.remove(key)
    }
  }

  /**
   * Force-disconnect a specific profile's connection, if pooled.
   */
  fun disconnect(profile: SshProfile) {
    val key = profileKey(profile)
    entries.remove(key)?.let { disconnectEntry(it) }
  }

  // ── Connection lifecycle ──────────────────────────────────────────────

  private suspend fun getOrCreateEntry(key: String, profile: SshProfile): PooledEntry {
    entries[key]?.let { existing ->
      val currentState = existing.state.value

      // If marked for reconnection, do exponential backoff.
      if (currentState is ConnectionState.Disconnected) {
        return reconnectWithBackoff(existing, profile, key)
      }

      // If healthy, reuse.
      if (existing.runner.isConnectionAlive()) {
        return existing
      }

      // Stale entry — remove and create fresh.
      disconnectEntry(existing)
      entries.remove(key)
    }
    return createEntry(key, profile)
  }

  private suspend fun createEntry(key: String, profile: SshProfile): PooledEntry {
    val runner = SshjExecRunner(
      knownHostsStore = knownHostsStore,
      hostKeyCallback = hostKeyCallback,
      passwordProvider = passwordProvider,
      keyContentProvider = keyContentProvider,
      passphraseProvider = passphraseProvider,
      reuseConnections = true,
    )

    // Pre-connect: run a no-op command to establish and authenticate the connection.
    // This ensures the connection is ready before we hand the runner to the caller.
    try {
      withContext(Dispatchers.IO) {
        runner.connect(profile)
      }
    } catch (e: Exception) {
      runner.close()
      throw e
    }

    val entry = PooledEntry(
      runner = runner,
      profile = profile,
      profileKey = key,
      createdAt = System.currentTimeMillis(),
    )
    entries[key] = entry
    return entry
  }

  /**
   * Reconnect a pooled entry using exponential backoff with jitter.
   *
   * Transitions the entry's state through [ConnectionState.Reconnecting] and
   * either back to [ConnectionState.Ready] on success or [ConnectionState.Failed]
   * on exhausted retries.
   */
  private suspend fun reconnectWithBackoff(
    existing: PooledEntry,
    profile: SshProfile,
    key: String,
  ): PooledEntry {
    // Close the old runner.
    disconnectEntry(existing)
    entries.remove(key)

    for (attempt in 1..config.maxReconnectAttempts) {
      val delayMs = calculateBackoff(attempt)
      existing.state.value = ConnectionState.Reconnecting(attempt, delayMs)

      delay(delayMs)

      try {
        val newEntry = createEntry(key, profile)
        newEntry.state.value = ConnectionState.Ready
        return newEntry
      } catch (e: Exception) {
        val category = categorizeError(e)
        if (category == "auth_failed" || category == "host_key_rejected" ||
          attempt == config.maxReconnectAttempts
        ) {
          existing.state.value = ConnectionState.Failed(
            message = e.message ?: "Reconnection failed after $attempt attempts",
            category = category,
          )
          throw e
        }
        // Continue to next attempt.
      }
    }

    // Should not reach here, but just in case.
    throw IllegalStateException("Reconnection exhausted")
  }

  /**
   * Calculate exponential backoff delay with jitter.
   *
   * Formula: min(baseDelay * 2^(attempt-1), maxDelay) + random jitter
   * where jitter is up to 25% of the calculated delay.
   */
  private fun calculateBackoff(attempt: Int): Long {
    val exponential = config.reconnectBaseDelayMs * (1L shl (attempt - 1).coerceAtMost(10))
    val capped = exponential.coerceAtMost(config.reconnectMaxDelayMs)
    val jitter = (capped * 0.25 * Math.random()).toLong()
    return capped + jitter
  }

  // ── Health checks ─────────────────────────────────────────────────────

  /**
   * Run health checks on all pooled connections.
   *
   * For each entry, calls [SshjExecRunner.isConnectionAlive]. If the probe fails,
   * marks the entry as disconnected for lazy reconnection.
   */
  private suspend fun runHealthChecks() {
    for ((_, entry) in entries) {
      val currentState = entry.state.value

      // Skip entries that are already being reconnected or have failed.
      if (currentState is ConnectionState.Reconnecting ||
        currentState is ConnectionState.Failed
      ) {
        continue
      }

      if (!entry.runner.isConnectionAlive()) {
        entry.state.value = ConnectionState.Disconnected("Health check failed — connection lost")
      } else {
        entry.lastUsedAt = System.currentTimeMillis()
      }
    }
  }

  // ── Eviction ──────────────────────────────────────────────────────────

  /**
   * Evict idle connections that have exceeded [ConnectionPoolConfig.ttlMs].
   */
  private fun evictExpired() {
    val now = System.currentTimeMillis()
    val iterator = entries.entries.iterator()
    while (iterator.hasNext()) {
      val (key, entry) = iterator.next()
      val idleMs = now - entry.lastUsedAt
      if (idleMs > config.ttlMs && entry.state.value != ConnectionState.Executing) {
        disconnectEntry(entry)
        iterator.remove()
      }
    }
  }

  /**
   * Evict the least-recently-used idle connection to make room for a new one.
   *
   * Only evicts entries whose state is [ConnectionState.Ready].
   */
  private fun evictLruIdle() {
    val lru = entries.entries
      .filter { it.value.state.value == ConnectionState.Ready }
      .minByOrNull { it.value.lastUsedAt }

    lru?.let { (key, entry) ->
      disconnectEntry(entry)
      entries.remove(key)
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private fun disconnectEntry(entry: PooledEntry) {
    try { entry.runner.close() } catch (_: Exception) {}
  }

  private fun profileKey(profile: SshProfile): String =
    "[${profile.host}]:${profile.port}:${profile.username}"

  private fun categorizeError(e: Exception): String = when (e) {
    is java.net.ConnectException -> "connection_refused"
    is java.net.SocketTimeoutException -> "timeout"
    is java.net.UnknownHostException -> "unknown_host"
    is SshHostKeyRejectedException -> "host_key_rejected"
    else -> if (e.message?.contains("Auth") == true) "auth_failed" else "ssh_error"
  }
}

/**
 * Wrapper around a pooled [SshjExecRunner] and its associated [PooledEntry].
 *
 * Created by [SshConnectionPool.acquire] and must be passed to
 * [SshConnectionPool.release] after command execution.
 *
 * @property runner  The exec runner for running commands.
 * @property entry   The underlying pool entry (for state observation).
 * @property poolKey The pool key for this entry.
 */
class AcquiredConnection(
  val runner: SshjExecRunner,
  internal val entry: PooledEntry,
  internal val poolKey: String,
) {
  /** Whether the execution semaphore permit has been released. */
  @Volatile internal var permitReleased: Boolean = false

  /** Observable connection state for this specific connection. */
  val connectionState: StateFlow<ConnectionState> = entry.state.asStateFlow()
}
