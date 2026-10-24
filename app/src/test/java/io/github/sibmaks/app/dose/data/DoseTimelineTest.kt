package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DoseTimelineTest {
    private val date = LocalDate.of(2026, 10, 25)
    private val scheduled = DoseLogic.scheduledMillis(date, "09:00")
    private fun log(status: String, recordedAt: Long = 0, onTime: Boolean = false) = DoseLog(
        date.toString(), "m1", "Test", "1 mg", "09:00", status, recordedAt, scheduled, onTime,
    )
    private fun event(type: String, at: Long, minutes: Long? = null, nextAt: Long? = null) =
        DoseEvent(date.toString(), "m1", "09:00", type, at, minutes, nextAt)

    @Test fun `every reminder snooze and the final intake appear in true order`() {
        val taken = log("taken", scheduled + 50 * 60_000)
        val rows = DoseTimeline.build(
            taken, listOf(
                event("reminder", scheduled),
                event("snooze", scheduled + 2 * 60_000, 10, scheduled + 12 * 60_000),
                event("repeat", scheduled + 12 * 60_000),
                event("snooze", scheduled + 15 * 60_000, 30, scheduled + 45 * 60_000),
                event("repeat", scheduled + 45 * 60_000),
                // Events of a different date and time must not leak into this slot.
                DoseEvent(date.minusDays(1).toString(), "m1", "09:00", "reminder", scheduled - 86_400_000),
                event("reminder", scheduled).copy(time = "08:00"),
            )
        )
        assertEquals(listOf("reminder", "snooze", "repeat", "snooze", "repeat", "taken"), rows.map { it.kind })
        assertEquals(
            listOf(scheduled, scheduled + 2 * 60_000, scheduled + 12 * 60_000,
                scheduled + 15 * 60_000, scheduled + 45 * 60_000, scheduled + 50 * 60_000),
            rows.map { it.at }
        )
        assertEquals(10L, rows[1].minutes)
        assertEquals(scheduled + 12 * 60_000, rows[1].nextAt)
        assertEquals(50L, rows.last().delayMinutes)
        assertFalse(rows.last().onTime)
    }

    @Test fun `an on-time intake carries no delay`() {
        val rows = DoseTimeline.build(log("taken", scheduled, onTime = true), listOf(event("reminder", scheduled)))
        assertEquals(listOf("reminder", "taken"), rows.map { it.kind })
        assertTrue(rows.last().onTime)
        assertNull(rows.last().delayMinutes)
    }

    @Test fun `missed rollover lands at the start of the day after the dose`() {
        val rows = DoseTimeline.build(
            log("missed"),
            listOf(event("reminder", scheduled), event("repeat", scheduled + 12 * 60_000))
        )
        assertEquals("missed", rows.last().kind)
        assertEquals(DoseLogic.scheduledMillis(date.plusDays(1), "00:00"), rows.last().at)
        assertEquals(scheduled + 12 * 60_000, rows.first { it.kind == "repeat" }.at)
    }

    @Test fun `awaiting entries keep only their reminder rail`() {
        val rows = DoseTimeline.build(
            log("pending"),
            listOf(event("reminder", scheduled), event("snooze", scheduled + 60_000, 10, scheduled + 11 * 60_000))
        )
        assertEquals(listOf("reminder", "snooze"), rows.map { it.kind })
    }

    @Test fun `legacy entries without events still show their outcome`() {
        assertEquals(listOf("taken"), DoseTimeline.build(log("taken", scheduled), emptyList()).map { it.kind })
        val skippedAt = scheduled + 5 * 60_000
        assertEquals(listOf("skipped"), DoseTimeline.build(log("skipped", skippedAt), emptyList()).map { it.kind })
    }

    @Test fun `late delay rounds to whole minutes and stays null for early acceptance`() {
        assertEquals(50L, DoseTimeline.delayMinutes(log("taken", scheduled + 50 * 60_000)))
        assertEquals(1L, DoseTimeline.delayMinutes(log("taken", scheduled + 40_000)))
        assertNull(DoseTimeline.delayMinutes(log("taken", scheduled - 60_000)))
        assertNull(DoseTimeline.delayMinutes(log("taken", scheduled, onTime = true)))
        assertNull(DoseTimeline.delayMinutes(log("taken", scheduled).copy(scheduledAt = null)))
        assertNull(DoseTimeline.delayMinutes(log("pending")))
    }

    @Test fun `event json roundtrip and validation`() {
        val snooze = event("snooze", scheduled, 10, scheduled + 10 * 60_000)
        assertEquals(snooze, DoseEventJson.decode(DoseEventJson.encode(snooze)))
        val reminder = event("reminder", scheduled)
        assertEquals(reminder, DoseEventJson.decode(DoseEventJson.encode(reminder)))
        assertTrue(
            DoseEventJson.decode(
                JSONObject().put("date", "").put("medicationId", "m1").put("time", "09:00")
                    .put("type", "reminder").put("at", 1L)
            ) == null
        )
        assertTrue(
            DoseEventJson.decode(
                JSONObject().put("date", date.toString()).put("medicationId", "m1").put("time", "09:00")
                    .put("type", "taken").put("at", 1L)
            ) == null
        )
    }
}
