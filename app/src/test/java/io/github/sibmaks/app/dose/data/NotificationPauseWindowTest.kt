package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NotificationPauseWindowTest {
    private val now = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test fun `selected future date and time becomes an absolute deadline`() {
        val selected = NotificationPauseWindow.selectedUntil("2026-10-17", "12:30", now)!!
        assertEquals(ZonedDateTime.of(2026, 10, 17, 12, 30, 0, 0, now.zone).toInstant().toEpochMilli(), selected)
        assertTrue(NotificationPauseWindow.active(selected, now.toInstant().toEpochMilli()))
        // Stored deadlines are instants, independent of a later device timezone change.
        val elsewhere = now.withZoneSameInstant(ZoneId.of("America/New_York"))
        assertTrue(NotificationPauseWindow.active(selected, elsewhere.toInstant().toEpochMilli()))
    }
    @Test fun `past or current time is rejected while a later time today is accepted`() {
        assertNull(NotificationPauseWindow.selectedUntil("2026-10-15", "23:59", now))
        assertNull(NotificationPauseWindow.selectedUntil("2026-10-16", "08:59", now))
        assertNull(NotificationPauseWindow.selectedUntil("2026-10-16", "09:00", now))
        assertNotNull(NotificationPauseWindow.selectedUntil("2026-10-16", "09:01", now))
    }
    @Test fun `selection across midnight and year boundary remains valid`() {
        val late = ZonedDateTime.of(2026, 12, 31, 23, 59, 30, 0, now.zone)
        assertNotNull(NotificationPauseWindow.selectedUntil("2027-01-01", "00:00", late))
        assertNull(NotificationPauseWindow.selectedUntil("2026-12-31", "00:00", late))
    }
    @Test fun `invalid dates and times cannot create a pause`() {
        listOf("", "2026-02-29", "2026-11-31", "2026-13-01").forEach {
            assertNull(NotificationPauseWindow.selectedUntil(it, "12:00", now))
        }
        listOf("", "24:00", "12:60", "1:00", "text").forEach {
            assertNull(NotificationPauseWindow.selectedUntil("2026-10-17", it, now))
        }
    }
    @Test fun `pause expires at the deadline and extending it survives the old resume alarm`() {
        val first = NotificationPauseWindow.selectedUntil("2026-10-16", "10:00", now)!!
        val extended = NotificationPauseWindow.selectedUntil("2026-10-17", "10:00", now)!!
        assertTrue(NotificationPauseWindow.active(first, first - 1))
        assertFalse(NotificationPauseWindow.active(first, first))
        assertFalse(NotificationPauseWindow.active(first, first + 1))
        assertTrue(NotificationPauseWindow.active(extended, first))
        assertFalse(NotificationPauseWindow.active(0, now.toInstant().toEpochMilli()))
    }
}
