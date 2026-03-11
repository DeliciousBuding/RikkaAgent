# Visual Design Spec

This document defines the **visual language** of `rikka-agent` (Mode A).

Goals:

- Match the “polished chat UI” feel of RikkaHub Android (inspiration only).
- Be **readable first**: stdout/stderr and code blocks are the hero.
- Keep everything implementable with **Jetpack Compose + Material 3**.

Non-goals:

- We are not copying RikkaHub code or assets.
- We are not implementing a full terminal emulator UI (PTY/ANSI).

## 1) Theme System

Baseline:

- Use **Material 3** with an **expressive** motion/shape/typography feel.
- Support **light/dark** + optional **dynamic color** (Android 12+).
- Offer a small set of **preset themes** so the app is beautiful even without dynamic color.

Recommended approach (conceptual):

- Provide a `LocalDarkMode` boolean (or equivalent) for code-highlight palettes.
- Optional: “AMOLED dark mode” background override (`#000000`) for true-black lovers.

Preset themes (suggested):

- `sakura` (soft pink, low-contrast background, warm accent)
- `ocean` (cool blue/teal accent, calmer containers)
- `spring` / `autumn` (seasonal accents)
- `black` (high-contrast neutral)

## 2) Color Usage Rules

Surfaces (prefer Material3 surface containers):

- App background: `colorScheme.background`
- Message bubbles / cards: `colorScheme.surfaceContainer` with **tonal elevation** rather than heavy shadows
- Header bars / code block headers: `colorScheme.surfaceContainerHighest`
- Inline code background: `colorScheme.secondaryContainer` with low alpha (e.g. 0.15–0.25)

Semantic colors:

- Primary actions (send, run, trust): `colorScheme.primary`
- Destructive: `colorScheme.errorContainer` / `onErrorContainer`
- Warnings (host key mismatch): `colorScheme.tertiaryContainer` (plus explicit warning copy)

stdout vs stderr:

- Do **not** rely only on red/green.
- Add a small label chip for `stderr` and optionally a thin left border.
- Ensure contrast remains acceptable in light and dark.

## 3) Typography

Principles:

- Default text is calm and “book-like”; code is crisp and slightly smaller.
- The app must remain readable under accessibility font scaling.

Recommended fonts:

- Body: platform default (Material typography baseline).
- Code: a bundled monospace (e.g. **JetBrains Mono**) for:
  - fenced code blocks
  - inline code
  - fingerprint strings
  - small “badge” labels (stderr/citation/etc.)

Suggested sizes (starting points, not hard rules):

- Body: 14–16sp
- Code block: 12–13sp with ~16–18sp line height
- Small labels/chips: 10–12sp

User-controlled density:

- Provide a **font size ratio** setting (e.g. 0.85–1.25) that scales message text and line height together.

## 4) Shapes, Corners, and “Bubbly” Language

Use Material shapes consistently:

- Message bubble: `shapes.medium`
- Code block container: `shapes.large` (distinct from message bubble)
- Attachment / pill chips: “rounded 50%” (pill)
- Icon buttons: circle

Avoid sharp corners for primary surfaces; “soft geometry” is part of the feel.

## 5) Spacing and Layout Rhythm

Use an 8dp grid:

- Outer screen padding: 12–16dp
- Bubble padding: 8–12dp
- Inter-message spacing: 8dp
- Intra-message spacing (parts inside a message): 4dp

Timeline width:

- On large screens/tablets, constrain message content width (to keep reading comfortable).

## 6) Blur / Glass (Optional)

RikkaHub Android uses a subtle glass feel for the input area.

Guidelines:

- Make blur **optional** (toggle in settings).
- If blur is enabled:
  - Apply a light haze/material blur to the input container background.
  - Keep text and icons fully opaque (no blurred content).
- If blur is disabled:
  - Use `surfaceContainerLow/High` colors to keep the same hierarchy.

## 7) Iconography

Guidelines:

- Use a single icon family for cohesion.
- Prefer open-source icon sets with a clear license compatible with Apache-2.0.
- Keep icon sizes consistent (e.g. 18–20dp in dense rows; 24dp in standalone buttons).

## 8) Accessibility (Visual)

Minimums:

- Touch targets: >= 48dp for primary controls.
- Don’t convey meaning by color alone (stdout/stderr, warning/safe).
- Support high font scales without clipping:
  - message bubbles should wrap and grow
  - toolbars may scroll horizontally rather than truncate critical controls

