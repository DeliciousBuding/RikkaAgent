# Server Hardening Guide (SSH)

This project connects directly to your SSH server (`sshd`). The safest setup is to keep SSH locked down and avoid adding new remote-execution services.

## 1) Recommended `sshd_config`

At minimum:

- Disable password auth:
  - `PasswordAuthentication no`
- Disable root login:
  - `PermitRootLogin no`
- Use modern keys/ciphers (defaults vary by distro; keep OpenSSH updated)

After editing, reload sshd (command varies by distro).

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

## 4) Network Exposure

Best practice: keep SSH off the public internet when possible.

Options:

- Use a VPN (WireGuard/Tailscale) and firewall SSH to only the VPN range
- If SSH must be public, use fail2ban and rate limiting, and ensure passwords are disabled

## 5) Validate Host Fingerprints

On first connection, verify the displayed host fingerprint matches your server’s real fingerprint.

If the fingerprint changes later, treat it as suspicious unless you intentionally reinstalled the server.

