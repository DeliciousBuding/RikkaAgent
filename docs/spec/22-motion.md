# Motion & Animation Spec

This document defines how `rikka-agent` should **move**.

The goal is “alive but not distracting”: micro-animations that make the UI feel premium, plus a few bigger transitions that explain state changes (connect, running, streaming output).

## 1) Motion Principles

- **Subtle** by default; prefer fade + small translate over big bounces.
- **Fast feedback**: button press and send/run should feel instant.
- **Respect performance**: animations must not cause message list jank.
- **Respect user control**: allow reducing motion (system “remove animations” where possible).

## 2) Core Motion Primitives

Recommended primitives in Compose:

- `AnimatedVisibility` for:
  - message action row (copy/rerun/export)
  - status banners (connecting/disconnected)
  - inline tool panels (e.g., host key prompt)
- `AnimatedContent` for:
  - switching “preview mode” vs “normal mode” (if implemented)
  - switching between profile list states (empty/loading/content)
- `animateContentSize` for:
  - expanding/collapsing code blocks
  - expanding the “more options” panel above the input bar

Transition style (guideline):

- enter: `fadeIn + slideInVertically(…/2)` or small scale-in (0.9–0.95)
- exit: symmetric fade + slide/scale out

Timing guidance:

- Use the motion token table in `docs/spec/25-design-tokens.md` to keep durations consistent.

## 3) Loading & Streaming States

Connection lifecycle:

- `connecting`:
  - show a small status card/banner near the top of the timeline
  - optional shimmer on disabled controls (run button)
- `connected`:
  - status banner fades out
- `disconnected`:
  - banner stays visible with “Reconnect” action

Command execution lifecycle:

- When the user sends a command:
  - command bubble appears immediately (optimistic UI)
  - output bubble appears immediately in “streaming” mode (empty → filling)
- While streaming:
  - update the UI in **batched ticks** (see `docs/spec/30-architecture.md` and `docs/spec/23-rendering.md`)
  - show a subtle “typing / running” indicator (small dots or spinner)
- On completion:
  - stop indicator
  - show exit code metadata (collapsed by default)

## 4) Shimmer (Optional, For Placeholders)

Use shimmer only for:

- skeleton placeholders while loading profile list
- “loading message bubble” placeholder

Do not shimmer real output text. It destroys readability.

Shimmer guidelines:

- duration: ~1.0–1.4s per sweep
- angle: slight tilt (~15–25 degrees)
- keep background subtle (use content color with alpha)

## 5) Scroll Behavior

Auto-scroll rules:

- If user is at (or near) the bottom:
  - keep the latest output visible during streaming
- If user has scrolled away:
  - do not “steal scroll”
  - show a floating “scroll to bottom” button

Jump-to-bottom button:

- appears with `AnimatedVisibility`
- small scale/fade in
- disappears when the list is back at bottom

## 6) Haptics (Optional)

If we add long-press menus / selection:

- light haptic on long-press open
- avoid haptics for every stream update

## 7) Error Transitions

Errors should be noticeable without being alarming:

- error card appears with fade + slight slide in
- dismissal uses fade out

## 8) Motion QA Checklist

- Streaming a long command output should not drop frames noticeably.
- Expand/collapse code blocks must not cause layout “teleporting”.
- Input bar expand/collapse must feel stable with IME on/off.
- No “infinite animations” on screens users stare at for minutes (battery + distraction).
