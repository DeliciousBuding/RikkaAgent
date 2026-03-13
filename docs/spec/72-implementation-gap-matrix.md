# Implementation Gap Matrix (Spec vs Current)

Purpose: convert spec requirements into executable backlog with severity and evidence.

## Legend

- Status: Done / Partial / Missing
- Priority: P0 (security/data risk), P1 (core UX/feature), P2 (polish)

## Gap Matrix

| Area | Requirement | Status | Priority | Evidence | Next Action |
|---|---|---|---|---|---|
| Mermaid | Optional Mermaid rendering with feature flag | Done | P1 | settings toggle + renderer + fallback path landed in app/core-ui | Keep regression coverage expanding |
| Host key mismatch | Replace host key must require double confirmation | Done | P0 | confirmation flow exists, ViewModel coverage exists, and Compose dialog regression covers the two-step decision path | Keep regression coverage expanding |
| Cancellation semantics | Distinct canceled terminal state in normalized events | Done | P1 | `ExecEvent.Canceled` exists and `ChatViewModel` maps canceled frames to persisted `MessageStatus.Canceled` | Keep regression coverage expanding |
| SSH output policy | stdout/stderr separation + truncation affordance | Done | P1 | `OutputFormatter` + Chat UI complete output actions | Keep regression tests expanding |
| JSONL tolerance | malformed line must not crash, raw lines preserved | Done | P1 | `JsonlParserTest` covers malformed + split chunk cases | Keep parser cases expanding as schema evolves |
| Testing spec | unit tests for known hosts/truncation/stream | Done | P1 | truncation/parser tests exist, ChatViewModel behavior suite covers init/thread switch/new session/delete thread/auth prompts, DataStoreKnownHostsStore persistence is covered, host-key dialog state machine + Compose dialog regression are covered, and instrumentation notes are documented | Keep coverage expanding |
| M1 freeze | spec wording consistency + TODO centralization | Done | P2 | terminology unified across core specs, centralized hygiene doc maintained | Keep terminology checks part of doc review |
| README publish quality | clear matrix/architecture/testing path | Done | P2 | README includes matrix, architecture, verification shortcuts, and instrumentation doc entry | Keep polish and screenshots updated before release |

## Immediate Execution Order

1. P1: keep regression coverage expanding for host-key dialogs and JSONL streaming.
2. P2: refresh docs and UI strings when copy changes.
