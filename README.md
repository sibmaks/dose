# Dose — Android medication reminder

Native Android adaptation of the Dose HTML/Vue prototype. Kotlin, Jetpack Compose and Material 3, AlarmManager, notifications, and on-device SharedPreferences/JSON. No account or server is needed at runtime.

## Build and run

Install JDK 17+, Android SDK API 35, and a compatible Android Studio. Open this folder and let Gradle sync, then run the app on Android 8.0+ (API 26+).

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. The bundled Gradle wrapper downloads its pinned distribution on first use. The project uses Gradle 9.8.1, AGP 9.1.1 with built-in Kotlin, and the Kotlin Compose plugin 2.2.10.

## Features

- Today dashboard, upcoming/overdue doses, completion indicator, and seven-day local history.
- Add, edit, pause, and delete medications without removing previous dose history.
- Medication forms: pill, capsule, liquid, spray, drops, injection, and cream. SVG source illustrations live in `design-reference/medication-forms`; Android vector drawables use the same paths in the editor, dashboard, medication list, and notification small icon.
- Separate dose amount and unit, with mg, ml, g, mcg, IU, tablet, capsule, drop, spray, and puff presets plus an editable custom unit.
- Multiple schedules per medication. Each rule has one or several times and can use specific dates, selected weekdays, selected month days, or every day.
- Six preset colors (including rose and amber) plus an RGB/hex color picker. Up to eight swatches appear, with frequently saved colors first and the current selection included. Usage persists across app restarts; pausing does not increase usage.
- Taken, Skip, and Snooze 10 min notification actions; Taken, Skip, Undo, and due-dose Snooze actions in the dashboard.
- Optional pop-up reminder window over other apps, with Taken, Snooze, Skip, and Later actions. Enable it from the settings gear and grant Android's Display over other apps access. The pop-up uses purple pill-shaped actions, side-by-side Snooze and Skip controls, medication cards, and scheduled-time badges. The test pop-up shows the same controls using up to two existing medications (or an example); its buttons close the preview without recording a dose.
- History includes a Time to take report: average delay overall and per medication, with 7-day, 30-day, and all-time views. Each Taken entry also shows its click time and delay.
- Reminders restored on reboot, clock/time-zone changes, package upgrades, and exact-alarm permission grants.

## Schedule behavior

- Start and end dates are optional and inclusive for recurring rules. An empty start is anchored to the day the rule was created, rather than advancing each day. Clearing an explicit start in the editor anchors it to today. An empty end continues until paused or deleted.
- Specific-date rules can contain several dates and stop after the last one. Each rule's times apply to all of its selected dates.
- Month days are actual calendar dates: a rule for the 31st skips months without a 31st; it does not move the dose to the last day of the month.
- Overlapping rules for the same medication, date, and time produce one dose, log, and notification. Duplicate times within one rule are rejected.
- Alarms find the next unrecorded matching date for each medication/time. Recording Taken or Skip completes only that slot and advances its alarm; snoozes are stored separately.
- Times follow the device's local time zone. Daylight-saving gaps and overlaps use Java's `ZonedDateTime` resolution (gaps move forward, overlaps use the earlier offset).
- Existing daily medications migrate automatically to a daily rule. Recognizable dosage text such as `500 mg` splits into amount and unit; other text is preserved. IDs, colors, instructions, pause state, and dose history remain intact.

## Delay report

Delay is the time from the original scheduled deadline to the Taken click. Early clicks contribute zero delay; skipped and unrecorded doses are excluded. The average is weighted by taken doses, not by medication. Reporting periods use the scheduled date, including doses taken after midnight. Snoozing does not move the report baseline.

New records persist their scheduled instant so later time-zone changes do not reinterpret that timestamp. Older records remain readable; their deadline is estimated from the stored date and time in the current time zone and the report labels these estimates. Renamed or deleted medications retain their historical report entries.

## Permissions and reliability

- Android 13+: use the bell or Enable banner to grant notification permission.
- Android 12+: grant Alarms & reminders access through the Allow exact alarms banner for precise timing. Without it, Android may delay alarms.
- Pop-ups are off by default. Enable **Settings gear → Pop-up over other apps**, grant **Display over other apps**, allow exact alarms, then use **Test pop-up**. Exact alarms allow the foreground pop-up service to start while Dose is closed. If Android blocks the service, the ordinary dose notification remains available.
- The pop-up runs for at most two minutes, then closes without recording or skipping doses. Several simultaneous reminders share one scrollable window. Later keeps the dose notifications; Taken, Skip, or Snooze use the same actions as notifications. Permission revocation, pause/delete/schedule changes, and dose actions remove invalid pop-ups.
- Pop-ups do not unlock or wake the phone. Android or protected apps can suppress overlays; lock-screen reminders continue to use notifications. The Reminder notification settings button opens Android's channel controls for sound and Pop on screen where supported.
- Vendor battery policies, force-stop, power off, and Do Not Disturb can interfere with alerts. A force-stopped app receives alarms again after launch.
- Snoozes apply to the current day's dose. This app does not provide clinical advice or track inventory.

## Source and validation

- `ui/DoseApp.kt`: dashboard, medication list, and history.
- `ui/MedicationEditor.kt`: form, dose, schedule, and color controls.
- `data/DoseModels.kt`: calendar rules, occurrence calculation, units, and color ordering.
- `data/MedicationJson.kt`, `data/DoseLogJson.kt`, and `data/DoseStore.kt`: persistence and legacy migration.
- `data/DoseReport.kt` and `ui/DelayReport.kt`: timing calculations and report UI.
- `reminders/ReminderOverlayService.kt` and `ui/ReminderSettings.kt`: optional bounded overlay and permission controls.
- `reminders/ReminderScheduler.kt` and `reminders/ReminderReceiver.kt`: alarms, notification actions, and delivery.
- `app/src/test`: calendar boundaries, finite rules, monthly/leap dates, overlap deduplication, recorded slots, snoozes, time-zone resolution, colors, and JSON migration/round-trip tests.

Verified on 2026-10-15: debug APK assembly succeeded, all 40 unit tests passed, and Android lint finished with no errors (warnings remain). No connected Android device or emulator image was available, so layout and real overlay/notification delivery and actions still need device verification.

Device verification: enable overlay access and exact alarms, test the pop-up, then schedule two medications for the same minute and switch to another app. Check both appear and Taken/Skip/Snooze affect only the intended dose. Check Later/timeout preserve notifications, notification actions remove the corresponding pop-up, and disabling overlay access or pausing/deleting a medication clears its pop-up. Confirm History reports the expected delay (including early clicks, snoozes, and a dose taken after midnight).

The browser prototype in `design-reference/pill-reminder.html` is kept for reference and is not bundled in the APK.
