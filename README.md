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
- Reminders restored on reboot, clock/time-zone changes, package upgrades, and exact-alarm permission grants.

## Schedule behavior

- Start and end dates are optional and inclusive for recurring rules. An empty start is anchored to the day the rule was created, rather than advancing each day. Clearing an explicit start in the editor anchors it to today. An empty end continues until paused or deleted.
- Specific-date rules can contain several dates and stop after the last one. Each rule's times apply to all of its selected dates.
- Month days are actual calendar dates: a rule for the 31st skips months without a 31st; it does not move the dose to the last day of the month.
- Overlapping rules for the same medication, date, and time produce one dose, log, and notification. Duplicate times within one rule are rejected.
- Alarms find the next unrecorded matching date for each medication/time. Recording Taken or Skip completes only that slot and advances its alarm; snoozes are stored separately.
- Times follow the device's local time zone. Daylight-saving gaps and overlaps use Java's `ZonedDateTime` resolution (gaps move forward, overlaps use the earlier offset).
- Existing daily medications migrate automatically to a daily rule. Recognizable dosage text such as `500 mg` splits into amount and unit; other text is preserved. IDs, colors, instructions, pause state, and dose history remain intact.

## Permissions and reliability

- Android 13+: use the bell or Enable banner to grant notification permission.
- Android 12+: grant Alarms & reminders access through the Allow exact alarms banner for precise timing. Without it, Android may delay alarms.
- Vendor battery policies, force-stop, power off, and Do Not Disturb can interfere with alerts. A force-stopped app receives alarms again after launch.
- Snoozes apply to the current day's dose. This app does not provide clinical advice or track inventory.

## Source and validation

- `ui/DoseApp.kt`: dashboard, medication list, and history.
- `ui/MedicationEditor.kt`: form, dose, schedule, and color controls.
- `data/DoseModels.kt`: calendar rules, occurrence calculation, units, and color ordering.
- `data/MedicationJson.kt` and `data/DoseStore.kt`: persistence and legacy migration.
- `reminders/ReminderScheduler.kt` and `reminders/ReminderReceiver.kt`: alarms, notification actions, and delivery.
- `app/src/test`: calendar boundaries, finite rules, monthly/leap dates, overlap deduplication, recorded slots, snoozes, time-zone resolution, colors, and JSON migration/round-trip tests.

Verified on 2026-10-14: debug APK assembly succeeded, all 25 unit tests passed, and Android lint finished with no errors (warnings remain). No connected Android device or emulator image was available, so editor layout and actual notification delivery/actions still need device verification.

The browser prototype in `design-reference/pill-reminder.html` is kept for reference and is not bundled in the APK.
