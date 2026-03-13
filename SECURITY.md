# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please do not open a public issue with sensitive details.

Instead, report it privately to the maintainer(s). Include:

- impact (what can be exploited)
- reproduction steps
- affected versions/commits
- suggested remediation (if any)

## Scope

This project deals with sensitive materials (SSH private keys, host fingerprints, command outputs). Please be careful when sharing logs or screenshots.

## Operational Security Guidance

- Prefer SSH key authentication over password authentication.
- Use a dedicated low-privilege SSH user for app access.
- Keep host key verification enabled (do not disable mismatch warnings).
- Avoid storing production secrets in profile names, commands, or chat messages.
- Treat exported session text as sensitive data.
- For local setup and CI troubleshooting, see `docs/troubleshooting.md`.

## Responsible Disclosure Expectations

- Provide a minimal proof-of-concept when possible.
- Include risk severity (data exposure, remote execution, auth bypass, etc.).
- Allow maintainers reasonable time to patch before public disclosure.

## What Not To Report

- UI copy or typo issues without security impact.
- Missing best-practice hardening on third-party servers outside this repo.
