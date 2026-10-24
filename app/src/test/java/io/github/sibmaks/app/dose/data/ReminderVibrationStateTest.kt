package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class ReminderVibrationStateTest {
    private val enabled = ReminderVibrationState(true, true, true, true, true)

    @Test fun `enabled vibration has no warning for either chosen style`() {
        assertNull(enabled.warning(feed = true, popups = true))
    }

    @Test fun `master vibration off warns even before a delivery style is selected`() {
        val off = enabled.copy(systemEnabled = false)
        assertEquals(ReminderVibrationWarning.DEVICE, off.warning(feed = false, popups = false))
        assertEquals(ReminderVibrationWarning.DEVICE, off.warning(feed = true, popups = true))
        assertFalse(off.onlyChannelDisabled(feed = true, popups = true))
    }

    @Test fun `silent mode suppresses feed vibration but not alarm usage popups`() {
        val silent = enabled.copy(silentMode = true)
        assertEquals(ReminderVibrationWarning.FEED, silent.warning(feed = true, popups = true))
        assertNull(silent.warning(feed = false, popups = true))
        assertFalse(silent.onlyChannelDisabled(feed = true, popups = true))
    }

    @Test fun `channel vibration off links to the channel without blaming enabled popup vibration`() {
        val channelOff = enabled.copy(channelEnabled = false)
        assertEquals(ReminderVibrationWarning.FEED, channelOff.warning(feed = true, popups = true))
        assertTrue(channelOff.onlyChannelDisabled(feed = true, popups = true))
        assertNull(channelOff.warning(feed = false, popups = true))
    }

    @Test fun `notification intensity off requires device settings even if channel also disables it`() {
        val off = enabled.copy(notificationEnabled = false, channelEnabled = false)
        assertEquals(ReminderVibrationWarning.FEED, off.warning(feed = true, popups = true))
        assertFalse(off.onlyChannelDisabled(feed = true, popups = true))
        assertNull(off.warning(feed = false, popups = true))
    }

    @Test fun `alarm intensity off affects only enabled popups`() {
        val off = enabled.copy(alarmEnabled = false)
        assertEquals(ReminderVibrationWarning.POPUP, off.warning(feed = true, popups = true))
        assertNull(off.warning(feed = true, popups = false))
    }

    @Test fun `independent feed and popup blockers produce a combined warning`() {
        val off = enabled.copy(alarmEnabled = false, channelEnabled = false)
        assertEquals(ReminderVibrationWarning.BOTH, off.warning(feed = true, popups = true))
        assertFalse(off.onlyChannelDisabled(feed = true, popups = true))
    }

    @Test fun `unreadable vendor settings do not falsely report disabled vibration`() {
        val unknown = ReminderVibrationState(hasVibrator = true)
        assertNull(unknown.warning(feed = true, popups = true))
        assertEquals(ReminderVibrationWarning.FEED, unknown.copy(channelEnabled = false).warning(feed = true, popups = true))
        assertTrue(unknown.copy(channelEnabled = false).onlyChannelDisabled(feed = true, popups = true))
    }

    @Test fun `missing vibrator reports unavailable without offering settings as a fix`() {
        val absent = enabled.copy(hasVibrator = false)
        assertEquals(ReminderVibrationWarning.UNAVAILABLE, absent.warning(feed = true, popups = true))
        assertFalse(absent.onlyChannelDisabled(feed = true, popups = true))
    }
}
