# Data Spec

## 1) Data Entities

### 1.1 SSH Profile

Fields (conceptual):

- `id`
- `name` (friendly label)
- `host`
- `port`
- `username`
- `authType` (initially: private key)
- `keyRef` (reference to encrypted storage, not raw key)
- `hostKeyPolicy` (strict by default)

### 1.2 Known Hosts Entry

- `host`
- `port`
- `keyType`
- `publicKey`
- `fingerprintSha256`
- `addedAt`
- `lastVerifiedAt`

### 1.3 Command History Entry

- `profileId`
- `command`
- `createdAt`
- `pinned` (bool)

### 1.4 Command Run (Timeline)

- `commandId`
- `profileId`
- `startedAt`
- `endedAt`
- `exitCode` (nullable)
- `status` (running/succeeded/failed/cancelled)
- `stdoutSizeBytes`, `stderrSizeBytes`
- `truncated` flags

## 2) Persistence Strategy

Default:

- Store profiles + history locally using **Room** (structured queries + migrations).
- Store app preferences (rendering toggles, haptics, safety defaults) using **DataStore**.
- Store private keys only in encrypted storage, referenced by `keyRef`.
- Store known_hosts locally and treat it as security-critical state.

Outputs:

- Store outputs locally in a bounded manner (size limits).
- Avoid indexing outputs by default.

## 3) Import/Export

Import:

- private key import from file/text
- optional support for OpenSSH key formats (subject to library support)

Export:

- export output as plain text or Markdown
- show warning before export
