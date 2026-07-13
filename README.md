# Pomodoro

Android Pomodoro timer that **fully blocks the phone** during a session. One
screen: set Focus (1–60 min) and Break (1–30 min), press Start. No pause, no
cancel, no leaving the app until the session ends. Emergency calls still work
(OS-guaranteed).

How it blocks: an accessibility service bounces any other app straight back,
device admin locks the screen if the service gets disabled mid-session and
blocks normal uninstall.

| Idle | Running |
|---|---|
| <img src="docs/idle.png" width="250" alt="Idle screen with sliders"> | <img src="docs/running.png" width="250" alt="Running session countdown"> |

## Build

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`).

```sh
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (signed with the debug
key — swap in a real keystore before distributing).

## Install from release (no computer)

1. On the phone, download the `.apk` from the [latest release](../../releases/latest).
2. Tap the downloaded file. When prompted, allow **Install unknown apps** for the app you downloaded with (e.g. Chrome or Files), go back and tap the file again.
3. Confirm the install (Play Protect may warn about an unrecognized developer → *Install anyway*).

Upgrading from an APK built on a different machine fails with "App not
installed" (different signing key) — uninstall the old version first (see
[Uninstall](#uninstall)).

## Install from source

Requires [Build](#build) above plus `adb` (Android SDK platform-tools) on the
computer.

Enable 'USB debugging' on phone:
1. Settings > About phone > Tap 'build number' 7 times to become 'developer'
2. Settings > System > Developer Options > Enable 'USB Debugging'

Connect the phone via USB (accept the "Allow USB debugging?" prompt), then:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

The "different signing key" caveat above applies here too: replacing a
version built elsewhere requires uninstalling it first.

On first launch tap **Grant permissions** — it walks you through enabling the
accessibility service and device admin in system settings. Both are required
before Start appears.

## Uninstall

Device admin blocks normal uninstall. First deactivate it under
*Settings → Security → Device admin apps → Pomodoro*, then uninstall as usual.

## Notes

- A session survives nothing: killing the process or rebooting ends it (no resume).
- minSdk 26, targetSdk 36. Kotlin + Jetpack Compose, no backend, settings stored on-device via DataStore.
