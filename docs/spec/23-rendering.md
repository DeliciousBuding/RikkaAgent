# Rendering Spec (Markdown / Code / Mermaid)

This document defines how we render “AI-like” rich content in a chat timeline, but adapted for Mode A (SSH command + output).

The north star is:

- command and output are readable
- copy/export are effortless
- rendering is safe (no unexpected HTML execution)
- rendering remains smooth while output streams

## 1) Message Rendering Modes

We have two primary content types:

1. **Command** (user): treated as plain text with minimal formatting.
2. **Output** (system/tool):
   - default: code block
   - optional: Markdown rendering if the user enables it (useful when output is Markdown-like)

We also have “system” messages (status/errors/prompts) rendered as cards.

## 2) Markdown Support (Optional, But Nice)

If Markdown rendering is enabled for outputs:

- Use a Markdown parser that supports:
  - GFM tables
  - code fences with language
  - autolinks
- Parse on a background dispatcher to avoid UI jank.
- Cache the parse result for stable content.

### Pre-processing Rules (If We Support Math)

If we support LaTeX:

- Convert `\\( ... \\)` to `$ ... $`
- Convert `\\[ ... \\]` to `$$ ... $$`
- **Never** transform content inside code fences or inline code spans.

If we support HTML breaks:

- Convert `<br>` / `<br/>` / `<br />` into `\n` in plain text nodes.

## 3) Code Block Rendering (Default For stdout/stderr)

Default stdout/stderr rendering:

- stdout: fenced code block, language `text`
- stderr: fenced code block, language `text`, plus a visible `stderr` label

Code block UI requirements:

- Copy button (copy all)
- Optional: “save/export” button
- Optional: line numbers
- Optional: word wrap toggle
- Collapsible large blocks:
  - collapse threshold: e.g. 10 lines for first view
  - expand/collapse with `animateContentSize`

Syntax highlighting:

- Use a deterministic mapping from language → highlighter (best-effort).
- When language is unknown: fall back to plain monospace text.
- Pick separate palettes for light/dark mode.

## 4) Mermaid Rendering

Mermaid is useful both for:

- AI-generated diagrams (future)
- user output that contains Mermaid fences

Rules:

- If a code fence language equals `mermaid`, render it as a diagram view.
- Rendering should be sandboxed:
  - prefer an isolated WebView approach with local HTML/JS assets
  - no remote network access required for Mermaid rendering

Failure mode:

- If diagram rendering fails, show the raw Mermaid code block with an error banner.

## 5) Inline “Citation” / Chip Rendering (Optional)

RikkaHub renders special inline citation-like links as small pills.

If we adopt a similar pattern:

- Define a clear, explicit syntax (example):
  - link text `citation,<label>` and destination `<id>`
- Render as an inline chip:
  - monospace
  - small font (10–12sp)
  - subtle container background
  - clickable

Important: for `rikka-agent`, citations are optional. The core app does not require them.

## 6) Link Handling

Rules:

- All links open via system browser (no in-app web execution by default).
- Display links with:
  - underline
  - primary color
- Autolinks may be italicized for readability, but do not degrade contrast.

## 7) HTML Handling

HTML is the largest attack surface in a “rich renderer”.

Rules:

- By default, treat HTML blocks as **plain text** (rendered as a code block).
- If we add an “enable HTML rendering” toggle:
  - keep it off by default
  - sanitize aggressively
  - do not allow JS execution

## 8) Streaming & Throttling

Streaming output updates must be **batched**:

- Append raw text into a buffer as it arrives.
- Emit UI updates at a fixed interval (e.g. every 50–100ms).
- Avoid reparsing Markdown for every small chunk:
  - render streaming output as plain code until completion
  - optionally parse/upgrade to Markdown after completion

## 9) Performance Checklist

- Rendering a long output should not recompose the entire list every tick.
- Code blocks should be virtualized and avoid heavy layout in tight loops.
- Markdown parsing must happen off the main thread.
- Mermaid rendering must not block scrolling (defer / lazy load).

