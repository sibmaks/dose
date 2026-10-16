package io.github.sibmaks.app.dose.localization

import android.content.Context
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.*

fun MedicationForm.localizedLabel(context: Context): String = context.getString(when (this) {
    MedicationForm.PILL -> R.string.pill
    MedicationForm.CAPSULE -> R.string.capsule
    MedicationForm.LIQUID -> R.string.liquid
    MedicationForm.SPRAY -> R.string.spray
    MedicationForm.DROPS -> R.string.drops
    MedicationForm.INJECTION -> R.string.injection
    MedicationForm.CREAM -> R.string.cream
    MedicationForm.OTHER -> R.string.other
    MedicationForm.INHALER -> R.string.inhaler
    MedicationForm.GEL -> R.string.gel
    MedicationForm.OINTMENT -> R.string.ointment
    MedicationForm.PATCH -> R.string.patch
    MedicationForm.POWDER -> R.string.powder
    MedicationForm.SUPPOSITORY -> R.string.suppository
})

fun ScheduleKind.localizedLabel(context: Context): String = context.getString(when (this) {
    ScheduleKind.SPECIFIC_DATES -> R.string.specific_dates
    ScheduleKind.WEEKDAYS -> R.string.days_of_week
    ScheduleKind.MONTH_DAYS -> R.string.days_of_month
    ScheduleKind.DAILY -> R.string.every_day
})

fun MedicationStep.localizedTitle(context: Context): String = context.getString(when (this) {
    MedicationStep.NAME -> R.string.name_color
    MedicationStep.FORM -> R.string.medication_form
    MedicationStep.OTHER_FORM -> R.string.describe_the_form
    MedicationStep.DOSAGE -> R.string.amount_unit
    MedicationStep.SCHEDULE -> R.string.due_dates_times
    MedicationStep.DETAILS -> R.string.final_details
})

fun Medication.localizedForm(context: Context): String = customForm.takeIf { form == MedicationForm.OTHER && it.isNotBlank() } ?: form.localizedLabel(context)
fun MedicationSuggestion.localizedForm(context: Context): String = when {
    !saved && customForm == "Inhaler" -> context.getString(R.string.inhaler)
    form == MedicationForm.OTHER && customForm.isNotBlank() -> customForm
    else -> form.localizedLabel(context)
}
fun Medication.localizedDose(context: Context): String = listOf(amount, localizedUnit(context, quantity)).filter { it.isNotBlank() }.joinToString(" ") + if (form == MedicationForm.OTHER) " · ${localizedForm(context)}" else ""

fun localizedValidationError(context: Context, error: String): String = when (error) {
    "Choose at least one valid time." -> context.getString(R.string.schedule_time_required)
    "Remove duplicate times in this schedule." -> context.getString(R.string.schedule_duplicate_times)
    "Choose at least one specific date." -> context.getString(R.string.schedule_date_required)
    "An immediate reminder must contain one date and time." -> context.getString(R.string.schedule_immediate_invalid)
    "Invalid schedule start." -> context.getString(R.string.schedule_start_invalid)
    "Choose valid start and end dates." -> context.getString(R.string.schedule_bounds_invalid)
    "The end date must be on or after the start date." -> context.getString(R.string.schedule_bounds_order)
    "Select at least one weekday." -> context.getString(R.string.schedule_weekday_required)
    "Select at least one day of the month." -> context.getString(R.string.schedule_monthday_required)
    else -> error
}

fun localizedColor(context: Context, color: String): String = when (color) {
    "violet" -> context.getString(R.string.violet)
    "mint" -> context.getString(R.string.mint)
    "peach" -> context.getString(R.string.peach)
    "blue" -> context.getString(R.string.blue)
    "rose" -> context.getString(R.string.rose)
    "amber" -> context.getString(R.string.amber)
    else -> color
}

fun localizedUnit(context: Context, unit: String): String = when (unit) {
    "mg" -> context.getString(R.string.unit_mg)
    "ml" -> context.getString(R.string.unit_ml)
    "g" -> context.getString(R.string.unit_g)
    "mcg" -> context.getString(R.string.unit_mcg)
    "IU" -> context.getString(R.string.unit_iu)
    "tablet" -> context.getString(R.string.unit_tablet)
    "capsule" -> context.getString(R.string.unit_capsule)
    "drop" -> context.getString(R.string.unit_drop)
    "spray" -> context.getString(R.string.unit_spray)
    "puff" -> context.getString(R.string.unit_puff)
    "patch" -> context.getString(R.string.patch).lowercase(AppLanguages.locale(context))
    "suppository" -> context.getString(R.string.suppository).lowercase(AppLanguages.locale(context))
    else -> unit
}

fun localizedDate(context: Context, value: String): String = runCatching {
    java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(AppLanguages.locale(context)))
}.getOrDefault(value)

fun dateHeading(context: Context, date: java.time.LocalDate): String {
    val locale = AppLanguages.locale(context)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEE MMMM d")
    return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}
