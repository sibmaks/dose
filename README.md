# Dose — Android pill reminder

Native Android adaptation of the single-file Dose HTML/Vue prototype.

**Technology:** Kotlin, Jetpack Compose + Material 3, AlarmManager, NotificationManager, SharedPreferences/JSON, Gradle Kotlin DSL. No WebView, CDN, account, or server required at runtime.

## Open in Android Studio

1. Install a recent Android Studio, Android SDK API 35, and JDK 17 or newer.
2. Extract the ZIP.
3. Because the official Gradle wrapper JAR could not be bundled in this environment, initialize it **once** from a terminal in the extracted project folder:
   - macOS / Linux: `./gradlew --version`
   - Windows: `gradlew.bat --version`
   The script downloads the **official** Gradle 8.10.2 wrapper JAR from `services.gradle.org` and verifies its published SHA-256 checksum, then downloads the Gradle 8.10.2 distribution (also hash-verified). A network connection is required on first run.
4. In Android Studio, select **Open**, then choose the `DoseAndroid` folder. Let Gradle sync.
5. Run the **app** configuration on an emulator or Android device running Android 8.0+ (API 26+).

Or use `./gradlew assembleDebug`. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

## Features

- Today dashboard, next upcoming/overdue dose, and completion indicator.
- Add, edit, pause, and delete medications; multiple reminder times per day.
- Mark a dose taken, skip it, or undo; seven-day local history.
- Snooze due reminders for 10 minutes.
- Daily Android notifications **even when the app UI is closed**, with notification actions for Taken and Snooze.
- Alarms restored on device reboot, clock/time-zone updates, package upgrade, and exact-alarm permission grants.
- No preconfigured medication alarms: users enter their own schedules.

## Android permissions and reliability

- Android 13+: tap the bell icon or **Enable** banner to grant notification permission (`POST_NOTIFICATIONS`).
- Android 12+: for best timing, use the **Allow exact alarms** banner to grant the special "Alarms & reminders" access (`SCHEDULE_EXACT_ALARM`). Without it, the app falls back to `setAndAllowWhileIdle`, which may run **later than the configured time**, especially in battery-saving modes.
- If notifications are disabled (globally or for the app), Android may still process alarms without displaying a notification.
- Device vendor battery policies, force-stop, power off, and Do Not Disturb can interfere with alerts. A force-stopped app cannot receive scheduled alarms until launched again.
- This prototype supports *daily recurring doses* based on the device's local time; it does not model start/end dates, specific weekdays, multiple daily quantities, pill inventory, or missed-dose clinical advice.

## Source structure

- `app/src/main/java/com/example/dose/ui/DoseApp.kt` — Compose screens and editor.
- `.../data/DoseModels.kt` — models and local-time calculation.
- `.../data/DoseStore.kt` — local JSON persistence.
- `.../reminders/ReminderScheduler.kt` — daily and snoozed Android alarms.
- `.../reminders/ReminderReceiver.kt` — notification delivery and actions.
- `.../reminders/RestoreReceiver.kt` — alarm restoration.
- `.../MainActivity.kt` — Android entry point.
- `app/src/test/.../DoseLogicTest.kt` — next-dose/snooze logic tests.

## Development notes

The original HTML `localStorage` data isn't imported automatically; Android maintains its own local database-like JSON preferences. The app currently targets Android API 35 and uses the pinned Gradle 8.10.2 / AGP 8.8.2 / Kotlin 2.0.21 versions.

**Build verification:** Source files and the project layout were reviewed, but an APK could not be compiled here because this environment has no Android SDK and cannot download Gradle/Android dependencies. Sync and test on your workstation before relying on notifications. This is a prototype, not a medically validated reminder system.

The original browser prototype is kept in `design-reference/pill-reminder.html` for visual comparison and is **not** bundled into the Android app.
