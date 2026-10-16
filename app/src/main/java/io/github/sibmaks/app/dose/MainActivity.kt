package io.github.sibmaks.app.dose

import io.github.sibmaks.app.dose.appearance.AppAppearance
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import io.github.sibmaks.app.dose.widget.DoseWidget
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
    private var todayRequests by mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(DoseWidget.OPEN_TODAY, false)) {
            todayRequests++
            intent.removeExtra(DoseWidget.OPEN_TODAY)
        }
    }

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguages.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppAppearance.activityTheme(this))
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(DoseWidget.OPEN_TODAY, false)) {
            todayRequests++
            intent.removeExtra(DoseWidget.OPEN_TODAY)
        }
        enableEdgeToEdge()
        ReminderNotifications.createChannel(this)
        ReminderScheduler.scheduleAll(this)
        setContent {
            DoseTheme { DoseApp(todayRequests) }
        }
    }
}
