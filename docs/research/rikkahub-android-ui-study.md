# RikkaHub Android UI Study (Reference Notes)

This document records **observations** from the RikkaHub Android app UI to help us match a similar level of polish.

Important constraints:

- This is **not** a permission to copy code.
- We treat RikkaHub as **UX inspiration only**.
- `rikka-agent` must be a clean-room implementation (see `docs/adr/0002-clean-room.md`).

Reference repo path (local):

- `D:\Code\Projects\rikkahub`

## 1) Theme & “Overall Look”

Theme entrypoint:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\theme\Theme.kt`

Key observations:

- Uses Material 3 expressive theme wiring (`MaterialExpressiveTheme`) and an expressive motion scheme.
- Supports:
  - system/light/dark selection
  - dynamic color (Android 12+)
  - AMOLED dark mode override for `background`/`surface`
- Preset themes are implemented as Material3 `ColorScheme` sets with detailed `surfaceContainer*` values.

Preset themes entrypoints:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\theme\PresetTheme.kt`
- Example palette: `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\theme\presets\SakuraTheme.kt`

What makes it “pretty”:

- Soft containers instead of heavy shadows.
- High attention to “surface stack” hierarchy: `surfaceContainer` / `Highest` etc.
- A consistent monospace font for code-related UI.

## 2) Chat Screen Structure

Navigation + screen entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\RouteActivity.kt`
- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\pages\chat\ChatPage.kt`

Key observations:

- Uses an adaptive drawer:
  - `PermanentNavigationDrawer` on large screens
  - `ModalNavigationDrawer` on small screens
- The chat input and list share a consistent “soft” visual language.

## 3) Chat Input “Glass” Feel (Blur/Haze)

Chat input entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\ai\ChatInput.kt`

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

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\message\ChatMessage.kt`

Key observations:

- Bubbles are `Surface` + `tonalElevation` (lightweight depth).
- The action row appears/disappears with `AnimatedVisibility` using fade + vertical slide.
- User bubble is clickable (edit) and uses selection containers for text.

Implication for `rikka-agent`:

- Command + output bubbles should use the same “Surface + tonalElevation” pattern.
- Avoid heavy strokes or large drop shadows.

## 5) Rich Text Rendering (Markdown / Code / Mermaid / Math)

Markdown renderer entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\richtext\Markdown.kt`

Key observations:

- Uses a Markdown AST parser (GFM-flavored).
- Parses off the main thread when content updates (important for smooth scrolling).
- Has pre-processing rules for LaTeX-like patterns while skipping code blocks.
- Tables render as a custom DataTable component, with column min/max widths.
- Special inline link patterns are rendered as small clickable pills (citation-like chips).

Code block renderer entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\richtext\HighlightCodeBlock.kt`

Key observations:

- Code block is a “card” with:
  - header action row (copy, save, navigation)
  - body with selection and optional line numbers
  - auto-collapse to a fixed line count (expand/collapse)
- If language is `mermaid`, a Mermaid renderer is used (diagram view).

Mermaid entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\richtext\Mermaid.kt`
- WebView wrapper:
  - `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\components\webview\WebView.kt`

Implication for `rikka-agent`:

- For Mode A, we can render stdout/stderr as “code cards” by default.
- Markdown parsing should be deferred or done after completion for streaming output to keep recompositions cheap.

## 6) Shimmer Placeholder

Entry:

- `D:\Code\Projects\rikkahub\app\src\main\java\me\rerere\rikkahub\ui\modifier\Shimmer.kt`

Key observations:

- Shimmer implemented as a modifier using `drawWithContent` and an infinite transition.
- Uses blend mode composition to reveal/hide underlying content.

Implication:

- We can implement shimmer as an optional polish layer for loading placeholders.

## 7) Takeaways (Concrete “Feel” Targets)

If `rikka-agent` matches these, we’re in the right territory:

- Soft surface hierarchy using Material3 `surfaceContainer*`.
- Calm typography with monospace for code blocks + chips.
- Lightweight animations:
  - fade/slide/scale in small ranges
  - expand/collapse with stable layout
- Optional blur/glass input bar.
- Rich output rendering with effortless copy/export.

