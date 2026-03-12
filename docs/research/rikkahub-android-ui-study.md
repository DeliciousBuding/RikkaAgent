# RikkaHub Android UI Study (Reference Notes)

This document records **observations** from the RikkaHub Android app UI to help us match a similar level of polish.

Important constraints:

- This is **not** a permission to copy code.
- We treat RikkaHub as **UX inspiration only**.
- `rikka-agent` must be a clean-room implementation (see `docs/adr/0002-clean-room.md`).

Reference repo:

- https://github.com/re-ovo/rikkahub

## 1) Theme & “Overall Look”

Theme entrypoint:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\theme\Theme.kt`

Key observations:

- Uses Material 3 expressive theme wiring (`MaterialExpressiveTheme`) and an expressive motion scheme.
- Supports:
  - system/light/dark selection
  - dynamic color (Android 12+)
  - AMOLED dark mode override for `background`/`surface`
- Preset themes are implemented as Material3 `ColorScheme` sets with detailed `surfaceContainer*` values.

Preset themes entrypoints:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\theme\PresetTheme.kt`
- Example palette: `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\theme\presets\SakuraTheme.kt`

What makes it “pretty”:

- Soft containers instead of heavy shadows.
- High attention to “surface stack” hierarchy: `surfaceContainer` / `Highest` etc.
- A consistent monospace font for code-related UI.

## 2) Chat Screen Structure

Navigation + screen entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\RouteActivity.kt`
- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\pages\chat\ChatPage.kt`

Key observations:

- Uses an adaptive drawer:
  - `PermanentNavigationDrawer` on large screens
  - `ModalNavigationDrawer` on small screens
- The chat input and list share a consistent “soft” visual language.

## 3) Chat Input “Glass” Feel (Blur/Haze)

Chat input entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\ai\ChatInput.kt`

Key observations:

- Uses a haze/blur effect when enabled:
  - blur is optional via settings
  - when blur is enabled, container color becomes transparent and haze provides the material feel
- The send button has clear state colors:
  - primary when enabled
  - surface container when disabled
  - error container when running (as a cancel affordance)
- “More options” panel expands/collapses with `animateContentSize`, which feels stable under IME changes.

## 4) Message Bubble Style & Action Rows

Message component entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\message\ChatMessage.kt`

Key observations:

- Bubbles are `Surface` + `tonalElevation` (lightweight depth).
- The action row appears/disappears with `AnimatedVisibility` using fade + vertical slide.
- User bubble is clickable (edit) and uses selection containers for text.

Implication for `rikka-agent`:

- Command + output bubbles should use the same “Surface + tonalElevation” pattern.
- Avoid heavy strokes or large drop shadows.

## 5) Rich Text Rendering (Markdown / Code / Mermaid / Math)

Markdown renderer entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\richtext\Markdown.kt`

Key observations:

- Uses a Markdown AST parser (GFM-flavored).
- Parses off the main thread when content updates (important for smooth scrolling).
- Has pre-processing rules for LaTeX-like patterns while skipping code blocks.
- Tables render as a custom DataTable component, with column min/max widths.
- Special inline link patterns are rendered as small clickable pills (citation-like chips).

Code block renderer entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\richtext\HighlightCodeBlock.kt`

Key observations:

- Code block is a “card” with:
  - header action row (copy, save, navigation)
  - body with selection and optional line numbers
  - auto-collapse to a fixed line count (expand/collapse)
- If language is `mermaid`, a Mermaid renderer is used (diagram view).

Mermaid entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\richtext\Mermaid.kt`
- WebView wrapper:
  - `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\components\webview\WebView.kt`

Key observations:

- Mermaid is rendered inside a WebView with zoom enabled.
- The component measures content height via a JS bridge and caches the resulting height (reduces “jumping”).
- Export to image is supported via a JS call that returns base64 image data to Android for saving.
- There is a “preview” affordance that opens a larger view in a bottom sheet.

Implication for `rikka-agent`:

- For Mode A, we can render stdout/stderr as “code cards” by default.
- Markdown parsing should be deferred or done after completion for streaming output to keep recompositions cheap.

## 6) Shimmer Placeholder

Entry:

- `rikkahub: app\src\main\java\me\rerere\rikkahub\ui\modifier\Shimmer.kt`

Key observations:

- Shimmer implemented as a modifier using `drawWithContent` and an infinite transition.
- Uses blend mode composition to reveal/hide underlying content.

Implication:

- We can implement shimmer as an optional polish layer for loading placeholders.

## 7) Architecture Patterns (2026-03-12 Update)

Entrypoints:

- DI: `rikkahub: app\src\main\java\me\rerere\rikkahub\di\`
- Repository: `rikkahub: app\src\main\java\me\rerere\rikkahub\data\repository\`
- Service: `rikkahub: app\src\main\java\me\rerere\rikkahub\service\ChatService.kt`
- Transformers: `rikkahub: app\src\main\java\me\rerere\rikkahub\data\ai\transformers\`

Key observations:

- **MVVM + Clean Architecture + Repository pattern**.
- **Dependency Injection via Koin** (v4.1.1) — modules split by layer:
  - `AppModule` (singletons), `DataSourceModule` (DB/API), `RepositoryModule`, `ViewModelModule`
- **Repository pattern**: `ConversationRepository` wraps Room DAO for thread-safe data access.
- **ChatService** orchestrates AI calls (maps to our SSH exec orchestration).
- **Message Transformation Pipeline**: Transformers modify messages before/after sending:
  - `TemplateTransformer` → Pebble template variables
  - `ThinkTagTransformer` → Extract `<think>` tags into reasoning parts
  - `RegexOutputTransformer` → Regex replacements on output
  - `OcrTransformer`, `DocumentAsPromptTransformer`, `TimeReminderTransformer`
- For RikkaAgent: we could adopt a simpler transformer pipeline for SSH output formatting (ANSI strip, Markdown wrap, etc.)

Data model pattern:

- **MessageNode** (branching): stores multiple message alternatives (for "regenerate"):
  ```
  MessageNode { messages: List<UIMessage>, selectIndex: Int }
  ```
- **UIMessage**: uses `parts: List<UIMessagePart>` (sealed class: Text, Image, Reasoning, ToolCall, ToolResult)
- For RikkaAgent: simpler model (command input → text output), but branching could map to "re-run command with different params"

Navigation:

- Uses **Navigation 3** (latest beta, replaces Navigation 2.x)
- `rikkahub: app\src\main\java\me\rerere\rikkahub\RouteActivity.kt`
- Shared element transitions between screens
- For RikkaAgent: we currently use Navigation 2.8 — can consider upgrading later

Build stack (reference, not target):

- AGP 9.0.1, Kotlin 2.3.10, Compose BOM 2026.02.01, minSdk 26, targetSdk 36
- OkHttp 5.3.2, Retrofit 3.0.0, Room 2.8.4, Koin 4.1.1
- These are significantly newer than RikkaAgent's current stack; upgrade incrementally as needed

## 8) Key Differences: RikkaHub vs. RikkaAgent

| Aspect | RikkaHub | RikkaAgent |
|--------|----------|-----------|
| Purpose | LLM chat client | SSH command → Codex bridge |
| Backend | HTTP/SSE to LLM APIs | Native SSH exec channel |
| Data Flow | User message → AI → streaming response | User command → SSH → stdout/stderr stream |
| Providers | OpenAI, Google, Claude | SSH profiles (host/user/key) |
| Message Types | Text, images, reasoning, tools | Command input, text/code output |
| Transformation | Template, CoT, OCR, regex | ANSI strip, Markdown wrap |
| Profile model | Assistant (system prompt, model, temp) | SSH profile (host, port, user, auth) |
| Session | Conversation history with branching | Command execution log |

## 9) Reusable Design Patterns for RikkaAgent

What to adapt (design inspiration, not code copy):

1. **Surface + tonalElevation bubble pattern** — already adopted ✅
2. **Code card component** — header action row (copy/save) + collapsible body + syntax highlight
3. **Koin DI structure** — consider adopting for clean testability
4. **Repository + DataStore/Room pattern** — needed for ProfileStore implementation
5. **Message transformer pipeline** — simplify to `AnsiStripper → MarkdownWrapper → OutputTruncator`
6. **Async Markdown parsing** — parse off main thread, especially for streaming output
7. **Adaptive drawer** — `PermanentNavigationDrawer` (tablet) / `ModalNavigationDrawer` (phone)

What NOT to adapt:

- Provider abstraction (we have SSH, not multiple AI APIs)
- Multimodal input (images/documents irrelevant for SSH commands)
- MCP support (out of scope for v1)
- Web UI module (Android-only for now)

## 10) Takeaways (Concrete "Feel" Targets)

If `rikka-agent` matches these, we're in the right territory:

- Soft surface hierarchy using Material3 `surfaceContainer*`.
- Calm typography with monospace for code blocks + chips.
- Lightweight animations:
  - fade/slide/scale in small ranges
  - expand/collapse with stable layout
- Optional blur/glass input bar.
- Rich output rendering with effortless copy/export.
- Clean layer separation with DI for testability.
- Interface-first design for all external integrations (SSH, storage).
