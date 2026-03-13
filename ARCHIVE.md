# ARCHIVE — rikka-agent 历史记录

> 用途：已完成的工作项归档。当前进度 → `ROADMAP.md` ｜ 事实 → `STATE.md`

---

## rikka-agent 应用开发

### M0 完成项（规范冻结）

- ✅ 开源仓库骨架 (Apache-2.0 + 贡献规范 + 安全文档)
- ✅ docs/spec 深化 (分版块 spec 全套)
- ✅ 参考项目 UI 观察笔记
- ✅ v1 远程执行规范 (`docs/spec/33-remote-exec.md`)
- ✅ ACP 规范 (`docs/spec/36-acp.md`)

### M1 完成项（UI 骨架 + 功能）

**屏幕：**

| 屏幕 | 文件 | 能力 |
|------|------|------|
| ProfilesScreen | `app/.../ui/screen/ProfilesScreen.kt` | LargeTopAppBar, 滑动删除, 空状态, FAB, 字母头像, 认证类型标签, 长按菜单, 配置复制 |
| ProfileEditorScreen | `app/.../ui/screen/ProfileEditorScreen.kt` | Card 分组, 端口校验 1-65535, SectionLabel, 私钥文件选择器 SAF, 剪贴板粘贴密钥, internal-key://, IME padding |
| ChatScreen | `app/.../ui/screen/ChatScreen.kt` | 会话侧边栏, 连接状态条, 执行计时器, Host Key 对话框, 密码弹窗, 命令建议芯片, 删除确认 |
| SettingsScreen | `app/.../ui/screen/SettingsScreen.kt` | 主题选择器含 AMOLED, Shell 选择器, Known Hosts 导航, About 导航 |
| KnownHostsScreen | `app/.../ui/screen/KnownHostsScreen.kt` | 已信任主机列表, 指纹/类型/日期, 单条删除确认, 空状态 |
| AboutScreen | `app/.../ui/screen/AboutScreen.kt` | 应用信息, Apache 2.0 协议, OSS 依赖列表含许可证 |

**组件 & 功能：**
- ChatBubble（用户气泡 + 助手 CodeCard/MarkdownText, 流式动画, 错误状态, 时间戳, 复制, 触觉反馈）
- CodeCard（可折叠代码输出, 复制, 语言标签, 水平滚动, 自动折叠 15 行）
- MarkdownText（标题/段落/列表/引用/链接/行内代码/代码块/表格/删除线）
- ChatInput（多行输入, 回车发送, Monospace, Shell 占位符, 执行中禁用）
- AnsiStripper（ANSI 转义码清理）
- RikkaAgentTheme（亮色/暗色/AMOLED 三模式）
- 构建链修复：Kotlin 2.1.0 / AGP 8.8.0 / Gradle 8.10.2 / Compose 编译器插件
- Room DB v1→v2 迁移
- Koin DI 注册
- 导航路由：7 screens (Profiles, ProfileEditor, Session, Settings, KnownHosts, About)
- CI/CD：GitHub Actions CI workflow

### M3 完成项（SSH 引擎）

- ✅ SSH 库选型：sshj 0.39.0 (BSD-2-Clause)
- ✅ SshjExecRunner — SSH exec 通道 (stdout/stderr 并发, exit code 捕获)
- ✅ KnownHostsStore + InMemoryKnownHostsStore + DataStoreKnownHostsStore
- ✅ HostKeyCallback (未知主机/密钥不匹配 UI 回调)
- ✅ ChatViewModel 重写为 SSH 驱动
- ✅ Host key 验证对话框
- ✅ 连接状态 UI (IDLE/READY/EXECUTING/ERROR)
- ✅ 密码认证 UI 弹窗
- ✅ 聊天消息持久化 (Room)
- ✅ SSH 连接复用 (SshjExecRunner 缓存)
- ✅ 会话管理侧边栏 (ModalNavigationDrawer)
- ✅ 聊天历史加载/恢复
- ✅ 线程自动标题
- ✅ 私钥文件选择器 (SAF)
- ✅ 密钥密码短语支持
- ✅ 内容模式密钥加载 (OpenSSHKeyFile + StringReader)
- ✅ Known Hosts 查看器
- ✅ Shell 偏好生效
- ✅ 连接自动重试 (stale → evict + reconnect)
- ✅ Android SSH 认证修复：PublicKey 无密钥时降级密码认证
- ✅ 剪贴板粘贴私钥 (internal-key:// scheme)
- ✅ PuTTY `.ppk` 私钥加载支持
- ✅ 应用内 Ed25519 密钥对生成 (SshKeyGenerator + BouncyCastle)
- ✅ 公钥展示卡片 + 一键复制
- ✅ 友好 SSH 错误提示
- ✅ 连接测试按钮 (TCP + SSH banner)
- ✅ Profile 自动命名
- ✅ 命令重执行按钮
- ✅ 分享输出按钮 (Android ACTION_SEND)
- ✅ 会话导出/分享
- ✅ richtext-commonmark 依赖移除
- ✅ BuildConfig 版本号 + Apache 2.0 协议显示
- ✅ 空输出提示 ("(no output)" + exit code)
- ✅ 执行计时器 (TopAppBar "Running… Ns")
- ✅ 命令建议芯片 (uname/df/uptime)
- ✅ 触觉反馈 (复制 + 发送)
- ✅ 会话删除确认
- ✅ 配置复制 (长按菜单)
- ✅ About 页面

### M2 完成项（渲染管线）

- ✅ commonmark-java 0.22.0 集成 (BSD-2-Clause)
- ✅ GFM 表格 + 删除线扩展
- ✅ MarkdownText 自定义 Compose 渲染器 (标题/段落/列表/引用/行内代码/代码块/表格/链接/粗体/斜体/删除线)
- ✅ ChatBubble Markdown 自动检测 (looksLikeMarkdown 启发式)
- ✅ Codex 输出 Markdown 渲染能力

### M4 完成项（Codex 接入）

- ✅ SshProfile + SshProfileEntity 增加 codexMode / codexWorkDir 字段
- ✅ Room DB v2 → v3 (fallbackToDestructiveMigration)
- ✅ entity ↔ model 映射更新
- ✅ ProfileEditorScreen Codex 设置区（Switch 开关 + 工作目录输入）
- ✅ ChatViewModel codex exec 命令包装 (codex exec --full-auto)
- ✅ wrapForCodex + shellQuote 安全转义

### 安全加固

- ✅ SSH 密钥加密存储 (EncryptedFile, AES-256-GCM via AndroidX Security Crypto)
- ✅ 旧明文密钥兼容读取 (graceful fallback)

### 工程治理与文档收敛（2026-03-13）

- ✅ 仓库边界清理：移除与 rikka-agent 无关的 VectorControl 内容并外部归档
- ✅ `README.md` 重构为发布导向版（徽章、能力矩阵、快速上手、状态表）
- ✅ CI 增强：增加 `:app:lintDevDebug` 与 test/lint/apk 报告产物上传
- ✅ Spec 纠偏：`docs/spec/05-open-questions.md` 与当前 sshj/commonmark 实现一致

### 体验增强（2026-03-13）

- ✅ Codex JSONL 状态流式展示（会话内可见执行状态）
- ✅ SSH 私钥格式扩展：新增 PuTTY `.ppk` 加载支持
- ✅ `:core:ssh` 单元测试补齐：`JsonlParserTest`（JSON/非JSON/分块/flush）
- ✅ 截断输出支持“完整查看 + 完整分享”（消息级）
- ✅ 输出格式化测试：`OutputFormatterTest`（截断标记/完整输出一致性）
- ✅ 认证链路测试：`SshAuthKeyFormatTest`（PuTTY/OpenSSH 判定）
