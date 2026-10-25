package io.github.sibmaks.app.dose.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.sibmaks.app.dose.data.PauseWindow
import io.github.sibmaks.app.dose.data.PauseWindowJson
import io.github.sibmaks.app.dose.data.PauseWindows
import org.json.JSONArray

/** Multiple independent pause windows; delivery stays silenced while at least one runs. */
object NotificationPause {
    private const val WINDOWS = "pause_windows"
    private const val LEGACY_UNTIL = "pause_until"
    private const val LEGACY_MIGRATED = "pause_windows_migrated"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dose_alert_settings", Context.MODE_PRIVATE)

    /** Legacy single deadline is carried over as one window, then dropped. */
    private fun migrate(context: Context) {
        if (prefs(context).getBoolean(LEGACY_MIGRATED, false)) return
        val legacy = prefs(context).getLong(LEGACY_UNTIL, 0L)
        val stored = runCatching {
            val array = JSONArray(prefs(context).getString(WINDOWS, "[]"))
            (0 until array.length()).mapNotNull { i ->
                PauseWindowJson.decode(array.optJSONObject(i) ?: return@mapNotNull null)
            }
        }.getOrDefault(emptyList())
        val migrated = if (legacy > System.currentTimeMillis()) {
            stored + PauseWindow(System.currentTimeMillis(), legacy - 3_600_000L, legacy)
        } else stored
        val array = JSONArray()
        migrated.forEach { array.put(PauseWindowJson.encode(it)) }
        prefs(context).edit()
            .putString(WINDOWS, array.toString())
            .putBoolean(LEGACY_MIGRATED, true)
            .remove(LEGACY_UNTIL)
            .commit()
    }

    @Synchronized
    fun windows(context: Context): List<PauseWindow> {
        migrate(context)
        val stored = runCatching {
            val array = JSONArray(prefs(context).getString(WINDOWS, "[]"))
            (0 until array.length()).mapNotNull { i ->
                PauseWindowJson.decode(array.optJSONObject(i) ?: return@mapNotNull null)
            }
        }.getOrDefault(emptyList())
        // Finished and canceled windows are not kept; drop them on read.
        val live = PauseWindows.live(stored, System.currentTimeMillis())
        if (live.size != stored.size) save(context, live)
        return live
    }

    private fun save(
        context: Context,
        windows: List<PauseWindow>
    ) {
        val array = JSONArray()
        windows.forEach { array.put(PauseWindowJson.encode(it)) }
        prefs(context).edit()
            .putString(WINDOWS, array.toString())
            .commit()
    }

    /** A new window starts recording right away or waits scheduled; schedules themselves stay untouched. */
    @Synchronized
    fun add(
        context: Context,
        startAt: Long,
        endAt: Long
    ) {
        // The window must be upcoming or already running; a slightly stale "now" start is tolerated.
        require(endAt > startAt && endAt > System.currentTimeMillis())
        save(context, windows(context) + PauseWindow(System.currentTimeMillis(), startAt, endAt))
        if (startAt <= System.currentTimeMillis()) silenceNow(context)
        ReminderScheduler.scheduleAll(context)
    }

    /** Resuming early or removing a scheduled window simply deletes it — there is no pause history. */
    @Synchronized
    fun cancel(
        context: Context,
        id: Long
    ) {
        save(context, windows(context).filterNot { it.id == id })
        ReminderScheduler.scheduleAll(context)
    }

    fun isPaused(context: Context): Boolean =
        PauseWindows.isPaused(windows(context), System.currentTimeMillis())

    /** Stops what is already on screen the moment a pause takes effect. */
    fun silenceNow(context: Context) {
        context.stopService(Intent(context, ReminderOverlayService::class.java))
        context.getSystemService(NotificationManager::class.java)
            .let { manager ->
                manager.activeNotifications.filter { it.notification.extras.containsKey(ReminderReceiver.EXTRA_MEDICATION_ID) }
                    .forEach { manager.cancel(it.id) }
            }
    }

    /** Called after reboot, clock changes, and each schedule rebuild. No missed-dose burst on resume. */
    fun restore(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, NotificationPauseReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val transition = PauseWindows.nextTransition(windows(context), System.currentTimeMillis())
        if (transition == null) {
            manager.cancel(pending)
        } else if (ReminderScheduler.exactAlarmsAvailable(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, transition, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, transition, pending)
            }
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, transition, pending)
    }
}

class NotificationPauseReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        // A window that just started silences what is already on screen; a finished one lets scheduleAll resume.
        if (NotificationPause.isPaused(context)) NotificationPause.silenceNow(context)
        ReminderScheduler.scheduleAll(context)
    }
}
