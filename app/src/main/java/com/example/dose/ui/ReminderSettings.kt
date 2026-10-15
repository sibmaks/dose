package com.example.dose.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dose.reminders.ReminderAlerts
import com.example.dose.reminders.ReminderReceiver
import com.example.dose.reminders.ReminderScheduler

@Composable
internal fun ReminderSettingsDialog(revision: Int, onDismiss: () -> Unit, onNotifications: () -> Unit, onExactAlarms: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderAlerts.enabled(context)) }
    val granted = remember(revision) { ReminderAlerts.permissionGranted(context) }
    val exact = remember(revision) { ReminderScheduler.exactAlarmsAvailable(context) }
    fun openPermission() {
        runCatching { ReminderAlerts.openPermission(context) }.onFailure {
            Toast.makeText(context, "Open Android Settings → Special app access → Display over other apps → Dose", Toast.LENGTH_LONG).show()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reminder alerts") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pop-up over other apps", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { value ->
                    enabled = value
                    ReminderAlerts.setEnabled(context, value)
                    if (value && !granted) openPermission()
                })
            }
            Text("At reminder time, show a medication window with Taken, Snooze, and Skip. It closes after 2 minutes if you do nothing; the notification remains.", fontSize = 13.sp)
            Text(when {
                !enabled -> "Pop-ups are off."
                !granted -> "Allow Display over other apps in Android Settings to enable pop-ups."
                !exact -> "Allow exact alarms so Android can open pop-ups from scheduled reminders while Dose is closed."
                else -> "Pop-ups are enabled."
            }, fontSize = 13.sp, color = Violet)
            if (enabled && !granted) OutlinedButton(onClick = ::openPermission) { Text("Allow display over other apps") }
            if (!exact) OutlinedButton(onClick = onExactAlarms) { Text("Allow exact alarms") }
            OutlinedButton(onClick = {
                if (!ReminderAlerts.preview(context)) Toast.makeText(context, "Android could not open the pop-up. Check display-over-apps access.", Toast.LENGTH_LONG).show()
            }, enabled = granted) { Text("Test pop-up") }
            Text("Pop-ups do not unlock or wake your phone. Protected screens, including some banking apps and system settings, can hide them. Use notifications on the lock screen.", fontSize = 12.sp, color = TextMuted)
            HorizontalDivider(color = PanelBorder)
            Text("Notification visibility", fontSize = 15.sp)
            Text("In Android notification settings, enable sound and Pop on screen for Medication reminders if your phone offers it.", fontSize = 13.sp)
            TextButton(onClick = onNotifications) { Text("Notification permission") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, ReminderReceiver.CHANNEL_ID)
                }) }.onFailure { onNotifications() }
            }) { Text("Reminder notification settings") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
