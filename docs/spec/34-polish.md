# UI Polish Spec (RikkaHub-Level Craft)

This document is a **quality bar**: what “beautiful like RikkaHub” means in concrete, testable terms.

We use RikkaHub Android as *inspiration* only. This repo remains clean-room.

## 1) Visual Hierarchy Rules

- The UI must be “output-first”:
  - stdout/stderr blocks are the hero
  - secondary controls (copy/rerun/export) are present but visually quiet
- Prefer **tonal elevation** over heavy shadows.
- Borders must be subtle:
  - use `outlineVariant` at low alpha
  - avoid 1px pure black borders in light theme

## 2) Bubble & Card Grammar

Message bubble:

- Rounded, soft, “friendly”
- Clear left/right alignment language:
  - command bubbles align end
  - output bubbles align start

Action row:

- Hidden by default; revealed on:
  - long-press (always)
  - optional: hover (tablets with pointer), only if it does not clutter

Action icon rules:

- Always show at least: copy / rerun for command; copy / more for output.
- Icon buttons must be **low-emphasis** until pressed:
  - `IconButton` with toned container, not bright filled buttons

## 3) Input Bar “Haze” (Optional, Premium Feel)

If blur/haze is enabled:

- input bar sits on a slightly translucent container
- blur affects only the background, never the text
- ensure text contrast remains stable in both light and dark

If blur is disabled:

- the hierarchy must still work:
  - input bar uses `surfaceContainerHigh` (or similar) with a subtle top divider

Non-negotiable:

- input bar must not visually “jump” when IME opens/closes.

## 4) Micro-Interactions That Make It Feel Premium

Required:

- Press feedback:
  - icon press uses `Motion.Fast` scale/fade ripple feedback
- Context menu open:
  - one light haptic + small scale-in
- Copy feedback:
  - show a small toast/snackbar: “Copied”
  - avoid huge snackbars that block output reading

Optional:

- Subtle shimmer placeholders for initial loading only (never shimmer real output).
- Smooth “jump to bottom” appear/disappear.

## 5) List & Streaming Smoothness (No-Jank Contract)

This is the most important “beauty” constraint:

- Streaming must feel fast and stable.
- The timeline must not “vibrate” or reflow constantly while chunks arrive.

Rules:

- Never reparse Markdown on every chunk.
- Batch UI updates (50–100ms).
- Keep stable keys for message items.
- Avoid large per-frame allocations in composables (especially concatenating giant strings).

## 6) Typography Quality Bar

- Body text is calm; code is crisp.
- Use a bundled monospace for all code/fingerprints.
- Avoid text that looks like “default debug UI”:
  - no tiny 10sp walls of text without line height
  - no center-aligned paragraphs in chat output

## 7) Color Quality Bar

- Light theme must not look washed out.
- Dark theme must not be pure gray on pure black unless user selects AMOLED mode.
- stderr must be identifiable without relying on “red text only”:
  - label chip
  - thin left stripe
  - optional icon

## 8) Empty States & First Impression

Required empty states:

- Profiles list empty:
  - one illustration-free, tasteful card with a single primary action (“Add profile”)
- Chat empty:
  - show “Connected” status + a hint (“Run your first command”)

Rules:

- Empty state should not look like Material template boilerplate.
- One strong headline + one sentence + one button is enough.

## 9) Visual QA Checklist (Release Gate)

Before any release, verify:

- Light theme looks coherent (no random grays, no mismatched radii).
- Dark theme looks coherent (no low-contrast text, no accidental black-on-black).
- Code blocks:
  - copy works
  - wrapping toggle works (if enabled)
  - horizontal scroll does not fight with swipe gestures
- Streaming:
  - long output does not drop frames noticeably
  - scroll-to-bottom behavior matches spec
- Host key prompts:
  - warnings are readable and not “panic red”
  - mismatch flow is double-confirmed and blocks by default

