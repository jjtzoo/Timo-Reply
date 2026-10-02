# Timo Reply starter

Android app for one configurable, casual and lightly flirty reply to Timo Club messages.
Starts in preview; auto-send is OFF. Default message:

> Hey you 😊 nice to hear from you.

## Build

Open this folder in Android Studio. Install Android SDK Platform 36 and Build Tools 36.0.0.
Use JDK 17 or 21. Gradle wrapper 9.3.1 and Android Gradle Plugin 9.1.1 are pinned.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
The delivery includes `timo-reply-debug.apk`, built with official Android SDK Platform 36 and Build Tools 36.0.0. It is a debug prototype for testing on your phone.
`tools/Build-Apk.ps1` reproduces this build directly with the SDK tools, taking the app version from `app/build.gradle`.

```powershell
.\tools\Build-Apk.ps1 -PlatformDirectory 'C:\Android\Sdk\platforms\android-36' -BuildToolsDirectory 'C:\Android\Sdk\build-tools\36.0.0' -JavaBinDirectory 'C:\path\to\jdk\bin'
```

## When the phone is connected

1. Turn on Developer options and USB debugging, connect by USB, unlock the phone, and approve its debugging prompt.
2. Run `adb devices`. Then install the built app:

```powershell
adb install -r .\timo-reply-debug.apk
```

3. Open **Timo Reply**, tap **Enable notification access**, and enable it in Android settings. For a sideloaded app, Android may require **Allow restricted settings** in the app's system information menu first.
4. Keep auto-send OFF. Put Timo in the background and receive a new test message from a cooperating contact.
5. Check **Latest diagnostic** for **Reply action: YES** or **NO**. ADB diagnostics are also available:

```powershell
.\tools\Check-Phone.ps1 -Watch
# If adb is not in PATH:
.\tools\Check-Phone.ps1 -AdbPath 'C:\path\to\platform-tools\adb.exe' -Watch
```

6. If a usable reply action exists, save your message and turn on auto-send. Wait 5 seconds, then receive a NEW message. Verify delivery in Timo itself. Dispatch success alone does not prove delivery.

## Behavior

- Filters only `com.hwsj.club`, the package in Timo Club's Play listing; confirm on the device.
- Uses notification reply actions, including wearable actions. No root or accessibility tapping.
- Skips existing notifications at listener connection, old messages, duplicates, summary/ongoing notifications, identifiable groups, and identifiable outgoing messages.
- At most one reply per conversation per 10 minutes, with a 30-second global gap.
- Starts in preview on first install. After explicit opt-in, a normal listener reconnect resumes auto-send after a fresh baseline and warmup; existing unread notifications are never sent replies during recovery.
- A disconnected listener pauses dispatch. Removing notification access disables auto-send when the app observes that permission loss.
- Support detection stays available after observing a usable reply action, even if a later summary notification has no action. Ambiguous unlabeled text-input actions are skipped.
- Messages without a usable timestamp are skipped because their age cannot be verified.
- Cooldown and deduplication reservations persist before sending. Failed dispatches consume the reservation and are not retried automatically.
- Gradle uses the existing ignored `work/manual-build/debug.keystore` when present, matching the direct SDK build. Retain that local key to update an installed prototype without changing its signing identity.
- Message text is processed locally, with no internet permission. Only hashes/timestamps and diagnostic status are retained; message bodies are not logged. No AI replies.
- When notification metadata does not identify a group or your own message, those classifications cannot be guaranteed. Do the controlled phone test before relying on auto-send.
- If Timo does not expose a usable notification reply action, this approach cannot send. Device inspection will determine the next approach. No official Timo bot API has been verified.
- Android notification delivery and battery restrictions affect reliability. Foreground Timo chats may not produce notifications.

## Verification

Ten unit tests cover preview, fresh messages, reposted old messages, duplicates, missing reply actions, group/outgoing messages, cooldown boundaries, summaries, warmup, future timestamps, callbacks after disconnect, and unknown message age.
See `tools/Test-Policy.ps1` to run them with a Java JDK without an Android SDK.
The Gradle debug build, all ten unit tests, and Android lint passed (lint still reports non-blocking warnings). The APK passed signature verification (v2/v3).
Actual Timo reply delivery and all-day/reboot recovery remain unverified. A controlled phone test is still required; reconnect recovery is implemented but not yet demonstrated on Timo.

## Platform references

- [Timo Club Play listing](https://play.google.com/store/apps/details?id=com.hwsj.club)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [RemoteInput](https://developer.android.com/reference/android/app/RemoteInput)
- [ADB](https://developer.android.com/tools/adb)
- [Android Gradle Plugin 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)

This is a personal prototype and is not affiliated with Timo Club.
