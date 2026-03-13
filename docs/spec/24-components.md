# UI Components Spec (Inventory)

This document lists the UI components we expect to build for Mode A.

Naming is **suggested**. The key requirement is that we design components so they can be reused and tested.

## 1) Screens

1. **Profiles List**
   - list of SSH profiles
   - create / import / edit
   - empty state (friendly + actionable)
2. **Profile Editor**
   - host / port / username
   - auth method selector
   - private key import
   - known hosts policy summary
3. **Fingerprint Confirm**
   - first-use trust prompt
   - host key mismatch prompt (danger)
4. **Session (Chat Timeline)**
   - message list + input bar
   - status banners (connecting/disconnected)
5. **Settings**
   - theme: dynamic/preset, dark mode, AMOLED option
   - rendering: wrap/line numbers/markdown output
   - privacy: export warnings, redact hints

## 2) Timeline Components

1. **CommandBubble**
   - shows `$ <command>`
   - actions:
     - copy
     - edit
     - rerun
     - pin (optional)
   - states:
     - normal
     - selected (for export/copy multi-select, optional)
     - disabled (when disconnected; rerun disabled but copy still allowed)
2. **OutputBubble**
   - contains:
     - stdout block
     - stderr block (if any)
     - metadata (exit code, duration, bytes)
   - actions:
     - copy stdout
     - copy stderr
     - copy all
     - expand/collapse
     - export (with warning)
   - states:
     - streaming (receiving chunks; show subtle indicator)
     - complete (exit code shown)
     - truncated (badge + export/copy all)
3. **StatusCard**
   - connection lifecycle (connecting/connected/disconnected)
   - host key prompts
4. **ErrorCard**
   - categorized errors with a primary action (retry/fix/cancel)

## 3) Code Block Components

1. **CodeBlockCard**
   - header row:
     - language label (if any)
     - copy button
     - wrap toggle (optional)
     - save/export (optional)
   - body:
     - monospace selectable text
     - optional line numbers
     - horizontal scroll if wrap disabled
2. **MermaidBlock**
   - diagram view with zoom
   - fallback to raw code when failed

## 4) Input Components

1. **CommandInputBar**
   - multi-line text field
   - send/run button with:
     - idle (primary color)
     - disabled (surface container)
     - running/cancel (error container)
   - optional “more” panel:
     - pinned commands
     - variables/snippets
   - behavior rules:
     - enter sends (configurable)
     - shift+enter inserts newline
     - while running, primary button cancels current exec
2. **PinnedCommandsRow** (optional early)
   - horizontally scrollable chips
   - tap to insert / long-press to edit

## 5) Profile/Auth Components

1. **AuthMethodSelector**
   - key-based auth (v1)
   - password auth (maybe later; off by default)
2. **PrivateKeyImporter**
   - import from file picker
   - optional paste
   - passphrase entry field (never logged)
3. **KnownHostsViewer**
   - list trusted keys per host
   - delete / replace flows gated by confirmations

## 6) Reusable “Polish” Components

1. **ActionIconButton**
   - consistent size + shape
2. **Chip**
   - small semantic labels (stderr, truncated, exit code)
3. **ShimmerPlaceholder**
   - optional shimmer for loading states (see `docs/spec/22-motion.md`)
4. **Toast / Snackbar**
   - copy confirmation
   - error summaries

## 7) Component Test Targets

At minimum:

- `CommandInputBar`:
  - disabled/enabled states
  - send/cancel behavior
- `OutputBubble`:
  - truncation UI appears correctly
  - copy actions produce correct content
- `FingerprintConfirm`:
  - “trust” vs “cancel” flows
  - mismatch warning copy present

- `CodeBlockCard`:
  - wrap toggle changes layout without jank
  - copy button copies the correct full content even when collapsed
