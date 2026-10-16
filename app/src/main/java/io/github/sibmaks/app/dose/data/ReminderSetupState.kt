package io.github.sibmaks.app.dose.data

enum class ReminderStyle { FEED, POPUP }
enum class ReminderEnableStep { FEED_ACCESS, OVERLAY_ACCESS, EXACT_ALARMS, ENABLE, CANCEL }

/** Readiness follows the delivery methods the user chose, including explicit opt-outs. */
data class ReminderSetupState(
    val reviewed: Boolean,
    val feedEnabled: Boolean,
    val popupsEnabled: Boolean,
    val feedAllowed: Boolean,
    val overlayAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    val feedChecked: Boolean get() = feedEnabled && feedAllowed && exactAlarmsAllowed
    val popupsChecked: Boolean get() = popupsEnabled && overlayAllowed && exactAlarmsAllowed

    /** A denied request ends the attempt instead of reopening Android settings in a loop. */
    fun nextEnableStep(style: ReminderStyle, returnedFrom: ReminderEnableStep? = null): ReminderEnableStep {
        if (returnedFrom != null && !when (returnedFrom) {
                ReminderEnableStep.FEED_ACCESS -> feedAllowed
                ReminderEnableStep.OVERLAY_ACCESS -> overlayAllowed
                ReminderEnableStep.EXACT_ALARMS -> exactAlarmsAllowed
                else -> false
            }) return ReminderEnableStep.CANCEL
        return when {
            style == ReminderStyle.FEED && !feedAllowed -> ReminderEnableStep.FEED_ACCESS
            style == ReminderStyle.POPUP && !overlayAllowed -> ReminderEnableStep.OVERLAY_ACCESS
            !exactAlarmsAllowed -> ReminderEnableStep.EXACT_ALARMS
            else -> ReminderEnableStep.ENABLE
        }
    }

    val anyEnabled: Boolean get() = feedEnabled || popupsEnabled
    val complete: Boolean get() = reviewed && anyEnabled && exactAlarmsAllowed &&
        (!feedEnabled || feedAllowed) && (!popupsEnabled || overlayAllowed)
}
