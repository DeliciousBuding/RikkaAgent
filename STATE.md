# STATE — rikka-agent 基础事实

> 用途：集中记录项目和基础设施的**静态事实**（地址、配置、架构、决策）。不含进度和历史。
>
> 进度 → `ROADMAP.md` ｜ 历史 → `ARCHIVE.md`

---

## 1. rikka-agent 关键决策（SSOT）

| 决策项 | 值 |
|--------|-----|
| 产品形态 | Android App（Kotlin + Jetpack Compose） |
| 交互模式 | Mode A（非交互 exec channel；不做 PTY/ANSI 渲染） |
| 连接方式 | Android 原生 SSH 直连；不引入"服务端 HTTP 远程执行中转" |
| 安全原则 | Known-hosts 默认开启；host key mismatch 默认阻断；密钥加密存储 |
| 开源协议 | Apache-2.0 |
| Clean-room | 参考 UI "感觉"，不拷贝参考项目代码 |
| SSH 库 | sshj 0.39.0 (BSD-2-Clause)，支持 Ed25519/RSA/ECDSA |
| 私钥格式 | OpenSSH PEM + PuTTY `.ppk` |
| Markdown 解析 | commonmark-java 0.22.0 (BSD-2-Clause) + GFM tables/strikethrough |
| 密钥存储 | EncryptedFile (AES-256-GCM via AndroidX Security Crypto) |
| Codex 集成 | `codex exec --full-auto` via SSH (exec channel) |
| 输出策略 | 截断显示 + 消息级完整查看/完整分享 |
| 国际化 | 中英双语 (values/strings.xml + values-zh/strings.xml)，中文优先 |
| 持久化 | Room DB v3 (聊天/配置/Codex字段) + DataStore (偏好) |
| DI | Koin |
| CI | GitHub Actions: test + lint + assemble + artifacts + step summary |
| Spec 索引 | `docs/spec/00-index.md` |

---

## 2. 代码模块状态

| 模块 | 完成度 | 说明 |
|------|--------|------|
| `:app` | 高完成度 | Navigation、ViewModel、Codex 模式、完整输出展开/分享、i18n |
| `:core:model` | 稳定 | SshProfile、Chat models、状态枚举 |
| `:core:ssh` | 稳定 | SSH exec、JSONL parser、HostKey 验证、`.ppk` 支持 |
| `:core:storage` | 稳定 | Room v3、DataStore、Profile/Chat 持久化 |
| `:core:ui` | 高完成度 | Theme、ChatBubble、CodeCard、MarkdownText、ChatInput |
