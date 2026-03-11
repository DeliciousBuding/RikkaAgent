# Design Tokens Spec

This document defines *implementable* design tokens for `rikka-agent`.

Intent:

- Keep the UI consistent across screens and components.
- Make it possible for contributors to change the “feel” without hunting through UI code.
- Stay compatible with Material 3 (we use tokens as a thin layer on top, not a replacement).

## 1) Token Naming Rules

- Tokens are stable and semantic-ish, but still simple: `Space.12`, `Radius.16`, `Motion.Fast`.
- Do not create per-feature tokens (`ChatBubblePadding`) unless we truly need it.
- Prefer composition:
  - base tokens (space/radius/motion)
  - component specs reference base tokens

## 2) Spacing Scale

We use an 8dp grid with a few “in-between” values for dense UI.

| Token | Value |
| --- | --- |
| `Space.0` | `0.dp` |
| `Space.2` | `2.dp` |
| `Space.4` | `4.dp` |
| `Space.6` | `6.dp` |
| `Space.8` | `8.dp` |
| `Space.12` | `12.dp` |
| `Space.16` | `16.dp` |
| `Space.20` | `20.dp` |
| `Space.24` | `24.dp` |
| `Space.32` | `32.dp` |

Usage guidelines:

- Timeline outer padding: `Space.12` or `Space.16`
- Bubble padding: `Space.8` (dense) or `Space.12` (comfortable)
- Inter-message gap: `Space.8`
- Intra-message (parts) gap: `Space.4`
- Icon button hitbox: `48.dp` minimum

## 3) Radii

Radii are what make the UI “soft”. Keep them consistent.

| Token | Value |
| --- | --- |
| `Radius.6` | `6.dp` |
| `Radius.8` | `8.dp` |
| `Radius.12` | `12.dp` |
| `Radius.16` | `16.dp` |
| `Radius.20` | `20.dp` |
| `Radius.Pill` | `50.dp` (conceptual pill) |
| `Radius.Circle` | `50%` (use `CircleShape`) |

Recommended mapping:

- message bubble: `Radius.16`
- code block card: `Radius.16` or `MaterialTheme.shapes.large`
- tiny chips: `Radius.Pill`
- media thumbnails: `Radius.12`

## 4) Elevation / Tonal Depth

Prefer tonal elevation (Material3 surfaces) over shadow.

| Token | Value |
| --- | --- |
| `Tonal.None` | `0.dp` |
| `Tonal.Low` | `1.dp` |
| `Tonal.Mid` | `2.dp` |
| `Tonal.High` | `4.dp` |

Rules:

- message bubble uses `Tonal.Mid` (or `Low` in dense mode)
- code block card header uses container colors, not large elevation
- avoid shadows in scroll lists (they look heavy and can be expensive)

## 5) Typography Scale

We keep the scale intentionally small. Most content is body text or code.

| Token | Target | Suggested |
| --- | --- | --- |
| `Type.Body` | normal messages | `15.sp` (line: `20.sp`) |
| `Type.BodySmall` | metadata | `13.sp` (line: `18.sp`) |
| `Type.Code` | code blocks | `12.5.sp` (line: `17.sp`) |
| `Type.Chip` | chips/badges | `11.sp` (line: `12.sp`) |
| `Type.Title` | screen title | use Material `titleMedium` |

Rules:

- Code must be monospace.
- Fingerprints/host keys must be monospace.
- Font-size ratio setting scales `Type.Body` and `Type.BodySmall` together, and also scales line height.
- For code, we may scale less aggressively to keep output from becoming “giant” under accessibility scaling.

## 6) Color Roles (Usage Map)

We rely on Material `colorScheme`, but we define “where to use what” so UI stays consistent.

| Role | Prefer |
| --- | --- |
| app background | `background` |
| base cards/bubbles | `surfaceContainer` |
| elevated header areas | `surfaceContainerHighest` |
| subtle separators/borders | `outlineVariant` |
| inline code bg | `secondaryContainer` with alpha 0.15–0.25 |
| primary action | `primary` / `onPrimary` |
| destructive action | `errorContainer` / `onErrorContainer` |
| warning container | `tertiaryContainer` (plus explicit text label) |

stdout vs stderr:

- stderr label chip uses `errorContainer` (low alpha) in dark mode, and `tertiaryContainer` in light mode if needed for contrast.
- add a left stripe (2dp) to stderr blocks for non-color affordance.

## 7) Motion Timings

We keep motion short and consistent.

| Token | Duration |
| --- | --- |
| `Motion.Instant` | `0ms` |
| `Motion.Fast` | `120ms` |
| `Motion.Medium` | `180ms` |
| `Motion.Slow` | `240ms` |
| `Motion.Extra` | `320ms` (rare) |

Usage guidelines:

- icon/button press feedback: `Fast`
- small appear/disappear: `Medium`
- big mode transition (preview/normal): `Slow`
- expand/collapse code blocks: `Medium` + `animateContentSize`

Easing guidelines:

- default: fast-out-slow-in (Compose default)
- shimmer: linear sweep (see `docs/spec/22-motion.md`)

## 8) Streaming Update Budget

Even though this is “design tokens”, we include a UI budget because it affects perceived polish.

Rules:

- Streaming output UI updates must be batched at `50–100ms`.
- Markdown parsing is deferred until completion (or at least until user stops receiving chunks).
- Long outputs should use truncation thresholds to avoid huge in-memory strings inside a single composable.

## 9) Suggested Kotlin Layout (Non-Normative)

We can implement tokens as a small file (example names only):

```kotlin
object Space {
  val S0 = 0.dp
  val S4 = 4.dp
  val S8 = 8.dp
  val S12 = 12.dp
  val S16 = 16.dp
}
```

Do not bikeshed exact names in early commits; keep them stable once published.

