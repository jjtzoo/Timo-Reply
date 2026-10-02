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
4. For unread inbox replies, tap **Enable unread access**, then enable **Timo Reply — unread chats** in Android Accessibility settings. Enabling this service alone does not send anything.
5. Save your reply and tap **Reply unread chats, then auto-reply**. The app opens Timo, selects **Unread**, and replies only to direct chats showing **Reply to messages to earn diamonds**. VIP and other tags are kept separate from the recipient name. Floating promotions are ignored. Keep Timo visible and the phone unlocked. Use **Stop unread replies** in Timo Reply to cancel. Leaving Timo pauses actions; after 20 seconds without the expected screen the pass stops.
6. Once the eligible unread pass finishes, the app turns on notification auto-replies and sends Timo to the background. A usable notification Reply action is still required; support and recipient delivery remain unverified until a fresh message is observed.
7. To test the notification path separately, keep auto-send OFF, put Timo in the background and receive a new test message from a cooperating contact.
8. Check **Latest diagnostic** for **Reply action: YES** or **NO**. ADB diagnostics are also available:

```powershell
.\tools\Check-Phone.ps1 -Watch
# If adb is not in PATH:
.\tools\Check-Phone.ps1 -AdbPath 'C:\path\to\platform-tools\adb.exe' -Watch
```

9. Save your message and turn on auto-send when notification access is connected. Wait 5 seconds, then receive a NEW message. It sends only when that notification supplies a usable reply action. Verify delivery in Timo itself. Dispatch success alone does not prove delivery.

## Behavior

- Filters only `com.hwsj.club`, the package in Timo Club's Play listing; confirm on the device.
- Unread mode uses an Android Accessibility service to read the visible Timo inbox and perform native click/set-text/scroll actions. It runs on the phone, with no PC or ADB needed after installation. Notifications use reply actions, including wearable actions.
- Unread mode is a user-started pass through accessible unread direct chats with the exact diamond-reply prompt. It skips groups/assistant/mentor inboxes and rows without that prompt, stops on existing drafts or changed recipients, and skips replies already visible in the chat. The configured Unicode/emoji reply is entered using Android's set-text action. Timo's observed `😊` to `[blush]` conversion is recognized without accepting other text changes.
- Unread chat names are hashed and reserved before opening for 24 hours to prevent retries after interruption. Two people with the same display name may be conservatively treated as one chat. Opening a chat marks it read in Timo, even if a later send fails; check the last chat after an interrupted pass. A new explicit start can finish the bot's matching, pre-send draft in the currently open chat. Once Send may have been tapped, it is never retried automatically. No automatic unread restart after accessibility reconnect.
- An outgoing bubble increments the unread counter; it does not prove recipient delivery. The unread workflow depends on the UI observed in Timo 2.9.9 and stops if required controls change. It recognizes anonymous nested inbox RecyclerViews and avoids the surrounding category ViewPager when scrolling. Scrolling past the first page is implemented but remains untested on the phone.
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

Sixteen unit tests cover notification freshness, preview, deduplication, cooldowns, group/outgoing filtering, unread eligibility, the diamond-prompt requirement, reserved chats, changed recipients/drafts, disabled Send controls and Timo's observed emoji conversion.
See `tools/Test-Policy.ps1` to run them with a Java JDK without an Android SDK.
The Gradle debug build, all ten unit tests, and Android lint passed (lint still reports non-blocking warnings). The APK passed signature verification (v2/v3).
On 2 October 2026, the app itself displayed four outgoing replies to matching unread chats on the Infinix X6873, ignored non-matching rows, completed the pass, enabled notification mode and returned to the launcher. The operator only installed/started the app and inspected diagnostics; the Accessibility service opened chats, entered text and tapped Send. It also finished its own pre-send draft from an earlier stopped test. Recipient delivery/read status, notification reply support, scrolling beyond the first page, all-day operation and reboot recovery remain unverified. A separate earlier manual ADB reply is not included in the four bot replies.

## Platform references

- [Timo Club Play listing](https://play.google.com/store/apps/details?id=com.hwsj.club)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [RemoteInput](https://developer.android.com/reference/android/app/RemoteInput)
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [AccessibilityNodeInfo actions](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)
- [ADB](https://developer.android.com/tools/adb)
- [Android Gradle Plugin 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)

This is a personal prototype and is not affiliated with Timo Club.
