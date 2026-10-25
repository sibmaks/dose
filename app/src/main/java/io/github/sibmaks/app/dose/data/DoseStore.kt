package io.github.sibmaks.app.dose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.*

/** Small on-device persistence layer. Never seeds real reminders with demo medications. */
object DoseStore {
    private const val PREFS = "dose_preferences_v1"
    private const val MEDICATIONS = "medications"
    private const val LOGS = "logs"
    private const val EVENTS = "events"
    private const val SNOOZES = "snoozes"
    private const val OVERRIDES = "daily_time_overrides"
    private const val DEFERRED = "deferred_slots"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun medications(context: Context): List<Medication> = runCatching {
        val array = JSONArray(prefs(context).getString(MEDICATIONS, "[]"))
        val medications = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            MedicationJson.decode(obj)
        }
        if ((0 until array.length()).any {
                array.optJSONObject(it)
                    ?.has("schedules") == false
            }) {
            // Keep unreadable entries intact instead of deleting them during a format upgrade.
            (0 until array.length()).forEach { index ->
                val obj = array.optJSONObject(index)
                if (obj != null && !obj.has("schedules")) {
                    MedicationJson.decode(obj)
                        ?.let { array.put(index, MedicationJson.encode(it)) }
                }
            }
            prefs(context).edit()
                .putString(MEDICATIONS, array.toString())
                .commit()
        }
        medications
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(
        context: Context,
        medication: Medication
    ) {
        require(medication.name.isNotBlank() && medication.amount.isNotBlank())
        require(medication.schedules.isNotEmpty() && medication.schedules.all { it.validationError() == null })
        require(
            DoseScheduleConflicts.unresolved(medication.schedules, medication.amount)
                .isEmpty()
        )
        val list = medications(context).filterNot { it.id == medication.id } + medication
        val array = JSONArray()
        list.forEach { array.put(MedicationJson.encode(it)) }
        prefs(context).edit()
            .putString(MEDICATIONS, array.toString())
            .commit()
    }

    @Synchronized
    fun delete(
        context: Context,
        medicationId: String
    ) {
        val remaining = medications(context).filterNot { it.id == medicationId }
        val array = JSONArray()
        remaining.forEach { array.put(MedicationJson.encode(it)) }
        prefs(context).edit()
            .putString(MEDICATIONS, array.toString())
            .commit()
        saveOverrides(context, timeOverrides(context).filterKeys { !it.contains("|$medicationId|") })
        val remainingSnoozes = snoozes(context).filterKeys { !it.contains("|$medicationId|") }
        saveSnoozes(context, remainingSnoozes)
        prefs(context).edit()
            .putStringSet(
                DEFERRED,
                deferred(context).filterNot { it.contains("|$medicationId|") }
                    .toSet()
            )
            .commit()
        // An awaiting entry is unresolved reminder state, not history; it never outlives its medication.
        val existingLogs = logs(context)
        val keptLogs = existingLogs.filterNot { it.medicationId == medicationId && it.status == "pending" }
        if (keptLogs.size != existingLogs.size) {
            val array = JSONArray()
            keptLogs.forEach { array.put(DoseLogJson.encode(it)) }
            prefs(context).edit()
                .putString(LOGS, array.toString())
                .commit()
        }
        // Reminder events survive only where their dose entry survives.
        val surviving =
            keptLogs.map { DoseLogic.slotKey(it.date, it.medicationId, it.time) }
                .toSet()
        val allEvents = events(context)
        val keptEvents = allEvents.filter { DoseLogic.slotKey(it.date, it.medicationId, it.time) in surviving }
        if (keptEvents.size != allEvents.size) saveEvents(context, keptEvents)
    }

    @Synchronized
    fun logs(context: Context): List<DoseLog> = runCatching {
        val array = JSONArray(prefs(context).getString(LOGS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            DoseLogJson.decode(obj)
        }
    }.getOrDefault(emptyList())

    /** Reminder deliveries and postponements per slot; intake outcomes live on the logs themselves. */
    @Synchronized
    fun events(context: Context): List<DoseEvent> = runCatching {
        val array = JSONArray(prefs(context).getString(EVENTS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            DoseEventJson.decode(obj)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun addEvents(
        context: Context,
        additions: List<DoseEvent>
    ) {
        val fresh = additions.filter { it.at > 0 }
        if (fresh.isEmpty()) return
        val existing = events(context)
        val known = existing.mapTo(mutableSetOf(), ::eventKey)
        val merged = existing + fresh.filter { eventKey(it) !in known }
        if (merged.size == existing.size) return
        saveEvents(context, merged)
    }

    private fun eventKey(event: DoseEvent) =
        "${event.date}|${event.medicationId}|${event.time}|${event.type}|${event.at}"

    private fun saveEvents(
        context: Context,
        list: List<DoseEvent>
    ) {
        val array = JSONArray()
        list.forEach { array.put(DoseEventJson.encode(it)) }
        prefs(context).edit()
            .putString(EVENTS, array.toString())
            .commit()
    }

    @Synchronized
    private fun removeSlotEvents(
        context: Context,
        key: String
    ) {
        val all = events(context)
        val kept = all.filterNot { DoseLogic.slotKey(it.date, it.medicationId, it.time) == key }
        if (kept.size == all.size) return
        saveEvents(context, kept)
    }

    @Synchronized
    fun record(
        context: Context,
        log: DoseLog
    ) {
        require(log.status == "taken" || log.status == "skipped")
        val array = JSONArray()
        (logs(context).filterNot {
            it.date == log.date && it.medicationId == log.medicationId && it.time == log.time
        } + log).forEach { item ->
            array.put(DoseLogJson.encode(item))
        }
        prefs(context).edit()
            .putString(LOGS, array.toString())
            .commit()
        clearSnooze(context, DoseLogic.slotKey(log.date, log.medicationId, log.time))
        clearDeferred(context, DoseLogic.slotKey(log.date, log.medicationId, log.time))
    }

    /** One commit starts a delivery's awaiting entries and writes its reminder events; silent refreshes pass null. */
    @Synchronized
    fun recordDelivery(
        context: Context,
        items: List<DoseItem>,
        repeat: Boolean?
    ) {
        val now = System.currentTimeMillis()
        val existing = logs(context)
        val additions = items.filter { item ->
            existing.none { it.date == item.date && it.medicationId == item.medication.id && it.time == item.time }
        }
            .map { DoseLogic.pendingLog(it.medication, it.date, it.time, it.scheduledAt) }
        val allEvents = events(context)
        val events = if (repeat == null) emptyList() else items.map {
            DoseEvent(it.date, it.medication.id, it.time, if (repeat) "repeat" else "reminder", now)
        }
            .filter { eventKey(it) !in allEvents.mapTo(mutableSetOf(), ::eventKey) }
        if (additions.isEmpty() && events.isEmpty()) return
        val logsArray = JSONArray()
        (existing + additions).forEach { logsArray.put(DoseLogJson.encode(it)) }
        val eventsArray = JSONArray()
        (allEvents + events).forEach { eventsArray.put(DoseEventJson.encode(it)) }
        prefs(context).edit()
            .putString(LOGS, logsArray.toString())
            .putString(EVENTS, eventsArray.toString())
            .commit()
    }

    /** Day rollover: doses alerted but never accepted become missed. */
    @Synchronized
    fun markMissed(context: Context): Boolean {
        val updated = DoseHistory.markMissed(logs(context)) ?: return false
        val array = JSONArray()
        updated.forEach { array.put(DoseLogJson.encode(it)) }
        return prefs(context).edit()
            .putString(LOGS, array.toString())
            .commit()
    }

    /** One commit records the whole group with one click time and clears its deferred state. */
    @Synchronized
    fun recordGroup(
        context: Context,
        members: List<DoseGroups.Member>,
        status: String
    ): List<DoseItem> {
        require(status == "taken" || status == "skipped")
        val now = System.currentTimeMillis()
        val existing = logs(context)
        val snoozes = snoozes(context)
        val current = DoseLogic.todayItems(
            medications(context), existing, snoozes, now,
            overrides = timeOverrides(context)
        )
        val selected = DoseGroups.eligible(current, members)
        if (selected.isEmpty()) return emptyList()
        val keys =
            selected.map { it.id }
                .toSet()
        val array = JSONArray()
        // An awaiting entry yields to the group's decision instead of duplicating the slot.
        (existing.filterNot { DoseLogic.slotKey(it.date, it.medicationId, it.time) in keys } +
                DoseGroups.logs(selected, status, now)).forEach { array.put(DoseLogJson.encode(it)) }
        prefs(context).edit()
            .putString(LOGS, array.toString())
            .putString(SNOOZES, JSONObject(snoozes.filterKeys { it !in keys }).toString())
            .putStringSet(DEFERRED, deferred(context) - keys)
            .commit()
        return selected
    }

    @Synchronized
    fun correctHistoryTime(
        context: Context,
        expected: DoseLog,
        at: Long
    ): Boolean {
        val corrected = runCatching { DoseHistory.correctTakenAt(expected, at, System.currentTimeMillis()) }.getOrNull()
            ?: return false
        return replaceHistory(context, expected, corrected)
    }

    /** An awaiting, missed or cancelled entry is accepted with a chosen taken time; decided entries only correct their time. */
    @Synchronized
    fun acceptHistory(
        context: Context,
        expected: DoseLog,
        at: Long
    ): Boolean {
        val accepted = runCatching { DoseHistory.acceptLate(expected, at, System.currentTimeMillis()) }.getOrNull()
            ?: return false
        return replaceHistory(context, expected, accepted)
    }

    /** Status switches: taken entries may revert; unresolved entries stay unresolved without a timestamp. */
    @Synchronized
    fun resolveHistory(
        context: Context,
        expected: DoseLog,
        status: String
    ): Boolean {
        val switched = runCatching {
            if (expected.status == "taken") DoseHistory.revert(expected, status, System.currentTimeMillis())
            else DoseHistory.defer(expected, status)
        }.getOrNull() ?: return false
        return replaceHistory(context, expected, switched)
    }

    @Synchronized
    fun deleteHistory(
        context: Context,
        expected: DoseLog
    ): Boolean {
        val deleted = replaceHistory(context, expected, null)
        // The entry's rail dies with it; a deleted slot cannot reopen its reminder history.
        if (deleted) removeSlotEvents(context, DoseHistory.key(expected))
        return deleted
    }

    private fun replaceHistory(
        context: Context,
        expected: DoseLog,
        replacement: DoseLog?
    ): Boolean {
        val updated = DoseHistory.replace(logs(context), expected, replacement) ?: return false
        val array = JSONArray()
        updated.forEach { array.put(DoseLogJson.encode(it)) }
        return prefs(context).edit()
            .putString(LOGS, array.toString())
            .commit()
    }

    @Synchronized
    fun undo(
        context: Context,
        date: String,
        medicationId: String,
        time: String
    ) {
        val array = JSONArray()
        logs(context).filterNot {
            it.date == date && it.medicationId == medicationId && it.time == time
        }
            .forEach { item ->
                array.put(DoseLogJson.encode(item))
            }
        prefs(context).edit()
            .putString(LOGS, array.toString())
            .commit()
    }

    @Synchronized
    fun snoozes(context: Context): Map<String, Long> = runCatching {
        val obj = JSONObject(prefs(context).getString(SNOOZES, "{}") ?: "{}")
        obj.keys()
            .asSequence()
            .associateWith { key -> obj.optLong(key) }
            .filterValues { it > 0L }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun putSnooze(
        context: Context,
        key: String,
        until: Long
    ) =
        saveSnoozes(context, snoozes(context) + (key to until))

    @Synchronized
    fun clearSnooze(
        context: Context,
        key: String
    ) =
        saveSnoozes(context, snoozes(context) - key)

    private fun saveSnoozes(
        context: Context,
        map: Map<String, Long>
    ) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        prefs(context).edit()
            .putString(SNOOZES, obj.toString())
            .commit()
    }

    @Synchronized
    fun timeOverrides(context: Context): Map<String, String> = runCatching {
        val obj = JSONObject(prefs(context).getString(OVERRIDES, "{}") ?: "{}")
        obj.keys()
            .asSequence()
            .associateWith { obj.optString(it) }
            .filterValues(DoseLogic::isTime)
    }.getOrDefault(emptyMap())

    private fun saveOverrides(
        context: Context,
        values: Map<String, String>
    ) {
        prefs(context).edit()
            .putString(OVERRIDES, JSONObject(values).toString())
            .commit()
    }

    /** The original slot key stays stable for alarms, snoozes, logs, and undo. */
    @Synchronized
    fun moveToday(
        context: Context,
        slotKey: String,
        newTime: String
    ): Boolean {
        val now = ZonedDateTime.now()
        val overrides = timeOverrides(context)
        val items = DoseLogic.todayItems(medications(context), logs(context), snoozes(context), overrides = overrides)
        val item = items.find { it.id == slotKey } ?: return false
        if (!DoseLogic.canMoveToday(item, newTime, items, now)) return false
        // Keep earlier dates for a still-visible reminder accepted after midnight.
        // Date-scoped keys never affect subsequent recurrences.
        saveOverrides(context, overrides + (slotKey to newTime))
        clearSnooze(context, slotKey)
        clearDeferred(context, slotKey)
        // The moved slot awaits its new reminder instant; its awaiting entry referred to the old one.
        removePending(context, slotKey)
        removeSlotEvents(context, slotKey)
        return true
    }

    private fun removePending(
        context: Context,
        key: String
    ) {
        val parts = key.split('|', limit = 3)
        if (parts.size != 3) return
        val (date, id, time) = parts
        val existing = logs(context)
        val kept = existing.filterNot {
            it.date == date && it.medicationId == id && it.time == time && it.status == "pending"
        }
        if (kept.size == existing.size) return
        val array = JSONArray()
        kept.forEach { array.put(DoseLogJson.encode(it)) }
        prefs(context).edit()
            .putString(LOGS, array.toString())
            .commit()
    }

    @Synchronized
    fun acceptToday(
        context: Context,
        slotKey: String,
        at: Long
    ): Boolean {
        val item = DoseLogic.todayItems(
            medications(context), logs(context), snoozes(context),
            overrides = timeOverrides(context)
        )
            .find { it.id == slotKey } ?: return false
        val log = runCatching { DoseLogic.acceptedAt(item, at, System.currentTimeMillis()) }.getOrNull() ?: return false
        record(context, log)
        return true
    }

    fun newMedication(
        name: String,
        amount: String,
        quantity: String,
        schedules: List<DoseSchedule>,
        note: String,
        color: String,
        form: MedicationForm
    ) =
        Medication(
            UUID.randomUUID()
                .toString(), name, amount, quantity, schedules, note, color, form = form
        )

    fun colorUsage(context: Context): Map<String, Int> = runCatching {
        val obj = JSONObject(prefs(context).getString("color_usage", "{}") ?: "{}")
        obj.keys()
            .asSequence()
            .associateWith { obj.optInt(it) }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun recordColorUse(
        context: Context,
        color: String
    ) {
        val usage = colorUsage(context).toMutableMap()
        usage[color] = (usage[color] ?: 0) + 1
        val obj = JSONObject()
        usage.forEach { (key, count) -> obj.put(key, count) }
        prefs(context).edit()
            .putString("color_usage", obj.toString())
            .commit()
    }

    private fun deferred(context: Context): Set<String> =
        prefs(context).getStringSet(DEFERRED, emptySet())
            .orEmpty()
            .toSet()

    fun wasDeferred(
        context: Context,
        key: String
    ): Boolean = key in deferred(context)

    @Synchronized
    fun markDeferred(
        context: Context,
        key: String
    ) {
        prefs(context).edit()
            .putStringSet(DEFERRED, deferred(context) + key)
            .commit()
    }

    @Synchronized
    private fun clearDeferred(
        context: Context,
        key: String
    ) {
        prefs(context).edit()
            .putStringSet(DEFERRED, deferred(context) - key)
            .commit()
    }

    fun newLog(
        item: DoseItem,
        status: String
    ): DoseLog = DoseLogic.recordedLog(
        item.medication, item.date, item.time, if (status == "on_time") "taken" else status,
        item.scheduledAt,
        System.currentTimeMillis(), onTime = status == "on_time",
    )

}
