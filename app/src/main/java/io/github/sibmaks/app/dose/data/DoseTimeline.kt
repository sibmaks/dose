package io.github.sibmaks.app.dose.data

import java.time.LocalDate

/** One rendered rail row; kind names the icon and strings are chosen by the UI layer. */
data class TimelineEntry(
    val at: Long,
    val kind: String, // "reminder" | "repeat" | "snooze" | "taken" | "skipped" | "missed"
    val minutes: Long? = null,
    val nextAt: Long? = null,
    val onTime: Boolean = false,
    val delayMinutes: Long? = null,
)

/**
 * Builds the complete reminder and action rail of one slot from its persisted events and final log.
 * The log supplies the current outcome (taken/skipped/missed); awaiting entries have no outcome yet.
 */
object DoseTimeline {
    /** Late-intake delay in whole minutes; null when on time, early, or without a known deadline. */
    fun delayMinutes(log: DoseLog): Long? {
        if (log.status != "taken" || log.onTime) return null
        val scheduled = log.scheduledAt ?: return null
        val late = log.recordedAt - scheduled
        if (late <= 0) return null
        return (late + 30_000) / 60_000
    }

    fun build(
        log: DoseLog,
        events: List<DoseEvent>
    ): List<TimelineEntry> {
        val slot = DoseLogic.slotKey(log.date, log.medicationId, log.time)
        val rail = events.asSequence()
            .filter { DoseLogic.slotKey(it.date, it.medicationId, it.time) == slot }
            .sortedBy { it.at }
            .map { TimelineEntry(it.at, it.type, it.minutes, it.nextAt) }
            .toList()
        val outcome = when (log.status) {
            "taken" -> TimelineEntry(log.recordedAt, "taken", onTime = log.onTime, delayMinutes = delayMinutes(log))
            "skipped" -> TimelineEntry(log.recordedAt, "skipped")
            // The rollover fires at the start of the day after the dose's own day.
            "missed" -> runCatching {
                TimelineEntry(DoseLogic.scheduledMillis(LocalDate.parse(log.date).plusDays(1), "00:00"), "missed")
            }.getOrNull()
            else -> null
        }
        // A stable sort keeps same-instant reminder rows ahead of the intake they prompted.
        return if (outcome == null) rail else (rail + outcome).sortedBy { it.at }
    }
}
