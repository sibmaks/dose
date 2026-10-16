package io.github.sibmaks.app.dose.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseStore
import io.github.sibmaks.app.dose.data.Medication
import java.time.LocalDate
import java.time.Instant
import java.time.ZonedDateTime

/** Uses independent one-shot alarms and finds the next matching date after each delivery. */
object ReminderScheduler {
    private const val PREFS = "dose_scheduled_alarms"
    private const val SLOTS = "scheduled_slots"

    fun exactAlarmsAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    @Synchronized
    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(SLOTS, emptySet()).orEmpty().toSet()
        previous.forEach { entry ->
            val parts = entry.split('|', limit = 2)
            if (parts.size == 2) manager.cancel(primaryIntent(app, parts[0], parts[1]))
        }

        val logs = DoseStore.logs(app)
        val medications = DoseStore.medications(app).filter { it.active }
        val now = ZonedDateTime.now()
        // Editing a rule, pausing, deleting, or recording a dose invalidates its visible reminder.
        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.activeNotifications.forEach { posted ->
            val extras = posted.notification.extras
            val id = extras.getString(ReminderReceiver.EXTRA_MEDICATION_ID) ?: return@forEach
            val time = extras.getString(ReminderReceiver.EXTRA_TIME) ?: return@forEach
            val date = extras.getString(ReminderReceiver.EXTRA_DATE) ?: return@forEach
            val scheduledDate = runCatching { LocalDate.parse(date) }.getOrNull()
            val valid = scheduledDate != null && medications.any { it.id == id && time in it.timesOn(scheduledDate) &&
                extras.getLong(ReminderReceiver.EXTRA_SCHEDULED_AT, 0L) == DoseLogic.effectiveScheduledMillis(it, scheduledDate, time, DoseStore.timeOverrides(app)) } &&
                logs.none { it.date == date && it.medicationId == id && it.time == time }
            if (!valid) notifications.cancel(posted.id)
        }
        val newSlots = mutableSetOf<String>()
        medications.forEach { med ->
            med.times.forEach timeLoop@ { time ->
                val at = DoseLogic.nextOccurrence(med, time, now, logs, DoseStore.timeOverrides(app)) ?: return@timeLoop
                val dueDate = Instant.ofEpochMilli(at).atZone(now.zone).toLocalDate().toString()
                val intent = primaryIntent(app, med.id, time, dueDate)
                schedule(manager, at, intent, app)
                newSlots += "${med.id}|$time"
            }
        }
        prefs.edit().putStringSet(SLOTS, newSlots).commit()
        NotificationPause.restore(app)
        rescheduleSnoozes(app, manager)
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(app)
    }


    /** Only updates the affected slot so simultaneous alarms are not accidentally canceled. */
    @Synchronized
    fun refreshSlot(context: Context, medicationId: String, time: String) {
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context)
        val app = context.applicationContext
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(primaryIntent(app, medicationId, time))
        val medication = DoseStore.medications(app).find { it.id == medicationId && it.active }
        if (medication == null || time !in medication.times) return
        val now = ZonedDateTime.now()
        val at = DoseLogic.nextOccurrence(medication, time, now, DoseStore.logs(app), DoseStore.timeOverrides(app)) ?: return
        val dueDate = Instant.ofEpochMilli(at).atZone(now.zone).toLocalDate().toString()
        schedule(manager, at, primaryIntent(app, medicationId, time, dueDate), app)
    }

    /** Sends newly saved one-time requests even though their current minute has already begun. */
    fun deliverImmediate(context: Context, medication: Medication, previous: Medication?) {
        DoseLogic.newImmediateSchedules(medication, previous).forEach { rule ->
            val slot = ReminderSlot(medication.id, rule.dates.single(), rule.times.single(), requireNotNull(rule.immediateAt))
            context.sendBroadcast(slot.putInto(Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_REMIND
            }))
        }
    }

    /** Store the snooze so it survives device restart. */
    fun snooze(context: Context, medicationId: String, time: String, date: String, minutes: Int = 10) {
        DoseStore.markDeferred(context, DoseLogic.slotKey(date, medicationId, time))
        val until = System.currentTimeMillis() + minutes * 60_000L
        val key = DoseLogic.slotKey(date, medicationId, time)
        DoseStore.putSnooze(context, key, until)
        scheduleSnooze(context, medicationId, time, date, until)
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context)
    }

    fun cancelSnooze(context: Context, medicationId: String, time: String, date: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(snoozeIntent(context, medicationId, time, date))
        DoseStore.clearSnooze(context, DoseLogic.slotKey(date, medicationId, time))
    }

    private fun rescheduleSnoozes(context: Context, manager: AlarmManager) {
        val now = System.currentTimeMillis()
        val currentDate = LocalDate.now().toString()
        val meds = DoseStore.medications(context).filter { it.active }.associateBy { it.id }
        val logs = DoseStore.logs(context)
        val snoozes = DoseStore.snoozes(context)
        snoozes.forEach { (key, until) ->
            val parts = key.split('|', limit = 3)
            if (parts.size != 3) return@forEach
            val (date, id, time) = parts
            val valid = date == currentDate && meds[id]?.timesOn(LocalDate.parse(date))?.contains(time) == true &&
                logs.none { it.date == date && it.medicationId == id && it.time == time }
            if (valid && until > now) {
                schedule(manager, until, snoozeIntent(context, id, time, date), context)
            } else {
                manager.cancel(snoozeIntent(context, id, time, date))
                DoseStore.clearSnooze(context, key)
            }
        }
    }

    private fun scheduleSnooze(context: Context, id: String, time: String, date: String, at: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedule(manager, at, snoozeIntent(context, id, time, date), context)
    }

    private fun schedule(manager: AlarmManager, at: Long, pending: PendingIntent, context: Context) {
        if (exactAlarmsAvailable(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } else {
            // Android may delay this significantly: the UI warns the user about that.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun primaryIntent(context: Context, id: String, time: String, date: String? = null): PendingIntent =
        pending(context, ReminderReceiver.ACTION_REMIND, "dose://daily/$id/$time", id, time, date)

    private fun snoozeIntent(context: Context, id: String, time: String, date: String): PendingIntent =
        pending(context, ReminderReceiver.ACTION_SNOOZE_FIRED, "dose://snooze/$date/$id/$time", id, time, date)

    fun actionIntent(context: Context, action: String, id: String, time: String, date: String, scheduledAt: Long? = null): PendingIntent =
        pending(context, action, "dose://action/$action/$date/$id/$time", id, time, date, scheduledAt)

    private fun pending(
        context: Context, action: String, url: String, id: String, time: String, date: String?, scheduledAt: Long? = null,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse(url)
            putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, id)
            putExtra(ReminderReceiver.EXTRA_TIME, time)
            if (date != null) {
                putExtra(ReminderReceiver.EXTRA_DATE, date)
                val original = scheduledAt ?: DoseStore.medications(context).find { it.id == id }
                    ?.let { DoseLogic.effectiveScheduledMillis(it, LocalDate.parse(date), time, DoseStore.timeOverrides(context)) } ?: DoseLogic.scheduledMillis(LocalDate.parse(date), time)
                putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, original)
            }
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
