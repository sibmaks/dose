package io.github.sibmaks.app.dose.reminders

import io.github.sibmaks.app.dose.localization.*
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.sibmaks.app.dose.MainActivity
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseGroups
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseStore
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.ui.iconResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == GroupDoseActions.TAKEN || intent.action == GroupDoseActions.SKIP) {
                GroupDoseActions.receive(context, intent)
                return
            }
            val slot = ReminderSlot.from(intent) ?: return
            val id = slot.medicationId
            val time = slot.time
            val date = slot.date
            val scheduledDate = LocalDate.parse(date)
            val med = DoseStore.medications(context).find { it.id == id && it.active && time in it.timesOn(scheduledDate) }
            // Ignore actions from a notification whose one-day deadline has since changed.
            if (med != null && slot.scheduledAt != DoseLogic.effectiveScheduledMillis(med, scheduledDate, time, DoseStore.timeOverrides(context))) return
            val notificationId = notificationId(id, time, date)
            when (intent.action) {
                ACTION_TAKEN, ACTION_ON_TIME, ACTION_SKIP -> {
                    if (med != null && DoseStore.logs(context).none {
                            it.date == date && it.medicationId == id && it.time == time
                        }) {
                        val now = System.currentTimeMillis()
                        if (intent.action == ACTION_ON_TIME && !DoseLogic.canRecordOnTime(DoseStore.wasDeferred(context, slot.key), slot.scheduledAt, now)) return
                        DoseStore.record(context, DoseLogic.recordedLog(med, date, time,
                            if (intent.action == ACTION_SKIP) "skipped" else "taken", slot.scheduledAt, now,
                            onTime = intent.action == ACTION_ON_TIME))
                    }
                    ReminderScheduler.cancelSnooze(context, id, time, date)
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    ReminderScheduler.refreshSlot(context, id, time)
                }
                ACTION_SNOOZE -> {
                    if (med != null && DoseStore.logs(context).none {
                            it.date == date && it.medicationId == id && it.time == time
                        } && date == LocalDate.now().toString()) {
                        ReminderScheduler.snooze(context, id, time, date)
                    }
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                ACTION_REMIND, ACTION_SNOOZE_FIRED -> {
                    if (intent.action == ACTION_REMIND) ReminderScheduler.refreshSlot(context, id, time)
                    if (med == null || date != LocalDate.now().toString()) return
                    if (DoseStore.logs(context).any {
                            it.date == date && it.medicationId == id && it.time == time
                        }) return
                    // Retain the elapsed snooze instant for grouping and snapshot validation.
                    // Recording, rescheduling or a new snooze clears/replaces it.
                    if (NotificationPause.isPaused(context)) return
                    notify(context, med, id, time, date, notificationId, slot.scheduledAt)
                    ReminderAlerts.show(context, slot)
                }
            }
            if (intent.action != ACTION_REMIND && intent.action != ACTION_SNOOZE_FIRED) refreshVisible(context)
        } finally { io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context) }
    }

    private fun notify(
        sourceContext: Context, medication: Medication,
        id: String, time: String, date: String, notificationId: Int, scheduledAt: Long, silent: Boolean = false,
    ) {
        val context = AppLanguages.wrap(sourceContext)
        if (NotificationPause.isPaused(context) || !ReminderAlerts.feedEnabled(context) || !ReminderNotifications.allowed(context)) return
        ReminderNotifications.createChannel(context)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val items = DoseLogic.todayItems(DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context),
            overrides = DoseStore.timeOverrides(context))
        val group = DoseGroups.together(items, items.find { it.medication.id == id && it.time == time && it.date == date })
        val multiple = group.size > 1
        val message = if (multiple) group.joinToString("\n") {
            "${it.medication.name} · ${notificationDetail(context, it.medication, it.scheduledAt, it.amount)}"
        } else notificationDetail(context, medication, scheduledAt, medication.amountAt(LocalDate.parse(date), time))
        val taken = if (multiple) GroupDoseActions.pending(context, GroupDoseActions.TAKEN, group)
            else ReminderScheduler.actionIntent(context, ACTION_TAKEN, id, time, date, scheduledAt)
        val skip = if (multiple) GroupDoseActions.pending(context, GroupDoseActions.SKIP, group)
            else ReminderScheduler.actionIntent(context, ACTION_SKIP, id, time, date, scheduledAt)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(medication.form.iconResource())
            .setContentTitle(if (multiple) context.getString(R.string.several_medicines, group.size)
                else context.getString(R.string.time_for_named, medication.name))
            .setContentText(if (multiple) group.joinToString(", ") { it.medication.name } else message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .addAction(R.drawable.ic_notification, context.getString(R.string.taken), taken)
            .addAction(R.drawable.ic_notification, context.getString(R.string.skip), skip)
            .addExtras(Bundle().apply {
                putString(EXTRA_MEDICATION_ID, id)
                putString(EXTRA_TIME, time)
                putString(EXTRA_DATE, date)
                putLong(EXTRA_SCHEDULED_AT, scheduledAt)
            })
            .setAutoCancel(true)
            .setSilent(silent)
            .setOnlyAlertOnce(silent)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (failure: SecurityException) {
            // Permission can be revoked after the readiness check; pop-ups remain independent.
            Log.w("DoseNotifications", "Notification permission was revoked before delivery", failure)
        }
    }

    private fun notificationDetail(context: Context, medication: Medication, scheduledAt: Long, amount: String): String {
        val time = Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        return medication.localizedDose(context, amount) + " · " + context.getString(R.string.scheduled_time, time) +
            if (medication.note.isBlank()) "" else " · ${medication.note}"
    }

    companion object {
        const val ACTION_REMIND = "io.github.sibmaks.app.dose.REMIND"
        const val ACTION_SNOOZE_FIRED = "io.github.sibmaks.app.dose.SNOOZE_FIRED"
        const val ACTION_SKIP = "io.github.sibmaks.app.dose.SKIP"
        const val ACTION_ON_TIME = "io.github.sibmaks.app.dose.ON_TIME"
        const val ACTION_TAKEN = "io.github.sibmaks.app.dose.TAKEN"
        const val ACTION_SNOOZE = "io.github.sibmaks.app.dose.SNOOZE"
        const val EXTRA_MEDICATION_ID = "medicationId"
        const val EXTRA_TIME = "time"
        const val EXTRA_DATE = "date"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val CHANNEL_ID = "dose_reminders_v1"

        /** Refresh only already visible reminders, without resurrecting dismissed ones. */
        fun refreshVisible(context: Context) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.forEach { posted ->
                val extras = posted.notification.extras
                val id = extras.getString(EXTRA_MEDICATION_ID) ?: return@forEach
                val date = extras.getString(EXTRA_DATE) ?: return@forEach
                val time = extras.getString(EXTRA_TIME) ?: return@forEach
                val slot = ReminderSlot(id, date, time, extras.getLong(EXTRA_SCHEDULED_AT))
                val item = DoseLogic.todayItems(DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context),
                    overrides = DoseStore.timeOverrides(context)).find { it.id == slot.key }
                if (item == null || item.log != null || item.scheduledAt != slot.scheduledAt ||
                    (item.snoozedUntil ?: 0L) > System.currentTimeMillis()) manager.cancel(posted.id)
                else refreshNotification(context, slot)
            }
        }

        fun refreshNotification(context: Context, slot: ReminderSlot) {
            val med = DoseStore.medications(context).find { it.id == slot.medicationId && it.active && slot.time in it.timesOn(LocalDate.parse(slot.date)) } ?: return
            if (DoseStore.logs(context).any { it.medicationId == slot.medicationId && it.date == slot.date && it.time == slot.time }) return
            if (slot.scheduledAt != DoseLogic.effectiveScheduledMillis(med, LocalDate.parse(slot.date), slot.time, DoseStore.timeOverrides(context))) return
            ReminderReceiver().notify(context, med, slot.medicationId, slot.time, slot.date,
                notificationId(slot.medicationId, slot.time, slot.date), slot.scheduledAt, silent = true)
        }

        fun notificationId(id: String, time: String, date: String): Int =
            "$id|$time|$date".hashCode() and 0x7fffffff
    }
}
