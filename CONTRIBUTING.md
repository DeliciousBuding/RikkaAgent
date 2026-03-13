# Contributing

Thanks for your interest in contributing to `rikka-agent`.

## Ground Rules

- Be respectful and constructive.
- Security-related issues: please follow `SECURITY.md`.
- Do not include secrets (private keys, tokens, credentials) in issues, logs, or pull requests.

## Development Status

This repository is currently in early development. The first milestone focuses on:

- SSH profiles + `known_hosts`
- non-interactive SSH command execution (`exec`)
- streaming output rendering in a chat-style UI

## Clean-Room Guidance

This project is implemented from scratch. Please do not copy code from projects under strong-copyleft licenses unless the licensing implications are explicitly accepted for this repository.

## How To Propose Changes

- Open an issue describing the problem and the proposed solution.
- For PRs, keep changes small and focused.
- Include a short testing note in the PR description (what you verified).

## Local Verification Checklist

Before opening a PR, follow the canonical checklist in `docs/verification.md`.

Baseline commands:

```bash
./gradlew test
./gradlew :app:lintDevDebug
./gradlew assembleDevDebug
```

If you changed UI flows or platform integration, also run:

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

And verify:

- changed behavior is covered by tests when practical
- `ROADMAP.md` / `STATE.md` / `ARCHIVE.md` are updated when milestones or facts change
- no secrets are present in code, docs, logs, or screenshots
