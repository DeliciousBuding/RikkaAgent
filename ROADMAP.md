# ROADMAP — rikka-agent

> 目标：做一个“聊天式的 SSH 命令执行器”，在手机端以漂亮的消息渲染/动画呈现命令与输出（Mode A：非交互 exec channel），替代传统丑终端体验。
>
> 说明：本项目 Roadmap 与运行态决策不再写入 `C:\Users\Ding\ROADMAP.md` / `C:\Users\Ding\ROADMAP_STATE.md`，避免污染共享任务清单。

## 入口

- Spec 索引：`docs/spec/00-index.md`
- 总体计划书（较早版本）：`docs/plan.md`
- 安全/威胁模型与服务端加固：
  - `docs/threat-model.md`
  - `docs/server-hardening.md`
- Repo 代理约束：`AGENTS.md`

## 里程碑

### M0 — 规范冻结（Docs-First）

- [x] 完成开源仓库骨架（Apache-2.0 + 贡献规范 + 安全文档）
- [x] 完成分版块 spec（UX/视觉/动效/渲染/组件/架构/安全/环境/测试/发布）
- [ ] 规范冻结检查：spec 中对“必须/应该/可以”的用词统一；把所有“TODO/待定”集中到一个清单（避免散落）

### M1 — Android 工程骨架（可运行、可预览、无 SSH）

- [ ] 开发环境准备文档：
  - Android Studio 是否必需（建议：是，至少用于首次导入/同步/模拟器）
  - JDK/Gradle/AGP 版本要求
  - Emulator/真机调试最小步骤
- [ ] 创建 Android 工程（Kotlin + Jetpack Compose + Material3）
- [ ] Build variants：`debug` / `release`（开发与生产隔离）
- [ ] 基础导航与页面壳：
  - `Chat`（主）
  - `Connections`（主机/账户/密钥管理）
  - `Settings`（渲染/隐私/安全开关）
- [ ] UI 骨架实现（假数据）：消息气泡、代码块、复制按钮、加载态 shimmer、列表动画
- [ ] 基线测试：至少能 `./gradlew test` + `./gradlew assembleDebug`

### M2 — 渲染管线（Markdown/代码块/复制/流式）

- [ ] Markdown 渲染 v1：段落/标题/列表/引用/链接/行内代码
- [ ] 代码块组件 v1：
  - 复制、折叠、换行/横向滚动切换
  - 行号（可选）
- [ ] 流式渲染策略落地：
  - 50–100ms 批处理刷新
  - 避免每个 chunk 触发全量 Markdown 重解析（见 `docs/spec/23-rendering.md`）
- [ ] 可选：Mermaid 渲染（WebView/JS bridge/高度缓存），严格按 spec 的安全边界

### M3 — SSH 引擎（Mode A：exec channel）

- [ ] 依赖选型与 ADR：
  - SSH 库（Android 可用性、体积、许可证、密钥算法支持）
  - Known-hosts/host key 校验实现策略
- [ ] 连接模型：
  - Profile（host/port/user/auth）
  - Key（Keystore/加密存储/导入导出策略）
- [ ] 执行模型：
  - 单次 command exec（stdout/stderr 分流）
  - 超时/取消/重试策略
  - 并发与队列（同一会话串行；多主机并行可选）
- [ ] 安全交互：
  - 首次连接的 host key 提示与存储
  - Host key mismatch 阻断（默认禁止继续）

### M4 — Codex 接入（通过 SSH 驱动远端）

> 说明：本项目不在手机端直接“执行模型”，而是把 Codex 运行在服务器侧；App 通过 SSH 执行远端命令并把输出渲染成消息。

- [ ] 约定远端命令协议：
  - “命令模板”（例如 `codex ...` 或自定义脚本）
  - 输出格式约定（纯文本/Markdown/JSON 包裹）
- [ ] “一键运行”动作：
  - 复制/重跑
  - 导出对话（含敏感信息清理）
- [ ] 延迟优化（优先级从高到低）：
  - SSH 复用与 keepalive
  - 流式读取与 UI 批量刷新
  - 本地渲染缓存（代码块高亮/mermaid 高度）

### M5 — 开源发布质量

- [ ] 完整的服务器指南（面向新手 + 安全默认）：
  - 创建低权限用户
  - `sshd_config` 基线建议（只给建议，不强制替用户改系统）
  - 防火墙与暴露面说明
- [ ] CI（GitHub Actions）：
  - lint/test/build
  - 生成 APK artifact（可选）
- [ ] 隐私审计清单：
  - 禁止日志写入密钥/命令输出（默认）
  - 崩溃报告默认关闭或可选
- [ ] Release checklist（见 `docs/spec/80-release.md`）

## 当前优先级（下一步最优动作）

1. 补齐/冻结 spec 中“待定项”集中清单（M0）
2. 然后进入 M1：创建 Android 工程骨架（不接 SSH，先把 UI 跑起来）

