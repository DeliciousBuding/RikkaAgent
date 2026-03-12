# Open Questions (v1 vs v2)

This document is the single place where we track unresolved decisions so they do not stay scattered across specs.

## Must Decide Before Implementing SSH (M3)

- SSH library choice
  - criteria: Android compatibility, host key verification hooks, key format support, license compatibility
- Private key handling
  - where passphrases live (memory-only vs optionally cached)
  - which Android crypto storage to use for key material (`keyRef` semantics)
- Known-hosts storage format
  - file-like OpenSSH `known_hosts` vs Room table only
  - canonical host+port formatting rules

## Should Decide Before Implementing Rendering (M2)

- Markdown library choice (Compose-friendly, performance characteristics)
- Code highlighting approach
  - lightweight syntax highlight vs full TextMate grammar
  - line numbers default (on/off)

## Optional v1 vs v2 Features

- Agent forwarding support (v2 unless the library makes it trivial and safe)
- Mermaid rendering (v2 if WebView overhead or security posture is uncertain)
- Multi-select export/copy (can be v1 if the UX remains lightweight)

## Protocol / Integration

- “Codex on server” invocation contract
  - recommended: a user-configured command template that returns Markdown
  - optional: JSONL events (`codex exec --json`) and a tolerant parser (`docs/spec/33-remote-exec.md`)
  - open: which JSON keys/event types should be promoted to first-class UI elements (vs debug-only)

- ACP support (v2+)
  - which ACP server do we support first (Copilot CLI vs Codex bridge vs others)?
  - transport choice: SSH-tunneled TCP by default; do we ever ship public WS support?
  - session persistence mapping: how ACP sessions map to app “threads”

## Privacy Defaults

- Should session history persist by default?
  - option A: persist only profiles + known_hosts; keep chat history ephemeral
  - option B: persist last N sessions with a clear retention policy
- Export redaction behavior
  - best-effort rules vs “manual only” (no automatic claims)
