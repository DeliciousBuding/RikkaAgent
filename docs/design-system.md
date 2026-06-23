# RikkaAgent Design System — Supplementary Principles

> Based on Vercel Geist Design System, adapted for Android/Material You.
> Primary source: RikkaHub's ExtendColors + MaterialExpressiveTheme.
> This document supplements, not replaces, the primary design system.

---

## Design Philosophy

**Primary**: RikkaHub's Material You design — dynamic color, ExtendColors, expressive motion.
**Supplementary**: Geist's precision — strict gray hierarchy, consistent spacing, minimal decoration.

### Core Principles

1. **Content-first**: SSH output is the hero. Every design decision should improve readability of command output.
2. **Instant feedback**: Most interactions should feel instant. Use motion only when it clarifies a change.
3. **Consistent rhythm**: One radius family per view. One spacing scale throughout.
4. **Accessible by default**: WCAG AA contrast (4.5:1 body text). Focus rings on every interactive element.

---

## Color Hierarchy

Geist's gray scale semantics apply to RikkaAgent's ExtendColors:

| Semantic Role | RikkaAgent Token | Geist Equivalent | Usage |
|---------------|-----------------|-------------------|-------|
| Primary text | `gray900` / `onSurface` | `gray-1000` | Command text, headings |
| Secondary text | `gray700` / `onSurfaceVariant` | `gray-900` | Timestamps, metadata |
| Disabled text | `gray500` | `gray-700` | Inactive states |
| Default background | `background` | `background-100` | Page background |
| Card surface | `surface` | `background-200` | Message cards |
| Hover surface | `surfaceVariant` | `gray-200` | Hover states |
| Active surface | `surfaceBright` | `gray-300` | Pressed states |
| Default border | `outline` | `gray-400` | Dividers, borders |
| Hover border | `outlineVariant` | `gray-500` | Interactive borders |
| Accent | `primary` / `blue600` | `tertiary` | Links, focus states |
| Error | `error` / `red600` | `red-700` | Errors, destructive |

---

## Spacing Scale (4px base)

| Token | Value | RikkaAgent Usage |
|-------|-------|-----------------|
| `space-1` | 4dp | Inline icon gap |
| `space-2` | 8dp | Within-group spacing |
| `space-3` | 12dp | Input padding |
| `space-4` | 16dp | Between-group spacing |
| `space-6` | 24dp | Card padding (default) |
| `space-8` | 32dp | Section spacing |
| `space-10` | 40dp | Hero/section break |
| `space-16` | 64dp | Page margins |

**Rhythm rule**: 8dp inside a group, 16dp between groups, 32dp between sections.

---

## Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `radius-sm` | 6dp | Buttons, inputs, chips |
| `radius-md` | 12dp | Cards, dialogs, menus |
| `radius-lg` | 16dp | Bottom sheets, full-screen surfaces |
| `radius-full` | 9999dp | Avatars, pills, circular buttons |

**Rule**: Keep one radius family per view. Don't mix rounded and sharp corners.

RikkaAgent current state: ChatBubble uses 16dp/18dp mix → standardize to `radius-md` (12dp) for consistency with RikkaHub.

---

## Typography

RikkaAgent uses Material 3 typography. Geist's token structure maps as:

| Geist Token | M3 Equivalent | Size | Usage |
|-------------|---------------|------|-------|
| `heading-24` | `titleLarge` | 24sp | Screen titles |
| `heading-20` | `titleMedium` | 20sp | Section headers |
| `label-14` | `labelMedium` | 14sp | Labels, chips |
| `label-12` | `labelSmall` | 12sp | Metadata, timestamps |
| `copy-14` | `bodyMedium` | 14sp | Body text, messages |
| `copy-16` | `bodyLarge` | 16sp | Primary content |
| `copy-14-mono` | `bodySmall` | 14sp | Code blocks, commands |
| `button-14` | `labelLarge` | 14sp | Button text |

**Code font**: JetBrains Mono (monospace) for all command output, code blocks, and terminal content.

---

## Elevation & Shadows

| Level | Usage | Shadow |
|-------|-------|--------|
| 0 | Flat content | None |
| 1 | Cards | `0 2px 2px rgba(0,0,0,0.04)` |
| 2 | Popovers, menus | `0 4px 8px -4px rgba(0,0,0,0.04)` |
| 3 | Modals, dialogs | `0 8px 16px -4px rgba(0,0,0,0.04)` |

Dark mode: increase opacity to 0.16 for raised cards.

---

## Motion

- **Default**: 0ms — most interactions should feel instant
- **State changes**: ~150ms
- **Popovers/tooltips**: ~200ms
- **Overlays/modals**: ~300ms
- **Easing**: `cubic-bezier(0.175, 0.885, 0.32, 1.275)` (overshoot for playful feel)
- **Constraint**: No looping animation. Honor `prefers-reduced-motion`.

---

## Content Guidelines (SSH-specific)

### Command Output
- Show exit code prominently (green for 0, red for non-zero)
- Truncate long output with "Show more" affordance
- Preserve whitespace in monospace blocks

### Error Messages
Format: **what happened** + **what to do next**
- ✅ "Connection refused — check if the server is running and the port is correct"
- ✅ "Authentication failed — verify your credentials in Profile settings"
- ❌ "Error"
- ❌ "Connection failed. Please try again."

### Status Messages
- Use present participle + ellipsis: "Connecting…", "Executing…", "Loading…"
- ✅ "Connected to 192.168.1.1"
- ❌ "Successfully connected to 192.168.1.1"

### Action Labels
- Verb + Noun: "Copy Output", "Export Session", "Delete Profile"
- ✅ "Run Command"
- ❌ "Submit"
- ❌ "OK"

### Empty States
- Point to the first action: "No profiles yet. Tap + to create one."
- ✅ "No command history. Run a command to get started."
- ❌ "No data"

### Toasts
- Name the specific thing that changed
- No trailing period
- ❌ "Profile saved successfully."
- ✅ "Profile saved"

---

## Interactive States

| State | Background | Border | Text |
|-------|-----------|--------|------|
| Default | `surface` | `outline` | `onSurface` |
| Hover | `surfaceVariant` | `outlineVariant` | `onSurface` |
| Active/Pressed | `surfaceBright` | `outline` | `onSurface` |
| Disabled | `surfaceVariant` (50% alpha) | `outline` (50% alpha) | `onSurface` (38% alpha) |
| Focus | `surface` | `primary` ring | `onSurface` |
| Error | `errorContainer` | `error` | `onErrorContainer` |

**Focus ring**: 2dp surface gap + 2dp accent ring (matches Geist's dual-layer pattern).

---

## Component-Specific Rules

### ChatBubble
- User bubble: `primaryContainer` background, `onPrimaryContainer` text
- Assistant bubble: `surfaceContainerHigh` background with optional opacity control
- Border radius: 12dp (consistent `radius-md`)
- Padding: 12dp horizontal, 8dp vertical

### ChatInput
- Monospace font for command input
- 40dp height (compact) / 48dp height (expanded)
- Send button: `primary` background, circular
- Cancel button: `error` background, circular

### CodeBlock
- Background: `surfaceVariant`
- Border radius: 8dp
- Language badge: `label-12` in top-right
- Copy button: icon-only, appears on hover/focus

### Navigation Drawer
- Width: 280dp (phone) / 320dp (tablet)
- Surface: `surface`
- Active item: `primaryContainer`
- Border: right `outline` divider
