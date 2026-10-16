package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DoseHistoryTest {
    private val date = LocalDate.of(2026, 10, 15)
    private val deadline = DoseLogic.scheduledMillis(date, "23:50")
    private val log = DoseLog(date.toString(), "deleted-medication", "Old name", "1 mg", "23:50", "taken",
        deadline + 1_200_000, deadline, false, deadline + 1_300_000)

    @Test fun `historical correction preserves original slot dose deadline and click audit`() {
        val corrected = DoseHistory.correctTakenAt(log, deadline + 600_000, deadline + 3_600_000)
        assertEquals(log.copy(recordedAt = deadline + 600_000), corrected)
        assertEquals(600_000, DoseReports.timing(corrected)!!.delayMillis)
        assertEquals(date.toString(), corrected.date)
        assertEquals(1, DoseReports.summarize(listOf(corrected), through = date).count)
        assertTrue(DoseHistory.correctTakenAt(log, deadline, deadline + 3_600_000).onTime)
        assertFalse(DoseHistory.correctTakenAt(log.copy(onTime = true), deadline + 10_000, deadline + 3_600_000).onTime)
    }

    @Test fun `legacy deadline stays estimated after correction`() {
        val corrected = DoseHistory.correctTakenAt(log.copy(scheduledAt = null), deadline, deadline + 3_600_000)
        assertNull(corrected.scheduledAt)
        assertTrue(corrected.onTime)
        assertTrue(DoseReports.timing(corrected)!!.estimated)
        assertEquals(0, DoseReports.timing(corrected)!!.delayMillis)
    }

    @Test fun `invalid times and skipped entries cannot be corrected to taken`() {
        for (at in listOf(0L, -1L, deadline + 3_600_001)) {
            assertTrue(runCatching { DoseHistory.correctTakenAt(log, at, deadline + 3_600_000) }.isFailure)
        }
        assertTrue(runCatching { DoseHistory.correctTakenAt(log.copy(status = "skipped"), deadline, deadline + 3_600_000) }.isFailure)
    }

    @Test fun `delete removes only its history row without creating a skipped log`() {
        val other = log.copy(medicationId = "other")
        val remaining = DoseHistory.replace(listOf(log, other), log, null)!!
        assertEquals(listOf(other), remaining)
        assertTrue(remaining.none { it.status == "skipped" })
        assertEquals(emptyList<DoseLog>(), DoseHistory.replace(listOf(log.copy(status = "skipped")), log.copy(status = "skipped"), null))
    }

    @Test fun `stale corrections and deletions cannot overwrite a newer action or resurrect a deleted entry`() {
        val corrected = DoseHistory.correctTakenAt(log, deadline, deadline + 3_600_000)
        assertEquals(listOf(corrected), DoseHistory.replace(listOf(log), log, corrected))
        assertNull(DoseHistory.replace(listOf(corrected), log, null))
        assertNull(DoseHistory.replace(emptyList(), log, corrected))
        assertNull(DoseHistory.replace(listOf(corrected), log, log))
    }
}
