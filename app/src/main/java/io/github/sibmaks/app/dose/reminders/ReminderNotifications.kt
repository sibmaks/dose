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

object ReminderNotifications {
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ReminderReceiver.CHANNEL_ID,
            "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders for medication schedules you added"
        })
    }

    fun runtimePermissionGranted(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun appAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun allowed(context: Context): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderReceiver.CHANNEL_ID)
        return runtimePermissionGranted(context) && appAllowed(context) && channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun openSettings(context: Context) {
        createChannel(context)
        val intent = if (runtimePermissionGranted(context) && appAllowed(context)) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderReceiver.CHANNEL_ID)
        } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
