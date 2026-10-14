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
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val timesArray = obj.optJSONArray("times") ?: JSONArray()
            val times = (0 until timesArray.length()).mapNotNull { j ->
                timesArray.optString(j).takeIf { isTime(it) }
            }.distinct().sorted()
            val id = obj.optString("id")
            val name = obj.optString("name")
            val dose = obj.optString("dose")
            if (id.isBlank() || name.isBlank() || dose.isBlank() || times.isEmpty()) return@mapNotNull null
            Medication(id, name, dose, times, obj.optString("note"), obj.optString("color", "violet"), obj.optBoolean("active", true))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(context: Context, medication: Medication) {
        val list = medications(context).filterNot { it.id == medication.id } + medication
        val array = JSONArray()
        list.forEach { med ->
            array.put(JSONObject().apply {
                put("id", med.id)
                put("name", med.name)
                put("dose", med.dose)
                put("times", JSONArray(med.times))
                put("note", med.note)
                put("color", med.color)
                put("active", med.active)
            })
        }
        prefs(context).edit().putString(MEDICATIONS, array.toString()).commit()
    }

    @Synchronized
    fun delete(context: Context, medicationId: String) {
        val remaining = medications(context).filterNot { it.id == medicationId }
        val array = JSONArray()
        remaining.forEach { med ->
            array.put(JSONObject().apply {
                put("id", med.id); put("name", med.name); put("dose", med.dose)
                put("times", JSONArray(med.times)); put("note", med.note)
                put("color", med.color); put("active", med.active)
            })
        }
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
            ).takeIf { it.date.isNotBlank() && it.medicationId.isNotBlank() && isTime(it.time) }
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

    fun newMedication(name: String, dose: String, times: List<String>, note: String, color: String) =
        Medication(UUID.randomUUID().toString(), name, dose, times, note, color)

    fun newLog(item: DoseItem, status: String): DoseLog = DoseLog(
        item.date, item.medication.id, item.medication.name, item.medication.dose,
        item.time, status, System.currentTimeMillis(),
    )

    private fun isTime(time: String) = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(time)
}
