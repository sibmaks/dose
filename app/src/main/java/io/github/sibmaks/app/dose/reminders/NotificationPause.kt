package io.github.sibmaks.app.dose.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.sibmaks.app.dose.data.NotificationPauseWindow

object NotificationPause {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dose_alert_settings", Context.MODE_PRIVATE)

    private fun deadline(context: Context) = prefs(context).getLong("pause_until", 0L)

    fun isPaused(context: Context) = NotificationPauseWindow.active(deadline(context), System.currentTimeMillis())

    fun until(context: Context): Long =
        deadline(context).takeIf { NotificationPauseWindow.active(it, System.currentTimeMillis()) } ?: 0L

    fun set(
        context: Context,
        until: Long
    ) {
        require(until == 0L || until > System.currentTimeMillis())
        prefs(context).edit()
            .putLong("pause_until", until)
            .commit()
        if (until > 0) {
            context.stopService(Intent(context, ReminderOverlayService::class.java))
            context.getSystemService(NotificationManager::class.java)
                .let { manager ->
                    manager.activeNotifications.filter { it.notification.extras.containsKey(ReminderReceiver.EXTRA_MEDICATION_ID) }
                        .forEach { manager.cancel(it.id) }
                }
        }
        ReminderScheduler.scheduleAll(context)
    }

    /** Called after reboot, clock changes, and each schedule rebuild. No missed-dose burst on resume. */
    fun restore(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, NotificationPauseReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val until = until(context)
        if (until == 0L) {
            prefs(context).edit()
                .remove("pause_until")
                .commit()
            manager.cancel(pending)
        } else if (ReminderScheduler.exactAlarmsAvailable(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending)
            }
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending)
    }
}

class NotificationPauseReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        // A stale alarm cannot end a newly extended pause.
        ReminderScheduler.scheduleAll(context)
    }
}
