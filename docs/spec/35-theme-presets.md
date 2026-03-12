# Theme Presets Spec

This document defines a small set of theme presets so the app looks great out of the box, even without Dynamic Color.

Constraints:

- Must remain compatible with Material 3.
- Must not depend on proprietary fonts or assets.
- Presets must be tasteful and “chat-native” (not neon).

## 1) Preset List (v1)

- `Sakura` (warm, soft pink accent)
- `Ocean` (cool blue/teal accent)
- `Forest` (green accent, calm neutrals)
- `Mono` (neutral grayscale; high clarity)
- `AMOLED` (optional, dark-only true black)

## 2) Preset Intent & “Feel”

Sakura:

- warm, friendly, slightly dreamy
- good for long reading; avoid high saturation

Ocean:

- clean, modern, calm
- avoid “hospital blue”; keep teal muted

Forest:

- grounded, low-fatigue green
- warn against using pure green for success/failure semantics (stderr must stay semantic, not theme-driven)

Mono:

- maximum readability, minimum personality
- ideal for people treating the app like an ops tool

AMOLED:

- truly black background with careful container hierarchy
- only if we can keep borders/dividers visible and code blocks readable

## 3) Token Mapping Rules

Each preset defines:

- `primary`, `secondary`, `tertiary`
- container backgrounds for:
  - `surfaceContainer`
  - `surfaceContainerHigh`
  - `surfaceContainerHighest`
- optional “accent gradient” used sparingly (e.g., header scrim)

Rules:

- Do not apply gradients to code blocks.
- Do not apply gradients behind large text paragraphs.
- Gradients, if used, belong to:
  - top app bar background scrim
  - empty state card background

## 4) Dynamic Color Integration

If Dynamic Color is enabled:

- still allow selecting a preset, but treat it as:
  - a bias for accent colors
  - or a “container style” profile (more/less contrast)

If Dynamic Color is disabled:

- preset colors are the full theme.

## 5) Contrast Requirements

- All text must pass reasonable contrast in both themes.
- Inline code background must not reduce readability:
  - keep alpha low
  - ensure `onSecondaryContainer` remains readable

## 6) Implementation Guidance (Non-Normative)

Implement presets as data objects:

- `PresetTheme(id, name, isDarkCapable, lightColors, darkColors, codePalette)`

Do not hardcode colors across UI components. Everything goes through the theme system.

