# Interaction Details Spec

This document defines interaction behavior that is easy to miss in high-level UX docs:

- gestures (tap/long-press/swipe)
- menus and actions
- selection + export
- scrolling + streaming edge cases
- keyboard/IME behavior

This is written for **Mode A** (non-interactive SSH exec). We are not building a terminal emulator.

## 1) Message Gestures

### Tap

- Tap on a message body:
  - toggles expanded/collapsed state *if* the message is collapsible (large output, code block, error stack)
  - otherwise: no-op (avoid accidental actions while scrolling)
- Tap on an inline affordance (copy/rerun/chevron):
  - triggers that action and consumes the gesture (must not also toggle collapse)

### Long-Press

Long-press anywhere on a message bubble opens a **context menu** anchored to the bubble.

Rules:

- The menu must be reachable with one hand (bottom sheet on phones is acceptable).
- Haptic feedback: one short haptic on menu open (configurable; see Settings spec).

### Swipe (Optional, but recommended)

If implemented, use one-direction swipe to reduce accidental triggers:

- Swipe **start→end** on a command bubble: `Rerun`
- Swipe **start→end** on an output bubble: `Copy`

Rules:

- Provide a subtle reveal background + icon.
- Must be cancelable by swiping back before threshold.
- Must not conflict with horizontal scroll in code blocks:
  - disable swipe when pointer down is inside a horizontally scrollable code block

## 2) Context Menus

### Command Bubble Menu

Actions (order matters):

1. Copy command
2. Rerun
3. Edit (if the command is the latest *user* message and not yet “committed”)
4. Pin (optional v1)
5. Delete (with confirm)

### Output Bubble Menu

Actions:

1. Copy visible
2. Copy all (if truncated, this copies the full buffered output if available)
3. Export (see Export rules below)
4. Search in output (opens in-bubble search UI or a full-screen search sheet)
5. Collapse / Expand

### Code Block Menu (Inside a Bubble)

Actions:

1. Copy code
2. Copy as file (exports into app storage and triggers Android share sheet)
3. Wrap lines: toggle (per-block override; persistent setting lives in Settings)

## 3) Multi-Select (Optional for v1, supported by design)

We should support selecting multiple bubbles for copy/export without making the UI feel “enterprise”.

Entry:

- long-press a bubble → show “Select” → enters selection mode

Selection mode UI:

- top bar shows selected count
- actions: Copy, Export, Cancel

Rules:

- Selection mode must not break scroll performance.
- Selected bubbles should be visually distinct but low-contrast (avoid neon outlines).

## 4) Export Rules

Supported export targets:

- Copy to clipboard (plain text by default; optional Markdown)
- Android share sheet as `.md` or `.txt`

Export formats:

- `Plain text`:
  - prefix each command with `$ `
  - include timestamps only if the user enables it
- `Markdown`:
  - command as fenced code block with `bash`
  - stdout/stderr as fenced code blocks

Privacy rules:

- Export UI must show a warning if the conversation may contain secrets.
- Provide an option: “Redact obvious secrets” (best-effort; never promise perfect redaction).

## 5) Scrolling & “Jump To Bottom”

### Default Auto-Scroll Behavior

While streaming output:

- If the user is already at (or near) bottom:
  - keep anchored to the bottom (auto-scroll)
- If the user scrolls up:
  - stop auto-scrolling immediately
  - show a floating `Jump to bottom` button

`Jump to bottom` button:

- appears only when not at bottom and new content arrives
- tap scrolls to bottom and clears the “new content” badge

### Scroll Jank Guardrails

- Never call `scrollToItem` for every chunk.
- Use batching (50–100ms) and only request a scroll when:
  - user is at bottom AND
  - enough content was appended to justify a scroll update

## 6) Input Bar Behavior (Keyboard / IME)

### Enter vs Shift+Enter

- `Enter`: send command
- `Shift+Enter`: newline

IME rules:

- When IME is composing text (CJK composition), Enter should commit IME text first, then send on next Enter.
- Provide a toggle in Settings: “Enter sends” (default ON).

### Paste

- If the paste includes multiple lines:
  - show a subtle hint: “Multi-line command”
  - do not auto-send
- If the paste is huge:
  - do not freeze UI; treat as plain text input with a max length and user-visible truncation.

### Command Templates

Optional v1:

- a small “templates” button opens a sheet of pinned commands/snippets
- selecting a template inserts into the input (does not auto-send)

## 7) Cancel / Timeout / Retry

Long-running command UX:

- show a status chip: `Running…` with elapsed time
- show an explicit `Cancel` action

Timeout policy:

- default timeout is configurable per profile
- on timeout:
  - output bubble ends with a clear status line (not a stack trace)
  - offer `Retry` and `Retry with longer timeout`

## 8) Haptics & Sound

Haptics:

- Long-press menu open: ON by default
- “Send” action: OFF by default (avoid noisy haptics in chat)

Sound:

- no sound effects by default

## 9) Accessibility

- All icon-only buttons must have content descriptions.
- Minimum touch target: 48dp.
- Text scaling:
  - message body respects system font scale
  - code blocks may cap scaling (configurable) to avoid destroying layout

