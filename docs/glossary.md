# Glossary

Shared terminology used across specs and docs.

## Core Terms

- **Mode A**: Non-interactive SSH exec channel (no PTY/ANSI terminal emulation).
- **Codex mode**: Running remote `codex exec --json --full-auto` over SSH exec.
- **Known hosts**: Host key trust records keyed by host + port.
- **Complete output**: Full message-level output when displayed text is truncated.
- **Output truncation**: Display only a capped tail of output and offer complete output view/share.

## Runtime Terms

- **Exec run**: One SSH command execution cycle.
- **Structured event**: Parsed JSONL event emitted during Codex runs.
- **Connection pool**: Reused authenticated SSH clients (`SshConnectionPool`).

## UI Terms

- **Output bubble**: Chat timeline card rendering stdout/stderr.
- **Complete output dialog**: Modal showing full output with share action.
