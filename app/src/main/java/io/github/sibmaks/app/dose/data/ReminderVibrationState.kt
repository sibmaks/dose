package io.github.sibmaks.app.dose.data

enum class ReminderVibrationWarning {
    UNAVAILABLE,
    DEVICE,
    FEED,
    POPUP,
    BOTH
}

/** Unknown platform/vendor settings stay unknown; touch and ringtone preferences are unrelated. */
data class ReminderVibrationState(
    val hasVibrator: Boolean,
    val systemEnabled: Boolean? = null,
    val notificationEnabled: Boolean? = null,
    val alarmEnabled: Boolean? = null,
    val channelEnabled: Boolean? = null,
    val silentMode: Boolean = false,
) {
    fun warning(
        feed: Boolean,
        popups: Boolean
    ): ReminderVibrationWarning? {
        if (!hasVibrator) return ReminderVibrationWarning.UNAVAILABLE
        if (systemEnabled == false) return ReminderVibrationWarning.DEVICE
        val feedOff = feed && (notificationEnabled == false || channelEnabled == false || silentMode)
        val popupOff = popups && alarmEnabled == false
        return when {
            feedOff && popupOff -> ReminderVibrationWarning.BOTH
            feedOff -> ReminderVibrationWarning.FEED
            popupOff -> ReminderVibrationWarning.POPUP
            else -> null
        }
    }

    fun onlyChannelDisabled(
        feed: Boolean,
        popups: Boolean
    ): Boolean =
        warning(feed, popups) == ReminderVibrationWarning.FEED && channelEnabled == false &&
                notificationEnabled != false && !silentMode
}
