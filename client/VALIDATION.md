# Verification boundaries

Local environment: macOS ARM, JDK 17, Android SDK 36, no full Xcode, no Apple
signing identity, no Windows host yet. Native Apple signing/testing needs the
appropriate team; Companion has no VPN extension entitlement.

Verified locally so far:
- Android debug APK builds; installs and starts on API 36 emulator; no
  AndroidRuntime fatal error after launch.
- Shared JVM tests: 2 original parser tests + 6 service API tests + offline cache
  and logout isolation test. Desktop: 2 profile persistence/deletion tests.
- Android device test on API 36: Room migration 1→2 preserves the existing
  encrypted profile and adds nullable account/server columns (1 test passed).
- Desktop JVM Kotlin compilation, including the Windows DPAPI/service adapter.
- Kotlin compilation for iOS Simulator ARM64 and macOS ARM64.
- Swift syntax parsing with `-Xcc -fno-implicit-module-maps` avoids a duplicate
  SwiftBridging module in this machine's Command Line Tools. This is syntax
  validation, not an Xcode/SwiftUI or signed tunnel build.
- XcodeGen generates all schemes; plist and entitlement files pass plutil.
- Desktop preview runs on Mac. This preview has memory-only credentials and
  intentionally delegates actual macOS VPN support to the Apple target.

Final GitHub platform check 42439194540, source commit 1c64dbb, built Android,
Windows MSI/EXE, the free
iOS Companion, native iOS tunnel and native macOS tunnel with Xcode 16.4.
Apple builds use CODE_SIGNING_ALLOWED=NO; they do not establish device signing
or a working physical-device VPN. Windows installers are built on Windows,
but UAC/driver/traffic behavior still awaits the user's Windows computer.
Local jpackage Mac preview was ad-hoc signed after removing FinderInfo
from its generated bundle; it is not a signed/distributable Apple VPN app.

Final local check passed all 12 tests (9 shared, 2 desktop, 1 Android migration),
rebuilt the APK, and installed/launched it on API 36 without an AndroidRuntime
fatal error. APK Signature Scheme v2 verification passed. The final source ZIP
passed its CRC check. Installer checksums are included with the delivered files.

Live API smoke test used an explicitly approved temporary non-administrator
account. Shared AccountController logged in, loaded the active server,
bound its own device (#67), generated a London config through the queue and
reused it on the next request. Raw AWG content was not logged. QA account,
device, peer, config and queue records were removed. Full pre-existing DB rows
were compared against the fresh backup: unchanged; integrity_check=ok.
Post-cleanup counts: 15 users, 54 devices, 91 configs, 5 server rows. AWG ifindex
remained 18. Backup: /root/backups/inoagent-before-appqa-20261108/app.db on 72.56.33.224.
