# Layout Spec (Chat-First)

This document defines layout rules so the UI remains consistent across phones and tablets.

## 1) Screen Grid

Baseline:

- Use `Space.*` tokens from `docs/spec/25-design-tokens.md`.
- The app is “chat-first”: center column is the primary content.

## 2) Breakpoints (Guidelines)

We do not hardcode too many breakpoints, but we do need a few layout modes:

1. Phone portrait:
   - single-column
   - profiles list is a full screen
2. Tablet / large landscape:
   - optional persistent drawer for profiles / sessions
   - content stays readable by constraining maximum width

## 3) Timeline Width & Alignment

Rules:

- Messages align left/right by role:
  - user commands align right
  - system outputs align left
- Constrain message content width:
  - maximum width (guideline): `560–720dp` on tablets
  - on phone, use full width minus outer padding

## 4) Message Spacing

Rules:

- Inter-message gap: `Space.8`
- Bubble inner padding: `Space.8` (dense) or `Space.12` (default)
- Grouping:
  - if multiple parts belong to one logical message (stdout+stderr), keep them inside one `OutputBubble` with `Space.4` internal spacing

## 5) Top Bar

Rules:

- Keep top bar background transparent (lets background breathe).
- Title truncates with ellipsis.
- Secondary line (optional): show `user@host` and connection state.

## 6) Input Bar

Rules:

- Input bar stays anchored above IME.
- Input container has:
  - rounded large shape
  - optional blur
  - consistent padding
- Control row can scroll horizontally rather than compress icons into unreadable sizes.

## 7) “Jump To Bottom” Button

Rules:

- Only appears when user is not at bottom and streaming is happening (or new content arrived).
- Placement:
  - bottom-right, above input bar
- Size:
  - 40–48dp touch target

## 8) Empty States

Profiles list empty state:

- Icon + one sentence explanation + primary CTA.

Session empty state:

- Show a friendly hint:
  - `Run a command to get started`

## 9) Error Banners / Cards Placement

Rules:

- Connection-level errors appear at top of timeline as a status card.
- Command-level errors appear inline near the related command/output.
- Avoid modal dialogs except for host key trust and destructive actions.

