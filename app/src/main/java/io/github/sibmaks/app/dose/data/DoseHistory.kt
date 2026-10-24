package io.github.sibmaks.app.dose.data

import java.time.LocalDate

/** Corrections change acceptance time, retaining the dose's identity, deadline and click audit. */
object DoseHistory {
    fun key(log: DoseLog): String = DoseLogic.slotKey(log.date, log.medicationId, log.time)

    fun correctTakenAt(
        log: DoseLog,
        at: Long,
        now: Long
    ): DoseLog {
        require(log.status == "taken" && at > 0 && at <= now)
        val deadline =
            log.scheduledAt ?: DoseReports.timing(log)
                ?.let { log.recordedAt - it.differenceMillis }
        return log.copy(recordedAt = at, onTime = deadline == at)
    }

    /** Doses alerted but never accepted become missed once their day is over; null means nothing changed. */
    fun markMissed(
        logs: List<DoseLog>,
        today: String = LocalDate.now()
            .toString()
    ): List<DoseLog>? {
        if (logs.none { it.status == "pending" && it.date < today }) return null
        return logs.map { if (it.status == "pending" && it.date < today) it.copy(status = "missed") else it }
    }

    /** Compare the opened entry, so a stale editor cannot replace a later correction or undo. */
    fun replace(
        logs: List<DoseLog>,
        expected: DoseLog,
        replacement: DoseLog?
    ): List<DoseLog>? {
        if (logs.find { key(it) == key(expected) } != expected) return null
        require(replacement == null || key(replacement) == key(expected))
        return logs.mapNotNull { if (key(it) == key(expected)) replacement else it }
    }
}
