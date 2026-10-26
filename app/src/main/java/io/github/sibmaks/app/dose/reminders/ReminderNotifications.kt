package io.github.sibmaks.app.dose.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ReminderVibrationStyle
import io.github.sibmaks.app.dose.localization.AppLanguages

object ReminderNotifications {
    fun createChannel(sourceContext: Context) {
        val context = AppLanguages.wrap(sourceContext)
        val manager = context.getSystemService(NotificationManager::class.java)
        val style = ReminderAlerts.vibrationStyle(sourceContext)
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                context.getString(R.string.medication_reminders), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                if (style == ReminderVibrationStyle.DEFAULT) {
                    // Keep the system default buzz; a chosen preset overrides it below.
                    vibrationPattern = null
                } else {
                    enableVibration(true)
                    vibrationPattern = style.timings
                }
            })
    }

    fun runtimePermissionGranted(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun appAllowed(context: Context): Boolean = NotificationManagerCompat.from(context)
        .areNotificationsEnabled()

    fun allowed(context: Context): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ReminderReceiver.CHANNEL_ID)
        return runtimePermissionGranted(context) && appAllowed(context) && channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun settingsIntent(context: Context): Intent {
        createChannel(context)
        val intent = if (runtimePermissionGranted(context) && appAllowed(context)) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(
                Settings.EXTRA_CHANNEL_ID,
                ReminderReceiver.CHANNEL_ID
            )
        } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        return intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}
