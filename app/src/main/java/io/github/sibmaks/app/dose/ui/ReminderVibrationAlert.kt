package io.github.sibmaks.app.dose.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ReminderVibrationState
import io.github.sibmaks.app.dose.data.ReminderVibrationWarning
import io.github.sibmaks.app.dose.reminders.ReminderHaptics
import io.github.sibmaks.app.dose.reminders.ReminderNotifications

/** Platform vibration switches, refreshed live by settings changes, ringer mode and resumes. */
@Composable
internal fun rememberVibrationState(): ReminderVibrationState {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var vibration by remember(context) { mutableStateOf(ReminderHaptics.vibrationState(context)) }
    DisposableEffect(context, lifecycle) {
        fun refresh() {
            vibration = ReminderHaptics.vibrationState(context)
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }
        // Vendor settings can be absent/protected. Returning from Settings always rechecks too.
        ReminderHaptics.observedSettings.forEach { key ->
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(key),
                    false,
                    observer
                )
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(lifecycleObserver)
        refresh()
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return vibration
}

@Composable
internal fun ReminderVibrationAlert(
    feed: Boolean,
    popups: Boolean,
    vibration: ReminderVibrationState,
    onSettings: (Intent, Int) -> Unit
) {
    val context = LocalContext.current
    val warning = vibration.warning(feed, popups) ?: return
    val unavailable = warning == ReminderVibrationWarning.UNAVAILABLE
    val channelSettings = vibration.onlyChannelDisabled(feed, popups)
    val message = when (warning) {
        ReminderVibrationWarning.UNAVAILABLE -> R.string.vibration_unavailable_hint
        ReminderVibrationWarning.FEED -> R.string.feed_vibration_disabled_hint
        ReminderVibrationWarning.POPUP -> R.string.popup_vibration_disabled_hint
        else -> R.string.vibration_disabled_hint
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Vibration, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (unavailable) R.string.vibration_unavailable_title else R.string.vibration_off),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                )
                Text(
                    stringResource(message),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (!unavailable) TextButton(onClick = {
                    onSettings(
                        if (channelSettings) ReminderNotifications.settingsIntent(context) else Intent(Settings.ACTION_SOUND_SETTINGS),
                        if (channelSettings) R.string.notification_settings_fallback else R.string.vibration_settings_fallback
                    )
                }) {
                    Text(stringResource(if (channelSettings) R.string.sound_notification_settings else R.string.sound_vibration_settings))
                }
            }
        }
    }
}
