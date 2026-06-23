# Error Handling Standardization

> Status: Proposed
> Created: 2026-06-23
> Scope: `core/ssh`, `app/vm`, `core/ui`

## 1. Problem Statement

Current error handling has three structural weaknesses:

1. **String-based categories are fragile.** `ExecEvent.Error(category: String, ...)` and `ConnectionError(category: String, ...)` rely on magic strings (`"connection_refused"`, `"timeout"`, `"auth_failed"`, etc.) that are scattered across `SshjExecRunner`, `CommandExecutor`, and `ErrorMessageMapper`. A typo produces a silent fallback to the generic branch.

2. **No type-safe error hierarchy.** Each layer invents its own ad-hoc error representation. `SshjExecRunner` catches exceptions and emits `ExecEvent.Error` with string categories. `CommandExecutor` wraps these into `ConnectionError(category, message)`. `ErrorMessageMapper` maps strings back to localized strings. There is no single sealed type that flows from SSH layer to UI.

3. **Error display is disconnected from error types.** `ErrorInfo` (UI) and `ConnectionError` (VM) are separate data classes with no conversion path. Retry logic is ad-hoc. No structured logging exists.

## 2. Design Goals

| # | Goal | Non-Goal |
|---|------|----------|
| G1 | Single sealed error type usable across SSH, VM, and UI layers | Replacing `Result<T>` with `Either` everywhere |
| G2 | Type-safe error categories (no magic strings) | Exhaustive error handling at every call site |
| G3 | Each error carries a user-facing message key + retry hint | Auto-retry at the SSH layer |
| G4 | Seamless conversion to existing `ErrorInfo` for UI display | Replacing `ErrorCard` composable |
| G5 | Structured logging with sensitive-data filtering | Full telemetry/analytics pipeline |
| G6 | Backward-compatible migration (coexistence period) | Big-bang rewrite |

## 3. Error Type Hierarchy

### 3.1 Core Sealed Class

```kotlin
// core/model/.../AppError.kt

package io.rikka.agent.model

/**
 * Unified error type for the entire application.
 *
 * Every error that reaches the UI layer must be an [AppError].
 * Lower layers (SSH, storage) produce [AppError] directly or
 * convert domain exceptions into it.
 *
 * @property messageKey  String resource key for the user-facing message.
 *                        UI resolves this via stringResource / getString.
 * @property isRetryable Whether the operation can be meaningfully retried.
 * @property cause       Optional underlying exception for logging.
 */
sealed class AppError(
    val messageKey: String,
    val isRetryable: Boolean = false,
    val cause: Throwable? = null,
) {
    // ── SSH / Connection ──────────────────────────────────────────────

    /** TCP connection refused by the remote host. */
    data class ConnectionRefused(
        val host: String,
        val port: Int,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_connection_refused",
        isRetryable = true,
        cause = cause,
    )

    /** TCP connect or SSH handshake timed out. */
    data class Timeout(
        val host: String,
        val port: Int,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_timeout",
        isRetryable = true,
        cause = cause,
    )

    /** DNS resolution failed. */
    data class UnknownHost(
        val host: String,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_unknown_host",
        isRetryable = false,
        cause = cause,
    )

    /** Password or public-key authentication rejected. */
    data class AuthFailed(
        val username: String,
        val authType: String,   // "password" | "publickey"
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_auth_failed",
        isRetryable = false,
        cause = cause,
    )

    /** Host key verification failed or was rejected by the user. */
    data class HostKeyRejected(
        val host: String,
        val fingerprint: String,
        val reason: HostKeyRejectionReason,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_host_key_rejected",
        isRetryable = false,
        cause = cause,
    )

    /** SSH command exited with a non-zero code. */
    data class CommandFailed(
        val exitCode: Int,
        val stderr: String? = null,
    ) : AppError(
        messageKey = "err_command_failed",
        isRetryable = false,
    )

    /** Catch-all for SSH errors not covered above. */
    data class SshGeneric(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_ssh_generic",
        isRetryable = false,
        cause = cause,
    )

    // ── Network (non-SSH) ─────────────────────────────────────────────

    /** Generic network error (HTTP, WebSocket, etc.). */
    data class NetworkError(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_network",
        isRetryable = true,
        cause = cause,
    )

    // ── Storage ───────────────────────────────────────────────────────

    /** Database or file-system operation failed. */
    data class StorageError(
        val operation: String,  // "read" | "write" | "delete" | "migrate"
        override val cause: Throwable? = null,
    ) : AppError(
        messageKey = "err_storage",
        isRetryable = false,
        cause = cause,
    )

    // ── Validation ────────────────────────────────────────────────────

    /** User input failed validation. */
    data class ValidationError(
        val field: String,
        val reason: String,
    ) : AppError(
        messageKey = "err_validation",
        isRetryable = false,
    )

    // ── Profile ───────────────────────────────────────────────────────

    /** Requested SSH profile was not found in the store. */
    data class ProfileNotFound(
        val profileId: String,
    ) : AppError(
        messageKey = "err_profile_not_found",
        isRetryable = false,
    )
}

/** Reason why a host key was rejected. */
enum class HostKeyRejectionReason {
    /** Stored fingerprint does not match the presented key. */
    Mismatch,
    /** User explicitly rejected the unknown host. */
    UserRejected,
}
```

### 3.2 Why Not a Single `data class Error(val code: ErrorCode, ...)`?

The sealed class approach gives:
- **Exhaustive `when`** -- the compiler warns when a new error type is added but a `when` branch is missing.
- **Typed payloads** -- `AuthFailed` carries `username` and `authType`; `Timeout` carries `host` and `port`. A single data class would need nullable fields or a `details: Map<String, Any>`.
- **Pattern matching** -- `is AppError.Timeout -> ...` is cleaner than `error.code == ErrorCode.TIMEOUT`.

### 3.3 Relationship to Existing Types

| Existing | Replaced By | Migration |
|----------|-------------|-----------|
| `ExecEvent.Error(category: String, message: String)` | `ExecEvent.Error(val error: AppError)` | Phase 1: add `error` field, keep `category`/`message` as deprecated aliases |
| `ConnectionError(category: String, message: String)` | `AppError` directly | Phase 2: `CommandExecutor` emits `AppError` |
| `ErrorMessageMapper` | `AppErrorResolver` | Phase 2: new resolver replaces mapper |
| `ErrorInfo` (UI) | Keep as-is, add `AppError.toErrorInfo()` | Phase 1: extension function |
| `DataError` (proposed in design-data-layer.md) | Subset of `AppError` | Align `DataError` fields into `AppError` |

## 4. Error Resolution (i18n)

### 4.1 AppErrorResolver

Replaces `ErrorMessageMapper`. Resolves an `AppError` into a user-facing localized string using Android string resources.

```kotlin
// app/vm/.../AppErrorResolver.kt

package io.rikka.agent.vm

import android.content.Context
import io.rikka.agent.R
import io.rikka.agent.model.AppError

/**
 * Resolves an [AppError] into a user-facing, localized message string.
 *
 * This is the single point where error types meet string resources.
 * All UI code should use this resolver instead of accessing string
 * resources directly.
 */
object AppErrorResolver {

    /**
     * Resolve the error to a display message.
     *
     * @param context  Android context for string resource access.
     * @param error    The error to resolve.
     * @return Localized, user-facing error message.
     */
    fun resolve(context: Context, error: AppError): String = when (error) {
        is AppError.ConnectionRefused ->
            context.getString(R.string.err_connection_refused)
        is AppError.Timeout ->
            context.getString(R.string.err_timeout)
        is AppError.UnknownHost ->
            context.getString(R.string.err_unknown_host)
        is AppError.AuthFailed ->
            context.getString(R.string.err_auth_failed)
        is AppError.HostKeyRejected -> when (error.reason) {
            HostKeyRejectionReason.Mismatch ->
                context.getString(R.string.err_host_key_mismatch)
            HostKeyRejectionReason.UserRejected ->
                context.getString(R.string.err_host_key_rejected)
        }
        is AppError.CommandFailed ->
            context.getString(R.string.err_command_failed, error.exitCode)
        is AppError.SshGeneric ->
            context.getString(R.string.err_ssh_generic, error.detail)
        is AppError.NetworkError ->
            context.getString(R.string.err_network, error.detail)
        is AppError.StorageError ->
            context.getString(R.string.err_storage, error.operation)
        is AppError.ValidationError ->
            context.getString(R.string.err_validation, error.field, error.reason)
        is AppError.ProfileNotFound ->
            context.getString(R.string.err_profile_not_found, error.profileId)
    }

    /**
     * Resolve a short title for the error (used in ErrorCard header).
     */
    fun resolveTitle(context: Context, error: AppError): String = when (error) {
        is AppError.ConnectionRefused ->
            context.getString(R.string.err_banner_connection_refused)
        is AppError.Timeout ->
            context.getString(R.string.err_banner_timeout)
        is AppError.UnknownHost ->
            context.getString(R.string.err_banner_unknown_host)
        is AppError.AuthFailed ->
            context.getString(R.string.err_banner_auth_failed)
        is AppError.HostKeyRejected ->
            context.getString(R.string.err_banner_key_mismatch)
        is AppError.CommandFailed ->
            context.getString(R.string.err_banner_command_failed)
        is AppError.SshGeneric,
        is AppError.NetworkError,
        is AppError.StorageError,
        is AppError.ValidationError,
        is AppError.ProfileNotFound ->
            context.getString(R.string.err_banner_generic)
    }
}
```

### 4.2 Recovery Suggestions

Each error type implies a recovery action. These are not stored in the error itself (to avoid coupling domain errors to UI concerns) but are resolved at the UI layer:

```kotlin
// app/vm/.../AppErrorRecovery.kt

package io.rikka.agent.vm

import android.content.Context
import io.rikka.agent.R
import io.rikka.agent.model.AppError
import io.rikka.agent.model.HostKeyRejectionReason

/**
 * Provides recovery action suggestions for [AppError] types.
 *
 * Returns a pair of (action label string resource, action description string resource),
 * or null if no recovery action is applicable.
 */
object AppErrorRecovery {

    data class RecoveryHint(
        val labelRes: Int,
        val descriptionRes: Int,
    )

    fun suggest(error: AppError): RecoveryHint? = when (error) {
        is AppError.ConnectionRefused -> RecoveryHint(
            R.string.action_retry,
            R.string.hint_retry_connection,
        )
        is AppError.Timeout -> RecoveryHint(
            R.string.action_retry,
            R.string.hint_retry_connection,
        )
        is AppError.AuthFailed -> RecoveryHint(
            R.string.action_check_settings,
            R.string.hint_check_credentials,
        )
        is AppError.HostKeyRejected -> when (error.reason) {
            HostKeyRejectionReason.Mismatch -> RecoveryHint(
                R.string.action_check_settings,
                R.string.hint_host_key_changed,
            )
            HostKeyRejectionReason.UserRejected -> null
        }
        is AppError.ProfileNotFound -> RecoveryHint(
            R.string.action_check_settings,
            R.string.hint_profile_not_found,
        )
        is AppError.ValidationError -> RecoveryHint(
            R.string.action_check_settings,
            R.string.hint_fix_validation,
        )
        // No actionable recovery for these:
        is AppError.UnknownHost,
        is AppError.CommandFailed,
        is AppError.SshGeneric,
        is AppError.NetworkError,
        is AppError.StorageError -> null
    }
}
```

## 5. Error Display

### 5.1 AppError to ErrorInfo Conversion

The existing `ErrorCard` / `ErrorInfo` UI components are kept as-is. A conversion extension bridges the domain error to the UI model:

```kotlin
// core/ui/.../AppErrorExt.kt

package io.rikka.agent.ui.components

import android.content.Context
import io.rikka.agent.model.AppError
import io.rikka.agent.vm.AppErrorRecovery
import io.rikka.agent.vm.AppErrorResolver
import java.util.UUID

/**
 * Convert an [AppError] to an [ErrorInfo] suitable for [ErrorCard] display.
 *
 * @param context  Android context for string resolution.
 * @param onRetry  Callback for retry action (only shown if error is retryable).
 */
fun AppError.toErrorInfo(
    context: Context,
    onRetry: (() -> Unit)? = null,
): ErrorInfo {
    val message = AppErrorResolver.resolve(context, this)
    val title = AppErrorResolver.resolveTitle(context, this)
    val recovery = AppErrorRecovery.suggest(this)

    val actionLabel = recovery?.let { context.getString(it.labelRes) }
    val onAction = when {
        isRetryable && onRetry != null -> onRetry
        recovery != null -> null  // hint-only, no auto-action
        else -> null
    }

    return ErrorInfo(
        id = "err_${UUID.randomUUID()}",
        title = title,
        message = message,
        actionLabel = if (isRetryable) actionLabel else null,
        onAction = onAction,
    )
}
```

### 5.2 Enhanced ErrorCard with Retry Button

The existing `ErrorCard` composable gains an explicit retry button when the error is retryable. This is a backward-compatible addition -- the `ErrorInfo` data class is extended with a `retryable` flag:

```kotlin
// Proposed addition to ErrorInfo
data class ErrorInfo(
    val id: String,
    val title: String? = null,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val isRetryable: Boolean = false,       // NEW
    val onRetry: (() -> Unit)? = null,      // NEW
)
```

The `ErrorCard` composable adds a retry button (Material3 `FilledTonalButton`) in the action row when `isRetryable && onRetry != null`. No changes to the existing dismiss/copy buttons.

### 5.3 Copy-Friendly Error Format

When the user taps the copy button, the clipboard content includes structured information for bug reports:

```
[Error] Connection Refused
Host: 192.168.1.100:22
Category: ConnectionRefused
Retryable: yes
```

This is produced by a new `AppError.toClipboardText()` extension:

```kotlin
fun AppError.toClipboardText(): String = buildString {
    append("[Error] ")
    appendLine(this@toClipboardText::class.simpleName)
    when (val e = this@toClipboardText) {
        is AppError.ConnectionRefused -> {
            append("Host: "); appendLine("${e.host}:${e.port}")
        }
        is AppError.Timeout -> {
            append("Host: "); appendLine("${e.host}:${e.port}")
        }
        is AppError.AuthFailed -> {
            append("User: "); appendLine(e.username)
            append("Auth: "); appendLine(e.authType)
        }
        is AppError.HostKeyRejected -> {
            append("Host: "); appendLine(e.host)
            append("Fingerprint: "); appendLine(e.fingerprint)
        }
        is AppError.CommandFailed -> {
            append("Exit code: "); appendLine(e.exitCode)
            e.stderr?.let { append("Stderr: "); appendLine(it.take(200)) }
        }
        else -> { /* generic: no extra fields */ }
    }
    append("Retryable: "); appendLine(if (isRetryable) "yes" else "no")
}
```

## 6. Error Logging

### 6.1 Structured Logger

```kotlin
// core/common/.../ErrorLogger.kt

package io.rikka.agent.core.logging

import android.util.Log
import io.rikka.agent.model.AppError

/**
 * Structured error logger that filters sensitive fields.
 *
 * Logs to Android logcat. In release builds, only WARN and above are emitted.
 * Sensitive fields (passwords, private keys, passphrases) are never logged.
 */
object ErrorLogger {

    private const val TAG = "RikkaAgent"

    /**
     * Log an [AppError] at WARN level.
     *
     * @param error   The error to log.
     * @param context Optional context string (e.g. "CommandExecutor.execute").
     */
    fun log(error: AppError, context: String? = null) {
        val prefix = if (context != null) "[$context] " else ""
        val safeMessage = buildSafeMessage(error)
        Log.w(TAG, "${prefix}${error::class.simpleName}: $safeMessage", error.cause)
    }

    /**
     * Log an error at ERROR level (for critical failures).
     */
    fun logError(error: AppError, context: String? = null) {
        val prefix = if (context != null) "[$context] " else ""
        val safeMessage = buildSafeMessage(error)
        Log.e(TAG, "${prefix}${error::class.simpleName}: $safeMessage", error.cause)
    }

    /**
     * Build a log-safe message that excludes sensitive fields.
     */
    private fun buildSafeMessage(error: AppError): String = when (error) {
        is AppError.ConnectionRefused ->
            "host=${error.host}:${error.port}"
        is AppError.Timeout ->
            "host=${error.host}:${error.port}"
        is AppError.UnknownHost ->
            "host=${error.host}"
        is AppError.AuthFailed ->
            // DO NOT log password or key content
            "user=${error.username}, authType=${error.authType}"
        is AppError.HostKeyRejected ->
            "host=${error.host}, reason=${error.reason}"
        is AppError.CommandFailed ->
            "exitCode=${error.exitCode}, stderrLen=${error.stderr?.length ?: 0}"
        is AppError.SshGeneric ->
            "detail=${error.detail}"
        is AppError.NetworkError ->
            "detail=${error.detail}"
        is AppError.StorageError ->
            "operation=${error.operation}"
        is AppError.ValidationError ->
            "field=${error.field}, reason=${error.reason}"
        is AppError.ProfileNotFound ->
            "profileId=${error.profileId}"
    }
}
```

### 6.2 Logging Rules

| Layer | What to Log | Level | Sensitive? |
|-------|------------|-------|------------|
| `SshjExecRunner` catch block | `AppError` with host/port | WARN | No -- host/port are not secrets |
| `CommandExecutor` error handler | `AppError` + context string | WARN | No |
| `AppError.AuthFailed` | username + authType only | WARN | Password/key never logged |
| `AppError.StorageError` | operation name | ERROR | No |
| `AppError.HostKeyRejected` | host + fingerprint | WARN | Fingerprints are not secrets |

**Never log:**
- Passwords, passphrases, private key content
- Full SSH session output (may contain secrets from remote commands)
- API keys or tokens

## 7. SSH Layer Migration

### 7.1 SshjExecRunner Changes

The `catch` block in `SshjExecRunner.run()` currently maps exceptions to `ExecEvent.Error(category: String, message: String)`. It will be changed to produce typed `AppError` instances:

```kotlin
// Current (SshjExecRunner.kt line 195-209)
} catch (e: Exception) {
    val category = when {
        e is java.net.ConnectException -> "connection_refused"
        e is java.net.SocketTimeoutException -> "timeout"
        e is java.net.UnknownHostException -> "unknown_host"
        e.message?.contains("Auth") == true -> "auth_failed"
        else -> "ssh_error"
    }
    trySend(ExecEvent.Error(category, e.message ?: e.javaClass.simpleName))
}

// Proposed
} catch (e: Exception) {
    val appError = e.toAppError(profile)
    ErrorLogger.log(appError, context = "SshjExecRunner")
    trySend(ExecEvent.Error(appError))
}

// Extension function for exception-to-AppError conversion
// core/ssh/.../ExceptionExt.kt
internal fun Exception.toAppError(profile: SshProfile): AppError = when (this) {
    is java.net.ConnectException ->
        AppError.ConnectionRefused(profile.host, profile.port, this)
    is java.net.SocketTimeoutException ->
        AppError.Timeout(profile.host, profile.port, this)
    is java.net.UnknownHostException ->
        AppError.UnknownHost(profile.host, this)
    is SshHostKeyRejectedException ->
        AppError.HostKeyRejected(
            host = profile.host,
            fingerprint = message ?: "",
            reason = HostKeyRejectionReason.UserRejected,
            cause = this,
        )
    else -> {
        if (message?.contains("Auth", ignoreCase = true) == true) {
            AppError.AuthFailed(
                username = profile.username,
                authType = profile.authType.name.lowercase(),
                cause = this,
            )
        } else {
            AppError.SshGeneric(
                detail = message ?: javaClass.simpleName,
                cause = this,
            )
        }
    }
}
```

### 7.2 ExecEvent.Error Backward Compatibility

During the migration period, `ExecEvent.Error` carries both the new typed `AppError` and the old string fields:

```kotlin
@Serializable
data class Error(
    val error: AppError,                    // NEW -- typed error
    val category: String = error.toCategory(), // DEPRECATED -- backward compat
    val message: String = "",               // DEPRECATED -- backward compat
) : ExecEvent()
```

A `AppError.toCategory()` extension provides the string mapping for any code still reading `category`:

```kotlin
fun AppError.toCategory(): String = when (this) {
    is AppError.ConnectionRefused -> "connection_refused"
    is AppError.Timeout -> "timeout"
    is AppError.UnknownHost -> "unknown_host"
    is AppError.AuthFailed -> "auth_failed"
    is AppError.HostKeyRejected -> "host_key_rejected"
    is AppError.CommandFailed -> "command_failed"
    is AppError.SshGeneric -> "ssh_error"
    is AppError.NetworkError -> "network_error"
    is AppError.StorageError -> "storage_error"
    is AppError.ValidationError -> "validation_error"
    is AppError.ProfileNotFound -> "profile_not_found"
}
```

## 8. CommandExecutor Changes

### 8.1 Current Flow

```
ExecEvent.Error(category: String)
    -> ErrorMessageMapper.friendlyErrorMessage(category, ...)
        -> ConnectionError(category, message)
            -> _lastConnectionError
            -> updateMessage(assistantId, errorContent, MessageStatus.Error)
```

### 8.2 Proposed Flow

```
ExecEvent.Error(error: AppError)
    -> ErrorLogger.log(error, "CommandExecutor")
    -> AppErrorResolver.resolve(context, error)     // message
    -> AppErrorResolver.resolveTitle(context, error) // title
    -> error.toErrorInfo(context, onRetry = { ... }) // ErrorInfo for UI
    -> _lastConnectionError = error                   // AppError directly
    -> updateMessage(assistantId, errorContent, MessageStatus.Error)
```

### 8.3 ConnectionError Replacement

`ConnectionError` is replaced by `AppError` directly:

```kotlin
// Current
data class ConnectionError(val category: String, val message: String)

// Proposed: CommandExecutor holds AppError?
private val _lastError = MutableStateFlow<AppError?>(null)
val lastError: StateFlow<AppError?> = _lastError
```

The `dismissConnectionError()` method becomes `dismissError()`.

## 9. New String Resources

Add to `values/strings.xml` and `values-zh/strings.xml`:

```xml
<!-- New error types -->
<string name="err_host_key_rejected">Host key rejected — the server\'s identity could not be verified.</string>
<string name="err_command_failed">Command exited with code %d.</string>
<string name="err_network">Network error: %s</string>
<string name="err_storage">Storage operation failed: %s</string>
<string name="err_validation">Invalid %1$s: %2$s</string>
<string name="err_profile_not_found">Profile not found: %s</string>

<!-- Banner titles -->
<string name="err_banner_command_failed">Command Failed</string>

<!-- Recovery hints -->
<string name="action_retry">Retry</string>
<string name="action_check_settings">Check Settings</string>
<string name="hint_retry_connection">Try connecting again.</string>
<string name="hint_check_credentials">Verify your username, password, or key file in profile settings.</string>
<string name="hint_host_key_changed">The server\'s host key has changed since your last connection. This could indicate a security issue.</string>
<string name="hint_profile_not_found">The selected SSH profile may have been deleted.</string>
<string name="hint_fix_validation">Please correct the highlighted field and try again.</string>
```

Chinese translations:

```xml
<string name="err_host_key_rejected">主机密钥被拒绝 — 无法验证服务器身份。</string>
<string name="err_command_failed">命令退出码：%d</string>
<string name="err_network">网络错误：%s</string>
<string name="err_storage">存储操作失败：%s</string>
<string name="err_validation">%1$s 无效：%2$s</string>
<string name="err_profile_not_found">配置未找到：%s</string>

<string name="err_banner_command_failed">命令执行失败</string>

<string name="action_retry">重试</string>
<string name="action_check_settings">检查设置</string>
<string name="hint_retry_connection">请重新尝试连接。</string>
<string name="hint_check_credentials">请在配置中检查用户名、密码或密钥文件。</string>
<string name="hint_host_key_changed">服务器主机密钥与上次连接时不同，可能存在安全风险。</string>
<string name="hint_profile_not_found">所选 SSH 配置可能已被删除。</string>
<string name="hint_fix_validation">请修正标出的字段后重试。</string>
```

## 10. File Inventory

| File | Action | Description |
|------|--------|-------------|
| `core/model/.../AppError.kt` | **CREATE** | Sealed error hierarchy + `HostKeyRejectionReason` |
| `core/model/.../AppErrorExt.kt` | **CREATE** | `toCategory()`, `toClipboardText()` extensions |
| `core/ssh/.../ExceptionExt.kt` | **CREATE** | `Exception.toAppError()` conversion |
| `core/ssh/.../SshInterfaces.kt` | **MODIFY** | `ExecEvent.Error` gains `error: AppError` field |
| `core/ssh/.../SshjExecRunner.kt` | **MODIFY** | Use `ExceptionExt` in catch blocks |
| `core/common/.../ErrorLogger.kt` | **CREATE** | Structured logger with sensitive-field filtering |
| `app/vm/.../AppErrorResolver.kt` | **CREATE** | Replaces `ErrorMessageMapper` |
| `app/vm/.../AppErrorRecovery.kt` | **CREATE** | Recovery hint provider |
| `app/vm/.../CommandExecutor.kt` | **MODIFY** | Use `AppError` instead of `ConnectionError` |
| `app/vm/.../ErrorMessageMapper.kt` | **DEPRECATE** | Keep for backward compat, mark `@Deprecated` |
| `core/ui/.../AppErrorExt.kt` | **CREATE** | `AppError.toErrorInfo()` conversion |
| `core/ui/.../ErrorCard.kt` | **MODIFY** | Add `isRetryable`/`onRetry` to `ErrorInfo` |
| `app/src/main/res/values/strings.xml` | **MODIFY** | Add new error/recovery strings |
| `app/src/main/res/values-zh/strings.xml` | **MODIFY** | Add Chinese translations |

## 11. Migration Phases

### Phase 1: Foundation (non-breaking)

1. Create `AppError` sealed class in `core/model`.
2. Create `AppErrorResolver` and `AppErrorRecovery` in `app/vm`.
3. Create `ErrorLogger` in `core/common`.
4. Add `AppError.toErrorInfo()` extension in `core/ui`.
5. Add new string resources.
6. Extend `ErrorInfo` with `isRetryable` / `onRetry` fields.
7. Update `ErrorCard` to show retry button.

No existing code is modified. All new code is additive.

### Phase 2: SSH layer integration

1. Create `ExceptionExt.kt` with `Exception.toAppError()`.
2. Modify `ExecEvent.Error` to carry `AppError` (with deprecated string fields as defaults).
3. Update `SshjExecRunner` catch blocks to use `ExceptionExt`.
4. Add logging calls in `SshjExecRunner`.

### Phase 3: VM layer migration

1. Replace `ConnectionError` with `AppError` in `CommandExecutor`.
2. Replace `ErrorMessageMapper` usage with `AppErrorResolver`.
3. Wire `AppError.toErrorInfo()` into the UI callback chain.
4. Add retry callback wiring in `CommandExecutor.execute()`.

### Phase 4: Cleanup

1. Remove deprecated `ConnectionError` class.
2. Remove deprecated `ErrorMessageMapper` class.
3. Remove deprecated string fields from `ExecEvent.Error`.
4. Update all tests to use `AppError` types.

## 12. Testing Strategy

### 12.1 Unit Tests for AppError

```kotlin
class AppErrorTest {
    @Test
    fun `ConnectionRefused is retryable`() {
        val error = AppError.ConnectionRefused("192.168.1.1", 22)
        assertTrue(error.isRetryable)
        assertEquals("connection_refused", error.toCategory())
    }

    @Test
    fun `AuthFailed is not retryable`() {
        val error = AppError.AuthFailed("root", "password")
        assertFalse(error.isRetryable)
    }

    @Test
    fun `toClipboardText does not leak sensitive data`() {
        val error = AppError.AuthFailed("admin", "publickey")
        val text = error.toClipboardText()
        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("key", ignoreCase = true))
        assertTrue(text.contains("admin"))
    }
}
```

### 12.2 Unit Tests for ExceptionExt

```kotlin
class ExceptionExtTest {
    @Test
    fun `ConnectException maps to ConnectionRefused`() {
        val ex = java.net.ConnectException("Connection refused")
        val profile = testProfile(host = "10.0.0.1", port = 22)
        val error = ex.toAppError(profile)
        assertTrue(error is AppError.ConnectionRefused)
        assertEquals("10.0.0.1", (error as AppError.ConnectionRefused).host)
    }

    @Test
    fun `SocketTimeoutException maps to Timeout`() {
        val ex = java.net.SocketTimeoutException("connect timed out")
        val error = ex.toAppError(testProfile())
        assertTrue(error is AppError.Timeout)
    }

    @Test
    fun `auth-related exception maps to AuthFailed`() {
        val ex = Exception("Auth failed")
        val error = ex.toAppError(testProfile(username = "root"))
        assertTrue(error is AppError.AuthFailed)
        assertEquals("root", (error as AppError.AuthFailed).username)
    }
}
```

### 12.3 UI Tests for ErrorCard

```kotlin
@Test
fun `ErrorCard shows retry button for retryable errors`() {
    val error = ErrorInfo(
        id = "test",
        title = "Timeout",
        message = "Connection timed out",
        isRetryable = true,
        onRetry = { /* verify called */ },
    )
    // Use Compose test rule to assert retry button is displayed
}

@Test
fun `ErrorCard hides retry button for non-retryable errors`() {
    val error = ErrorInfo(
        id = "test",
        title = "Auth Failed",
        message = "Authentication failed",
        isRetryable = false,
    )
    // Assert no retry button
}
```

## 13. Design Decisions Log

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Sealed class over enum+map | Typed payloads per variant; exhaustive when; pattern matching |
| D2 | `messageKey: String` over `@StringRes: Int` | Model layer must not depend on Android resources; `core/model` is pure Kotlin |
| D3 | Keep `ErrorInfo` as UI model, don't embed `AppError` in composables | Separation of concerns; UI model is a thin display DTO |
| D4 | `ErrorLogger` in `core/common`, not `core/ssh` | Logging is cross-cutting; VM and storage layers also need it |
| D5 | `isRetryable` on `AppError`, not on `ErrorInfo` | Retryability is a domain property, not a UI decision |
| D6 | Deprecate `ErrorMessageMapper` instead of deleting | Avoids breaking existing callers during migration |
| D7 | `ExceptionExt` as `internal` in `core/ssh` | Conversion logic is SSH-specific; other layers produce `AppError` directly |
