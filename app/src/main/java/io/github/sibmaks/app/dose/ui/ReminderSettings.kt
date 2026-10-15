package io.github.sibmaks.app.dose.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ReminderSetupState
import io.github.sibmaks.app.dose.reminders.ReminderAlerts
import io.github.sibmaks.app.dose.reminders.ReminderNotifications

@Composable
internal fun ReminderSettingsScreen(state: ReminderSetupState, onDismiss: () -> Unit, onRefresh: () -> Unit, onExactAlarms: () -> Unit) {
    val context = LocalContext.current
    var feed by rememberSaveable { mutableStateOf(state.feedEnabled) }
    var popups by rememberSaveable { mutableStateOf(if (state.reviewed) state.popupsEnabled else true) }
    var accessStep by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    fun notificationSettings() {
        runCatching { ReminderNotifications.openSettings(context) }.onFailure {
            Toast.makeText(context, context.getString(R.string.notification_settings_fallback), Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, context.getString(R.string.overlay_settings_fallback), Toast.LENGTH_LONG).show()
        }
    }
    val primary = if (!accessStep) {
        if (feed || popups) R.string.continue_action else R.string.turn_off_reminders
    } else when {
        feed && !state.feedAllowed -> R.string.allow_notifications
        popups && !state.overlayAllowed -> R.string.allow_pop_ups
        !state.exactAlarmsAllowed -> R.string.allow_exact_alarms
        else -> R.string.done
    }
    SetupScreen(title = context.getString(if (accessStep) R.string.finish_reminder_setup else R.string.set_up_reminders),
        onClose = onDismiss, primaryLabel = context.getString(primary), onPrimary = {
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
        }, secondaryLabel = context.getString(if (accessStep) R.string.back else R.string.later),
        onSecondary = { if (accessStep) accessStep = false else onDismiss() },
        onBack = { if (accessStep) accessStep = false else onDismiss() }, pageKey = accessStep) {
        if (!accessStep) {
            Text(context.getString(R.string.reminder_permission_intro), fontSize = 14.sp)
            ReminderChoice(context.getString(R.string.notification_feed), context.getString(R.string.notification_feed_hint), feed) { feed = it }
            ReminderChoice(context.getString(R.string.pop_up_over_other_apps), context.getString(R.string.overlay_hint), popups) { popups = it }
            if (!feed && !popups) Text(context.getString(R.string.reminders_disabled_hint), fontSize = 13.sp, color = TextMuted)
            else Text(context.getString(R.string.reminder_access_hint), fontSize = 13.sp, color = TextMuted)
        } else {
            Text(context.getString(R.string.reminder_ready_hint), fontSize = 14.sp)
            if (feed) AccessStatus(context.getString(R.string.notification_feed), state.feedAllowed)
            if (popups) AccessStatus(context.getString(R.string.display_over_other_apps), state.overlayAllowed)
            AccessStatus(context.getString(R.string.exact_alarms), state.exactAlarmsAllowed)
            when {
                feed && !state.feedAllowed -> Text(context.getString(R.string.notification_access_hint), fontSize = 13.sp)
                popups && !state.overlayAllowed -> Text(context.getString(R.string.overlay_access_hint), fontSize = 13.sp)
                !state.exactAlarmsAllowed -> Text(context.getString(R.string.exact_alarm_access_hint), fontSize = 13.sp)
                else -> Text(context.getString(R.string.your_reminder_setup_is_ready), fontSize = 14.sp, color = Violet)
            }
            if (feed && state.feedAllowed) TextButton(onClick = ::notificationSettings) { Text(context.getString(R.string.sound_notification_settings)) }
            if (popups) {
                OutlinedButton(enabled = state.overlayAllowed, onClick = {
                    if (!ReminderAlerts.preview(context)) Toast.makeText(context, context.getString(R.string.overlay_error), Toast.LENGTH_LONG).show()
                }) { Text(context.getString(R.string.test_pop_up)) }
                Text(context.getString(R.string.popup_limits), fontSize = 12.sp, color = TextMuted)
                if (!feed) Text(context.getString(R.string.popup_feed_disabled_hint), fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun ReminderChoice(title: String, explanation: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (checked) Color(0xFFF0ECFF) else Color(0xFFF8F9FD)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(explanation, color = TextMuted, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun AccessStatus(title: String, allowed: Boolean) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF8F9FD)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(context.getString(if (allowed) R.string.ready else R.string.access_needed), color = if (allowed) Violet else TextMuted, fontSize = 13.sp)
        }
    }
}
