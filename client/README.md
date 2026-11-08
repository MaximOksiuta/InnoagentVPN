# Inoagent VPN client

Kotlin Multiplatform client for https://vpn.oksiuta.ru with the same graphite,
copper route identity and saved server postcards as the website.

## Account and VPN workflow

Sign in with the existing website phone/password, or register and wait for
administrator approval. Formatted Russian/international phone input is
normalized by shared code. Recovery opens the existing Telegram flow.

The app loads authenticated servers, new-server notifications and host status.
On first connection it binds its own device in the account; another phone's
configuration is never silently reused. It downloads the device/server's
existing config or requests one via the persistent backend queue, then polls
and connects. Every AWG field is retained. Errors and pending approval remain
visible; queue timeouts leave work on the server for the next attempt.

Android and Windows share Compose UI. API/models/provisioning are shared by
all four platforms. Apple uses native SwiftUI and Network Extension.

Saved account metadata and downloaded configs are cached in each platform's
protected store. Reconnecting can use a previously downloaded config when the
site cannot be reached. Refresh requests current server data and makes the next
connection fetch the server's current config. Logout clears the saved session.

## Platforms

| Platform | Client | Tunnel |
|---|---|---|
| Android 8+ | Compose, encrypted Room/Keystore | AmneziaWG GoBackend, system permission, foreground notification |
| Windows | Compose Desktop, DPAPI secrets/profiles | Official AmneziaWG tunnel service, normal UAC |
| iOS/iPadOS 17+ | SwiftUI, Keychain, KMP API | Native Packet Tunnel / WireGuardKit; requires supported Apple signing |
| macOS 14+ | SwiftUI, Keychain, KMP API | Native Packet Tunnel / WireGuardKit; requires supported Apple signing |
| iOS Companion | Same account/server UI | Exports .conf to an installed AmneziaWG client; no bundled VPN extension |

Imported profiles remain supported. The Android Room migration 1→2 adds
account/server IDs without dropping old profiles. Android credentials and
configs use Keystore AES-GCM; Windows uses DPAPI; Apple uses Keychain. Passwords
are not persisted. Private configuration content is not sent to image services
or logs. Android foreground notification includes a disconnect action.

## Build

JDK 17 and Android SDK 36:

```sh
./gradlew :shared:desktopTest :desktopApp:test :androidApp:assembleDebug
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. This is a
debug-signed testing build; APK updates require the same signing key. A public
release needs a dedicated backed-up release key. Do not commit keystores.

Windows, JDK 17 and WiX 3:

```powershell
.\gradlew.bat :desktopApp:packageMsi :desktopApp:packageExe
```

Install the official AmneziaWG system runtime once. The optional
`desktopApp/windows/Install-Runtime.ps1` downloads its MSI from the official
GitHub release, verifies the published SHA-256, then shows the normal installer.
The Inoagent UI uses `%ProgramFiles%\AmneziaWG\amneziawg.exe` and normal UAC to
install/remove its own per-user named tunnel service. Active config files are
restricted to the user and SYSTEM. Closing the UI leaves the VPN service up;
use Disconnect to stop it. Imported config hooks executing commands are refused.
Local persistent client files live under `%LOCALAPPDATA%\InoagentVPN`.

Desktop UI preview on Mac: `./gradlew :desktopApp:run`. It uses memory-only
credentials and does not create a macOS VPN; use the native Apple target for VPN.

## Apple

Full Xcode, XcodeGen, Go and JDK 17 are required:

```sh
xcodegen generate --spec iosApp/project.yml
open iosApp/InoagentVPN.xcodeproj
```

Schemes: `InoagentVPN` (iPhone/iPad), `InoagentVPNMac` (Mac),
`InoagentCompanion` (iPhone/iPad config manager without a tunnel extension).
Choose your team and globally unique bundle identifiers. App and extension must
use the same team and Keychain group. Provider bundle ID derives from the app
bundle ID plus `.tunnel`, and must match the extension target.

Native VPN requires Network Extension signing/provisioning. A free Personal
Team can be used for the Companion account/config UI; it cannot provision our
Packet Tunnel entitlement. Companion stores configs in its own Keychain and
shares an explicit temporary .conf only after the user's Share action.

KMP targets include iOS ARM64/simulator ARM64, macOS ARM64/x64, Android and JVM.
Xcode build phases build the shared framework and the official AWG Go bridge.
`iosApp/project.yml` is the source of truth; regenerate after changes.

See `VALIDATION.md` for the exact verified build/runtime boundaries and
`APP-DESIGN.md` for identity decisions. Existing website data requires no
migration or replacement for these clients; all use the current API.
