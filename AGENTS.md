# AGENTS.md (rikka-agent)

This file defines repository-specific guidance for coding agents and contributors.

## Mission

Build a production-quality Android (Jetpack Compose) app that provides a chat-style UI to run SSH commands (Mode A: non-interactive `exec`) and render outputs beautifully (Markdown/code blocks), with security-by-default.

## Project Planning (SSOT)

- Roadmap: `ROADMAP.md`
- Running state / decisions: `ROADMAP_STATE.md`
- Specs: `docs/spec/00-index.md`

## Non-Negotiables (Security & Privacy)

- Never commit or paste secrets:
  - SSH private keys, passphrases, tokens, cookies, certificates
  - real hostnames/IPs/usernames from a user's private infrastructure
- Do not read sensitive OS folders (e.g. `.ssh`, `.aws`) unless the user explicitly authorizes it and it is necessary.
- Treat all command outputs as potentially sensitive. Avoid logging raw outputs in CI or debug logs.

## Licensing / Clean-Room

- This repository is Apache-2.0.
- Use other projects (including RikkaHub) for UX inspiration only.
- Do not copy code from strong-copyleft repositories (e.g. AGPL/GPL) into this repo unless the licensing implications are explicitly accepted for this repo.

## Scope: Mode A First

- Focus on `exec` commands over SSH + streaming stdout/stderr.
- Do NOT implement interactive terminal emulation (PTY/ANSI cursor) in early milestones.
- Do NOT add a server-side HTTP remote-exec relay as a default path (attack surface).

## Dev Workflow Expectations

- Prefer small, reversible changes.
- Add docs before large refactors.
- Include a brief verification note with changes (what was run / what was checked).
