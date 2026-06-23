# RikkaAgent 产品口径审查报告

> 审查日期：2026-06-23
> 审查范围：README.md, STATE.md, docs/prd.md, docs/architecture.md
> 审查目标：产品定位一致性、功能描述准确性、技术栈准确性、RikkaHub 混淆、误导性描述

---

## 审查结论

**总体评级：基本一致，存在 3 项口径偏差和 2 项安全描述不一致。**

四份文档在核心定位（SSH command executor, not AI chat client）上保持一致，技术栈描述基本准确。主要问题集中在 PRD 对 "Claude Code" 的混用引用、`codexApiKey` 存储方式的描述矛盾、以及架构文档的定位措辞偏差。

---

## 1. 产品定位一致性

### 1.1 各文档定位措辞对比

| 文档 | 定位措辞 | 评估 |
|------|----------|------|
| README.md | "SSH command executor for Android with chat-style output rendering" | 准确 |
| STATE.md | "SSH command executor (not an AI chat client)" | 准确 |
| PRD | "Chat-first SSH Runner" | 偏差 -- 引入新术语 |
| architecture.md | "Android SSH 终端客户端" | 偏差 -- 用词不同 |

### 1.2 问题 A-1：architecture.md 定位措辞偏差

**位置**：`docs/architecture.md` 第 1 行

**现状**：
> RikkaAgent 是一个 Android SSH 终端客户端

**问题**："终端客户端"（terminal client）暗示 PTY/终端模拟能力，与项目明确的 "SSH command executor"（非交互式 exec channel）定位矛盾。README 和 STATE 反复强调 "not an AI chat client"，architecture.md 也应保持一致的 "executor" 口径。

**建议修改**：
> RikkaAgent 是一个 Android SSH 命令执行器（command executor），通过 SSH exec 通道连接远程服务器执行命令，并以聊天消息形式呈现输出。

### 1.3 问题 A-2：PRD 定位术语不统一

**位置**：`docs/prd.md` 第 22 行

**现状**：
> RikkaAgent 是一款 **Chat-first SSH Runner**

**问题**："SSH Runner" 是新造术语，在 README、STATE、architecture 中均未出现。虽然语义不冲突，但引入额外术语增加认知成本。README 使用 "SSH command executor"，STATE 使用相同措辞，PRD 应保持一致。

**建议**：统一为 "SSH command executor with chat-style UI" 或类似表述，避免引入 "Runner" 这一未在其他文档定义的术语。

### 1.4 问题 A-3：PRD v2.0 定位漂移

**位置**：`docs/prd.md` 第 479-480 行

**现状**：
> v2.0 -- 多 Agent Runner + PTY（平台进化）
> **目标**：从 SSH 客户端进化为远程 AI Agent 平台。

**问题**："远程 AI Agent 平台" 是重大定位跳跃。当前产品定位是 SSH command executor，v2.0 直接跃迁为 "AI Agent 平台" 缺乏过渡描述。这与 README FAQ Q5 的定位声明（"RikkaAgent is a SSH command executor"）存在张力。

**建议**：在 v2.0 描述中增加定位过渡说明，明确 "AI Agent 平台" 仍以 SSH exec 为基础，不是独立的 AI 产品。

---

## 2. 功能描述准确性

### 2.1 问题 F-1：PRD 混用 "Codex" 与 "Claude Code"

**位置**：`docs/prd.md` 第 31、246、395、415、485 行

**现状**：PRD 多处将 "Codex" 和 "Claude Code" 并列使用：
- 第 31 行：用户画像提到 "通过 Codex/Claude Code 执行 AI 辅助编码任务"
- 第 246 行：功能节标题 "3.5 Codex / Claude Code 远程执行"
- 第 395 行：竞品矩阵 "Codex / Claude Code（一等公民）"
- 第 415 行："RikkaAgent 是唯一将 Codex/Claude Code 集成为一等公民的 Android SSH 客户端"
- 第 485 行：v2.0 规划 "支持 Claude Code、Aider 等更多 AI CLI 工具"

**问题**：
1. 当前实现仅集成 Codex CLI（`codex exec --json --full-auto`），不包含 Claude Code。
2. STATE.md 仅提到 "Codex integration"，README 也只提到 "Codex bridge" 和 "Codex mode"。
3. "Claude Code" 出现在 P2 需求（F-AI-08）中，属于未来规划，不是当前能力。
4. 竞品矩阵中将 "Codex/Claude Code" 标记为 "一等公民" 是误导性描述 -- Claude Code 尚未集成。

**影响**：读者会误以为 RikkaAgent 已经同时集成了 Codex 和 Claude Code。

**建议**：
- 当前能力描述统一为 "Codex"（不带 "Claude Code"）
- 未来规划明确标注 "（规划中）" 或 "（P2）"
- 竞品矩阵中改为 "Codex（一等公民）"

### 2.2 问题 F-2：PRD 错误引用 JSch 异常

**位置**：`docs/prd.md` 第 112 行

**现状**：
> SSH 连接错误映射为用户友好的中文描述（如"连接超时"而非"com.jcraft.jsch.JSchException: timeout"）

**问题**：项目使用 sshj 库（BSD-2-Clause），不使用 JSch。错误示例引用了错误的库名。STATE.md 明确记录 "SSH library: sshj 0.39.0"。

**建议**：将错误示例改为 sshj 的异常格式，如 `net.schmizz.sshj.userauth.UserAuthException`，或使用通用描述。

### 2.3 问题 F-3：PRD 性能指标缺乏依据

**位置**：`docs/prd.md` 第 322-329 行

**现状**：PRD 列出 7 项性能目标（60fps、<3s 连接、<2s 冷启动等），但：
- README 未提及任何性能指标
- STATE.md 未记录性能基准
- 未见性能测试代码或 CI 集成

**问题**：这些指标是否经过测量验证？如果是目标值，应标注为 "目标" 而非暗示已达成。

**建议**：明确标注为 "性能目标（待验证）" 或提供测量数据来源。

---

## 3. 技术栈描述准确性

### 3.1 各文档技术栈对比

| 技术 | README | STATE | PRD | architecture | 一致性 |
|------|--------|-------|-----|--------------|--------|
| Kotlin 2.1.0 | Yes | - | Yes | - | 一致 |
| Jetpack Compose | Yes | Yes | Yes | Yes | 一致 |
| Room v5 | Yes | Yes | Yes | Yes | 一致 |
| sshj | Yes | Yes (0.39.0) | - | Yes | 一致 |
| IntelliJ MarkdownParser | Yes | Yes (0.7.3) | - | - | 一致 |
| Material Expressive Theme | Yes | Yes | Yes | - | 一致 |
| Koin | - | Yes | - | Yes | 一致 |
| Lucide Icons | Yes | Yes (1.1.0) | - | - | 一致 |
| AndroidX Security Crypto | Yes | Yes | Yes | Yes | 一致 |
| API level | 24+ | - | 24+ | - | 一致 |
| Target API | 35 | - | 35 | - | 一致 |

**评估**：技术栈描述在四份文档间基本一致，无重大偏差。PRD 的技术栈表（第 19 行）缺少 sshj 和 IntelliJ MarkdownParser，但这属于粒度差异，不构成错误。

---

## 4. RikkaHub 混淆引用

### 4.1 各文档 RikkaHub 引用统计

| 文档 | 引用次数 | 上下文 | 评估 |
|------|----------|--------|------|
| README.md | 2 | 第 14 行：UX inspired by；FAQ Q5：区别说明 | 合理，明确声明独立 |
| STATE.md | 1 | 第 19 行：UX inspiration only | 合理 |
| PRD | 3 | 第 435 行：UI 对齐目标；第 466 行：体验增强目标 | 问题 |
| architecture.md | 0 | - | 无问题 |

### 4.2 问题 R-1：PRD 将 RikkaHub 作为 UI 质量对齐目标

**位置**：`docs/prd.md` 第 435 行和第 466 行

**现状**：
- 第 435 行：`UI 复刻 RikkaHub 工作量大 | 分阶段对齐，v1.0 先保证功能可用，v1.1 再提升视觉质量`
- 第 466 行：`v1.1 -- Codex 集成 + Mermaid（体验增强）... UI 质量对齐 RikkaHub 水准`

**问题**：
1. "复刻 RikkaHub" 措辞不当 -- README 明确声明 "clean-room from scratch"，"复刻" 暗示代码复用，与 clean-room 声明矛盾。
2. 将外部项目的 UI 水准作为内部质量目标，缺乏可衡量性。RikkaHub 本身在迭代，目标会漂移。
3. README FAQ Q5 已明确两者是 "independent projects with different purposes"，PRD 不应建立 UI 对齐依赖。

**建议**：
- 删除 "复刻 RikkaHub" 表述，改为 "提升视觉质量"
- 将 UI 质量目标具体化（如 Compose 组件覆盖率、主题一致性检查），不依赖外部项目

---

## 5. 安全描述一致性

### 5.1 问题 S-1：codexApiKey 存储方式描述矛盾（严重）

**各文档描述对比**：

| 文档 | 描述 |
|------|------|
| STATE.md 第 27 行 | "Key storage: EncryptedFile (AES-256-GCM via AndroidX Security Crypto)" |
| PRD F-SEC-05 | "Codex API Key 等敏感配置项加密存储，不以明文写入数据库" |
| architecture.md 第 356 行 | `SshProfile` 数据类中 `codexApiKey: String? = null`，存储在 Room DB |
| architecture.md 第 414 行 | 安全架构表未列出 codexApiKey 的存储方式 |
| security.md | "SshProfileEntity.codexApiKey 存储在 Room DB 明文字段中" |
| ROADMAP.md | "encrypt codexApiKey at rest" 列为待办 |
| analysis-report.md | "安全隐患: codexApiKey 明文存储" |

**问题**：
1. STATE.md 的 "Key storage: EncryptedFile" 仅适用于 SSH 私钥，不适用于 codexApiKey，但表述容易让人误以为所有密钥都已加密。
2. PRD F-SEC-05 声称 "Codex API Key 加密存储，不以明文写入数据库"，但实际实现中 `codexApiKey` 存储在 Room DB 明文字段中。
3. 这是一个已知的技术债务（ROADMAP.md 已列入），但 PRD 将其列为 P0 需求，暗示已实现。

**影响**：安全审计时会产生误导。PRD 的 P0 验收标准与实际实现不符。

**建议**：
- PRD F-SEC-05 标注当前状态："已知问题 -- codexApiKey 明文存储，修复排期见 ROADMAP"
- STATE.md 的 "Key storage" 条目拆分为 SSH 私钥和 Codex API Key 两项，分别说明当前状态

### 5.2 问题 S-2：PRD F-SEC-04 描述范围过大

**位置**：`docs/prd.md` 第 236 行

**现状**：
> F-SEC-04 | 密钥加密存储 | 应用管理的私钥通过 AndroidX Security Crypto 加密存储，不在文件系统中保留明文

**问题**：architecture.md 第 413 行明确指出 SSH 私钥通过 SAF ContentUri 引用，由 Android 系统管理，应用不复制私钥到自己的目录。"应用管理的私钥" 这一措辞暗示应用持有私钥副本，与 SAF 架构不符。

**建议**：改为 "SSH 私钥通过 Android SAF ContentUri 引用，不复制到应用目录；应用管理的 passphrase 通过 AndroidX Security Crypto 加密存储"。

---

## 6. 其他观察

### 6.1 P0/P1 优先级与实现状态的对应

PRD 将 Codex 相关功能列为 P1（v1.1），但 README Capability Matrix 显示 Codex integration 已 Done，STATE.md 也确认 Codex 模块为 Stable。PRD 的版本规划与实际开发节奏存在脱节。

**建议**：在 PRD 中增加实现状态列，或注明优先级反映的是原始规划而非当前状态。

### 6.2 术语表覆盖度

PRD 附录 A 术语表覆盖了核心术语，但缺少 "Chat-first SSH Runner"（PRD 自创术语）和 "Mode A"（README/STATE 使用的术语）的定义。

---

## 修复优先级

| 编号 | 问题 | 严重度 | 建议处理 |
|------|------|--------|----------|
| S-1 | codexApiKey 存储描述矛盾 | High | 更新 PRD/STATE，标注已知债务 |
| F-1 | Claude Code 混用引用 | High | PRD 中当前能力描述删除 "Claude Code" |
| A-1 | architecture.md 定位措辞 | Medium | 改为 "command executor" |
| R-1 | RikkaHub 复刻措辞 | Medium | 改为具体 UI 质量目标 |
| F-2 | JSch 错误示例 | Low | 改为 sshj 异常 |
| A-2 | PRD Runner 术语 | Low | 统一为 executor |
| A-3 | v2.0 定位漂移 | Low | 增加过渡说明 |
| F-3 | 性能指标无依据 | Low | 标注为待验证目标 |
| S-2 | F-SEC-04 范围过大 | Low | 改为 SAF + EncryptedFile 分述 |
