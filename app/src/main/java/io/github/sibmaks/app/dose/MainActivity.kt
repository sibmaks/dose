package io.github.sibmaks.app.dose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.sibmaks.app.dose.reminders.ReminderScheduler
import io.github.sibmaks.app.dose.ui.DoseApp
import io.github.sibmaks.app.dose.ui.DoseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.scheduleAll(this)
        setContent {
            DoseTheme { DoseApp() }
        }
    }
}
