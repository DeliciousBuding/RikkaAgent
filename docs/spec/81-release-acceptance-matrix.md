# Release Acceptance Matrix

This matrix defines practical release gates for `rikka-agent`.

| Area | Gate | Verification |
|---|---|---|
| Build | Dev debug APK builds successfully | `./gradlew assembleDevDebug` |
| Tests | Unit tests pass | `./gradlew test` |
| Tests (modules) | Key modules pass focused suite | `./gradlew :core:model:testDebugUnitTest :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDevDebugUnitTest` |
| Lint | No blocking lint failures | `./gradlew :app:lintDevDebug` |
| Verification | Local checklist followed | `docs/verification.md` |
| Security | No secrets committed | Manual review + CI checklists |
| SSH Core | Password + key auth paths compile and pass tests | core/app tests |
| Output Safety | Truncation path covered | `OutputFormatterTest` |
| Codex Path | JSONL parser tests pass | `JsonlParserTest` |
| Docs | README/ROADMAP/STATE/ARCHIVE updated | PR checklist |

## Pre-Tag Checklist

1. Re-run full CI locally.
2. Re-run focused module suite for quick regression confidence.
3. Run instrumentation tests if UI/platform flows changed.
4. Review changelog impact in `ARCHIVE.md`.
5. Ensure release notes mention security-relevant changes.
6. Confirm artifacts are generated and downloadable in Actions.
