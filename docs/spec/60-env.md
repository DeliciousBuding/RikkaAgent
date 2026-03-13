# Environments Spec (Dev vs Prod)

Android apps typically distinguish environments via build variants.

## 1) Build Variants

We will separate:

- `debug` (development)
- `release` (production)

Optionally, we may add product flavors later:

- `dev`
- `prod`

## 2) Dev Environment Requirements

- Verbose logging allowed (but never secrets).
- Allow connecting to test hosts (user-supplied).
- Developer options (e.g. show SSH library diagnostics) may be available.

## 3) Prod Environment Requirements

- Minimal logging (no sensitive content).
- Strong defaults:
  - strict host verification enabled
  - safe export prompts
- Disable any debug/test tooling.

## 4) Configuration Injection

No secrets should be bundled in the app.

If we need runtime toggles:

- use BuildConfig fields for non-sensitive toggles (e.g. enableDiagnostics)
- use encrypted local storage for user-provided secrets (keys)

## 5) CI Considerations

- `./gradlew test` should run in CI without device secrets.
- Instrumentation tests must not depend on real servers by default.
- Refer to `docs/verification.md` for local checks and `docs/troubleshooting.md` for setup issues.
