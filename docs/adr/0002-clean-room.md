# ADR 0002: Clean-Room Implementation

## Status

Accepted (2026-03-11)

## Context

We want `rikka-agent` to be permissively licensed (Apache-2.0) and broadly usable.
Some reference projects for UI inspiration may be under strong copyleft licenses.

## Decision

Implement `rikka-agent` from scratch:

- Use reference projects for UX ideas only.
- Do not copy code or substantial implementation details from strong-copyleft repositories unless the repository license is explicitly changed/accepted.

## Consequences

- More initial engineering effort.
- Clearer licensing and easier adoption.

