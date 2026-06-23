# RikkaAgent PTY 交互模式支持方案

> 状态：设计文档
> 日期：2026-06-23
> 前置阅读：`docs/spec/32-ssh.md`, `docs/spec/33-remote-exec.md`, `docs/spec/30-architecture.md`

---

## 1. 当前 Mode A（exec channel）的限制

### 1.1 现状

`SshjExecRunner` 使用 sshj 的 `Session.exec(command)` 打开 exec channel：

```
val session = client.startSession()
val cmd = session.exec(command)   // 单向：写入命令，读取 stdout/stderr
```

`ExecEvent` sealed class 定义了事件流：`StdoutChunk` / `StderrChunk` / `StructuredEvent` / `Exit` / `Canceled` / `Error`。

### 1.2 核心限制

| 限制 | 影响 | 用户感知 |
|------|------|---------|
| **无 PTY 分配** | 远程进程没有控制终端，`isatty()` 返回 false | `ls` 不带颜色，`git log` 不分页，进度条不刷新 |
| **无 stdin** | exec channel 一旦打开，无法向远程进程写入数据 | 无法交互式输入密码、无法使用 REPL（python/node）、无法 `git rebase -i` |
| **无终端尺寸协商** | 远程进程不知道终端的行列数 | `vim`/`htop` 等全屏 TUI 应用无法正常渲染 |
| **stdout/stderr 分离** | 两个独立 InputStream | 正确，但 PTY 模式下会合并为一个流 |
| **无 ANSI 解析** | 原始字节直接渲染为代码块 | 带颜色的输出显示为乱码转义序列 |
| **无信号转发** | 只能关闭 channel 来取消 | 无法发送 Ctrl+C、Ctrl+Z、SIGWINCH |

### 1.3 能力边界

Mode A 能做：运行一次性命令、获取输出、解析 JSONL（Codex exec）。
Mode A 不能做：交互式 shell、密码输入、全屏 TUI、需要 PTY 的程序（如 `script`、`expect`）。

---

## 2. PTY 模式技术方案

### 2.1 sshj API 映射

```kotlin
// exec 模式（当前）
val session = client.startSession()
val cmd = session.exec(command)
// → cmd.inputStream (stdout), cmd.errorStream (stderr)
// → cmd.join() 等待结束
// → cmd.exitStatus

// PTY 模式（新增）
val session = client.startSession()
session.allocatePTY("xterm-256color", cols, rows, 0, 0, PTYModes.EMPTY)
val shell = session.startShell()
// → shell.outputStream (写入 stdin)
// → shell.inputStream (读取 stdout+stderr 合并流)
// → shell.join() 等待结束
// → shell.exitStatus
// → session.sendWindowChange(cols, rows) // SIGWINCH
```

### 2.2 新接口设计

```kotlin
/**
 * PTY 交互式 shell runner。
 * 与 SshExecRunner 并列，共享认证/连接池基础设施。
 */
interface SshPtyRunner {
  fun open(profile: SshProfile, ptyConfig: PtyConfig): Flow<PtyEvent>
  fun sendInput(sessionId: String, data: ByteArray)
  fun resize(sessionId: String, cols: Int, rows: Int)
  fun close(sessionId: String)
}

data class PtyConfig(
  val term: String = "xterm-256color",
  val cols: Int = 80,
  val rows: Int = 24,
  val shellCommand: String? = null,  // null = login shell
)

sealed class PtyEvent {
  data class Output(val bytes: ByteArray) : PtyEvent()     // 合并的 stdout+stderr
  data class Exit(val code: Int?) : PtyEvent()
  data class Error(val category: String, val message: String) : PtyEvent()
  data object Closed : PtyEvent()
}
```

### 2.3 实现要点：`SshjPtyRunner`

```
SshjPtyRunner
  ├── 复用 SshjExecRunner 的认证逻辑（HostKeyCallback, PasswordProvider 等）
  ├── 复用 SshConnectionPool 的连接复用
  ├── 新增：PTY 分配 + shell 启动
  ├── 新增：双向流处理（output → 事件流，input ← UI 侧写入）
  └── 新增：窗口尺寸变更通知
```

关键差异：

| 维度 | exec 模式 | PTY 模式 |
|------|----------|---------|
| Channel 打开 | `session.exec(cmd)` | `session.allocatePTY()` + `session.startShell()` |
| stdin | 无 | 持续写入 `shell.outputStream` |
| stdout+stderr | 两个独立流 | 合并为一个流（PTY 特性） |
| 窗口变更 | 不支持 | `session.sendWindowChange(cols, rows)` |
| 退出检测 | `cmd.exitStatus` | `shell.exitStatus`（同） |
| 连接复用 | 每次 exec 新 channel | 同一 session 上可同时有 exec 和 shell channel |

### 2.4 新增文件结构

```
core/ssh/src/main/kotlin/io/rikka/agent/ssh/
  ├── SshInterfaces.kt          ← 新增 PtyConfig, PtyEvent, SshPtyRunner
  ├── SshjPtyRunner.kt          ← 新实现（核心）
  ├── SshjExecRunner.kt         ← 不修改
  └── SshExecRunnerFactory.kt   ← 不修改（PTY 用独立 factory）
```

---

## 3. 终端仿真需求

### 3.1 需要解析的内容

PTY 模式下，远程进程输出的是带 ANSI 转义码的字节流。必须解析才能正确显示。

#### P0 — 必须支持（最小可用）

| 功能 | ANSI 序列 | 说明 |
|------|----------|------|
| 文本输出 | 普通字符 + `\r` `\n` `\t` | 基础渲染 |
| SGR 颜色/样式 | `\e[...m` | 前景色、背景色、粗体、斜体、下划线 |
| 光标定位 | `\e[nA/B/C/D`, `\e[H`, `\e[n;mH` | 上下左右移动、绝对定位 |
| 行内擦除 | `\e[K` | 清除光标到行尾 |
| 回车覆盖 | `\r` | 进度条效果（`Downloading... 45%`） |

#### P1 — 应该支持（常用工具需要）

| 功能 | ANSI 序列 | 说明 |
|------|----------|------|
| 全屏擦除 | `\e[2J`, `\e[H` | 清屏 |
| 滚动区域 | `\e[r` | `less`/`man` 使用 |
| 备用屏幕 | `\e[?1049h/l` | `vim`/`htop`/`less` 进入/退出全屏模式 |
| 行擦除 | `\e[2K` | 清除整行 |
| 光标可见性 | `\e[?25h/l` | 隐藏/显示光标 |

#### P2 — 可以延后

| 功能 | 说明 |
|------|------|
| 鼠标追踪 | `\e[?1000h` — `mc`、`htop` 鼠标支持 |
| 256色/真彩色 | `\e[38;5;n`, `\e[38;2;r;g;b` |
| Unicode 宽字符 | CJK 字符宽度计算 |

### 3.2 实现方案选择

| 方案 | 优点 | 缺点 | 推荐 |
|------|------|------|------|
| **自研 VT100 解析器** | 完全控制，无外部依赖，可深度集成 Compose | 开发量大（VT100 规范复杂） | P0 阶段 |
| **WebView + xterm.js** | 成熟终端仿真，零开发成本 | WebView 开销大，与 Compose 集成差，安全面大 | 否 |
| **JVM 终端库（如 jediterm）** | 已有 VT100 实现 | 为 Swing 设计，需适配 Compose | 调研后决定 |

**推荐路径**：

1. **第一阶段**：自研最小 VT100 解析器，覆盖 P0 功能（SGR + 光标定位 + 行内擦除 + 回车覆盖）。
2. **第二阶段**：根据用户反馈决定是否引入备用屏幕（P1），此时评估 jediterm 移植成本。
3. **全屏 TUI**（vim/htop）作为远期目标，需要完整的 P1+P2 支持。

### 3.3 解析器数据模型

```kotlin
/** 终端屏幕缓冲区 — 二维字符网格 + 样式 */
data class TerminalBuffer(
  val cols: Int,
  val rows: Int,
  val cells: Array<Array<Cell>>,   // [row][col]
  var cursorRow: Int = 0,
  var cursorCol: Int = 0,
  var cursorStyle: CursorStyle = CursorStyle.BLOCK,
)

data class Cell(
  val char: Char = ' ',
  val style: SgrStyle = SgrStyle(),
)

data class SgrStyle(
  val fg: TerminalColor? = null,    // null = 默认色
  val bg: TerminalColor? = null,
  val bold: Boolean = false,
  val dim: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val reverse: Boolean = false,
)
```

---

## 4. UI 适配

### 4.1 两种渲染模式

Mode A 的输出渲染为 **Chat Bubble**（代码块在聊天气泡中）。
PTY 模式需要一个独立的 **Terminal View**。

```
┌─────────────────────────────────────┐
│ ┌─ 聊天视图 ──────────────────────┐ │
│ │ $ ls -la                        │ │
│ │ ┌─ Output Bubble ────────────┐  │ │  ← Mode A：exec 输出
│ │ │ total 48                    │  │ │
│ │ │ drwxr-xr-x 5 user user ... │  │ │
│ │ └────────────────────────────┘  │ │
│ │                                 │ │
│ │ $ bash                          │ │
│ │ ┌─ Terminal View ────────────┐  │ │  ← PTY 模式：内嵌终端
│ │ │ user@host:~$ █              │  │ │
│ │ │                             │  │ │
│ │ │                             │  │ │
│ │ └────────────────────────────┘  │ │
│ └─────────────────────────────────┘ │
│ ┌─ 输入栏 ────────────────────────┐ │
│ │ [发送]  [Ctrl] [Tab] [↑] [↓]   │ │  ← PTY 专用工具栏
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 4.2 Terminal View 组件

```
TerminalView (Compose)
  ├── Canvas 渲染层
  │     ├── 背景色填充
  │     ├── 逐字符绘制（等宽字体，网格对齐）
  │     ├── SGR 样式应用（颜色、粗体等）
  │     └── 光标绘制（闪烁块/下划线/竖线）
  ├── 输入处理层
  │     ├── 软键盘输入 → 写入 PTY stdin
  │     ├── 特殊键映射（Ctrl+C → 0x03, Tab → 0x09, Esc → 0x1B）
  │     └── 箭头键 → ANSI 序列（\e[A/B/C/D）
  ├── 窗口变更
  │     ├── 监听 Compose 尺寸变化
  │     ├── 计算 cols/rows（像素 / 字符宽高）
  │     └── 调用 `ptyRunner.resize(cols, rows)`
  └── 滚动
        ├── 终端历史缓冲区（可配置行数，如 10000 行）
        └── 手势滚动（上滑查看历史）
```

### 4.3 输入工具栏

移动端没有物理键盘的 Ctrl/Tab/Esc，需要虚拟按键：

```
┌──────────────────────────────────────────┐
│ [Ctrl] [Tab] [Esc] [↑] [↓] [←] [→] [F1..F12] │
└──────────────────────────────────────────┘
```

- **Ctrl**：切换模式，按下后下一个键自动附加 Ctrl 修饰（Ctrl+C = `\x03`）
- **Tab**：发送 `\t`
- **Esc**：发送 `\x1B`
- **方向键**：发送对应 ANSI 序列

### 4.4 内嵌 vs 全屏

| 模式 | 场景 | 实现 |
|------|------|------|
| **内嵌 Terminal View** | 交互式 shell、简单命令 | 在聊天气泡区域内渲染终端 Canvas |
| **全屏 Terminal** | vim/htop/less | 占满整个屏幕，隐藏聊天 UI，退出时恢复 |

全屏模式的触发：检测到备用屏幕切换序列（`\e[?1049h`）时自动进入，检测到退出序列（`\e[?1049l`）时自动退出。

---

## 5. 与现有 exec 模式的共存策略

### 5.1 并行共存

exec 模式和 PTY 模式可以在同一 SSH 连接上并行工作。SSH 协议支持多 channel 复用：

```
SSH Connection (单个 TCP 连接)
  ├── Channel 1: exec "docker ps"          ← Mode A
  ├── Channel 2: exec "uptime"             ← Mode A
  └── Channel 3: shell (PTY)               ← PTY 模式
```

`SshConnectionPool` 已支持连接复用，新增 PTY 不需要修改池化逻辑。

### 5.2 用户入口

```
输入栏行为：
  ┌─────────────────────────────────────────┐
  │ [输入框: 输入命令...] [发送] [终端模式]  │
  └─────────────────────────────────────────┘

  - 普通输入 + 发送 → Mode A（exec channel）
  - 点击 [终端模式] → 切换到 PTY 输入模式
  - PTY 模式下输入栏变为终端工具栏（Ctrl/Tab/Esc/方向键）
  - 再次点击 [终端模式] 或输入 /exit → 退出 PTY
```

### 5.3 命令模板与 PTY

| Runner 类型 | 模式 | 说明 |
|------------|------|------|
| `shell_exec` | exec | 一次性命令 |
| `codex_exec_jsonl` | exec | Codex CLI JSONL 流 |
| `interactive_shell` (新增) | PTY | 登录 shell |
| `custom_tui` (新增) | PTY | 指定命令（如 `htop`, `vim /etc/hosts`）|

### 5.4 生命周期管理

```
PTY Session 生命周期：

  用户点击 [终端模式]
    → ViewModel 调用 ptyRunner.open(profile, config)
    → 创建 TerminalView，绑定输入/输出
    → 进入 PTY 交互循环

  用户输入 → TerminalView 捕获 → ptyRunner.sendInput()
  远程输出 → ptyRunner 事件流 → TerminalBuffer 解析 → Canvas 重绘
  窗口变更 → 计算新 cols/rows → ptyRunner.resize()

  退出条件：
    - 用户输入 exit/logout
    - 远程 shell 进程退出（PtyEvent.Exit）
    - 用户主动关闭（返回按钮）
    - 网络断开（PtyEvent.Error）

  退出处理：
    - 清理 TerminalView
    - 关闭 PTY channel
    - 在聊天记录中留下 "PTY session closed" 消息
```

---

## 6. 优先级评估

### 6.1 用户价值矩阵

| 能力 | 用户需求 | 技术复杂度 | 优先级 |
|------|---------|-----------|--------|
| 交互式 shell（bash/zsh） | 极高 | 中 | **v1.x P0** |
| 密码输入（sudo、ssh） | 高 | 低 | **v1.x P0** |
| 进度条/Spinner 显示 | 高 | 低（SGR + 回车覆盖） | **v1.x P0** |
| 带颜色的命令输出 | 中高 | 低（SGR 解析） | **v1.x P0** |
| REPL（python/node） | 中 | 低（PTY 天然支持） | **v1.x P1** |
| vim/nano 编辑器 | 中 | 高（备用屏幕 + 完整 VT100） | **v2.0** |
| htop/top 全屏 TUI | 中 | 高（备用屏幕 + 鼠标） | **v2.0** |
| git rebase -i | 低 | 高（需要 vim 交互） | **v2.0** |

### 6.2 实施建议

**v1.x（建议纳入）**：

- 最小 PTY 支持：交互式 shell、密码输入、SGR 颜色、光标定位
- 内嵌 Terminal View（Canvas 渲染）
- 虚拟键盘工具栏（Ctrl/Tab/Esc/方向键）
- 复用现有认证和连接池

**理由**：
1. 交互式 shell 是 SSH 客户端的核心能力，缺失会让 RikkaAgent 始终是个"半成品"
2. 技术上只需在现有架构上增量添加，不破坏 Mode A
3. SGR + 光标定位的解析器开发量可控（~500-800 行 Kotlin）
4. 用户价值/开发成本比最高

**v2.0（延后）**：

- 完整 VT100 仿真（备用屏幕、滚动区域、鼠标追踪）
- 全屏 TUI 支持（vim/htop/less）
- 可能需要评估 jediterm 移植或 WebView 方案

**延后理由**：
1. 备用屏幕 + 鼠标追踪的开发量是 P0 的 2-3 倍
2. 手机屏幕上运行 vim 的 UX 本身就不理想
3. 少数用户需要，ROI 不高

### 6.3 工作量估算

| 阶段 | 内容 | 估算 |
|------|------|------|
| sshj PTY runner | SshjPtyRunner + 接口定义 | 2-3 天 |
| VT100 解析器 P0 | SGR + 光标 + 行擦除 + 回车 | 3-5 天 |
| TerminalBuffer | 屏幕缓冲区 + 渲染 | 2-3 天 |
| TerminalView | Compose Canvas 组件 | 3-4 天 |
| 输入工具栏 | 虚拟键 + 特殊键映射 | 1-2 天 |
| 集成测试 | Docker sshd + 端到端 | 2-3 天 |
| **合计** | | **13-20 天** |

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| VT100 解析器覆盖不全 | 部分程序显示异常 | P0 聚焦高频序列；日志记录未解析序列，逐步补齐 |
| Compose Canvas 渲染性能 | 快速输出时卡顿 | 帧率限制（30fps）、脏区域重绘、异步缓冲 |
| 输入延迟 | 打字体验差 | 输入直写 PTY，不经过 UI 线程批处理 |
| sshj 的 PTY API 限制 | 某些 PTY 功能不支持 | 验证 allocatePTY + sendWindowChange 的可用性；必要时降级到 JSch |
| 终端尺寸计算 | CJK 字符宽度不对齐 | 暂不支持 CJK 宽字符（P2）；英文环境下 cols/rows 计算准确 |

---

## 附录：参考实现

- sshj Shell 示例：`sshd-core` 测试中的 `SessionTest.startShell()`
- VT100 规范：ECMA-48 / DEC VT100 User Guide
- Android 终端模拟器参考：Termux（完整 VT100，GPL）
- Compose Canvas 文本渲染：`drawText` + `TextMeasurer`
