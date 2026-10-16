package io.github.sibmaks.app.dose.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import androidx.core.os.BundleCompat
import io.github.sibmaks.app.dose.reminders.ReminderReceiver
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
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) = updateAll(context)
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
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("dose://widget/today")
                putExtra(OPEN_TODAY, true)
            }
            val openToday = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val icons = items.map { it.medication }.distinctBy { it.id }.associate { it.id to WidgetMedicationIcons.render(context, listOf(it)) }
            val compact = RemoteViews(context.packageName, R.layout.widget_upcoming_dose).apply {
                setTextViewText(R.id.widget_title, context.getString(if (next == null) R.string.today else if (next.dueMillis <= now.toInstant().toEpochMilli()) R.string.due_now else R.string.upcoming_dose))
                setTextViewText(R.id.widget_time, next?.let { Instant.ofEpochMilli(it.dueMillis).atZone(now.zone).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "—")
                setTextViewText(R.id.widget_name, if (group.size > 1) context.getString(R.string.several_medicines, group.size)
                    else next?.medication?.name ?: context.getString(R.string.no_doses_due_today))
                setTextViewText(R.id.widget_dose, if (group.size > 1) group.joinToString(", ") { it.medication.name }
                    else next?.medication?.localizedDose(context) ?: context.getString(R.string.widget_open_today))
                setImageViewBitmap(R.id.widget_icon, WidgetMedicationIcons.render(context, group.map { it.medication }))
                setContentDescription(R.id.widget_icon, if (group.size > 1) context.getString(R.string.several_medicines, group.size)
                    else next?.medication?.localizedForm(context))
                setOnClickPendingIntent(R.id.widget_root, openToday)
            }
            fun render(height: Float): RemoteViews {
                if (height < DoseWidgetState.EXPANDED_HEIGHT) return compact
                val count = DoseWidgetState.visibleRows(height, items.size)
                return RemoteViews(context.packageName, R.layout.widget_dose_list).apply {
                    setTextViewText(R.id.widget_title, context.getString(R.string.today_s_schedule))
                    setTextViewText(R.id.widget_time, next?.let { Instant.ofEpochMilli(it.dueMillis).atZone(now.zone).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "—")
                    setTextViewText(R.id.widget_dose, if (count < items.size) context.getString(R.string.widget_more_doses, items.size - count)
                        else context.getString(if (items.isEmpty()) R.string.no_doses_due_today else R.string.widget_open_today))
                    setOnClickPendingIntent(R.id.widget_header, openToday)
                    setOnClickPendingIntent(R.id.widget_dose, openToday)
                    removeAllViews(R.id.widget_rows)
                    items.take(count).forEach { item ->
                        val row = RemoteViews(context.packageName, R.layout.widget_dose_row).apply {
                            setTextViewText(R.id.medication_name, item.medication.name)
                            setTextViewText(R.id.medication_detail, item.medication.localizedDose(context))
                            setTextViewText(R.id.widget_row_time, item.scheduledTime)
                            val status = when (item.status) {
                                "taken" -> R.string.taken
                                "skipped" -> R.string.skipped
                                "overdue" -> R.string.overdue
                                "due" -> R.string.due_now
                                "snoozed" -> R.string.snoozed
                                else -> R.string.upcoming
                            }
                            setTextViewText(R.id.widget_row_status, context.getString(status))
                            setTextColor(R.id.widget_row_status, when (item.status) {
                                "taken" -> 0xFF379D80.toInt()
                                "due", "overdue" -> 0xFFB65E24.toInt()
                                else -> 0xFF6F748B.toInt()
                            })
                            setImageViewBitmap(R.id.widget_icon, icons.getValue(item.medication.id))
                            setContentDescription(R.id.widget_icon, item.medication.localizedForm(context))
                            setOnClickPendingIntent(R.id.widget_row, openToday)
                            setViewVisibility(R.id.widget_row_actions, if (item.log == null) View.VISIBLE else View.GONE)
                            setTextViewText(R.id.take_now, context.getString(R.string.taken))
                            setTextViewText(R.id.skip, context.getString(R.string.skip))
                            setOnClickPendingIntent(R.id.take_now, ReminderScheduler.actionIntent(context, ReminderReceiver.ACTION_TAKEN, item.medication.id, item.time, item.date, item.scheduledAt))
                            setOnClickPendingIntent(R.id.skip, ReminderScheduler.actionIntent(context, ReminderReceiver.ACTION_SKIP, item.medication.id, item.time, item.date, item.scheduledAt))
                        }
                        addView(R.id.widget_rows, row)
                    }
                }
            }
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    val supported = sizes?.filter { it.width > 0 && it.height > 0 }?.takeIf { it.isNotEmpty() }
                        ?: listOf(SizeF(180f, 110f), SizeF(180f, 220f), SizeF(180f, 332f), SizeF(180f, 444f), SizeF(180f, 556f), SizeF(180f, 668f))
                    RemoteViews(supported.associateWith { render(it.height) })
                } else {
                    val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val key = if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    render(options.getInt(key, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)).toFloat())
                }
                manager.updateAppWidget(id, views)
            }
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
