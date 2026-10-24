package io.github.sibmaks.app.dose.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rebuilds Android alarms after a reboot, clock/timezone update, or permission grant. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != null) ReminderScheduler.scheduleAll(context)
    }
}
