# Implementation Gap Matrix (Spec vs Current)

Purpose: convert spec requirements into executable backlog with severity and evidence.

## Legend

- Status: Done / Partial / Missing
- Priority: P0 (security/data risk), P1 (core UX/feature), P2 (polish)

## Gap Matrix

| Area | Requirement | Status | Priority | Evidence | Next Action |
|---|---|---|---|---|---|
| Mermaid | Optional Mermaid rendering with feature flag | Done | P1 | settings toggle + renderer + fallback path landed in app/core-ui | Keep regression coverage expanding |
| Host key mismatch | Replace host key must require double confirmation | Partial | P0 | confirmation flow exists and ViewModel event coverage now exists, but UI-level double-confirm regression test is still missing | Add UI regression coverage for replacement flow |
| Cancellation semantics | Distinct canceled terminal state in normalized events | Partial | P1 | `docs/spec/33-remote-exec.md` defines RunCancelled; current `ExecEvent` lacks dedicated canceled event | Add canceled event or map cancel action to explicit status in UI/persistence |
| SSH output policy | stdout/stderr separation + truncation affordance | Done | P1 | `OutputFormatter` + Chat UI complete output actions | Keep regression tests expanding |
| JSONL tolerance | malformed line must not crash, raw lines preserved | Done | P1 | `JsonlParserTest` exists | Add more chunk-fragment cases |
| Testing spec | unit tests for known-hosts/truncation/stream | Partial | P1 | truncation/parser tests exist, ChatViewModel behavior suite covers init/thread switch/new session/delete thread/auth prompts, and DataStoreKnownHostsStore persistence is now covered | Add UI-level double-confirm and deeper integration coverage |
| M1 freeze | spec wording consistency + TODO centralization | Partial | P2 | `docs/spec/99-spec-hygiene.md` exists, but terminology still mixed across docs | run terminology pass in core spec docs |
| README publish quality | clear matrix/architecture/testing path | Partial | P2 | README exists but can improve visual hierarchy and onboarding scanability | redesign README hero + matrix + quick start verification |

## Immediate Execution Order

1. P0: keep host-key replacement double-confirm flow and add regression checks.
2. P1: add UI regression coverage for host-key replacement double confirmation.
3. P1: keep cancellation semantics aligned between structured events and persisted UI state.
4. P2: complete terminology unification + README / GitHub information architecture refresh.
