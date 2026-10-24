package io.github.sibmaks.app.dose.reminders

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.ReminderEnableStep
import io.github.sibmaks.app.dose.data.ReminderSetupState
import io.github.sibmaks.app.dose.data.ReminderStyle
import java.time.LocalDate

/** A dose keeps its original schedule even when its alert has been snoozed. */
data class ReminderSlot(
    val medicationId: String,
    val date: String,
    val time: String,
    val scheduledAt: Long
) {
    val key: String get() = DoseLogic.slotKey(date, medicationId, time)

    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, medicationId)
        putExtra(ReminderReceiver.EXTRA_DATE, date)
        putExtra(ReminderReceiver.EXTRA_TIME, time)
        putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
    }

    companion object {
        fun from(intent: Intent): ReminderSlot? {
            val id =
                intent.getStringExtra(ReminderReceiver.EXTRA_MEDICATION_ID)
                    ?.takeIf { it.isNotBlank() } ?: return null
            val date =
                intent.getStringExtra(ReminderReceiver.EXTRA_DATE) ?: LocalDate.now()
                    .toString()
            val time = intent.getStringExtra(ReminderReceiver.EXTRA_TIME) ?: return null
            if (!DoseLogic.isDate(date) || !DoseLogic.isTime(time)) return null
            return ReminderSlot(
                id,
                date,
                time,
                intent.getLongExtra(
                    ReminderReceiver.EXTRA_SCHEDULED_AT,
                    DoseLogic.scheduledMillis(LocalDate.parse(date), time)
                )
            )
        }
    }
}

object ReminderAlerts {
    private fun prefs(context: Context) = context.getSharedPreferences("dose_alert_settings", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("overlay_enabled", false) &&
            permissionGranted(context) && ReminderScheduler.exactAlarmsAvailable(context)

    fun feedEnabled(context: Context): Boolean = prefs(context).getBoolean("feed_enabled", false) &&
            ReminderNotifications.allowed(context) && ReminderScheduler.exactAlarmsAvailable(context)

    fun permissionRequested(context: Context): Boolean = prefs(context).getBoolean("feed_permission_requested", false)
    fun markPermissionRequested(context: Context) {
        prefs(context).edit()
            .putBoolean("feed_permission_requested", true)
            .apply()
    }

    fun saveChoices(
        context: Context,
        feed: Boolean,
        popups: Boolean
    ) {
        val access = setupState(context)
        val enableFeed = feed && access.nextEnableStep(ReminderStyle.FEED) == ReminderEnableStep.ENABLE
        val enablePopups = popups && access.nextEnableStep(ReminderStyle.POPUP) == ReminderEnableStep.ENABLE
        prefs(context).edit()
            .putBoolean("feed_enabled", enableFeed)
            .putBoolean("overlay_enabled", enablePopups)
            .putBoolean("setup_reviewed", true)
            .apply()
        if (!enablePopups) context.stopService(Intent(context, ReminderOverlayService::class.java))
        if (!enableFeed) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.filter { it.notification.extras.containsKey(ReminderReceiver.EXTRA_MEDICATION_ID) }
                .forEach { manager.cancel(it.id) }
        }
    }

    fun setupOffered(context: Context): Boolean =
        prefs(context).getBoolean("setup_offered", prefs(context).getBoolean("setup_reviewed", false))

    fun markSetupOffered(context: Context) {
        prefs(context).edit()
            .putBoolean("setup_offered", true)
            .apply()
    }

    fun setupState(context: Context) = ReminderSetupState(
        reviewed = prefs(context).getBoolean("setup_reviewed", false),
        feedEnabled = prefs(context).getBoolean("feed_enabled", false),
        popupsEnabled = prefs(context).getBoolean("overlay_enabled", false),
        feedAllowed = ReminderNotifications.allowed(context),
        overlayAllowed = permissionGranted(context),
        exactAlarmsAllowed = ReminderScheduler.exactAlarmsAvailable(context),
    )

    fun permissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)
    fun show(
        context: Context,
        slot: ReminderSlot
    ) {
        if (NotificationPause.isPaused(context) || !enabled(context) || !permissionGranted(context)) return
        start(context, slot.putInto(Intent(context, ReminderOverlayService::class.java)))
    }

    private fun start(
        context: Context,
        intent: Intent
    ): Boolean = try {
        context.startForegroundService(intent)
        true
    } catch (failure: IllegalStateException) {
        // An inexact alarm may not qualify for Android's background-start exemption.
        Log.w("DoseAlerts", "Android blocked the pop-up service; the dose notification remains available", failure)
        false
    } catch (failure: SecurityException) {
        Log.w("DoseAlerts", "Pop-up permission is unavailable; the dose notification remains available", failure)
        false
    }
}
