package io.github.sibmaks.app.dose.reminders

import android.content.Context
import android.widget.RemoteViews
import android.view.View
import io.github.sibmaks.app.dose.data.DoseItem
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.localization.localizedDose

/** Expanded rows retain individual controls; heads-up prioritizes the group actions. */
internal object DeferredNotificationViews {
    fun expanded(context: Context, med: Medication, date: String, time: String, scheduledAt: Long, group: List<DoseItem>, onTime: Boolean) =
        create(context, R.layout.notification_deferred, med, date, time, scheduledAt, true, group, onTime)
    fun headsUp(context: Context, med: Medication, date: String, time: String, scheduledAt: Long, group: List<DoseItem>, onTime: Boolean) =
        create(context, R.layout.notification_deferred_heads_up, med, date, time, scheduledAt, false, group, onTime)
    private fun create(context: Context, layout: Int, med: Medication, date: String, time: String, scheduledAt: Long, expanded: Boolean, group: List<DoseItem>, onTime: Boolean) = RemoteViews(context.packageName, layout).apply {
        val multiple = group.size > 1
        setTextViewText(R.id.medication_name, if (multiple && !expanded) context.getString(R.string.several_medicines, group.size) else med.name)
        setViewVisibility(R.id.group_actions, if (multiple) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.acceptance_actions, if (!expanded && multiple) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.take_on_time, if (onTime) View.VISIBLE else View.GONE)
        if (multiple) {
            setTextViewText(R.id.take_all, context.getString(R.string.taken_all))
            setTextViewText(R.id.skip_all, context.getString(R.string.skip_all))
            setOnClickPendingIntent(R.id.take_all, GroupDoseActions.pending(context, GroupDoseActions.TAKEN, group))
            setOnClickPendingIntent(R.id.skip_all, GroupDoseActions.pending(context, GroupDoseActions.SKIP, group))
        }
        if (expanded) setTextViewText(R.id.medication_detail, (if (multiple) context.getString(R.string.several_medicines, group.size) + " · " else "") + med.localizedDose(context) + " · " + context.getString(R.string.scheduled_time, java.time.Instant.ofEpochMilli(scheduledAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + if (med.note.isBlank()) "" else " · ${med.note}")
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
