package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.NotificationPauseWindow
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.reminders.NotificationPause
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationPauseSection(onRefresh: () -> Unit) {
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(NotificationPause.until(context)) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var amount by rememberSaveable { mutableStateOf("1") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.pause_hours, R.string.pause_days, R.string.until).forEachIndexed { index, label ->
            FilterChip(mode == index, { mode = index; error = false }, label = { Text(context.getString(label)) })
        }
    }
    if (mode != 2) {
        OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(8); error = false },
            label = { Text(context.getString(if (mode == 0) R.string.pause_hours else R.string.pause_days)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = error, supportingText = { if (error) Text(context.getString(R.string.pause_time_error)) }, modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedButton(onClick = {
            val selected = LocalDate.parse(date)
            DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString(); error = false },
                selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        }, modifier = Modifier.fillMaxWidth()) { Text(LocalDate.parse(date).format(dateFormat)) }
        OutlinedButton(onClick = {
            val selected = LocalTime.parse(time)
            TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm")); error = false },
                selected.hour, selected.minute, true).show()
        }, modifier = Modifier.fillMaxWidth()) { Text(time) }
        if (error) Text(context.getString(R.string.pause_time_error), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = {
        val now = System.currentTimeMillis()
        val chosen = if (mode == 2) LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else NotificationPauseWindow.after(amount, mode == 1, now)
        if (chosen == null || chosen <= now) error = true else {
            NotificationPause.set(context, chosen)
            until = chosen
            error = false
            onRefresh()
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.pause_notifications)) }
}
