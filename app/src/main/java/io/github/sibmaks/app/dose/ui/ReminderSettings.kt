package io.github.sibmaks.app.dose.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.data.ReminderSetupState
import io.github.sibmaks.app.dose.reminders.ReminderAlerts
import io.github.sibmaks.app.dose.reminders.ReminderNotifications

@Composable
internal fun ReminderSettingsDialog(state: ReminderSetupState, onDismiss: () -> Unit, onRefresh: () -> Unit, onExactAlarms: () -> Unit) {
    val context = LocalContext.current
    var feed by rememberSaveable { mutableStateOf(state.feedEnabled) }
    var popups by rememberSaveable { mutableStateOf(state.popupsEnabled) }
    var accessStep by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(accessStep) { scroll.scrollTo(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    fun notificationSettings() {
        runCatching { ReminderNotifications.openSettings(context) }.onFailure {
            Toast.makeText(context, "Open Android Settings → Apps → Dose → Notifications", Toast.LENGTH_LONG).show()
        }
    }
    fun requestFeedAccess() {
        if (Build.VERSION.SDK_INT >= 33 && !ReminderNotifications.runtimePermissionGranted(context) && !ReminderAlerts.permissionRequested(context)) {
            ReminderAlerts.markPermissionRequested(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else notificationSettings()
    }
    fun openOverlayAccess() {
        runCatching { ReminderAlerts.openPermission(context) }.onFailure {
            Toast.makeText(context, "Open Android Settings → Special app access → Display over other apps → Dose", Toast.LENGTH_LONG).show()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (accessStep) "Finish reminder setup" else "How should Dose remind you?") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!accessStep) {
                Text("Choose one or both reminder styles. You can change these choices any time using the bell.", fontSize = 14.sp)
                ReminderChoice("Notification feed", "Keep reminders in your notification feed, with Taken, Snooze, and Skip actions.", feed) { feed = it }
                ReminderChoice("Pop-up over other apps", "Show a medication window above the app you are using.", popups) { popups = it }
                if (!feed && !popups) Text("Both reminder styles will be turned off. Your medication schedules stay saved.", fontSize = 13.sp, color = TextMuted)
                else Text("Next, allow the Android access needed for your choices.", fontSize = 13.sp, color = TextMuted)
            } else {
                Text("The bell turns purple when access for your chosen reminder styles is ready.", fontSize = 14.sp)
                if (feed) AccessStatus("Notification feed", state.feedAllowed)
                if (popups) AccessStatus("Display over other apps", state.overlayAllowed)
                AccessStatus("Exact alarms", state.exactAlarmsAllowed)
                when {
                    feed && !state.feedAllowed -> Text("Allow notifications for Dose and its Medication reminders channel. If you previously denied access, Android settings will open.", fontSize = 13.sp)
                    popups && !state.overlayAllowed -> Text("Enable Display over other apps for Dose in Android settings, then return here.", fontSize = 13.sp)
                    !state.exactAlarmsAllowed -> Text("Allow Alarms & reminders for Dose so reminders can arrive on time while the app is closed.", fontSize = 13.sp)
                    else -> Text("Your reminder setup is ready.", fontSize = 14.sp, color = Violet)
                }
                if (feed && state.feedAllowed) TextButton(onClick = ::notificationSettings) { Text("Sound & notification settings") }
                if (popups) {
                    OutlinedButton(enabled = state.overlayAllowed, onClick = {
                        if (!ReminderAlerts.preview(context)) Toast.makeText(context, "Android could not open the pop-up. Check display-over-apps access.", Toast.LENGTH_LONG).show()
                    }) { Text("Test pop-up") }
                    Text("Pop-ups close after 2 minutes without recording a dose${if (feed) "; feed notifications remain" else ""}. They do not unlock or wake the phone, and protected screens can hide them.", fontSize = 12.sp, color = TextMuted)
                    if (!feed) Text("With feed notifications off, there is no dose notification to return to after the pop-up closes. Android may still show a temporary notification while the pop-up is open.", fontSize = 12.sp, color = TextMuted)
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (!accessStep) {
                ReminderAlerts.saveChoices(context, feed, popups)
                onRefresh()
                if (!feed && !popups) onDismiss() else accessStep = true
            } else when {
                feed && !state.feedAllowed -> requestFeedAccess()
                popups && !state.overlayAllowed -> openOverlayAccess()
                !state.exactAlarmsAllowed -> onExactAlarms()
                else -> onDismiss()
            }
        }) { Text(if (!accessStep) { if (feed || popups) "Continue" else "Turn off reminders" }
            else when { feed && !state.feedAllowed -> "Allow notifications"; popups && !state.overlayAllowed -> "Allow pop-ups"; !state.exactAlarmsAllowed -> "Allow exact alarms"; else -> "Done" }) }
    }, dismissButton = {
        TextButton(onClick = { if (accessStep) accessStep = false else onDismiss() }) { Text(if (accessStep) "Back" else "Cancel") }
    })
}

@Composable
private fun ReminderChoice(title: String, explanation: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(explanation, color = TextMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccessStatus(title: String, allowed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(if (allowed) "Ready" else "Access needed", color = if (allowed) Violet else TextMuted, fontSize = 13.sp)
    }
}
