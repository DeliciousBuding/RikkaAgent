# Release Spec

## 1) Release Artifacts

- Android App Bundle (`.aab`) for Play Store (optional)
- APK for GitHub Releases (optional)

## 2) Versioning

Recommended:

- Semantic-ish: `0.x` while APIs are evolving
- `1.0` when core interfaces stabilize

## 3) Release Checklist (High Level)

- Verify `release` build works
- Verify host verification UX is correct
- Verify no debug-only features leak into release
- Verify `.gitignore` prevents keys/secrets from being committed
- Update CHANGELOG (later)

