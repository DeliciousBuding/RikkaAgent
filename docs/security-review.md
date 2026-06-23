# RikkaAgent -- Security Review

> Based on source code audit (2026-06-23) | Covers v0.1.0 codebase

---

## 1. SSH Key Storage

### 1.1 Overview

RikkaAgent supports two private key storage modes, both managed by `ContentUriKeyContentProvider`:

### 1.2 SAF ContentUri (External Reference)

When the user imports a key via the Android file picker:

```
User selects key file via SAF (Storage Access Framework)
  -> App receives content:// URI
  -> URI stored as profile.keyRef
  -> On connection: ContentResolver.openInputStream(uri) -> read key text
  -> Private key never copied to app's private directory
```

**Security properties:**
- The app holds a URI permission grant, not the key content itself
- If the original ContentProvider URI becomes invalid, the key cannot be read
- On app uninstall, the URI grant is automatically revoked
- No copy of the key remains in app storage

**Risk:** The content URI may be accessible to other apps if the source ContentProvider does not enforce permissions.

### 1.3 EncryptedInternalKeyStore (Pasted Keys)

When the user pastes key content directly (clipboard import):

```
User pastes private key content
  -> ContentUriKeyContentProvider.savePastedKey(content)
  -> EncryptedInternalKeyStore.write(keyId, content)
  -> EncryptedFile (AES-256-GCM-HKDF-4KB) written to filesDir/ssh_keys/{keyId}
  -> Returns "internal-key://{keyId}" as profile.keyRef
```

**Encryption details:**

| Parameter | Value |
|---|---|
| Encryption scheme | `AES256_GCM_HKDF_4KB` |
| Master key | `MasterKeys.AES256_GCM_SPEC` (Android Keystore, hardware-backed on devices with TEE) |
| Key size | 256-bit AES |
| Mode | GCM (authenticated encryption) |
| Key derivation | HKDF |
| File location | `context.filesDir/ssh_keys/{uuid}` |

**Legacy fallback (security gap):**

```kotlin
override fun read(keyId: String): String? {
    val file = File(keysDir, keyId)
    if (!file.exists()) return null
    return try {
        encryptedFile(file).openFileInput().use { ... }  // AES-256-GCM read
    } catch (_: Exception) {
        // Legacy fallback: plaintext read
        file.readText(Charsets.UTF_8)                    // PLAINTEXT fallback!
    }
}
```

**Impact:** Keys stored before the encryption migration remain as plaintext files. The `read()` method silently falls back to plaintext if encrypted read fails, meaning an old unencrypted key file is still accessible.

### 1.4 Codex API Key Storage (Gap)

**Current state:** `codexApiKey` is stored as a plaintext column in `SshProfileEntity`:

```kotlin
@Entity(tableName = "ssh_profiles")
data class SshProfileEntity(
    // ...
    val codexApiKey: String? = null,  // PLAINTEXT in Room DB
)
```

The Room database (`rikka_agent.db`) is not encrypted. On a rooted device, an attacker can read the database and extract API keys. Android Auto Backup may also include the database.

**Recommended fix:** Move `codexApiKey` to `EncryptedSharedPreferences` or `EncryptedFile`, storing only a reference key in the Room profile.

### 1.5 Key Storage Summary

| Asset | Mechanism | Encrypted? | Risk Level |
|---|---|---|---|
| Private key (SAF import) | Content URI, no app copy | N/A (not stored) | Low |
| Private key (pasted) | EncryptedFile (AES-256-GCM) | Yes (new) / No (legacy fallback) | **Medium** |
| Codex API Key | Room DB plaintext column | No | **High** |
| SSH password | In-memory only, never persisted | N/A | Low |
| Key passphrase | In-memory only, never persisted | N/A | Low |

---

## 2. Password Handling

### 2.1 Design

SSH passwords and key passphrases are **never persisted** -- they exist only in memory during the authentication phase:

```
SSH connection attempt
  -> PasswordProvider.getPassword(profile)  // suspend callback
  -> UI shows password dialog (AlertDialog with PasswordVisualTransformation)
  -> User enters password -> respondToPassword(password: String?)
  -> sshj uses password for AuthPassword
  -> After authentication: password String eligible for GC
```

### 2.2 Implementation

```kotlin
// SshjExecRunner.kt -- authenticate()
AuthType.Password -> {
    val password = passwordProvider?.getPassword(profile)
        ?: throw IllegalStateException("...")
    client.authPassword(username, password)
}
```

The `passwordProvider` is a lambda that bridges to a `MutableSharedFlow`:

```kotlin
val passwordProvider = PasswordProvider { profile ->
    val desc = "${profile.username}@${profile.host}:${profile.port}"
    _passwordRequest.emit(desc)          // Trigger UI dialog
    _passwordResponse.first()            // Await user input
}
```

### 2.3 Security Analysis

| Concern | Status | Detail |
|---|---|---|
| Persistence | Safe | Password never written to disk, database, or DataStore |
| Memory lifetime | Medium | `String` object lives until GC; cannot be explicitly zeroed (JVM limitation) |
| Log exposure | Safe | Password never appears in logcat |
| Transmission | Safe | SSH transport layer encrypts the password during auth |
| SharedFlow buffer | Safe | Only one value buffered, consumed atomically |

### 2.4 Residual Risks

1. **JVM String immutability**: After `authenticate()` returns, the password `String` remains in memory until GC reclaims it. The JVM provides no mechanism to explicitly clear a `String`'s backing `char[]`. On a memory-dump-capable attacker (e.g., root with `am dumpheap`), the password could be recovered.

2. **Password in throwable messages**: Some sshj exceptions include the authentication context in their message. The app's error handler uses `ErrorMessageMapper` which maps categories (e.g., `"auth_failed"`) rather than propagating raw exception messages, mitigating this.

3. **No CharArray usage**: Switching `PasswordProvider` from returning `String` to `CharArray` would allow explicit zeroing after use (`Arrays.fill(charArray, '0')`), but the `String`-based sshj API (`client.authPassword(username, String)`) limits this option.

---

## 3. Network Security Configuration

### 3.1 Android Network Security Config

The app defines its network security policy via `AndroidManifest.xml` and optionally `res/xml/network_security_config.xml`.

**Current analysis:**

| Setting | Status | Recommendation |
|---|---|---|
| `android:usesCleartextTraffic` | Not explicitly set (default = false for targetSdk 35) | Safe -- cleartext HTTP not allowed |
| Certificate pinning | Not applicable | App connects only via SSH, not HTTP/TLS |
| Proxy configuration | Not configured | SSH is direct; no HTTP proxy needed |
| Intent-based proxy | Not configured | No VPN or proxy intent handling |

### 3.2 SSH Transport Security

The sshj library handles all network transport. Default algorithm negotiation:

| Category | Algorithms | Security Level |
|---|---|---|
| Key exchange | Curve25519, ECDH (P-256/384/521), DH (group14, group16, group18) | Modern |
| Server host key | Ed25519, ECDSA, RSA (2048+), DSA (disabled) | Strong |
| Encryption | AES-256-GCM, ChaCha20-Poly1305, AES-128-GCM | AEAD, strong |
| MAC | AEAD modes use built-in authentication; fallback: HMAC-SHA2-256/512 | Strong |

**The app does not restrict or customize the sshj algorithm set.** This is acceptable because:
- sshj's defaults are modern and avoid known-weak algorithms (CBC, SHA-1 MAC, RC4)
- Android's Bouncy Castle provider is used for cryptographic operations
- Algorithm downgrade is not possible without server cooperation

### 3.3 Connection Security

| Property | Implementation | Risk |
|---|---|---|
| Connection reuse | Cached by `[host]:port:username`; stale detection on next use | Low -- no cross-user sharing |
| Keepalive | `keepaliveIntervalSec` (default 60s) | Low -- prevents orphan connections |
| Retry | One automatic retry on stale connection | Low -- retry count bounded |
| Auth failure retry | Never retry on auth failure | Safe -- prevents account lockout |
| Cancel handling | `ExecEvent.Canceled` emitted, channel closed | Safe -- no partial state leak |
| Connection pool | `SshConnectionPool` with mutex-guarded map | Safe -- per-profile isolation |

---

## 4. Known Hosts Verification

### 4.1 Architecture

Host key verification follows the TOFU (Trust On First Use) model, implemented in `SshjExecRunner.buildVerifier()`:

```
sshj HostKeyVerifier.verify(hostname, port, key)
  |
  |-- profile.hostKeyPolicy == AcceptAll -> true (no verification)
  |-- profile.hostKeyPolicy == RejectUnknown
  |     |-- knownHostsStore.getFingerprint(host, port) present and match -> true
  |     |-- else -> false (connection rejected)
  |
  |-- profile.hostKeyPolicy == TrustFirstUse (default)
        |-- No stored key -> HostKeyCallback.onUnknownHost()
        |     |-- User accepts -> store fingerprint -> true
        |     |-- User rejects -> false (SshHostKeyRejectedException)
        |
        |-- Stored key matches -> true (fast path, no user interaction)
        |
        |-- Stored key MISMATCHES -> HostKeyCallback.onHostKeyMismatch()
              |-- User accepts replacement -> update stored fingerprint -> true
              |-- User rejects -> false
              |-- User accepts replacement -> SECONDARY CONFIRMATION dialog
                    |-- User confirms -> true
                    |-- User rejects -> false
```

### 4.2 Three Policies

| Policy | Behavior | Use Case | Risk |
|---|---|---|---|
| **TrustFirstUse** (default) | Show fingerprint on first connect, verify on subsequent | Standard TOFU -- same as OpenSSH | First-connect MITM (user must verify out-of-band) |
| **RejectUnknown** | Only connect if fingerprint already stored | CI/CD, scripted access | Cannot connect to unregistered hosts |
| **AcceptAll** | Accept any host key | Development, isolated networks | **High** -- vulnerable to MITM |

### 4.3 Sync-to-Async Bridge

sshj's `HostKeyVerifier.verify()` is a synchronous callback. RikkaAgent bridges it to an async UI decision flow:

```
sshj IO thread:
  verify() -> knownHostFingerprint loaded (susending, pre-fetched)
           -> HostKeyCallback.onUnknownHost() / onHostKeyMismatch()
           -> CompletableDeferred<Boolean>.await()  // single runBlocking
           -> returns true/false to sshj

Main thread (UI):
  _hostKeyEvent.emit(HostKeyEvent)     -> Compose collects
  Compose shows dialog                 -> User accepts/rejects
  respondToHostKey(decision)           -> _hostKeyDecision.tryEmit()
```

The `CompletableDeferred` pattern is the **single unavoidable `runBlocking`** in the SSH layer. It is a pure signal wait with no coroutine dispatching, so it cannot cause deadlocks.

### 4.4 Mismatch Secondary Confirmation

When a stored fingerprint mismatches, a two-step dialog sequence is enforced:

1. **First dialog**: Shows expected vs actual fingerprint. User clicks "Replace Trust"
2. **Second dialog**: "Are you sure you want to replace the host key for [host]:[port]?" User confirms or rejects

This prevents accidental one-click trust replacement. The state machine is implemented in `HostKeyDialogStateMachine.kt`.

### 4.5 Storage

Fingerprints are stored via `DataStoreKnownHostsStore`:

```kotlin
prefKey = "[{host}]:{port}"  // e.g., "[example.com]:22"
value = jsonEncode(StoredHostKey(fingerprint, keyType, addedAtMs))
```

**Storage security:** Fingerprints are stored in plaintext in DataStore. This is acceptable because:
- Host key fingerprints are public information (the server sends them to every connecting client)
- Integrity matters more than confidentiality for known hosts
- DataStore is not encrypted by default, but fingerprints pose no credential disclosure risk

### 4.6 Gaps

| Issue | Severity | Status |
|---|---|---|
| `AcceptAll` policy has no persistent UI warning banner | Medium | Only documented, not enforced in UI |
| No fingerprint hash visualization (like OpenSSH's randomart) | Low | SHA-256 hex string shown in dialog |
| No host key expiry or rotation alert | Low | Fingerprint stays until user deletes |

---

## 5. Sensitive Data in Logs

### 5.1 Log Analysis

The codebase was audited for log statements that could leak sensitive information.

**Log sources found in app code:**

| File | Log Statement | Sensitive? |
|---|---|---|
| `ChatViewModel.kt` | No explicit logging of commands or output | Safe |
| `SshjExecRunner.kt` | Exception messages from sshj (caught and mapped to categories) | **Conditional** |
| `ContentUriKeyContentProvider.kt` | No logging of key content | Safe |
| `ErrorMessageMapper.kt` | Maps raw exceptions to user-friendly strings; no credential exposure | Safe |
| `SessionExporter.kt` | No logging | Safe |

### 5.2 Error Message Mapping

`ErrorMessageMapper` converts raw SSH exceptions into categorized user-friendly messages:

```kotlin
fun friendlyErrorMessage(category: String, raw: String, ...): String = when (category) {
    "connection_refused" -> "Connection refused"
    "timeout" -> "Connection timed out"
    "unknown_host" -> "Unknown host"
    "auth_failed" -> "Authentication failed"
    else -> generic(raw)  // fallback: shows raw message
}
```

**Risk:** The `generic` fallback path passes the raw exception message to the user. If an exception message contains protocol details (key fingerprints, partial credentials), these would be shown. In practice, sshj's exception messages typically contain hostnames, port numbers, and algorithm names -- not credentials.

### 5.3 sshj Library Logging

sshj uses SLF4J for internal logging. The app does not configure an SLF4J binding:

| Log Level | Content | Risk |
|---|---|---|
| INFO | Connection lifecycle | Low -- no secrets |
| DEBUG | Protocol messages, key exchange details | **Medium** -- key exchange material, algorithm negotiation |
| TRACE | Raw packet bytes | **High** -- contains encrypted data, not credentials |

**Recommendation:** Ensure sshj's effective log level is set to WARN or higher in release builds to prevent accidental disclosure during debugging.

### 5.4 Privacy Audit Reference

The separate [privacy-audit.md](privacy-audit.md) confirms:
- No analytics SDK (Firebase, Crashlytics, Amplitude, etc.)
- No telemetry or usage data collection
- No third-party network requests other than SSH connections
- All data remains on-device

---

## 6. Command Injection Protection

### 6.1 shellQuote Implementation

```kotlin
fun shellQuote(input: String): String {
    val escaped = input.replace("'", "'\\''")
    return "'$escaped'"
}
```

Uses the standard POSIX single-quote escaping strategy:
- Wrap entire string in single quotes: `'...'`
- Any internal `'` is replaced with `'\''` (end quote, escaped quote, reopen quote)

This is the same approach used by Ruby's `Shellwords.escape` and Python's `pipes.quote`.

### 6.2 wrapForCodex Analysis

```kotlin
fun wrapForCodex(task: String, workDir: String?, apiKey: String?): String {
    val escapedTask = task.replace("\"", "\\\"")
    val cdPart = if (!workDir.isNullOrBlank()) "cd ${shellQuote(workDir)} && " else ""
    val envPart = if (!apiKey.isNullOrBlank()) "OPENAI_API_KEY=${shellQuote(apiKey)} " else ""
    return "${cdPart}${envPart}codex exec --json --full-auto \"$escapedTask\""
}
```

**Vectors tested:**

| Input Vector | Escape Mechanism | Result |
|---|---|---|
| `workDir: "/tmp; rm -rf /"` | `shellQuote()` -> `'/tmp; rm -rf /'` | Safe (treated as literal path) |
| `apiKey: "'; malicious_cmd'"` | `shellQuote()` -> `"''\"'\"'; malicious_cmd'"'"` | Safe (shell-quoted) |
| `task: "'; malicious_cmd'"` | `replace("\"", "\\\"")` only | **Partial** |
| `task: "$(malicious)"` | Not escaped (inside double quotes) | **Partial** |
| `task: \`malicious\`` | Not escaped (inside double quotes) | **Partial** |

**Gap:** The `task` parameter is only escaped for double quotes (`"`) but not for backticks or `$()` command substitution, because it is wrapped in double quotes (`"$escapedTask"`). Within double quotes, `$`, `` ` ``, `\`, `!` retain special meaning in most shells.

**Mitigation:** The command is executed by sshj's `session.exec(command)`, which sends the raw string to the remote shell. The same shell parsing risks apply as if the user typed the command directly. Since the user is intentionally executing commands on their own server, this is an accepted risk.

### 6.3 wrapWithShell Analysis

```kotlin
fun wrapWithShell(command: String, shell: String): String {
    if (shell == "/bin/sh" || shell.isBlank()) return command  // No wrapping for /bin/sh
    val escaped = command.replace("'", "'\\''")
    return "$shell -c '$escaped'"
}
```

- When `shell` is `/bin/sh` or blank, the command is passed unwrapped -- sshj sends it directly
- When a custom shell is configured, the command is single-quote wrapped inside `-c`
- `shellQuote()` ensures single quotes in the command are properly escaped

---

## 7. Recommended Remediations

### High Priority

| ID | Issue | Location | Recommendation |
|---|---|---|---|
| SEC-01 | Codex API Key in plaintext Room DB | `SshProfileEntity.codexApiKey` | Move to `EncryptedSharedPreferences` or `EncryptedFile`; store only a reference in the profile |
| SEC-02 | Legacy plaintext key fallback | `EncryptedInternalKeyStore.read()` | Remove the `catch` fallback; migrate existing plaintext keys on first read |
| SEC-03 | `wrapForCodex()` task parameter shell escape | `CommandComposer.wrapForCodex()` | Use `shellQuote()` for the task parameter instead of double-quote-only escaping |
| SEC-04 | sshj log level in release builds | No SLF4J binding configured | Set sshj log level to WARN or higher; configure SLF4J simple logger |

### Medium Priority

| ID | Issue | Location | Recommendation |
|---|---|---|---|
| SEC-05 | No `AcceptAll` warning banner | `SettingsScreen` | Add persistent UI warning when `AcceptAll` is selected |
| SEC-06 | Room database not encrypted | `AppDatabase` | Evaluate SQLCipher integration for profile and message tables |
| SEC-07 | No `android:allowBackup` restriction | `AndroidManifest.xml` | Add `android:allowBackup="false"` or use `android:fullBackupContent` to exclude sensitive files |
| SEC-08 | Password as `String` in auth flow | `PasswordProvider` | Consider `CharArray` interface and zero-fill after use (limited by sshj API) |
| SEC-09 | Export no sensitive data warning | `SessionExporter` | Add "export may contain sensitive data" dialog before sharing |

### Low Priority

| ID | Issue | Location | Recommendation |
|---|---|---|---|
| SEC-10 | No host key randomart visualization | `HostKeyDialog` | Add ASCII randomart alongside SHA-256 fingerprint (improves user verification) |
| SEC-11 | No biometric gate for key access | `EncryptedInternalKeyStore` | Consider `BiometricPrompt` for accessing stored private keys |
| SEC-12 | No root detection | Application | Optional root detection with warning banner (not a security control) |
| SEC-13 | `defaultShell` no whitelist | `CommandComposer` | Restrict `defaultShell` to `/bin/sh`, `/bin/bash`, `/bin/zsh` |
| SEC-14 | `wrapForCodex()` exposes API Key in `ps aux` | `CommandComposer` | Pass API Key via `stdin` or temporary env file instead of command line |

---

## 8. Security-by-Design Summary

### What's Done Right

1. **Zero plaintext credentials**: SSH passwords and passphrases are never persisted
2. **Encrypted key storage**: Android Keystore-backed AES-256-GCM for app-managed keys
3. **TOFU host key verification with secondary confirmation**: Mismatch requires two explicit user actions
4. **Minimum Android permissions**: Only `INTERNET` permission declared
5. **No analytics/telemetry**: Zero third-party SDKs; no network data collection
6. **Command injection protection**: `shellQuote()` implements POSIX-standard single-quote escaping
7. **Safe error messages**: Categorized error mapping prevents raw exception leaks
8. **Connection isolation**: Per-profile connection caching prevents cross-user leaks
9. **Cancel safety**: `ExecEvent.Canceled` ensures clean state after cancellation
10. **Clean Architecture**: `core:model` has zero Android dependencies; strict module boundaries

### What Needs Work

1. **Codex API Key in plaintext**: The most critical gap -- API keys are stored unencrypted in Room DB
2. **Legacy plaintext key fallback**: Old key files remain readable as plaintext
3. **sshj log level**: Library-level logging may expose protocol details in debug builds
4. **Backup exposure risk**: Database backup inclusion needs explicit exclusion rules

---

## Appendix A: Code Paths Referenced

| Component | File |
|---|---|
| Key storage (encrypted) | `app/.../ssh/ContentUriKeyContentProvider.kt` |
| Key storage (SAF URI) | `app/.../ssh/ContentUriKeyContentProvider.kt` |
| Key format detection | `core/ssh/.../ssh/SshjExecRunner.kt` |
| Password/passphrase providers | `core/ssh/.../ssh/SshjExecRunner.kt` |
| Host key verification | `core/ssh/.../ssh/SshjExecRunner.kt` (buildVerifier) |
| Known hosts store | `app/.../ssh/DataStoreKnownHostsStore.kt` |
| Command composition | `app/.../vm/CommandComposer.kt` |
| Error message mapping | `app/.../vm/ErrorMessageMapper.kt` |
| Session export	| `app/.../vm/SessionExporter.kt` |
| SSH connection pool | `core/ssh/.../ssh/SshConnectionPool.kt` |
| Room database | `core/storage/.../db/AppDatabase.kt` |
| Profile entity | `core/storage/.../db/SshProfileEntity.kt` |
| Chat ViewModel | `app/.../vm/ChatViewModel.kt` |

## Appendix B: Related Documents

| Document | Path |
|---|---|
| Threat Model | [threat-model.md](threat-model.md) |
| Privacy Audit | [privacy-audit.md](privacy-audit.md) |
| Server Hardening Guide | [server-hardening.md](server-hardening.md) |
| Testing Strategy | [testing.md](testing.md) |
| Security Testing Checklist | [spec/40-security.md](spec/40-security.md) |
