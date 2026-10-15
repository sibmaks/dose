package com.example.dose.data

import java.time.LocalDate
import java.time.ZoneId

/** The original scheduled time, never the snoozed deadline, is the report baseline. */
data class DoseTiming(val log: DoseLog, val differenceMillis: Long, val estimated: Boolean) {
    val delayMillis: Long get() = differenceMillis.coerceAtLeast(0)
}

data class MedicationDelay(val medicationId: String, val name: String, val count: Int, val averageDelayMillis: Double)
data class DelayReport(val timings: List<DoseTiming>, val medications: List<MedicationDelay>) {
    val count: Int get() = timings.size
    val averageDelayMillis: Double? get() = timings.takeIf { it.isNotEmpty() }?.map { it.delayMillis }?.average()
    val earlyCount: Int get() = timings.count { it.differenceMillis < 0 }
    val estimatedCount: Int get() = timings.count { it.estimated }
}

object DoseReports {
    fun timing(log: DoseLog, legacyZone: ZoneId = ZoneId.systemDefault()): DoseTiming? {
        if (log.status != "taken" || log.recordedAt <= 0) return null
        val scheduled = log.scheduledAt ?: runCatching {
            DoseLogic.scheduledMillis(LocalDate.parse(log.date), log.time, legacyZone)
        }.getOrNull() ?: return null
        return DoseTiming(log, log.recordedAt - scheduled, log.scheduledAt == null)
    }

    /** Periods are selected by the scheduled date, including doses taken after midnight. */
    fun summarize(logs: List<DoseLog>, from: LocalDate? = null, through: LocalDate = LocalDate.now(), legacyZone: ZoneId = ZoneId.systemDefault()): DelayReport {
        val timings = logs.filter { log ->
            runCatching {
                val date = LocalDate.parse(log.date)
                date <= through && (from == null || date >= from)
            }.getOrDefault(false)
        }.mapNotNull { timing(it, legacyZone) }
        val medications = timings.groupBy { it.log.medicationId }.map { (id, doses) ->
            MedicationDelay(id, doses.maxBy { it.log.recordedAt }.log.medicationName, doses.size, doses.map { it.delayMillis }.average())
        }.sortedWith(compareByDescending<MedicationDelay> { it.averageDelayMillis }.thenBy { it.name })
        return DelayReport(timings, medications)
    }
}
