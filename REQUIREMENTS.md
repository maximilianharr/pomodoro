# REQUIREMENTS

Android app that fully blocks the phone for one Pomodoro session (focus + break).

## Design
- lean and simple
- two colors, dark and bright gray

## Screen (single screen, two states)

**Idle**
- Two sliders: Focus 1-60 min (default 25), Break 1-30 min (default 5)
- Last-used values persisted (DataStore)
- "Start" button
- If a required permission is missing, sliders/Start are replaced by a "Grant Permissions" button that walks through system settings screens one at a time

**Running**
- Phase label ("Focus" / "Break") + countdown
- No cancel button
- Short vibration pulse on Focus->Break and on session end
- Back to idle when session finishes

## Blocking behavior

- One session = Focus phase then Break phase, back-to-back, no pause
- Everything is blocked in both phases except emergency calls (OS-guaranteed, no allowlist)
- Home/Recents also bounce back into this app (launcher counts as "not this app")
- No in-app way to cancel a running session
- If the app process is killed or the phone reboots mid-session, the session just ends (no resume)

## Enforcement architecture

- AccessibilityService detects foreground app/launcher and brings this app back to front if it's not this app
- Device Admin granted once during setup, stays active permanently (blocks normal uninstall at all times; only removable via adb)
- Watchdog re-locks the screen if the Accessibility Service is disabled during a running session
- No VPN/DNS component (browsers are already blocked outright, nothing to reach)

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- minSdk 26, targetSdk latest stable
- DataStore for settings (focus/break minutes)
- No backend, no cloud sync

## Icon

- Generated flat-vector tomato, Android adaptive icon
