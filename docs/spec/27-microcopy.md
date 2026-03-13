# Microcopy Spec (UI Text)

This document defines **tone + exact message requirements** for high-risk or high-friction flows in `rikka-agent`.

Principles:

- Calm, direct, non-technical where possible.
- Always include the actionable next step.
- For security warnings, be explicit about the risk (MITM, key theft) without fearmongering.
- Do not show secrets in UI by accident.

## 1) Tone

- Short sentences.
- Avoid jokes in security prompts.
- Use consistent verbs:
  - `Trust`, `Cancel`, `Reconnect`, `Run`, `Stop`, `Export`, `Copy`.

## 2) Host Key Verification

### First Connection (Unknown Host Key)

Title:

- `Trust this host?`

Body (must include):

- `Host:` `<host>:<port>`
- `User:` `<username>`
- `Fingerprint (SHA256):` `<fingerprint>`
- One-line explanation:
  - `You should only trust this fingerprint if it matches the server you intend to connect to.`

Actions:

- Primary: `Trust and continue`
- Secondary: `Cancel`

### Host Key Mismatch (Fingerprint Changed)

Title:

- `Host key changed`

Body (must include):

- `This may indicate a man-in-the-middle attack, or the server was reinstalled.`
- `Host:` `<host>:<port>`
- `Previous fingerprint:` `<old>`
- `New fingerprint:` `<new>`

Actions:

- Primary (safe): `Cancel`
- Secondary (danger): `Replace fingerprint…`
  - Must open a second confirmation.

Second confirmation (required):

- Title: `Replace fingerprint?`
- Body:
  - `Only continue if you have verified the new fingerprint through a trusted channel.`
- Actions:
  - Primary: `Replace`
  - Secondary: `Cancel`

## 3) Authentication Errors

### Key Rejected / Auth Failed

Title:

- `Authentication failed`

Body:

- `The server rejected the provided credentials.`
- Optional details:
  - `User:` `<username>`
  - `Key:` `<key name / fingerprint>` (never show full key)
- Suggestions (short):
  - `Check that the public key is in authorized_keys, and the user has access.`

Actions:

- Primary: `Edit profile`
- Secondary: `Retry`

## 4) Connection Errors

### Network Error

Title:

- `Connection error`

Body:

- `Unable to reach the server.`
- Optional:
  - `Host:` `<host>:<port>`

Actions:

- Primary: `Reconnect`
- Secondary: `Cancel`

### Disconnected

Banner text:

- `Disconnected`

Actions:

- `Reconnect`

## 5) Command Execution

### Running

Inline status (subtle):

- `Running…`

Primary button state:

- label/icon: `Stop`

### Exit Code

If exit code is available:

- `Exit code: <n>`

If not available:

- `Completed`

### Timeout

Title:

- `Command timed out`

Body:

- `The command did not finish within the configured timeout.`

Actions:

- Primary: `Run again`
- Secondary: `Edit timeout`

## 6) Export / Copy Warnings

Export confirmation (when enabled):

Title:

- `Export output?`

Body:

- `Command outputs may contain sensitive information (tokens, keys, IPs).`
- `Make sure you trust the destination app or storage location.`

Actions:

- Primary: `Export`
- Secondary: `Cancel`

Copy confirmation (toast/snackbar):

- `Copied`

## 7) Truncation Messaging

When output is truncated:

- Badge text: `Truncated`
- Help text:
  - `Large output was trimmed for performance. Use Export to save the complete output.`

## 8) Localization Strategy (Later)

For v1:

- English-only or bilingual is acceptable.
- Microcopy strings must be centralized (single source of truth) so community can translate.
