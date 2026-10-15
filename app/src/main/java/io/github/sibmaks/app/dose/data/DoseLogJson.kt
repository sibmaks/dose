package io.github.sibmaks.app.dose.data

import org.json.JSONObject

object DoseLogJson {
    fun decode(obj: JSONObject): DoseLog? = DoseLog(
        obj.optString("date"), obj.optString("medicationId"), obj.optString("medicationName"),
        obj.optString("dose"), obj.optString("time"), obj.optString("status"), obj.optLong("recordedAt"),
        scheduledAt = if (obj.has("scheduledAt") && !obj.isNull("scheduledAt")) obj.optLong("scheduledAt").takeIf { it > 0 } else null,
        onTime = obj.optBoolean("onTime", false),
        clickedAt = if (obj.has("clickedAt") && !obj.isNull("clickedAt")) obj.optLong("clickedAt").takeIf { it > 0 } else null,
    ).takeIf { it.date.isNotBlank() && it.medicationId.isNotBlank() && DoseLogic.isTime(it.time) }

    fun encode(log: DoseLog): JSONObject = JSONObject().apply {
        put("date", log.date); put("medicationId", log.medicationId); put("medicationName", log.medicationName)
        put("dose", log.dose); put("time", log.time); put("status", log.status); put("recordedAt", log.recordedAt)
        put("scheduledAt", log.scheduledAt ?: JSONObject.NULL)
        put("onTime", log.onTime); put("clickedAt", log.clickedAt ?: JSONObject.NULL)
    }
}
