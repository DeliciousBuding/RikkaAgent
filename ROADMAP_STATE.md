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
| ProfilesScreen | `app/.../ui/screen/ProfilesScreen.kt` | ✅ 完成（LargeTopAppBar, 滑动删除, 空状态, FAB） |
| ProfileEditorScreen | `app/.../ui/screen/ProfileEditorScreen.kt` | ✅ 完成（Card 分组, 端口校验 1-65535, SectionLabel） |
| ChatScreen | `app/.../ui/screen/ChatScreen.kt` | ✅ 完成（Scaffold, 自动滚动, 进度条, 取消按钮, Host Key 对话框, 空状态） |
| SettingsScreen | `app/.../ui/screen/SettingsScreen.kt` | ✅ 完成（主题选择器, Shell 选择器） |

**已完成的组件：**
- ChatBubble（Markdown 渲染, 复制按钮, 流式动画, 错误状态红色容器, 时间戳, 流式打点动画）
- ChatInput（多行输入, 回车发送）
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

**待完成：**
- 私钥文件选择器（keyRef 路径管理）
- SSH 连接复用（当前每次命令创建新连接）
- 聊天历史加载（恢复已保存的历史记录到 UI）

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
