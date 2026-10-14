package com.example.dose.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dose.MainActivity
import com.example.dose.R
import com.example.dose.data.DoseLog
import com.example.dose.data.DoseStore
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val time = intent.getStringExtra(EXTRA_TIME) ?: return
        val med = DoseStore.medications(context).find { it.id == id && it.active && time in it.times }
        val date = intent.getStringExtra(EXTRA_DATE) ?: LocalDate.now().toString()
        val notificationId = notificationId(id, time, date)
        when (intent.action) {
            ACTION_TAKEN -> {
                if (med != null && DoseStore.logs(context).none {
                        it.date == date && it.medicationId == id && it.time == time
                    }) {
                    DoseStore.record(context, DoseLog(
                        date, id, med.name, med.dose, time, "taken", System.currentTimeMillis(),
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
                notify(context, med.name, med.dose, med.note, id, time, date, notificationId)
            }
        }
    }

    private fun notify(
        context: Context, name: String, dose: String, note: String,
        id: String, time: String, date: String, notificationId: Int,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Medication reminders", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Reminders for medication schedules you added" })

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (note.isBlank()) dose else "$dose · $note"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time for $name")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_notification, "Taken", ReminderScheduler.actionIntent(
                context, ACTION_TAKEN, id, time, date,
            ))
            .addAction(R.drawable.ic_notification, "Snooze 10 min", ReminderScheduler.actionIntent(
                context, ACTION_SNOOZE, id, time, date,
            ))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val ACTION_REMIND = "com.example.dose.REMIND"
        const val ACTION_SNOOZE_FIRED = "com.example.dose.SNOOZE_FIRED"
        const val ACTION_TAKEN = "com.example.dose.TAKEN"
        const val ACTION_SNOOZE = "com.example.dose.SNOOZE"
        const val EXTRA_MEDICATION_ID = "medicationId"
        const val EXTRA_TIME = "time"
        const val EXTRA_DATE = "date"
        private const val CHANNEL_ID = "dose_reminders_v1"

        fun notificationId(id: String, time: String, date: String): Int =
            "$id|$time|$date".hashCode() and 0x7fffffff
    }
}
