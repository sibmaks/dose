package io.github.sibmaks.app.dose.reminders

import android.content.Context
import android.widget.RemoteViews
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.localization.localizedDose

/** Four dose actions fit in expanded content, independently of Android's three-action limit. */
internal object DeferredNotificationViews {
    fun expanded(context: Context, med: Medication, date: String, time: String, scheduledAt: Long) =
        create(context, R.layout.notification_deferred, med, date, time, scheduledAt, true)
    fun headsUp(context: Context, med: Medication, date: String, time: String, scheduledAt: Long) =
        create(context, R.layout.notification_deferred_heads_up, med, date, time, scheduledAt, false)
    private fun create(context: Context, layout: Int, med: Medication, date: String, time: String, scheduledAt: Long, expanded: Boolean) = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.medication_name, med.name)
        if (expanded) setTextViewText(R.id.medication_detail, med.localizedDose(context) + " · " + context.getString(R.string.scheduled_time, java.time.Instant.ofEpochMilli(scheduledAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + if (med.note.isBlank()) "" else " · ${med.note}")
        val actions = listOf(
            Triple(R.id.take_now, R.string.taken, ReminderReceiver.ACTION_TAKEN),
            Triple(R.id.take_on_time, R.string.on_time, ReminderReceiver.ACTION_ON_TIME),
        ) + if (expanded) listOf(
            Triple(R.id.snooze, R.string.snooze_10_min, ReminderReceiver.ACTION_SNOOZE),
            Triple(R.id.skip, R.string.skip, ReminderReceiver.ACTION_SKIP),
        ) else emptyList()
        actions.forEach { (view, label, action) ->
            setTextViewText(view, context.getString(label))
            setOnClickPendingIntent(view, ReminderScheduler.actionIntent(context, action, med.id, time, date, scheduledAt))
        }
    }
}
