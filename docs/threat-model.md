# Threat Model (Draft)

This document captures major threats and mitigations for `rikka-agent`.

## Assets

- SSH private keys (and optional passphrases)
- `known_hosts` host key fingerprints
- Command history (may contain sensitive hostnames/paths)
- Command outputs (may contain secrets)

## Threats & Mitigations

### 1) Private Key Exfiltration (Device Compromise)

Risk:
- An attacker extracts private keys from device storage.

Mitigations:
- Store keys encrypted at rest using Android Keystore backed encryption.
- Avoid logging sensitive materials.
- Optionally require passphrase and allow “do not store passphrase”.

Residual risk:
- Rooted devices / malware with sufficient privileges can still capture secrets.

### 2) Man-in-the-Middle (MITM) on First Connection

Risk:
- First-use connection accepts a malicious host key.

Mitigations:
- Display host fingerprint and require explicit user confirmation.
- Store fingerprint in `known_hosts`.

### 3) Host Key Change (Server Reinstall / MITM)

Risk:
- Host key changes could indicate MITM.

Mitigations:
- Default behavior: block or require explicit override with clear warning.

### 4) Dangerous Commands (User Error)

Risk:
- Users run destructive commands.

Mitigations:
- Prefer explicit user intent.
- Optional “dangerous command confirmation” (later milestone).

### 5) Sensitive Output Leakage

Risk:
- Outputs contain credentials, tokens, private key material, secrets.

Mitigations:
- Do not auto-upload outputs.
- Warn on export/share.
- Optional heuristic redaction warnings.

