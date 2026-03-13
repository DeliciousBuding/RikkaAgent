# Implementation Gap Matrix (Spec vs Current)

Purpose: convert spec requirements into executable backlog with severity and evidence.

## Legend

- Status: Done / Partial / Missing
- Priority: P0 (security/data risk), P1 (core UX/feature), P2 (polish)

## Gap Matrix

| Area | Requirement | Status | Priority | Evidence | Next Action |
|---|---|---|---|---|---|
| Mermaid | Optional Mermaid rendering with feature flag | Done | P1 | settings toggle + renderer + fallback path landed in app/core-ui | Keep regression coverage expanding |
| Host key mismatch | Replace host key must require double confirmation | Partial | P0 | confirmation flow exists, ViewModel coverage exists, and UI state-machine regression now covers the two-step decision path; full Compose/UI coverage is still missing | Add full Compose/UI regression coverage for replacement flow |
| Cancellation semantics | Distinct canceled terminal state in normalized events | Done | P1 | `ExecEvent.Canceled` exists and `ChatViewModel` maps canceled frames to persisted `MessageStatus.Canceled` | Keep regression coverage expanding |
| SSH output policy | stdout/stderr separation + truncation affordance | Done | P1 | `OutputFormatter` + Chat UI complete output actions | Keep regression tests expanding |
| JSONL tolerance | malformed line must not crash, raw lines preserved | Done | P1 | `JsonlParserTest` exists | Add more chunk-fragment cases |
| Testing spec | unit tests for known-hosts/truncation/stream | Partial | P1 | truncation/parser tests exist, ChatViewModel behavior suite covers init/thread switch/new session/delete thread/auth prompts, DataStoreKnownHostsStore persistence is covered, and host-key dialog state machine is covered | Add full Compose/UI and deeper integration coverage |
| M1 freeze | spec wording consistency + TODO centralization | Partial | P2 | `docs/spec/99-spec-hygiene.md` exists, but terminology still mixed across docs | run terminology pass in core spec docs |
| README publish quality | clear matrix/architecture/testing path | Partial | P2 | README exists but can improve visual hierarchy and onboarding scanability | redesign README hero + matrix + quick start verification |

## Immediate Execution Order

1. P0: keep host-key replacement double-confirm flow and add regression checks.
2. P1: add UI regression coverage for host-key replacement double confirmation.
3. P2: complete terminology unification + README / GitHub information architecture refresh.
