package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseReportsTest {
    private val date = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.of("Europe/Moscow")
    private val scheduled = DoseLogic.scheduledMillis(date, "08:00", zone)
    private fun log(delayMinutes: Long, id: String = "a", status: String = "taken") =
        DoseLog(date.toString(), id, "Medication $id", "500 mg", "08:00", status, scheduled + delayMinutes * 60_000, scheduled)
    private fun report(vararg logs: DoseLog) = DoseReports.summarize(logs.toList(), through = date, legacyZone = zone)

    @Test fun emptyReportHasNoInventedAverage() {
        assertNull(report().averageDelayMillis)
        assertEquals(0, report().count)
        assertTrue(report().medications.isEmpty())
    }
    @Test fun skippedDosesDoNotAffectAverageOrSampleCount() {
        val result = report(log(10), log(200, status = "skipped"))
        assertEquals(1, result.count)
        assertEquals(600_000.0, result.averageDelayMillis!!, 0.0)
    }
    @Test fun earlyClicksAreZeroDelayAndCannotCancelLateDoses() {
        val result = report(log(-20), log(10), log(0))
        assertEquals(1, result.earlyCount)
        assertEquals(200_000.0, result.averageDelayMillis!!, 0.0)
        assertEquals(-1_200_000, DoseReports.timing(log(-20))!!.differenceMillis)
    }
    @Test fun overallAverageIsWeightedByDosesRatherThanMedications() {
        val result = report(log(0), log(10), log(50, "b"))
        assertEquals(20 * 60_000.0, result.averageDelayMillis!!, 0.0)
        assertEquals(5 * 60_000.0, result.medications.single { it.medicationId == "a" }.averageDelayMillis, 0.0)
        assertEquals(2, result.medications.single { it.medicationId == "a" }.count)
    }
    @Test fun rangeIsInclusiveAndUsesScheduledDateEvenAfterMidnight() {
        val late = log(16 * 60 + 30)
        val yesterday = log(5).copy(date = date.minusDays(1).toString())
        val future = log(5).copy(date = date.plusDays(1).toString())
        val result = DoseReports.summarize(listOf(late, yesterday, future), date, date, zone)
        assertEquals(listOf(late), result.timings.map { it.log })
        assertEquals(990 * 60_000.0, result.averageDelayMillis!!, 0.0)
    }
    @Test fun originalDeadlineIsUsedAfterSnoozing() {
        // A dose due at 08:00, snoozed to 08:10, and taken at 08:12 is 12 minutes late.
        val result = report(log(12))
        assertEquals(12 * 60_000.0, result.averageDelayMillis!!, 0.0)
    }
    @Test fun storedInstantSurvivesTimeZoneChange() {
        val result = DoseReports.summarize(listOf(log(15)), through = date, legacyZone = ZoneId.of("America/New_York"))
        assertEquals(15 * 60_000.0, result.averageDelayMillis!!, 0.0)
        assertEquals(0, result.estimatedCount)
    }
    @Test fun legacyEntriesAreEstimatedWithoutLosingExistingHistory() {
        val result = report(log(25).copy(scheduledAt = null))
        assertEquals(25 * 60_000.0, result.averageDelayMillis!!, 0.0)
        assertEquals(1, result.estimatedCount)
    }
    @Test fun invalidDatesAndMissingClickTimestampsAreExcluded() {
        val result = report(log(10).copy(date = "bad"), log(10).copy(recordedAt = 0), log(10).copy(time = "bad", scheduledAt = null))
        assertEquals(0, result.count)
    }
    @Test fun renamedMedicationKeepsItsGroupAndUsesRecentName() {
        val result = report(log(5).copy(medicationName = "Old name"), log(10).copy(medicationName = "New name"))
        assertEquals(1, result.medications.size)
        assertEquals("New name", result.medications.single().name)
    }
    @Test fun secondsAreNotRoundedBeforeAveraging() {
        val result = report(log(0).copy(recordedAt = scheduled + 30_000), log(0).copy(recordedAt = scheduled + 90_000))
        assertEquals(60_000.0, result.averageDelayMillis!!, 0.0)
    }
}
