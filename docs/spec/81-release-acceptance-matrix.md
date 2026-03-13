# Release Acceptance Matrix

This matrix defines practical release gates for `rikka-agent`.

| Area | Gate | Verification |
|---|---|---|
| Build | Dev debug APK builds successfully | `./gradlew assembleDevDebug` |
| Tests | Unit tests pass | `./gradlew test` |
| Lint | No blocking lint failures | `./gradlew :app:lintDevDebug` |
| Security | No secrets committed | Manual review + CI checklists |
| SSH Core | Password + key auth paths compile and pass tests | core/app tests |
| Output Safety | Truncation path covered | `OutputFormatterTest` |
| Codex Path | JSONL parser tests pass | `JsonlParserTest` |
| Docs | README/ROADMAP/STATE/ARCHIVE updated | PR checklist |

## Pre-Tag Checklist

1. Re-run full CI locally.
2. Review changelog impact in `ARCHIVE.md`.
3. Ensure release notes mention security-relevant changes.
4. Confirm artifacts are generated and downloadable in Actions.
