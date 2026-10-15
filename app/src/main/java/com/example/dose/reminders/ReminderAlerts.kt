package com.example.dose.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.dose.data.DoseLogic
import java.time.LocalDate

/** A dose keeps its original schedule even when its alert has been snoozed. */
data class ReminderSlot(val medicationId: String, val date: String, val time: String, val scheduledAt: Long) {
    val key: String get() = DoseLogic.slotKey(date, medicationId, time)
    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, medicationId)
        putExtra(ReminderReceiver.EXTRA_DATE, date)
        putExtra(ReminderReceiver.EXTRA_TIME, time)
        putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, scheduledAt)
    }
    companion object {
        fun from(intent: Intent): ReminderSlot? {
            val id = intent.getStringExtra(ReminderReceiver.EXTRA_MEDICATION_ID)?.takeIf { it.isNotBlank() } ?: return null
            val date = intent.getStringExtra(ReminderReceiver.EXTRA_DATE) ?: LocalDate.now().toString()
            val time = intent.getStringExtra(ReminderReceiver.EXTRA_TIME) ?: return null
            if (!DoseLogic.isDate(date) || !DoseLogic.isTime(time)) return null
            return ReminderSlot(id, date, time, intent.getLongExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, DoseLogic.scheduledMillis(LocalDate.parse(date), time)))
        }
    }
}

object ReminderAlerts {
    private fun prefs(context: Context) = context.getSharedPreferences("dose_alert_settings", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("overlay_enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("overlay_enabled", enabled).apply()
        if (!enabled) context.stopService(Intent(context, ReminderOverlayService::class.java))
    }
    fun permissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)
    fun openPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    }
    fun show(context: Context, slot: ReminderSlot) {
        if (!enabled(context) || !permissionGranted(context)) return
        start(context, slot.putInto(Intent(context, ReminderOverlayService::class.java)))
    }
    fun preview(context: Context): Boolean {
        if (!permissionGranted(context)) return false
        return start(context, Intent(context, ReminderOverlayService::class.java).putExtra(ReminderOverlayService.EXTRA_PREVIEW, true))
    }
    private fun start(context: Context, intent: Intent): Boolean = try {
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
