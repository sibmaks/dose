package io.github.sibmaks.app.dose.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Day rollover: settles awaiting entries as missed; scheduleAll also schedules the next rollover. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        ReminderScheduler.scheduleAll(context)
    }

    companion object {
        const val ACTION_MIDNIGHT = "io.github.sibmaks.app.dose.MIDNIGHT"
    }
}
