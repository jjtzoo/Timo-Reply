# Timo Reply — Scrum development plan

Prepared: 2 October 2026 (Asia/Manila)

## Product goal

Build a personal Android app that sends a short, configurable generic reply to supported incoming Timo Club messages. The user can enable or stop replies, leave Timo in the background, and use the phone normally. Validate operation with the screen locked and during a full-day trial before promising all-day reliability.

**First priority: inspect the phone and prove that a Timo notification can receive a reply.** Later development depends on that result.

## Current position

- Actual Git repository: `D:\Timo Club\Timo-Reply`.
- Remote: `https://github.com/jjtzoo/Timo-Reply.git`.
- The starter source is imported at the repository root, with the Scrum plan in `docs/SCRUM_PLAN.md`. The policy tests were rerun from this repository and all eight passed.
- A starter source ZIP and debug APK already exist. The source uses native Android Java, Android SDK APIs, Gradle, `NotificationListenerService`, and `RemoteInput`.
- The starter supports one configurable reply, preview mode, duplicate suppression, conversation/global cooldowns, and diagnostic status.
- Eight policy tests passed. Java/resources compiled and the APK passed signature verification. The Gradle build and Android lint have not been run.
- Actual Timo compatibility, phone installation, locked-screen operation, and delivery are unverified.
- The starter turns auto-send OFF when the notification listener reconnects. It is not yet an all-day release.
- The initial ADB check on 2 October found no connected devices.

## MVP scope

Include:

- One short generic response, editable by the user; suggested text: “Thanks! I'll reply later.”
- Explicit auto-send toggle, with preview as the first-install default.
- Replies to supported one-to-one notifications from the verified Timo package only.
- Duplicate suppression, cooldowns, and protection against replying to old/self/group notifications when identity is known.
- Conservative handling of ambiguous message or conversation metadata.
- Clear access, connection, pause, dispatch, and failure status.
- Persistent settings and intentional reconnect/reboot behavior.
- A tested APK, source, and short installation/troubleshooting instructions.

Keep outside the first release:

- AI chat, keyword engines, unsolicited outbound messages, account farming, voice/video automation, and multiple-account management.
- Cloud backend, subscriptions, and Play Store launch.
- Rooting or continuous screen-tapping automation. If notification replies fail, create a separate feasibility decision before adopting another approach.

## Scrum working agreement

This is a lightweight Scrum workflow for a small project; do not imply that a full Scrum team already exists.

| Responsibility | Owner |
|---|---|
| Product Owner | You: choose reply behavior, priorities, and accept demos |
| Development | Coding assistant, with your repository and phone access |
| Scrum facilitation | You and the assistant: keep the backlog ordered and surface blockers |
| Device acceptance | You: provide the phone and confirm replies in controlled test chats |

- Sprint length: one week. Four proposed sprints; begin Sprint 1 when the phone is available.
- Planning: 20–30 minutes at the start of each sprint. Select work based on actual availability.
- Daily check: a short update on completed work, next work, and blockers on active development days.
- Review: demonstrate the working increment and compare it with its acceptance criteria.
- Retrospective: identify one improvement to the next sprint's workflow.
- Backlog refinement: clarify upcoming stories before the next planning session.
- Treat story points as relative complexity, not days. Initial estimates below are provisional; adjust after Sprint 1.
- Board columns: **Backlog → Ready → In progress → Review/Test → Done**, with **Blocked** for external dependencies.
- Work-in-progress limit: one main implementation story at a time. Mark a story Done only after its required checks pass.

## Ordered product backlog

P0 = required to prove feasibility or prevent incorrect replies. P1 = required for the intended MVP. P2 = release polish.

| ID | Priority | Story | Points | Acceptance criteria |
|---|---|---|---:|---|
| TR-01 | P0 | As the owner, I can open and build the starter from the repository. | 3 | Import source into the repository root; keep build output/keys out of Git; record build prerequisites; Gradle debug build, policy tests, and lint pass or any genuine blocker is explicitly recorded. |
| TR-02 | P0 | As the owner, I know whether Timo supports notification replies on my phone. | 5 | ADB identifies phone/Android/Timo version and package; install the starter; grant notification access; observe a fresh notification in preview; identify text input and reply action; dispatch one controlled reply and confirm it in the chat. |
| TR-03 | P0 | As the owner, I know which phone/app states produce usable notifications. | 3 | Record results for Timo background, Timo foreground/chat open, screen locked, and notification settings; distinguish missing notification, inaccessible content, absent action, failed dispatch, and unconfirmed delivery; make a written continue/re-scope decision. |
| TR-04 | P1 | As the user, I can set a short generic response. | 2 | Edit, validate, save, and restore the response; reject blank text; define a sensible maximum length; use the saved text exactly. |
| TR-05 | P0 | As the user, I can preview, start, and stop auto-send. | 3 | First install sends nothing; missing access/support prevents arming; status is visible; disabling immediately prevents subsequent dispatches; enabling never replies to the old notification backlog. |
| TR-06 | P0 | As the user, I receive predictable reply limits without loops. | 5 | Deduplicate reposts and repeated callback events; persist reservations; default to one reply per conversation per 10 minutes plus a 30-second global gap; skip known self/group/summary/ongoing messages; skip ambiguous identity rather than guessing. |
| TR-07 | P1 | As the user, I can understand whether the bot is working. | 3 | Show access, listener connection, enabled/paused state, last observation, last dispatch attempt, and errors; dispatch is not labeled delivered without confirmation; omit message bodies from diagnostic logs. |
| TR-08 | P1 | As the user, I can leave the app enabled through a normal listener reconnect. | 5 | Preserve explicit opt-in; inspect a reconnect baseline without sending; accept only genuinely new eligible messages after recovery; persisted deduplication/cooldowns remain active; turning the toggle off stays off. |
| TR-09 | P1 | As the user, I know what happens after reboot or access revocation. | 5 | Define and display the resume policy; test reboot with enabled and disabled states; never auto-enable after a first install or permission revocation; stale notifications remain skipped; if automatic recovery fails, show the required recovery step. |
| TR-10 | P1 | As the user, I can operate with the screen locked and the PC disconnected. | 5 | Test background delivery, locked screen, an idle period, network loss/restoration, and USB disconnected; document any phone-specific settings actually needed; avoid requiring the Timo chat to stay foregrounded. |
| TR-11 | P1 | As the user, failed dispatches do not cause reply bursts or repeated attempts. | 3 | Handle canceled/replaced actions, notification removal, and changing permissions; consume failed-attempt reservations as documented; no automatic retry storm; distinguish temporary failure from unsupported operation. |
| TR-12 | P1 | As the owner, I can judge full-day reliability from evidence. | 5 | Run a 24-hour controlled trial on the target phone; log eligible notifications, attempts, failures, confirmed replies, duplicate/unwanted replies, recovery events, and battery impact; compare outcomes with release criteria below. |
| TR-13 | P2 | As the user, I can install and update the APK consistently. | 3 | Produce a versioned, verified APK; keep a consistent signing identity stored outside Git; demonstrate an update preserving settings; provide rollback/reinstall notes and concise setup instructions. |
| TR-14 | P2 | As the owner, I have a reproducible, reviewable codebase. | 3 | CI runs debug build, unit tests, and lint; repository contains no keystores, tokens, or raw chat dumps; document version/build numbers and tested device coverage; record outstanding limitations. |

## Proposed sprint roadmap

| Sprint | Goal | Candidate stories | Review increment |
|---|---|---|---|
| 1 — Feasibility | Prove the notification approach works on the actual phone. | TR-01, TR-02, TR-03 — 11 points | Repository baseline plus one confirmed Timo reply and a phone-state compatibility record. |
| 2 — Controlled MVP | Make generic replies predictable and user-controlled. | TR-04, TR-05, TR-06, TR-07 — 13 points | Configurable short reply, usable start/stop controls, duplicate/cooldown protection, and clear diagnostics. |
| 3 — Background reliability | Recover safely and operate while the phone is locked. | TR-08, TR-09, TR-10, TR-11 — 18 points | Demonstrated reconnect/reboot behavior and locked-screen operation with USB disconnected. Split this sprint if capacity is insufficient. |
| 4 — Pilot and release | Validate a full day and package a maintainable release. | TR-12, TR-13, TR-14 — 11 points | Pilot evidence, versioned signed APK, setup guide, and passing CI. |

These are planning candidates, not fixed commitments. Do not start all-day features before Sprint 1 establishes a usable reply path. Reorder or split stories using the measured capacity and phone-test results.

## Sprint 1 task checklist

- [x] Import the existing source ZIP into `D:\Timo Club\Timo-Reply` and verify the file layout.
- [ ] Run the Gradle build, policy tests, and lint with the pinned tool versions.
- [ ] Connect the phone by USB, unlock it, enable USB debugging, and approve the computer.
- [ ] Confirm the device appears as authorized in `adb devices`.
- [ ] Record phone model, Android version, Timo package/version, and relevant notification settings.
- [ ] Install the starter APK and grant notification access. Keep auto-send OFF.
- [ ] Receive a fresh test message with Timo in the background; record usable reply metadata without logging chat content.
- [ ] If supported, enable auto-send, wait through the warmup, and receive a new controlled message.
- [ ] Confirm the reply in the Timo conversation; do not infer delivery from PendingIntent dispatch alone.
- [ ] Test screen locked, Timo foreground/chat open, and notification settings on/off.
- [ ] Record the result and make the architecture decision below.

### Feasibility decision

**Continue:** a fresh incoming notification exposes a usable reply action and a controlled reply is confirmed in Timo.

**Investigate configuration:** no notification appears, or content is unavailable. Check Timo notification settings, Android permissions, app state, and the device's restrictions before concluding the method is unsupported.

**Re-scope:** notifications arrive but do not expose a working reply action. Stop notification-based sending work and document the evidence. Research an official integration or a separate UI-automation prototype. Assess screen/unlock requirements and reliability before changing the all-day goal; no official Timo API has been verified.

## Definition of Ready

A story has a user outcome, clear acceptance criteria, an estimate, known dependencies, and a practical verification method. Device-dependent stories identify which phone state and cooperating test contact are needed.

## Definition of Done

- Implementation is reviewable in the repository and matches the acceptance criteria.
- Relevant automated tests pass; debug build and lint pass for code changes.
- Device-dependent behavior is demonstrated on the target phone and recorded.
- No accidental reply to old messages, duplicate observations, or unrelated apps is found in the relevant checks.
- Settings and diagnostics explain failures without exposing raw messages.
- Documentation and backlog status reflect the actual outcome.
- Known limits are documented; blocked or untested work is not labeled Done.

## Full-day acceptance and test coverage

For the pilot, use controlled messages and respect the configured cooldowns. Collect at least 30 eligible notification events across the required states; extend the trial if fewer occur.

- Zero duplicate or unwanted replies in the controlled sample.
- At least 95% of eligible, accessible notifications dispatch within 10 seconds of observation. Record chat-confirmed delivery separately.
- Every dispatch has a diagnostic outcome; every suspected missed incoming message is checked for whether a usable notification existed.
- Toggle OFF prevents subsequent replies. Access loss and unsupported states are visible.
- Reconnect/reboot follows the documented resume policy and sends no stale backlog.
- Operation is demonstrated with the phone locked and USB disconnected.
- Measure battery usage over the trial and agree on acceptability using the phone's baseline; do not invent a universal battery percentage.

| Case | Expected result |
|---|---|
| First install / preview | Observe only; send nothing. |
| Fresh supported one-to-one message | Send saved response when enabled and eligible. |
| Duplicate/reposted notification | No extra reply. |
| Within conversation/global cooldown | No reply; explain the cooldown. |
| Old notification after enabling/reconnecting | No reply. |
| Known group/self/summary/ongoing notification | No reply. |
| Ambiguous sender/conversation identity | Skip and expose a clear reason. |
| Missing reply action or unreadable text | No reply; identify the unsupported observation. |
| Replaced/canceled reply action | Record failure; do not burst or blindly retry. |
| Permission revoked / toggle disabled | Stop dispatching. |
| Timo foreground/chat open | Record observed notification behavior; do not promise detection without an accessible notification. |
| Lock screen, idle, reboot, network return | Validate recovery and freshness on the actual phone. |

## Immediate next action

Connect the phone. Perform the read-only ADB/device check, then the controlled notification/reply test. Use those results to finalize Sprint 1 and decide whether the proposed four-sprint roadmap is feasible.

Platform references: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [RemoteInput](https://developer.android.com/reference/android/app/RemoteInput), [ADB](https://developer.android.com/tools/adb). These describe Android mechanisms; they do not prove Timo compatibility on the target phone.
