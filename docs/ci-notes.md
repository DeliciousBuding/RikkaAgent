# CI Notes

Quick reference for the GitHub Actions workflow and artifacts.

## Workflow

- Workflow file: `.github/workflows/ci.yml`
- Branches: `master` (push + PR)
- Steps:
  - Unit tests (`./gradlew test`)
  - Focused module tests
  - Lint (`:app:lintDevDebug`)
  - Build APK (`assembleDevDebug`)

## Artifacts

- APK: `app-dev-debug`
- Unit test reports: `unit-test-reports`
- Lint reports: `lint-reports`

## CI Summary

The workflow publishes a step summary with:

- test XML count
- test case count
- failure/skipped counts
- lint issue count
- APK size

## Common Warnings

- GitHub Actions runtime deprecations (Node versions) may appear as warnings.
  The workflow currently opts into Node 24 via `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true`.
