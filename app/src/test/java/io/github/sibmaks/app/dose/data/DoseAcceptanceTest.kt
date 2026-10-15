package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseAcceptanceTest {
    private val date = LocalDate.of(2026, 10, 15)
    private val med = Medication("id", "Test", "1", "mg", listOf(DoseSchedule(createdOn = date.toString())))
    private val scheduled = DoseLogic.scheduledMillis(date, "23:55", ZoneId.of("Europe/Moscow"))
    private val clicked = scheduled + 20 * 60_000L // after midnight, after a snooze
    private fun record(onTime: Boolean) = DoseLogic.recordedLog(med, date.toString(), "23:55", "taken", scheduled, clicked, onTime)

    @Test fun `Taken preserves the click time and original deadline`() {
        val log = record(false)
        assertEquals(clicked, log.recordedAt)
        assertEquals(clicked, log.clickedAt)
        assertEquals(scheduled, log.scheduledAt)
        assertFalse(log.onTime)
        assertEquals(20 * 60_000L, DoseReports.timing(log)!!.delayMillis)
    }
    @Test fun `On Time uses original deadline even across midnight and persists explicit choice`() {
        val log = DoseLogJson.decode(JSONObject(DoseLogJson.encode(record(true)).toString()))!!
        assertEquals(scheduled, log.recordedAt)
        assertEquals(clicked, log.clickedAt)
        assertTrue(log.onTime)
        assertEquals(date.toString(), log.date)
        assertEquals(0L, DoseReports.timing(log)!!.delayMillis)
        assertEquals(0.0, DoseReports.summarize(listOf(log), date, date).averageDelayMillis!!, 0.0)
    }
    @Test fun `a notification needs prior deferral and a reached original deadline`() {
        assertFalse(DoseLogic.canRecordOnTime(false, scheduled, clicked))
        assertFalse(DoseLogic.canRecordOnTime(true, scheduled, scheduled - 1))
        assertTrue(DoseLogic.canRecordOnTime(true, scheduled, scheduled))
        assertTrue(DoseLogic.canRecordOnTime(true, scheduled, clicked))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `On Time cannot record a future acceptance`() {
        DoseLogic.recordedLog(med, date.toString(), "23:55", "taken", scheduled, scheduled - 1, true)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `skip cannot masquerade as On Time`() {
        DoseLogic.recordedLog(med, date.toString(), "23:55", "skipped", scheduled, clicked, true)
    }
}
