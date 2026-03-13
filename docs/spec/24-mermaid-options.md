# Mermaid Implementation Options (v1)

## Option A: WebView + local Mermaid bundle

Pros:
- Strong compatibility with Mermaid syntax
- Fastest path to feature completeness

Cons:
- WebView lifecycle complexity
- JS execution surface requires strict hardening

Security controls:
- Disable file access and universal access from file URLs
- No external network requests
- Local-only HTML template and bundled JS

## Option B: Server-side render (not recommended for default)

Pros:
- No JS runtime on device

Cons:
- Requires network dependency and extra attack surface
- Violates current architecture preference (direct SSH app path)

## Option C: Native parser subset (long-term)

Pros:
- Minimal runtime surface
- Better Compose integration

Cons:
- Very high implementation cost
- Incomplete Mermaid compatibility initially

## Recommendation

Adopt **Option A** behind a feature flag, with strict WebView hardening and fallback to source code view on render errors.

## Acceptance criteria

1. Toggle in settings (`Enable Mermaid rendering`)
2. Render only finalized assistant messages (not streaming)
3. Error fallback card with source + retry
4. Dedicated test cases for WebView security settings
