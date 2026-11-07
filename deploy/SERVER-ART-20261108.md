# Server postcards and route loaders — 2026-11-08

SVG postcards are generated entirely on the backend, without external image
services or dependencies. NFKC-normalized, trimmed, lowercase server names are
SHA-256 seeds. Known city aliases choose a landmark; unknown names select one
of four landscape types. The seed also selects one of six palettes, sky details
and sun geometry. Identical normalized names reuse the same postcard.

Files: `/app/data/server-art/v1-{sha256}.svg` in the existing backend_data volume.
Startup backfills all existing names, including archived servers. Create/update
prepares the image before saving the server. Rename produces a new image; older
files remain for existing caches. Writes use a temporary file and atomic move.
Public GET `/api/server-art/{serverId}` serves only generated SVG, never server
connection data or arbitrary paths. There is no user-provided markup, scripts,
external references or text in the SVG. Responses use image/svg+xml, nosniff,
restrictive CSP, ETag/304 and a one-hour cache. Frontend URLs include the name
as a cache version, so renaming updates the visible image immediately.

The shared ServerName component is used in status checks, server admin lists,
the configuration catalog and previews, device configs/QR dialogs, queue jobs,
notifications and a keyboard-accessible illustrated server picker. Images are
decorative beside the actual text name; failed images have a neutral fallback.

Route loaders indicate real waits at page, status, action-button and job level.
They have status announcements, no invented progress, and reduced-motion rules.
No artificial waiting is added in production. A local delayed proxy was used
only for visual inspection on dummy accounts. Desktop, 390px layout, picker
selection/Enter/Escape and loading states were inspected.

Validation: 36 backend tests pass, including stable persistence/concurrent
requests, SVG parsing of hostile names, safe paths, API caching and rename.
Frontend lint and production build pass. Bot is unchanged.

Deploy on 72.56.33.224 from `/root/InnoagentVPN-release-20261108-art`:
`docker compose -p innoagentvpn -f docker-compose.yml -f deploy/web-20261108-art.override.yml up -d --no-deps backend frontend`.
Reuse the existing volume and .env. Fresh online SQLite backup goes under
`/root/backups/inoagent-before-20261108-art`, mode 0700, database mode 0600.
Rollback uses release-20261107 images and the same current database; no schema
or user/server/config rows are changed by this feature. Preserve server-art in
normal volume backups. Never restore an older database merely to roll back UI.

Live verification: five server names produced five files in the persistent
volume, including archived records. All five public URLs returned valid SVG
with HTTP 200 and conditional HTTP 304; file bytes matched the saved files and
modification timestamps did not change on reads. /health returned 200 and the
published CSS contained the new loader/postcard styles. All pre-switch rows in
users/devices/device_servers/servers were compared in full: unchanged. Counts
after rollout: 15 users, 53 devices, 90 configs, 5 servers; integrity_check=ok.
Fresh backup app.db SHA-256:
`e9d3c12b476b81b5fae91efcc7699f912e1279cf9c006e27bd4c27cb5d94cac6`.
