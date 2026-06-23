package io.rikka.agent.model

import kotlinx.serialization.Serializable

/**
 * Purpose-based grouping for SSH profiles.
 *
 * Helps users organize profiles by deployment target or usage context.
 * Each group can have different default settings or visual indicators.
 *
 * ## Thread safety
 *
 * Enum constants are inherently thread-safe.
 */
@Serializable
enum class ProfileGroup {
  /** Development and local environments. */
  Development,

  /** Production and live service environments. */
  Production,

  /** Testing, staging, and QA environments. */
  Testing,

  /** Personal or hobby projects. */
  Personal,

  /** Uncategorized / no specific group. */
  None,
}

/**
 * Configuration profile for an SSH connection.
 *
 * Stores all parameters needed to establish and maintain an SSH session to
 * a remote host, including authentication credentials, host key policy, and
 * optional Codex integration settings.
 *
 * ## Thread safety
 *
 * Immutable data class. Safe to share across threads without synchronization.
 *
 * ## Example
 *
 * ```kotlin
 * val profile = SshProfile(
 *     id = "prod-server",
 *     name = "Production Server",
 *     host = "10.0.1.50",
 *     port = 22,
 *     username = "deploy",
 *     authType = AuthType.PublicKey,
 *     keyRef = "~/.ssh/id_ed25519",
 *     hostKeyPolicy = HostKeyPolicy.TrustFirstUse,
 *     keepaliveIntervalSec = 30,
 *     group = ProfileGroup.Production,
 *     tags = listOf("web", "nginx", "deploy"),
 * )
 * ```
 *
 * @property id Unique identifier for this profile. Must be unique across all
 *   profiles. Typically a short slug (e.g. `"prod-server"`, `"dev-box"`).
 * @property name Human-readable display name for this profile.
 * @property host The remote host to connect to. Can be an IP address
 *   (e.g. `"10.0.1.50"`) or a hostname (e.g. `"server.example.com"`).
 * @property port SSH server port. Defaults to `22` (the standard SSH port).
 *   Valid range: 1..65535.
 * @property username The username to authenticate as on the remote host.
 * @property authType The authentication method to use. Defaults to
 *   [AuthType.PublicKey].
 * @property keyRef Reference to the private key for [AuthType.PublicKey]
 *   authentication. Can be a file path (e.g. `"~/.ssh/id_ed25519"`) or a
 *   key identifier. `null` when [authType] is [AuthType.Password].
 * @property hostKeyPolicy How to handle unknown host keys during the initial
 *   connection. Defaults to [HostKeyPolicy.TrustFirstUse].
 * @property keepaliveIntervalSec Interval in seconds between SSH keepalive
 *   messages to prevent the connection from being dropped by firewalls or
 *   NAT devices. Defaults to `60`. Set to `0` to disable keepalive.
 * @property codexMode Whether to enable Codex mode for this connection.
 *   When `true`, the SSH session will be configured for Codex AI tool
 *   integration. Defaults to `false`.
 * @property codexWorkDir Working directory for Codex on the remote host.
 *   Only used when [codexMode] is `true`. `null` means use the remote
 *   user's default working directory.
 * @property codexApiKey API key for Codex authentication on the remote host.
 *   Only used when [codexMode] is `true`. `null` when Codex does not require
 *   an API key or when the key is managed externally.
 * @property group Purpose-based grouping for organizational filtering.
 *   Defaults to [ProfileGroup.None].
 * @property tags Freeform string labels for cross-cutting categorization
 *   (e.g. `"web"`, `"docker"`, `"gpu"`). Empty by default.
 */
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
  val group: ProfileGroup = ProfileGroup.None,
  val tags: List<String> = emptyList(),
)

/**
 * SSH authentication method.
 *
 * Determines how the client authenticates itself to the SSH server.
 *
 * ## Thread safety
 *
 * Enum constants are inherently thread-safe.
 */
@Serializable
enum class AuthType {
  /**
   * Public-key authentication (SSH key pair).
   *
   * Uses the private key referenced by [SshProfile.keyRef]. This is the
   * most secure and recommended authentication method.
   */
  PublicKey,

  /**
   * Password authentication.
   *
   * The password is provided interactively or from a secure credential store.
   * Less secure than [PublicKey]; use only when key-based auth is unavailable.
   */
  Password,
}

/**
 * Policy for handling unknown SSH host keys.
 *
 * Controls how the SSH client behaves when it encounters a host key that is
 * not already in the known-hosts file.
 *
 * ## Thread safety
 *
 * Enum constants are inherently thread-safe.
 */
@Serializable
enum class HostKeyPolicy {
  /**
   * Trust the host key on first connection, then verify on subsequent connections.
   *
   * On the first connection, the host key is saved to the known-hosts file.
   * On later connections, the key is verified against the saved value. If the
   * key changes, the connection is rejected (potential MITM attack).
   *
   * This is the default and recommended policy for most use cases.
   */
  TrustFirstUse,

  /**
   * Reject connections to hosts with unknown keys.
   *
   * The host key must already be present in the known-hosts file. Connections
   * to unknown hosts are refused. Use this in high-security environments
   * where host keys are pre-distributed.
   */
  RejectUnknown,

  /**
   * Accept all host keys without verification.
   *
   * **Warning**: This disables host key verification entirely and is
   * vulnerable to man-in-the-middle attacks. Use only for testing or
   * in trusted networks.
   */
  AcceptAll,
}
