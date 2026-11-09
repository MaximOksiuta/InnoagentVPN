# Release 2026-11-05

Web/API host: `72.56.33.224`, release directory `/root/InnoagentVPN-release-20261105`.
Bot host: `82.148.252.240`, same release directory. HTTPS probe stays on the VPN node `186.246.18.21`.

## Data safety

Before changes, a consistent snapshot was taken with backend/bot stopped briefly:
`/root/backups/inoagent-before-20261105/data.tgz` and `source.tgz` on the web host.
The data archive contains both SQLite and bot sessions and is sensitive. Keep it root-only.
The SQLite volume remains `innoagentvpn_backend_data`; never use `docker compose down -v`.
The bot volume on the new host is `inoagentbot_bot_data`. Stop the old polling container before copying sessions and starting the new one.

## Services

Web: `docker compose -p innoagentvpn -f docker-compose.yml -f deploy/web-release.override.yml up -d --no-deps backend frontend` from the web release directory.
Bot on `82.148.252.240`: copy `deploy/bot-only.compose.yml` to `docker-compose.yml`, and provide a root-only `.env` containing `TG_BOT_TOKEN` and `SUPER_KEY`. Run `docker compose -p inoagentbot up -d` after loading/building `inoagentbot-tg_bot:release-20261105`.
Probe on `186.246.18.21`: use `deploy/probe.compose.yml` and `deploy/Caddyfile`. It does not require bot credentials. The initial combined compose file is retained only as a build recipe; do not start a second bot there.
Only one polling bot instance may run at a time.

## Recovery

The website links to `https://t.me/inoagentvpnbot?start=reset`; `/reset` and the bot menu work too.
The bot accepts the Telegram sender's own contact only, in a private chat, during an active reset flow.
The privileged backend issues a cryptographically random, single-use token, stores its SHA-256 hash, enforces a 60-second request interval, and expires it after ten minutes. Existing Telegram bindings must match.
The token travels in the URL fragment, then is removed from the address bar. Passwords are entered on the website, hashed with bcrypt, and not sent to Telegram. A successful reset revokes older website JWTs and all outstanding reset links for that user.

## Retiring servers

Previously DELETE returned 409 whenever saved configurations referenced a server.
DELETE now sets `servers.archived = 1`, removes it from the pool and prevents new generation, preserving every existing device/configuration and remote peer. It does not uninstall VPN software or revoke existing VPN connections.
Administrators can restore an archived row after review with `UPDATE servers SET archived = 0 WHERE id = ...`; no existing rows are automatically archived during deployment.

## Monitoring

The site and bot use `GET /api/servers/status` for ICMP echo and TCP reachability of the stored SSH address and port. The API requires authentication, exposes no credentials, and caches samples for 25 seconds. The backend image supplies `iputils-ping`. A missing ICMP reply is reported separately from SSH reachability.

The browser tries the HTTPS health URL derived from each public IPv4 address automatically. When HTTPS, TLS, or CORS prevents a result it shows “Не подтверждено”. A node can serve the optional `/_inoagent/health` endpoint with a valid certificate and website CORS to provide a positive browser result. The server-side ICMP and SSH checks require no per-node setup.

The bot polls the same measurements every 30 seconds and notifies approved administrators with a linked Telegram ID or a contact-verified bot session after two fresh samples agree on a change. It keeps its baseline in `/app/data/server-status-alerts.json`. Initial readings and repeated cached samples do not trigger alerts.

On the Vienna host, apply `bot-20261109-monitor.override.yml` over the existing bot Compose file. The bot has a dedicated bridge that sends outbound traffic from `83.141.80.158`, the address with a working route to the site API. Its existing data volume is retained.

## Design

Graphite control-panel direction: neutral surfaces, one restrained teal accent for successful checks, consistent spacing, compact controls, clear focus states, responsive layouts, reduced-motion support. No image generation is needed for this dashboard.
Design workflow: [unslop by Damir Sokolovsky](https://github.com/heydamir/unslop), CC BY 4.0. Source credit is retained in `graphite.css`.

## Validation

Run `./gradlew test` in backend and bot, and `npm ci && npm run lint && npm run build` in frontend.
Verify `/health`, unauthenticated recovery rejection, an authenticated status read, HTTPS probe TLS/CORS, matching user/device/configuration counts and SQLite integrity after deployment.
Complete a real Telegram contact-sharing reset manually from the account owner; never reset a real user's password just for a smoke test.

Post-deployment verification: SQLite integrity is `ok`; SHA-256 hashes of all existing columns and all rows of users/devices/device_servers/servers exactly match the pre-change backup (15 users, 53 devices, 75 configs, 4 servers). The bot sessions file hash also matches before/after migration. Both runtime containers are running; the old bot is stopped.
Frontend production dependencies pass `npm audit --omit=dev`; seven moderate findings remain in development-only ESLint tooling.
The public login screen was visually checked after deployment. Browser checks on older hosts require provisioning their HTTPS probes. The VPN node `186.246.18.21` became unreachable over SSH/HTTPS during final checks, so its probe/certificate and staging-credential cleanup could not be confirmed.

## Added VPN node

At the user's subsequent request, `82.148.252.240` was added as `London`, server #5, with `amnezia-awg2`, `awg0`, `/opt/amnezia/awg`, SSH root on port 22. London/UK is the GeoIP label, not a physical-location guarantee.
After explicit approval, a dedicated Ed25519 service key was generated in the persistent backend volume at `/app/data/ssh/server-82.148.252.240` (private mode 0600, owned by uid 10001). Only its public key was installed on the VPN host, with `restrict,from="72.56.33.224"`. The prior authorized_keys was backed up on that host under `/root/backups/inoagent-before-20261105/authorized_keys.before-vpn-server`.
A read-only `AwgServerScripts.listUsers()` invocation from the actual backend container authenticated and read the existing AWG peer successfully before creation. No new VPN peer was created for testing.
The new node also runs `inoagentprobe-probe-1` using `Caddyfile.london` and `probe.compose.yml`; the web release override explicitly allows its browser probe.
The four previous cards were subsequently retired through DELETE requests in the live website; their rows, saved configurations and peers remain preserved. They were not automatically retired by migration.
