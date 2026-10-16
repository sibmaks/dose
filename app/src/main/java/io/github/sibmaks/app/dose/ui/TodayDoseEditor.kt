package io.github.sibmaks.app.dose.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.*
import io.github.sibmaks.app.dose.localization.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A one-day exception never edits the medication's recurrence rules. */
@Composable
internal fun TodayDoseEditor(item: DoseItem, onDismiss: () -> Unit, onEdit: () -> Unit,
                             onSave: (Long?, String) -> Boolean) {
    val context = LocalContext.current
    val medication = item.medication
    var acceptance by rememberSaveable(item.id, item.log != null) { mutableStateOf(item.log != null) }
    var reminderTime by rememberSaveable(item.id, item.scheduledTime) { mutableStateOf(item.scheduledTime) }
    var acceptedTime by rememberSaveable(item.id, item.log?.recordedAt) {
        mutableStateOf(Instant.ofEpochMilli(item.log?.recordedAt ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var error by rememberSaveable(item.id) { mutableStateOf(false) }
    val time = if (acceptance) acceptedTime else reminderTime
    val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(AppLanguages.locale(context))
    fun formattedTime(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

    SetupScreen(title = medication.name, onClose = onDismiss,
        primaryLabel = context.getString(R.string.save), onPrimary = { error = !onSave(if (acceptance) DoseLogic.scheduledMillis(LocalDate.parse(item.date), time) else null, time) },
        secondaryLabel = context.getString(R.string.back)) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF8F6FF)),
            border = BorderStroke(1.dp, PanelBorder)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillAvatar(medication.color, medication.form, medication.localizedForm(context))
                    Column(Modifier.weight(1f)) {
                        Text(context.getString(R.string.dose_label), fontSize = 12.sp, color = TextMuted)
                        Text(listOf(medication.amount, localizedUnit(context, medication.quantity)).filter { it.isNotBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.dose_status), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    StatusBadge(item.status)
                }
                DoseDetail(context.getString(R.string.medication_form), medication.localizedForm(context))
                DoseDetail(context.getString(R.string.reminder_time), formattedTime(item.log?.scheduledAt ?: item.scheduledAt))
                if (item.log == null) item.snoozedUntil?.let {
                    DoseDetail(context.getString(R.string.snoozed_until), formattedTime(it))
                }
                item.log?.let { log ->
                    DoseDetail(context.getString(if (log.status == "taken") R.string.taken_at else R.string.skipped_at), formattedTime(log.recordedAt))
                    if (log.dose != medication.dose) DoseDetail(context.getString(R.string.recorded_dose), log.dose)
                }
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(context.getString(R.string.edit_medication), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = PanelBorder)
        Text(context.getString(R.string.today_only_hint), fontSize = 13.sp, color = TextMuted)
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
        if (medication.note.isNotBlank()) {
            HorizontalDivider(color = PanelBorder)
            DoseDetail(context.getString(R.string.instructions), medication.note)
        }
        HorizontalDivider(color = PanelBorder)
        Text(context.getString(R.string.due_dates_times), fontWeight = FontWeight.Bold)
        medication.schedules.forEach { rule -> Text(scheduleSummary(context, rule), fontSize = 14.sp) }
    }
}

@Composable
private fun DoseDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, fontSize = 14.sp)
    }
}
