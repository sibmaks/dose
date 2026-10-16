package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.appearance.AppAppearance
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.NotificationPauseWindow
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.reminders.NotificationPause
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun NotificationPauseSection(onRefresh: () -> Unit) {
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(NotificationPause.until(context)) }
    val initialEnd = remember {
        if (until > 0) Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()) else ZonedDateTime.now().plusHours(1)
    }
    var date by rememberSaveable { mutableStateOf(initialEnd.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initialEnd.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var error by rememberSaveable { mutableStateOf(false) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(AppLanguages.locale(context))
    val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(AppLanguages.locale(context))
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val current = NotificationPause.until(context)
            if (until != current) { until = current; onRefresh() }
        }
    }

    Text(context.getString(R.string.pause_notifications), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (until > 0) {
        Text(context.getString(R.string.notifications_paused_until, Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).format(dateTimeFormat)), color = Violet)
        OutlinedButton(onClick = { NotificationPause.set(context, 0); until = 0; onRefresh() }) { Text(context.getString(R.string.resume_notifications)) }
    }
    Text(context.getString(R.string.until), style = MaterialTheme.typography.labelLarge)
    OutlinedButton(onClick = {
        val selected = LocalDate.parse(date)
        DatePickerDialog(context, AppAppearance.dialogTheme(context), { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString(); error = false },
            selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }, modifier = Modifier.fillMaxWidth()) { Text(LocalDate.parse(date).format(dateFormat)) }
    OutlinedButton(onClick = {
        val selected = LocalTime.parse(time)
        TimePickerDialog(context, AppAppearance.dialogTheme(context), { _, hour, minute -> time = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm")); error = false },
            selected.hour, selected.minute, true).show()
    }, modifier = Modifier.fillMaxWidth()) { Text(time) }
    if (error) Text(context.getString(R.string.pause_time_error), color = MaterialTheme.colorScheme.error)
    Button(onClick = {
        val chosen = NotificationPauseWindow.selectedUntil(date, time, ZonedDateTime.now())
        if (chosen == null) error = true else {
            NotificationPause.set(context, chosen)
            until = chosen
            error = false
            onRefresh()
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.pause_notifications)) }
}
