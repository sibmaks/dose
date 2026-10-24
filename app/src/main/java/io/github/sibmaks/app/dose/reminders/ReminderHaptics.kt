package io.github.sibmaks.app.dose.reminders

import android.content.Context
import android.app.NotificationManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.*
import android.provider.Settings
import android.util.Log
import io.github.sibmaks.app.dose.data.ReminderVibrationState

object ReminderHaptics {
    // Android does not expose public constants for the per-usage intensity settings.
    private const val NOTIFICATION_INTENSITY = "notification_vibration_intensity"
    private const val ALARM_INTENSITY = "alarm_vibration_intensity"
    val observedSettings = listOf(Settings.System.VIBRATE_ON, NOTIFICATION_INTENSITY, ALARM_INTENSITY)

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun vibrationState(context: Context): ReminderVibrationState {
        fun setting(name: String): Int? = runCatching {
            Settings.System.getString(context.contentResolver, name)?.toIntOrNull()
        }.getOrNull()
        fun intensityEnabled(name: String): Boolean? = setting(name)?.takeIf { it in 0..3 }?.let { it != 0 }
        ReminderNotifications.createChannel(context)
        return ReminderVibrationState(
            hasVibrator = vibrator(context)?.hasVibrator() == true,
            // Android 13 made vibrate_on the master switch; older versions use a ringer bitmask.
            systemEnabled = if (Build.VERSION.SDK_INT >= 33) setting(Settings.System.VIBRATE_ON)?.let { it > 0 } else null,
            notificationEnabled = intensityEnabled(NOTIFICATION_INTENSITY),
            // Alarm intensity is an Android 13+ setting and may be unreadable on some devices.
            alarmEnabled = if (Build.VERSION.SDK_INT >= 33) intensityEnabled(ALARM_INTENSITY) else null,
            channelEnabled = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(ReminderReceiver.CHANNEL_ID)?.shouldVibrate(),
            silentMode = context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_SILENT,
        )
    }

    @Suppress("DEPRECATION")
    fun vibrate(context: Context) {
        val vibrator = vibrator(context)
        if (vibrator?.hasVibrator() != true) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        } catch (failure: SecurityException) {
            Log.w("DoseHaptics", "Vibration is unavailable; the pop-up remains visible", failure)
        }
    }
}
