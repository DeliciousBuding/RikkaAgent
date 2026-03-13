# 计划书：rikka-agent（Android / Compose）模式A「原生 SSH 命令面板」+ 聊天式渲染

> 放置位置：`D:\Code\Projects\rikka-agent\docs\plan.md`  
> 参考 UI：`D:\Code\Projects\rikkahub\app`（Jetpack Compose 聊天界面与富文本渲染）  
> 约束：客户端原生 SSH（不新增服务端 HTTP 执行入口），以“好看 + 低延迟 + 不扩大攻击面”为第一优先级。

---

## 1. 背景与目标

用户当前在手机上通过 Termux + `ssh`（ssh key 配对）操作服务器，功能可用但 UI “太丑”。目标是在 Android 端实现一个“命令执行 → 输出流式展示 → 可复制/可重跑”的聊天式界面，复用 RikkaHub 的 Markdown/代码块渲染能力，让运维/脚本执行体验更好看、更顺滑。

核心目标：
1. **原生 SSH**：Android 端直接连接现有 `sshd`（key auth），不新增服务端 HTTP relay。
2. **模式 A（非交互 shell）**：用 `exec` channel 执行命令，流式展示 stdout/stderr。
3. **低延迟**：长连接复用 + 流式增量渲染 + UI 节流。
4. **安全优先**：私钥加密存储、严格 host key 校验、最小权限建议、输出脱敏提示。

---

## 2. 范围与非目标

范围（本期做）：
1. SSH Profile 管理（多主机或至少单主机）：
   - `host` / `port` / `username`
   - 私钥导入（PEM/OpenSSH），可选 passphrase
   - known hosts：首次指纹确认、变更告警/阻断
2. 命令执行（非交互）：
   - `exec` 单条命令
   - 流式读取 stdout/stderr（合并节流）
   - 取消/超时
3. UI（聊天式）：
   - 命令卡片：编辑/复制/重跑/收藏
   - 输出卡片：复制/折叠/搜索/完整输出/导出（带风险提示）
   - 错误态：认证失败、指纹变更、网络断开、超时

非目标（暂不做）：
1. 交互式终端（PTY、ANSI 光标控制、vim/top 等）。
2. 自动化“AI 自主执行命令”的 agent（先把执行与展示打稳）。
3. 服务端新增端口（如 mosh / 自建 HTTP 执行服务）。

---

## 3. 安全模型与设计原则

前提：服务器“可能不太安全”。因此策略是**不新增高危入口**，只使用现有 sshd。

客户端安全：
1. 私钥存储：
   - 使用 Android Keystore 保护的加密存储（EncryptedFile/EncryptedSharedPreferences）。
   - passphrase 支持输入；是否保存由用户决定，保存必须加密。
2. Host key 校验：
   - 首次连接：展示指纹（SHA256）让用户确认并写入 known hosts。
   - 指纹变更：默认阻断并明确提示风险，允许手动更新（需二次确认）。
3. 日志与持久化：
   - 禁止在日志/崩溃信息中写入私钥、passphrase。
   - 命令输出默认仅本地保存，不自动上传/同步；导出前提示风险。

服务端加固建议（不属于客户端代码改动，但作为交付建议写入文档）：
1. `PasswordAuthentication no`
2. `PermitRootLogin no`
3. 为 app 建独立低权限用户
4. `authorized_keys` 限制：`no-port-forwarding` / `no-agent-forwarding` / `no-X11-forwarding` 等

---

## 4. 低延迟方案

1. **长连接复用**：一个 SSH session 持久化，用于多次 `exec`，避免每条命令重新握手。
2. **流式输出节流**：stdout/stderr 以 chunk 聚合（例如 50–100ms 合并一次）再触发 Compose 更新，避免重组过频。
3. **大输出策略**：
   - 超过阈值后截断（保留尾部 + “加载更多/导出”）。
   - 避免把 MB 级文本直接塞进单个 Composable 导致卡顿/内存峰值。
4. **断线策略**：
   - 显示“已断开”，提供“一键重连”和“重跑上一条命令”。

---

## 5. UI/交互设计（模式 A）

推荐 UI 形态：
1. 以“会话”为单位：每个 SSH Profile 对应一个对话（标题显示 `user@host`），消息流里是命令与输出。

消息语法（建议）：
1. 命令消息：以 `$ ` 前缀展示，形成统一视觉。
2. 输出消息：
   - stdout：Markdown code fence
   - stderr：单独 code fence + “stderr”标签/颜色区分

操作：
1. 命令卡片：编辑、复制、重跑、收藏。
2. 输出卡片：复制、折叠、搜索、导出（提示风险）。

---

## 6. 技术落点（参考 RikkaHub）

参考代码入口（只读勘探用）：
1. 导航壳层：`D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\RouteActivity.kt`
2. 聊天页：`D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\pages\chat\ChatPage.kt`
3. 富文本渲染：
   - Markdown：`D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\richtext\Markdown.kt`
   - 代码块：`D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\richtext\HighlightCodeBlock.kt`

后续实现时，我们会新增：
1. `ssh/` 模块（或 package）：
   - Profile、known hosts、key storage、connection manager、exec runner
2. `ui/pages/ssh/`：
   - Profile 管理页、命令面板页（复用 chat-style 布局）

---

## 7. 里程碑与验收

Phase 0（只读勘探，不写业务代码）：
1. 找到 RikkaHub 消息模型与消息列表数据流（VM → UI）。
2. 输出“落点文档”：我们要接入的 ViewModel、消息结构、持久化位置、推荐 package 路径。

验收：落点文档可指导后续实现，不靠猜。

Phase 1（Profile + known hosts，不执行命令）：
1. Profile CRUD、私钥导入、known hosts 写入（首次确认占位 UI）。

验收：能保存 profile，能显示指纹确认流程（无需连真实服务器也可走到 UI 完整）。

Phase 2（单条命令执行，非流式）：
1. connect + auth + exec，命令结束后显示 stdout/stderr。

验收：`uname -a`、`ls`、`whoami` 等命令可稳定执行并显示。

Phase 3（流式输出 + 取消）：
1. 流式追加输出到同一消息。
2. 取消执行（关闭 channel）。

验收：长输出命令可实时滚动显示并可停止，UI 不明显卡顿。

Phase 4（体验打磨）：
1. 历史/收藏/重跑、错误分类提示、导出提示与脱敏提示。

---

## 8. 风险与对策

1. SSH 库选择/兼容性：
   - 对策：封装传输层接口，避免 UI/业务强绑定具体库，保留替换空间。
2. 输出敏感信息泄露：
   - 对策：默认不上传；导出前提示；可选简单检测（疑似密钥片段时提示）。
3. 用户误执行危险命令：
   - 对策：先不做“AI 自动执行”；可选对高危模式命令二次确认（后续再加）。

---

## 9. 下一步（等待确认后执行）

1. Phase 0：对 `D:\Code\Projects\rikkahub` 做只读勘探，产出落点文档到 `D:\Code\Projects\rikka-agent\docs\`.
2. 明确一个产品决策：SSH Profile 先支持单主机还是多主机（不影响底层，但影响信息架构与页面）。

## 10. Spec 文档入口

完整 spec（分版块）位于：

- `D:\Code\Projects\rikka-agent\docs\spec\00-index.md`
