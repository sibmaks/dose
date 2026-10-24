package io.github.sibmaks.app.dose.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ReminderEnableStep
import io.github.sibmaks.app.dose.data.ReminderSetupState
import io.github.sibmaks.app.dose.data.ReminderStyle
import io.github.sibmaks.app.dose.reminders.ReminderAlerts
import io.github.sibmaks.app.dose.reminders.ReminderNotifications
import io.github.sibmaks.app.dose.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first

@Composable
internal fun ReminderSettingsScreen(
    state: ReminderSetupState,
    firstLaunch: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pendingStyle by rememberSaveable { mutableStateOf<ReminderStyle?>(null) }
    var requestedStep by rememberSaveable { mutableStateOf<ReminderEnableStep?>(null) }
    var returned by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        returned = true
        onRefresh()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        returned = true
        onRefresh()
    }

    fun launchSettings(
        intent: Intent,
        fallback: Int
    ) {
        runCatching { settingsLauncher.launch(intent) }.onFailure {
            pendingStyle = null
            requestedStep = null
            Toast.makeText(context, resources.getString(fallback), Toast.LENGTH_LONG)
                .show()
        }
    }

    // The switch is never optimistic: only actual Android access can complete an enable attempt.
    LaunchedEffect(returned, pendingStyle) {
        val style = pendingStyle ?: return@LaunchedEffect
        if (!returned) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        returned = false
        val current = ReminderAlerts.setupState(context)
        when (val step = current.nextEnableStep(style, requestedStep)) {
            ReminderEnableStep.ENABLE -> {
                ReminderAlerts.saveChoices(
                    context, current.feedChecked || style == ReminderStyle.FEED,
                    current.popupsChecked || style == ReminderStyle.POPUP
                )
                pendingStyle = null
                requestedStep = null
                ReminderScheduler.scheduleAll(context)
                onRefresh()
            }

            ReminderEnableStep.CANCEL -> {
                pendingStyle = null; requestedStep = null
            }

            else -> {
                requestedStep = step
                when (step) {
                    ReminderEnableStep.FEED_ACCESS -> {
                        if (Build.VERSION.SDK_INT >= 33 && !ReminderNotifications.runtimePermissionGranted(context) && !ReminderAlerts.permissionRequested(
                                context
                            )
                        ) {
                            ReminderAlerts.markPermissionRequested(context)
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else launchSettings(
                            ReminderNotifications.settingsIntent(context),
                            R.string.notification_settings_fallback
                        )
                    }

                    ReminderEnableStep.OVERLAY_ACCESS -> launchSettings(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        ), R.string.overlay_settings_fallback
                    )

                    ReminderEnableStep.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) launchSettings(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            "package:${context.packageName}".toUri()
                        ), R.string.exact_alarm_settings_fallback
                    )

                    else -> Unit
                }
            }
        }
    }

    fun choose(
        style: ReminderStyle,
        enabled: Boolean
    ) {
        if (enabled) {
            requestedStep = null
            pendingStyle = style
            returned = true
        } else {
            val current = ReminderAlerts.setupState(context)
            ReminderAlerts.saveChoices(
                context, current.feedChecked && style != ReminderStyle.FEED,
                current.popupsChecked && style != ReminderStyle.POPUP
            )
            onRefresh()
        }
    }

    @Composable
    fun accessHint(style: ReminderStyle): String? {
        val step = state.nextEnableStep(style)
        val resource = when (step) {
            ReminderEnableStep.FEED_ACCESS -> R.string.notification_access_hint
            ReminderEnableStep.OVERLAY_ACCESS -> R.string.overlay_access_hint
            ReminderEnableStep.EXACT_ALARMS -> R.string.exact_alarm_access_hint
            else -> return null
        }
        return stringResource(resource)
    }

    SetupScreen(
        title = stringResource(R.string.set_up_reminders), onClose = onDismiss,
        primaryLabel = if (firstLaunch && state.complete && pendingStyle == null) stringResource(R.string.ready) else null,
        onPrimary = onDismiss,
        secondaryLabel = stringResource(R.string.later), showSecondary = !state.complete
    ) {
        ReminderVibrationAlert(state.feedChecked, state.popupsChecked, ::launchSettings)
        ReminderChoice(
            stringResource(R.string.notification_feed), stringResource(R.string.notification_feed_hint),
            state.feedChecked, pendingStyle == null, accessHint(ReminderStyle.FEED)
        ) { choose(ReminderStyle.FEED, it) }
        ReminderChoice(
            stringResource(R.string.pop_up_over_other_apps), stringResource(R.string.overlay_hint),
            state.popupsChecked, pendingStyle == null, accessHint(ReminderStyle.POPUP)
        ) { choose(ReminderStyle.POPUP, it) }
        if (!state.feedChecked && !state.popupsChecked) Text(
            stringResource(R.string.reminders_disabled_hint),
            fontSize = 13.sp,
            color = TextMuted
        )
        if (state.feedChecked) TextButton(onClick = {
            launchSettings(ReminderNotifications.settingsIntent(context), R.string.notification_settings_fallback)
        }) { Text(stringResource(R.string.sound_notification_settings)) }
        if (state.popupsChecked && !state.feedChecked) Text(
            stringResource(R.string.popup_feed_disabled_hint),
            fontSize = 12.sp,
            color = TextMuted
        )
        if (!firstLaunch) {
            HorizontalDivider(color = PanelBorder)
            NotificationPauseSection(onRefresh)
        }
    }
}

@Composable
private fun ReminderChoice(
    title: String,
    explanation: String,
    checked: Boolean,
    enabled: Boolean,
    accessHint: String?,
    onChange: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (checked) AccentPanel else SoftPanel) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(explanation, color = TextMuted, fontSize = 12.sp)
                if (accessHint != null) Text(accessHint, color = Violet, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
