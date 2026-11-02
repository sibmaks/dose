package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TodayDoseTest {
    private val date = LocalDate.of(2026, 10, 16)
    private val zone = ZoneId.systemDefault()
    private val med =
        Medication(
            "id",
            "Test",
            "1",
            "mg",
            listOf(DoseSchedule(times = listOf("08:00", "20:00"), createdOn = date.toString()))
        )
    private val key = DoseLogic.slotKey(date.toString(), med.id, "08:00")
    private val overrides = mapOf(key to "10:00")
    private fun at(
        time: String,
        day: LocalDate = date
    ) = DoseLogic.scheduledMillis(day, time, zone)

    private fun now(time: String) = ZonedDateTime.of(date, LocalTime.parse(time), zone)
    private fun items(
        logs: List<DoseLog> = emptyList(),
        day: LocalDate = date,
        snoozes: Map<String, Long> = emptyMap()
    ) =
        DoseLogic.todayItems(listOf(med), logs, snoozes, at("09:00", day), day, overrides)

    @Test
    fun `only today's selected slot moves and identity remains stable`() {
        val moved = items().first()
        assertEquals(key, moved.id)
        assertEquals("08:00", moved.time)
        assertEquals("10:00", moved.scheduledTime)
        assertEquals(at("10:00"), moved.dueMillis)
        assertEquals("upcoming", moved.status)
        assertEquals("20:00", items()[1].scheduledTime)
        assertEquals("08:00", items(day = date.plusDays(1)).first().scheduledTime)
    }

    @Test
    fun `an original time that already passed can still be rescheduled for today`() {
        assertEquals(at("10:00"), DoseLogic.nextOccurrence(med, "08:00", now("09:00"), emptyList(), overrides))
        assertEquals(
            at("08:00", date.plusDays(1)),
            DoseLogic.nextOccurrence(med, "08:00", now("11:00"), emptyList(), overrides)
        )
    }

    @Test
    fun `moving earlier skips the original alarm rather than delivering twice`() {
        assertEquals(
            at("08:00", date.plusDays(1)),
            DoseLogic.nextOccurrence(med, "08:00", now("07:00"), emptyList(), mapOf(key to "06:00"))
        )
    }

    @Test
    fun `retaining awaiting history does not suppress the rescheduled alarm`() {
        val pending = DoseLogic.pendingLog(med, date.toString(), "08:00", at("08:00"))
        assertEquals(at("10:00"), DoseLogic.nextOccurrence(med, "08:00", now("09:00"), listOf(pending), overrides))
        val taken = DoseLogic.acceptedAt(items(listOf(pending)).first(), at("10:00"), at("10:00"))
        assertEquals(
            at("08:00", date.plusDays(1)),
            DoseLogic.nextOccurrence(med, "08:00", now("09:00"), listOf(taken), overrides)
        )
    }

    @Test
    fun `recorded moved dose resolves its original slot and undo keeps exception`() {
        val item = items().first()
        val log = DoseLogic.acceptedAt(item, at("10:15"), at("11:00"))
        assertEquals(at("08:00"), log.scheduledAt)
        assertEquals(at("11:00"), log.clickedAt)
        assertEquals(135 * 60_000L, DoseReports.timing(log)!!.delayMillis)
        assertEquals("taken", items(listOf(log)).first().status)
        assertEquals(
            at("08:00", date.plusDays(1)),
            DoseLogic.nextOccurrence(med, "08:00", now("09:00"), listOf(log), overrides)
        )
        assertEquals("10:00", items().first().scheduledTime)
    }

    @Test
    fun `snooze after moving a reminder retains the original intake deadline`() {
        val item = items(snoozes = mapOf(key to at("10:30"))).first()
        assertEquals(at("10:30"), item.dueMillis)
        assertEquals(at("10:00"), item.scheduledAt)
        assertEquals(at("08:00"), item.intakeScheduledAt)
        val log = DoseLogic.acceptedAt(item, item.dueMillis, at("11:00"))
        assertFalse(log.onTime)
        assertEquals(150 * 60_000L, DoseReports.timing(log)!!.delayMillis)
        assertEquals(150L, DoseTimeline.delayMinutes(log))
    }

    @Test
    fun `reject collisions past reminders and resolved or previous day slots`() {
        val item = items().first()
        assertTrue(DoseLogic.canMoveToday(item, "11:00", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "20:00", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "08:30", items(), now("09:00")))
        assertFalse(DoseLogic.canMoveToday(item, "bad", items(), now("09:00")))
        assertFalse(
            DoseLogic.canMoveToday(
                item.copy(
                    date = date.minusDays(1)
                        .toString()
                ), "11:00", items(), now("09:00")
            )
        )
        assertFalse(
            DoseLogic.canMoveToday(
                item.copy(log = DoseLogic.acceptedAt(item, at("08:00"), at("09:00"))),
                "11:00",
                items(),
                now("09:00")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `future acceptance is rejected`() {
        DoseLogic.acceptedAt(items().first(), at("12:00"), at("11:00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `previous day acceptance is rejected`() {
        DoseLogic.acceptedAt(items().first(), at("23:00", date.minusDays(1)), at("11:00"))
    }

    @Test
    fun `correcting acceptance keeps the captured deadline`() {
        val item = items().first()
        val original = DoseLogic.acceptedAt(item, at("10:20"), at("11:00"))
        val corrected =
            DoseLogic.acceptedAt(item.copy(log = original, scheduledAt = at("09:00")), at("08:00"), at("12:00"))
        assertEquals(at("08:00"), corrected.scheduledAt)
        assertTrue(corrected.onTime)
    }

    @Test
    fun `taking a dose exactly at its moved reminder still reports the original delay`() {
        val item = items().first()
        val log = DoseLogic.acceptedAt(item, at("10:00"), at("10:00"))
        assertFalse(log.onTime)
        assertEquals(120 * 60_000L, DoseReports.timing(log)!!.delayMillis)
        assertEquals(120L, DoseTimeline.delayMinutes(log))
        val restored = DoseLogJson.decode(DoseLogJson.encode(log))!!
        assertEquals(at("08:00"), restored.scheduledAt)
        assertEquals(120L, DoseTimeline.delayMinutes(restored))
    }

    @Test
    fun `bulk acceptance and pending history retain original deadlines after a second move`() {
        val moved = DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), at("12:00"), date, mapOf(key to "12:00"))
            .first()
        val pending = DoseLogic.pendingLog(med, moved.date, moved.time, moved.intakeScheduledAt)
        assertEquals(at("08:00"), pending.scheduledAt)
        val log =
            DoseGroups.logs(listOf(moved), "taken", at("12:00"))
                .single()
        assertEquals(at("08:00"), log.scheduledAt)
        assertEquals(240L, DoseTimeline.delayMinutes(log))
    }

    @Test
    fun `moving a reminder earlier does not turn early intake into a late one`() {
        val moved = DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), at("07:00"), date, mapOf(key to "07:00"))
            .first()
        val log = DoseLogic.acceptedAt(moved, at("07:00"), at("07:00"))
        assertEquals(-60 * 60_000L, DoseReports.timing(log)!!.differenceMillis)
        assertNull(DoseTimeline.delayMinutes(log))
    }
}
