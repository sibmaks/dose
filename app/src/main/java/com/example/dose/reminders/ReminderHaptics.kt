package com.example.dose.reminders

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object ReminderHaptics {
    @Suppress("DEPRECATION")
    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() != true) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            } else {
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
        } catch (failure: SecurityException) {
            Log.w("DoseHaptics", "Vibration is unavailable; the pop-up remains visible", failure)
        }
    }
}
