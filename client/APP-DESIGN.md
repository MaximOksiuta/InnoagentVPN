# Inoagent client, 2026-11-08

Design skill: anthropics/skills/frontend-design. Identity shared with vpn.oksiuta.ru.
Graphite #141415, panel #1d1d20, chalk #f0ede7, copper #e5aa86, muted #a9a5a0.
System sans fonts, 32/24/20/14 scale, left aligned. No glass/aurora/glow treatments.

    [route mark / inoagent vpn]                         [website]
    [Connection] [Import] [Account]
    [animated route / real tunnel state / connect]
    [server postcard / name / location / actual availability]

The route mark and moving signal repeat the website identity. The same stored
SVG postcards come from the backend. The large connection element is a path
and a clear button, replacing the old glowing orb. Motion belongs to active
operations; Apple Reduce Motion freezes it. Native text fields use platform
password autofill/secure entry, clear loading states, and visible errors.
Android/Windows share Compose UI; iOS/macOS use native SwiftUI with the same
structure and the shared Kotlin HTTP/configuration workflow.
