package io.github.sibmaks.app.dose.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.DoseItem
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.localization.localizedForm
import io.github.sibmaks.app.dose.localization.localizedUnit
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A one-day exception never edits the medication's recurrence rules. */
@Composable
internal fun TodayDoseEditor(
    item: DoseItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (String) -> Boolean
) {
    val context = LocalContext.current
    val medication = item.medication
    var reminderTime by rememberSaveable(item.id, item.scheduledTime) { mutableStateOf(item.scheduledTime) }
    var error by rememberSaveable(item.id) { mutableStateOf(false) }
    val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(AppLanguages.locale(context))

    fun formattedTime(at: Long) = Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormat)

    SetupScreen(
        title = medication.name, onClose = onDismiss,
        primaryLabel = stringResource(if (item.log == null) R.string.save else R.string.done),
        onPrimary = { if (item.log == null) error = !onSave(reminderTime) else onDismiss() },
        secondaryLabel = stringResource(R.string.back)
    ) {
        Card(
            shape = DoseShapes.panel, colors = CardDefaults.cardColors(containerColor = SoftPanel),
            border = BorderStroke(DoseDimensions.border, PanelBorder)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(DoseSpacing.medium), verticalArrangement = Arrangement.spacedBy(DoseSpacing.small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DoseSpacing.small)
                ) {
                    PillAvatar(medication.color, medication.form, medication.localizedForm(context))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dose_label), style = DoseText.caption, color = TextMuted)
                        Text(
                            listOf(item.amount, localizedUnit(context, medication.quantity)).filter { it.isNotBlank() }
                                .joinToString(" "),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dose_status), style = DoseText.supporting, fontWeight = FontWeight.Bold)
                    StatusBadge(item.status)
                }
                DoseDetail(stringResource(R.string.medication_form), medication.localizedForm(context))
                if (item.log == null) DoseDetail(
                    stringResource(R.string.reminder_time),
                    formattedTime(item.scheduledAt)
                )
                if (item.log == null) item.snoozedUntil?.let {
                    DoseDetail(stringResource(R.string.snoozed_until), formattedTime(it))
                }
                item.log?.let { log ->
                    if (log.dose != item.dose) DoseDetail(stringResource(R.string.recorded_dose), log.dose)
                }
            }
        }
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DoseDimensions.touchTarget),
            shape = DoseShapes.button
        ) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DoseSpacing.extraSmall))
            Text(stringResource(R.string.edit_medication), fontWeight = FontWeight.Bold)
        }
        if (item.log == null) {
            HorizontalDivider(color = PanelBorder)
            Text(stringResource(R.string.today_only_hint), style = DoseText.supporting, color = TextMuted)
            Text(stringResource(R.string.reminder_time), fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = {
                    val parsed = LocalTime.parse(reminderTime)
                    TimePickerDialog(context, AppAppearance.dialogTheme(context), { _, hour, minute ->
                        reminderTime =
                            LocalTime.of(hour, minute)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        error = false
                    }, parsed.hour, parsed.minute, true).show()
                }, modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DoseDimensions.field)
            ) {
                Text(
                    reminderTime,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            if (error) Text(stringResource(R.string.today_time_error), color = MaterialTheme.colorScheme.error)
        }
        if (medication.note.isNotBlank()) {
            HorizontalDivider(color = PanelBorder)
            DoseDetail(stringResource(R.string.instructions), medication.note)
        }
        HorizontalDivider(color = PanelBorder)
        Text(stringResource(R.string.due_dates_times), fontWeight = FontWeight.Bold)
        medication.schedules.forEach { rule ->
            Text(
                scheduleSummary(
                    context,
                    rule,
                    medication.amount,
                    medication.quantity
                ), style = DoseText.body
            )
        }
    }
}

@Composable
private fun DoseDetail(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextMuted, style = DoseText.caption)
        Text(value, style = DoseText.body)
    }
}
