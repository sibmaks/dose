package io.github.sibmaks.app.dose.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import io.github.sibmaks.app.dose.MainActivity
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.*
import io.github.sibmaks.app.dose.localization.*
import io.github.sibmaks.app.dose.reminders.ReminderScheduler
import java.time.*
import java.time.format.DateTimeFormatter

class DoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)
    override fun onEnabled(context: Context) = updateAll(context)
    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH) updateAll(context)
    }
    companion object {
        const val OPEN_TODAY = "openToday"
        private const val REFRESH = "io.github.sibmaks.app.dose.WIDGET_REFRESH"
        private fun refreshIntent(context: Context) = PendingIntent.getBroadcast(context, 0,
            Intent(context, DoseWidget::class.java).setAction(REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun updateAll(source: Context) {
            val context = AppLanguages.wrap(source.applicationContext)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DoseWidget::class.java))
            if (ids.isEmpty()) return
            val now = ZonedDateTime.now()
            val items = DoseLogic.todayItems(DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context),
                now.toInstant().toEpochMilli(), now.toLocalDate(), DoseStore.timeOverrides(context))
            val group = DoseWidgetState.nextGroup(items)
            val next = group.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_upcoming_dose).apply {
                setTextViewText(R.id.widget_title, context.getString(if (next == null) R.string.today else if (next.dueMillis <= now.toInstant().toEpochMilli()) R.string.due_now else R.string.upcoming_dose))
                setTextViewText(R.id.widget_time, next?.let { Instant.ofEpochMilli(it.dueMillis).atZone(now.zone).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "—")
                setTextViewText(R.id.widget_name, if (group.size > 1) context.getString(R.string.several_medicines, group.size)
                    else next?.medication?.name ?: context.getString(R.string.no_doses_due_today))
                setTextViewText(R.id.widget_dose, if (group.size > 1) group.joinToString(", ") { it.medication.name }
                    else next?.medication?.localizedDose(context) ?: context.getString(R.string.widget_open_today))
                setImageViewBitmap(R.id.widget_icon, WidgetMedicationIcons.render(context, group.map { it.medication }))
                setContentDescription(R.id.widget_icon, if (group.size > 1) context.getString(R.string.several_medicines, group.size)
                    else next?.medication?.localizedForm(context))
                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    data = Uri.parse("dose://widget/today")
                    putExtra(OPEN_TODAY, true)
                }
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            ids.forEach { manager.updateAppWidget(it, views) }
            // Update at the next due time and at midnight, even if Dose is not open.
            val alarms = context.getSystemService(AlarmManager::class.java)
            val at = DoseWidgetState.nextRefresh(items, now)
            val refresh = refreshIntent(context)
            if (ReminderScheduler.exactAlarmsAvailable(context)) {
                try { alarms.setExact(AlarmManager.RTC, at, refresh) }
                catch (_: SecurityException) { alarms.set(AlarmManager.RTC, at, refresh) }
            } else alarms.set(AlarmManager.RTC, at, refresh)
        }
    }
}
