# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Phase 1-5 refactor complete
- MessagePart sealed class (8 subtypes: Command, Stdout, Stderr, Reasoning, Code, Text, Error, Mermaid)
- Room Migration v4→v5 with zero data loss
- ChatViewModel split (529→269 lines, -49%)
- MaterialExpressiveTheme + ExtendColors (50 semantic colors)
- 21 Composable components aligned with RikkaHub UI
- Lucide Icons migration (19 icon replacements)
- PresetThemes (Sakura/Ocean/Spring/Autumn/Black)
- ChatFont system (Default/Serif/Monospace + fontSizeRatio)
- SshOutputMapper: SSH stdout/stderr → MessagePart bridge
- SSH engine: runBlocking elimination via CompletableDeferred
- Codex JSONL → MessagePart mapping
- ActionsSheet (copy/rerun/share/full_output/delete)
- ChatAvatar (user/assistant avatars)
- BranchSelector (message branching)
- NerdLine (exit code, duration, line count)
- DisplaySettingsScreen (bubbleOpacity, avatar toggles, font size)
- ProfileImportExport (JSON format)
- ProfileSearchFilter (search by name/host/tags)
- Session search (full-text), pin, archive, tags, statistics
- Session export (Markdown/JSON/HTML)
- Comprehensive spec suite (PRD/Architecture/Design/API/Security/Testing)
- Test infrastructure (Fakes, TestUtilities, Conventions)
- License/Privacy/Product compliance reports
- detekt configuration
- CODEOWNERS

### Changed
- ChatMessage: content → parts (List<MessagePart>)
- RikkaAgentTheme: MaterialExpressiveTheme + MotionScheme.expressive()
- SettingsScreen: added dynamic color toggle, theme selection, display settings
- ChatBubble: primaryContainer colors, Surface wrapping, 16dp corners
- ChainOfThought: i18n strings, Lucide icons, @Preview
- .gitignore: enhanced with modern patterns

### Fixed
- insertMessage: REPLACE → IGNORE + explicit update (prevents title clearing)
- codexApiKey: plaintext → EncryptedFile
- Thread title: no longer cleared on message insert
- SSH runBlocking: 6 instances eliminated via CompletableDeferred
- Markdown links: now clickable
- ANSI codes: properly stripped before rendering

### Security
- codexApiKey encrypted at rest (EncryptedFile, AES-256-GCM)
- Command injection prevention in CommandComposer
- HostKey mismatch requires explicit confirmation
- Sensitive info scanning (no hardcoded IPs/hostnames)
- License compliance verified (Apache-2.0 compatible)

## [0.1.0] - 2026-03-14

### Added
- Initial release
- SSH exec channel (Mode A)
- Chat-style UI
- Profile management
- Known Hosts verification
- Codex integration (basic)
- i18n (Chinese + English)
