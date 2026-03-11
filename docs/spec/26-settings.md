# Settings Spec

This document defines the settings surface for `rikka-agent` (Mode A).

Principles:

- Settings must be small, high-signal, and safe by default.
- Every toggle should clearly state tradeoffs (privacy, performance, security).
- “Pretty” features (blur, markdown rendering) are optional and must have sane fallbacks.

## 1) Sections

Recommended top-level sections:

1. Appearance
2. Rendering
3. Behavior
4. Security & Privacy
5. Advanced (optional)

## 2) Appearance

### Theme Mode

- `System` (default)
- `Light`
- `Dark`

### Dynamic Color (Android 12+)

- Toggle: `Use dynamic color`
- Default: `On` (if supported) or `Off` (if we prefer stable presets)
- Notes:
  - When off, use preset themes (`sakura`, `ocean`, etc.)

### Preset Theme

- Selector: `sakura`, `ocean`, `spring`, `autumn`, `black`
- Default: `sakura`

### AMOLED Dark Mode

- Toggle: `True black background`
- Default: `Off`
- Notes:
  - Only affects dark mode.
  - This should override `background` and `surface` only.

### Reduce Motion (Optional)

- Toggle: `Reduce motion`
- Default: `System`
- Notes:
  - When enabled, prefer `Motion.Instant` or reduce transitions (see `docs/spec/25-design-tokens.md`).

## 3) Rendering

### Output Rendering Mode

- Choice:
  - `Code blocks (recommended)` (default)
  - `Markdown (render output as Markdown)` (optional)
- Notes:
  - While streaming, we still render as code until completion (see `docs/spec/23-rendering.md`).

### Code Block Options

- Toggle: `Wrap long lines`
  - Default: `Off` (safer for alignment; user can enable)
- Toggle: `Show line numbers`
  - Default: `Off`
- Toggle: `Auto-collapse long code blocks`
  - Default: `On`
- Threshold: `Collapse lines`
  - Default: `10`

### Mermaid

- Toggle: `Render Mermaid diagrams`
  - Default: `On`
- Notes:
  - When off, show Mermaid as raw code.

### Blur / Glass Input Bar

- Toggle: `Enable blur effect`
  - Default: `On` if device performance allows; otherwise `Off`
- Notes:
  - Must degrade to a non-blur surface color while preserving hierarchy (see `docs/spec/21-visual.md`).

## 4) Behavior

### Enter-to-Run

- Choice:
  - `Enter runs command` (default)
  - `Enter inserts newline`
- Notes:
  - Always allow `Shift+Enter` to insert newline when enter-to-run is on.

### Auto-Scroll During Streaming

- Toggle: `Auto-scroll when at bottom`
  - Default: `On`
- Notes:
  - Must not steal scroll if user scrolled away; show “Jump to bottom” button instead.

### Font Size Ratio

- Slider: `0.85x` to `1.25x`
- Default: `1.0x`
- Notes:
  - Must scale font size and line height together.
  - Code blocks may scale less aggressively to preserve density.

## 5) Security & Privacy

### Host Key Policy

- Hard rule: host key mismatch blocks by default (not a user toggle).
- Optional: `Allow replacing known host key after double-confirmation` (default `On`)
- UI must warn clearly about MITM risk (see `docs/spec/27-microcopy.md`).

### Output Retention

- Choice:
  - `Do not save history` (default, safest)
  - `Save session history on device`
- Notes:
  - If enabled, provide:
    - per-session “Clear history”
    - global “Clear all”

### Export Warnings

- Toggle: `Show export warning`
  - Default: `On`
- Notes:
  - Warn that outputs may contain secrets (tokens, keys, IPs).

### Logs / Diagnostics

- Toggle: `Enable debug logs`
  - Default: `Off`
- Notes:
  - Debug logs must never include:
    - private keys
    - passphrases
    - full command outputs

## 6) Advanced (Optional)

### Keepalive

- Toggle: `Enable keepalive`
  - Default: `On`
- Setting: interval seconds (default `30s`)

### Timeouts

- `Connect timeout` (default `10s`)
- `Command timeout` (default `0` = no timeout)

## 7) Settings QA Checklist

- Every setting has:
  - default value
  - one-line explanation
  - clear effect on UI
- Enabling Markdown output does not degrade streaming performance (render-as-code-while-streaming rule holds).
- Enabling blur does not break readability on light/dark themes.

