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
import androidx.compose.runtime.saveable.rememberSaveable
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

private val ScheduleListSaver = androidx.compose.runtime.saveable.Saver<List<DoseSchedule>, String>(
    save = { rules -> org.json.JSONArray().apply { rules.forEach { put(MedicationJson.encodeSchedule(it)) } }.toString() },
    restore = { saved -> runCatching {
        val array = org.json.JSONArray(saved)
        (0 until array.length()).map { MedicationJson.decodeSchedule(array.getJSONObject(it)) }
    }.getOrDefault(listOf(DoseSchedule())) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationEditor(
    original: Medication?, onDismiss: () -> Unit,
    onSave: (Medication) -> Unit, onDelete: (Medication) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(original?.amount.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(original?.quantity ?: "mg") }
    var form by rememberSaveable { mutableStateOf(original?.form ?: MedicationForm.PILL) }
    var customForm by rememberSaveable { mutableStateOf(original?.customForm.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(original?.color ?: "violet") }
    var schedules by rememberSaveable(stateSaver = ScheduleListSaver) { mutableStateOf(original?.schedules ?: listOf(DoseSchedule())) }
    var step by rememberSaveable { mutableStateOf(MedicationStep.NAME) }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var suggestedForm by rememberSaveable { mutableStateOf<String?>(null) }
    var addInstructions by rememberSaveable { mutableStateOf(original?.note?.isNotBlank() == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val colorUsage = remember { DoseStore.colorUsage(context) }
    val medications = remember { DoseStore.medications(context) }
    val steps = MedicationEditorFlow.steps(form)
    val position = steps.indexOf(step)
    val scroll = rememberScrollState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(step) { scroll.scrollTo(0) }

    fun previous() {
        keyboard?.hide()
        if (step == MedicationStep.NAME) onDismiss() else step = MedicationEditorFlow.previous(step, form)
        error = null
    }
    fun stepError(candidate: MedicationStep): String? = when (candidate) {
        MedicationStep.NAME -> if (name.isBlank()) "Enter a medication name." else null
        MedicationStep.DOSAGE -> when {
            amount.isBlank() -> "Enter an amount."
            quantity.isBlank() && !(original?.quantity == "" && amount == original.amount) -> "Choose a unit or enter your own."
            else -> null
        }
        MedicationStep.SCHEDULE -> if (schedules.isEmpty()) "Add at least one schedule." else schedules.mapIndexedNotNull { index, rule ->
            rule.validationError()?.let { "Schedule ${index + 1}: $it" }
        }.firstOrNull()
        else -> null
    }
    fun advance() {
        keyboard?.hide()
        error = stepError(step)
        if (error != null) return
        if (step != MedicationStep.DETAILS) { step = MedicationEditorFlow.next(step, form); return }
        val invalidStep = steps.firstOrNull { stepError(it) != null }
        if (invalidStep != null) { step = invalidStep; error = stepError(invalidStep); return }
        val rules = schedules.map { it.copy(times = it.times.sorted(), dates = it.dates.distinct().sorted(), weekDays = it.weekDays.sorted(), monthDays = it.monthDays.sorted()) }
        onSave((original ?: DoseStore.newMedication(name.trim(), amount.trim(), quantity.trim(), rules, "", color, form))
            .copy(name = name.trim(), amount = amount.trim(), quantity = quantity.trim(), schedules = rules,
                note = if (addInstructions) note.trim() else "", color = color, form = form,
                customForm = if (form == MedicationForm.OTHER) customForm.trim() else ""))
    }

    Dialog(onDismissRequest = ::previous, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(0.94f).imePadding(),
            shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (original == null) "Add medication" else "Edit medication", fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close medication editor") }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step ${position + 1} of ${steps.size} · ${step.title}", color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { (position + 1f) / steps.size }, modifier = Modifier.fillMaxWidth(), color = Violet)
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (step) {
                        MedicationStep.NAME -> {
                            OutlinedTextField(name, { name = it.take(70); showSuggestions = true; suggestedForm = null; error = null },
                                label = { Text("Medication name *") }, placeholder = { Text("Start typing a name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            val suggestions = if (showSuggestions) MedicationDictionary.suggestions(name, medications) else emptyList()
                            if (suggestions.isNotEmpty()) {
                                Text("Name suggestions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                suggestions.forEach { suggestion ->
                                    Surface(onClick = {
                                        name = suggestion.name; form = suggestion.form; customForm = suggestion.customForm
                                        suggestedForm = suggestion.formLabel; showSuggestions = false; keyboard?.hide(); error = null
                                    }, shape = RoundedCornerShape(13.dp), color = Color(0xFFF5F2FF), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(painterResource(suggestion.form.iconResource()), null, tint = Violet, modifier = Modifier.size(25.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(suggestion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(suggestion.formLabel + if (suggestion.saved) " · Previously entered" else "", color = TextMuted, fontSize = 12.sp)
                                            }
                                            Icon(Icons.Rounded.Add, "Choose ${suggestion.name}, ${suggestion.formLabel}", tint = Violet)
                                        }
                                    }
                                }
                            }
                            if (suggestedForm != null) Text("Suggested form: $suggestedForm. You can change it on the next screen.", fontSize = 13.sp, color = Violet)
                            Text("Color", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Your most used colors appear first.", color = TextMuted, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MedicationColors.choices(colorUsage, medications, color).forEach { choice ->
                                    val foreground = palette(choice).first
                                    val selected = MedicationColors.hex(color) == MedicationColors.hex(choice)
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(foreground)
                                        .then(if (selected) Modifier.border(3.dp, TextPrimary, RoundedCornerShape(12.dp)) else Modifier)
                                        .clickable { color = choice }.semantics { contentDescription = "$choice color${if (selected) ", selected" else ""}" }, contentAlignment = Alignment.Center) {
                                        if (selected) Icon(Icons.Rounded.Check, null, tint = if (foreground.luminance() > 0.45f) Color.Black else Color.White)
                                    }
                                }
                            }
                            TextButton(onClick = { showColorPicker = true }) { Icon(Icons.Rounded.Palette, null); Spacer(Modifier.width(6.dp)); Text("Pick a custom color") }
                        }
                        MedicationStep.FORM -> {
                            Text("Choose the form of $name.", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (suggestedForm != null) Text("$suggestedForm was suggested with the name. Choose a different form if needed.", color = TextMuted, fontSize = 13.sp)
                            MedicationForm.entries.forEach { choice ->
                                Surface(onClick = { form = choice; error = null }, shape = RoundedCornerShape(14.dp),
                                    color = if (form == choice) Color(0xFFF0ECFF) else Color(0xFFF8F9FD), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(painterResource(choice.iconResource()), null, tint = Violet, modifier = Modifier.size(30.dp))
                                        Text(choice.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        RadioButton(selected = form == choice, onClick = null)
                                    }
                                }
                            }
                        }
                        MedicationStep.OTHER_FORM -> {
                            Text("Give this form a name", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Optional—for example, a patch, inhaler, or suppository. Leave it empty to use Other.", fontSize = 13.sp, color = TextMuted)
                            OutlinedTextField(customForm, { customForm = it.take(40) }, label = { Text("Custom form (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        MedicationStep.DOSAGE -> {
                            Text("How much per dose?", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(amount, { amount = it.take(70); error = null }, label = { Text("Amount *") }, placeholder = { Text("e.g. 500") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(quantity, { quantity = it.take(40); error = null }, label = { Text("Unit *") }, placeholder = { Text("Choose below or type your own") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DoseUnits.common.forEach { unit -> FilterChip(selected = quantity == unit, onClick = { quantity = unit; error = null }, label = { Text(unit) }) }
                            }
                        }
                        MedicationStep.SCHEDULE -> {
                            Text("When should Dose remind you?", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Use one or several times per schedule. Overlapping dates and times produce one dose.", color = TextMuted, fontSize = 12.sp)
                            schedules.forEachIndexed { index, rule ->
                                ScheduleEditor(rule, index + 1, onChange = { updated ->
                                    schedules = schedules.toMutableList().apply { set(index, updated) }; error = null
                                }, onRemove = if (schedules.size > 1) ({ schedules = schedules.filterIndexed { i, _ -> i != index }; error = null }) else null)
                            }
                            OutlinedButton(onClick = { schedules = schedules + DoseSchedule(); error = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Add another schedule") }
                        }
                        MedicationStep.DETAILS -> {
                            Text("Review & finish", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Surface(color = Color(0xFFF5F2FF), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(name, fontWeight = FontWeight.Bold)
                                    Text((if (form == MedicationForm.OTHER) customForm.ifBlank { "Other" } else form.label) + " · $amount $quantity", fontSize = 14.sp)
                                    schedules.forEach { Text(scheduleSummary(it), color = TextMuted, fontSize = 12.sp) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Add instructions", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Switch(checked = addInstructions, onCheckedChange = { addInstructions = it })
                            }
                            if (addInstructions) OutlinedTextField(note, { note = it.take(140) }, label = { Text("Instructions (optional)") },
                                placeholder = { Text("e.g. Take after a meal") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            if (original != null) TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete medication", color = Color(0xFFC45151)) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (error != null) { Text(error.orEmpty(), color = Color(0xFFD14D53), fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (step != MedicationStep.NAME) OutlinedButton(onClick = ::previous) { Text("Back") }
                        Button(onClick = ::advance, modifier = Modifier.weight(1f).heightIn(min = 51.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (step == MedicationStep.DETAILS) { if (original == null) "Add medication" else "Save changes" } else "Continue", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showColorPicker) ColorPicker(color, onDismiss = { showColorPicker = false }, onChoose = { color = it; showColorPicker = false })
    if (showDeleteConfirm && original != null) AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text("Delete ${original.name}?") },
        text = { Text("Reminders will stop. Your previous dose history will be kept.") },
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
