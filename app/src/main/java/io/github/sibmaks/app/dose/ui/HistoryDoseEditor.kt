package io.github.sibmaks.app.dose.ui

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
import io.github.sibmaks.app.dose.data.*
import io.github.sibmaks.app.dose.localization.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** History is editable even when the medication no longer exists or its schedule has changed. */
@Composable
internal fun HistoryDoseEditor(log: DoseLog, onDismiss: () -> Unit, onSave: (Long) -> Boolean, onDelete: () -> Boolean) {
    val context = LocalContext.current
    var at by rememberSaveable(log) { mutableLongStateOf(log.recordedAt) }
    var timeError by rememberSaveable(log) { mutableStateOf(false) }
    var changedError by rememberSaveable(log) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(log) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val selected = Instant.ofEpochMilli(at).atZone(zone)
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(AppLanguages.locale(context))
    val deadline = log.scheduledAt ?: runCatching { DoseLogic.scheduledMillis(LocalDate.parse(log.date), log.time) }.getOrNull()

    SetupScreen(title = log.medicationName, onClose = onDismiss,
        primaryLabel = context.getString(if (log.status == "taken") R.string.save else R.string.done),
        onPrimary = {
            if (log.status != "taken") onDismiss()
            else {
                timeError = at <= 0 || at > System.currentTimeMillis()
                if (!timeError) changedError = !onSave(at)
            }
        }, secondaryLabel = context.getString(R.string.back)) {
        Text(context.getString(R.string.dose_history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(log.dose, style = MaterialTheme.typography.titleMedium)
        StatusBadge(log.status)
        deadline?.let {
            Text(context.getString(R.string.reminder_time), color = TextMuted)
            Text(Instant.ofEpochMilli(it).atZone(zone).format(formatter))
        }
        HorizontalDivider(color = PanelBorder)
        if (log.status == "taken") {
            Text(context.getString(R.string.taken_at), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        at = LocalDate.of(year, month + 1, day).atTime(selected.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        timeError = false; changedError = false
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }, modifier = Modifier.weight(1.5f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(selected.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(AppLanguages.locale(context))), maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        at = selected.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                        timeError = false; changedError = false
                    }, selected.hour, selected.minute, true).show()
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(selected.format(DateTimeFormatter.ofPattern("HH:mm")), maxLines = 1, softWrap = false)
                }
            }
            if (timeError) Text(context.getString(R.string.history_time_error), color = MaterialTheme.colorScheme.error)
        } else {
            Text(context.getString(R.string.skipped_at), color = TextMuted)
            Text(selected.format(formatter))
        }
        if (changedError) Text(context.getString(R.string.history_changed_error), color = MaterialTheme.colorScheme.error)
        HorizontalDivider(color = PanelBorder)
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(context.getString(R.string.delete_history_entry))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(context.getString(R.string.delete_history_entry)) },
        text = { Text(context.getString(R.string.delete_history_hint)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; changedError = !onDelete() }) {
            Text(context.getString(R.string.delete), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(context.getString(R.string.cancel)) } })
}
