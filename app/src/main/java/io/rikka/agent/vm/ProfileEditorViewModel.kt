package io.rikka.agent.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.R
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Form state for the SSH profile editor screen.
 *
 * All fields have sensible defaults so the form can be initialized empty for "create" mode
 * or populated from an existing [SshProfile] for "edit" mode.
 *
 * @property name          Display name for the profile (auto-generated if blank on save).
 * @property host          SSH hostname or IP address.
 * @property port          SSH port as a string (parsed to Int on save, defaults to 22).
 * @property username      SSH username.
 * @property authType      Authentication method (PublicKey, Password, etc.).
 * @property keyRef        Reference to the SSH private key (path or alias), nullable.
 * @property codexMode     Whether to enable Codex JSONL mode for this profile.
 * @property codexWorkDir  Working directory for Codex execution, nullable.
 * @property codexApiKey   API key for Codex mode, nullable.
 * @property group         Purpose-based grouping for organizational filtering.
 * @property tags          Comma-separated tag string for freeform categorization.
 */
data class ProfileForm(
  val name: String = "",
  val host: String = "",
  val port: String = "22",
  val username: String = "root",
  val authType: AuthType = AuthType.PublicKey,
  val keyRef: String? = null,
  val codexMode: Boolean = false,
  val codexWorkDir: String = "",
  val codexApiKey: String = "",
  val group: ProfileGroup = ProfileGroup.None,
  val tags: String = "",
)

/**
 * ViewModel for the SSH profile editor screen (create / edit).
 *
 * ## Responsibilities
 * - Holds the [ProfileForm] state for the editor UI and provides [updateForm] for two-way binding.
 * - Loads an existing profile into the form on init when [profileId] is non-null (edit mode).
 * - Persists the profile via [ProfileStore.upsert] on [save], auto-generating a name if blank.
 * - Provides a TCP-level [testConnection] that verifies host:port reachability and reads
 *   the SSH banner, running on [Dispatchers.IO].
 *
 * ## Thread Safety
 * - All mutable UI-facing state is held in [MutableStateFlow] instances, safe for concurrent access.
 * - [save] and [testConnection] launch coroutines in [viewModelScope]; the network test
 *   explicitly switches to [Dispatchers.IO] to avoid blocking the Main thread.
 * - The underlying [ProfileStore] Room DAO operations are inherently thread-safe.
 *
 * ## Exposed State
 * | StateFlow       | Type                 | Description                                         |
 * |-----------------|----------------------|-----------------------------------------------------|
 * | [form]          | `StateFlow<ProfileForm>` | Current form state for two-way binding.          |
 * | [saved]         | `StateFlow<Boolean>` | `true` after a successful save (single-shot event). |
 * | [testResult]    | `StateFlow<String?>` | Connection test result message, or null.            |
 * | [testing]       | `StateFlow<Boolean>` | `true` while a connection test is in progress.      |
 *
 * @param profileId The profile ID to edit, or `null` for create mode.
 * @param store     The [ProfileStore] for profile persistence.
 * @param app       Android [Application] for string resource access.
 */
class ProfileEditorViewModel(
  private val profileId: String?,
  private val store: ProfileStore,
  private val app: Application,
) : ViewModel() {

  /** Backing field for the profile form state. */
  private val _form = MutableStateFlow(ProfileForm())
  /** Observable form state for two-way data binding with the editor UI. */
  val form: StateFlow<ProfileForm> = _form

  /** Backing field for the save completion flag. */
  private val _saved = MutableStateFlow(false)
  /** `true` after a successful [save] call. Reset by the UI after navigation. */
  val saved: StateFlow<Boolean> = _saved

  /** Backing field for the connection test result. */
  private val _testResult = MutableStateFlow<String?>(null)
  /** Connection test result message (success or error), or null if not tested / cleared. */
  val testResult: StateFlow<String?> = _testResult

  /** Backing field for the testing-in-progress flag. */
  private val _testing = MutableStateFlow(false)
  /** `true` while a [testConnection] call is in progress. */
  val testing: StateFlow<Boolean> = _testing

  init {
    if (profileId != null) {
      viewModelScope.launch {
        store.getById(profileId)?.let { p ->
          _form.value = ProfileForm(
            name = p.name,
            host = p.host,
            port = p.port.toString(),
            username = p.username,
            authType = p.authType,
            keyRef = p.keyRef,
            codexMode = p.codexMode,
            codexWorkDir = p.codexWorkDir ?: "",
            codexApiKey = p.codexApiKey ?: "",
            group = p.group,
            tags = p.tags.joinToString(", "),
          )
        }
      }
    }
  }

  /**
   * Update the form state. Called from the UI on every field change for two-way binding.
   *
   * @param updated The new [ProfileForm] value.
   */
  fun updateForm(updated: ProfileForm) {
    _form.value = updated
  }

  /**
   * Persist the current form as an [SshProfile].
   *
   * - Validates that host and username are non-blank; returns silently otherwise.
   * - Auto-generates a display name from `username@host[:port]` if the name field is blank.
   * - Uses the existing [profileId] for edits, or generates a new UUID for creates.
   * - Sets [saved] to `true` on success.
   */
  fun save() {
    val f = _form.value
    if (f.host.isBlank() || f.username.isBlank()) return
    viewModelScope.launch {
      val autoName = f.name.ifBlank {
        val portSuffix = if ((f.port.toIntOrNull() ?: 22) != 22) ":${f.port}" else ""
        "${f.username.trim()}@${f.host.trim()}$portSuffix"
      }
      val parsedTags = f.tags.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
      val profile = SshProfile(
        id = profileId ?: UUID.randomUUID().toString(),
        name = autoName,
        host = f.host.trim(),
        port = f.port.toIntOrNull() ?: 22,
        username = f.username.trim(),
        authType = f.authType,
        keyRef = f.keyRef,
        codexMode = f.codexMode,
        codexWorkDir = f.codexWorkDir.trim().ifBlank { null },
        codexApiKey = f.codexApiKey.trim().ifBlank { null },
        group = f.group,
        tags = parsedTags,
      )
      store.upsert(profile)
      _saved.value = true
    }
  }

  /**
   * Perform a TCP-level connection test: verifies host:port reachability and reads the SSH banner.
   *
   * Runs on [Dispatchers.IO] to avoid blocking the Main thread. Sets [testing] to `true`
   * while in progress and writes the result to [testResult].
   *
   * Possible outcomes:
   * - Success: Displays the SSH banner string.
   * - Connection refused: Host reachable but port closed.
   * - Timeout: Host unreachable or firewall blocking.
   * - Unknown host: DNS resolution failed.
   * - Generic error: Any other exception.
   */
  fun testConnection() {
    val f = _form.value
    val host = f.host.trim()
    val port = f.port.toIntOrNull() ?: 22
    if (host.isBlank()) {
      _testResult.value = app.getString(R.string.test_host_empty)
      return
    }
    _testing.value = true
    _testResult.value = null
    viewModelScope.launch {
      _testResult.value = withContext(Dispatchers.IO) {
        try {
          Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 3000
            val banner = BufferedReader(InputStreamReader(socket.getInputStream()))
              .readLine() ?: app.getString(R.string.test_no_banner)
            app.getString(R.string.test_ok, banner)
          }
        } catch (e: java.net.ConnectException) {
          app.getString(R.string.test_refused, host, port)
        } catch (e: java.net.SocketTimeoutException) {
          app.getString(R.string.test_timeout, host)
        } catch (e: java.net.UnknownHostException) {
          app.getString(R.string.test_host_not_found)
        } catch (e: Exception) {
          app.getString(R.string.test_failed, e.message ?: "unknown")
        }
      }
      _testing.value = false
    }
  }

  /**
   * Clear the current [testResult]. Called by the UI after the user dismisses the result.
   */
  fun clearTestResult() {
    _testResult.value = null
  }
}
