package io.github.sibmaks.app.dose.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.sibmaks.app.dose.data.DoseGroups
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseStore
import org.json.JSONObject
import java.time.LocalDate

/** What one repeat tick decides to do with its cycle. */
enum class CycleAction {
    /** Buzz now and re-arm the next tick. */
    VIBRATE,

    /** Keep the cycle alive without buzzing, e.g. while notifications are paused. */
    QUIET,

    /** The cycle is done; drop its alarm and stored state. */
    STOP,
}

/**
 * Pure repeat decision, ordered from hard stops to soft holds: a moved, resolved, snoozed,
 * stale or disabled dose stops buzzing; a paused one or a same-instant sibling of the buzzing
 * anchor waits silently; the rest keep buzzing.
 */
fun decideCycle(
    isToday: Boolean,
    paused: Boolean,
    alertsActive: Boolean,
    resolved: Boolean,
    snoozed: Boolean,
    rescheduled: Boolean,
    shared: Boolean = false
): CycleAction = when {
    rescheduled || !isToday || !alertsActive || resolved || snoozed -> CycleAction.STOP
    paused || shared -> CycleAction.QUIET
    else -> CycleAction.VIBRATE
}

/**
 * Repeats the reminder vibration every five minutes until the dose is accepted, skipped or
 * snoozed; the ten-minute snooze starts a fresh cycle when it fires. Each delivered dose keeps
 * its own cycle, which survives reboots and app restarts until its slot is resolved.
 */
object VibrationRepeat {
    const val KEY = "vibrationKey"
    private const val INTERVAL = 5 * 60_000L
    private const val PREFS = "dose_vibration_repeat"
    private const val SLOTS = "repeat_slots"

    private data class Cycle(
        val scheduledAt: Long,
        val nextAt: Long
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cycles(context: Context): Map<String, Cycle> = runCatching {
        val json = JSONObject(prefs(context).getString(SLOTS, "{}") ?: "{}")
        json.keys().asSequence().mapNotNull { key ->
            val entry = json.optJSONObject(key) ?: return@mapNotNull null
            key to Cycle(entry.optLong("at"), entry.optLong("next"))
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun save(context: Context, cycles: Map<String, Cycle>) {
        val json = JSONObject()
        cycles.forEach { (key, cycle) ->
            json.put(key, JSONObject().put("at", cycle.scheduledAt).put("next", cycle.nextAt))
        }
        prefs(context).edit().putString(SLOTS, json.toString()).commit()
    }

    /** A fresh delivery starts its five-minute repeat cycle. */
    fun start(context: Context, slot: ReminderSlot) {
        if (!alertsActive(context)) return
        val next = System.currentTimeMillis() + INTERVAL
        val cycles = cycles(context).toMutableMap()
        cycles[slot.key] = Cycle(slot.scheduledAt, next)
        save(context, cycles)
        schedule(context, slot.key, next)
    }

    /** The dose was resolved or postponed; the snooze alarm starts the next cycle. */
    fun stop(context: Context, key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, key))
        val cycles = cycles(context)
        if (key in cycles) save(context, cycles - key)
    }

    /** Rebuilds cycle alarms after a reboot or any full reschedule. */
    fun restore(context: Context) {
        cycles(context).forEach { (key, cycle) -> schedule(context, key, cycle.nextAt) }
    }

    /** One repeat tick: validate the slot, buzz if it still awaits an answer, re-arm or drop. */
    fun fire(context: Context, key: String) {
        val cycle = cycles(context)[key] ?: return
        val now = System.currentTimeMillis()
        val parts = key.split('|', limit = 3)
        if (parts.size != 3) return stop(context, key)
        val (date, id, time) = parts
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val medication = parsedDate?.let {
            DoseStore.medications(context).find { med -> med.id == id && med.active && time in med.timesOn(it) }
        }
        val resolved = DoseStore.logs(context).any {
            it.date == date && it.medicationId == id && it.time == time && it.status != "pending"
        }
        val snoozed = (DoseStore.snoozes(context)[key] ?: 0L) > now
        val rescheduled = medication == null || cycle.scheduledAt != DoseLogic.effectiveScheduledMillis(
            medication, parsedDate, time, DoseStore.timeOverrides(context)
        )
        // Same-instant siblings share one buzz: only the notification anchor's cycle sounds.
        val items = DoseLogic.todayItems(
            DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context), now,
            overrides = DoseStore.timeOverrides(context)
        )
        val group = DoseGroups.together(
            items, items.find { it.medication.id == id && it.time == time && it.date == date }
        )
        val shared = group.size > 1 && group.first().medication.id != id
        when (
            decideCycle(
                isToday = date == LocalDate.now().toString(),
                paused = NotificationPause.isPaused(context),
                alertsActive = alertsActive(context),
                resolved = resolved,
                snoozed = snoozed,
                rescheduled = rescheduled,
                shared = shared
            )
        ) {
            CycleAction.STOP -> stop(context, key)
            CycleAction.QUIET -> rearm(context, key, cycle.scheduledAt, now)
            CycleAction.VIBRATE -> {
                ReminderHaptics.vibrate(context)
                rearm(context, key, cycle.scheduledAt, now)
            }
        }
    }

    private fun rearm(
        context: Context,
        key: String,
        scheduledAt: Long,
        now: Long
    ) {
        val next = now + INTERVAL
        val cycles = cycles(context).toMutableMap()
        cycles[key] = Cycle(scheduledAt, next)
        save(context, cycles)
        schedule(context, key, next)
    }

    private fun alertsActive(context: Context): Boolean =
        ReminderAlerts.enabled(context) || ReminderAlerts.feedEnabled(context)

    private fun pendingIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, VibrationReceiver::class.java)
            .setData("dose://vibrate/${android.net.Uri.encode(key)}".toUri())
            .putExtra(KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(context: Context, key: String, at: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, key)
        if (ReminderScheduler.exactAlarmsAvailable(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } else {
            // Android may delay this significantly, exactly like the reminder alarms themselves.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
