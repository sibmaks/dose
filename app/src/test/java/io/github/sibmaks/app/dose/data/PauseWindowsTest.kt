package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PauseWindowsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val day = LocalDate.of(2026, 10, 25)
    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day) =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    private fun window(start: Long, end: Long, canceled: Boolean = false) =
        PauseWindow(1L, start, end, canceled)
    private val now = at(13, 0)

    @Test fun `overlapping pauses silence notifications until the longest end`() {
        val a = window(at(14), at(16))
        val b = window(at(15), at(18))
        val windows = listOf(a, b)
        assertFalse(PauseWindows.isPaused(windows, now))
        assertTrue(PauseWindows.isPaused(windows, at(15, 30)))
        // 14:00–16:00 + 15:00–18:00 effectively pause until 18:00.
        assertEquals(at(18), PauseWindows.resumeAt(windows, at(15, 30)))
        assertTrue(PauseWindows.isPaused(windows, at(17, 59)))
        assertFalse(PauseWindows.isPaused(windows, at(18)))
    }

    @Test fun `windows partition into active scheduled and history`() {
        val active = window(now - 1000, now + 60_000)
        val scheduled = window(now + 60_000, now + 120_000)
        val completed = window(now - 120_000, now - 60_000)
        val canceled = window(now + 60_000, now + 120_000, canceled = true)
        val windows = listOf(active, scheduled, completed, canceled)
        assertEquals(listOf(active), PauseWindows.active(windows, now))
        assertEquals(listOf(scheduled), PauseWindows.scheduled(windows, now))
    }

    @Test fun `next transition is the nearest start or end`() {
        val scheduled = window(now + 120_000, now + 300_000)
        val running = window(now - 60_000, now + 180_000)
        assertEquals(now + 120_000, PauseWindows.nextTransition(listOf(scheduled), now))
        assertEquals(now + 180_000, PauseWindows.nextTransition(listOf(running), now))
        assertEquals(now + 120_000, PauseWindows.nextTransition(listOf(running, scheduled), now))
        assertNull(PauseWindows.nextTransition(listOf(window(now - 100, now - 50)), now))
        // Canceled windows never schedule a transition.
        assertNull(PauseWindows.nextTransition(listOf(scheduled.copy(canceled = true)), now))
    }

    @Test fun `a new pause is detected as overlapping`() {
        val running = window(at(12), at(14))
        assertTrue(PauseWindows.overlaps(listOf(running), at(13), at(15)))
        assertTrue(PauseWindows.overlaps(listOf(running), at(11), at(12, 30)))
        assertFalse(PauseWindows.overlaps(listOf(running), at(14), at(16)))
        assertFalse(PauseWindows.overlaps(listOf(running), at(10), at(12)))
        assertFalse(PauseWindows.overlaps(listOf(running.copy(canceled = true)), at(13), at(15)))
        assertFalse(PauseWindows.overlaps(emptyList(), at(13), at(15)))
    }

    @Test fun `until morning is the next seven in the morning`() {
        val evening = day.atTime(LocalTime.of(22, 0)).atZone(zone).toInstant().toEpochMilli()
        assertEquals(day.plusDays(1).atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli(), PauseWindows.morningAfter(evening, zone))
        val early = day.atTime(LocalTime.of(6, 0)).atZone(zone).toInstant().toEpochMilli()
        assertEquals(day.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli(), PauseWindows.morningAfter(early, zone))
        // Exactly at 07:00 the pause runs to the next morning.
        val atSeven = day.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli()
        assertEquals(day.plusDays(1).atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli(), PauseWindows.morningAfter(atSeven, zone))
    }

    @Test fun `pause window json roundtrip and validation`() {
        val window = PauseWindow(42L, at(12), at(14))
        assertEquals(window, PauseWindowJson.decode(PauseWindowJson.encode(window)))
        assertTrue(PauseWindowJson.decode(PauseWindowJson.encode(window.copy(canceled = true)))!!.canceled)
        assertTrue(
            PauseWindowJson.decode(
                JSONObject().put("id", 1L).put("start", at(13)).put("end", at(12))
            ) == null
        )
        assertTrue(
            PauseWindowJson.decode(
                JSONObject().put("id", 0L).put("start", at(12)).put("end", at(13))
            ) == null
        )
    }
}
