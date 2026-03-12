# ROADMAP — rikka-agent

> 目标：聊天式 SSH 命令执行器，手机端以漂亮的消息渲染呈现命令与输出（Mode A：非交互 exec channel）。
>
> 事实/配置 → `STATE.md` ｜ 历史 → `ARCHIVE.md`

## 入口

- Spec 索引：`docs/spec/00-index.md`
- 总体计划书：`docs/plan.md`
- 安全/威胁模型：`docs/threat-model.md`, `docs/server-hardening.md`
- Repo 代理约束：`AGENTS.md`

---

## 里程碑进度总览

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| M0 规范冻结 | ✅ 基本完成 | 剩余：spec 用词统一检查 |
| M1 UI 骨架 | ✅ 主要完成 | 8 屏 + ProfilesVM/EditorVM/ChatVM 全部连接 Room |
| M2 渲染管线 | ✅ 主要完成 | Markdown v1 + 流式渲染优化 + CodeCard；Mermaid 可选 |
| M3 SSH 引擎 | ✅ 主要完成 | sshj exec + 认证 + host key + 会话管理 + 密钥生成 + 加密存储 |
| M4 Codex 接入 | ✅ 主要完成 | JSONL 解析 + API Key 管理 + exec --json + profile 开关 + Markdown 渲染 |
| M5 开源发布 | ⬜ 未开始 | CI workflow 已有 |
| i18n 中英双语 | ✅ 完成 | 全面国际化：120+ 字符串资源，中文优先，覆盖全部 UI 屏幕 + ViewModel |

---

## M1 待完成

- [x] ProfilesVM / ProfileEditorVM 连接 Room 存储（真实 CRUD）
- [x] ChatViewModel 连接 Room（消息持久化 + 会话管理）
- [ ] 更多密钥格式支持（PuTTY .ppk）
- [ ] 规范冻结检查：spec 用词统一 + TODO 集中

## M2 待完成

- [x] Markdown 渲染 v1（段落/标题/列表/引用/链接/行内代码/代码块/表格/删除线）
- [x] CodeCard 基础组件（折叠/展开/复制/语言标签）
- [x] 流式渲染策略（流式阶段用 CodeCard，终态切 MarkdownText 单次解析 + remember 缓存）
- [ ] 可选：Mermaid 渲染（WebView/JS bridge）

## M4 待完成

- [x] Profile 增加 codexMode / codexWorkDir 字段
- [x] ChatViewModel Codex exec 命令包装 (`codex exec --full-auto`)
- [x] ProfileEditorScreen Codex 设置 UI（开关 + 工作目录）
- [x] Codex 输出 Markdown 渲染（复用 M2 MarkdownText）
- [x] "一键运行"动作（复制/重跑/导出）
- [x] 远端命令协议约定（`--json` JSONL 解析 + JsonlParser + JsonlLineBuffer）
- [x] 延迟优化（SSH 连接池复用 + 流式 JSONL 缓冲 + remember 渲染缓存）
- [x] Codex API Key 管理（profile 级别配置，密码遮蔽 UI，OPENAI_API_KEY 环境变量注入）

## M5 待完成

- [ ] 服务器指南（低权限用户/sshd_config/防火墙）
- [ ] 隐私审计清单
- [ ] Release checklist

---

## VectorControl 基础设施活跃工作

### 服务重编排计划（进行中）

> 详细方案: `docs/network.md` (v5)

**目标：** metapi+CPA → sgp1 (Docker) + HK 中国边缘入口 + OpenClaw → gz

| 步骤 | 内容 | 状态 |
|------|------|------|
| 1 | CPA Docker 化 (Dockerfile + 去 SOCKS proxy) | ⬜ |
| 2 | 数据备份 (hub.db + CPA config 从 gz 导出) | ✅ (2026-06-10 本地备份) |
| 3 | metapi + CPA Docker 部署到 sgp1 | ⬜ |
| 4 | sgp2 nginx upstream 从 gz → sgp1 切流 | ⬜ |
| 5 | 端到端验证 + gz 旧服务停止 | ⬜ |
| 6 | HK 边缘: nginx + certbot + 中国入口 | ⬜ (HK VPS 已就绪✅) |
| 7 | OpenClaw 迁移到 gz | ⬜ |

**预期收益：** 国际 API 延迟 350ms → 2ms (-99.4%)，中国 ~370ms → ~55ms (-85%)

### 关注项

- ⚠️ sgp1 内存压力 (1.0G/1.9G + 297M swap)
- ⚠️ sgp2 内存 469M/847M + 69M swap
- ⚠️ SSL 证书到期 2026-06-10（certbot.timer 自动续期中）

---

## 下一步最优动作

1. **执行服务重编排** — 按 service-reshuffle-plan.md 逐步实施
2. **M1 收尾** — ProfilesVM/EditorVM 连接 Room（真实 CRUD）
3. **M2 启动** — 流式渲染策略落地

