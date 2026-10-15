package io.github.sibmaks.app.dose.data

/** Readiness follows the delivery methods the user chose, including explicit opt-outs. */
data class ReminderSetupState(
    val reviewed: Boolean,
    val feedEnabled: Boolean,
    val popupsEnabled: Boolean,
    val feedAllowed: Boolean,
    val overlayAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    val anyEnabled: Boolean get() = feedEnabled || popupsEnabled
    val complete: Boolean get() = reviewed && anyEnabled && exactAlarmsAllowed &&
        (!feedEnabled || feedAllowed) && (!popupsEnabled || overlayAllowed)
}
