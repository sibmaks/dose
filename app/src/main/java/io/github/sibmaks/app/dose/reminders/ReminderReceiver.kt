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
import io.github.sibmaks.app.dose.data.DoseLog
import io.github.sibmaks.app.dose.data.DoseStore
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.ui.iconResource
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = ReminderSlot.from(intent) ?: return
        val id = slot.medicationId
        val time = slot.time
        val date = slot.date
        val scheduledDate = LocalDate.parse(date)
        val med = DoseStore.medications(context).find { it.id == id && it.active && time in it.timesOn(scheduledDate) }
        val notificationId = notificationId(id, time, date)
        when (intent.action) {
            ACTION_TAKEN, ACTION_SKIP -> {
                if (med != null && DoseStore.logs(context).none {
                        it.date == date && it.medicationId == id && it.time == time
                    }) {
                    DoseStore.record(context, DoseLog(
                        date, id, med.name, med.dose, time, if (intent.action == ACTION_SKIP) "skipped" else "taken", System.currentTimeMillis(), scheduledAt = slot.scheduledAt,
                    ))
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
                if (intent.action == ACTION_SNOOZE_FIRED) {
                    DoseStore.clearSnooze(context, "${date}|${id}|${time}")
                }
                notify(context, med, id, time, date, notificationId, slot.scheduledAt)
                ReminderAlerts.show(context, slot)
            }
        }
    }

    private fun notify(
        sourceContext: Context, medication: Medication,
        id: String, time: String, date: String, notificationId: Int, scheduledAt: Long,
    ) {
        val context = AppLanguages.wrap(sourceContext)
        if (!ReminderAlerts.feedEnabled(context) || !ReminderNotifications.allowed(context)) return
        ReminderNotifications.createChannel(context)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = medication.localizedDose(context) + if (medication.note.isBlank()) "" else " · ${medication.note}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(medication.form.iconResource())
            .setContentTitle(context.getString(R.string.time_for_named, medication.name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .addExtras(Bundle().apply {
                putString(EXTRA_MEDICATION_ID, id)
                putString(EXTRA_TIME, time)
                putString(EXTRA_DATE, date)
            })
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_notification, context.getString(R.string.taken), ReminderScheduler.actionIntent(
                context, ACTION_TAKEN, id, time, date, scheduledAt,
            ))
            .addAction(R.drawable.ic_notification, context.getString(R.string.snooze_10_min), ReminderScheduler.actionIntent(
                context, ACTION_SNOOZE, id, time, date, scheduledAt,
            ))
            .addAction(R.drawable.ic_notification, context.getString(R.string.skip), ReminderScheduler.actionIntent(
                context, ACTION_SKIP, id, time, date, scheduledAt,
            ))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (failure: SecurityException) {
            // Permission can be revoked after the readiness check; pop-ups remain independent.
            Log.w("DoseNotifications", "Notification permission was revoked before delivery", failure)
        }
    }

    companion object {
        const val ACTION_REMIND = "io.github.sibmaks.app.dose.REMIND"
        const val ACTION_SNOOZE_FIRED = "io.github.sibmaks.app.dose.SNOOZE_FIRED"
        const val ACTION_SKIP = "io.github.sibmaks.app.dose.SKIP"
        const val ACTION_TAKEN = "io.github.sibmaks.app.dose.TAKEN"
        const val ACTION_SNOOZE = "io.github.sibmaks.app.dose.SNOOZE"
        const val EXTRA_MEDICATION_ID = "medicationId"
        const val EXTRA_TIME = "time"
        const val EXTRA_DATE = "date"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val CHANNEL_ID = "dose_reminders_v1"

        fun notificationId(id: String, time: String, date: String): Int =
            "$id|$time|$date".hashCode() and 0x7fffffff
    }
}
