# RikkaAgent UI Design Document

> Auto-generated from source code analysis of `core/ui/src/main/kotlin/.../ui/theme/` and `components/`, `app/src/main/java/.../ui/screen/`.
>
> Last updated: 2026-06-23

---

## 1. Design System

### 1.1 Color System

#### 1.1.1 Material You Dynamic Color

RikkaAgent supports Android 12+ dynamic color (Material You) via `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`. When dynamic color is enabled, the system wallpaper-derived palette overrides the static schemes. Controlled by user preference `dynamicColor` (default: `false`).

#### 1.1.2 Static Color Schemes

Three built-in schemes act as fallback or explicit user choices:

| Mode | Background | Surface | Primary | On Primary | Secondary |
|------|-----------|---------|---------|------------|-----------|
| **Light** | `#F8F5F0` | `#FFFFFF` | `#0A0A0A` | `#F5F1E9` | `#3A6EA5` |
| **Dark** | `#0B0B0B` | `#141414` | `#F5F1E9` | `#0A0A0A` | `#7FB2E5` |
| **AMOLED** | `#000000` | `#0A0A0A` | `#F5F1E9` | `#000000` | `#7FB2E5` |

AMOLED mode overrides `background` and `surface` to pure black even when dynamic color is active.

#### 1.1.3 ExtendColors -- 50 Semantic Extended Colors

`ExtendColors` is a data class holding 50 colors across 5 hue groups (red, orange, green, blue, gray), each with 10 lightness levels (1 = lightest, 10 = darkest). Light and dark variants are inverted: in dark mode, `red1` is the darkest shade and `red10` is the lightest.

| Group | Level 1-2 (backgrounds) | Level 3-5 (medium) | Level 6-8 (foreground) | Level 9-10 (deep) |
|-------|------------------------|---------------------|----------------------|-------------------|
| **Red** | `#FFECE8` / `#FDCDC5` | `#FBACA3` / `#F98981` / `#F76560` | `#F53F3F` / `#CB272D` / `#A1151E` | `#770813` / `#4D000A` |
| **Orange** | `#FFF7E8` / `#FFE4BA` | `#FFCF8B` / `#FFB65D` / `#FF9A2E` | `#FF7D00` / `#D25F00` / `#A64500` | `#792E00` / `#4D1B00` |
| **Green** | `#E8FFEA` / `#AFF0B5` | `#7BE188` / `#4CD263` / `#23C343` | `#00B42A` / `#009A29` / `#008026` | `#006622` / `#004D1C` |
| **Blue** | `#E8F7FF` / `#C3E7FE` | `#9FD4FD` / `#7BC0FC` / `#57A9FB` | `#3491FA` / `#206CCF` / `#114BA3` | `#063078` / `#001A4D` |
| **Gray** | `#F7F8FA` / `#F2F3F5` | `#E5E6EB` / `#C9CDD4` / `#A9AEB8` | `#86909C` / `#6B7785` / `#4E5969` | `#272E3B` / `#1D2129` |

**Usage in code:** Access via `MaterialTheme.extendColors.red6`, etc. The `CustomColors` object provides pre-configured `CardColors`, `ListItemColors`, and `TopAppBarColors` that reference `colorScheme.surfaceContainer` / `surfaceBright` for consistent layering.

#### 1.1.4 Semantic Color Mapping

| Semantic Role | Light | Dark | Source |
|---------------|-------|------|--------|
| User bubble | `primary` | `primary` | `colorScheme.primary` |
| Assistant bubble | `surface` | `surface` | `colorScheme.surface` |
| Error bubble | `errorContainer` | `errorContainer` | `colorScheme.errorContainer` |
| Canceled bubble | `surfaceVariant` | `surfaceVariant` | `colorScheme.surfaceVariant` |
| Tag success bg | `extendColors.green2` | `extendColors.green2` | Extended |
| Tag error bg | `extendColors.red2` | `extendColors.red2` | Extended |
| Tag warning bg | `extendColors.orange2` | `extendColors.orange2` | Extended |
| Tag info bg | `extendColors.blue2` | `extendColors.blue2` | Extended |
| Code inline | `secondary` text on `secondaryContainer` bg | Same | Material3 |

---

### 1.2 Typography System

The theme uses `MaterialTheme.typography` (Material 3 defaults) with the following semantic usage:

| Role | Style | Size | Weight | Font Family |
|------|-------|------|--------|-------------|
| **Headline (H1)** | `headlineSmall` | ~24sp | Bold | Default (sans-serif) |
| **Title Large** | `titleLarge` | ~22sp | Bold | Default |
| **Title Medium** | `titleMedium` | ~16sp | Medium | Default |
| **Title Small** | `titleSmall` | ~14sp | Medium | Default |
| **Body Large** | `bodyLarge` | ~16sp | Normal | Default |
| **Body Medium** | `bodyMedium` | ~14sp | Normal | Default |
| **Body Small** | `bodySmall` | ~12sp | Normal | Default |
| **Label Large** | `labelLarge` | ~14sp | Medium | Default |
| **Label Medium** | `labelMedium` | ~12sp | Medium | Default |
| **Label Small** | `labelSmall` | ~11sp | Medium | Default |
| **Code (inline)** | `bodySmall` | 13sp | Normal | `FontFamily.Monospace` |
| **Code (block)** | `bodySmall` | 13sp, line-height 18sp | Normal | `FontFamily.Monospace` |
| **Chat input** | `bodyLarge` | 15sp | Normal | `FontFamily.Monospace` |
| **Code card body** | `bodySmall` | 13sp, line-height 18sp | Normal | `FontFamily.Monospace` |
| **Syntax highlight** | custom `TextStyle` | 13sp, line-height 18sp | Varies per token | `FontFamily.Monospace` |

**Code font family:** Provided via `LocalCodeFontFamily` (static composition local, default `FontFamily.Monospace`). All terminal/code/SSH output uses this.

**Markdown heading scale (MarkdownBlock):**

| Level | Font Size | Line Height Ratio |
|-------|-----------|-------------------|
| H1 (`# `) | 24sp | 1.25x |
| H2 (`## `) | 22sp | 1.25x |
| H3 (`### `) | 20sp | 1.25x |
| H4 (`#### `) | 18sp | 1.25x |
| H5 (`##### `) | 16sp | 1.25x |
| H6 | 14sp | 1.25x |

---

### 1.3 Spacing System

RikkaAgent follows a **4dp base grid** throughout:

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 2dp | Tag vertical padding, streaming dot padding |
| `sm` | 4dp | Markdown block spacing, list item gaps, section label indent |
| `md` | 8dp | Card header padding, icon gaps, list indent levels, standard spacer |
| `lg` | 12dp | Card content padding, code card padding, error card padding, inner gaps |
| `xl` | 16dp | Screen horizontal padding, list content padding, FAB padding, drawer padding |
| `xxl` | 24dp | Profile editor section spacing, about screen horizontal padding, chat input radius |

**Key layout constants:**

| Constant | Value | Location |
|----------|-------|----------|
| Screen horizontal padding | 16dp | ProfilesScreen, ProfileEditorScreen |
| Card vertical spacing (list) | 8dp | ProfilesScreen `Arrangement.spacedBy(8.dp)` |
| Message list vertical spacing | 2dp | ChatScreen `Arrangement.spacedBy(2.dp)` |
| Message list bottom padding | 80dp | ChatScreen (clearance for ChatInput) |
| ChatInput horizontal padding | 16dp | ChatInput |
| ChatInput vertical padding | 12dp | ChatInput |
| Drawer width | 300dp | SessionDrawerContent |
| Chat bubble content padding | 14dp horizontal, 10dp vertical | ChatBubble default `contentPadding` |
| TopAppBar | Standard Material3 `TopAppBar` / `LargeTopAppBar` | All screens |

---

### 1.4 Corner Radius & Elevation

| Element | Shape | Radius |
|---------|-------|--------|
| **User bubble** | `RoundedCornerShape(topStart=18, topEnd=18, bottomEnd=6, bottomStart=18)` | Asymmetric |
| **Assistant bubble** | `RoundedCornerShape(topStart=18, topEnd=18, bottomEnd=18, bottomStart=6)` | Asymmetric (inverse) |
| **CodeCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **CommandCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **StreamOutputCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **ReasoningCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **ErrorCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **MessagePartErrorCard** | `RoundedCornerShape(12.dp)` | Uniform |
| **ChainOfThought card** | `RoundedCornerShape(16.dp)` | Uniform |
| **ChatInput field** | `RoundedCornerShape(24.dp)` | Pill |
| **Tag** | `RoundedCornerShape(50)` | Full pill |
| **ProfileCard** | Material3 default Card shape | System |
| **Session item** | `RoundedCornerShape(12.dp)` | Uniform |
| **Connection error banner** | `RoundedCornerShape(12.dp)` | Uniform |
| **MermaidDiagramCard** | `MaterialTheme.shapes.medium` | System |
| **DataTable** | `MaterialTheme.shapes.small` | System |

**Elevation:**

| Element | Elevation | Notes |
|---------|-----------|-------|
| ErrorCard | `shadowElevation = 4.dp` | Floating error notification |
| Connection error banner | `shadowElevation = 4.dp` | Same pattern as ErrorCard |
| Host key dialog warning card | `shadowElevation = 2.dp` | Subtle lift |
| Scroll-to-bottom FAB | `defaultElevation = 2.dp` | Minimal |
| All other cards | `tonalElevation = 0.dp` | Flat, rely on surface color differentiation |

---

## 2. Component Library

### 2.1 ChatBubble

**File:** `components/ChatBubble.kt`

Renders a complete chat message with role-specific styling, part dispatching, action bar, and timestamp.

```kotlin
@Composable
fun ChatBubble(
    message: ChatMessage,
    enableMermaid: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    onRerun: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    showExpand: Boolean = false,
    onExpand: (() -> Unit)? = null,
    onShareFull: (() -> Unit)? = null,
)
```

**Variants by role:**
- **User:** Solid `primary` color bubble with plain `Text`, asymmetric corners (sharp bottom-end).
- **Assistant:** Transparent/`surface` background, dispatches `MessagePart` list to specialized renderers.
- **Streaming (empty):** Animated dots (`.` x 1-3, 800ms cycle) inside a minimal bubble.
- **Error status:** `errorContainer` background.
- **Canceled status:** `surfaceVariant` background.

**Action bar:** Appears after streaming ends. Contains timestamp (relative: "just now", "5m ago", "2h ago", or "MMM d, HH:mm"), copy button, rerun button (user messages), share button, expand button, share-full button. All icon buttons are 24dp, icons 14dp, tint `onSurfaceVariant` at 50% alpha.

---

### 2.2 ChatInput

**File:** `components/ChatInput.kt`

Message input field with send button.

```kotlin
@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSend: (String) -> Unit,
)
```

- Rounded pill shape (`24dp`), `surfaceVariant` background.
- Monospace font (`FontFamily.Monospace`, 15sp).
- Placeholder text at 40% alpha of `onSurfaceVariant`.
- Max 5 lines, `ImeAction.Send` keyboard action.
- Send icon button: `primary` tint, triggers haptic feedback (`TextHandleMove`).
- Clears input after send.

---

### 2.3 CodeCard

**File:** `components/CodeCard.kt`

Displays code output with language label, copy button, and auto-collapse.

```kotlin
@Composable
fun CodeCard(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
)
```

- Header: language label (`labelMedium`, 60% alpha) + copy button (28dp, shows checkmark for 1.5s after copy with haptic `LongPress`).
- Body: Monospace 13sp/18sp, selectable, horizontal scroll.
- Auto-collapse: > 15 lines collapses to first 15, max height 320dp when collapsed.
- Expand/collapse toggle: full-width `TextButton` at bottom.

---

### 2.4 HighlightCodeBlock

**File:** `components/HighlightCodeBlock.kt`

Syntax-highlighted code block supporting 50+ languages via regex-based tokenizer.

```kotlin
@Composable
fun HighlightCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    stripAnsi: Boolean = true,
    initialCollapsed: Boolean? = null,
    textStyle: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
)
```

**Syntax color palettes (14 semantic roles):**

| Role | Dark | Light |
|------|------|-------|
| Keyword | `#CC7832` (warm orange) | `#0000FF` (blue) |
| String | `#6A8759` (muted green) | `#008000` (green) |
| Number | `#6897BB` (steel blue) | `#098658` (teal) |
| Comment | `#808080` (gray, italic) | `#808080` (gray) |
| Function | `#FFC66D` (gold) | `#795E26` (brown) |
| ClassName | `#CB772F` (amber) | `#267F99` (cyan) |
| Property | `#9876AA` (purple) | `#9876AA` (purple) |
| Boolean | `#6897BB` (steel blue) | `#0000FF` (blue) |
| Builtin | `#8888C6` (lavender) | `#267F99` (cyan) |
| Tag | `#E8BF6A` (yellow) | `#800000` (maroon) |
| AttrName | `#BABABA` (silver) | `#E50808` (red) |
| AttrValue | `#6A8759` (green) | `#008000` (green) |
| Regex | `#36633D` (dark green) | `#36633D` (dark green) |

Max highlight length: 8192 characters (falls back to plain text beyond). ANSI escape stripping enabled by default.

---

### 2.5 MarkdownText

**File:** `components/MarkdownText.kt`

Renders Markdown via CommonMark parser (with GFM tables + strikethrough extensions).

```kotlin
@Composable
fun MarkdownText(
    markdown: String,
    enableMermaid: Boolean = false,
    modifier: Modifier = Modifier,
)
```

Supports: headings, paragraphs, bold/italic/strikethrough/code, fenced/indented code blocks, ordered/bullet lists, block quotes (vertical bar + indented), thematic breaks, links, GFM tables. Inline code: `secondary` color on `secondaryContainer` background, monospace 13sp.

When `enableMermaid = true`, fenced ```` ```mermaid ```` blocks are split out and rendered via `MermaidDiagramCard`.

---

### 2.6 MarkdownBlock

**File:** `components/MarkdownBlock.kt`

Alternative Markdown renderer using IntelliJ MarkdownParser. Async parsing on `Dispatchers.Default` for streaming performance. Same feature set as `MarkdownText` but with:
- GFM checkbox support (`[x]` / `[ ]`).
- Clickable links via `LinkAnnotation.Url`.
- Block quote: left border draw + background fill.
- Nested list indentation (3 bullet styles cycling: `•`, `◦`, `▪`).

---

### 2.7 CommandCard

**File:** `components/CommandCard.kt`

Terminal-style command display.

```kotlin
@Composable
fun CommandCard(
    command: String,
    exitCode: Int?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
)
```

- Header: `$` prompt (primary color) + command text (monospace 13sp) + exit code badge.
- Exit code badge states: running (`...`, gray), success (`exit 0`, primary on 12% primary bg), failure (`exit N`, error on 12% error bg).
- ANSI-stripped command text.
- Horizontal scroll for long commands.

---

### 2.8 StreamOutputCard

**File:** `components/StreamOutputCard.kt`

Renders stdout/stderr output.

```kotlin
@Composable
fun StreamOutputCard(
    text: String,
    isStderr: Boolean,
    modifier: Modifier = Modifier,
)
```

- **Stdout:** Neutral `surfaceVariant` at 50% alpha, `outlineVariant` accent bar.
- **Stderr:** `errorContainer` at 15%/30% alpha (light/dark), `error` accent bar at 60% alpha.
- Label: "stdout" or "stderr" in `labelSmall`.
- Auto-collapse > 15 lines, max height 400dp when collapsed.
- Horizontal + vertical scroll, selectable text.

---

### 2.9 ErrorCard / ErrorCardsDisplay

**File:** `components/ErrorCard.kt`

Stackable, dismissible error notifications.

```kotlin
data class ErrorInfo(
    val id: String,
    val title: String? = null,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun ErrorCardsDisplay(
    errors: List<ErrorInfo>,
    onDismissError: (String) -> Unit,
    onClearAllErrors: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L,
)
```

- Slide-in from bottom + fade-in animation.
- `errorContainer` background, `RoundedCornerShape(12.dp)`, `shadowElevation = 4.dp`.
- Copy-to-clipboard button + dismiss button (32dp each).
- Auto-dismiss after 5 seconds (configurable).
- "Clear all" button when multiple errors exist.

---

### 2.10 MessagePartErrorCard

**File:** `components/MessagePartErrorCard.kt`

Inline error display within assistant messages.

```kotlin
@Composable
fun MessagePartErrorCard(
    message: String,
    cause: String?,
    code: Int?,
    modifier: Modifier = Modifier,
)
```

- Warning icon + "Error" label + optional error code badge.
- Cause chain in monospace 12sp below the message.
- Background: `errorContainer` at 40% (dark) / 25% (light) alpha.

---

### 2.11 ReasoningCard

**File:** `components/ReasoningCard.kt`

Collapsible chain-of-thought display.

```kotlin
@Composable
fun ReasoningCard(
    text: String,
    stepId: String?,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
)
```

- Collapsed by default; auto-expands during streaming.
- Header: icon + "Thinking" label + streaming pulse dot + expand/collapse chevron.
- Content: monospace `bodySmall` text, selectable when not streaming.
- `surfaceVariant` at 60%/40% alpha (dark/light), `RoundedCornerShape(12.dp)`.
- Streaming pulse: 6dp dot, `secondary` color, alpha oscillation 0.3-1.0 over 600ms.

---

### 2.12 ChainOfThought

**File:** `components/ChainOfThought.kt`

Timeline-style reasoning step list.

```kotlin
@Composable
fun <T> ChainOfThought(
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
    steps: List<T>,
    collapsedVisibleCount: Int = 2,
    collapsedAdaptiveWidth: Boolean = false,
    content: @Composable ChainOfThoughtScope.(T) -> Unit,
)
```

- Vertical timeline line drawn behind step icons.
- Auto-collapse: shows last N steps when collapsed, expand/collapse toggle bar at top.
- Step icon: 14dp in 20dp opaque background circle (hides timeline line).
- Default icon: 8dp `onSurfaceVariant` dot in `CircleShape`.
- Expand indicator: 16dp `KeyboardArrowUp`/`KeyboardArrowDown`.

**Scope interface:**
```kotlin
interface ChainOfThoughtScope {
    @Composable
    fun ChainOfThoughtStep(
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        collapsedAdaptiveWidth: Boolean = false,
        content: (@Composable () -> Unit)? = null,
    )

    @Composable
    fun ControlledChainOfThoughtStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        // ... same params as above plus contentVisible: Boolean
    )
}
```

---

### 2.13 Tag

**File:** `components/Tag.kt`

Semantic status tags.

```kotlin
enum class TagType { DEFAULT, SUCCESS, ERROR, WARNING, INFO }

@Composable
fun Tag(
    modifier: Modifier = Modifier,
    type: TagType = TagType.DEFAULT,
    onClick: (() -> Unit)? = null,
    children: @Composable RowScope.() -> Unit,
)
```

- Full pill shape (`RoundedCornerShape(50)`).
- Color mapping: SUCCESS -> green2/green8, ERROR -> red2/red8, WARNING -> orange2/orange8, INFO -> blue2/blue8, DEFAULT -> tertiaryContainer/onTertiaryContainer.
- `labelSmall` text style, 6dp horizontal / 1dp vertical padding.
- Optional click handler.

---

### 2.14 DotLoading

**File:** `components/DotLoading.kt`

Pulsing loading indicator.

```kotlin
@Composable
fun DotLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 600,
    size: Dp = 16.dp,
)
```

- Circular dot, alpha oscillation 0.3-1.0, configurable duration and size.

---

### 2.15 FormItem

**File:** `components/Form.kt`

Settings/form row layout.

```kotlin
@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
)
```

- `Row` with label column (weight 1f) + tail slot.
- Label: `titleMedium` style.
- Description: `labelSmall` at 60% alpha.
- Used in settings and form screens.

---

### 2.16 DataTable

**File:** `components/DataTable.kt`

SubcomposeLayout-based table with auto-fit columns.

```kotlin
@Composable
fun DataTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 8.dp,
    cellBorder: BorderStroke? = BorderStroke(0.5.dp, outlineVariant at 50%),
    headerBackground: Color = surfaceVariant,
    zebraStriping: Boolean = true,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    stretchToFillWidth: Boolean = true,
    // ...
)
```

- Two-phase measurement: natural size pass, then fixed-width uniform-height re-measure.
- Zebra striping on odd rows.
- Horizontal scroll when content exceeds viewport.
- Stretch-to-fill when content is narrower than viewport.
- Header: `labelLarge`, `SemiBold`, monospace 12sp.
- Body: `bodySmall`, monospace 12sp/16sp.

**Parsers:** `parseMarkdownTable()`, `parseCsvOrWhitespaceTable()`, `parseTabularOutput()` for SSH output detection.

---

### 2.17 MermaidDiagramCard

**File:** `components/MermaidDiagramCard.kt`

Renders Mermaid diagrams via WebView.

```kotlin
@Composable
fun MermaidDiagramCard(
    source: String,
    modifier: Modifier = Modifier,
)
```

- Loads mermaid@11 from jsDelivr CDN.
- Auto-detects dark/light theme from `MaterialTheme.colorScheme`.
- Maps Material3 colors into Mermaid `themeVariables` (primary, secondary, tertiary, surface, error palettes).
- Auto-height WebView (min 120dp, max 600dp) via JS bridge `AndroidInterface.onHeightReady()`.
- Graceful fallback: on render error, shows source code in `CodeCard` + retry button.
- Security: `javaScriptEnabled = true`, all file access disabled, `domStorageEnabled = false`.

---

### 2.18 MessagePartsBlock

**File:** `components/MessagePartsBlock.kt`

Dispatches `MessagePart` list to type-specific renderers.

```kotlin
@Composable
fun MessagePartsBlock(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    enableMermaid: Boolean = false,
    isStreaming: Boolean = false,
)
```

Dispatch table:
| Part Type | Renderer |
|-----------|----------|
| `MessagePart.Text` | `MarkdownText` (if markdown detected) or plain `Text` |
| `MessagePart.Code` | `CodeCard` |
| `MessagePart.Command` | `CommandCard` |
| `MessagePart.Stdout` | `StreamOutputCard(isStderr = false)` |
| `MessagePart.Stderr` | `StreamOutputCard(isStderr = true)` |
| `MessagePart.Error` | `MessagePartErrorCard` |
| `MessagePart.Reasoning` | `ReasoningCard` |
| `MessagePart.Mermaid` | `MermaidDiagramCard` (if `enableMermaid`) |

During streaming, `SelectionContainer` is skipped to avoid `ConcurrentModificationException`.

---

## 3. Page Design

### 3.1 ProfilesScreen

**File:** `screen/ProfilesScreen.kt`

**Layout:**
```
Scaffold
  +-- LargeTopAppBar (collapsible, exitUntilCollapsed)
  |     Title: app name
  |     Actions: Settings icon button
  +-- Content:
  |     Empty state: centered text "No profiles" + subtitle
  |     LazyColumn:
  |       contentPadding = innerPadding + 16dp H + 8dp V
  |       verticalArrangement = spacedBy(8.dp)
  |       items:
  |         SwipeToDismissBox (EndToStart only, shows Trash2 icon)
  |           ProfileCard
  +-- FAB: Plus icon -> new profile
```

**ProfileCard:**
```
Card (surface bg, combinedClickable: click -> open, long-press -> menu)
  Row (16dp padding, verticalCenter):
    Circle avatar (40dp, primaryContainer bg, first letter)
    Column (weight 1f):
      Title: profile.name or "username@host" (titleMedium, 1 line, ellipsis)
      Subtitle: "username@host:port" (bodyMedium, onSurfaceVariant, 1 line, ellipsis)
    Auth badge: "Key" or "Pass" (labelSmall, secondaryContainer bg, 8dp H / 4dp V)
  DropdownMenu (long-press):
    Edit
    Duplicate
```

---

### 3.2 ChatScreen

**File:** `screen/ChatScreen.kt`

**Layout:**
```
ModalNavigationDrawer (width 300dp)
  Drawer: SessionDrawerContent
    Header: "Sessions" title + Plus button
    LazyColumn of SessionItem (12dp H / 10dp V padding, 12dp radius, icon + title + delete button)
    Active item: secondaryContainer bg
  Main:
    Scaffold
      +-- TopAppBar (background color)
      |     Title Column:
      |       profileLabel (titleMedium, 1 line, ellipsis)
      |       Status row: pulsing dot (8dp, CircleShape) + status text (labelSmall)
      |         IDLE: onSurfaceVariant, "Connecting..."
      |         READY: primary, "Ready"
      |         EXECUTING: tertiary, "Running... Ns" (pulses)
      |         ERROR: error, "Error"
      |     Navigation: Menu icon (drawer toggle)
      |     Actions: Share (when messages exist, not streaming), X cancel (when streaming), ArrowLeft back
      +-- Content Box (fillMaxSize):
            Column:
              LinearProgressIndicator (when EXECUTING)
              ConnectionErrorBanner (slide-in from top, errorContainer, 12dp radius, 4dp shadow)
              Empty state (when no messages): hint text + suggestion chips ("uname -a", "df -h", "uptime")
              LazyColumn (weight 1f, top 8dp, bottom 80dp padding, spacedBy 2dp):
                ChatBubble items (keyed by id)
            Scroll-to-bottom SmallFAB (CircleShape, surfaceVariant, 2dp elevation, bottomCenter, 80dp above bottom)
            ChatInput (bottomCenter, enabled when not streaming)
```

**ChatInput positioning:** Docked at bottom via `Box(Modifier.align(Alignment.BottomCenter))`. The message list has 80dp bottom padding to avoid overlap.

---

### 3.3 SettingsScreen

**File:** `screen/SettingsScreen.kt`

**Layout:**
```
Scaffold
  +-- TopAppBar: "Settings" title, ArrowLeft back
  +-- Column (fillMaxSize, verticalScroll, innerPadding):
        SectionHeader: "General" (labelMedium, primary color, 16dp H / 8dp V)
        SettingsItem: Theme -> ThemePickerDialog (system/light/dark/amoled radio buttons)
        SettingsSwitchItem: Dynamic Color (Android 12+ only)
        SettingsItem: Default Shell -> ShellPickerDialog (/bin/bash, /bin/sh, /bin/zsh, /bin/fish)
        SettingsSwitchItem: Enable Mermaid Rendering

        SectionHeader: "Security"
        SettingsItem: Known Hosts -> KnownHostsScreen

        SectionHeader: "About"
        SettingsItem: Version -> AboutScreen
        SettingsItem: License -> AboutScreen
```

**SettingsItem:** `ListItem` with `headlineContent` (title) + `supportingContent` (subtitle), clickable.
**SettingsSwitchItem:** `ListItem` with `trailingContent = Switch`, entire row clickable to toggle.

---

### 3.4 ProfileEditorScreen

**File:** `screen/ProfileEditorScreen.kt`

**Layout:**
```
Scaffold
  +-- TopAppBar: "New Profile" / "Edit Profile", ArrowLeft back
  +-- FAB: Check icon (save)
  +-- SnackbarHost
  +-- Column (fillMaxSize, imePadding, 16dp H padding, verticalScroll, spacedBy 16dp):

        SectionLabel: "Connection" (labelMedium, primary color)
        Card (surface bg):
          OutlinedTextField: Name
          OutlinedTextField: Host (required, error state when blank + attempted)
          Row:
            OutlinedTextField: Port (120dp wide, number keyboard, 1-65535 validation)
            OutlinedTextField: Username (weight 1f, required)

        OutlinedButton: "Test Connection" (full width, loading spinner when testing)
        Test result text (primary if OK, error if failed)

        SectionLabel: "Authentication" (labelMedium, primary color)
        Card (surface bg):
          ExposedDropdownMenuBox: AuthType (PublicKey / Password)
          [When PublicKey]:
            Row: FilledTonalButton "Select File" + OutlinedButton "Paste Key"
            OutlinedButton: "Generate Key" (full width)
            [When generated]: Card (secondaryContainer) with pubkey hint + copy button
            [When key selected]: key filename + X remove button
            [When no key]: hint text at 50% alpha

        SectionLabel: "Codex Integration" (labelMedium, primary color)
        Card (surface bg):
          Row: "Codex Mode" label + Switch toggle
          [When enabled]:
            Description text
            OutlinedTextField: Working Directory
            OutlinedTextField: API Key (password visibility toggle, Eye/EyeOff icon)
            Hint text

        Spacer: 80dp (FAB clearance)
```

---

## 4. Interaction Specifications

### 4.1 Gestures

| Gesture | Context | Behavior |
|---------|---------|----------|
| **Swipe left (EndToStart)** | ProfileCard in ProfilesScreen | Deletes profile, shows trash icon in background |
| **Swipe right (StartToEnd)** | ProfileCard | Disabled (`enableDismissFromStartToEnd = false`) |
| **Tap** | ProfileCard | Opens SSH session (`onOpenSession`) |
| **Long press** | ProfileCard | Shows context menu (Edit, Duplicate) |
| **Tap** | Session drawer item | Switches to that thread |
| **Tap** | Chat bubble action buttons | Copy, rerun, share, expand |
| **Tap** | CodeCard / StreamOutputCard toggle | Expand/collapse long content |
| **Tap** | ReasoningCard header | Toggle expand/collapse |
| **Tap** | ChainOfThought step | Toggle step content or custom `onClick` |
| **Tap** | ChainOfThought collapse bar | Toggle all steps visibility |
| **Tap** | Tag | Optional `onClick` callback |
| **Tap** | Markdown link | Opens URL via `ACTION_VIEW` intent |
| **Tap** | Error card action link | Invokes `onAction` callback |
| **IME Send** | ChatInput | Sends message |

### 4.2 Animations

| Element | Animation | Duration | Spec |
|---------|-----------|----------|------|
| **Streaming dots** | Alpha 1.0->0.3 infinite | 800ms | `LinearEasing`, `RepeatMode.Restart` |
| **ReasoningCard pulse** | Alpha 0.3->1.0 infinite | 600ms | `LinearEasing`, `RepeatMode.Reverse` |
| **DotLoading** | Alpha 0.3->1.0 infinite | 600ms (configurable) | `tween`, `RepeatMode.Reverse` |
| **Status dot pulse** | Alpha 1.0->0.3 infinite | 800ms | `tween`, `RepeatMode.Reverse` |
| **ChatBubble action bar** | slideInVertically + fadeIn / slideOutVertically + fadeOut | Default | Enters when streaming ends |
| **ErrorCardsDisplay** | slideInVertically (from bottom) + fadeIn / slideOutVertically + fadeOut | Default | Per-card |
| **ConnectionErrorBanner** | slideInVertically (from top) + fadeIn / slideOutVertically + fadeOut | Default | |
| **CodeCard / StreamOutputCard** | `animateContentSize()` | Default | Smooth expand/collapse |
| **ReasoningCard content** | `expandVertically(from=Top) + fadeIn` / `shrinkVertically(to=Top) + fadeOut` | Default | |
| **ChainOfThought container** | `animateContentSize(defaultSpatialSpec)` | Material Expressive | |
| **ChainOfThought steps** | `animateContentSize()` | Default | |
| **MessagePartsBlock** | `animateContentSize()` | Default | |
| **Scroll-to-bottom FAB** | `fadeIn + slideInVertically` / `fadeOut` | Default | |
| **Host key dialog warning** | Standard `AlertDialog` | Default | |

**Motion scheme:** `MotionScheme.expressive()` via `MaterialExpressiveTheme`. This provides more pronounced spatial animations than the default Material3 motion.

### 4.3 Haptic Feedback

| Trigger | Haptic Type | Location |
|---------|-------------|----------|
| Send message | `HapticFeedbackType.TextHandleMove` | `ChatInput.doSend()` |
| Copy code | `HapticFeedbackType.LongPress` | `CodeCard.CopyCodeButton` |
| Copy message | `HapticFeedbackType.LongPress` | `ChatBubble.CopyButton` |
| Rerun command | `HapticFeedbackType.LongPress` | `ChatBubble.RerunButton` |

### 4.4 Visual Feedback

| Action | Feedback |
|--------|----------|
| Copy code/message | Icon changes to checkmark (`"✓"`) for 1.5 seconds, `secondary` color |
| Send message | Input clears immediately |
| Profile swipe delete | Background reveals trash icon aligned to end |
| Streaming active | Pulsing dot in TopAppBar status, streaming dots in bubble, progress indicator |
| Connection error | Banner slides in from top with error styling |
| Test connection | Loading spinner in button, result text below |

---

## 5. Responsive Design

### 5.1 Current Implementation

The current codebase targets phone-first with the following characteristics:

- **Fixed drawer width:** 300dp (`ModalDrawerSheet(modifier = Modifier.width(300.dp))`) -- does not adapt to screen width.
- **Single-column layout:** All screens use vertical `Column` or `LazyColumn`.
- **No explicit breakpoint system:** No `WindowSizeClass` usage detected.
- **Horizontal padding:** Fixed 16dp on most screens.

### 5.2 Phone (< 600dp)

Current layout works as-is:
- ProfilesScreen: full-width card list.
- ChatScreen: full-width message list with bottom-docked input. Drawer overlays content.
- SettingsScreen: full-width scrollable list.
- ProfileEditorScreen: full-width form with IME padding.

### 5.3 Tablet (600dp - 840dp)

Recommended adaptations (not yet implemented):
- **ProfilesScreen:** 2-column grid (`LazyVerticalGrid`) instead of single-column list. Cards can be narrower.
- **ChatScreen:** Persistent side panel (300dp) instead of modal drawer for session list. Message area takes remaining width.
- **SettingsScreen:** Two-pane layout: category list on left, detail on right.
- **ProfileEditorScreen:** Wider cards, possibly side-by-side port/username fields.

### 5.4 Foldable / Large Screen (> 840dp)

Recommended adaptations:
- **ChatScreen:** Three-column layout: session list (280dp) | message area (flex) | file/info panel (320dp, optional).
- **ProfilesScreen:** 3-column grid or list + detail preview pane.
- **CodeCard / HighlightCodeBlock:** Allow wider content before horizontal scroll kicks in.
- **DataTable:** More generous column widths, potentially show all columns without scroll.

### 5.5 Key Constraints for Responsive Work

- `ChatInput` uses `fillMaxWidth` with fixed 16dp horizontal padding -- needs width constraint on large screens.
- `ErrorCardsDisplay` uses fixed 16dp horizontal padding -- should respect content width limits.
- `MermaidDiagramCard` WebView has min/max height (120dp-600dp) but no width adaptation.
- `DataTable` has `stretchToFillWidth = true` by default, which handles wide viewports well.

---

## Appendix A: Component Composition Diagram

```
ChatBubble
  +-- UserBubble (Surface + Text)
  +-- MessagePartsBlock
  |     +-- MarkdownText / MarkdownBlock
  |     |     +-- CodeCard
  |     |     +-- MermaidDiagramCard
  |     +-- CodeCard
  |     +-- CommandCard
  |     +-- StreamOutputCard
  |     +-- MessagePartErrorCard
  |     +-- ReasoningCard
  |     +-- MermaidDiagramCard
  +-- StreamingDots
  +-- ActionBar
        +-- CopyButton
        +-- RerunButton
        +-- ShareButton
        +-- ExpandButton
        +-- ShareFullButton
```

## Appendix B: Theme Provider Chain

```
RikkaAgentTheme
  +-- MaterialExpressiveTheme (MotionScheme.expressive)
  |     +-- colorScheme (Light/Dark/Amoled/Dynamic)
  |     +-- AppTypography (MaterialTheme.typography)
  +-- CompositionLocalProvider
        +-- LocalDarkMode (Boolean)
        +-- LocalExtendColors (ExtendColors)
        +-- LocalCodeFontFamily (FontFamily.Monospace)
        +-- LocalOverscrollFactory (null -- disables overscroll glow)
```
