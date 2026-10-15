package com.example.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DoseLogicTest {
    private val today = LocalDate.of(2026, 10, 14) // Wednesday
    private val zone = ZoneId.of("Europe/Helsinki")
    private fun rule(kind: ScheduleKind = ScheduleKind.DAILY) = DoseSchedule(kind, createdOn = today.toString())
    private fun med(vararg rules: DoseSchedule) = Medication("med-1", "Test", "500", "mg", rules.toList())
    private fun now(time: String, date: LocalDate = today) = ZonedDateTime.of(date, LocalTime.parse(time), zone)
    private fun next(med: Medication, time: String = "08:00", at: ZonedDateTime = now("07:00"), logs: List<DoseLog> = emptyList()) =
        DoseLogic.nextOccurrence(med, time, at, logs)
    private fun at(date: LocalDate = today, time: String = "08:00") = DoseLogic.scheduledMillis(date, time, zone)
    private fun log(date: LocalDate = today, status: String = "taken") = DoseLog(date.toString(), "med-1", "Test", "500 mg", "08:00", status, 123)

    @Test fun dailyBeforeAndAfterDose() {
        assertEquals(at(), next(med(rule())))
        assertEquals(at(today.plusDays(1)), next(med(rule()), at = now("09:00")))
        assertEquals(at(today.plusDays(1)), next(med(rule()), at = now("08:00")))
    }
    @Test fun takenAndSkippedSlotsAdvanceToNextDate() {
        listOf("taken", "skipped").forEach {
            assertEquals(at(today.plusDays(1)), next(med(rule()), logs = listOf(log(status = it))))
        }
    }
    @Test fun futureRecordedSlotsAreAlsoExcluded() {
        assertEquals(at(today.plusDays(2)), next(med(rule()), logs = listOf(log(), log(today.plusDays(1)))))
    }
    @Test fun specificDatesExpireAndCanBeFarInFuture() {
        val distant = today.plusYears(5)
        val specific = rule(ScheduleKind.SPECIFIC_DATES).copy(dates = listOf(today.toString(), distant.toString()))
        assertEquals(at(distant), next(med(specific), at = now("09:00")))
        assertNull(next(med(specific), at = now("09:00", distant)))
    }
    @Test fun multipleWeekdaysAndTimes() {
        val weekly = rule(ScheduleKind.WEEKDAYS).copy(weekDays = listOf(1, 5), times = listOf("08:00", "20:00"))
        assertEquals(at(LocalDate.of(2026, 10, 16)), next(med(weekly)))
        assertEquals(at(LocalDate.of(2026, 10, 16), "20:00"), next(med(weekly), "20:00"))
        assertEquals(listOf("08:00", "20:00"), med(weekly).timesOn(LocalDate.of(2026, 10, 16)))
        assertTrue(med(weekly).timesOn(today).isEmpty())
    }
    @Test fun monthly31SkipsFebruaryWithoutClamping() {
        val monthly = rule(ScheduleKind.MONTH_DAYS).copy(monthDays = listOf(31), from = "2026-01-01")
        assertEquals(at(LocalDate.of(2026, 3, 31)), next(med(monthly), at = now("07:00", LocalDate.of(2026, 2, 1))))
        assertFalse(monthly.includes(LocalDate.of(2026, 2, 28)))
    }
    @Test fun monthly29IncludesLeapDay() {
        val monthly = rule(ScheduleKind.MONTH_DAYS).copy(monthDays = listOf(29), from = "2026-01-01")
        assertEquals(at(LocalDate.of(2028, 2, 29)), next(med(monthly), at = now("07:00", LocalDate.of(2028, 2, 1))))
        assertEquals(at(LocalDate.of(2027, 3, 29)), next(med(monthly), at = now("07:00", LocalDate.of(2027, 2, 1))))
    }
    @Test fun optionalBoundsAreInclusiveAndDefaultStartIsStable() {
        val daily = rule().copy(until = today.plusDays(1).toString())
        assertFalse(daily.includes(today.minusDays(1)))
        assertTrue(daily.includes(today))
        assertTrue(daily.includes(today.plusDays(1)))
        assertFalse(daily.includes(today.plusDays(2)))
        assertNull(next(med(daily), at = now("09:00", today.plusDays(1))))
        assertTrue(rule().includes(today.plusYears(10)))
    }
    @Test fun futureStartAndEndOnly() {
        val start = today.plusYears(2)
        assertEquals(at(start), next(med(rule().copy(from = start.toString()))))
        assertNotNull(next(med(rule().copy(until = today.toString()))))
    }
    @Test fun validationRejectsMissingSelectionsAndReversedBounds() {
        assertNotNull(rule(ScheduleKind.WEEKDAYS).validationError())
        assertNotNull(rule(ScheduleKind.MONTH_DAYS).copy(monthDays = listOf(32)).validationError())
        assertNotNull(rule(ScheduleKind.SPECIFIC_DATES).validationError())
        assertNotNull(rule(ScheduleKind.SPECIFIC_DATES).copy(dates = listOf("2026-02-30")).validationError())
        assertNotNull(rule().copy(from = "2026-10-20", until = "2026-10-19").validationError())
        assertNotNull(rule().copy(until = "2026-10-13").validationError())
        assertNotNull(rule().copy(times = listOf("08:00", "08:00")).validationError())
        assertNotNull(rule().copy(times = listOf("24:00")).validationError())
        assertNotNull(rule().copy(times = emptyList()).validationError())
    }
    @Test fun overlappingRulesYieldOneSlotAndChooseEarliestAlarm() {
        val specific = rule(ScheduleKind.SPECIFIC_DATES).copy(dates = listOf(today.toString()))
        val medication = med(rule(), specific)
        assertEquals(1, DoseLogic.todayItems(listOf(medication), emptyList(), emptyMap(), at(), today).size)
        assertEquals(at(), next(medication))
    }
    @Test fun differentRuleTimesRemainIndependent() {
        val future = rule(ScheduleKind.SPECIFIC_DATES).copy(dates = listOf(today.plusDays(3).toString()), times = listOf("20:00"))
        val medication = med(rule(), future)
        assertEquals(listOf("08:00"), medication.timesOn(today))
        assertEquals(at(today.plusDays(3), "20:00"), next(medication, "20:00"))
    }
    @Test fun pausedAndExpiredMedicationHaveNoAlarm() {
        assertNull(next(med(rule()).copy(active = false)))
        val expired = rule(ScheduleKind.SPECIFIC_DATES).copy(dates = listOf(today.minusDays(1).toString()))
        assertNull(next(med(expired)))
    }
    @Test fun recordedDoseBeatsUpcomingTimeAndSnooze() {
        val medication = med(rule())
        val items = DoseLogic.todayItems(listOf(medication), listOf(log()), mapOf(DoseLogic.slotKey(today.toString(), medication.id, "08:00") to Long.MAX_VALUE), at(time = "07:00"), today)
        assertEquals("taken", items.single().status)
    }
    @Test fun snoozedDoseReportsSnoozedUntilNewDueTime() {
        val medication = med(rule())
        val normal = DoseLogic.scheduledMillis(today, "09:00")
        val snooze = normal + 600_000L
        val key = DoseLogic.slotKey(today.toString(), medication.id, "08:00")
        assertEquals("snoozed", DoseLogic.todayItems(listOf(medication), emptyList(), mapOf(key to snooze), normal, today).single().status)
        assertEquals("due", DoseLogic.todayItems(listOf(medication), emptyList(), mapOf(key to snooze), snooze, today).single().status)
    }
    @Test fun noDosesOnUnselectedDay() {
        assertTrue(DoseLogic.todayItems(listOf(med(rule(ScheduleKind.WEEKDAYS).copy(weekDays = listOf(1)))), emptyList(), emptyMap(), at(), today).isEmpty())
    }
    @Test fun daylightSavingGapUsesLocalZoneResolution() {
        val date = LocalDate.of(2026, 3, 29)
        val medication = med(rule().copy(from = date.toString(), times = listOf("03:30")))
        val expected = ZonedDateTime.of(date, LocalTime.of(3, 30), zone).toInstant().toEpochMilli()
        assertEquals(expected, next(medication, "03:30", now("00:00", date)))
    }
    @Test fun legacyDoseSplittingPreservesUnitsAndUnstructuredText() {
        assertEquals("500" to "mg", DoseUnits.splitLegacy("500 mg"))
        assertEquals("1/2" to "tablet", DoseUnits.splitLegacy("1/2 tablet"))
        assertEquals("0,5" to "ml", DoseUnits.splitLegacy("0,5 ml"))
        assertEquals("As directed" to "", DoseUnits.splitLegacy("As directed"))
    }
    @Test fun colorListRanksUsageAndCapsAtEight() {
        val usage = (1..12).associate { "#%06X".format(it) to it }
        val choices = MedicationColors.choices(usage, emptyList(), "#000001")
        assertEquals(8, choices.size)
        assertEquals("#00000C", choices.first())
        assertTrue("#000001" in choices)
        assertEquals(6, MedicationColors.defaults.size)
    }
    @Test fun namedAndCustomVersionsOfSameColorShareUsage() {
        val choices = MedicationColors.choices(mapOf("mint" to 3, "#39a98e" to 3, "blue" to 5), emptyList(), "blue")
        assertEquals("mint", choices.first())
        assertEquals(1, choices.count { MedicationColors.hex(it) == "#39A98E" })
    }
    @Test fun existingColorsRankAndSameHexDoesNotDuplicate() {
        val choices = MedicationColors.choices(emptyMap(), listOf(med(rule()).copy(color = "mint")), "#39A98E")
        assertEquals("mint", choices.first())
        assertEquals(1, choices.count { MedicationColors.hex(it) == "#39A98E" })
    }
}
