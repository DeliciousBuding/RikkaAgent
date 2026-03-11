# rikka-agent

An Android (Jetpack Compose) chat-style SSH command runner (Mode A): execute commands over SSH (`exec`), stream stdout/stderr, and render outputs beautifully as Markdown / code blocks.

## Why

Mobile terminals work, but they're not always pleasant for reading command outputs. This project aims to provide a clean, readable UI for “run a command → see output → copy / rerun / export”.

## Features (Planned)

- SSH profiles (host/port/user)
- Private key import + encrypted at-rest storage (Android Keystore backed)
- Strict `known_hosts` verification (first-use fingerprint confirmation; change warning/block)
- Command cards (edit / copy / rerun / pin)
- Streaming output cards (stdout/stderr; collapse; search)

## Non-Goals

- Interactive terminal emulation (PTY/ANSI cursor control)
- Server-side HTTP relays that execute commands (this project prefers not to expand server attack surface)
- Fully autonomous AI agents that execute commands without explicit user intent

## License

Apache-2.0. See `LICENSE`.

## Docs

- Plan: `docs/plan.md`
- Spec index: `docs/spec/00-index.md`
- Reference study notes: `docs/research/rikkahub-android-ui-study.md`
- Architecture: `docs/architecture.md`
- Threat model: `docs/threat-model.md`
- Server hardening guide: `docs/server-hardening.md`
- Contributing: `CONTRIBUTING.md`

## Clean-Room Note

This project is inspired by the UX ideas of various chat UIs (including RikkaHub), but is implemented from scratch. Please do not copy code from repositories that are under strong-copyleft licenses unless you fully understand and accept the licensing implications.
