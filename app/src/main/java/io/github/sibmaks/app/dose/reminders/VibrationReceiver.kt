package io.github.sibmaks.app.dose.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Delivers one five-minute vibration repeat for a dose that still awaits an answer. */
class VibrationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val key = intent.getStringExtra(VibrationRepeat.KEY) ?: return
        VibrationRepeat.fire(context, key)
    }
}
