# Spec Hygiene

Purpose: keep v1 spec wording and unresolved items centralized so implementation work does not miss hidden TODOs.

## Canonical Terms (v1)

- Use "Mode A" to mean non-interactive SSH exec channel behavior.
- Use "Codex mode" to mean running remote `codex exec --json --full-auto` through SSH exec.
- Use "known hosts" for host key trust records keyed by host+port.
- Use "complete output" for message-level output when display text is truncated.

## Centralized Open Questions

- Product and scope open questions: see `docs/spec/05-open-questions.md`.
- ACP integration open questions: see `docs/spec/36-acp.md` section "Open Questions".

## Hygiene Rule

When a new unresolved item appears in any spec file:

1. Add the full context in the source spec file.
2. Add a one-line pointer here under "Centralized Open Questions".
3. Link the item to roadmap work if it blocks implementation.
