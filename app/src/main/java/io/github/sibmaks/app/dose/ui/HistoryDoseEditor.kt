package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.DoseLog
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.localization.AppLanguages
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Intake editing: unresolved statuses may become "taken" with an explicitly chosen date and time;
 * a taken entry only corrects its real time. The reminder rail is never touched here.
 */
@Composable
internal fun HistoryDoseEditor(
    log: DoseLog,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Boolean
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val locale = AppLanguages.locale(context)
    val now = remember(log) { System.currentTimeMillis() }
    val scheduled = log.scheduledAt
        ?: runCatching { DoseLogic.scheduledMillis(LocalDate.parse(log.date), log.time) }.getOrNull()

    var status by rememberSaveable(log) { mutableStateOf(log.status) }
    var at by rememberSaveable(log) {
        mutableLongStateOf(if (log.status == "taken") log.recordedAt else 0L)
    }
    var staleError by rememberSaveable(log) { mutableStateOf(false) }

    val chosen = at.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val chosenDate = chosen?.toLocalDate()
    val chosenTime = chosen?.toLocalTime()
    // A taken entry keeps and corrects its status; unresolved entries may switch to taken or between themselves.
    val options = when (log.status) {
        "taken" -> listOf("taken")
        "skipped" -> listOf("taken", "skipped")
        else -> listOf("taken", "pending", "missed")
    }
    val changed = status != log.status || (status == "taken" && at != log.recordedAt)
    val valid = status != "taken" || (at in 1..now)
    val enabled = changed && valid
    // Pickers open at the recorded time, else near the reminder deadline — the value itself stays empty until chosen.
    val pickerBase = Instant.ofEpochMilli((at.takeIf { it > 0 } ?: scheduled ?: now).coerceAtMost(now))
        .atZone(zone)
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun pickDate() {
        DatePickerDialog(
            context, AppAppearance.dialogTheme(context), { _, year, month, day ->
                at = LocalDate.of(year, month + 1, day)
                    .atTime(pickerBase.toLocalTime())
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }, pickerBase.year, pickerBase.monthValue - 1, pickerBase.dayOfMonth,
        ).apply { datePicker.maxDate = now }.show()
    }

    fun pickTime() {
        TimePickerDialog(
            context, AppAppearance.dialogTheme(context), { _, hour, minute ->
                at = pickerBase.toLocalDate()
                    .atTime(hour, minute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }, pickerBase.hour, pickerBase.minute, true,
        ).show()
    }

    IntakeScreen(
        title = stringResource(R.string.history_edit_title),
        onClose = onDismiss,
        bottomBar = {
            Button(
                onClick = { if (!onSave(status, at)) staleError = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 51.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
    ) {
        IntakeMedicationCard(log, scheduled, DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale))

        TimelineHeading(stringResource(R.string.status_section))
        options.forEach { option ->
            StatusOption(
                style = intakeStatusStyle(option),
                selected = status == option,
                current = option == log.status,
                onClick = { status = option },
            )
        }

        if (status == "taken") {
            TimelineHeading(stringResource(R.string.intake_time_section))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = stringResource(R.string.intake_date_label),
                    value = chosenDate?.format(dateFormatter) ?: "",
                    placeholder = stringResource(R.string.pick_date_placeholder),
                    icon = Icons.Rounded.CalendarMonth,
                    invalid = !valid,
                    modifier = Modifier.weight(1.4f),
                    onClick = ::pickDate,
                )
                PickerField(
                    label = stringResource(R.string.intake_time_label),
                    value = chosenTime?.format(timeFormatter) ?: "",
                    placeholder = stringResource(R.string.pick_time_placeholder),
                    icon = Icons.Rounded.Schedule,
                    invalid = !valid,
                    modifier = Modifier.weight(1f),
                    onClick = ::pickTime,
                )
            }
            when {
                at <= 0L -> FieldError(stringResource(R.string.intake_time_required_error))
                at > now -> FieldError(stringResource(R.string.history_time_error))
            }
            Text(
                stringResource(
                    if (log.status == "taken") R.string.edit_intake_hint_taken else R.string.edit_intake_hint_late
                ), fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (staleError) Text(
            stringResource(R.string.history_changed_error), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StatusOption(
    style: IntakeStatusStyle,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentPanel else Panel,
        border = BorderStroke(1.dp, if (selected) Violet else PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(style.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (current) {
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Panel else AccentPanel)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.current_status_tag), fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) TextMuted else Violet,
                            )
                        }
                    }
                }
                Text(style.hint, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(top = 1.dp))
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (invalid) themeColor(0xFFFFF6F4, 0xFF3A2A24) else Panel
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(background)
                .border(
                    BorderStroke(1.5.dp, if (invalid) MaterialTheme.colorScheme.error else PanelBorder),
                    RoundedCornerShape(15.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { placeholder },
                fontSize = 15.sp,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (value.isBlank()) TextMuted else TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(19.dp))
        }
        Box(Modifier.offset(x = 10.dp, y = (-8).dp)) {
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (invalid) MaterialTheme.colorScheme.error else TextMuted,
                modifier = Modifier
                    .background(background)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun FieldError(message: String) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.ErrorOutline, null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
