package io.github.sibmaks.app.dose.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Reads the original daily format and writes only the structured version. */
object MedicationJson {
    fun decode(obj: JSONObject, today: LocalDate = LocalDate.now()): Medication? = runCatching {
        val legacy = DoseUnits.splitLegacy(obj.optString("dose"))
        val scheduleArray = obj.optJSONArray("schedules")
        val schedules = if (scheduleArray == null) {
            listOf(DoseSchedule(times = strings(obj.optJSONArray("times")).filter(DoseLogic::isTime).distinct().sorted(), createdOn = today.toString()))
        } else {
            (0 until scheduleArray.length()).map { i ->
                decodeSchedule(scheduleArray.getJSONObject(i), today)
            }
        }
        Medication(
            id = obj.getString("id"), name = obj.getString("name"),
            amount = obj.optString("amount", legacy.first), quantity = obj.optString("quantity", legacy.second),
            schedules = schedules, note = obj.optString("note"), color = obj.optString("color", "violet"),
            active = obj.optBoolean("active", true),
            customForm = obj.optString("customForm"),
            form = runCatching { MedicationForm.valueOf(obj.optString("form", "PILL")) }.getOrDefault(MedicationForm.PILL),
        ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.amount.isNotBlank() && it.schedules.isNotEmpty() && it.schedules.all { rule -> rule.validationError() == null } }
    }.getOrNull()

    fun encode(med: Medication): JSONObject = JSONObject().apply {
        put("version", 3)
        put("id", med.id); put("name", med.name)
        put("amount", med.amount); put("quantity", med.quantity)
        put("note", med.note); put("color", med.color); put("active", med.active); put("form", med.form.name); put("customForm", med.customForm)
        put("schedules", JSONArray().apply {
            med.schedules.forEach { rule ->
                put(encodeSchedule(rule))
            }
        })
    }

    // The editor also uses these to restore an unfinished schedule before final validation.
    fun decodeSchedule(rule: JSONObject, today: LocalDate = LocalDate.now()) = DoseSchedule(
        kind = ScheduleKind.valueOf(rule.getString("kind")), times = strings(rule.optJSONArray("times")),
        dates = strings(rule.optJSONArray("dates")), weekDays = ints(rule.optJSONArray("weekDays")),
        monthDays = ints(rule.optJSONArray("monthDays")), from = optional(rule, "from"), until = optional(rule, "until"),
        createdOn = rule.optString("createdOn", today.toString()),
        immediateAt = if (rule.has("immediateAt") && !rule.isNull("immediateAt")) rule.getLong("immediateAt") else null,
        amount = if (rule.has("amount") && !rule.isNull("amount")) rule.getString("amount") else null,
        priority = rule.optInt("priority", 0).coerceAtLeast(0),
    )

    fun encodeSchedule(rule: DoseSchedule): JSONObject = JSONObject().apply {
        put("kind", rule.kind.name); put("times", JSONArray(rule.times)); put("dates", JSONArray(rule.dates))
        put("weekDays", JSONArray(rule.weekDays)); put("monthDays", JSONArray(rule.monthDays))
        put("from", rule.from ?: JSONObject.NULL); put("until", rule.until ?: JSONObject.NULL); put("createdOn", rule.createdOn); put("immediateAt", rule.immediateAt ?: JSONObject.NULL)
        put("amount", rule.amount ?: JSONObject.NULL); put("priority", rule.priority)
    }

    private fun optional(obj: JSONObject, key: String): String? = if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
    private fun strings(array: JSONArray?): List<String> = array?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty()
    private fun ints(array: JSONArray?): List<Int> = array?.let { (0 until it.length()).map { i -> it.getInt(i) } }.orEmpty()
}
