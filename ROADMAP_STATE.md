# ROADMAP STATE — rikka-agent

> 用途：记录 `rikka-agent` 项目内的运行态事实、关键决策与交接说明。

## 关键决策（SSOT）

- 产品形态：Android App（Kotlin + Jetpack Compose）
- 交互模式：Mode A（非交互命令执行；不做终端模拟器/PTY/ANSI 渲染）
- 连接方式：Android 端原生 SSH 直连服务器；默认不引入“服务端 HTTP 远程执行中转”
- 安全原则：Known-hosts 校验默认开启；host key mismatch 默认阻断；密钥不写日志；不把私钥提交到仓库
- 开源协议：Apache-2.0（见 `LICENSE`）
- Clean-room：参考 UI“感觉”，不拷贝参考项目代码

## 当前进展（截至 2026-03-12）

- 已完成 M0：开源仓库骨架 + docs/spec 深化 + 参考项目 UI 观察笔记
- 已补充：v1 远程执行规范 (`docs/spec/33-remote-exec.md`)、ACP 规范 (`docs/spec/36-acp.md`)
- 已完成 M1 部分：Android 构建链修复并成功出包 `app-dev-debug.apk`
  - 技术栈升级：Kotlin 2.1.0 / AGP 8.8.0 / Gradle 8.10.2 / Compose 编译器插件
  - 构建状态：✅ BUILD SUCCESSFUL

### M1 UI 实现进度

**已完成的屏幕：**

| 屏幕 | 文件 | 状态 |
|------|------|------|
| ProfilesScreen | `app/.../ui/screen/ProfilesScreen.kt` | ✅ 完成（LargeTopAppBar, 滑动删除, 空状态, FAB, 字母头像, 认证类型标签, 长按菜单, 配置复制） |
| ProfileEditorScreen | `app/.../ui/screen/ProfileEditorScreen.kt` | ✅ 完成（Card 分组, 端口校验 1-65535, SectionLabel, 私钥文件选择器 SAF, 剪贴板粘贴密钥, internal-key://, IME padding） |
| ChatScreen | `app/.../ui/screen/ChatScreen.kt` | ✅ 完成（会话侧边栏, 连接状态条, 执行计时器, Host Key 对话框, 密码弹窗, 命令建议芯片, 删除确认） |
| SettingsScreen | `app/.../ui/screen/SettingsScreen.kt` | ✅ 完成（主题选择器含 AMOLED, Shell 选择器, Known Hosts 导航, About 导航） |
| KnownHostsScreen | `app/.../ui/screen/KnownHostsScreen.kt` | ✅ 完成（已信任主机列表, 指纹/类型/日期显示, 单条删除确认, 空状态） |
| AboutScreen | `app/.../ui/screen/AboutScreen.kt` | ✅ 完成（应用信息, Apache 2.0 协议, OSS 依赖列表含许可证） |

**已完成的组件：**
- ChatBubble（用户消息气泡 + 助手消息 CodeCard 渲染, 流式动画, 错误状态, 时间戳, 复制按钮, 触觉反馈）
- CodeCard（可折叠代码输出, 复制按钮, 语言标签, 水平滚动, 自动折叠 15 行, 触觉反馈）
- ChatInput（多行输入, 回车发送, Monospace 字体, Shell 占位符, 执行中禁用, 触觉反馈）
- AnsiStripper（ANSI 转义码清理）
- RikkaAgentTheme（亮色/暗色/AMOLED 三模式, ThemeMode enum, 从设置实时切换）

### M3 SSH 引擎实现进度

**已完成：**
- ✅ SSH 库选型：sshj 0.39.0 (BSD-2-Clause, 支持 Ed25519/RSA/ECDSA)
- ✅ `SshjExecRunner` — 真实 SSH exec 通道实现（stdout/stderr 并发读取, exit code 捕获）
- ✅ `KnownHostsStore` 接口 + `InMemoryKnownHostsStore` 实现
- ✅ `HostKeyCallback` 接口（未知主机 / 密钥不匹配的 UI 回调）
- ✅ ChatViewModel 重写为 SSH 驱动（替换了假数据流式模拟）
- ✅ Host key 验证对话框（首次连接提示 + 密钥变更警告）
- ✅ 连接状态 UI（IDLE/READY/EXECUTING/ERROR + 进度条 + 取消按钮）
- ✅ Koin DI 注册（KnownHostsStore, ChatViewModel 带 profileId 参数）
- ✅ BouncyCastle META-INF 冲突已在 packaging 中排除
- ✅ 构建通过：BUILD SUCCESSFUL
- ✅ `DataStoreKnownHostsStore` — 持久化 known hosts（DataStore, JSON 序列化）
- ✅ 密码认证 UI 弹窗（PasswordProvider + PasswordDialog）
- ✅ 聊天消息持久化（Room: ChatThreadEntity + ChatMessageEntity + ChatMessageDao + RoomChatRepository）
- ✅ ChatBubble 增强：错误状态 errorContainer 红色样式, 流式打点动画, 相对时间戳
- ✅ AMOLED 纯黑主题模式（0x000000 背景, ThemeMode.Amoled）
- ✅ 主题实时切换（MainActivity 观察 AppPreferences.theme → ThemeMode 映射）
- ✅ Room DB v1→v2 迁移（fallbackToDestructiveMigration）
- ✅ SSH 连接复用（SshjExecRunner 内部缓存, reuseConnections 标志）
- ✅ 会话管理侧边栏（ModalNavigationDrawer: 线程列表, 新建/切换/删除）
- ✅ 聊天历史加载/恢复（switchThread 从 Room 加载）
- ✅ 线程自动标题（首条用户命令前 50 字符）
- ✅ CodeCard 组件（可折叠代码输出, 复制按钮, 语言标签, 水平滚动）
- ✅ ChatBubble 重构（助手消息改用 CodeCard 渲染, 用户消息保持气泡样式）
- ✅ 私钥文件选择器（SAF 文件选取, ContentUri 持久化权限, ProfileEditor 集成）
- ✅ 密钥密码短语支持（PassphraseProvider + 弹窗复用 PasswordDialog）
- ✅ 内容模式密钥加载（OpenSSHKeyFile + StringReader, 不依赖文件路径）
- ✅ Known Hosts 查看器（KnownHostsScreen: 列表 + 单条删除 + 空状态）
- ✅ 导航路由完善（Screen.KnownHosts, type-safe navigation）
- ✅ Edge-to-edge 优化（ProfileEditorScreen 添加 imePadding）
- ✅ Shell 偏好生效（ChatViewModel 中 wrapWithShell, 非 /bin/sh 时包装命令）
- ✅ 连接自动重试（stale cache → evict + reconnect once）
- ✅ ChatInput 体感优化（Monospace 字体, Shell 占位符, 执行中禁用）
- ✅ richtext-commonmark 依赖移除（CodeCard 替代 Markdown 渲染）
- ✅ BuildConfig 版本号 + Apache 2.0 协议显示
- ✅ 空输出提示（命令无输出时显示 "(no output)" + exit code）
- ✅ 执行计时器（TopAppBar 显示 "Running… Ns"）
- ✅ 命令建议芯片（空会话时显示 uname/df/uptime 快捷命令）
- ✅ 触觉反馈（复制按钮 + 发送按钮 HapticFeedback）
- ✅ 会话删除确认（侧边栏删除需确认）
- ✅ 配置复制（长按配置卡片弹出菜单: 编辑/复制）
- ✅ About 页面（应用信息 + OSS 依赖列表 + 许可证）
- ✅ 导航路由: 7 screens (Profiles, ProfileEditor, Session, Settings, KnownHosts, About)

- ✅ Android SSH 认证修复：PublicKey 无密钥时降级密码认证（不再尝试不存在的 ~/.ssh/）
- ✅ 剪贴板粘贴私钥：internal-key:// scheme, 保存到 app 私有 filesDir/ssh_keys/
- ✅ ProfileEditorScreen 密钥操作："Select File" + "Paste Key" + "Generate Ed25519 Key Pair" 三按钮布局
- ✅ Koin 双注册（ContentUriKeyContentProvider 同时作为具体类型和 KeyContentProvider 接口）
- ✅ 应用内 Ed25519 密钥对生成（SshKeyGenerator + BouncyCastle, PKCS8 PEM 私钥 + OpenSSH 公钥格式）
- ✅ 公钥展示卡片 + 一键复制（用于添加到服务器 authorized_keys）
- ✅ 友好 SSH 错误提示（connection_refused/timeout/unknown_host/auth_failed → 用户可操作描述）
- ✅ 连接测试按钮（TCP 可达性检查 + SSH banner 显示, 超时 5s）
- ✅ Profile 自动命名（空名称时 → username@host:port）
- ✅ 粘贴密钥 Snackbar 反馈（成功/空剪贴板/格式无效）

**待完成：**
- 更多密钥格式支持（PuTTY .ppk）

## VectorControl Portal (原 Gateway Portal) 基础设施

> 独立项目：`D:\Code\Projects\VectorControl-Portal`（纯静态站 HTML/CSS/JS，无构建系统）

### 部署架构

```
用户 → DNS (vectorcontrol.tech / www.vectorcontrol.tech)
     → A 记录 20.195.40.11
     → sgp2 (Azure Singapore, 20.195.40.11)
       ├─ nginx 443 → 静态门户 /var/www/vectorcontrol-portal/
       ├─ /management → proxy_pass gz:8317/management.html (CLIProxyAPI 管理界面)
       ├─ /fund/ → alias /var/www/vectorcontrol-fund/ (React SPA 投研系统)
       ├─ /playground/ → proxy_pass gz:4001/ (metapi Playground)
       ├─ /api/, /v1/ → proxy_pass gz:4001 (metapi OpenAI API)
       └─ SSL cert: /etc/letsencrypt/live/www.vectorcontrol.tech/ (certbot + nginx authenticator)

gz (Alibaba Cloud, 8.163.12.208 / Tailscale 100.96.101.24)
  ├─ metapi Docker (--network host, port 4001) = OpenAI 兼容 API 网关
  ├─ CLIProxyAPI (binary v6.8.51, port 8317) = API 密钥管理 + 路由配置
  ├─ nginx 443 → /playground/ proxy to metapi (仅内部, 阿里云无备案拦截 443)
  └─ Tailscale 组网: gz ↔ sgp1 ↔ sgp2 ↔ 本机

hk (Azure Hong Kong, 20.191.156.135 / Tailscale 100.79.22.119)  [2026-06-10 上线]
  ├─ 2核1G, Ubuntu 24.04, kernel 6.14.0-1017-azure
  ├─ Tailscale 已连接, UFW (80/443/41641, SSH 仅 Tailscale)
  ├─ fail2ban + unattended-upgrades + SSH hardening
  ├─ 角色: 备用公网入口候选
  ├─ 延迟: → sgp2 0.5ms, → sgp1 22ms, → gz 82ms
  └─ SSH 别名: hk (Tailscale IP)
```

### 关键事实

| 条目 | 值 |
|------|-----|
| 公网入口 | https://www.vectorcontrol.tech |
| DNS A 记录 | 20.195.40.11 (sgp2 Azure) |
| 静态文件位置 | sgp2:/var/www/vectorcontrol-portal/ |
| 后端 | gz:4001 metapi (Docker, `1467078763/metapi:latest`) |
| SSL 覆盖域名 | vectorcontrol.tech + www.vectorcontrol.tech |
| SSL 续期 | certbot.timer 自动，nginx authenticator，30 天提前续期，到期 2026-06-10 |
| 阿里云限制 | gz 443 端口被拦截（无 ICP 备案），公网流量必须经 sgp2 |
| 部署方式 | SCP 到 sgp2 via Tailscale IP (root@100.82.99.84) |
| 主题 | 翡翠绿深色极简 (accent #10b981)，Hero 辐射光晕 + 玻璃拟态面板 |
| 配置归档 | `deploy/sgp2.vectorcontrol.tech.conf` (sgp2 专用) |
| 快照脚本 | `tools/measure-chain.ps1` → `artifacts/latency-snapshot.json` |
| GitHub 仓库 | DeliciousBuding/VectorControl-Portal (private) |

### 门户进展（2026-03-12）

**视觉与交互**
- ✅ 翡翠绿深色主题、Hero 辐射光晕、玻璃拟态面板、卡片 hover 交互
- ✅ 极简布局重设计、品牌脉冲动画、滚动入场动画
- ✅ 实时服务状态点（探活 HEAD 请求 + 优雅降级）

**功能**
- ✅ Services 卡片区（Management / Fund / Playground / API）
- ✅ OG 社交标签、Footer 版权年份自动更新
- ✅ 裸域 → www 301 重定向
- ✅ Fund 前端部署 /fund/（React SPA）、Management 路由代理到 CPA

**安全与性能**
- ✅ 安全头（HSTS/nosniff/DENY/no-referrer）
- ✅ SSL 双域名覆盖（到期 2026-06-10, certbot 自动续期）
- ✅ 静态资源 7 天缓存头 + gzip 全类型启用（总传输 ~10.8KB）
- ✅ SEO: robots.txt + sitemap.xml + OG 标签

**运维**
- ✅ 项目重命名：vectorcontrol-gateway-portal → VectorControl-Portal（全链路）
- ✅ CLIProxyAPI 升级 6.8.50 → 6.8.51、管理密码重置
- ✅ GitHub 私有仓库 DeliciousBuding/VectorControl-Portal
- ✅ 旧仓库/旧目录已清理
- ✅ 服务端自动采集: gz cron 5min 运行 measure-snapshot.py → /api/snapshot 端点
- ✅ 前端从 API 拉取实时数据，加载骨架屏 + 自刷新倒计时
- ✅ 基础设施加固审计 prompt 已保存 (docs/infra-audit-prompt.md)

### 基础设施深度审计（2026-06-10）

**已修复：**
- ✅ metapi 503 大规模故障：`token_routes` 和 `route_channels` 表清空（Docker 重建副作用）→ 从备份恢复 17 routes + 17 channels，重新激活账户，gpt-5.4 completion 验证通过
- ✅ 前端 XSS 加固：`renderSnapshot()` 中 6 处 innerHTML（summaryTags / snapshotGrid / topology / hopGrid / branchGrid / renderMetricList）未调用 `escapeHtml()` → 全部修复并部署 (commit `1ce4f92`)
- ✅ sgp1 rp_filter 从 loose (2) 修复为 strict (1)：`/etc/sysctl.d/99-rp-filter.conf`

**审计确认（安全状态良好）：**
- ✅ gz: fail2ban active, unattended-upgrades, kernel 6.8.0-63, Docker restart=unless-stopped, CPA restart=always
- ✅ gz: 8317/8443 端口公网不可达（UFW default deny + ISP 阻断）
- ✅ gz: SSH 有效配置安全（99-delicious-hardening.conf 覆盖：PermitRootLogin no, PasswordAuthentication no）
- ✅ sgp2: fail2ban active, unattended-upgrades, UFW policy DROP, kernel 6.14.0-1017-azure
- ✅ sgp2: certbot.timer active, SSL 到期 2026-06-10
- ✅ sgp2: 仅开放 22/80/443 + Tailscale SOCKS (1080 限定 IP)

**关注项（低风险）：**
- ⚠️ sgp1: 内存压力 (1.0G/1.9G + 297M swap, disk 55%)
- ⚠️ sgp2: 内存 469M/847M + 69M swap
- ⚠️ sgp1: Docker HTTPS (443) 上的 VectorControl 部署无独立 Let's Encrypt（cert 可能在容器内）

**端到端验证（2026-06-10）：**
- Portal: 200 ✅ | API Snapshot: 200 ✅ | API Tokens: 200 ✅ | v1/models: 401 ✅ (需认证)
- gpt-5.4 completion (公网 → sgp2 → gz → metapi → OpenAI): "OK" ✅

### 服务重编排计划（2026-06-10）

> 详细方案: `C:\Users\Ding\docs\service-reshuffle-plan.md`

**目标：** gz ↔ sgp1 服务互换 + Docker 统一管理

**推荐方案 (A+C)：**
1. metapi + CPA → sgp1（全新 Docker image，保留 hub.db 统计数据和下游 key）
2. OpenClaw → gz
3. sgp2 入口不变，upstream 从 gz 改为 sgp1
4. API 延迟: 87ms → 3ms (-97%)

**Docker 化路径：**
- 立即: metapi (已 Docker) + CPA Docker 化
- 短期: OpenClaw Docker 化
- 长期: 每台服务器 docker-compose.yml 声明式管理

**HK VPS 状态：** 已初始化，Tailscale + UFW + fail2ban，备用入口候选

**延迟矩阵（2026-06-10 实测）：**

| 源→目标 | sgp1 | sgp2 | gz | hk |
|---------|------|------|-----|-----|
| sgp2 | 3ms | - | 87ms | 0.5ms |
| hk | 22ms | 0.5ms | 82ms | - |

### 性能指标（2026-03-12 最新）

| 资源 | 原始 | gzip | TTFB |
|------|------|------|------|
| HTML | 8.5KB | 2.5KB | 0.22s |
| CSS | 18.7KB | 4.1KB | 0.22s |
| JS | 12.8KB | 3.7KB | 0.24s |
| favicon | 0.5KB | - | 0.23s |
| **总计** | **40.5KB** | **~10.8KB** | |

- gzip: 全类型已启用 (nginx.conf gzip_types 取消注释)
- 缓存: 静态资源 7 天 `Cache-Control: public, immutable`
- 安全头: HSTS/nosniff/DENY/no-referrer ✅
- SEO: robots.txt + sitemap.xml ✅, OG 标签 ✅
- A11y: lang="zh-CN", viewport, 单 h1, aria-label 覆盖全 ✅

## 代码健康审查（2026-03-12 更新）

| 模块            | 完成度  | 说明                                      |
|----------------|---------|------------------------------------------|
| `:app`         | 55% M1  | Navigation (Profiles→Editor→Session→Settings), Koin DI, 3 screens + 3 ViewModels |
| `:core:model`  | 60%     | SshProfile（authType/keyRef/hostKeyPolicy/keepalive）, ChatMessage, enums |
| `:core:ssh`    | 接口    | SshExecRunner + ExecEvent sealed class; 零实现; SSH lib = JSch mwiede fork |
| `:core:storage`| 骨架    | Room DB (AppDatabase + SshProfileEntity + DAO), DataStore (AppPreferences), ProfileStore interface + RoomProfileStore |
| `:core:ui`     | MVP     | Theme + ChatBubble + ChatInput + AnsiStripper; enableEdgeToEdge; 无 accompanist |

**已修复**：
- [x] accompanist/systemuicontroller deprecated → 迁移到 `enableEdgeToEdge()` API
- [x] 测试覆盖：23 单测 (14 core:model + 9 core:ui)，junit4/coroutines-test/turbine
- [x] SSH 库选型：JSch mwiede fork (`com.github.mwiede:jsch`)，BSD license
- [x] Markdown 库选型：richtext-commonmark（当前），IntelliJ Markdown（备选）
- [x] CI/CD：GitHub Actions CI workflow (.github/workflows/ci.yml)
- [x] 导航骨架：Jetpack Navigation Compose + 4 routes (Profiles/ProfileEditor/Session/Settings)
- [x] Koin DI：Application 类 + AppModule (Room/DataStore) + ViewModelModule (3 VMs)
- [x] Room 数据库：AppDatabase v1, SshProfileEntity, SshProfileDao, Mappers
- [x] DataStore：AppPreferences (theme/defaultShell/lastProfileId)

**已知待处理**：
- [ ] RichText 库仍为 alpha（`1.0.0-alpha03`），生产风险
- [ ] spec 部分 TODO 待收口（见 `05-open-questions.md`）
- [ ] SSH exec 实现（M2-M3）
- [ ] ProfileEditorVM 尚未连接 Room 存储
- [ ] ProfilesVM 仍使用 in-memory mock 数据

## 下一步（按顺序）

1. M1：将 ProfilesVM/ProfileEditorVM 连接 Room 存储（真实 CRUD）
2. M1：补全代码块折叠/复制功能
3. M2：SSH exec 接入 JSch mwiede，实现真实流式输出
4. M2：host key 验证 UX（Fingerprint Confirm 屏幕）
5. M3：渲染管线优化（大输出截断、语法高亮）
6. M3：选定 SSH 库，接入 SSH exec channel，并完成 host key 流程
