package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.localization.*
import io.github.sibmaks.app.dose.R
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
import io.github.sibmaks.app.dose.data.*
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
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val colorUsage = remember { DoseStore.colorUsage(context) }
    val medications = remember { DoseStore.medications(context) }
    val dictionary = remember { MedicationDictionary.load(context) }
    val language = AppLanguages.locale(context).language
    val steps = MedicationEditorFlow.steps(form)
    val position = steps.indexOf(step)
    val scroll = rememberScrollState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(step) { scroll.scrollTo(0) }

    fun previous() {
        keyboard?.hide()
        if (step == MedicationStep.NAME) onDismiss() else step = MedicationEditorFlow.previous(step, form)
        showValidation = false
    }
    fun stepError(candidate: MedicationStep): String? = when (candidate) {
        MedicationStep.NAME -> if (name.isBlank()) context.getString(R.string.name_required) else null
        MedicationStep.DOSAGE -> when {
            amount.isBlank() -> context.getString(R.string.amount_required)
            quantity.isBlank() && !(original?.quantity == "" && amount == original.amount) -> context.getString(R.string.unit_required)
            else -> null
        }
        MedicationStep.SCHEDULE -> if (schedules.isEmpty()) context.getString(R.string.schedule_required) else schedules.mapIndexedNotNull { index, rule ->
            rule.validationError()?.let { context.getString(R.string.schedule_error, index + 1, localizedValidationError(context, it)) }
        }.firstOrNull()
        else -> null
    }
    fun advance() {
        keyboard?.hide()
        showValidation = true
        if (stepError(step) != null) return
        if (step != MedicationStep.DETAILS) { step = MedicationEditorFlow.next(step, form); showValidation = false; return }
        val invalidStep = steps.firstOrNull { stepError(it) != null }
        if (invalidStep != null) { step = invalidStep; return }
        val rules = schedules.map { it.copy(times = it.times.sorted(), dates = it.dates.distinct().sorted(), weekDays = it.weekDays.sorted(), monthDays = it.monthDays.sorted()) }
        onSave((original ?: DoseStore.newMedication(name.trim(), amount.trim(), quantity.trim(), rules, "", color, form))
            .copy(name = name.trim(), amount = amount.trim(), quantity = quantity.trim(), schedules = rules,
                note = if (addInstructions) note.trim() else "", color = color, form = form,
                customForm = if (form == MedicationForm.OTHER) customForm.trim() else "",
                active = original?.active ?: true))
    }

    Dialog(onDismissRequest = ::previous, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(0.94f).imePadding(),
            shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (original == null) context.getString(R.string.add_medication) else context.getString(R.string.edit_medication), fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, context.getString(R.string.close_medication_editor)) }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.editor_step, position + 1, steps.size, step.localizedTitle(context)), color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { (position + 1f) / steps.size }, modifier = Modifier.fillMaxWidth(), color = Violet)
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (step) {
                        MedicationStep.NAME -> {
                            OutlinedTextField(name, { name = it.take(70); showSuggestions = true; suggestedForm = null },
                                isError = showValidation && name.isBlank(),
                                supportingText = { if (showValidation && name.isBlank()) Text(context.getString(R.string.name_required)) },
                                label = { Text(context.getString(R.string.medication_name)) }, placeholder = { Text(context.getString(R.string.start_typing_a_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            val suggestions = if (showSuggestions) dictionary.suggestions(name, medications, language = language) else emptyList()
                            if (suggestions.isNotEmpty()) {
                                Text(context.getString(R.string.name_suggestions), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                suggestions.forEach { suggestion ->
                                    Surface(onClick = {
                                        name = suggestion.name; form = suggestion.form; customForm = if (suggestion.customForm.isBlank()) "" else suggestion.localizedForm(context)
                                        suggestedForm = suggestion.localizedForm(context); showSuggestions = false; keyboard?.hide()
                                    }, shape = RoundedCornerShape(13.dp), color = Color(0xFFF5F2FF), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(painterResource(suggestion.form.iconResource()), null, tint = Violet, modifier = Modifier.size(25.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(suggestion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(suggestion.localizedForm(context) + if (suggestion.saved) context.getString(R.string.previously_entered) else "", color = TextMuted, fontSize = 12.sp)
                                            }
                                            Icon(Icons.Rounded.Add, context.getString(R.string.choose_suggestion, suggestion.name, suggestion.localizedForm(context)), tint = Violet)
                                        }
                                    }
                                }
                            }
                            if (suggestedForm != null) Text(context.getString(R.string.suggested_form, suggestedForm.orEmpty()), fontSize = 13.sp, color = Violet)
                            Text(context.getString(R.string.color), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(context.getString(R.string.color_order_hint), color = TextMuted, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MedicationColors.choices(colorUsage, medications, color).forEach { choice ->
                                    val foreground = palette(choice).first
                                    val selected = MedicationColors.hex(color) == MedicationColors.hex(choice)
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(foreground)
                                        .then(if (selected) Modifier.border(3.dp, TextPrimary, RoundedCornerShape(12.dp)) else Modifier)
                                        .clickable { color = choice }.semantics { contentDescription = context.getString(R.string.color_description, localizedColor(context, choice), if (selected) context.getString(R.string.selected_suffix) else "") }, contentAlignment = Alignment.Center) {
                                        if (selected) Icon(Icons.Rounded.Check, null, tint = if (foreground.luminance() > 0.45f) Color.Black else Color.White)
                                    }
                                }
                            }
                            TextButton(onClick = { showColorPicker = true }) { Icon(Icons.Rounded.Palette, null); Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.pick_a_custom_color)) }
                        }
                        MedicationStep.FORM -> {
                            Text(context.getString(R.string.choose_form, name), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (suggestedForm != null) Text(context.getString(R.string.change_suggested_form, suggestedForm.orEmpty()), color = TextMuted, fontSize = 13.sp)
                            MedicationForm.entries.forEach { choice ->
                                Surface(onClick = { form = choice }, shape = RoundedCornerShape(14.dp),
                                    color = if (form == choice) Color(0xFFF0ECFF) else Color(0xFFF8F9FD), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(painterResource(choice.iconResource()), null, tint = Violet, modifier = Modifier.size(30.dp))
                                        Text(choice.localizedLabel(context), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        RadioButton(selected = form == choice, onClick = null)
                                    }
                                }
                            }
                        }
                        MedicationStep.OTHER_FORM -> {
                            Text(context.getString(R.string.give_this_form_a_name), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.custom_form_hint), fontSize = 13.sp, color = TextMuted)
                            OutlinedTextField(customForm, { customForm = it.take(40) }, label = { Text(context.getString(R.string.custom_form_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        MedicationStep.DOSAGE -> {
                            Text(context.getString(R.string.how_much_per_dose), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(amount, { amount = it.take(70) }, label = { Text(context.getString(R.string.amount)) }, placeholder = { Text(context.getString(R.string.e_g_500)) },
                                isError = showValidation && amount.isBlank(), supportingText = { if (showValidation && amount.isBlank()) Text(context.getString(R.string.amount_required)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(localizedUnit(context, quantity), { quantity = it.take(40) }, label = { Text(context.getString(R.string.unit)) }, placeholder = { Text(context.getString(R.string.choose_below_or_type_your_own)) },
                                isError = showValidation && quantity.isBlank() && !(original?.quantity == "" && amount == original.amount),
                                supportingText = { if (showValidation && quantity.isBlank() && !(original?.quantity == "" && amount == original.amount)) Text(context.getString(R.string.unit_required)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DoseUnits.common.forEach { unit -> FilterChip(selected = quantity == unit, onClick = { quantity = unit }, label = { Text(localizedUnit(context, unit)) }) }
                            }
                        }
                        MedicationStep.SCHEDULE -> {
                            Text(context.getString(R.string.when_should_dose_remind_you), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.schedule_overlap_hint), color = TextMuted, fontSize = 12.sp)
                            schedules.forEachIndexed { index, rule ->
                                ScheduleEditor(rule, index + 1, showValidation, onChange = { updated ->
                                    schedules = schedules.toMutableList().apply { set(index, updated.copy(immediateAt = null)) }
                                }, onRemove = if (schedules.size > 1) ({ schedules = schedules.filterIndexed { i, _ -> i != index } }) else null)
                            }
                            OutlinedButton(onClick = { schedules = schedules + DoseSchedule() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(context.getString(R.string.add_another_schedule)) }
                        }
                        MedicationStep.DETAILS -> {
                            Text(context.getString(R.string.review_finish), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Surface(color = Color(0xFFF5F2FF), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(name, fontWeight = FontWeight.Bold)
                                    Text((if (form == MedicationForm.OTHER) customForm.ifBlank { context.getString(R.string.other) } else form.localizedLabel(context)) + " · $amount ${localizedUnit(context, quantity)}", fontSize = 14.sp)
                                    schedules.forEach { Text(scheduleSummary(context, it), color = TextMuted, fontSize = 12.sp) }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(context.getString(R.string.add_instructions), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Switch(checked = addInstructions, onCheckedChange = { addInstructions = it })
                            }
                            if (addInstructions) OutlinedTextField(note, { note = it.take(140) }, label = { Text(context.getString(R.string.instructions_optional)) },
                                placeholder = { Text(context.getString(R.string.e_g_take_after_a_meal)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            if (original != null) TextButton(onClick = { showDeleteConfirm = true }) { Text(context.getString(R.string.delete_medication), color = Color(0xFFC45151)) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (step != MedicationStep.NAME) OutlinedButton(onClick = ::previous, modifier = Modifier.weight(1f).heightIn(min = 51.dp), shape = RoundedCornerShape(14.dp)) { Text(context.getString(R.string.back), fontWeight = FontWeight.Bold) }
                        Button(onClick = ::advance, modifier = Modifier.weight(1f).heightIn(min = 51.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (step == MedicationStep.DETAILS) context.getString(R.string.save) else context.getString(R.string.continue_action), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showColorPicker) ColorPicker(color, onDismiss = { showColorPicker = false }, onChoose = { color = it; showColorPicker = false })
    if (showDeleteConfirm && original != null) AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text(context.getString(R.string.delete_named, original.name)) },
        text = { Text(context.getString(R.string.delete_medication_hint)) },
        confirmButton = { TextButton(onClick = { onDelete(original) }) { Text(context.getString(R.string.delete), color = Color(0xFFC45151)) } },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(context.getString(R.string.cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(rule: DoseSchedule, number: Int, showValidation: Boolean, onChange: (DoseSchedule) -> Unit, onRemove: (() -> Unit)?) {
    val context = LocalContext.current
    val issue = if (showValidation) rule.validationIssue() else null
    val errorColor = MaterialTheme.colorScheme.error
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
                Text(context.getString(R.string.schedule_number, number), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, context.getString(R.string.remove_schedule, number), tint = Peach) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScheduleKind.entries.forEach { kind ->
                    FilterChip(selected = rule.kind == kind, onClick = { onChange(rule.copy(kind = kind)) }, label = { Text(kind.localizedLabel(context)) })
                }
            }
            when (rule.kind) {
                ScheduleKind.SPECIFIC_DATES -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rule.dates.sorted().forEach { date ->
                            InputChip(selected = false, onClick = { pickDate(date) { picked -> onChange(rule.copy(dates = (rule.dates - date + picked).distinct())) } },
                                label = { Text(localizedDate(context, date)) }, trailingIcon = {
                                    Icon(Icons.Rounded.Close, context.getString(R.string.remove_date, date), Modifier.size(20.dp).clickable { onChange(rule.copy(dates = rule.dates - date)) })
                                })
                        }
                    }
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = if (issue?.field == ScheduleField.DATES) errorColor else Violet), onClick = { pickDate(null) { onChange(rule.copy(dates = (rule.dates + it).distinct())) } }) {
                        Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.add_date))
                    }
                    ScheduleError(issue, ScheduleField.DATES)
                }
                ScheduleKind.WEEKDAYS -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(selected = day in rule.weekDays,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (issue?.field == ScheduleField.WEEKDAYS) errorColor else PanelBorder),
                                onClick = { onChange(rule.copy(weekDays = if (day in rule.weekDays) rule.weekDays - day else rule.weekDays + day)) },
                                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, AppLanguages.locale(context))) })
                        }
                    }
                    ScheduleError(issue, ScheduleField.WEEKDAYS)
                }
                ScheduleKind.MONTH_DAYS -> {
                    (1..31).toList().chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { day ->
                                val selected = day in rule.monthDays
                                Surface(onClick = { onChange(rule.copy(monthDays = if (selected) rule.monthDays - day else rule.monthDays + day)) },
                                    modifier = Modifier.weight(1f).height(40.dp).semantics { contentDescription = context.getString(R.string.day_description, day, if (selected) context.getString(R.string.selected_suffix) else "") },
                                    shape = RoundedCornerShape(8.dp), color = if (selected) Violet else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (issue?.field == ScheduleField.MONTH_DAYS) errorColor else PanelBorder)) {
                                    Box(contentAlignment = Alignment.Center) { Text("$day", fontSize = 12.sp, color = if (selected) Color.White else TextPrimary) }
                                }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    ScheduleError(issue, ScheduleField.MONTH_DAYS)
                    Text(context.getString(R.string.missing_month_days_hint), fontSize = 12.sp, color = TextMuted)
                }
                ScheduleKind.DAILY -> Unit
            }
            if (rule.kind != ScheduleKind.SPECIFIC_DATES) {
                DateBound(context.getString(R.string.from), rule.from, context.getString(R.string.default_date, localizedDate(context, rule.createdOn)), onPick = { pickDate(rule.from ?: rule.createdOn) { onChange(rule.copy(from = it)) } }, onClear = { onChange(rule.copy(from = null, createdOn = LocalDate.now().toString())) }, error = issue?.takeIf { it.field in listOf(ScheduleField.START, ScheduleField.BOUNDS) }?.let { localizedValidationError(context, it.message) })
                DateBound(context.getString(R.string.until), rule.until, context.getString(R.string.no_end_date), onPick = { pickDate(rule.until ?: rule.from) { onChange(rule.copy(until = it)) } }, onClear = { onChange(rule.copy(until = null)) }, error = issue?.takeIf { it.field in listOf(ScheduleField.END, ScheduleField.BOUNDS) }?.let { localizedValidationError(context, it.message) })
                Text(context.getString(R.string.date_bounds_hint), fontSize = 12.sp, color = TextMuted)
            }
            Text(context.getString(R.string.times), color = if (issue?.field == ScheduleField.TIMES) errorColor else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.times.forEachIndexed { index, time ->
                    InputChip(selected = false, border = androidx.compose.foundation.BorderStroke(1.dp, if (issue?.field == ScheduleField.TIMES) errorColor else PanelBorder), onClick = { pickTime(index, time) }, label = { Text(time) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (rule.times.size > 1) Icon(Icons.Rounded.Close, context.getString(R.string.remove_time, time), Modifier.size(20.dp).clickable {
                                onChange(rule.copy(times = rule.times.filterIndexed { i, _ -> i != index }))
                            })
                        })
                }
            }
            ScheduleError(issue, ScheduleField.TIMES)
            TextButton(onClick = { pickTime(null) }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(context.getString(R.string.add_time)) }
        }
    }
}

@Composable
private fun ScheduleError(issue: ScheduleIssue?, field: ScheduleField) {
    if (issue?.field == field) Text(localizedValidationError(LocalContext.current, issue.message), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

@Composable
private fun DateBound(label: String, value: String?, fallback: String, onPick: () -> Unit, onClear: () -> Unit, error: String? = null) {
    val context = LocalContext.current
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(border = androidx.compose.foundation.BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else PanelBorder), onClick = onPick, modifier = Modifier.weight(1f)) { Text(context.getString(R.string.label_value, label, value?.let { localizedDate(context, it) } ?: fallback)) }
        if (value != null) IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, context.getString(R.string.clear_bound, label)) }
    }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun ColorPicker(initial: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var hex by remember { mutableStateOf(MedicationColors.hex(initial)) }
    var rgb by remember { mutableStateOf(android.graphics.Color.parseColor(hex)) }
    fun update(channel: Int, value: Float) {
        val channels = intArrayOf(android.graphics.Color.red(rgb), android.graphics.Color.green(rgb), android.graphics.Color.blue(rgb))
        channels[channel] = value.toInt()
        rgb = android.graphics.Color.rgb(channels[0], channels[1], channels[2])
        hex = "#%06X".format(Locale.US, rgb and 0xFFFFFF)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(R.string.pick_a_color)) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(55.dp).clip(RoundedCornerShape(12.dp)).background(Color(rgb)))
            OutlinedTextField(hex, { input ->
                hex = input.take(7).uppercase()
                if (Regex("#[0-9A-F]{6}").matches(hex)) rgb = android.graphics.Color.parseColor(hex)
            }, label = { Text(context.getString(R.string.hex_color_rrggbb)) }, singleLine = true, isError = !Regex("#[0-9A-F]{6}").matches(hex))
            listOf(context.getString(R.string.red), context.getString(R.string.green), context.getString(R.string.blue)).forEachIndexed { index, label ->
                val channel = when (index) { 0 -> android.graphics.Color.red(rgb); 1 -> android.graphics.Color.green(rgb); else -> android.graphics.Color.blue(rgb) }
                Text(context.getString(R.string.label_value, label, channel.toString()), fontSize = 12.sp)
                Slider(value = channel.toFloat(), onValueChange = { update(index, it) }, valueRange = 0f..255f,
                    modifier = Modifier.semantics { contentDescription = context.getString(R.string.color_channel, label) })
            }
        }
    }, confirmButton = { TextButton(onClick = { onChoose(hex) }, enabled = Regex("#[0-9A-F]{6}").matches(hex)) { Text(context.getString(R.string.use_color)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) } })
}

internal fun scheduleSummary(context: android.content.Context, rule: DoseSchedule): String {
    val dates = when (rule.kind) {
        ScheduleKind.DAILY -> context.getString(R.string.every_day)
        ScheduleKind.SPECIFIC_DATES -> rule.dates.sorted().joinToString(", ") { localizedDate(context, it) }
        ScheduleKind.WEEKDAYS -> rule.weekDays.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, AppLanguages.locale(context)) }
        ScheduleKind.MONTH_DAYS -> context.getString(R.string.monthly_days, rule.monthDays.sorted().joinToString(", "))
    }
    val bounds = if (rule.kind == ScheduleKind.SPECIFIC_DATES) "" else context.getString(R.string.from_date, localizedDate(context, rule.from ?: rule.createdOn)) + (rule.until?.let { context.getString(R.string.until_date, localizedDate(context, it)) } ?: "")
    return "$dates · ${rule.times.joinToString(", ")}$bounds"
}
