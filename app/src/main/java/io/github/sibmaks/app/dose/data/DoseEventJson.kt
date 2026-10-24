package io.github.sibmaks.app.dose.data

import org.json.JSONObject

object DoseEventJson {
    fun decode(obj: JSONObject): DoseEvent? = DoseEvent(
        obj.optString("date"), obj.optString("medicationId"), obj.optString("time"),
        obj.optString("type"), obj.optLong("at"),
        minutes = if (obj.has("minutes") && !obj.isNull("minutes")) obj.optLong("minutes")
            .takeIf { it > 0 } else null,
        nextAt = if (obj.has("nextAt") && !obj.isNull("nextAt")) obj.optLong("nextAt")
            .takeIf { it > 0 } else null,
    ).takeIf {
        it.date.isNotBlank() && it.medicationId.isNotBlank() && DoseLogic.isTime(it.time) &&
                it.at > 0 && it.type in listOf("reminder", "repeat", "snooze")
    }

    fun encode(event: DoseEvent): JSONObject = JSONObject().apply {
        put("date", event.date); put("medicationId", event.medicationId); put("time", event.time)
        put("type", event.type); put("at", event.at)
        if (event.minutes != null) put("minutes", event.minutes)
        if (event.nextAt != null) put("nextAt", event.nextAt)
    }
}
