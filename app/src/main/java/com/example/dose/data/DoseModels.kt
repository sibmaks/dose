package com.example.dose.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time values are 24-hour strings of the form HH:mm. */
data class Medication(
    val id: String,
    val name: String,
    val dose: String,
    val times: List<String>,
    val note: String = "",
    val color: String = "violet",
    val active: Boolean = true,
)

data class DoseLog(
    val date: String,
    val medicationId: String,
    val medicationName: String,
    val dose: String,
    val time: String,
    val status: String,
    val recordedAt: Long,
)

data class DoseItem(
    val medication: Medication,
    val date: String,
    val time: String,
    val log: DoseLog?,
    val snoozedUntil: Long?,
    val dueMillis: Long,
    val status: String,
) {
    val id: String get() = "${date}|${medication.id}|$time"
}

object DoseLogic {
    fun dateKey(date: LocalDate = LocalDate.now()): String = date.toString()

    fun slotKey(date: String, medicationId: String, time: String): String = "$date|$medicationId|$time"

    fun scheduledMillis(date: LocalDate, time: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(date, LocalTime.parse(time), zone).toInstant().toEpochMilli()

    fun nextOccurrence(
        time: String,
        now: ZonedDateTime,
        todayRecorded: Boolean,
    ): Long {
        val today = now.toLocalDate()
        val scheduled = ZonedDateTime.of(today, LocalTime.parse(time), now.zone)
        val date = if (todayRecorded || !scheduled.isAfter(now)) today.plusDays(1) else today
        return ZonedDateTime.of(date, LocalTime.parse(time), now.zone).toInstant().toEpochMilli()
    }

    fun todayItems(
        medications: List<Medication>,
        logs: List<DoseLog>,
        snoozes: Map<String, Long>,
        nowMillis: Long = System.currentTimeMillis(),
        date: LocalDate = LocalDate.now(),
    ): List<DoseItem> {
        val today = dateKey(date)
        val logIndex = logs.filter { it.date == today }.associateBy {
            slotKey(it.date, it.medicationId, it.time)
        }
        return medications.filter { it.active }.flatMap { medication ->
            medication.times.map { time ->
                val key = slotKey(today, medication.id, time)
                val logged = logIndex[key]
                val snoozedUntil = snoozes[key]
                val normal = scheduledMillis(date, time)
                val due = snoozedUntil ?: normal
                val status = when {
                    logged != null -> logged.status
                    snoozedUntil != null && nowMillis < snoozedUntil -> "snoozed"
                    nowMillis < due -> "upcoming"
                    nowMillis - due >= 30 * 60_000L -> "overdue"
                    else -> "due"
                }
                DoseItem(medication, today, time, logged, snoozedUntil, due, status)
            }
        }.sortedWith(compareBy({ it.time }, { it.medication.name }))
    }
}
