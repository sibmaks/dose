package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.appearance.AppAppearance
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
                             onSave: (String) -> Boolean) {
    val context = LocalContext.current
    val medication = item.medication
    var reminderTime by rememberSaveable(item.id, item.scheduledTime) { mutableStateOf(item.scheduledTime) }
    var error by rememberSaveable(item.id) { mutableStateOf(false) }
    val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(AppLanguages.locale(context))
    fun formattedTime(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

    SetupScreen(title = medication.name, onClose = onDismiss,
        primaryLabel = context.getString(if (item.log == null) R.string.save else R.string.done),
        onPrimary = { if (item.log == null) error = !onSave(reminderTime) else onDismiss() },
        secondaryLabel = context.getString(R.string.back)) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SoftPanel),
            border = BorderStroke(1.dp, PanelBorder)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillAvatar(medication.color, medication.form, medication.localizedForm(context))
                    Column(Modifier.weight(1f)) {
                        Text(context.getString(R.string.dose_label), fontSize = 12.sp, color = TextMuted)
                        Text(listOf(item.amount, localizedUnit(context, medication.quantity)).filter { it.isNotBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.dose_status), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    StatusBadge(item.status)
                }
                DoseDetail(context.getString(R.string.medication_form), medication.localizedForm(context))
                if (item.log == null) DoseDetail(context.getString(R.string.reminder_time), formattedTime(item.scheduledAt))
                if (item.log == null) item.snoozedUntil?.let {
                    DoseDetail(context.getString(R.string.snoozed_until), formattedTime(it))
                }
                item.log?.let { log ->
                    if (log.dose != item.dose) DoseDetail(context.getString(R.string.recorded_dose), log.dose)
                }
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(context.getString(R.string.edit_medication), fontWeight = FontWeight.Bold)
        }
        if (item.log == null) {
            HorizontalDivider(color = PanelBorder)
            Text(context.getString(R.string.today_only_hint), fontSize = 13.sp, color = TextMuted)
            Text(context.getString(R.string.reminder_time), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = {
                val parsed = LocalTime.parse(reminderTime)
                TimePickerDialog(context, AppAppearance.dialogTheme(context), { _, hour, minute ->
                    reminderTime = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                    error = false
                }, parsed.hour, parsed.minute, true).show()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(reminderTime, style = MaterialTheme.typography.headlineSmall) }
            if (error) Text(context.getString(R.string.today_time_error), color = MaterialTheme.colorScheme.error)
        }
        if (medication.note.isNotBlank()) {
            HorizontalDivider(color = PanelBorder)
            DoseDetail(context.getString(R.string.instructions), medication.note)
        }
        HorizontalDivider(color = PanelBorder)
        Text(context.getString(R.string.due_dates_times), fontWeight = FontWeight.Bold)
        medication.schedules.forEach { rule -> Text(scheduleSummary(context, rule, medication.amount, medication.quantity), fontSize = 14.sp) }
    }
}

@Composable
private fun DoseDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, fontSize = 14.sp)
    }
}
