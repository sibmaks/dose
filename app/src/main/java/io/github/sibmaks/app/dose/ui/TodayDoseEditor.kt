package io.github.sibmaks.app.dose.ui

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
import java.time.*
import java.time.format.DateTimeFormatter

/** A one-day exception never edits the medication's recurrence rules. */
@Composable
internal fun TodayDoseEditor(item: DoseItem, onDismiss: () -> Unit, onSave: (Long?, String) -> Boolean) {
    val context = LocalContext.current
    var acceptance by rememberSaveable(item.id) { mutableStateOf(item.log != null) }
    var reminderTime by rememberSaveable(item.id) { mutableStateOf(item.scheduledTime) }
    var acceptedTime by rememberSaveable(item.id) {
        mutableStateOf(Instant.ofEpochMilli(item.log?.recordedAt ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var error by rememberSaveable { mutableStateOf(false) }
    val time = if (acceptance) acceptedTime else reminderTime
    val deadline = item.log?.scheduledAt ?: item.scheduledAt
    val onTimeValue = Instant.ofEpochMilli(deadline).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    SetupScreen(title = item.medication.name, onClose = onDismiss,
        primaryLabel = context.getString(R.string.save), onPrimary = { error = !onSave(if (acceptance) DoseLogic.scheduledMillis(LocalDate.parse(item.date), time) else null, time) },
        secondaryLabel = context.getString(R.string.back)) {
        Text(context.getString(R.string.today_only_hint))
        if (item.log == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!acceptance, { acceptance = false; error = false }, label = { Text(context.getString(R.string.reminder_time)) })
                FilterChip(acceptance, { acceptance = true; error = false }, label = { Text(context.getString(R.string.taken_at)) })
            }
        } else Text(context.getString(R.string.taken_at), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = {
            val parsed = LocalTime.parse(time)
            TimePickerDialog(context, { _, hour, minute ->
                val selected = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                if (acceptance) acceptedTime = selected else reminderTime = selected
                error = false
            }, parsed.hour, parsed.minute, true).show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(time, style = MaterialTheme.typography.headlineSmall) }
        if (error) Text(context.getString(if (acceptance) R.string.acceptance_time_error else R.string.today_time_error), color = MaterialTheme.colorScheme.error)
        if (deadline <= System.currentTimeMillis()) {
            TextButton(onClick = {
                acceptance = true
                acceptedTime = onTimeValue
                error = !onSave(deadline, onTimeValue)
            }) { Text(context.getString(R.string.on_time)) }
        }
    }
}
