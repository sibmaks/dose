package io.github.sibmaks.app.dose.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class MedicationForm(val label: String) {
    PILL("Pill"), CAPSULE("Capsule"), LIQUID("Liquid"), SPRAY("Spray"),
    DROPS("Drops"), INJECTION("Injection"), CREAM("Cream"), OTHER("Other");
}

enum class ScheduleKind(val label: String) {
    SPECIFIC_DATES("Specific dates"), WEEKDAYS("Days of week"), MONTH_DAYS("Days of month"), DAILY("Every day");
}

enum class ScheduleField { TIMES, DATES, START, END, BOUNDS, WEEKDAYS, MONTH_DAYS }
enum class ScheduleIssue(val field: ScheduleField, val message: String) {
    TIME_REQUIRED(ScheduleField.TIMES, "Choose at least one valid time."),
    DUPLICATE_TIMES(ScheduleField.TIMES, "Remove duplicate times in this schedule."),
    DATE_REQUIRED(ScheduleField.DATES, "Choose at least one specific date."),
    INVALID_IMMEDIATE(ScheduleField.DATES, "An immediate reminder must contain one date and time."),
    INVALID_START(ScheduleField.START, "Invalid schedule start."),
    INVALID_BOUNDS(ScheduleField.BOUNDS, "Choose valid start and end dates."),
    END_BEFORE_START(ScheduleField.END, "The end date must be on or after the start date."),
    WEEKDAY_REQUIRED(ScheduleField.WEEKDAYS, "Select at least one weekday."),
    MONTHDAY_REQUIRED(ScheduleField.MONTH_DAYS, "Select at least one day of the month."),
}

/** Bounds are inclusive; an omitted start is anchored to the day the rule was created. */
data class DoseSchedule(
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val times: List<String> = listOf("08:00"),
    val dates: List<String> = emptyList(),
    val weekDays: List<Int> = emptyList(), // ISO: Monday = 1, Sunday = 7
    val monthDays: List<Int> = emptyList(),
    val from: String? = null,
    val until: String? = null,
    val createdOn: String = LocalDate.now().toString(),
    val immediateAt: Long? = null,
) {
    fun validationError(): String? = validationIssue()?.message

    fun validationIssue(): ScheduleIssue? = when {
        times.isEmpty() || times.any { !DoseLogic.isTime(it) } -> ScheduleIssue.TIME_REQUIRED
        times.distinct().size != times.size -> ScheduleIssue.DUPLICATE_TIMES
        kind == ScheduleKind.SPECIFIC_DATES && (dates.isEmpty() || dates.any { !DoseLogic.isDate(it) }) ->
            ScheduleIssue.DATE_REQUIRED
        immediateAt != null && (immediateAt <= 0 || kind != ScheduleKind.SPECIFIC_DATES || dates.size != 1 || times.size != 1) ->
            ScheduleIssue.INVALID_IMMEDIATE
        kind != ScheduleKind.SPECIFIC_DATES && !DoseLogic.isDate(createdOn) -> ScheduleIssue.INVALID_START
        kind != ScheduleKind.SPECIFIC_DATES && (from != null && !DoseLogic.isDate(from) || until != null && !DoseLogic.isDate(until)) ->
            ScheduleIssue.INVALID_BOUNDS
        kind != ScheduleKind.SPECIFIC_DATES && until != null && until < (from ?: createdOn) ->
            ScheduleIssue.END_BEFORE_START
        kind == ScheduleKind.WEEKDAYS && (weekDays.isEmpty() || weekDays.any { it !in 1..7 }) -> ScheduleIssue.WEEKDAY_REQUIRED
        kind == ScheduleKind.MONTH_DAYS && (monthDays.isEmpty() || monthDays.any { it !in 1..31 }) -> ScheduleIssue.MONTHDAY_REQUIRED
        else -> null
    }

    fun includes(date: LocalDate): Boolean {
        if (validationError() != null) return false
        val key = date.toString()
        if (kind == ScheduleKind.SPECIFIC_DATES) return key in dates
        if (key < (from ?: createdOn) || (until != null && key > until)) return false
        return when (kind) {
            ScheduleKind.DAILY -> true
            ScheduleKind.WEEKDAYS -> date.dayOfWeek.value in weekDays
            ScheduleKind.MONTH_DAYS -> date.dayOfMonth in monthDays
            ScheduleKind.SPECIFIC_DATES -> false
        }
    }

    fun nextDate(onOrAfter: LocalDate): LocalDate? {
        if (validationError() != null) return null
        if (kind == ScheduleKind.SPECIFIC_DATES) {
            return dates.map(LocalDate::parse).filter { it >= onOrAfter }.minOrNull()
        }
        var date = maxOf(onOrAfter, LocalDate.parse(from ?: createdOn))
        // A valid monthly rule can skip February, but always matches within 62 days.
        repeat(62) {
            if (until != null && date.toString() > until) return null
            if (includes(date)) return date
            date = date.plusDays(1)
        }
        return null
    }

    companion object {
        fun immediately(now: ZonedDateTime = ZonedDateTime.now()) = DoseSchedule(
            kind = ScheduleKind.SPECIFIC_DATES,
            times = listOf(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))),
            dates = listOf(now.toLocalDate().toString()), createdOn = now.toLocalDate().toString(),
            immediateAt = now.toInstant().toEpochMilli(),
        )
    }
}

data class Medication(
    val id: String,
    val name: String,
    val amount: String,
    val quantity: String,
    val schedules: List<DoseSchedule>,
    val note: String = "",
    val color: String = "violet",
    val active: Boolean = true,
    val form: MedicationForm = MedicationForm.PILL,
    val customForm: String = "",
) {
    val formLabel: String get() = customForm.takeIf { form == MedicationForm.OTHER && it.isNotBlank() } ?: form.label
    val doseDescription: String get() = dose + if (form == MedicationForm.OTHER) " · $formLabel" else ""
    val dose: String get() = listOf(amount, quantity).filter { it.isNotBlank() }.joinToString(" ")
    val times: List<String> get() = schedules.flatMap { it.times }.distinct().sorted()
    fun timesOn(date: LocalDate): List<String> = schedules.filter { it.includes(date) }.flatMap { it.times }.distinct().sorted()
    fun scheduledMillis(date: LocalDate, time: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        schedules.filter { it.includes(date) && time in it.times }.minOfOrNull { it.immediateAt ?: DoseLogic.scheduledMillis(date, time, zone) }
            ?: DoseLogic.scheduledMillis(date, time, zone)
}

object DoseUnits {
    val common = listOf("mg", "ml", "g", "mcg", "IU", "tablet", "capsule", "drop", "spray", "puff")
    /** Preserve unstructured legacy text instead of discarding or guessing it. */
    fun splitLegacy(dose: String): Pair<String, String> {
        val match = Regex("^([0-9]+(?:[.,][0-9]+)?(?:/[0-9]+)?)\\s+(.+)$").matchEntire(dose.trim())
        return if (match != null) match.groupValues[1] to match.groupValues[2] else dose to ""
    }
}

object MedicationColors {
    val defaults = listOf("violet", "mint", "peach", "blue", "rose", "amber")
    fun hex(color: String): String = when (color) {
        "mint" -> "#39A98E"
        "peach" -> "#ED9C64"
        "blue" -> "#6899DE"
        "rose" -> "#D85C88"
        "amber" -> "#BD870F"
        "violet" -> "#7969E7"
        else -> color.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.uppercase() ?: "#7969E7"
    }
    fun choices(usage: Map<String, Int>, medications: List<Medication>, selected: String): List<String> {
        val counts = mutableMapOf<String, Int>()
        usage.forEach { (color, count) -> counts[hex(color)] = (counts[hex(color)] ?: 0) + count }
        medications.groupingBy { hex(it.color) }.eachCount().forEach { (color, count) -> counts.putIfAbsent(color, count) }
        val ranked = (counts.filterValues { it > 0 }.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { entry -> defaults.firstOrNull { hex(it) == entry.key } ?: entry.key } + defaults + selected).distinctBy(::hex)
        val visible = ranked.take(8)
        return if (visible.any { hex(it) == hex(selected) }) visible else visible.take(7) + selected
    }
}

data class DoseLog(
    val date: String, val medicationId: String, val medicationName: String, val dose: String,
    val time: String, val status: String, val recordedAt: Long,
    val scheduledAt: Long? = null,
)

data class DoseItem(
    val medication: Medication, val date: String, val time: String, val log: DoseLog?,
    val snoozedUntil: Long?, val dueMillis: Long, val status: String,
) {
    val id: String get() = "${date}|${medication.id}|$time"
}

object DoseLogic {
    /** Snoozing changes alert delivery, never the original dose deadline. */
    fun isDistantDose(item: DoseItem, nowMillis: Long): Boolean =
        item.medication.scheduledMillis(LocalDate.parse(item.date), item.time) - nowMillis > 30 * 60_000L

    fun dateKey(date: LocalDate = LocalDate.now()): String = date.toString()
    fun slotKey(date: String, medicationId: String, time: String): String = "$date|$medicationId|$time"
    fun isTime(time: String) = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(time)
    fun isDate(date: String) = runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)
    fun scheduledMillis(date: LocalDate, time: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(date, LocalTime.parse(time), zone).toInstant().toEpochMilli()

    /** Finds the next unrecorded slot across all rules, including finite and distant schedules. */
    fun nextOccurrence(medication: Medication, time: String, now: ZonedDateTime, logs: List<DoseLog>): Long? {
        if (!medication.active || !isTime(time)) return null
        val recordedDates = logs.filter { it.medicationId == medication.id && it.time == time }.map { it.date }.toSet()
        return medication.schedules.filter { time in it.times }.mapNotNull { rule ->
            var search = now.toLocalDate()
            repeat(recordedDates.size + 2) {
                val date = rule.nextDate(search) ?: return@mapNotNull null
                val at = rule.immediateAt ?: scheduledMillis(date, time, now.zone)
                if (at > now.toInstant().toEpochMilli() && date.toString() !in recordedDates) return@mapNotNull at
                search = date.plusDays(1)
            }
            null
        }.minOrNull()
    }

    /** Delivery is an effect of saving a new immediate request, not of resuming the app. */
    fun newImmediateSchedules(medication: Medication, previous: Medication?): List<DoseSchedule> {
        if (!medication.active) return emptyList()
        val existing = previous?.takeIf { it.id == medication.id }?.schedules.orEmpty()
        return medication.schedules.filter { it.immediateAt != null && it.validationError() == null && it !in existing }
            .distinctBy { it.dates.single() to it.times.single() }
    }

    fun todayItems(
        medications: List<Medication>, logs: List<DoseLog>, snoozes: Map<String, Long>,
        nowMillis: Long = System.currentTimeMillis(), date: LocalDate = LocalDate.now(),
    ): List<DoseItem> {
        val today = dateKey(date)
        val logIndex = logs.filter { it.date == today }.associateBy { slotKey(it.date, it.medicationId, it.time) }
        return medications.filter { it.active }.flatMap { medication ->
            medication.timesOn(date).map { time ->
                val key = slotKey(today, medication.id, time)
                val logged = logIndex[key]
                val snoozedUntil = snoozes[key]
                val due = snoozedUntil ?: medication.scheduledMillis(date, time)
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
