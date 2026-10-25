package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.*
import io.github.sibmaks.app.dose.localization.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

private val ScheduleListSaver = Saver<List<DoseSchedule>, String>(
    save = { rules ->
        org.json.JSONArray()
            .apply { rules.forEach { put(MedicationJson.encodeSchedule(it)) } }
            .toString()
    },
    restore = { saved ->
        runCatching {
            val array = org.json.JSONArray(saved)
            (0 until array.length()).map { MedicationJson.decodeSchedule(array.getJSONObject(it)) }
        }.getOrDefault(listOf(DoseSchedule()))
    },
)

@Composable
internal fun MedicationEditor(
    original: Medication?,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(original?.amount.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(original?.quantity ?: DoseUnits.defaultFor(MedicationForm.PILL)) }
    var automaticUnit by rememberSaveable { mutableStateOf(original == null) }
    var customUnitMode by rememberSaveable {
        mutableStateOf(original?.quantity?.let { it.isNotBlank() && it !in DoseUnits.common } == true)
    }
    var listedUnit by rememberSaveable { mutableStateOf(if (quantity in DoseUnits.common) quantity else "") }
    var customUnitText by rememberSaveable { mutableStateOf(if (customUnitMode) quantity else "") }
    var form by rememberSaveable { mutableStateOf(original?.form ?: MedicationForm.PILL) }
    var customForm by rememberSaveable { mutableStateOf(original?.customForm.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(original?.color ?: "violet") }
    var schedules by rememberSaveable(stateSaver = ScheduleListSaver) {
        mutableStateOf(
            original?.schedules ?: listOf(
                DoseSchedule()
            )
        )
    }
    var step by rememberSaveable { mutableStateOf(MedicationStep.NAME) }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var suggestedForm by rememberSaveable { mutableStateOf<String?>(null) }
    var addInstructions by rememberSaveable { mutableStateOf(original?.note?.isNotBlank() == true) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var conflictAttention by rememberSaveable { mutableStateOf(false) }
    var expandedConflict by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var colorPickerInitial by rememberSaveable { mutableStateOf<String?>(null) }
    val colorUsage = remember { DoseStore.colorUsage(context) }
    val medications = remember { DoseStore.medications(context) }
    val appContext = context.applicationContext
    val dictionary by produceState<MedicationDictionary?>(initialValue = null, key1 = appContext) {
        value = withContext(Dispatchers.Default) { MedicationDictionary.load(appContext) }
    }
    val language = AppLanguages.locale(context).language
    val localizedForms = remember(context, language) {
        MedicationForms.sorted(AppLanguages.locale(context)) { it.localizedLabel(context) }
    }
    // The overlaps screen appears only when coinciding schedules with different dosages are detected.
    val hasOverlaps = remember(schedules, amount) {
        DoseScheduleConflicts.coincidences(schedules, amount.trim())
            .isNotEmpty()
    }
    val steps = MedicationEditorFlow.steps(form, hasOverlaps)
    var position = steps.indexOf(step)
    if (position < 0) {
        // The optional overlaps screen disappeared while it was the current step; fall back to its neighbour.
        step = if (step == MedicationStep.OVERLAPS) MedicationStep.SCHEDULE else MedicationStep.DETAILS
        position =
            steps.indexOf(step)
                .coerceAtLeast(0)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    // A legacy medication may intentionally have no unit; it stays optional while the amount is untouched.
    val unitOptional = original?.quantity == "" && amount == original.amount

    fun changeSchedules(updated: List<DoseSchedule>) {
        schedules = updated.map { it.copy(priority = 0) }
        conflictAttention = false
        expandedConflict = null
    }

    fun chooseForm(chosen: MedicationForm) {
        quantity = DoseUnits.forFormChange(chosen, quantity, automaticUnit)
        // An untouched default is a list selection; it follows the form change.
        if (automaticUnit) {
            listedUnit = quantity
            customUnitMode = false
        }
        form = chosen
    }

    fun chooseUnit(chosen: String) {
        quantity = chosen.take(40)
        automaticUnit = false
        listedUnit = quantity
    }

    /** Mode switches swap the effective unit for the other mode's remembered value; nothing is lost. */
    fun useListedUnit() {
        if (!customUnitMode) return
        customUnitMode = false
        quantity = listedUnit
    }

    fun useCustomUnit() {
        if (customUnitMode) return
        customUnitMode = true
        quantity = customUnitText
    }

    fun previous() {
        keyboard?.hide()
        if (step == MedicationStep.NAME) onDismiss() else step = MedicationEditorFlow.previous(step, form, hasOverlaps)
        showValidation = false
    }

    fun stepError(candidate: MedicationStep): String? = when (candidate) {
        MedicationStep.NAME -> if (name.isBlank()) resources.getString(R.string.name_required) else null
        MedicationStep.DOSAGE -> when {
            amount.isBlank() -> resources.getString(R.string.amount_required)
            quantity.isBlank() && !unitOptional -> resources.getString(R.string.unit_required)
            (amount.trim()
                .replace(',', '.')
                .toDoubleOrNull() ?: 1.0) <= 0.0 ->
                resources.getString(R.string.amount_positive)

            else -> null
        }

        MedicationStep.SCHEDULE -> if (schedules.isEmpty()) resources.getString(R.string.schedule_required) else schedules.mapIndexedNotNull { index, rule ->
            rule.validationError()
                ?.let { resources.getString(R.string.schedule_error, index + 1, localizedValidationError(context, it)) }
        }
            .firstOrNull()

        MedicationStep.OVERLAPS -> if (DoseScheduleConflicts.unresolved(schedules, amount.trim())
                .isNotEmpty()
        ) resources.getString(R.string.dose_overlap_pick_all) else null

        MedicationStep.DETAILS -> if (addInstructions && note.isBlank()) resources.getString(R.string.instructions_required) else null
        else -> null
    }

    fun save() {
        keyboard?.hide()
        showValidation = true
        val invalidStep = steps.firstOrNull { stepError(it) != null }
        if (invalidStep != null) {
            step = invalidStep; return
        }
        val rules = schedules.map {
            it.copy(
                times = it.times.sorted(),
                dates = it.dates.distinct()
                    .sorted(),
                weekDays = it.weekDays.sorted(),
                monthDays = it.monthDays.sorted(),
                amount = it.amount?.trim()
            )
        }
        if (DoseScheduleConflicts.unresolved(rules, amount.trim())
                .isNotEmpty()
        ) {
            // Saving waits until every coincidence has a priority; the overlaps screen collects the choices.
            conflictAttention = true
            showValidation = true
            val first = DoseScheduleConflicts.coincidences(rules, amount.trim())
                .firstOrNull { !it.resolved }
            if (first != null) expandedConflict = first.key
            step = MedicationStep.OVERLAPS
            return
        }
        conflictAttention = false
        onSave(
            (original ?: DoseStore.newMedication(name.trim(), amount.trim(), quantity.trim(), rules, "", color, form))
                .copy(
                    name = name.trim(), amount = amount.trim(), quantity = quantity.trim(), schedules = rules,
                    note = if (addInstructions) note.trim() else "", color = color, form = form,
                    customForm = if (form == MedicationForm.OTHER) customForm.trim() else "",
                    active = original?.active ?: true
                )
        )
    }

    fun advance() {
        if (step == MedicationStep.DETAILS) {
            save(); return
        }
        keyboard?.hide()
        showValidation = true
        if (stepError(step) != null) return
        step = MedicationEditorFlow.next(step, form, hasOverlaps)
        showValidation = false
    }

    // Only expose the next page after the same validation used by Continue succeeds.
    // Pager bookkeeping goes through one snapshot variable: the count and the per-page keys can then
    // never observe disagreeing versions of the steps list while the overlaps screen appears or vanishes.
    var pagerPageCount by remember { mutableStateOf(MedicationEditorFlow.steps(form, hasOverlaps).size) }
    val validStep = stepError(step) == null
    val pager = rememberPagerState(initialPage = position.coerceIn(0, steps.lastIndex)) { pagerPageCount }
    val activePosition by rememberUpdatedState(position)
    LaunchedEffect(step) {
        pager.animateScrollToPage(position.coerceIn(0, steps.lastIndex))
    }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage > activePosition) advance()
        else if (pager.settledPage < activePosition) previous()
    }
    SideEffect {
        val wanted = if (validStep) steps.size else position + 1
        if (pagerPageCount != wanted) pagerPageCount = wanted
    }
    val forwardBlocked by rememberUpdatedState(!validStep || step == MedicationStep.DETAILS)
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    Dialog(
        onDismissRequest = ::previous,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .fillMaxHeight(0.94f)
                .imePadding(),
            shape = RoundedCornerShape(26.dp), color = Panel
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (original == null) stringResource(R.string.add_medication) else stringResource(R.string.edit_medication),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (original != null && step != MedicationStep.DETAILS) TextButton(onClick = ::save) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.close_medication_editor)
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.editor_step,
                            position + 1,
                            steps.size,
                            step.localizedTitle(context)
                        ), color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    LinearProgressIndicator(
                        progress = { (position + 1f) / steps.size },
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet
                    )
                }
                HorizontalPager(
                    state = pager, key = { page -> steps.getOrNull(page)?.ordinal ?: (1000 + page) },
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(step, swipeThreshold) {
                            // Observe blocked forward attempts without consuming the pager's gesture.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var distance = Offset.Zero
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    distance += change.position - change.previousPosition
                                } while (event.changes.any { it.pressed })
                                if (forwardBlocked && distance.x < -swipeThreshold && -distance.x > kotlin.math.abs(
                                        distance.y
                                    )
                                ) {
                                    keyboard?.hide()
                                    showValidation = true
                                }
                            }
                        }) { page ->
                    val pageStep = steps.getOrNull(page) ?: return@HorizontalPager
                    val scroll = rememberScrollState()
                    val validationVisible = showValidation && pageStep == step
                    LaunchedEffect(step) { if (pageStep == step) scroll.scrollTo(0) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (pageStep) {
                            MedicationStep.NAME -> {
                                OutlinedTextField(
                                    name,
                                    { name = it.take(70); showSuggestions = true; suggestedForm = null },
                                    isError = validationVisible && name.isBlank(),
                                    supportingText = { if (validationVisible && name.isBlank()) Text(stringResource(R.string.name_required)) },
                                    label = { Text(stringResource(R.string.medication_name)) },
                                    placeholder = { Text(stringResource(R.string.start_typing_a_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val suggestions =
                                    if (showSuggestions) dictionary?.suggestions(name, medications, language = language)
                                        .orEmpty() else emptyList()
                                if (suggestions.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.name_suggestions),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    suggestions.forEach { suggestion ->
                                        Surface(
                                            onClick = {
                                                name = suggestion.name; chooseForm(suggestion.form); customForm =
                                                if (suggestion.customForm.isBlank()) "" else suggestion.localizedForm(
                                                    context
                                                )
                                                suggestedForm = suggestion.localizedForm(context); showSuggestions =
                                                false; keyboard?.hide()
                                            },
                                            shape = RoundedCornerShape(13.dp),
                                            color = AccentPanel,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    painterResource(suggestion.form.iconResource()),
                                                    null,
                                                    tint = Violet,
                                                    modifier = Modifier.size(25.dp)
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        suggestion.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        suggestion.localizedForm(context) + if (suggestion.saved) stringResource(
                                                            R.string.previously_entered
                                                        ) else "", color = TextMuted, fontSize = 12.sp
                                                    )
                                                }
                                                Icon(
                                                    Icons.Rounded.Add,
                                                    stringResource(
                                                        R.string.choose_suggestion,
                                                        suggestion.name,
                                                        suggestion.localizedForm(context)
                                                    ),
                                                    tint = Violet
                                                )
                                            }
                                        }
                                    }
                                }
                                if (suggestedForm != null) Text(
                                    stringResource(
                                        R.string.suggested_form,
                                        suggestedForm.orEmpty()
                                    ), fontSize = 13.sp, color = Violet
                                )
                                Text(stringResource(R.string.color), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MedicationColors.choices(colorUsage, medications, color)
                                        .forEach { choice ->
                                            val foreground =
                                                Color(
                                                    MedicationColors.hex(choice)
                                                        .toColorInt()
                                                )
                                            val selected = MedicationColors.hex(color) == MedicationColors.hex(choice)
                                            val colorDescriptionLocalization = stringResource(
                                                R.string.color_description,
                                                localizedColor(context, choice),
                                                if (selected) stringResource(R.string.selected_suffix) else ""
                                            )
                                            Box(
                                                Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(foreground)
                                                    .border(
                                                        if (selected) 3.dp else 1.dp,
                                                        if (selected) TextPrimary else PanelBorder,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable { color = choice }
                                                    .semantics {
                                                        contentDescription = colorDescriptionLocalization
                                                    }, contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) Icon(
                                                    Icons.Rounded.Check,
                                                    null,
                                                    tint = if (foreground.luminance() > 0.45f) Color.Black else Color.White
                                                )
                                            }
                                        }
                                }
                                TextButton(onClick = { colorPickerInitial = color }) {
                                    Icon(
                                        Icons.Rounded.Palette,
                                        null
                                    ); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pick_a_custom_color))
                                }
                            }

                            MedicationStep.FORM -> {
                                Text(
                                    stringResource(R.string.choose_form, name),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (suggestedForm != null) Text(
                                    stringResource(
                                        R.string.change_suggested_form,
                                        suggestedForm.orEmpty()
                                    ), color = TextMuted, fontSize = 13.sp
                                )
                                localizedForms.forEach { choice ->
                                    Surface(
                                        onClick = { chooseForm(choice) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (form == choice) AccentPanel else SoftPanel,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            PillAvatar(color, choice, choice.localizedLabel(context))
                                            Text(
                                                choice.localizedLabel(context),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            RadioButton(selected = form == choice, onClick = null)
                                        }
                                    }
                                }
                            }

                            MedicationStep.OTHER_FORM -> {
                                Text(
                                    stringResource(R.string.give_this_form_a_name),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(stringResource(R.string.custom_form_hint), fontSize = 13.sp, color = TextMuted)
                                OutlinedTextField(
                                    customForm,
                                    { customForm = it.take(40) },
                                    label = { Text(stringResource(R.string.custom_form_optional)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            MedicationStep.DOSAGE -> {
                                Text(
                                    stringResource(R.string.how_much_per_dose),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    amount,
                                    { amount = it.take(70); changeSchedules(schedules) },
                                    label = { Text(stringResource(R.string.amount)) },
                                    placeholder = { Text(stringResource(R.string.e_g_500)) },
                                    isError = validationVisible && amount.isBlank(),
                                    supportingText = {
                                        if (validationVisible && amount.isBlank()) Text(
                                            stringResource(R.string.amount_required)
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(R.string.unit),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp)),
                                ) {
                                    UnitModeSegment(
                                        stringResource(R.string.unit_list_mode),
                                        active = !customUnitMode,
                                        modifier = Modifier.weight(1f),
                                        onClick = ::useListedUnit,
                                    )
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(PanelBorder)
                                    )
                                    UnitModeSegment(
                                        stringResource(R.string.unit_custom_mode),
                                        active = customUnitMode,
                                        modifier = Modifier.weight(1f),
                                        onClick = ::useCustomUnit,
                                    )
                                }
                                if (customUnitMode) {
                                    OutlinedTextField(
                                        customUnitText,
                                        {
                                            customUnitText = it.take(40); quantity = customUnitText; automaticUnit =
                                            false
                                        },
                                        label = { Text(stringResource(R.string.unit_custom_name)) },
                                        placeholder = { Text(stringResource(R.string.unit_custom_placeholder)) },
                                        isError = validationVisible && quantity.isBlank() && !unitOptional,
                                        supportingText = {
                                            if (validationVisible && quantity.isBlank() && !unitOptional) Text(
                                                stringResource(R.string.unit_custom_required)
                                            ) else Text(stringResource(R.string.unit_custom_hint))
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DoseUnits.common.chunked(3)
                                            .forEach { rowUnits ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    rowUnits.forEach { unit ->
                                                        UnitChip(
                                                            localizedUnit(context, unit),
                                                            selected = quantity == unit,
                                                            modifier = Modifier.weight(1f),
                                                        ) { chooseUnit(unit) }
                                                    }
                                                    repeat(3 - rowUnits.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                    }
                                    if (validationVisible && quantity.isBlank() && !unitOptional) Text(
                                        stringResource(R.string.unit_required),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp,
                                    )
                                }
                            }

                            MedicationStep.SCHEDULE -> {
                                Text(
                                    stringResource(R.string.when_should_dose_remind_you),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                schedules.forEachIndexed { index, rule ->
                                    ScheduleEditor(
                                        rule,
                                        index + 1,
                                        validationVisible,
                                        amount,
                                        quantity,
                                        onChange = { updated ->
                                            changeSchedules(
                                                schedules.toMutableList()
                                                    .apply { set(index, updated.copy(immediateAt = null)) })
                                        },
                                        onRemove = if (schedules.size > 1) ({
                                            changeSchedules(schedules.filterIndexed { i, _ -> i != index })
                                        }) else null
                                    )
                                }
                                OutlinedButton(
                                    onClick = { changeSchedules(schedules + DoseSchedule()) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        null
                                    ); Text(stringResource(R.string.add_another_schedule))
                                }
                            }

                            MedicationStep.OVERLAPS -> {
                                OverlapsStep(
                                    schedules,
                                    amount,
                                    quantity,
                                    attention = conflictAttention || validationVisible,
                                    expandedKey = expandedConflict,
                                    onExpand = { expandedConflict = it },
                                    onResolve = { group, winner ->
                                        schedules = DoseScheduleConflicts.resolveGroup(schedules, winner, group.members)
                                        expandedConflict = null
                                    },
                                )
                            }

                            MedicationStep.DETAILS -> {
                                Text(
                                    stringResource(R.string.review_finish),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(color = AccentPanel, shape = RoundedCornerShape(16.dp)) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(name, fontWeight = FontWeight.Bold)
                                        Text((if (form == MedicationForm.OTHER) customForm.ifBlank { stringResource(R.string.other) } else form.localizedLabel(
                                            context
                                        )) + " · $amount ${localizedUnit(context, quantity)}", fontSize = 14.sp)
                                        schedules.forEach {
                                            Text(
                                                scheduleSummary(context, it, amount, quantity),
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.add_instructions),
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(checked = addInstructions, onCheckedChange = { addInstructions = it })
                                }
                                if (addInstructions) OutlinedTextField(
                                    note,
                                    { note = it.take(140) },
                                    label = { Text(stringResource(R.string.instructions)) },
                                    placeholder = { Text(stringResource(R.string.e_g_take_after_a_meal)) },
                                    isError = validationVisible && note.isBlank(),
                                    supportingText = { if (validationVisible && note.isBlank()) Text(stringResource(R.string.instructions_required)) },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (original != null && step == MedicationStep.NAME) OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .heightIn(min = 51.dp),
                        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.delete_medication), fontWeight = FontWeight.Bold) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step != MedicationStep.NAME) OutlinedButton(
                            onClick = ::previous,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 51.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(stringResource(R.string.back), fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = ::advance,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 51.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                if (step == MedicationStep.DETAILS) stringResource(R.string.save) else stringResource(
                                    R.string.continue_action
                                ), fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    colorPickerInitial?.let { initial ->
        ColorPicker(
            initial,
            onDismiss = { colorPickerInitial = null },
            onChoose = { color = it; colorPickerInitial = null })
    }
    if (showDeleteConfirm && original != null) AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text(stringResource(R.string.delete_named, original.name)) },
        text = { Text(stringResource(R.string.delete_medication_hint)) },
        confirmButton = {
            TextButton(onClick = { onDelete(original) }) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                showDeleteConfirm = false
            }) { Text(stringResource(R.string.cancel)) }
        })
}

@Composable
private fun ScheduleEditor(
    rule: DoseSchedule,
    number: Int,
    showValidation: Boolean,
    defaultAmount: String,
    quantity: String,
    onChange: (DoseSchedule) -> Unit,
    onRemove: (() -> Unit)?
) {
    val context = LocalContext.current
    val issue = if (showValidation) rule.validationIssue() else null
    val errorColor = MaterialTheme.colorScheme.error
    fun pickDate(
        initial: String?,
        onPicked: (String) -> Unit
    ) {
        val date = initial?.let(LocalDate::parse) ?: LocalDate.now()
        DatePickerDialog(
            context,
            AppAppearance.dialogTheme(context),
            { _, year, month, day ->
                onPicked(
                    LocalDate.of(year, month + 1, day)
                        .toString()
                )
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    fun pickTime(
        index: Int?,
        initial: String = "12:00"
    ) {
        val time = LocalTime.parse(initial)
        TimePickerDialog(context, AppAppearance.dialogTheme(context), { _, hour, minute ->
            val formatted = "%02d:%02d".format(Locale.US, hour, minute)
            onChange(
                rule.copy(
                    times = if (index == null) rule.times + formatted else rule.times.toMutableList()
                        .apply { set(index, formatted) })
            )
        }, time.hour, time.minute, true).show()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = SoftPanel, border = BorderStroke(1.dp, PanelBorder)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.schedule_number, number),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (onRemove != null) IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        stringResource(R.string.remove_schedule, number),
                        tint = Peach
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleKind.entries.chunked(2)
                    .forEach { rowKinds ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowKinds.forEach { kind ->
                                UnitChip(
                                    kind.localizedLabel(context),
                                    selected = rule.kind == kind,
                                    modifier = Modifier.weight(1f),
                                ) { onChange(rule.copy(kind = kind)) }
                            }
                        }
                    }
            }
            when (rule.kind) {
                ScheduleKind.SPECIFIC_DATES -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rule.dates.sorted()
                            .forEach { date ->
                                InputChip(
                                    selected = false,
                                    onClick = { pickDate(date) { picked -> onChange(rule.copy(dates = (rule.dates - date + picked).distinct())) } },
                                    label = { Text(localizedDate(context, date)) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            stringResource(R.string.remove_date, date),
                                            Modifier
                                                .size(20.dp)
                                                .clickable { onChange(rule.copy(dates = rule.dates - date)) })
                                    })
                            }
                    }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = if (issue?.field == ScheduleField.DATES) errorColor else Violet),
                        onClick = { pickDate(null) { onChange(rule.copy(dates = (rule.dates + it).distinct())) } }) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            null
                        ); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_date))
                    }
                    ScheduleError(issue, ScheduleField.DATES)
                }

                ScheduleKind.WEEKDAYS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..7).chunked(4)
                            .forEach { rowDays ->
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    rowDays.forEach { day ->
                                        UnitChip(
                                            DayOfWeek.of(day)
                                                .getDisplayName(TextStyle.SHORT, AppLanguages.locale(context)),
                                            selected = day in rule.weekDays,
                                            modifier = Modifier.weight(1f),
                                            borderColor = if (issue?.field == ScheduleField.WEEKDAYS) errorColor else Violet,
                                        ) { onChange(rule.copy(weekDays = if (day in rule.weekDays) rule.weekDays - day else rule.weekDays + day)) }
                                    }
                                    repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                    }
                    ScheduleError(issue, ScheduleField.WEEKDAYS)
                }

                ScheduleKind.MONTH_DAYS -> {
                    (1..31).toList()
                        .chunked(7)
                        .forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { day ->
                                    val selected = day in rule.monthDays
                                    val dayDescriptionLocalization = stringResource(
                                        R.string.day_description,
                                        day,
                                        if (selected) stringResource(R.string.selected_suffix) else ""
                                    )
                                    Surface(
                                        onClick = { onChange(rule.copy(monthDays = if (selected) rule.monthDays - day else rule.monthDays + day)) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .semantics {
                                                contentDescription = dayDescriptionLocalization
                                            },
                                        shape = RoundedCornerShape(8.dp), color = if (selected) Violet else Panel,
                                        border = BorderStroke(
                                            1.dp,
                                            if (issue?.field == ScheduleField.MONTH_DAYS) errorColor else PanelBorder
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "$day",
                                                fontSize = 12.sp,
                                                color = if (selected) OnAccent else TextPrimary
                                            )
                                        }
                                    }
                                }
                                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    ScheduleError(issue, ScheduleField.MONTH_DAYS)
                    Text(stringResource(R.string.missing_month_days_hint), fontSize = 12.sp, color = TextMuted)
                }

                ScheduleKind.DAILY -> Unit
            }
            if (rule.kind != ScheduleKind.SPECIFIC_DATES) {
                HorizontalDivider(color = PanelBorder)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBound(
                        stringResource(R.string.from),
                        rule.from,
                        stringResource(R.string.default_date, localizedDate(context, rule.createdOn)),
                        onPick = { pickDate(rule.from ?: rule.createdOn) { onChange(rule.copy(from = it)) } },
                        onClear = {
                            onChange(
                                rule.copy(
                                    from = null,
                                    createdOn = LocalDate.now()
                                        .toString()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        error = issue?.takeIf { it.field in listOf(ScheduleField.START, ScheduleField.BOUNDS) }
                            ?.let { localizedValidationError(context, it.message) })
                    DateBound(
                        stringResource(R.string.until),
                        rule.until,
                        stringResource(R.string.no_end_date),
                        onPick = { pickDate(rule.until ?: rule.from) { onChange(rule.copy(until = it)) } },
                        onClear = { onChange(rule.copy(until = null)) },
                        modifier = Modifier.weight(1f),
                        error = issue?.takeIf { it.field in listOf(ScheduleField.END, ScheduleField.BOUNDS) }
                            ?.let { localizedValidationError(context, it.message) })
                }
            }
            HorizontalDivider(color = PanelBorder)
            Text(
                stringResource(R.string.times),
                color = if (issue?.field == ScheduleField.TIMES) errorColor else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.times.forEachIndexed { index, time ->
                    InputChip(
                        selected = false,
                        border = BorderStroke(
                            1.dp,
                            if (issue?.field == ScheduleField.TIMES) errorColor else PanelBorder
                        ),
                        onClick = { pickTime(index, time) },
                        label = { Text(time) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (rule.times.size > 1) Icon(
                                Icons.Rounded.Close,
                                stringResource(R.string.remove_time, time),
                                Modifier
                                    .size(20.dp)
                                    .clickable {
                                        onChange(rule.copy(times = rule.times.filterIndexed { i, _ -> i != index }))
                                    })
                        })
                }
            }
            ScheduleError(issue, ScheduleField.TIMES)
            TextButton(onClick = { pickTime(null) }) {
                Icon(
                    Icons.Rounded.Add,
                    null
                ); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_time))
            }
            if (rule.amount == null) {
                TextButton(onClick = { onChange(rule.copy(amount = defaultAmount)) }) {
                    Icon(Icons.Rounded.Medication, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.set_schedule_amount))
                }
            } else {
                OutlinedTextField(
                    value = rule.amount,
                    onValueChange = { onChange(rule.copy(amount = it.take(70))) },
                    label = { Text(stringResource(R.string.amount) + " · " + localizedUnit(context, quantity)) },
                    isError = issue?.field == ScheduleField.AMOUNT,
                    supportingText = { ScheduleError(issue, ScheduleField.AMOUNT) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onChange(rule.copy(amount = null)) }) {
                    Text(
                        stringResource(
                            R.string.default_schedule_amount,
                            "$defaultAmount ${localizedUnit(context, quantity)}"
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleError(
    issue: ScheduleIssue?,
    field: ScheduleField
) {
    if (issue?.field == field) Text(
        localizedValidationError(LocalContext.current, issue.message),
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp
    )
}

/** A bound rendered as an outlined text field: floating label on the border, the value row is the tap target. */
@Composable
private fun DateBound(
    label: String,
    value: String?,
    fallback: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    val context = LocalContext.current
    val border = if (error != null) MaterialTheme.colorScheme.error else PanelBorder
    val labelColor = if (error != null) MaterialTheme.colorScheme.error else TextMuted
    Column(modifier) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .border(BorderStroke(1.dp, border), RoundedCornerShape(15.dp))
                    .clickable(onClick = onPick)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value?.let { localizedDate(context, it) } ?: fallback,
                    fontSize = 15.sp,
                    fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (value != null) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (value != null) IconButton(onClick = onClear) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.clear_bound, label),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(Modifier.offset(x = 11.dp, y = (-8).dp)) {
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                    // The caption masks the border behind it with the panel's own color.
                    modifier = Modifier
                        .background(SoftPanel)
                        .padding(horizontal = 6.dp),
                )
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

internal fun scheduleSummary(
    context: Context,
    rule: DoseSchedule,
    defaultAmount: String? = null,
    quantity: String = ""
): String {
    val dates = when (rule.kind) {
        ScheduleKind.DAILY -> context.getString(R.string.every_day)
        ScheduleKind.SPECIFIC_DATES -> rule.dates.sorted()
            .joinToString(", ") { localizedDate(context, it) }

        ScheduleKind.WEEKDAYS -> rule.weekDays.sorted()
            .joinToString(", ") {
                DayOfWeek.of(it)
                    .getDisplayName(TextStyle.SHORT, AppLanguages.locale(context))
            }

        ScheduleKind.MONTH_DAYS -> context.getString(
            R.string.monthly_days,
            rule.monthDays.sorted()
                .joinToString(", ")
        )
    }
    val bounds = if (rule.kind == ScheduleKind.SPECIFIC_DATES) "" else context.getString(
        R.string.from_date,
        localizedDate(context, rule.from ?: rule.createdOn)
    ) + (rule.until?.let { context.getString(R.string.until_date, localizedDate(context, it)) } ?: "")
    val dose =
        defaultAmount?.let { "${rule.amount ?: it} ${localizedUnit(context, quantity)} · " }
            .orEmpty()
    return "$dose$dates · ${rule.times.joinToString(", ")}$bounds"
}

/** Localized long date (full month name) used by the coincidence cards: «24 октября 2026». */
internal fun localizedLongDate(
    context: Context,
    value: String
): String = runCatching {
    LocalDate.parse(value)
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", AppLanguages.locale(context)))
}.getOrDefault(value)

/** One coincidence pair shares the choice between exactly these two schedules; dose text is reused everywhere. */
private fun doseLabel(
    context: Context,
    rule: DoseSchedule,
    amount: String,
    quantity: String
): String = "${rule.amount ?: amount} ${localizedUnit(context, quantity)}"

/** The optional screen after schedule editing: it materialises only while coincidences exist. */
@Composable
private fun OverlapsStep(
    schedules: List<DoseSchedule>,
    amount: String,
    quantity: String,
    attention: Boolean,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
    onResolve: (group: DoseScheduleConflicts.Coincidence, winner: Int) -> Unit,
) {
    val coincidences = remember(schedules, amount) { DoseScheduleConflicts.coincidences(schedules, amount.trim()) }
    if (coincidences.isEmpty()) return
    val resolvedCount = coincidences.count { it.resolved }
    Text(
        stringResource(R.string.dose_overlap_found, coincidences.size),
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { resolvedCount.toFloat() / coincidences.size },
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = GreenAction,
            trackColor = AmberBorder,
        )
        Text(
            stringResource(R.string.dose_overlap_progress, resolvedCount, coincidences.size),
            color = AmberText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text(
        stringResource(R.string.dose_overlap_choose_desc),
        color = TextMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        coincidences.forEach { group ->
            ConflictItem(
                schedules,
                amount,
                quantity,
                group,
                expanded = expandedKey == group.key,
                onToggle = { onExpand(if (expandedKey == group.key) null else group.key) },
                onResolve = onResolve,
            )
        }
    }
    if (attention && coincidences.any { !it.resolved }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                Icons.Rounded.Warning,
                null,
                Modifier.size(15.dp),
                tint = AmberIcon
            )
            Text(
                stringResource(R.string.dose_overlap_pick_all),
                color = AmberText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConflictItem(
    schedules: List<DoseSchedule>,
    amount: String,
    quantity: String,
    group: DoseScheduleConflicts.Coincidence,
    expanded: Boolean,
    onToggle: () -> Unit,
    onResolve: (group: DoseScheduleConflicts.Coincidence, winner: Int) -> Unit,
) {
    val context = LocalContext.current
    val resolved = group.resolved
    val winner = group.members.maxBy { schedules[it].priority }
    val headline = "${localizedLongDate(context, group.date.toString())} · ${group.times.joinToString(" · ")} · " +
            group.members.joinToString(" / ") { index -> doseLabel(context, schedules[index], amount, quantity) }
    Surface(
        onClick = onToggle,
        color = if (resolved) GreenPanel else AmberPanel,
        border = BorderStroke(1.dp, if (resolved) GreenBorder else AmberBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    Modifier.size(16.dp),
                    tint = AmberIcon
                )
                Text(
                    headline,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ConflictStatus(resolved)
                Icon(
                    Icons.Rounded.ExpandMore,
                    null,
                    Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = if (resolved) GreenMuted else AmberIcon,
                )
            }
            if (resolved) {
                Text(
                    "${
                        stringResource(
                            R.string.dose_overlap_priority_dose,
                            doseLabel(context, schedules[winner], amount, quantity)
                        )
                    } " +
                            "(${stringResource(R.string.schedule_number, winner + 1)})",
                    color = GreenMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (expanded) ExpandedConflict(
                schedules, amount, quantity, group, onResolve = onResolve,
            )
        }
    }
}

/** Collapsed item state: a green pill when the priority is chosen, an amber one while it needs a choice. */
@Composable
private fun ConflictStatus(resolved: Boolean) {
    Surface(
        color = Panel,
        border = BorderStroke(1.dp, if (resolved) GreenBorder else AmberBorder),
        shape = RoundedCornerShape(50.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (resolved) Icons.Rounded.Check else Icons.Rounded.Warning,
                null,
                Modifier.size(10.dp),
                tint = if (resolved) GreenMuted else AmberIcon
            )
            Text(
                stringResource(
                    if (resolved) R.string.dose_overlap_resolved_tag else R.string.dose_overlap_pending_tag
                ),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (resolved) GreenText else AmberText,
            )
        }
    }
}

/** Expanded body: the overlap moment, one radio card per participating schedule, live preview, apply action. */
@Composable
private fun ExpandedConflict(
    schedules: List<DoseSchedule>,
    amount: String,
    quantity: String,
    group: DoseScheduleConflicts.Coincidence,
    onResolve: (group: DoseScheduleConflicts.Coincidence, winner: Int) -> Unit,
) {
    val context = LocalContext.current
    val resolved = group.resolved
    val winner = group.members.maxBy { schedules[it].priority }
    var pending by remember(group) { mutableStateOf(if (resolved) winner else null) }
    HorizontalDivider(color = AmberBorder)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Rounded.CalendarMonth,
            null,
            Modifier.size(13.dp),
            tint = TextMuted
        )
        Text(
            localizedLongDate(context, group.date.toString()),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text("·", color = TextMuted, fontSize = 12.5.sp)
        Icon(
            Icons.Rounded.AccessTime,
            null,
            Modifier.size(13.dp),
            tint = TextMuted
        )
        Text(
            group.times.joinToString(" · "),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
    }
    // One grid keeps every overlapping dose visible; the choice stays a single radio group.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.members.chunked(2)
            .forEach { rowMembers ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowMembers.forEach { index ->
                        val rule = schedules[index]
                        Surface(
                            onClick = { pending = index },
                            color = if (pending == index) AccentPanel else SoftPanel,
                            border = BorderStroke(
                                if (pending == index) 1.5.dp else 1.dp,
                                if (pending == index) Violet else PanelBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    RadioButtonCircle(pending == index)
                                    Text(
                                        doseLabel(context, rule, amount, quantity),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (pending == index) Violet else TextPrimary,
                                    )
                                }
                                Text(
                                    stringResource(R.string.schedule_number, index + 1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    scheduleSummary(context, rule),
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                    if (rowMembers.size < 2) Spacer(Modifier.weight(1f))
                }
            }
    }
    Surface(
        color = SoftPanel,
        border = BorderStroke(1.dp, PanelBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    null,
                    Modifier.size(11.dp),
                    tint = Violet
                )
                Text(
                    stringResource(R.string.dose_overlap_what_happens),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Violet
                )
            }
            Text(
                pending?.let { index ->
                    stringResource(
                        R.string.dose_overlap_result,
                        doseLabel(context, schedules[index], amount, quantity)
                    )
                } ?: stringResource(R.string.dose_overlap_result_pending),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.dose_overlap_result_keep),
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
    }
    Button(
        enabled = pending != null,
        onClick = { pending?.let { onResolve(group, it) } },
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Text(stringResource(R.string.dose_overlap_apply), fontWeight = FontWeight.Bold)
    }
}

/** Single-selection radio circle of the option cards: empty ring stays neutral, chosen ring turns violet. */
@Composable
private fun RadioButtonCircle(selected: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(
                BorderStroke(2.dp, if (selected) Violet else RadioButtonBorder),
                CircleShape
            )
            .background(Panel),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Violet)
        )
    }
}

/** Soft amber for warnings, calm green for a settled coincidence — same hues in both themes. */
private val AmberPanel: Color @Composable get() = themeColor(0xFFFDF4DC, 0xFF3A3118)
private val AmberBorder: Color @Composable get() = themeColor(0xFFF0E2B8, 0xFF5A4C22)
private val AmberText: Color @Composable get() = themeColor(0xFF7A600E, 0xFFF2CE74)
private val AmberMuted: Color @Composable get() = themeColor(0xFFA3842E, 0xFFD9BC7A)
private val AmberIcon: Color @Composable get() = themeColor(0xFFB8830F, 0xFFEBCC6B)
private val GreenPanel: Color @Composable get() = themeColor(0xFFEFF9F3, 0xFF1E3A2C)
private val GreenBorder: Color @Composable get() = themeColor(0xFFD4EBDD, 0xFF2C5240)
private val GreenText: Color @Composable get() = themeColor(0xFF1E6B45, 0xFF8FE3BC)
private val GreenMuted: Color @Composable get() = themeColor(0xFF3E8562, 0xFF7BD5B5)
private val GreenAction: Color @Composable get() = themeColor(0xFF27A468, 0xFF2FA468)
private val RadioButtonBorder: Color @Composable get() = themeColor(0xFFC9CDDD, 0xFF4A4B60)

/** One half of the unit-mode segmented control; the active side carries the soft purple fill. */
@Composable
private fun UnitModeSegment(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxHeight()
            .background(if (active) AccentPanel else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) Violet else TextMuted,
        )
    }
}

/** A selectable unit cell of the fixed grid: equal size, single selection, purple when chosen. */
@Composable
private fun UnitChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = Violet,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) AccentPanel else Color.Transparent)
            .border(1.5.dp, if (selected) borderColor else PanelBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Violet else TextPrimary,
        )
    }
}
