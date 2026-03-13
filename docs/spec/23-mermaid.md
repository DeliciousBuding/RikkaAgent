# Mermaid Rendering (Optional)

## 1) Goal

Provide optional Mermaid diagram rendering for assistant outputs while keeping Mode A security posture unchanged.

## 2) Scope

- Parse fenced code blocks with language `mermaid`
- Render diagrams only in assistant output bubbles
- Keep a fallback text view when rendering fails

Out of scope:

- Arbitrary remote JS execution
- User-provided external Mermaid plugins/themes

## 3) Security Constraints

- Use sandboxed WebView with JavaScript enabled only for local Mermaid runtime
- Disable file access and universal access from file URLs
- Do not allow network fetches from Mermaid content
- Escape/validate markdown input before injecting into HTML template

## 4) UX Behavior

- Default collapsed state for large diagrams
- Actions: expand/collapse, copy source, retry render
- On render error: show source block with "Render failed" badge

## 5) Performance

- Render off main thread where possible
- Cache diagram source hash -> rendered HTML snapshot key
- Debounce re-renders during streaming; only render on final message

## 6) API Design (Draft)

- `MermaidRenderer.render(source: String): MermaidRenderResult`
- `MermaidDiagramCard(source: String, state: MermaidRenderState)`
- App setting: `Enable Mermaid rendering` (default off)

## 7) Test Targets

- Unit: mermaid fence detection, hash key generation, fallback behavior
- UI: expand/collapse interaction and error state rendering
- Security: WebView flags and blocked network access assertions

## 8) Rollout Plan

1. Add feature flag and docs-only acceptance criteria
2. Implement static template renderer + fallback
3. Add UI tests and performance baseline check
4. Enable by default only after stability confirmation
