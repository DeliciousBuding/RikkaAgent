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
| M1 UI 骨架 | 🔶 55% | 6 屏完成；ProfilesVM/EditorVM 未连接 Room |
| M2 渲染管线 | ⬜ 未开始 | CodeCard 基础完成，流式渲染待做 |
| M3 SSH 引擎 | ✅ 主要完成 | sshj exec + 认证 + host key + 会话管理 + 密钥生成 |
| M4 Codex 接入 | 🔶 40% | 基础集成完成：profile Codex 开关 + 工作目录 + exec 命令包装 |
| M5 开源发布 | ⬜ 未开始 | CI workflow 已有 |

---

## M1 待完成

- [ ] ProfilesVM / ProfileEditorVM 连接 Room 存储（真实 CRUD）
- [ ] 更多密钥格式支持（PuTTY .ppk）
- [ ] 规范冻结检查：spec 用词统一 + TODO 集中

## M2 待完成

- [ ] Markdown 渲染 v1（段落/标题/列表/引用/链接/行内代码）
- [ ] 流式渲染策略（50-100ms 批处理，避免全量重解析）
- [ ] 可选：Mermaid 渲染（WebView/JS bridge）

## M4 待完成

- [x] Profile 增加 codexMode / codexWorkDir 字段
- [x] ChatViewModel Codex exec 命令包装 (`codex exec --full-auto`)
- [x] ProfileEditorScreen Codex 设置 UI（开关 + 工作目录）
- [ ] 远端命令协议约定（`--json` JSONL 解析）
- [ ] Codex 输出 Markdown 渲染
- [x] "一键运行"动作（复制/重跑/导出）
- [ ] 延迟优化（SSH 复用/流式/渲染缓存）

## M5 待完成

- [ ] 服务器指南（低权限用户/sshd_config/防火墙）
- [ ] 隐私审计清单
- [ ] Release checklist

---

## VectorControl 基础设施活跃工作

### 服务重编排计划（进行中）

> 详细方案: `C:\Users\Ding\docs\service-reshuffle-plan.md`

**目标：** gz ↔ sgp1 服务互换 + Docker 统一管理

| 步骤 | 内容 | 状态 |
|------|------|------|
| 1 | metapi + CPA → sgp1（全新 Docker image，保留 hub.db 统计+key） | ⬜ |
| 2 | OpenClaw → gz | ⬜ |
| 3 | sgp2 upstream 从 gz 改为 sgp1 | ⬜ |
| 4 | Docker 化 CPA + OpenClaw | ⬜ |
| 5 | 每台服务器 docker-compose.yml 声明式管理 | ⬜ |

**预期收益：** API 延迟 87ms → 2ms (-97%)

### 关注项

- ⚠️ sgp1 内存压力 (1.0G/1.9G + 297M swap)
- ⚠️ sgp2 内存 469M/847M + 69M swap
- ⚠️ SSL 证书到期 2026-06-10（certbot.timer 自动续期中）

---

## 下一步最优动作

1. **执行服务重编排** — 按 service-reshuffle-plan.md 逐步实施
2. **M1 收尾** — ProfilesVM/EditorVM 连接 Room（真实 CRUD）
3. **M2 启动** — 流式渲染策略落地

