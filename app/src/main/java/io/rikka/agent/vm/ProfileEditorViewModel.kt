package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.RoomProfileStore
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

data class ProfileForm(
  val name: String = "",
  val host: String = "",
  val port: String = "22",
  val username: String = "root",
  val authType: AuthType = AuthType.PublicKey,
  val keyRef: String? = null,
)

class ProfileEditorViewModel(
  private val profileId: String?,
  private val store: RoomProfileStore,
) : ViewModel() {

  private val _form = MutableStateFlow(ProfileForm())
  val form: StateFlow<ProfileForm> = _form

  private val _saved = MutableStateFlow(false)
  val saved: StateFlow<Boolean> = _saved

  private val _testResult = MutableStateFlow<String?>(null)
  val testResult: StateFlow<String?> = _testResult

  private val _testing = MutableStateFlow(false)
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
          )
        }
      }
    }
  }

  fun updateForm(updated: ProfileForm) {
    _form.value = updated
  }

  fun save() {
    val f = _form.value
    if (f.host.isBlank() || f.username.isBlank()) return
    viewModelScope.launch {
      val profile = SshProfile(
        id = profileId ?: UUID.randomUUID().toString(),
        name = f.name,
        host = f.host.trim(),
        port = f.port.toIntOrNull() ?: 22,
        username = f.username.trim(),
        authType = f.authType,
        keyRef = f.keyRef,
      )
      store.upsert(profile)
      _saved.value = true
    }
  }

  /** TCP-level connection test: verifies host:port reachable + reads SSH banner. */
  fun testConnection() {
    val f = _form.value
    val host = f.host.trim()
    val port = f.port.toIntOrNull() ?: 22
    if (host.isBlank()) {
      _testResult.value = "Host is empty"
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
              .readLine() ?: "Connected (no banner)"
            "OK — $banner"
          }
        } catch (e: java.net.ConnectException) {
          "Connection refused — is SSH running on $host:$port?"
        } catch (e: java.net.SocketTimeoutException) {
          "Timed out — host $host may be unreachable."
        } catch (e: java.net.UnknownHostException) {
          "Host not found — check the hostname."
        } catch (e: Exception) {
          "Failed: ${e.message}"
        }
      }
      _testing.value = false
    }
  }

  fun clearTestResult() {
    _testResult.value = null
  }
}
