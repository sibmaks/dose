package com.example.dose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small on-device persistence layer. Never seeds real reminders with demo medications. */
object DoseStore {
    private const val PREFS = "dose_preferences_v1"
    private const val MEDICATIONS = "medications"
    private const val LOGS = "logs"
    private const val SNOOZES = "snoozes"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun medications(context: Context): List<Medication> = runCatching {
        val array = JSONArray(prefs(context).getString(MEDICATIONS, "[]"))
        val medications = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            MedicationJson.decode(obj)
        }
        if ((0 until array.length()).any { array.optJSONObject(it)?.has("schedules") == false }) {
            // Keep unreadable entries intact instead of deleting them during a format upgrade.
            (0 until array.length()).forEach { index ->
                val obj = array.optJSONObject(index)
                if (obj != null && !obj.has("schedules")) {
                    MedicationJson.decode(obj)?.let { array.put(index, MedicationJson.encode(it)) }
                }
            }
            prefs(context).edit().putString(MEDICATIONS, array.toString()).commit()
        }
        medications
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(context: Context, medication: Medication) {
        require(medication.name.isNotBlank() && medication.amount.isNotBlank())
        require(medication.schedules.isNotEmpty() && medication.schedules.all { it.validationError() == null })
        val list = medications(context).filterNot { it.id == medication.id } + medication
        val array = JSONArray()
        list.forEach { array.put(MedicationJson.encode(it)) }
        prefs(context).edit().putString(MEDICATIONS, array.toString()).commit()
    }

    @Synchronized
    fun delete(context: Context, medicationId: String) {
        val remaining = medications(context).filterNot { it.id == medicationId }
        val array = JSONArray()
        remaining.forEach { array.put(MedicationJson.encode(it)) }
        prefs(context).edit().putString(MEDICATIONS, array.toString()).commit()
        val remainingSnoozes = snoozes(context).filterKeys { !it.contains("|$medicationId|") }
        saveSnoozes(context, remainingSnoozes)
    }

    @Synchronized
    fun logs(context: Context): List<DoseLog> = runCatching {
        val array = JSONArray(prefs(context).getString(LOGS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            DoseLog(
                obj.optString("date"), obj.optString("medicationId"),
                obj.optString("medicationName"), obj.optString("dose"), obj.optString("time"),
                obj.optString("status"), obj.optLong("recordedAt"),
            ).takeIf { it.date.isNotBlank() && it.medicationId.isNotBlank() && DoseLogic.isTime(it.time) }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(context: Context, log: DoseLog) {
        require(log.status == "taken" || log.status == "skipped")
        val array = JSONArray()
        (logs(context).filterNot {
            it.date == log.date && it.medicationId == log.medicationId && it.time == log.time
        } + log).forEach { item ->
            array.put(JSONObject().apply {
                put("date", item.date); put("medicationId", item.medicationId)
                put("medicationName", item.medicationName); put("dose", item.dose)
                put("time", item.time); put("status", item.status)
                put("recordedAt", item.recordedAt)
            })
        }
        prefs(context).edit().putString(LOGS, array.toString()).commit()
        clearSnooze(context, DoseLogic.slotKey(log.date, log.medicationId, log.time))
    }

    @Synchronized
    fun undo(context: Context, date: String, medicationId: String, time: String) {
        val array = JSONArray()
        logs(context).filterNot {
            it.date == date && it.medicationId == medicationId && it.time == time
        }.forEach { item ->
            array.put(JSONObject().apply {
                put("date", item.date); put("medicationId", item.medicationId)
                put("medicationName", item.medicationName); put("dose", item.dose)
                put("time", item.time); put("status", item.status)
                put("recordedAt", item.recordedAt)
            })
        }
        prefs(context).edit().putString(LOGS, array.toString()).commit()
    }

    @Synchronized
    fun snoozes(context: Context): Map<String, Long> = runCatching {
        val obj = JSONObject(prefs(context).getString(SNOOZES, "{}") ?: "{}")
        obj.keys().asSequence().associateWith { key -> obj.optLong(key) }.filterValues { it > 0L }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun putSnooze(context: Context, key: String, until: Long) =
        saveSnoozes(context, snoozes(context) + (key to until))

    @Synchronized
    fun clearSnooze(context: Context, key: String) =
        saveSnoozes(context, snoozes(context) - key)

    private fun saveSnoozes(context: Context, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        prefs(context).edit().putString(SNOOZES, obj.toString()).commit()
    }

    fun newMedication(name: String, amount: String, quantity: String, schedules: List<DoseSchedule>, note: String, color: String, form: MedicationForm) =
        Medication(UUID.randomUUID().toString(), name, amount, quantity, schedules, note, color, form = form)

    fun colorUsage(context: Context): Map<String, Int> = runCatching {
        val obj = JSONObject(prefs(context).getString("color_usage", "{}") ?: "{}")
        obj.keys().asSequence().associateWith { obj.optInt(it) }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun recordColorUse(context: Context, color: String) {
        val usage = colorUsage(context).toMutableMap()
        usage[color] = (usage[color] ?: 0) + 1
        val obj = JSONObject()
        usage.forEach { (key, count) -> obj.put(key, count) }
        prefs(context).edit().putString("color_usage", obj.toString()).commit()
    }

    fun newLog(item: DoseItem, status: String): DoseLog = DoseLog(
        item.date, item.medication.id, item.medication.name, item.medication.dose,
        item.time, status, System.currentTimeMillis(),
    )

}
