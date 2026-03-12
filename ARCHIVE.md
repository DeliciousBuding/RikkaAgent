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
- ChatBubble（用户气泡 + 助手 CodeCard, 流式动画, 错误状态, 时间戳, 复制, 触觉反馈）
- CodeCard（可折叠代码输出, 复制, 语言标签, 水平滚动, 自动折叠 15 行）
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

---

## VectorControl Portal 历史

### 门户视觉 & 功能（2026-03-12 完成）

- ✅ v1→v10 翡翠绿深色主题迭代
- ✅ Hero 辐射光晕、玻璃拟态面板、卡片 hover 交互
- ✅ 极简布局重设计、品牌脉冲动画、滚动入场动画
- ✅ 实时服务状态点（探活 HEAD + 优雅降级）
- ✅ Services 卡片区 (Management / Fund / Playground / API)
- ✅ OG 社交标签、Footer 版权年份自动更新
- ✅ 裸域 → www 301 重定向
- ✅ Fund 前端部署 /fund/（React SPA）
- ✅ Management 路由代理到 CPA
- ✅ 安全头（HSTS/nosniff/DENY/no-referrer）
- ✅ SSL 双域名覆盖
- ✅ 静态资源 7 天缓存 + gzip 全类型
- ✅ SEO: robots.txt + sitemap.xml + OG 标签
- ✅ 项目重命名 vectorcontrol-gateway-portal → VectorControl-Portal
- ✅ CLIProxyAPI 升级 6.8.50 → 6.8.51、管理密码重置
- ✅ 服务端自动采集: gz cron measure-snapshot.py → /api/snapshot
- ✅ 前端实时数据拉取 + 骨架屏 + 自刷新倒计时

### 基础设施深度审计（2026-06-10 完成）

**修复项：**
- ✅ metapi 503 大规模故障：`token_routes` + `route_channels` 表清空 → 从 `/root/hub.db.bak-pre-host-network` 恢复 17 routes + 17 channels，重新激活账户
- ✅ 前端 XSS 加固：6 处 innerHTML 未调用 `escapeHtml()` → 全部修复 (commit `1ce4f92`)
- ✅ sgp1 rp_filter 从 loose (2) → strict (1)：`/etc/sysctl.d/99-rp-filter.conf`

**审计确认（安全状态良好）：**
- ✅ gz: fail2ban, unattended-upgrades, SSH hardening, Docker restart=unless-stopped, CPA restart=always
- ✅ gz: 8317/8443 公网不可达（UFW deny + ISP 阻断）
- ✅ sgp2: fail2ban, unattended-upgrades, UFW DROP, certbot.timer active
- ✅ sgp2: 仅 22/80/443 + Tailscale SOCKS 1080

**端到端验证（2026-06-10）：**
- Portal: 200 ✅ | API Snapshot: 200 ✅ | API Tokens: 200 ✅ | v1/models: 401 ✅
- gpt-5.4 completion (公网 → sgp2 → gz → metapi → OpenAI): "OK" ✅

### HK VPS 初始化（2026-06-10 完成）

- ✅ Root SSH 访问启用
- ✅ 系统包更新
- ✅ SSH hardening (99-hardening.conf)
- ✅ UFW: 80/443/41641 (SSH 仅 Tailscale)
- ✅ fail2ban + unattended-upgrades
- ✅ Tailscale 安装并认证 (100.79.22.119)
- ✅ .ssh/config 别名 `hk` 配置

### 关注项（低风险，2026-06-10 记录）

- ⚠️ sgp1: 内存压力 (1.0G/1.9G + 297M swap, disk 55%)
- ⚠️ sgp2: 内存 469M/847M + 69M swap
- ⚠️ sgp1: Docker HTTPS (443) 上的 VectorControl 部署无独立 Let's Encrypt
