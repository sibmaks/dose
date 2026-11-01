package io.github.sibmaks.app.dose

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import io.github.sibmaks.app.dose.reminders.PendingReminderPopups
import io.github.sibmaks.app.dose.widget.DoseWidget

class DoseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                PendingReminderPopups.restore(context, immediately = true)
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DoseWidget.updateAll(this)
    }
}
