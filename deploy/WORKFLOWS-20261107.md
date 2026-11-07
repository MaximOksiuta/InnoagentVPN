# Configuration workflows — 2026-11-07

Web/backend remains on 72.56.33.224; Telegram bot remains on 82.148.252.240.
No existing user, device, config, server or SSH-key rows are replaced. The migrations add generation and notification tables only.
Before changes, SQLite was snapshotted via SQLite backup API and checked (`integrity_check=ok`). Baseline: 15 users, 53 devices, 88 configurations, 5 server records. Backups are root-only under `/root/backups/inoagent-before-20261107` on both hosts, including backend SSH keys and the AWG container directory.

## Website

`/configurations` offers a server-first catalog for every user. Administrators can switch between their own configs and all users, filter by server/user/search, preview text/QR, and download individual files or a ZIP for the selection/current filter (up to 1000 files, 20 MB uncompressed). ZIP paths include user/server/config IDs. Private content is fetched only for explicit preview/download and sent with Cache-Control: no-store. Ordinary users cannot access another user's files, ZIP entries, metadata or jobs.
Archived server configurations remain accessible. Retired servers cannot be selected for new generation.

## Generation

`POST /api/generation/jobs` accepts serverId, optional deviceIds, requestKey, and administrator-only allUsers/optional userId. Omitted deviceIds means all owned devices (or the chosen admin scope). Existing configs are skipped, never overwritten. A partial unique index prevents two active queue items for one device/server. A repeated requestKey returns the same job.
The SQLite queue survives application restarts. Workers use the existing server mutation locks; two different servers can run concurrently, while work on one server is serialized. Up to sixteen items share a single authenticated SSH transport, with independent command channels. A single backend process owns the queue; do not run multiple replicas against this database without adding distributed leases.
Failed items can be retried without regenerating completed configs. The worker rechecks ownership, approval/ban state, device existence and server availability. Device deletion locks active server mutations before cleaning up peers.

`wg set` applies peers live. The former `ip link del` / `wg-quick up` cycle has been removed, so adding clients no longer tears down the VPN interface. Configuration and clientsTable writes use a restrictive umask and atomic rename. Before adding a peer, its generated client config is saved under `/opt/amnezia/awg/.inoagent/{userId}-{deviceId}.conf` inside the container; this enables an interrupted generation to reuse its keys. These files contain private keys, are mode 0600, are backed up with AWG data, and are removed when the corresponding peer is deleted through the application.
Unexpected exceptions are not returned to users with raw SSH output or key material. If a legacy peer exists remotely without a recoverable config, generation stops for manual recovery rather than silently creating another peer.

The old synchronous single-generation API remains compatible with existing clients; website and bot now submit queue jobs. The live interface and persistence format remain AmneziaWG-compatible.

## New-server notifications

New server creation atomically creates one inbox/outbox row per approved, non-banned user. Existing servers are not backfilled, to avoid sending an old-server broadcast during upgrade. Updating a card does not create a second announcement.
The website polls the inbox every 30 seconds. Each notification links to the server and can enqueue all of the recipient's devices.
The bot polls the privileged outbox every 30 seconds and uses an existing verified Telegram binding or an existing contact-verified bot session. Users without a Telegram link still get website notifications. Failed deliveries retry every five minutes, at most ten times; successful delivery is acknowledged separately from website read state. Delivery is at-least-once: a crash between Telegram send and acknowledgement can duplicate a notification. Configurations/private keys are never attached to broadcasts.
Telegram single/bulk generation also uses the queue, keeping the polling loop responsive. Inline job controls show progress and links to completed configs.

## Operations

Web release folder: `/root/InnoagentVPN-release-20261107`. Use `docker compose -p innoagentvpn -f docker-compose.yml -f deploy/web-20261107.override.yml up -d --no-deps backend frontend`.
Bot release folder: the same path on 82.148.252.240, with bot-20261107.compose.yml copied to docker-compose.yml. Use `docker compose -p inoagentbot up -d`.
The original named volumes remain in use. Do not use `down -v`.
Queue shutdown gets a 45-second grace period; unfinished records resume after restart. Inspect `/api/generation/jobs` and retry failed items after resolving SSH/server issues.

Rollback: the previous release directories and release-20261105 image tags are retained. Stop the new backend cleanly before switching back; old code ignores the additive tables, but pending jobs will wait until the new version runs again. Never restore an older DB over new user data just to roll back code.
