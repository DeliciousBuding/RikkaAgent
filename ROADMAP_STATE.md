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

- 已完成：开源仓库骨架 + docs/spec 深化（见 `docs/spec/00-index.md`）
- 已完成：参考项目 Android UI 的"只读观察笔记"（见 `docs/research/rikkahub-android-ui-study.md`）
- 已补充：v1 远程执行规范（`docs/spec/33-remote-exec.md`）
- 已补充：ACP（v2+）单独规范（`docs/spec/36-acp.md`）
- 已完成（2026-03-12）：修复 Android 构建依赖链问题并成功出包 `app-dev-debug.apk`
  - Kotlin 1.9.24 → 2.1.0（修复 metadata 版本不兼容）
  - AGP 8.5.2 → 8.8.0，Gradle 8.7 → 8.10.2（支持 compileSdk 35）
  - Compose 编译器从 `composeOptions` 迁移到 `kotlin-compose` 插件（Kotlin 2.0+ 机制）
  - `richtext-markdown` → `richtext-commonmark`（`Markdown(String)` API 迁移）
  - 排除 `org.jetbrains.compose.*` 传递依赖避免版本冲突
  - `themes.xml` 修复 `?android:colorTransparent` → `@android:color/transparent`
- 构建状态：✅ BUILD SUCCESSFUL（35s），唯一警告为 accompanist/systemuicontroller deprecated

## VectorControl Gateway Portal 基础设施

> 独立项目：`D:\Code\Projects\vectorcontrol-gateway-portal`（纯静态站 HTML/CSS/JS，无构建系统）

### 部署架构

```
用户 → DNS (vectorcontrol.tech / www.vectorcontrol.tech)
     → A 记录 20.195.40.11
     → sgp2 (Azure Singapore, 20.195.40.11)
       ├─ nginx 443 → 静态门户 /var/www/vectorcontrol-gateway-portal/
       ├─ /management → proxy_pass gz:8317/management.html (CLIProxyAPI 管理界面)
       ├─ /fund/ → alias /var/www/vectorcontrol-fund/ (React SPA 投研系统)
       ├─ /playground/ → proxy_pass gz:4001/ (metapi Playground)
       ├─ /api/, /v1/ → proxy_pass gz:4001 (metapi OpenAI API)
       └─ SSL cert: /etc/letsencrypt/live/www.vectorcontrol.tech/ (certbot + nginx authenticator)

gz (Alibaba Cloud, 8.163.12.208 / Tailscale 100.96.101.24)
  ├─ metapi Docker (--network host, port 4001) = OpenAI 兼容 API 网关
  ├─ CLIProxyAPI (binary, port 8317) = API 密钥管理 + 路由配置
  ├─ nginx 443 → /playground/ proxy to metapi (仅内部, 阿里云无备案拦截 443)
  └─ Tailscale 组网: gz ↔ sgp1 ↔ sgp2 ↔ 本机
```

### 关键事实

| 条目 | 值 |
|------|-----|
| 公网入口 | https://www.vectorcontrol.tech |
| DNS A 记录 | 20.195.40.11 (sgp2 Azure) |
| 静态文件位置 | sgp2:/var/www/vectorcontrol-gateway-portal/ |
| 后端 | gz:4001 metapi (Docker, `1467078763/metapi:latest`) |
| SSL 覆盖域名 | vectorcontrol.tech + www.vectorcontrol.tech |
| SSL 续期 | certbot.timer 自动，nginx authenticator，30 天提前续期，到期 2026-06-10 |
| 阿里云限制 | gz 443 端口被拦截（无 ICP 备案），公网流量必须经 sgp2 |
| 部署方式 | SCP 到 sgp2 via Tailscale IP (root@100.82.99.84) |
| 主题 | 翡翠绿深色极简 (accent #10b981)，Hero 辐射光晕 + 玻璃拟态面板 |
| 配置归档 | `deploy/sgp2.vectorcontrol.tech.conf` (sgp2 专用) |
| 快照脚本 | `tools/measure-chain.ps1` → `artifacts/latency-snapshot.json` |
| GitHub 仓库 | DeliciousBuding/vectorcontrol-gateway-portal (private) |

### 门户进展（2026-03-12）

- ✅ 金色 → 翡翠绿主题替换
- ✅ 探活增强（probePlayground + runLiveProbes 优雅降级）
- ✅ 去掉 section 大卡片，极简布局重设计
- ✅ 安全头（HSTS/nosniff/DENY/no-referrer）
- ✅ 部署到 sgp2 并验证全端点
- ✅ 裸域 SSL 证书扩展（vectorcontrol.tech + www 双覆盖，到期 2026-06-10）
- ✅ 高级视觉升级：Hero 辐射光晕、玻璃拟态面板、卡片 hover 交互、渐变标题、品牌脉冲动画
- ✅ 私有 GitHub 仓库创建并推送 (DeliciousBuding/vectorcontrol-gateway-portal)
- ✅ 裸域 → www 301 重定向
- ✅ Fund 前端部署到 /fund/（React SPA, base: '/fund/'）
- ✅ Management 路由代理到 gz:8317 的 CLIProxyAPI
- ✅ 门户重构：移除建议型文案，新增 Services 服务入口卡片区（Management / Fund / Playground / API）
- ✅ 所有子站点可访问验证（/, /management, /fund/, /playground/）

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
5. M3：选定 SSH 库，接入 SSH exec channel，并完成 host key 流程
