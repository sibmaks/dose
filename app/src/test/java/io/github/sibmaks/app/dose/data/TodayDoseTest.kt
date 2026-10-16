package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TodayDoseTest {
    private val date = LocalDate.of(2026, 10, 16)
    private val zone = ZoneId.systemDefault()
    private val med = Medication("id", "Test", "1", "mg", listOf(DoseSchedule(times = listOf("08:00", "20:00"), createdOn = date.toString())))
    private val key = DoseLogic.slotKey(date.toString(), med.id, "08:00")
    private val overrides = mapOf(key to "10:00")
    private fun at(time: String, day: LocalDate = date) = DoseLogic.scheduledMillis(day, time, zone)
    private fun now(time: String) = ZonedDateTime.of(date, LocalTime.parse(time), zone)
    private fun items(logs: List<DoseLog> = emptyList(), day: LocalDate = date, snoozes: Map<String, Long> = emptyMap()) =
        DoseLogic.todayItems(listOf(med), logs, snoozes, at("09:00", day), day, overrides)

    @Test fun `only today's selected slot moves and identity remains stable`() {
        val moved = items().first()
        assertEquals(key, moved.id)
        assertEquals("08:00", moved.time)
        assertEquals("10:00", moved.scheduledTime)
        assertEquals(at("10:00"), moved.dueMillis)
        assertEquals("upcoming", moved.status)
        assertEquals("20:00", items()[1].scheduledTime)
        assertEquals("08:00", items(day = date.plusDays(1)).first().scheduledTime)
    }
    @Test fun `an original time that already passed can still be rescheduled for today`() {
        assertEquals(at("10:00"), DoseLogic.nextOccurrence(med, "08:00", now("09:00"), emptyList(), overrides))
        assertEquals(at("08:00", date.plusDays(1)), DoseLogic.nextOccurrence(med, "08:00", now("11:00"), emptyList(), overrides))
    }
    @Test fun `moving earlier skips the original alarm rather than delivering twice`() {
        assertEquals(at("08:00", date.plusDays(1)), DoseLogic.nextOccurrence(med, "08:00", now("07:00"), emptyList(), mapOf(key to "06:00")))
    }
    @Test fun `recorded moved dose resolves its original slot and undo keeps exception`() {
        val item = items().first()
        val log = DoseLogic.acceptedAt(item, at("10:15"), at("11:00"))
        assertEquals(at("10:00"), log.scheduledAt)
        assertEquals(at("11:00"), log.clickedAt)
        assertEquals(15 * 60_000L, DoseReports.timing(log)!!.delayMillis)
        assertEquals("taken", items(listOf(log)).first().status)
        assertEquals(at("08:00", date.plusDays(1)), DoseLogic.nextOccurrence(med, "08:00", now("09:00"), listOf(log), overrides))
        assertEquals("10:00", items().first().scheduledTime)
    }
    @Test fun `snooze affects alert time but not moved deadline`() {
        val item = items(snoozes = mapOf(key to at("10:30"))).first()
        assertEquals(at("10:30"), item.dueMillis)
        assertEquals(at("10:00"), item.scheduledAt)
        val log = DoseLogic.acceptedAt(item, item.scheduledAt, at("11:00"))
        assertTrue(log.onTime)
        assertEquals(0L, DoseReports.timing(log)!!.delayMillis)
    }
    @Test fun `reject collisions past reminders and resolved or previous day slots`() {
        val item = items().first()
        assertTrue(DoseLogic.canMoveToday(item, "11:00", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "20:00", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "08:30", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "bad", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item.copy(date = date.minusDays(1).toString()), "11:00", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item.copy(log = DoseLogic.acceptedAt(item, at("08:00"), at("09:00"))), "11:00", items(), now("09:00")))
    }
    @Test(expected = IllegalArgumentException::class) fun `future acceptance is rejected`() {
        DoseLogic.acceptedAt(items().first(), at("12:00"), at("11:00"))
    }
    @Test(expected = IllegalArgumentException::class) fun `previous day acceptance is rejected`() {
        DoseLogic.acceptedAt(items().first(), at("23:00", date.minusDays(1)), at("11:00"))
    }
    @Test fun `correcting acceptance keeps the captured deadline`() {
        val item = items().first()
        val original = DoseLogic.acceptedAt(item, at("10:20"), at("11:00"))
        val corrected = DoseLogic.acceptedAt(item.copy(log = original, scheduledAt = at("09:00")), at("10:00"), at("12:00"))
        assertEquals(at("10:00"), corrected.scheduledAt)
        assertTrue(corrected.onTime)
    }
}
