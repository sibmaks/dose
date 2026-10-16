package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class ReminderEnableFlowTest {
    private val noAccess = ReminderSetupState(true, true, true, false, false, false)

    @Test fun `feed stays unchecked through notification and exact alarm requests`() {
        assertFalse(noAccess.feedChecked)
        assertEquals(ReminderEnableStep.FEED_ACCESS, noAccess.nextEnableStep(ReminderStyle.FEED))
        val feedAccess = noAccess.copy(feedAllowed = true)
        assertFalse(feedAccess.feedChecked)
        assertEquals(ReminderEnableStep.EXACT_ALARMS, feedAccess.nextEnableStep(ReminderStyle.FEED, ReminderEnableStep.FEED_ACCESS))
        val ready = feedAccess.copy(exactAlarmsAllowed = true)
        assertTrue(ready.feedChecked)
        assertEquals(ReminderEnableStep.ENABLE, ready.nextEnableStep(ReminderStyle.FEED, ReminderEnableStep.EXACT_ALARMS))
    }
    @Test fun `overlay enablement requires overlay and exact alarms but not feed access`() {
        assertEquals(ReminderEnableStep.OVERLAY_ACCESS, noAccess.nextEnableStep(ReminderStyle.POPUP))
        val overlayAccess = noAccess.copy(overlayAllowed = true)
        assertFalse(overlayAccess.popupsChecked)
        assertEquals(ReminderEnableStep.EXACT_ALARMS, overlayAccess.nextEnableStep(ReminderStyle.POPUP, ReminderEnableStep.OVERLAY_ACCESS))
        val ready = overlayAccess.copy(exactAlarmsAllowed = true)
        assertTrue(ready.popupsChecked)
        assertFalse(ready.feedChecked)
        assertEquals(ReminderEnableStep.ENABLE, ready.nextEnableStep(ReminderStyle.POPUP, ReminderEnableStep.EXACT_ALARMS))
    }
    @Test fun `denied notification access cancels without looping or requesting unrelated permissions`() {
        assertEquals(ReminderEnableStep.CANCEL, noAccess.nextEnableStep(ReminderStyle.FEED, ReminderEnableStep.FEED_ACCESS))
        assertFalse(noAccess.feedChecked)
    }
    @Test fun `denied overlay access leaves popup off`() {
        val exactOnly = noAccess.copy(exactAlarmsAllowed = true)
        assertEquals(ReminderEnableStep.CANCEL, exactOnly.nextEnableStep(ReminderStyle.POPUP, ReminderEnableStep.OVERLAY_ACCESS))
        assertFalse(exactOnly.popupsChecked)
    }
    @Test fun `returning without granting exact alarms cancels either enable attempt`() {
        val stylesAllowed = noAccess.copy(feedAllowed = true, overlayAllowed = true)
        ReminderStyle.entries.forEach { style ->
            assertEquals(ReminderEnableStep.CANCEL, stylesAllowed.nextEnableStep(style, ReminderEnableStep.EXACT_ALARMS))
        }
        assertFalse(stylesAllowed.feedChecked)
        assertFalse(stylesAllowed.popupsChecked)
    }
    @Test fun `permissions alone do not switch on a style the user turned off`() {
        val off = noAccess.copy(feedEnabled = false, popupsEnabled = false, feedAllowed = true, overlayAllowed = true, exactAlarmsAllowed = true)
        assertFalse(off.feedChecked)
        assertFalse(off.popupsChecked)
        ReminderStyle.entries.forEach { assertEquals(ReminderEnableStep.ENABLE, off.nextEnableStep(it)) }
    }
    @Test fun `all permission combinations permit enablement only when every required access is present`() {
        for (mask in 0..7) {
            val state = noAccess.copy(feedAllowed = mask and 1 != 0, overlayAllowed = mask and 2 != 0, exactAlarmsAllowed = mask and 4 != 0)
            for (style in ReminderStyle.entries) {
                if (state.nextEnableStep(style) == ReminderEnableStep.ENABLE) {
                    assertTrue(state.exactAlarmsAllowed)
                    assertTrue(if (style == ReminderStyle.FEED) state.feedAllowed else state.overlayAllowed)
                }
            }
        }
    }
}
