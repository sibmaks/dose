package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UpcomingDoseTest {
    private val date = LocalDate.of(2026, 10, 15)
    private val med = Medication("id", "Test", "1", "mg", listOf(DoseSchedule(times = listOf("12:00"), createdOn = date.toString())))
    private val scheduled = med.scheduledMillis(date, "12:00")
    private fun item(snooze: Long? = null) = DoseItem(med, date.toString(), "12:00", null, snooze, snooze ?: scheduled, "upcoming")

    @Test fun `upcoming card hides take only beyond thirty minutes`() {
        assertTrue(DoseLogic.isDistantDose(item(), scheduled - 30 * 60_000L - 1))
        assertFalse(DoseLogic.isDistantDose(item(), scheduled - 30 * 60_000L))
        assertFalse(DoseLogic.isDistantDose(item(), scheduled))
        assertFalse(DoseLogic.isDistantDose(item(), scheduled + 60_000L))
    }
    @Test fun `snooze never turns a due dose into a distant upcoming dose`() {
        assertFalse(DoseLogic.isDistantDose(item(scheduled + 60 * 60_000L), scheduled + 60_000L))
    }
}
