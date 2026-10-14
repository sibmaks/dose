package com.example.dose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dose.reminders.ReminderScheduler
import com.example.dose.ui.DoseApp
import com.example.dose.ui.DoseTheme

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
