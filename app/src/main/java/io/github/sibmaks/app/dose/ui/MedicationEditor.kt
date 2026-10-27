package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        MedicationEditorContent(original, onDismiss, onSave, onDelete)
    }
}

@Composable
private fun MedicationEditorContent(
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
    val unitColumns = if (LocalDensity.current.fontScale >= 1.5f) 2 else 3
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
        if (step == MedicationStep.NAME) onDismiss() else step = MedicationEditorFlow.previous(step, form, hasOverlaps)
        showValidation = false
    }

    fun amountError(): String? = when (MedicationEditorFlow.amountIssue(amount)) {
        MedicationAmountIssue.REQUIRED -> resources.getString(R.string.amount_required)
        MedicationAmountIssue.NON_POSITIVE -> resources.getString(R.string.amount_positive)
        null -> null
    }

    fun stepError(candidate: MedicationStep): String? = when (candidate) {
        MedicationStep.NAME -> if (name.isBlank()) resources.getString(R.string.name_required) else null
        MedicationStep.DOSAGE -> when {
            amountError() != null -> amountError()
            quantity.isBlank() && !unitOptional -> resources.getString(R.string.unit_required)
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
        showValidation = true
        if (stepError(step) != null) return
        step = MedicationEditorFlow.next(step, form, hasOverlaps)
        showValidation = false
    }

    val forwardBlocked = stepError(step) != null || step == MedicationStep.DETAILS
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    BackHandler(onBack = ::previous)
    // These controllers must belong to the dialog's view, not the activity behind it.
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun finishInput() {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }
    LaunchedEffect(step) { finishInput() }
    val onSwipe by rememberUpdatedState<(Boolean) -> Unit> { forward ->
        if (forward || step != MedicationStep.NAME) {
            finishInput()
            if (!forward) previous()
            else if (forwardBlocked) showValidation = true
            else advance()
        }
    }
    // A full-size window lets the card grow again after IME resize on older Android.
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DoseSpacing.shellMargin)
                .fillMaxHeight(0.94f),
            shape = DoseShapes.shell, color = Panel,
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = DoseSpacing.shellInset, end = DoseSpacing.controlGap, top = DoseSpacing.controlGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (original == null) stringResource(R.string.add_medication) else stringResource(R.string.edit_medication),
                        style = DoseText.shellTitle,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (original != null && step != MedicationStep.DETAILS) TextButton(onClick = {
                        finishInput(); save()
                    }) {
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
                        .padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)
                ) {
                    Text(
                        stringResource(
                            R.string.editor_step,
                            position + 1,
                            steps.size,
                            step.localizedTitle(context)
                        ), color = Violet, fontWeight = FontWeight.Bold, style = DoseText.supporting
                    )
                    LinearProgressIndicator(
                        progress = { (position + 1f) / steps.size },
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(step, swipeThreshold) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragCancel = { distance = 0f },
                                onHorizontalDrag = { change, delta ->
                                    change.consume()
                                    distance += delta
                                },
                                onDragEnd = {
                                    if (kotlin.math.abs(distance) > swipeThreshold) {
                                        onSwipe(distance < 0)
                                    }
                                },
                            )
                        },
                ) {
                    // Only the current step owns input. Retaining offscreen editable pages lets an
                    // outgoing focused field pull the layout back while the keyboard is closing.
                    val pageStep = step
                    val scroll = rememberScrollState()
                    val validationVisible = showValidation
                    LaunchedEffect(step) { scroll.scrollTo(0) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(DoseSpacing.cardInset)
                    ) {
                        when (pageStep) {
                            MedicationStep.NAME -> {
                                OutlinedTextField(
                                    name,
                                    { name = it.take(70); showSuggestions = true; suggestedForm = null },
                                    isError = validationVisible && name.isBlank(),
                                    supportingText = { if (validationVisible && name.isBlank()) Text(stringResource(R.string.name_required)) },
                                    shape = DoseShapes.field,
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
                                        style = DoseText.supporting
                                    )
                                    suggestions.forEach { suggestion ->
                                        Surface(
                                            onClick = {
                                                name = suggestion.name; chooseForm(suggestion.form); customForm =
                                                if (suggestion.customForm.isBlank()) "" else suggestion.localizedForm(
                                                    context
                                                )
                                                suggestedForm = suggestion.localizedForm(context); showSuggestions =
                                                false; finishInput()
                                            },
                                            shape = DoseShapes.button,
                                            color = AccentPanel,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                Modifier.padding(DoseSpacing.small),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap)
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
                                                        style = DoseText.body
                                                    )
                                                    Text(
                                                        suggestion.localizedForm(context) + if (suggestion.saved) stringResource(
                                                            R.string.previously_entered
                                                        ) else "", color = TextMuted, style = DoseText.caption
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
                                    ), style = DoseText.supporting, color = Violet
                                )
                                Text(stringResource(R.string.color), fontWeight = FontWeight.Bold, style = DoseText.itemTitle)
                                FlowRow(
                                    modifier = Modifier.selectableGroup(),
                                    horizontalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap),
                                    verticalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap)
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
                                                ""
                                            )
                                            Box(
                                                Modifier
                                                    .size(44.dp)
                                                    .clip(DoseShapes.choice)
                                                    .background(foreground)
                                                    .border(
                                                        if (selected) 3.dp else 1.dp,
                                                        if (selected) TextPrimary else PanelBorder,
                                                        DoseShapes.choice
                                                    )
                                                    .selectionControl(selected) { color = choice }
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
                                    ); Spacer(Modifier.width(DoseSpacing.compactGap)); Text(stringResource(R.string.pick_a_custom_color))
                                }
                            }

                            MedicationStep.FORM -> {
                                Text(
                                    stringResource(R.string.choose_form, name),
                                    style = DoseText.contextTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                if (suggestedForm != null) Text(
                                    stringResource(
                                        R.string.change_suggested_form,
                                        suggestedForm.orEmpty()
                                    ), color = TextMuted, style = DoseText.supporting
                                )
                                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DoseSpacing.cardInset)) {
                                    localizedForms.forEach { choice ->
                                        Surface(
                                            shape = DoseShapes.button,
                                            color = if (form == choice) AccentPanel else SoftPanel,
                                            modifier = Modifier.fillMaxWidth().selectionControl(form == choice) { chooseForm(choice) }
                                        ) {
                                            Row(
                                                Modifier.padding(DoseSpacing.small),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(DoseSpacing.small)
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

                            }

                            MedicationStep.OTHER_FORM -> {
                                Text(
                                    stringResource(R.string.give_this_form_a_name),
                                    style = DoseText.sectionTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(stringResource(R.string.custom_form_hint), style = DoseText.supporting, color = TextMuted)
                                OutlinedTextField(
                                    customForm,
                                    { customForm = it.take(40) },
                                    shape = DoseShapes.field,
                                    label = { Text(stringResource(R.string.custom_form_optional)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            MedicationStep.DOSAGE -> {
                                val visibleAmountError = if (validationVisible) amountError() else null
                                Text(
                                    stringResource(R.string.how_much_per_dose),
                                    style = DoseText.sectionTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    amount,
                                    { amount = it.take(70); changeSchedules(schedules) },
                                    shape = DoseShapes.field,
                                    label = { Text(stringResource(R.string.amount)) },
                                    placeholder = { Text(stringResource(R.string.e_g_500)) },
                                    isError = visibleAmountError != null,
                                    supportingText = {
                                        visibleAmountError?.let { Text(it) }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(R.string.unit),
                                    fontWeight = FontWeight.Bold,
                                    style = DoseText.itemTitle
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = DoseDimensions.segmentedControl)
                                        .height(IntrinsicSize.Min)
                                        .clip(DoseShapes.button)
                                        .border(DoseDimensions.border, PanelBorder, DoseShapes.button)
                                        .selectableGroup(),
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
                                        shape = DoseShapes.field,
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
                                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
                                        DoseUnits.common.chunked(unitColumns)
                                            .forEach { rowUnits ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
                                                    rowUnits.forEach { unit ->
                                                        UnitChip(
                                                            localizedUnit(context, unit),
                                                            selected = quantity == unit,
                                                            modifier = Modifier.weight(1f),
                                                        ) { chooseUnit(unit) }
                                                    }
                                                    repeat(unitColumns - rowUnits.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                    }
                                    if (validationVisible && quantity.isBlank() && !unitOptional) Text(
                                        stringResource(R.string.unit_required),
                                        color = MaterialTheme.colorScheme.error,
                                        style = DoseText.supporting,
                                    )
                                }
                            }

                            MedicationStep.SCHEDULE -> {
                                Text(
                                    stringResource(R.string.when_should_dose_remind_you),
                                    style = DoseText.sectionTitle,
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
                                    style = DoseText.sectionTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(color = AccentPanel, shape = DoseShapes.tile) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(DoseSpacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(DoseSpacing.compactGap)
                                    ) {
                                        Text(name, fontWeight = FontWeight.Bold)
                                        Text((if (form == MedicationForm.OTHER) customForm.ifBlank { stringResource(R.string.other) } else form.localizedLabel(
                                            context
                                        )) + " · $amount ${localizedUnit(context, quantity)}", style = DoseText.body)
                                        schedules.forEach {
                                            Text(
                                                scheduleSummary(context, it, amount, quantity),
                                                color = TextMuted,
                                                style = DoseText.caption
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
                                    LabeledSwitch(
                                        checked = addInstructions, onCheckedChange = { addInstructions = it },
                                        label = stringResource(R.string.add_instructions),
                                    )
                                }
                                if (addInstructions) OutlinedTextField(
                                    note,
                                    { note = it.take(140) },
                                    shape = DoseShapes.field,
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
                        .padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.small)
                ) {
                    if (original != null && step == MedicationStep.NAME) OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = DoseSpacing.extraSmall)
                            .heightIn(min = DoseDimensions.dialogAction),
                        shape = DoseShapes.button, border = BorderStroke(DoseDimensions.border, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.delete_medication), fontWeight = FontWeight.Bold) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step != MedicationStep.NAME) OutlinedButton(
                            onClick = { finishInput(); previous() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = DoseDimensions.dialogAction),
                            shape = DoseShapes.button
                        ) { Text(stringResource(R.string.back), fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { finishInput(); advance() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = DoseDimensions.dialogAction),
                            shape = DoseShapes.button
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
    val weekdayColumns = if (LocalDensity.current.fontScale >= 1.5f) 2 else 4
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
    Surface(shape = DoseShapes.tile, color = SoftPanel, border = BorderStroke(DoseDimensions.border, PanelBorder)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(DoseSpacing.small), verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)
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
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
                ScheduleKind.entries.chunked(2)
                    .forEach { rowKinds ->
                        Row(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.compactGap)) {
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
                        ); Spacer(Modifier.width(DoseSpacing.compactGap)); Text(stringResource(R.string.add_date))
                    }
                    ScheduleError(issue, ScheduleField.DATES)
                }

                ScheduleKind.WEEKDAYS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..7).chunked(weekdayColumns)
                            .forEach { rowDays ->
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    rowDays.forEach { day ->
                                        UnitChip(
                                            DayOfWeek.of(day)
                                                .getDisplayName(TextStyle.SHORT, AppLanguages.locale(context)),
                                            selected = day in rule.weekDays,
                                            multiple = true,
                                            modifier = Modifier.weight(1f),
                                            borderColor = if (issue?.field == ScheduleField.WEEKDAYS) errorColor else Violet,
                                        ) { onChange(rule.copy(weekDays = if (day in rule.weekDays) rule.weekDays - day else rule.weekDays + day)) }
                                    }
                                    repeat(weekdayColumns - rowDays.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                    }
                    ScheduleError(issue, ScheduleField.WEEKDAYS)
                }

                ScheduleKind.MONTH_DAYS -> {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = ((maxWidth + 4.dp) / 52.dp).toInt().coerceIn(1, 7)
                        Column(verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
                            (1..31).toList()
                                .chunked(columns)
                                .forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.tiny)) {
                                        row.forEach { day ->
                                            val selected = day in rule.monthDays
                                            val dayDescriptionLocalization = stringResource(
                                                R.string.day_description,
                                                day,
                                                ""
                                            )
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = DoseDimensions.touchTarget)
                                                    .selectionControl(selected, multiple = true) {
                                                        onChange(rule.copy(monthDays = if (selected) rule.monthDays - day else rule.monthDays + day))
                                                    }
                                                    .semantics {
                                                        contentDescription = dayDescriptionLocalization
                                                    },
                                                shape = DoseShapes.badge, color = if (selected) Violet else Panel,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (issue?.field == ScheduleField.MONTH_DAYS) errorColor else PanelBorder
                                                )
                                            ) {
                                                Box(Modifier.padding(vertical = DoseSpacing.extraSmall), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "$day",
                                                        style = DoseText.caption,
                                                        color = if (selected) OnAccent else TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                        }
                    }
                    ScheduleError(issue, ScheduleField.MONTH_DAYS)
                    Text(stringResource(R.string.missing_month_days_hint), style = DoseText.caption, color = TextMuted)
                }

                ScheduleKind.DAILY -> Unit
            }
            if (rule.kind != ScheduleKind.SPECIFIC_DATES) {
                HorizontalDivider(color = PanelBorder)
                ResponsiveFields {
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
                style = DoseText.supporting,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.compactGap)) {
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
                ); Spacer(Modifier.width(DoseSpacing.compactGap)); Text(stringResource(R.string.add_time))
            }
            if (rule.amount == null) {
                TextButton(onClick = { onChange(rule.copy(amount = defaultAmount)) }) {
                    Icon(Icons.Rounded.Medication, null)
                    Spacer(Modifier.width(DoseSpacing.compactGap))
                    Text(stringResource(R.string.set_schedule_amount))
                }
            } else {
                OutlinedTextField(
                    value = rule.amount,
                    onValueChange = { onChange(rule.copy(amount = it.take(70))) },
                    shape = DoseShapes.field,
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
        style = DoseText.caption
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
    error: String? = null,
) {
    val context = LocalContext.current
    OutlinedPickerField(
        label = label,
        value = value?.let { localizedDate(context, it) }.orEmpty(),
        onClick = onPick,
        modifier = modifier,
        placeholder = fallback,
        icon = Icons.Rounded.CalendarMonth,
        onClear = onClear.takeIf { value != null },
        errorMessage = error,
        supportingError = true,
        containerColor = SoftPanel,
    )
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
        style = DoseText.sectionTitle,
        fontWeight = FontWeight.Bold
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)
    ) {
        LinearProgressIndicator(
            progress = { resolvedCount.toFloat() / coincidences.size },
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(DoseShapes.pill),
            color = GreenAction,
            trackColor = AmberBorder,
        )
        Text(
            stringResource(R.string.dose_overlap_progress, resolvedCount, coincidences.size),
            color = AmberText,
            style = DoseText.caption,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text(
        stringResource(R.string.dose_overlap_choose_desc),
        color = TextMuted,
        style = DoseText.caption,
    )
    Column(verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
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
                style = DoseText.caption,
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
        border = BorderStroke(DoseDimensions.border, if (resolved) GreenBorder else AmberBorder),
        shape = DoseShapes.tile,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DoseSpacing.small, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)
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
                    style = DoseText.supporting,
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
                    style = DoseText.label,
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
        border = BorderStroke(DoseDimensions.border, if (resolved) GreenBorder else AmberBorder),
        shape = DoseShapes.pill,
    ) {
        Row(
            Modifier.padding(horizontal = DoseSpacing.extraSmall, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DoseSpacing.tiny)
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
                style = DoseText.label,
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
        horizontalArrangement = Arrangement.spacedBy(DoseSpacing.compactGap)
    ) {
        Icon(
            Icons.Rounded.CalendarMonth,
            null,
            Modifier.size(13.dp),
            tint = TextMuted
        )
        Text(
            localizedLongDate(context, group.date.toString()),
            style = DoseText.supporting,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text("·", color = TextMuted, style = DoseText.supporting)
        Icon(
            Icons.Rounded.AccessTime,
            null,
            Modifier.size(13.dp),
            tint = TextMuted
        )
        Text(
            group.times.joinToString(" · "),
            style = DoseText.supporting,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
    }
    // One grid keeps every overlapping dose visible; the choice stays a single radio group.
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
        group.members.chunked(2)
            .forEach { rowMembers ->
                Row(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.extraSmall)) {
                    rowMembers.forEach { index ->
                        val rule = schedules[index]
                        Surface(
                            color = if (pending == index) AccentPanel else SoftPanel,
                            border = BorderStroke(
                                if (pending == index) DoseDimensions.selectionBorder else 1.dp,
                                if (pending == index) Violet else PanelBorder
                            ),
                            shape = DoseShapes.choice,
                            modifier = Modifier.weight(1f).selectionControl(pending == index) { pending = index },
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(DoseSpacing.controlGap),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    RadioButtonCircle(pending == index)
                                    Text(
                                        doseLabel(context, rule, amount, quantity),
                                        style = DoseText.contextTitle,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (pending == index) Violet else TextPrimary,
                                    )
                                }
                                Text(
                                    stringResource(R.string.schedule_number, index + 1),
                                    style = DoseText.label,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    scheduleSummary(context, rule),
                                    color = TextMuted,
                                    style = DoseText.label,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
        border = BorderStroke(DoseDimensions.border, PanelBorder),
        shape = DoseShapes.choice,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DoseSpacing.controlGap, vertical = DoseSpacing.extraSmall),
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
                    style = DoseText.label,
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
                style = DoseText.supporting,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.dose_overlap_result_keep),
                color = TextMuted,
                style = DoseText.label,
            )
        }
    }
    Button(
        enabled = pending != null,
        onClick = { pending?.let { onResolve(group, it) } },
        shape = DoseShapes.button,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DoseDimensions.touchTarget),
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
private val AmberPanel: Color @Composable get() = Semantic.warningContainer
private val AmberBorder: Color @Composable get() = Semantic.warningBorder
private val AmberText: Color @Composable get() = Semantic.warning
private val AmberIcon: Color @Composable get() = Semantic.warningIcon
private val GreenPanel: Color @Composable get() = Semantic.resolvedContainer
private val GreenBorder: Color @Composable get() = Semantic.resolvedBorder
private val GreenText: Color @Composable get() = Semantic.resolved
private val GreenMuted: Color @Composable get() = GreenText
private val GreenAction: Color @Composable get() = Semantic.resolvedProgress
private val RadioButtonBorder: Color @Composable get() = MaterialTheme.colorScheme.outline

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
            .selectionControl(active, onClick = onClick)
            .padding(horizontal = DoseSpacing.extraSmall, vertical = DoseSpacing.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = DoseText.body,
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
    multiple: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier
            .heightIn(min = DoseDimensions.touchTarget)
            .clip(DoseShapes.choice)
            .background(if (selected) AccentPanel else Color.Transparent)
            .border(DoseDimensions.selectionBorder, if (selected) borderColor else PanelBorder, DoseShapes.choice)
            .selectionControl(selected, multiple, onClick)
            .padding(horizontal = DoseSpacing.extraSmall, vertical = DoseSpacing.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = DoseText.body,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Violet else TextPrimary,
        )
    }
}
