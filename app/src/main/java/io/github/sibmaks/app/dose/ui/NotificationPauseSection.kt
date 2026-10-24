package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.NotificationPauseWindow
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.reminders.NotificationPause
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun NotificationPauseSection(onRefresh: () -> Unit) {
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(NotificationPause.until(context)) }
    val initialEnd = remember {
        if (until > 0) Instant.ofEpochMilli(until)
            .atZone(ZoneId.systemDefault()) else ZonedDateTime.now()
            .plusHours(1)
    }
    var date by rememberSaveable {
        mutableStateOf(
            initialEnd.toLocalDate()
                .toString()
        )
    }
    var time by rememberSaveable { mutableStateOf(initialEnd.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var error by rememberSaveable { mutableStateOf(false) }
    val dateFormat =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(AppLanguages.locale(context))
    val dateTimeFormat =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(AppLanguages.locale(context))
    LaunchedEffect(Unit) {
        while (true) {
            delay(30.seconds)
            val current = NotificationPause.until(context)
            if (until != current) {
                until = current; onRefresh()
            }
        }
    }

    Text(
        stringResource(R.string.pause_notifications),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    if (until > 0) {
        Text(
            stringResource(
                R.string.notifications_paused_until,
                Instant.ofEpochMilli(until)
                    .atZone(ZoneId.systemDefault())
                    .format(dateTimeFormat)
            ), color = Violet
        )
        OutlinedButton(onClick = {
            NotificationPause.set(context, 0); until =
            0; onRefresh()
        }) { Text(stringResource(R.string.resume_notifications)) }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.until), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        OutlinedButton(onClick = {
            val selected = LocalDate.parse(date)
            DatePickerDialog(
                context,
                AppAppearance.dialogTheme(context),
                { _, year, month, day ->
                    date =
                        LocalDate.of(year, month + 1, day)
                            .toString(); error = false
                },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth
            ).apply {
                datePicker.minDate =
                    LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
            }
                .show()
        }, modifier = Modifier.weight(1f)) {
            Text(
                LocalDate.parse(date)
                    .format(dateFormat),
                maxLines = 1,
            )
        }
        OutlinedButton(onClick = {
            val selected = LocalTime.parse(time)
            TimePickerDialog(
                context,
                AppAppearance.dialogTheme(context),
                { _, hour, minute ->
                    time =
                        LocalTime.of(hour, minute)
                            .format(DateTimeFormatter.ofPattern("HH:mm")); error =
                    false
                },
                selected.hour,
                selected.minute,
                true
            ).show()
        }, modifier = Modifier.weight(1f)) { Text(time) }
    }
    if (error) Text(stringResource(R.string.pause_time_error), color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        val chosen = NotificationPauseWindow.selectedUntil(date, time, ZonedDateTime.now())
        if (chosen == null) error = true else {
            NotificationPause.set(context, chosen)
            until = chosen
            error = false
            onRefresh()
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pause_notifications)) }
}
