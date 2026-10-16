package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class NotificationPauseWindowTest {
    private val now = 1_792_145_600_000L
    @Test fun `hours and days produce persistent absolute deadlines`() {
        assertEquals(now + 3 * 3_600_000L, NotificationPauseWindow.after("3", false, now))
        assertEquals(now + 2 * 86_400_000L, NotificationPauseWindow.after("2", true, now))
    }
    @Test fun `deadline remains active after restart and expires at selected instant`() {
        val stored = NotificationPauseWindow.after("1", false, now)!!
        assertTrue(NotificationPauseWindow.active(stored, now))
        assertTrue(NotificationPauseWindow.active(stored, stored - 1))
        assertFalse(NotificationPauseWindow.active(stored, stored))
        assertFalse(NotificationPauseWindow.active(stored, stored + 1))
        assertFalse(NotificationPauseWindow.active(0, now))
    }
    @Test fun `extending pause keeps old resume alarm from ending it`() {
        val old = now + 3_600_000
        val extended = now + 86_400_000
        assertTrue(NotificationPauseWindow.active(extended, old))
    }
    @Test fun `invalid zero negative and overflow durations fail`() {
        listOf("", "0", "-1", "1.5", "text", Long.MAX_VALUE.toString()).forEach {
            assertNull(NotificationPauseWindow.after(it, false, now))
            assertNull(NotificationPauseWindow.after(it, true, now))
        }
        assertNull(NotificationPauseWindow.after("1", false, Long.MAX_VALUE - 1000))
    }
}
