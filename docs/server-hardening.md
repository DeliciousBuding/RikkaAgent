# Server Hardening Guide (SSH)

This project connects directly to your SSH server (`sshd`). The safest setup is to keep SSH locked down and avoid adding new remote-execution services.

## 0) Philosophy (Read First)

- This guide is **defense-in-depth**, not a guarantee.
- Prefer “VPN-only SSH” over exposing SSH to the public internet.
- Avoid convenience features that expand blast radius (password auth, root login, agent forwarding).

## 1) Recommended `sshd_config` Baseline

At minimum:

- Disable password auth:
  - `PasswordAuthentication no`
- Disable root login:
  - `PermitRootLogin no`
- Use modern keys/ciphers (defaults vary by distro; keep OpenSSH updated)

Recommended extras (pick what fits your environment):

- Disable keyboard-interactive auth (often used for OTP but also for password-like flows):
  - `KbdInteractiveAuthentication no`
- Limit which users can log in:
  - `AllowUsers rikka` (or your chosen dedicated user)
- If you must keep SSH public, reduce brute force surface:
  - `MaxAuthTries 3`

After editing, reload sshd (command varies by distro). Always keep an existing session open while testing changes.

## 2) Create a Dedicated Low-Privilege User

Create a user specifically for this app (e.g. `rikka`):

- no sudo by default
- restrict filesystem permissions to only what is needed

## 3) Restrict the Key in `authorized_keys`

For the key used by the app, consider adding OpenSSH key options:

- `no-port-forwarding`
- `no-agent-forwarding`
- `no-X11-forwarding`

If your phone uses a stable VPN or static IP, you can also restrict the source:

- `from="x.x.x.x/yy"`

Notes:

- Avoid `command="..."` forced commands for this project, because it prevents arbitrary exec commands.
- If you need command-level restrictions, create a dedicated “ops wrapper” user and expose only the commands you want (v2 idea; out of scope for this repo’s v1).

## 4) Network Exposure

Best practice: keep SSH off the public internet when possible.

Options:

- Use a VPN (WireGuard/Tailscale) and firewall SSH to only the VPN range
- If SSH must be public, use fail2ban and rate limiting, and ensure passwords are disabled

## 5) Validate Host Fingerprints (First-Use Trust)

On first connection, verify the displayed host fingerprint matches your server’s real fingerprint.

If the fingerprint changes later, treat it as suspicious unless you intentionally reinstalled the server.

How to obtain the fingerprint on the server depends on your host key type. Common locations:

- `/etc/ssh/ssh_host_ed25519_key.pub`
- `/etc/ssh/ssh_host_ecdsa_key.pub`
- `/etc/ssh/ssh_host_rsa_key.pub`

Most distros include `ssh-keygen`, which can print fingerprints from a public key file.

## 6) Backups & Rollback

Before changing SSH configuration:

- copy the existing config to a backup file
- ensure you have an alternative access path (cloud console / KVM / provider panel)

If you lose SSH access after a change:

- revert using the provider console
- restart/reload `sshd`

## 7) Running Codex on the Server (Optional, v1)

If you want `rikka-agent` to run Codex on the server (recommended, to keep API keys off the phone):

- Install the `codex` CLI on the server following its official documentation.
- Authenticate on the server (so the Android device never needs to store LLM API keys).
- Run Codex under the same dedicated low-privilege user you use for SSH (e.g. `rikka`).

Hardening notes:

- Treat Codex outputs as sensitive (they may echo environment details or file paths).
- Do not put API keys in shell history:
  - prefer environment variables via a root-owned config file + systemd drop-in
  - or use the CLI's interactive login flow on the server
- If you use `tmux` to keep long sessions, lock down who can access that user account.
