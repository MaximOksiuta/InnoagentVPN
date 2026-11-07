# Verification boundaries

Local environment: macOS ARM, JDK 17, Android SDK 36, no full Xcode, no Apple
signing identity, no Windows host yet. Native Apple signing/testing needs the
appropriate team; Companion has no VPN extension entitlement.

Verified locally so far:
- Android debug APK builds; installs and starts on API 36 emulator; no
  AndroidRuntime fatal error after launch.
- Shared JVM tests: 2 original parser tests + 6 service API tests.
- Desktop JVM Kotlin compilation, including the Windows DPAPI/service adapter.
- Kotlin compilation for iOS Simulator ARM64 and macOS ARM64.
- Swift syntax parsing with `-Xcc -fno-implicit-module-maps` avoids a duplicate
  SwiftBridging module in this machine's Command Line Tools. This is syntax
  validation, not an Xcode/SwiftUI or signed tunnel build.
- XcodeGen generates all schemes; plist and entitlement files pass plutil.
- Desktop preview runs on Mac. This preview has memory-only credentials and
  intentionally delegates actual macOS VPN support to the Apple target.

Remaining platform checks must be reported explicitly: Windows UAC/service/
Wintun and installer execution; Apple Xcode builds, signing, device entitlement
provisioning and a real handshake. Passing common tests alone proves none of
those. Local jpackage Mac preview was ad-hoc signed after removing FinderInfo
from its generated bundle; it is not a signed/distributable Apple VPN app.
