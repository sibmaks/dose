package com.example.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dose.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationEditor(
    original: Medication?, onDismiss: () -> Unit,
    onSave: (Medication) -> Unit, onDelete: (Medication) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var amount by remember { mutableStateOf(original?.amount.orEmpty()) }
    var quantity by remember { mutableStateOf(original?.quantity ?: "mg") }
    var form by remember { mutableStateOf(original?.form ?: MedicationForm.PILL) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var color by remember { mutableStateOf(original?.color ?: "violet") }
    var schedules by remember { mutableStateOf(original?.schedules ?: listOf(DoseSchedule())) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val colorUsage = remember { DoseStore.colorUsage(context) }
    val medications = remember { DoseStore.medications(context) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(0.94f).imePadding(),
            shape = RoundedCornerShape(26.dp), color = Color.White,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (original == null) "Add medication" else "Edit medication", fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(name, { name = it.take(70); error = null }, label = { Text("Medication name *") },
                        placeholder = { Text("e.g. Vitamin D3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Medication form", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MedicationForm.entries.forEach { choice ->
                            FilterChip(selected = form == choice, onClick = { form = choice }, label = { Text(choice.label) },
                                leadingIcon = { Icon(painterResource(choice.iconResource()), null, modifier = Modifier.size(23.dp)) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(amount, { amount = it.take(70); error = null }, label = { Text("Amount *") },
                            placeholder = { Text("e.g. 500") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(quantity, { quantity = it.take(40); error = null }, label = { Text("Unit *") },
                            placeholder = { Text("mg or your own") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DoseUnits.common.forEach { unit ->
                            FilterChip(selected = quantity == unit, onClick = { quantity = unit; error = null }, label = { Text(unit) })
                        }
                    }
                    Text("Due dates & times", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Add several schedules, each with one time or a list of times. Overlapping dates and times produce one dose.", color = TextMuted, fontSize = 12.sp)
                    schedules.forEachIndexed { index, rule ->
                        ScheduleEditor(rule, index + 1, onChange = { updated ->
                            schedules = schedules.toMutableList().apply { set(index, updated) }; error = null
                        }, onRemove = if (schedules.size > 1) ({ schedules = schedules.filterIndexed { i, _ -> i != index }; error = null }) else null)
                    }
                    OutlinedButton(onClick = { schedules = schedules + DoseSchedule(); error = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add another schedule")
                    }
                    OutlinedTextField(note, { note = it.take(140) }, label = { Text("Instructions (optional)") },
                        placeholder = { Text("e.g. Take after a meal") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Your most used colors appear first.", color = TextMuted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MedicationColors.choices(colorUsage, medications, color).forEach { choice ->
                            val foreground = palette(choice).first
                            val selected = MedicationColors.hex(color) == MedicationColors.hex(choice)
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(foreground)
                                .then(if (selected) Modifier.border(3.dp, TextPrimary, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { color = choice }.semantics { contentDescription = "$choice color${if (selected) ", selected" else ""}" },
                                contentAlignment = Alignment.Center) {
                                if (selected) Icon(Icons.Rounded.Check, null, tint = if (foreground.luminance() > 0.45f) Color.Black else Color.White)
                            }
                        }
                    }
                    TextButton(onClick = { showColorPicker = true }) {
                        Icon(Icons.Rounded.Palette, null); Spacer(Modifier.width(6.dp)); Text("Pick a custom color")
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    if (error != null) {
                        Text(error.orEmpty(), color = Color(0xFFD14D53), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(modifier = Modifier.fillMaxWidth().height(51.dp), shape = RoundedCornerShape(14.dp), onClick = {
                        val invalid = schedules.mapIndexedNotNull { index, rule -> rule.validationError()?.let { "Schedule ${index + 1}: $it" } }.firstOrNull()
                        when {
                            name.isBlank() || amount.isBlank() -> error = "Enter a medication name and amount."
                            quantity.isBlank() && !(original?.quantity == "" && amount == original.amount) -> error = "Choose a unit or enter your own."
                            schedules.isEmpty() -> error = "Add at least one schedule."
                            invalid != null -> error = invalid
                            else -> {
                                val cleanRules = schedules.map { it.copy(times = it.times.sorted(), dates = it.dates.distinct().sorted(), weekDays = it.weekDays.sorted(), monthDays = it.monthDays.sorted()) }
                                onSave((original ?: DoseStore.newMedication(name.trim(), amount.trim(), quantity.trim(), cleanRules, note.trim(), color, form))
                                    .copy(name = name.trim(), amount = amount.trim(), quantity = quantity.trim(), schedules = cleanRules, note = note.trim(), color = color, form = form))
                            }
                        }
                    }) { Text(if (original == null) "Add medication" else "Save changes", fontWeight = FontWeight.Bold) }
                    if (original != null) TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Delete medication", color = Color(0xFFC45151))
                    }
                }
            }
        }
    }
    if (showColorPicker) ColorPicker(color, onDismiss = { showColorPicker = false }, onChoose = { color = it; showColorPicker = false })
    if (showDeleteConfirm && original != null) AlertDialog(onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Delete ${original.name}?") }, text = { Text("Reminders will stop. Your previous dose history will be kept.") },
        confirmButton = { TextButton(onClick = { onDelete(original) }) { Text("Delete", color = Color(0xFFC45151)) } },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(rule: DoseSchedule, number: Int, onChange: (DoseSchedule) -> Unit, onRemove: (() -> Unit)?) {
    val context = LocalContext.current
    fun pickDate(initial: String?, onPicked: (String) -> Unit) {
        val date = initial?.let(LocalDate::parse) ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).toString()) },
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    fun pickTime(index: Int?, initial: String = "12:00") {
        val time = LocalTime.parse(initial)
        TimePickerDialog(context, { _, hour, minute ->
            val formatted = "%02d:%02d".format(Locale.US, hour, minute)
            onChange(rule.copy(times = if (index == null) rule.times + formatted else rule.times.toMutableList().apply { set(index, formatted) }))
        }, time.hour, time.minute, true).show()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFAFAFE), border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Schedule $number", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove schedule $number", tint = Peach) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScheduleKind.entries.forEach { kind ->
                    FilterChip(selected = rule.kind == kind, onClick = { onChange(rule.copy(kind = kind)) }, label = { Text(kind.label) })
                }
            }
            when (rule.kind) {
                ScheduleKind.SPECIFIC_DATES -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rule.dates.sorted().forEach { date ->
                            InputChip(selected = false, onClick = { pickDate(date) { picked -> onChange(rule.copy(dates = (rule.dates - date + picked).distinct())) } },
                                label = { Text(date) }, trailingIcon = {
                                    Icon(Icons.Rounded.Close, "Remove date $date", Modifier.size(20.dp).clickable { onChange(rule.copy(dates = rule.dates - date)) })
                                })
                        }
                    }
                    TextButton(onClick = { pickDate(null) { onChange(rule.copy(dates = (rule.dates + it).distinct())) } }) {
                        Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text("Add date")
                    }
                }
                ScheduleKind.WEEKDAYS -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(selected = day in rule.weekDays,
                                onClick = { onChange(rule.copy(weekDays = if (day in rule.weekDays) rule.weekDays - day else rule.weekDays + day)) },
                                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) })
                        }
                    }
                }
                ScheduleKind.MONTH_DAYS -> {
                    (1..31).toList().chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { day ->
                                val selected = day in rule.monthDays
                                Surface(onClick = { onChange(rule.copy(monthDays = if (selected) rule.monthDays - day else rule.monthDays + day)) },
                                    modifier = Modifier.weight(1f).height(40.dp).semantics { contentDescription = "Day $day${if (selected) ", selected" else ""}" },
                                    shape = RoundedCornerShape(8.dp), color = if (selected) Violet else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, PanelBorder)) {
                                    Box(contentAlignment = Alignment.Center) { Text("$day", fontSize = 12.sp, color = if (selected) Color.White else TextPrimary) }
                                }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text("Dates absent from a month are skipped (e.g. February 31).", fontSize = 12.sp, color = TextMuted)
                }
                ScheduleKind.DAILY -> Unit
            }
            if (rule.kind != ScheduleKind.SPECIFIC_DATES) {
                DateBound("From", rule.from, "${rule.createdOn} (default)", onPick = { pickDate(rule.from ?: rule.createdOn) { onChange(rule.copy(from = it)) } }, onClear = { onChange(rule.copy(from = null, createdOn = LocalDate.now().toString())) })
                DateBound("Until", rule.until, "No end date", onPick = { pickDate(rule.until ?: rule.from) { onChange(rule.copy(until = it)) } }, onClear = { onChange(rule.copy(until = null)) })
                Text("From and until are optional and inclusive. Empty from uses the default date shown (today for a new schedule); empty until continues until paused or deleted.", fontSize = 12.sp, color = TextMuted)
            }
            Text("Times", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.times.forEachIndexed { index, time ->
                    InputChip(selected = false, onClick = { pickTime(index, time) }, label = { Text(time) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (rule.times.size > 1) Icon(Icons.Rounded.Close, "Remove time $time", Modifier.size(20.dp).clickable {
                                onChange(rule.copy(times = rule.times.filterIndexed { i, _ -> i != index }))
                            })
                        })
                }
            }
            TextButton(onClick = { pickTime(null) }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add time") }
        }
    }
}

@Composable
private fun DateBound(label: String, value: String?, fallback: String, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) { Text("$label: ${value ?: fallback}") }
        if (value != null) IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Clear $label date") }
    }
}

@Composable
private fun ColorPicker(initial: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var hex by remember { mutableStateOf(MedicationColors.hex(initial)) }
    var rgb by remember { mutableStateOf(android.graphics.Color.parseColor(hex)) }
    fun update(channel: Int, value: Float) {
        val channels = intArrayOf(android.graphics.Color.red(rgb), android.graphics.Color.green(rgb), android.graphics.Color.blue(rgb))
        channels[channel] = value.toInt()
        rgb = android.graphics.Color.rgb(channels[0], channels[1], channels[2])
        hex = "#%06X".format(Locale.US, rgb and 0xFFFFFF)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pick a color") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(55.dp).clip(RoundedCornerShape(12.dp)).background(Color(rgb)))
            OutlinedTextField(hex, { input ->
                hex = input.take(7).uppercase()
                if (Regex("#[0-9A-F]{6}").matches(hex)) rgb = android.graphics.Color.parseColor(hex)
            }, label = { Text("Hex color (#RRGGBB)") }, singleLine = true, isError = !Regex("#[0-9A-F]{6}").matches(hex))
            listOf("Red", "Green", "Blue").forEachIndexed { index, label ->
                val channel = when (index) { 0 -> android.graphics.Color.red(rgb); 1 -> android.graphics.Color.green(rgb); else -> android.graphics.Color.blue(rgb) }
                Text("$label: $channel", fontSize = 12.sp)
                Slider(value = channel.toFloat(), onValueChange = { update(index, it) }, valueRange = 0f..255f,
                    modifier = Modifier.semantics { contentDescription = "$label color channel" })
            }
        }
    }, confirmButton = { TextButton(onClick = { onChoose(hex) }, enabled = Regex("#[0-9A-F]{6}").matches(hex)) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun scheduleSummary(rule: DoseSchedule): String {
    val dates = when (rule.kind) {
        ScheduleKind.DAILY -> "Every day"
        ScheduleKind.SPECIFIC_DATES -> rule.dates.sorted().joinToString(", ")
        ScheduleKind.WEEKDAYS -> rule.weekDays.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        ScheduleKind.MONTH_DAYS -> "Monthly: ${rule.monthDays.sorted().joinToString(", ")}"
    }
    val bounds = if (rule.kind == ScheduleKind.SPECIFIC_DATES) "" else " · from ${rule.from ?: rule.createdOn}" + (rule.until?.let { " until $it" } ?: "")
    return "$dates · ${rule.times.joinToString(", ")}$bounds"
}
