# Security Spec

## 1) Security Objectives

- Prevent silent MITM (strict host key verification).
- Encrypt SSH private keys at rest on device.
- Minimize leakage via logs/screenshots/exports.
- Avoid expanding server attack surface (no mandatory remote-exec HTTP relay).

## 2) Sensitive Data Handling

Sensitive data includes:

- private keys
- passphrases
- trusted host keys / fingerprints
- command output (may contain secrets)

Rules:

- never commit sensitive materials
- do not write private key content to logs
- store keys encrypted using Android Keystore backed encryption
- default to not syncing/storing outputs outside the device

## 3) Host Key Verification

### 3.1 First Use

- show SHA256 fingerprint
- require explicit confirmation
- persist to known hosts store

### 3.2 Mismatch

Default behavior: block.

Allow override only after a second, explicit confirmation.

## 4) Command Safety

Mode A is user-driven:

- Commands must be typed/pasted by the user, or selected from user-saved pins.
- If future AI integrations exist, the app must require explicit user confirmation before executing any AI-suggested command.

## 5) Export / Share Warnings

On export/share:

- warn that outputs may contain sensitive data
- allow “copy redacted” option in a future milestone

## 6) Server-Side Guidance

We provide `docs/server-hardening.md` to encourage:

- disabling password auth
- disabling root login
- restricting authorized_keys options
- limiting SSH exposure via VPN/firewall
