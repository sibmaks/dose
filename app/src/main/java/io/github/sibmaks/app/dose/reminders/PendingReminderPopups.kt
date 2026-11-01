package io.github.sibmaks.app.dose.reminders

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.github.sibmaks.app.dose.data.DoseGroups
import io.github.sibmaks.app.dose.data.DoseItem
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseStore
import org.json.JSONArray
import org.json.JSONObject

/** Unseen popups survive screen lock and process death without keeping a short service alive. */
object PendingReminderPopups {
    private const val MEMBERS = "members"
    private const val RETRY_MILLIS = 15_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dose_pending_popups", Context.MODE_PRIVATE)

    fun screenAvailable(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun members(context: Context): List<DoseGroups.Member> = runCatching {
        val array = JSONArray(prefs(context).getString(MEMBERS, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            DoseGroups.Member(item.getString("key"), item.getLong("scheduledAt"), item.getLong("dueMillis"))
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, members: List<DoseGroups.Member>) {
        val array = JSONArray()
        members.forEach { member ->
            array.put(JSONObject().put("key", member.key).put("scheduledAt", member.scheduledAt)
                .put("dueMillis", member.dueMillis))
        }
        prefs(context).edit().putString(MEMBERS, array.toString()).commit()
    }

    private fun current(context: Context): List<DoseItem> = DoseLogic.todayItems(
        DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context),
        overrides = DoseStore.timeOverrides(context)
    )

    private fun eligible(context: Context): List<DoseItem> {
        if (!ReminderAlerts.enabled(context) || NotificationPause.isPaused(context)) return emptyList()
        val now = System.currentTimeMillis()
        return DoseGroups.eligible(current(context), members(context)).filter { it.dueMillis <= now }
    }

    fun defer(context: Context, slots: Collection<ReminderSlot>) {
        val expected = slots.associateBy { it.key }
        val added = DoseGroups.snapshot(current(context).filter { expected[it.id]?.scheduledAt == it.scheduledAt })
        save(context, (members(context) + added).associateBy { it.key }.values.toList())
        restore(context)
    }

    /** Revalidate after unlock, reboot, schedule edits, snoozes, resolution, or permission changes. */
    fun restore(context: Context, immediately: Boolean = false) {
        val pending = DoseGroups.snapshot(eligible(context))
        save(context, pending)
        schedule(context, pending.isNotEmpty(), if (immediately) 0L else RETRY_MILLIS)
    }

    fun deliver(context: Context) {
        val pending = eligible(context)
        save(context, DoseGroups.snapshot(pending))
        if (screenAvailable(context)) pending.forEach { item ->
            ReminderAlerts.show(context, ReminderSlot(item.medication.id, item.date, item.time, item.scheduledAt))
        }
        // A successful render acknowledges its members and cancels this retry.
        schedule(context, pending.isNotEmpty(), RETRY_MILLIS)
    }

    fun shown(context: Context, keys: Collection<String>) {
        val pending = members(context).filterNot { it.key in keys }
        save(context, pending)
        schedule(context, pending.isNotEmpty(), RETRY_MILLIS)
    }

    fun clear(context: Context) {
        save(context, emptyList())
        schedule(context, false)
    }

    private fun schedule(context: Context, needed: Boolean, delay: Long = RETRY_MILLIS) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, PendingPopupReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!needed || !ReminderScheduler.exactAlarmsAvailable(context)) {
            manager.cancel(intent)
            return
        }
        try {
            // RTC does not wake the phone. The exact alarm can restart the short popup service
            // after unlock even when Android restricts foreground starts from a background app.
            manager.setExact(AlarmManager.RTC, System.currentTimeMillis() + delay, intent)
        } catch (_: SecurityException) {
            // The permission may be revoked after the check; retain the queue until access returns.
        }
    }
}

class PendingPopupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PendingReminderPopups.deliver(context)
    }
}
