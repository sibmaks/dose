package io.github.sibmaks.app.dose.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationRepeatTest {
    private val alive = CycleAction.VIBRATE

    private fun decide(
        isToday: Boolean = true,
        paused: Boolean = false,
        alertsActive: Boolean = true,
        resolved: Boolean = false,
        snoozed: Boolean = false,
        rescheduled: Boolean = false,
        shared: Boolean = false
    ) = decideCycle(isToday, paused, alertsActive, resolved, snoozed, rescheduled, shared)

    @Test fun `a delivered dose keeps buzzing every repeat until an answer`() {
        assertEquals(alive, decide())
    }

    @Test fun `accepting or skipping the dose stops the cycle`() {
        assertEquals(CycleAction.STOP, decide(resolved = true))
    }

    @Test fun `an active snooze stops the cycle until the snooze fires a fresh one`() {
        assertEquals(CycleAction.STOP, decide(snoozed = true))
    }

    @Test fun `paused notifications hold the buzz but keep the cycle alive`() {
        assertEquals(CycleAction.QUIET, decide(paused = true))
    }

    @Test fun `a same-instant sibling holds quietly while the group anchor buzzes`() {
        assertEquals(CycleAction.QUIET, decide(shared = true))
        assertEquals(alive, decide())
    }

    @Test fun `a hard stop wins over a shared sibling's silent hold`() {
        assertEquals(CycleAction.STOP, decide(shared = true, resolved = true))
        assertEquals(CycleAction.STOP, decide(shared = true, snoozed = true))
    }

    @Test fun `a rescheduled slot stops instead of buzzing for a dose that moved`() {
        assertEquals(CycleAction.STOP, decide(rescheduled = true))
    }

    @Test fun `yesterday's unresolved dose stops after the rollover`() {
        assertEquals(CycleAction.STOP, decide(isToday = false))
    }

    @Test fun `disabled reminders stop the cycle entirely`() {
        assertEquals(CycleAction.STOP, decide(alertsActive = false))
    }

    @Test fun `resolution wins over a pause and over snoozing`() {
        assertEquals(CycleAction.STOP, decide(paused = true, resolved = true))
        assertEquals(CycleAction.STOP, decide(snoozed = true, paused = true))
    }
}
