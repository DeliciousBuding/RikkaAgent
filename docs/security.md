# RikkaAgent Security Design Document

> 本文档是 RikkaAgent 的完整安全设计文档，覆盖威胁模型、安全控制、测试清单与合规检查。
> 基于源码审计（2026-06-23），适用于 v0.1.0 版本。

---

## 1. Threat Model

### 1.1 Assets Inventory

| 资产 | 敏感级别 | 存储位置 | 说明 |
|------|----------|----------|------|
| SSH 私钥 | Critical | SAF ContentUri / EncryptedFile (AES-256-GCM) | 用户导入的 OpenSSH/PuTTY 私钥 |
| SSH 密码 | Critical | 仅内存（不持久化） | 通过 `PasswordProvider` 回调实时获取 |
| 私钥 passphrase | Critical | 仅内存（不持久化） | 通过 `PassphraseProvider` 回调实时获取 |
| Codex API Key | High | Room DB (明文) | 存储在 `SshProfileEntity.codexApiKey` |
| SSH Profile 配置 | Medium | Room DB (明文) | host、port、username、authType、keyRef |
| Known Hosts 指纹 | Medium | DataStore (明文) | fingerprint + keyType + addedAtMs |
| 聊天历史 | Medium | Room DB (明文) | 命令文本 + 输出，可能含敏感信息 |
| 用户偏好 | Low | DataStore (明文) | theme、shell、lastProfileId |

### 1.2 Attack Surface Analysis

#### 1.2.1 Network Attack Surface

| 向量 | 描述 | 当前状态 |
|------|------|----------|
| SSH 传输层 | sshj 库处理 SSH 协议，密钥交换与加密由库管理 | 受 sshj 算法协商保护 |
| MITM (首次连接) | 首次连接时无已知主机指纹，可能接受恶意密钥 | TOFU 模式要求用户显式确认 |
| MITM (后续连接) | 主机密钥变更可能指示中间人攻击 | mismatch 时阻断 + 二次确认 |
| 无 HTTP 客户端 | 应用不发起 HTTP 请求，不存在 SSRF 向量 | 通过隐私审计确认 |
| AcceptAll 策略 | 用户可选择禁用主机密钥验证 | 存在，但有文档警告 |

#### 1.2.2 Storage Attack Surface

| 向量 | 描述 | 当前状态 |
|------|------|----------|
| 私钥文件泄露 | Root 设备可读取 EncryptedFile 底层数据 | Android Keystore 硬件绑定提供保护 |
| Room DB 明文 | SSH Profile、聊天历史、Codex API Key 以明文存储 | 无 SQLCipher 加密 |
| DataStore 明文 | Known hosts 指纹、偏好设置以明文存储 | 无额外加密 |
| 旧版未加密密钥 | `EncryptedInternalKeyStore.read()` 有明文回退路径 | 存在向后兼容风险 |
| 备份泄露 | Android Auto Backup 可能包含数据库 | 需确认 `android:allowBackup` 配置 |

#### 1.2.3 UI Attack Surface

| 向量 | 描述 | 当前状态 |
|------|------|----------|
| 主机密钥确认疲劳 | 用户可能盲目点击"信任" | 显示 SHA256 指纹，但无强制阅读机制 |
| 导出数据泄露 | `SessionExporter` 导出原始命令和输出 | 无自动脱敏，用户需自行判断 |
| 剪贴板泄露 | 命令输出复制到系统剪贴板 | Android 剪贴板对所有应用可见 |
| 截图泄露 | 聊天界面可能包含敏感信息 | 无 FLAG_SECURE 保护 |

#### 1.2.4 Supply Chain Attack Surface

| 向量 | 描述 | 当前状态 |
|------|------|----------|
| sshj 依赖 | SSH 协议实现 | 成熟开源库，定期更新 |
| Room 依赖 | 数据库 ORM | AndroidX 官方库 |
| EncryptedFile 依赖 | 加密存储 | AndroidX Security Crypto |
| 无第三方 SDK | 无广告/分析/崩溃上报 SDK | 通过隐私审计确认 |
| 依赖许可证 | 全部兼容 Apache-2.0 | 无 GPL/AGPL 依赖 |

### 1.3 Threat Actors

| Actor | 能力 | 动机 | 目标资产 |
|-------|------|------|----------|
| **本地攻击者 (恶意应用)** | 同设备其他应用，无 root | 窃取 SSH 凭证访问远程服务器 | 私钥、密码、API Key |
| **本地攻击者 (root)** | 设备 root 权限 | 完整凭证窃取 | 所有存储资产 |
| **中间人攻击者** | 网络层控制 (同 Wi-Fi、DNS 劫持) | 窃取 SSH 会话、注入恶意响应 | SSH 连接完整性 |
| **恶意服务器** | 用户连接的远程服务器被入侵 | 获取客户端凭证、诱导执行恶意命令 | 客户端信任关系 |
| **物理攻击者** | 设备物理访问 | 读取未锁屏设备数据 | 聊天历史、已连接会话 |

### 1.4 Risk Matrix

| 威胁 | 概率 | 影响 | 风险级别 | 缓解状态 |
|------|------|------|----------|----------|
| MITM 首次连接 | Medium | Critical | **High** | 已缓解 (TOFU + 用户确认) |
| 私钥明文回退读取 | Low | Critical | **High** | 存在风险 (向后兼容路径) |
| Codex API Key 明文存储 | Medium | High | **High** | 未缓解 |
| Room DB 明文 (root) | Low | High | **Medium** | 部分缓解 (Android Keystore) |
| 导出数据泄露 | Medium | Medium | **Medium** | 已缓解 (用户主动触发) |
| AcceptAll 策略滥用 | Low | Critical | **Medium** | 已缓解 (文档警告) |
| 剪贴板泄露 | Medium | Medium | **Medium** | 未缓解 |
| 密码 String 不可擦除 | Low | Medium | **Low** | JVM 限制，无法完全解决 |
| 截图泄露 | Low | Low | **Low** | 未缓解 |

---

## 2. Security Controls

### 2.1 Authentication Security

#### 2.1.1 SSH Key Storage

**实现位置**: `ContentUriKeyContentProvider.kt`

RikkaAgent 支持两种私钥存储方式：

**方式 A — SAF ContentUri (外部引用)**

```
用户通过 Android 文件选择器选取私钥文件
  → 应用获得 content:// URI 引用
  → 每次连接时通过 ContentResolver 按需读取
  → 私钥内容不复制到应用私有目录
```

- 优势：应用不持有私钥副本，卸载即清除引用
- 风险：外部 ContentProvider 可能在 URI 持久化后被撤销

**方式 B — EncryptedFile (内部加密存储)**

```
用户粘贴私钥内容
  → ContentUriKeyContentProvider.savePastedKey()
  → 生成 UUID keyId
  → EncryptedInternalKeyStore.write(keyId, content)
  → EncryptedFile (AES-256-GCM-HKDF-4KB) 写入 filesDir/ssh_keys/{keyId}
  → 返回 "internal-key://{keyId}" 作为 keyRef
```

- 加密方案：`EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB`
- 密钥管理：`MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` — Android Keystore 硬件绑定
- 向后兼容：`read()` 方法包含明文文件回退路径（Legacy fallback）

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| KEY-01 | 移除明文回退路径，或在读取后立即迁移为加密存储 | High |
| KEY-02 | 私钥内容读取后尽快从内存清除（使用 `CharArray` 而非 `String`） | Medium |
| KEY-03 | 考虑使用 `BiometricPrompt` 保护私钥访问 | Low |

#### 2.1.2 Password Handling

**实现位置**: `SshjExecRunner.kt` — `authenticate()`

```
PasswordProvider.getPassword(profile)  // suspend, UI 回调
  → 返回 String
  → client.authPassword(username, password)
  → String 对象由 GC 回收（无法主动擦除）
```

**设计决策**: 密码和 passphrase 永不持久化，每次连接时通过 Provider 回调实时获取。

**安全属性**:
- 持久化：无
- 内存生命周期：连接建立期间
- 擦除能力：JVM String 不可变，无法主动擦除残留

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| PWD-01 | Provider 接口改为返回 `CharArray`，使用后填充零 | Medium |
| PWD-02 | 考虑在 `authenticate()` 完成后触发 `System.gc()` 提示回收 | Low |

#### 2.1.3 Host Key Verification

**实现位置**: `SshjExecRunner.kt` — `buildVerifier()`, `KnownHostsStore.kt`

三种策略由 `SshProfile.hostKeyPolicy` 控制：

| 策略 | 行为 | 安全级别 |
|------|------|----------|
| `TrustFirstUse` (默认) | 首次连接显示 SHA256 指纹并要求用户确认；后续连接严格匹配 | High |
| `RejectUnknown` | 仅接受已存储的主机密钥；未知主机直接拒绝 | Highest |
| `AcceptAll` | 接受所有主机密钥，不做验证 | None |

**TrustFirstUse 验证流程**:

```
1. createClient() 预加载 knownHostFingerprint (Dispatchers.IO)
2. buildVerifier() 创建 HostKeyVerifier
3. verify() 回调（同步）:
   ├─ knownHost == null && fingerprint 匹配 → 直接接受
   ├─ knownHost == null → 通过 Channel 桥接到 UI，显示指纹，等待用户确认
   ├─ knownHost.fingerprint == fp → 直接接受
   └─ knownHost.fingerprint != fp → 通过 Channel 桥接到 UI，显示 expected vs actual，等待用户确认
4. 用户接受 → 在协程上下文中写入 KnownHostsStore（非同步回调内）
```

**关键实现细节**:

- `CompletableDeferred<Boolean>` + `Channel<HostKeyDecisionRequest>` 桥接模式解决 sshj 同步回调与异步 UI 的矛盾
- `runBlocking { deferred.await() }` 是唯一阻塞点，纯信号等待，不会死锁
- Host key 指纹使用 `SecurityUtils.getFingerprint(key)` 计算
- 持久化键为 `[$host]:$port` 格式，避免 hostname 规范化 bug

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| HK-01 | AcceptAll 策略应显示持久化警告 banner，而非仅文档说明 | Medium |
| HK-02 | TOFU 首次确认对话框应强制显示指纹（不可跳过） | High |
| HK-03 | mismatch 确认应要求用户输入 "YES" 而非单击按钮 | Low |

### 2.2 Command Security

#### 2.2.1 Shell Command Injection Protection

**实现位置**: `CommandComposer.kt`

RikkaAgent 的命令执行模型是**用户直接输入 shell 命令**，不存在传统 web 应用意义上的命令注入。但仍需防止以下场景：

**shellQuote()**:
```kotlin
fun shellQuote(input: String): String {
  val escaped = input.replace("'", "'\\''")
  return "'$escaped'"
}
```
使用标准单引号转义策略：将 `'` 替换为 `'\''`（结束当前单引号、插入转义单引号、重新开始单引号）。

**wrapWithShell()**:
```kotlin
fun wrapWithShell(command: String, shell: String): String {
  if (shell == "/bin/sh" || shell.isBlank()) return command
  val escaped = command.replace("'", "'\\''")
  return "$shell -c '$escaped'"
}
```
将用户命令作为 `-c` 参数传递，不额外拼接。

**wrapForCodex()**:
```kotlin
fun wrapForCodex(task: String, workDir: String?, apiKey: String?): String {
  val escapedTask = task.replace("\"", "\\\"")
  val cdPart = if (!workDir.isNullOrBlank()) "cd ${shellQuote(workDir)} && " else ""
  val envPart = if (!apiKey.isNullOrBlank()) "OPENAI_API_KEY=${shellQuote(apiKey)} " else ""
  return "${cdPart}${envPart}codex exec --json --full-auto \"$escapedTask\""
}
```

**安全分析**:

| 场景 | 风险 | 状态 |
|------|------|------|
| `workDir` 注入 | 使用 `shellQuote()` 包裹 | 安全 |
| `apiKey` 注入 | 使用 `shellQuote()` 包裹 | 安全 |
| `task` 双引号逃逸 | `"escapedTask"` 在 `"$escapedTask"` 内 | 存在理论风险 (见下) |
| 命令通过 `-c` 执行 | 用户命令在 shell 上下文中执行 | 预期行为 |

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| CMD-01 | `wrapForCodex` 中 `task` 参数考虑使用 `shellQuote()` 而非手动双引号转义 | Medium |
| CMD-02 | 考虑增加危险命令模式匹配（`rm -rf /`、`mkfs`、`dd`），显示额外确认 | Low |
| CMD-03 | `defaultShell` 偏好设置应限制为白名单路径（`/bin/sh`、`/bin/bash`、`/bin/zsh`） | Medium |

#### 2.2.2 Codex API Key Protection

**当前状态**: `SshProfileEntity.codexApiKey` 存储在 Room DB 明文字段中。

**风险**:
- Root 设备可直接读取 `rikka_agent.db` 获取 API Key
- Android Auto Backup 可能包含数据库副本
- `wrapForCodex()` 将 API Key 作为命令行参数传递，`ps aux` 可见

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| API-01 | Codex API Key 应使用 EncryptedFile 或 EncryptedSharedPreferences 存储 | **High** |
| API-02 | `wrapForCodex()` 应通过 `stdin` 或临时环境变量文件传递 API Key，避免命令行暴露 | Medium |
| API-03 | API Key 在 UI 中应默认遮蔽显示（`sk-...****`） | Low |

#### 2.2.3 Environment Variable Injection

`wrapForCodex()` 通过命令行前缀注入环境变量：
```bash
OPENAI_API_KEY='value' codex exec --json --full-auto "task"
```

这是标准 shell 语法，`shellQuote()` 确保值被正确包裹。但 API Key 值会出现在：
- 进程列表 (`/proc/{pid}/cmdline`)
- Shell 历史记录
- 系统日志（如果 shell 启用了 history）

### 2.3 Data Security

#### 2.3.1 Room Database Encryption

**当前状态**: 标准 Room 数据库，无 SQLCipher。

**存储内容**:

| 表 | 敏感字段 | 加密状态 |
|----|----------|----------|
| `ssh_profiles` | `codexApiKey` | 明文 |
| `ssh_profiles` | `host`, `username`, `keyRef` | 明文 |
| `chat_messages` | `content`, `partsJson` | 明文 |
| `chat_threads` | `title` | 明文 |

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| DB-01 | 评估 SQLCipher 集成的性能影响与必要性 | Medium |
| DB-02 | 至少将 `codexApiKey` 从 Room 迁移到 EncryptedFile | **High** |
| DB-03 | 考虑使用 `SupportFactory` (SQLCipher for Room) 全库加密 | Low |
| DB-04 | 确认 `android:allowBackup="false"` 或排除敏感文件 | High |

#### 2.3.2 Log Sanitization

**当前状态**:
- 隐私审计确认 Release 构建不输出 SSH 命令/会话内容到 Logcat
- ProGuard 已配置用于发布构建混淆

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| LOG-01 | 确保 sshj 的日志级别在 Release 中设为 WARN 或更高 | High |
| LOG-02 | 异常消息中不应包含密码、私钥内容或 API Key | High |
| LOG-03 | 考虑使用 Timber 或自定义 Logger 统一脱敏规则 | Medium |

#### 2.3.3 Export Data Security

**实现位置**: `SessionExporter.kt`

```kotlin
fun export(profileLabel: String, messages: List<ChatMessage>): String = buildString {
  appendLine("# Session: $profileLabel")
  for (msg in messages) {
    if (msg.role == ChatRole.User) appendLine("$ ${msg.content}")
    else appendLine(msg.content)
  }
}
```

**风险**: 导出内容为原始命令和输出，可能包含：
- SSH 密码（如果用户在命令中回显）
- API Key（如果命令输出中包含）
- 敏感文件内容

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| EXP-01 | 导出前显示警告对话框："导出内容可能包含敏感信息" | High |
| EXP-02 | 考虑增加可选的自动脱敏模式（匹配常见密钥模式） | Low |
| EXP-03 | 导出文件不应自动上传或同步到云存储 | Medium |

### 2.4 Network Security

#### 2.4.1 SSH Connection Security

**实现位置**: `SshjExecRunner.kt` — `createClient()`

sshj 默认算法协商提供以下安全属性：

| 类别 | 默认行为 | 说明 |
|------|----------|------|
| 密钥交换 | Curve25519, ECDH | 现代 ECDH 密钥交换 |
| 主机密钥 | Ed25519, RSA, ECDSA | 支持多种密钥类型 |
| 对称加密 | AES-256-GCM, ChaCha20-Poly1305 | AEAD 加密 |
| MAC | 依赖加密算法的内置 MAC | AEAD 模式无需额外 MAC |
| 密码认证 | 通过 SSH 传输层加密 | 密码不在明文中传输 |

**连接管理**:
- 连接缓存键：`[$host]:$port:$username`
- Keepalive 间隔：`profile.keepaliveIntervalSec`（默认 60s）
- 连接失效时自动重试一次（evict + reconnect）
- 缓存连接的认证失败不会重试（避免锁定账户）

**安全建议**:

| 编号 | 建议 | 优先级 |
|------|------|--------|
| NET-01 | 考虑限制 sshj 使用的算法集，禁用弱算法（如 SHA-1 MAC、CBC 模式） | Medium |
| NET-02 | 连接超时应有合理默认值（当前依赖 sshj 默认） | Low |
| NET-03 | 考虑在连接失败时清理可能泄露的认证状态 | Low |

#### 2.4.2 Certificate Pinning

**当前状态**: 无外部 HTTP 服务连接，SSH 使用 sshj 内置的主机密钥验证机制，不依赖 TLS 证书。

**评估**: 证书固定不适用于当前架构。主机密钥验证（TOFU + 指纹匹配）提供了等效的服务器身份验证。

---

## 3. Security Testing Checklist

### 3.1 Authentication Testing

| 测试项 | 验证方法 | 预期结果 | 自动化 |
|--------|----------|----------|--------|
| 私钥加密存储 | 检查 `filesDir/ssh_keys/` 文件是否为密文 | 文件内容不可读 | Unit |
| 私钥明文回退 | 写入明文文件后调用 `read()` | 能读取但应触发迁移 | Unit |
| 密码不持久化 | 连接后检查数据库和 DataStore | 无密码字段 | Unit |
| passphrase 不持久化 | 同上 | 无 passphrase 字段 | Unit |
| EncryptedFile 密钥绑定 | 使用不同 Android Keystore 密钥读取 | 抛出异常 | Instrumented |
| SAF ContentUri 访问 | 撤销 URI 权限后尝试读取 | 返回 null | Instrumented |

### 3.2 Host Key Verification Testing

| 测试项 | 验证方法 | 预期结果 | 自动化 |
|--------|----------|----------|--------|
| 首次连接确认 | 模拟未知主机 | 显示指纹，等待用户确认 | Unit |
| 指纹匹配 | 已知主机，密钥匹配 | 直接连接，无确认 | Unit |
| 指纹不匹配 | 已知主机，密钥变更 | 阻断连接，显示警告 | Unit |
| mismatch 二次确认 | 用户点击"替换" | 需要二次确认 | Unit |
| AcceptAll 策略 | 设置 AcceptAll 并连接未知主机 | 直接连接，无确认 | Unit |
| RejectUnknown 策略 | 设置 RejectUnknown 并连接未知主机 | 直接拒绝 | Unit |
| 指纹持久化 | 接受主机密钥后重启应用 | 指纹仍在 KnownHostsStore | Unit |
| 指纹删除 | 删除已知主机 | 后续连接需重新确认 | Unit |

### 3.3 Command Injection Testing

| 测试项 | 验证方法 | 预期结果 | 自动化 |
|--------|----------|----------|--------|
| shellQuote 单引号 | 输入含 `'` 的字符串 | 正确转义，无注入 | Unit |
| shellQuote 空字符串 | 输入 `""` | 返回 `''` | Unit |
| shellQuote 特殊字符 | 输入 `$()、backtick、\n` | 全部在单引号内 | Unit |
| wrapWithShell 命令 | 含特殊字符的命令 | 正确包裹 | Unit |
| wrapForCodex workDir | 含空格和特殊字符的路径 | 正确转义 | Unit |
| wrapForCodex apiKey | 含特殊字符的 API Key | 正确转义 | Unit |
| wrapForCodex task | 含双引号的任务描述 | 正确转义 | Unit |

### 3.4 Data Security Testing

| 测试项 | 验证方法 | 预期结果 | 自动化 |
|--------|----------|----------|--------|
| Room DB 无 API Key 明文 | 导出数据库并搜索 API Key 模式 | 不应以明文出现（待实现） | Manual |
| DataStore 无敏感数据 | 检查 known_hosts DataStore 内容 | 仅含指纹，无凭证 | Unit |
| 导出内容检查 | 导出会话并检查 | 不自动包含私钥/密码 | Unit |
| 日志无敏感信息 | Debug 构建检查 Logcat | 无密码、私钥、API Key | Manual |
| 数据库备份排除 | 检查 AndroidManifest backup 规则 | 敏感文件被排除 | Manual |

### 3.5 Network Security Testing

| 测试项 | 验证方法 | 预期结果 | 自动化 |
|--------|----------|----------|--------|
| 弱算法拒绝 | 配置 sshj 仅允许强算法 | 连接使用强算法 | Integration |
| 连接超时 | 连接不可达主机 | 超时后返回错误 | Unit |
| 连接缓存隔离 | 不同 profile 使用独立连接 | 无连接串扰 | Unit |
| Keepalive 失败 | 连接中断后 keepalive | 检测到断开，状态更新 | Integration |
| 取消命令 | 执行中取消 | 关闭 exec channel，状态正确 | Unit |

---

## 4. Compliance Check

### 4.1 OWASP Mobile Top 10 (2024) Mapping

| OWASP 类别 | 要求 | RikkaAgent 状态 | 严重程度 |
|------------|------|-----------------|----------|
| **M1: Improper Credential Usage** | 不当使用凭证 | 密码/passphrase 不持久化；Codex API Key 明文存储 | **Partial** |
| **M2: Inadequate Supply Chain Security** | 供应链安全 | 无第三方 SDK；依赖均为官方/成熟开源库 | **Pass** |
| **M3: Insecure Authentication/Authorization** | 不安全的认证 | SSH 密钥认证 + TOFU；AcceptAll 策略可用 | **Partial** |
| **M4: Insufficient Input/Output Validation** | 输入输出验证不足 | shellQuote 转义；但导出无脱敏 | **Partial** |
| **M5: Insecure Communication** | 不安全通信 | SSH 加密传输；无 HTTP 明文 | **Pass** |
| **M6: Inadequate Privacy Controls** | 隐私控制不足 | 无遥测；但数据库明文、剪贴板暴露 | **Partial** |
| **M7: Insufficient Binary Protections** | 二进制保护不足 | ProGuard 已配置；无 root 检测 | **Partial** |
| **M8: Security Misconfiguration** | 安全配置错误 | AcceptAll 可用；defaultShell 无白名单 | **Partial** |
| **M9: Insecure Data Storage** | 不安全数据存储 | 私钥加密；但 API Key、DB 明文 | **Partial** |
| **M10: Insufficient Cryptography** | 密码学不足 | AES-256-GCM 用于私钥；但未全库加密 | **Partial** |

### 4.2 Minimum Privilege Principle Check

| 检查项 | 状态 | 说明 |
|--------|------|------|
| Android 权限最小化 | Pass | 仅 `INTERNET` 权限 |
| 存储权限 | Pass | 使用 SAF，无需 `READ/WRITE_EXTERNAL_STORAGE` |
| 无位置/相机/麦克风权限 | Pass | 不申请 |
| SSH exec 最小权限 | Pass | 无 PTY，无端口转发，无文件传输 |
| 密钥访问最小化 | Pass | 通过 Provider 回调按需获取，不全局持有 |
| 连接缓存范围 | Pass | 按 profile 隔离，`close()` 时清除 |

### 4.3 Additional Security Recommendations Summary

**High Priority (应在下一版本修复)**:

1. **API-01**: Codex API Key 从 Room 明文迁移到 EncryptedFile
2. **DB-04**: 确认并配置 `android:allowBackup="false"`
3. **KEY-01**: 移除或迁移 EncryptedInternalKeyStore 的明文回退路径
4. **HK-02**: TOFU 首次确认对话框强制显示完整指纹
5. **LOG-01**: Release 构建中 sshj 日志级别设为 WARN+

**Medium Priority (计划修复)**:

6. **CMD-03**: `defaultShell` 白名单限制
7. **EXP-01**: 导出前敏感数据警告
8. **NET-01**: sshj 算法集限制（禁用弱算法）
9. **API-02**: API Key 避免命令行暴露
10. **DB-01**: 评估 SQLCipher 集成

**Low Priority (长期改进)**:

11. **KEY-03**: BiometricPrompt 保护私钥访问
12. **HK-03**: mismatch 确认增强为输入 "YES"
13. **CMD-02**: 危险命令模式匹配警告
14. **KEY-02**: 内存中使用 CharArray 替代 String

---

## Appendix A: Cryptographic Inventory

| 用途 | 算法/方案 | 实现 |
|------|-----------|------|
| 私钥文件加密 | AES-256-GCM-HKDF-4KB | AndroidX EncryptedFile |
| 加密主密钥 | AES-256-GCM | Android Keystore (硬件绑定) |
| SSH 密钥交换 | Curve25519 / ECDH | sshj 默认 |
| SSH 会话加密 | AES-256-GCM / ChaCha20-Poly1305 | sshj 默认 |
| 主机密钥指纹 | SHA-256 | `SecurityUtils.getFingerprint()` |
| Known Hosts 存储 | 明文 JSON | DataStore Preferences |

## Appendix B: Data Flow Security Diagram

```
User Input
  │
  ▼
CommandComposer.shellQuote() / wrapForCodex()
  │  ← Shell escaping applied
  ▼
SshjExecRunner.run(profile, command)
  │
  ├─ authenticate()
  │   ├─ PasswordProvider.getPassword() → String (memory only)
  │   ├─ PassphraseProvider.getPassphrase() → String (memory only)
  │   └─ KeyContentProvider.getKeyContent()
  │       ├─ SAF ContentUri → ContentResolver.openInputStream()
  │       └─ internal-key:// → EncryptedFile.read() (AES-256-GCM)
  │
  ├─ buildVerifier() → HostKeyVerifier
  │   ├─ TrustFirstUse → CompletableDeferred → UI confirm
  │   └─ StoredHostKey comparison (SHA-256 fingerprint)
  │
  ▼
SSH exec channel (sshj, encrypted transport)
  │
  ▼
Remote Server
  │
  ▼
stdout/stderr bytes → Flow<ExecEvent>
  │
  ├─ JsonlParser (if Codex mode)
  │
  ▼
ChatMessageEntity → Room DB (plaintext)
  │
  ├─ SessionExporter (user-triggered, no auto-redaction)
  │
  ▼
UI Rendering (Compose)
```

## Appendix C: Known Limitations

1. **JVM String Immutability**: 密码和 passphrase 作为 `String` 传递，无法在使用后主动擦除内存残留。这是 JVM 平台的根本限制。
2. **Root Device Threat**: 在 root 设备上，任何应用级加密都可被绕过。EncryptedFile 依赖 Android Keystore 的硬件绑定能力，但 root 环境下 Keystore 保护可能被削弱。
3. **TOFU Inherent Risk**: Trust-on-first-use 模型无法在首次连接时提供服务器身份的密码学保证。用户需自行验证指纹（如通过带外渠道）。
4. **Legacy Plaintext Fallback**: `EncryptedInternalKeyStore.read()` 包含向后兼容的明文读取路径，旧版密钥文件在首次读取后仍以明文形式存在于磁盘上。
5. **Room DB Unencrypted**: 聊天历史和 SSH Profile 配置以明文存储，root 设备或备份泄露可导致数据暴露。
