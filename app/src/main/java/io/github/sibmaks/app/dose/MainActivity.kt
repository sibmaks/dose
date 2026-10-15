package io.github.sibmaks.app.dose

import android.os.Bundle
import android.content.Context
import io.github.sibmaks.app.dose.localization.AppLanguages
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.sibmaks.app.dose.reminders.ReminderScheduler
import io.github.sibmaks.app.dose.reminders.ReminderNotifications
import io.github.sibmaks.app.dose.ui.DoseApp
import io.github.sibmaks.app.dose.ui.DoseTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguages.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderNotifications.createChannel(this)
        ReminderScheduler.scheduleAll(this)
        setContent {
            DoseTheme { DoseApp() }
        }
    }
}
