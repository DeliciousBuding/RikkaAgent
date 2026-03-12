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
       ├─ nginx 443 → 静态文件 /var/www/vectorcontrol-gateway-portal/
       ├─ /playground/ → proxy_pass http://100.96.101.24:4001/ (gz via Tailscale)
       ├─ /api/, /v1/ → proxy_pass http://100.96.101.24:4001 (gz via Tailscale)
       └─ SSL cert: /etc/letsencrypt/live/www.vectorcontrol.tech/ (certbot + nginx authenticator)

gz (Alibaba Cloud, 8.163.12.208 / Tailscale 100.96.101.24)
  ├─ metapi Docker (--network host, port 4001) = OpenAI 兼容 API 网关
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
| SSL 覆盖域名 | www.vectorcontrol.tech（裸域待扩展） |
| SSL 续期 | certbot.timer 自动，nginx authenticator，30 天提前续期 |
| 阿里云限制 | gz 443 端口被拦截（无 ICP 备案），公网流量必须经 sgp2 |
| 部署方式 | SCP 到 sgp2 via Tailscale IP (root@100.82.99.84) |
| 主题 | 翡翠绿深色极简 (accent #10b981) |
| 配置归档 | `deploy/sgp2.vectorcontrol.tech.conf` (sgp2 专用) |
| 快照脚本 | `tools/measure-chain.ps1` → `artifacts/latency-snapshot.json` |

### 门户进展（2026-03-12）

- ✅ 金色 → 翡翠绿主题替换
- ✅ 探活增强（probePlayground + runLiveProbes 优雅降级）
- ✅ 去掉 section 大卡片，极简布局重设计
- ✅ 安全头（HSTS/nosniff/DENY/no-referrer）
- ✅ 部署到 sgp2 并验证全端点
- ⚠️ 裸域 SSL 证书未覆盖
- ⚠️ Git remote 未配置（待创建 private 仓库）
- ⚠️ 门户视觉仍需进一步改进（对外展示美观性）

## 代码健康审查（2026-03-12）

| 模块            | 完成度  | 说明                                      |
|----------------|---------|------------------------------------------|
| `:app`         | 25% M1  | ChatScreen + ViewModel 骨架；缺导航、Profile 管理 |
| `:core:model`  | 30%     | SshProfile/ChatMessage 基础模型；缺 authType/keyRef/hostKeyPolicy 等 |
| `:core:ssh`    | 接口    | SshExecRunner + ExecEvent sealed class；零实现 |
| `:core:storage`| 接口    | ProfileStore 接口；缺 Room/Keystore/DataStore 实现 |
| `:core:ui`     | MVP     | Theme + ChatBubble + ChatInput + AnsiStripper；缺代码块折叠/语法高亮 |

**已知待处理**：
- [ ] accompanist/systemuicontroller deprecated → 迁移到 `WindowCompat` API
- [ ] 测试覆盖率 0%：无任何 test 文件，需添加 JUnit4/Mockk/Truth 依赖 + 创建 test 目录
- [ ] RichText 库仍为 alpha（`1.0.0-alpha03`），生产风险
- [ ] CI/CD 未搭建（缺 GitHub Actions）
- [ ] spec 未完成 M0 freeze（`05-open-questions.md` 中 SSH 库选型等 9 项未决定）

## 下一步（按顺序）

1. M0：把 spec 中所有 "TODO/待定" 收口成一个清单，并明确 v1/v2 边界
2. M1：补全导航（Profiles → Profile Editor → Session → Settings），添加假数据 Profile 管理
3. M1：添加测试基础设施（依赖 + 目录 + 核心模型单测）
4. M2：落地渲染管线与 streaming 机制
5. M3：选定 SSH 库，接入 SSH exec channel，并完成 host key 流程
