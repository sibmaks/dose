package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class ReminderSetupStateTest {
    private val both = ReminderSetupState(reviewed = true, feedEnabled = true, popupsEnabled = true,
        feedAllowed = true, overlayAllowed = true, exactAlarmsAllowed = true)

    @Test fun bothStylesNeedAllTheirPermissions() {
        assertTrue(both.complete)
        assertFalse(both.copy(feedAllowed = false).complete)
        assertFalse(both.copy(overlayAllowed = false).complete)
        assertFalse(both.copy(exactAlarmsAllowed = false).complete)
    }
    @Test fun firstVisitAsksUserToReviewChoicesEvenWithExistingPermissions() {
        assertFalse(both.copy(reviewed = false).complete)
    }
    @Test fun feedOnlyIsCompleteWithoutOverlayPermission() {
        val feedOnly = both.copy(popupsEnabled = false, overlayAllowed = false)
        assertTrue(feedOnly.complete)
        assertFalse(feedOnly.copy(feedAllowed = false).complete)
    }
    @Test fun popupOnlyDoesNotRequireUnchosenFeedAccess() {
        val popupOnly = both.copy(feedEnabled = false, feedAllowed = false)
        assertTrue(popupOnly.complete)
        assertFalse(popupOnly.copy(overlayAllowed = false).complete)
    }
    @Test fun turningBothStylesOffNeverShowsActiveBell() {
        val off = both.copy(feedEnabled = false, popupsEnabled = false)
        assertFalse(off.anyEnabled)
        assertFalse(off.complete)
    }
    @Test fun revokedExactAlarmsAffectEitherChosenStyle() {
        assertFalse(both.copy(feedEnabled = false, exactAlarmsAllowed = false).complete)
        assertFalse(both.copy(popupsEnabled = false, exactAlarmsAllowed = false).complete)
    }
    @Test fun disabledMedicationChannelCountsAsIncompleteFeedAccess() {
        assertFalse(both.copy(feedAllowed = false, popupsEnabled = false).complete)
    }
}
