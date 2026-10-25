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

    /** An awaiting, missed or cancelled entry is accepted with the chosen taken time, replacing the unresolved state. */
    fun acceptLate(
        log: DoseLog,
        at: Long,
        now: Long
    ): DoseLog {
        require(
            (log.status == "pending" || log.status == "missed" || log.status == "skipped") &&
                    at > 0 && at <= now
        )
        return log.copy(status = "taken", recordedAt = at, onTime = at == log.scheduledAt, clickedAt = now)
    }

    /** Pending and missed are both unresolved; switching between them records no intake time. */
    fun defer(
        log: DoseLog,
        status: String
    ): DoseLog {
        require(log.status == "pending" || log.status == "missed")
        require(status == "pending" || status == "missed")
        if (status == log.status) return log
        return log.copy(status = status, recordedAt = 0, onTime = false, clickedAt = null)
    }

    /** A taken entry may be undone: awaiting forgets the timestamp, cancelled stamps the correction itself. */
    fun revert(
        log: DoseLog,
        status: String,
        now: Long
    ): DoseLog {
        require(log.status == "taken")
        require(status == "pending" || status == "skipped")
        // Awaiting only makes sense for today's dose; a past pending entry would flip to missed at the next rollover.
        require(
            status == "skipped" || log.date == LocalDate.now()
                .toString()
        )
        return when (status) {
            "pending" -> log.copy(status = "pending", recordedAt = 0, onTime = false, clickedAt = null)
            else -> log.copy(status = "skipped", recordedAt = now, onTime = false, clickedAt = now)
        }
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
