# RikkaAgent -- Product Requirements Document

> 版本：2.0 | 日期：2026-06-23 | 状态：Draft | 作者：Product Team

---

## 1. 产品概述

### 1.1 基本信息

| 项 | 值 |
|---|---|
| 产品名称 | RikkaAgent |
| 版本 | v2.0 PRD（对应重构周期） |
| 平台 | Android（API 24+） |
| 开源协议 | Apache-2.0 |
| 仓库 | github.com/DeliciousBuding/RikkaAgent |
| 技术栈 | Kotlin 2.1.0 / Jetpack Compose / Room v5 / sshj / Material Expressive Theme |

### 1.2 产品定位

RikkaAgent 是一款 **SSH 命令执行器（command executor）**，面向 Android 平台。它将 SSH 命令执行的输入输出以聊天消息的形式呈现，让用户"像读对话一样读命令输出"，而非面对传统终端的滚动文本墙。

**一句话核心价值主张**：把远程运维的交互范式从终端模拟器转移到对话界面，让每一次 SSH 执行都像聊天一样可读、可搜索、可分享。

### 1.3 目标用户画像

| 用户画像 | 年龄段 | 技术水平 | 典型设备 | 核心诉求 |
|----------|--------|----------|----------|----------|
| **SSH 运维人员** | 25-40 | 高 | 手机（紧急场景） | 在没有电脑的情况下快速执行远程诊断命令，阅读结构化输出，一键复制关键信息 |
| **远程开发者** | 22-35 | 高 | 手机/平板 | 触发远程构建、查看日志、通过 Codex/Claude Code 执行 AI 辅助编码任务 |
| **AI 工具用户** | 20-45 | 中高 | 手机/平板 | 在远程服务器上与 AI 交互，实时查看推理过程和执行结果，无需手动操作 CLI |
| **技术爱好者** | 18-35 | 中 | 手机 | 管理家庭服务器/NAS/树莓派，需要轻量但可读性高的 SSH 客户端 |
| **Linux 初学者** | 16-25 | 低-中 | 手机/平板 | 学习 Linux 命令行，聊天式界面降低终端的心理门槛，语法高亮辅助理解 |

### 1.4 使用场景

- **场景 A -- 紧急故障排查**：手机收到告警推送，打开 RikkaAgent 选择服务器 Profile，执行 `docker ps` / `journalctl -u xxx --since "5 min ago"`，以可读的气泡形式查看输出，一键复制关键信息分享到群聊。
- **场景 B -- AI 辅助运维**：启用 Codex 模式，向远程服务器发送自然语言任务描述，Codex 在服务端执行 `codex exec --json --full-auto`，RikkaAgent 实时解析 JSONL 流，以推理步骤 + 代码块的形式展示 AI 的思考和操作过程。
- **场景 C -- 批量巡检**：在多个 Profile 之间切换，逐一执行预设的巡检命令（`df -h`、`free -m`、`uptime`），通过会话导出功能将结果汇总为报告。
- **场景 D -- 学习实验**：初学者在远程 Linux 机器上练习命令，聊天式界面让输入输出一目了然，代码块带语法高亮便于理解。
- **场景 E -- 代码审查与部署**：开发者通过 SSH 执行 `git pull`、`npm run build`、`pm2 restart`，查看带语法高亮的构建日志，出错时快速定位问题行。

---

## 2. 用户故事

### US-01：首次使用 -- 创建第一个连接

> As a **首次使用的用户**, I want **通过简单的向导创建第一个 SSH 连接配置并成功连接到远程服务器**, so that **我能快速上手使用应用，无需阅读冗长的文档**。

**验收标准**：
- 应用首次启动时展示简洁的引导页或直接进入 Profile 创建界面
- 必填字段仅主机地址、用户名、认证方式三项
- 一键测试连接，成功后自动跳转到聊天界面
- Host Key 首次信任以对话框形式展示指纹，用户点击"信任"后保存

**关联需求**：F-PF-01, F-PF-03, F-SEC-01, F-SEC-03

---

### US-02：日常运维 -- 执行远程诊断命令

> As a **运维工程师**, I want **在手机上快速连接到出问题的服务器并执行诊断命令，以清晰可读的气泡形式查看输出**, so that **在没有电脑的情况下也能在 15 秒内开始定位问题**。

**验收标准**：
- 从打开应用到看到命令输出 < 15 秒（含选择 Profile、输入命令）
- 输出中的关键信息（错误信息、堆栈跟踪）有语法高亮
- 可一键复制输出内容
- exit code 非 0 时以红色醒目展示

**关联需求**：F-SSH-01, F-SSH-03, F-SSH-07, F-SSH-08, F-UI-01, F-UI-02, F-UI-03

---

### US-03：Codex 执行 -- AI 辅助远程运维

> As a **开发者**, I want **在远程服务器上启用 Codex 模式，用自然语言描述任务并实时看到 AI 的推理过程和执行结果**, so that **我能借助 AI 自动完成复杂的运维和编码任务，无需手动执行每一步**。

**验收标准**：
- Codex 模式启用后，用户输入自动包装为 `codex exec --json --full-auto` 命令
- JSONL 流实时解析并展示推理步骤、工具调用、执行结果
- 推理步骤以可折叠的 ChainOfThought 卡片展示（折叠/预览/展开三态）
- API Key 安全注入，不在命令行中明文出现
- 进度摘要实时显示 AI 当前阶段（思考/执行/完成）

**关联需求**：F-AI-01 ~ F-AI-07

---

### US-04：密钥管理 -- 安全存储和使用 SSH 密钥

> As a **安全意识较强的用户**, I want **导入 SSH 密钥后由应用安全加密存储，并在连接时自动使用**, so that **我不用每次输入密码，同时密钥不会以明文形式暴露在设备上**。

**验收标准**：
- 支持导入 RSA/Ed25519 私钥文件和 PuTTY `.ppk` 格式
- 导入的私钥通过 AndroidX Security Crypto (AES-256-GCM) 加密存储
- 支持密钥 + 密码短语的组合认证
- 密码仅在会话期间保留在内存中，不写入本地存储
- 可查看已存储密钥的指纹信息，不可查看私钥原文

**关联需求**：F-SSH-01, F-SSH-02, F-SEC-04, F-SEC-07

---

### US-05：错误处理 -- 理解连接失败原因

> As a **遇到连接问题的用户**, I want **看到清晰的错误信息和解决建议，而不是晦涩的技术报错**, so that **我能自行排查问题而不需要搜索错误代码**。

**验收标准**：
- SSH 连接错误映射为用户友好的中文描述（如"连接超时"而非 `net.schmizz.sshj.userauth.UserAuthException: timeout`）
- Host Key 不匹配时展示高风险警告，显示旧指纹和新指纹，要求显式确认替换
- 认证失败时提示可能的原因（密码错误、密钥未授权、用户名不存在）
- 网络不可达时提示检查网络和防火墙设置

**关联需求**：F-SEC-02, F-SEC-03

---

### US-06：多 Profile -- 管理多台服务器

> As a **运维工程师**, I want **保存 10+ 台服务器的连接配置并快速在 Profile 之间切换**, so that **我能高效地在多台服务器间执行巡检任务而无需反复输入连接信息**。

**验收标准**：
- Profile 列表支持搜索/过滤，切换 < 1 秒
- 每个 Profile 可独立配置：默认工作目录、环境变量、Shell、Codex 模式开关
- Profile 列表显示连接状态标识（在线/离线/未知）
- 每个 Profile 的聊天记录独立保存，切换后自动恢复

**关联需求**：F-PF-01 ~ F-PF-05

---

### US-07：会话管理 -- 回顾和重用历史命令

> As a **开发者**, I want **查看历史会话记录、重执行之前的命令、搜索特定输出内容**, so that **我能快速回顾之前的运维操作而不需要重新执行**。

**验收标准**：
- 会话列表按时间倒序展示，显示 Profile 名称和最后执行的命令
- 每条消息支持"重新执行"按钮，点击后在同一 Profile 下重新发送该命令
- 命令历史支持上下键翻找，支持多行输入
- 会话记录在应用重启后完整恢复

**关联需求**：F-UI-04, F-UI-08, F-UI-09

---

### US-08：导出分享 -- 将执行结果分享给团队

> As a **运维工程师**, I want **将整个会话或单条命令的输出导出为 Markdown 文件并分享给同事**, so that **我能将故障排查过程或巡检结果作为文档留存或团队协作**。

**验收标准**：
- 支持导出整个会话为 Markdown 格式，包含时间戳、命令、输出
- 支持导出为纯文本格式
- 单条消息支持"分享"操作，通过 Android 系统分享菜单发送
- 导出文件名包含 Profile 名称和日期（如 `prod-server_2026-06-23.md`）

**关联需求**：F-UI-10

---

### US-09：主题切换 -- 适配不同使用环境

> As a **在不同光线环境下使用手机的用户**, I want **在亮色、暗色和 AMOLED 纯黑三种主题之间切换**, so that **在白天户外和夜间暗室都能舒适地阅读命令输出**。

**验收标准**：
- 支持亮色、暗色、AMOLED 三种主题模式
- Android 12+ 设备支持 Material You 动态取色
- 主题切换即时生效，无需重启应用
- 代码块的语法高亮配色随主题自动适配

**关联需求**：F-UI-12

---

### US-10：安全审计 -- 验证连接安全性

> As a **安全敏感的用户**, I want **查看所有已信任的 Host Key 列表、审计应用的安全配置**, so that **我能确认没有中间人攻击风险并验证凭据存储是否安全**。

**验收标准**：
- Known Hosts 管理界面：查看所有已信任的 host key 指纹、删除不再信任的条目
- Host Key 变更时弹出高风险警告，显示旧指纹和新指纹，要求用户显式确认替换
- 安全设置页展示：密钥存储方式（加密/明文）、API Key 存储状态
- 应用日志中不记录密码、私钥、API Key 等敏感信息

**关联需求**：F-SEC-01 ~ F-SEC-07

---

## 3. 功能需求

### P0 核心功能

> 必须在 v1.0 中完成，没有这些功能产品不可用。

#### 3.1 SSH 连接管理

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-PF-01 | Profile CRUD | 创建/编辑/删除 SSH 连接配置（主机地址、端口、用户名、认证方式、默认 Shell） | P0 |
| F-PF-02 | Profile 列表 | 支持搜索/过滤，显示连接状态标识 | P0 |
| F-PF-03 | 连接测试 | 编辑 Profile 时可测试 SSH 连接是否成功 | P0 |
| F-SSH-01 | 认证链 | 支持密码、密钥（RSA/Ed25519）、密钥+密码短语三种认证方式 | P0 |
| F-SSH-02 | PuTTY 密钥 | 支持 PuTTY `.ppk` 格式密钥导入 | P0 |
| F-SSH-03 | 流式输出 | 命令执行以 `Flow<ExecEvent>` 形式流式输出 stdout/stderr/exit code | P0 |
| F-SSH-04 | 连接复用 | 同一 Profile 的连续命令复用已有连接，减少握手开销 | P0 |
| F-SSH-07 | ANSI 清理 | 自动剥离 ANSI 颜色/光标控制码，保留纯文本内容 | P0 |
| F-SSH-08 | 退出码展示 | 命令结束后以醒目样式显示 exit code（0 绿色 / 非 0 红色） | P0 |
| F-SEC-01 | Known Hosts | 首次连接时展示 host key 指纹，用户确认后保存（TOFU 模型） | P0 |
| F-SEC-02 | Host Key 变更警告 | 已信任的主机 host key 发生变化时，弹出高风险警告，要求用户显式确认替换 | P0 |

#### 3.2 命令执行

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-UI-04 | 消息操作栏 | 每条消息支持复制、重新执行、分享操作 | P0 |
| F-UI-05 | 流式渲染 | 命令输出实时追加到气泡中，不阻塞 UI 线程 | P0 |
| F-UI-08 | 输入框 | 支持多行输入、命令历史（上下箭头）、回车发送 | P0 |

#### 3.3 聊天界面

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-UI-01 | 消息气泡 | 用户命令右对齐（主色调），输出左对齐（灰色），视觉区分清晰 | P0 |
| F-UI-02 | Markdown 渲染 | 输出中的 Markdown 内容正确渲染（标题、列表、表格、代码块、链接） | P0 |
| F-UI-03 | 语法高亮 | 代码块支持 50+ 编程语言的语法高亮，可折叠长代码块 | P0 |
| F-UI-09 | 会话持久化 | 聊天记录本地存储，应用重启后恢复 | P0 |
| F-UI-12 | 国际化 | 中文 + 英文双语 UI，中文优先 | P0 |

#### 3.4 安全

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-SEC-03 | TOFU | 首次连接采用 Trust On First Use 模型，降低首次使用门槛 | P0 |
| F-SEC-04 | 密钥加密存储 | 应用管理的私钥通过 AndroidX Security Crypto 加密存储，不在文件系统中保留明文 | P0 |
| F-SEC-05 | API Key 加密 | Codex API Key 等敏感配置项应加密存储，不以明文写入数据库（已知债务：当前 codexApiKey 存储在 Room DB 明文字段，修复排期见 ROADMAP） | P0 |
| F-SEC-06 | 日志脱敏 | 应用日志中不记录密码、私钥、API Key 等敏感信息 | P0 |
| F-SEC-07 | 密码不持久化 | SSH 密码仅在会话期间保留在内存中，不写入本地存储 | P0 |

---

### P1 增强功能

> v1.1 中完成，显著提升用户体验但不影响核心可用性。

#### 3.5 Codex / Claude Code 远程执行

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-AI-01 | Profile 级开关 | 每个 Profile 可独立启用/禁用 Codex 模式 | P1 |
| F-AI-02 | 命令组装 | `CommandComposer.wrapForCodex()` 自动包装用户输入为 `codex exec --json --full-auto` 命令 | P1 |
| F-AI-03 | JSONL 解析 | 容错解析 Codex 输出的 JSONL 事件流，提取 thread/turn/item 级别的进度 | P1 |
| F-AI-04 | 进度摘要 | 实时展示 AI 当前执行阶段（思考/执行/完成）和进度 | P1 |
| F-AI-05 | 工作目录配置 | 可指定 Codex 在远程服务器上的工作目录 | P1 |
| F-AI-06 | API Key 注入 | 安全注入 Codex 所需的 API Key 到远程环境 | P1 |
| F-AI-07 | 命令注入防护 | `wrapForCodex()` 必须转义 `$`、`` ` ``、`\` 等 shell 特殊字符 | P1 |

#### 3.6 渲染增强

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-UI-06 | 长输出截断 | 超过阈值（默认 500 行）的输出自动截断，提供"查看全部"展开 | P1 |
| F-UI-07 | Mermaid 图表 | 输出中的 Mermaid 代码块自动检测并渲染为流程图/时序图，失败时回退到源码展示 | P1 |
| F-UI-11 | 链接可点击 | 输出中的 URL 可识别并跳转浏览器 | P1 |

#### 3.7 会话管理增强

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-UI-10 | 会话导出 | 支持将整个会话导出为 Markdown/纯文本文件 | P1 |
| F-SSH-05 | 超时控制 | 单条命令执行超时可配置（默认 30s），超时后自动断开 | P1 |
| F-SSH-06 | 长命令支持 | 支持 `tail -f` 类持续输出场景，用户可手动停止 | P1 |

#### 3.8 主题与视觉

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-PF-05 | Profile 独立配置 | 每个 Profile 可独立配置：默认工作目录、环境变量、Codex 模式开关 | P1 |

---

### P2 扩展功能

> v2.0 中完成，产品进化为远程 AI Agent 平台。

#### 3.9 多 Agent Runner 支持

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-AI-08 | 多 AI 后端 | 支持 Claude Code、Aider 等更多 AI CLI 工具 | P2 |
| F-AI-09 | Agent 抽象层 | `AgentRunner` 接口统一不同 AI CLI 的调用方式和输出格式 | P2 |

#### 3.10 PTY 交互模式

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-SSH-09 | PTY 支持 | 可选的 PTY 终端模拟，支持 `vim`、`top`、`htop` 等交互式程序 | P2 |
| F-SSH-10 | 终端大小协商 | PTY 模式下支持终端窗口大小动态调整 | P2 |

#### 3.11 Profile 增强

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-PF-04 | Profile 导入/导出 | 支持 JSON 格式的 Profile 批量导入导出（不含密钥） | P2 |
| F-PF-06 | Profile 分组 | 支持按项目/环境对 Profile 进行分组管理 | P2 |
| F-PF-07 | 快速切换侧边栏 | 平板横屏模式下左侧常驻 Profile 列表 | P2 |

#### 3.12 搜索与导航

| ID | 需求 | 描述 | 优先级 |
|----|------|------|--------|
| F-UI-13 | 全文搜索 | 在当前会话中搜索文本，高亮匹配结果 | P2 |
| F-UI-14 | 命令模板 | 保存常用命令为模板，快速选择执行 | P2 |

---

## 4. 非功能需求

### 4.1 性能

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| 消息渲染帧率 | >= 60fps，单帧 < 16ms | Compose 帧率监控 |
| SSH 连接建立 | < 3s（局域网）/ < 8s（公网） | 从发起连接到认证完成 |
| 命令执行首字节延迟 | < 500ms（局域网）/ < 2s（公网） | 从命令发送到首个 stdout 字节 |
| 应用冷启动 | < 2s（API 24+ 设备） | 从 Launcher 点击到首帧渲染 |
| 流式输出吞吐 | 10,000 行输出不卡顿 | 虚拟化滚动 + 异步渲染 |
| 内存占用 | 空闲状态 < 80MB RSS | Android Profiler |
| APK 体积 | < 15MB（不含 Mermaid WebView 资源） | CI 构建产物 |

### 4.2 安全

| 项目 | 要求 | 实现方式 |
|------|------|----------|
| 零明文凭据 | 私钥、密码、API Key 均不以明文形式存储 | AndroidX Security Crypto (AES-256-GCM) + Android Keystore |
| 零命令注入 | 用户输入在传递给 SSH 前必须正确转义 shell 特殊字符 | `CommandComposer` 转义层 |
| 零日志泄露 | 应用日志中不记录任何敏感信息 | 日志脱敏中间件 |
| Host Key 验证 | TOFU + 变更显式确认 | `KnownHostsStore` + 高风险对话框 |
| 传输安全 | 仅 SSH 协议，不使用 HTTP 明文传输凭据 | sshj 库 |
| 依赖审计 | 第三方依赖无已知高危漏洞 | Dependabot + CI 集成 |

### 4.3 可用性

| 项目 | 要求 |
|------|------|
| 最低 API | Android 7.0（API 24） |
| 目标 API | Android 15（API 35） |
| 无障碍 | WCAG 2.1 AA 级别，关键操作提供 contentDescription |
| TalkBack | 屏幕阅读器支持，所有交互元素可访问 |
| 主题 | 亮色 / 暗色 / AMOLED 三模式 |
| 动态取色 | Material You 动态取色（Android 12+） |
| 屏幕适配 | 手机竖屏为主，平板横屏基本可用 |

### 4.4 国际化

| 项目 | 要求 |
|------|------|
| 语言 | 中文（zh-CN）为默认，英文（en）完整覆盖 |
| 资源管理 | Android string resources，禁止硬编码字符串 |
| 文本方向 | LTR 为主，预留 RTL 扩展能力 |
| 错误信息 | 所有用户可见的错误信息均有中英文版本 |

### 4.5 兼容性

| 项目 | 要求 |
|------|------|
| 设备类型 | 手机 + 平板 |
| 屏幕尺寸 | 5" ~ 12.9" |
| 分辨率 | 720p ~ 2K |
| SSH 服务器 | OpenSSH 7.0+（RSA/Ed25519/ECDSA） |
| 密钥格式 | OpenSSH、PuTTY `.ppk`、PEM |

### 4.6 可维护性

| 项目 | 要求 |
|------|------|
| 架构 | 五模块分层（app / core:model / core:ssh / core:storage / core:ui） |
| 测试 | 核心路径单元测试覆盖率 > 70%，CI 门禁 |
| 静态分析 | lint 零 error，建议引入 detekt |
| 文档 | 每个模块有 README，API 有 KDoc 注释 |
| CI/CD | GitHub Actions 自动验证：单元测试 + lint + assemble + artifacts |

---

## 5. 竞品分析

### 5.1 功能对比矩阵

| 维度 | RikkaAgent | Termux | ConnectBot | JuiceSSH |
|------|------------|--------|------------|----------|
| **交互范式** | 聊天气泡 | 终端模拟器 | 终端模拟器 | 终端模拟器 |
| **定位** | Chat-first SSH Runner | 本地 Linux 环境 | 轻量 SSH 客户端 | 商业级 SSH 客户端 |
| **PTY 支持** | 不支持（设计决策） | 完整 | 完整 | 完整 |
| **输出可读性** | 高（Markdown + 语法高亮 + 代码折叠） | 低（纯文本） | 低（纯文本） | 中（基本 ANSI 颜色） |
| **AI 集成** | Codex / Claude Code（一等公民） | 无（需手动安装） | 无 | 无 |
| **推理展示** | ChainOfThought 三态折叠 | 无 | 无 | 无 |
| **Mermaid 图表** | 支持（自动检测 + 渲染） | 无 | 无 | 无 |
| **密钥管理** | Android Keystore 加密 | 文件系统 | Android Keystore | Android Keystore |
| **Host Key 验证** | TOFU + 显式确认 | TOFU | TOFU + 显式确认 | 弱（自动接受） |
| **会话导出** | Markdown / 纯文本 | 无 | 无 | 有限 |
| **多 Profile** | 支持（搜索/过滤/独立配置） | N/A | 支持 | 支持 |
| **开源** | Apache-2.0 | GPL-3.0 | Apache-2.0 | 部分开源 |
| **价格** | 免费 | 免费 | 免费 | 免费/付费 |
| **APK 体积** | < 15MB | ~200MB（含完整 Linux 环境） | ~5MB | ~15MB |
| **最低 API** | 24 | 24 | 21 | 21 |

### 5.2 差异化优势

**1. 可读性优先**

传统 SSH 客户端输出是连续滚动的终端文本，长输出（如 `docker logs`、`journalctl`）需要用户自己在大量文本中搜索关键信息。RikkaAgent 将每条命令的输入输出封装为独立的消息气泡，支持 Markdown 渲染、语法高亮、代码折叠，让结构化输出一目了然。

**2. AI 原生集成**

RikkaAgent 是唯一将 Codex/Claude Code 集成为一等公民的 Android SSH 客户端。用户不需要在远程服务器上手动操作 AI 工具，只需在 Profile 中启用 Codex 模式，即可通过聊天界面与 AI 交互，实时查看推理过程。这是传统终端模拟器无法提供的体验。

**3. 安全基线**

- TOFU + 显式确认的 Host Key 验证（ConnectBot 也有，但 JuiceSSH 较弱）
- AndroidX Security Crypto 加密存储（非明文文件）
- 日志脱敏、密码不持久化
- 命令注入防护

**4. 轻量与专注**

不试图成为"万能终端"，而是聚焦于 exec channel 场景（日常运维命令），避免了 PTY 模拟的复杂性。这意味着更小的 APK、更低的内存占用、更高的可靠性。

### 5.3 竞品劣势与应对

| 劣势 | 应对策略 | 时间线 |
|------|----------|--------|
| 不支持 PTY（无法运行 `vim`/`top`） | v2.0 引入可选 PTY 模块 | v2.0 |
| 无本地 Shell（Termux 有完整 Linux 环境） | 不竞争此场景，聚焦远程执行 | N/A |
| 生态不成熟（新项目，社区小） | 开源 + 清晰文档 + CI 质量门禁，降低贡献门槛 | 持续 |
| UI 复刻 RikkaHub 工作量大 | 分阶段对齐，v1.0 先保证功能可用，v1.1 再提升视觉质量 | v1.0 ~ v1.1 |

---

## 6. 版本规划

### v1.0 -- 核心 SSH + 聊天 UI（MVP）

**目标**：完成核心 SSH 聊天体验，可以日常使用。

| 功能域 | 包含内容 |
|--------|----------|
| SSH 引擎 | exec channel、密码/密钥认证（RSA/Ed25519/PuTTY ppk）、连接复用、ANSI 清理、退出码展示 |
| 聊天 UI | 气泡布局、Markdown 基础渲染、语法高亮（50+ 语言）、代码折叠、消息操作栏（复制/重执行/分享） |
| Profile | 创建/编辑/删除/搜索、连接测试、Profile 级 Shell 配置 |
| 安全 | Known Hosts TOFU + 变更警告、密钥加密存储（AndroidX Security Crypto）、API Key 加密、日志脱敏、密码不持久化 |
| 持久化 | 聊天记录 Room 存储、会话列表、命令历史 |
| 输入 | 多行输入、命令历史（上下键）、回车发送 |
| i18n | 中文 + 英文 |
| 主题 | 亮色 / 暗色 / AMOLED 三模式 |

**必须修复的技术债务**：
- 存储层 Bug 修复（insertMessage 覆写 thread 标题、REPLACE 策略导致消息丢失）
- `codexApiKey` 明文存储修复
- `wrapForCodex()` 命令注入防护（转义 `$`、`` ` ``、`\`）
- ChatViewModel 拆分（SessionManager / CommandExecutor / AuthCallbackBroker）
- SSH `runBlocking` 死锁消除

**里程碑**：M0 Spec freeze -> M1 App core UX -> M2 Rendering pipeline -> M3 SSH engine -> M5 Release quality

### v1.1 -- Codex 集成 + Mermaid（体验增强）

**目标**：UI 质量对齐 RikkaHub 水准，补全 AI 集成和高频功能。

| 功能域 | 新增内容 |
|--------|----------|
| Codex 集成 | Profile 级开关、JSONL 解析、推理步骤展示（ChainOfThought 三态折叠）、进度摘要、工作目录配置 |
| 渲染升级 | Mermaid 图表渲染（流程图/时序图）、链接可点击、长输出截断 + 展开 |
| 会话管理 | 会话导出（Markdown/纯文本）、长命令支持（`tail -f`） |
| 主题增强 | Material You 动态取色（Android 12+） |
| 输入增强 | 环境变量配置、超时控制 |

**里程碑**：M4 Codex integration -> M6 MessagePart + ViewModel refactor

### v2.0 -- 多 Agent Runner + PTY（平台进化）

**目标**：从 SSH 客户端进化为远程 AI Agent 平台。

| 功能域 | 新增内容 |
|--------|----------|
| 多 AI 后端 | AgentRunner 抽象层，支持 Claude Code、Aider 等更多 AI CLI 工具 |
| PTY 交互 | 可选 PTY 终端模拟模块，支持 `vim`/`top`/`htop`，终端大小协商 |
| Profile 增强 | Profile 导入/导出（JSON）、Profile 分组、快速切换侧边栏 |
| 连接池 | 跨 Profile 连接池管理、自动重连、断线检测 |
| 搜索 | 全文搜索、命令模板 |
| 平板适配 | 横屏双栏布局（Profile 列表 + 聊天区） |
| 测试与质量 | 核心路径测试覆盖率 > 70%、detekt 静态分析、CI 覆盖率门禁 |

---

## 附录

### A. 术语表

| 术语 | 定义 |
|------|------|
| Exec Channel | SSH 协议中的非交互式命令执行通道，不分配 PTY |
| PTY | Pseudo Terminal，伪终端，支持光标控制等交互式操作 |
| TOFU | Trust On First Use，首次使用信任模型 |
| JSONL | JSON Lines，每行一个 JSON 对象的流式数据格式 |
| Host Key | SSH 服务器的身份标识密钥，用于验证服务器身份 |
| Profile | RikkaAgent 中的一组 SSH 连接配置 |
| MessagePart | 结构化消息模型的密封类，支持 Command/Stdout/Stderr/Text/Code/Reasoning/Error/Mermaid 八种类型 |
| ChainOfThought | AI 推理过程的可视化展示，支持折叠/预览/展开三态 |
| ANSI | 美国国家标准学会制定的终端控制字符标准 |

### B. 参考文档

| 文档 | 路径 |
|------|------|
| README | [README.md](../README.md) |
| 技术分析报告 | [analysis/analysis-report.md](analysis/analysis-report.md) |
| 任务分解 | [plan/task-breakdown.md](plan/task-breakdown.md) |
| 威胁模型 | [threat-model.md](threat-model.md) |
| 隐私审计 | [privacy-audit.md](privacy-audit.md) |
| Spec 索引 | [spec/00-index.md](spec/00-index.md) |
| 架构文档 | [docs/architecture.md](../docs/architecture.md) |
| 发布验收矩阵 | [docs/spec/81-release-acceptance-matrix.md](../docs/spec/81-release-acceptance-matrix.md) |

### C. 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-06-23 | 初始版本，基于 README 和分析报告 |
| 2.0 | 2026-06-23 | 补充 10 个用户故事、P0/P1/P2 分级功能需求、竞品矩阵、版本规划细化 |
