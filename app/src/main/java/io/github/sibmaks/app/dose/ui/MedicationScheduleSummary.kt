package io.github.sibmaks.app.dose.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseSchedule
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.data.ScheduleKind
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.localization.localizedDate
import io.github.sibmaks.app.dose.localization.localizedUnit
import java.time.DayOfWeek
import java.time.format.TextStyle

private data class ScheduleSummaryParts(
    val dose: String,
    val days: String,
    val times: String,
    val dates: String,
) {
    val cells get() = listOf(dose, days, times, dates)
}

private fun scheduleSummaryParts(
    context: Context,
    rule: DoseSchedule,
    defaultAmount: String? = null,
    quantity: String = "",
): ScheduleSummaryParts {
    val days = when (rule.kind) {
        ScheduleKind.DAILY -> context.getString(R.string.every_day)
        ScheduleKind.SPECIFIC_DATES -> ""
        ScheduleKind.WEEKDAYS -> rule.weekDays.sorted().joinToString(", ") {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, AppLanguages.locale(context))
        }
        ScheduleKind.MONTH_DAYS -> context.getString(R.string.monthly_days, rule.monthDays.sorted().joinToString(", "))
    }
    val dates = if (rule.kind == ScheduleKind.SPECIFIC_DATES) {
        rule.dates.sorted().joinToString(", ") { localizedDate(context, it) }
    } else {
        context.getString(R.string.from_date, localizedDate(context, rule.from ?: rule.createdOn)) +
            (rule.until?.let { context.getString(R.string.until_date, localizedDate(context, it)) } ?: "")
    }
    val dose = defaultAmount?.let {
        listOf(rule.amount ?: it, localizedUnit(context, quantity)).filter { part -> part.isNotBlank() }.joinToString(" ")
    }.orEmpty()
    return ScheduleSummaryParts(dose, days, rule.times.joinToString(", "), dates)
}

/** Keep the existing prose summaries for editor/detail screens, using the same localized fields. */
internal fun scheduleSummary(
    context: Context,
    rule: DoseSchedule,
    defaultAmount: String? = null,
    quantity: String = "",
): String {
    val parts = scheduleSummaryParts(context, rule, defaultAmount, quantity)
    val cells = if (rule.kind == ScheduleKind.SPECIFIC_DATES) listOf(parts.dose, parts.dates, parts.times) else parts.cells
    return cells.filter { it.isNotBlank() }.joinToString(" · ")
}

/** Shared column starts across schedules, with paired/stacked fields when text needs more room. */
@Composable
internal fun MedicationScheduleSummary(medication: Medication) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = DoseText.caption
    val rows = medication.schedules.map { scheduleSummaryParts(context, it, medication.amount, medication.quantity) }
    if (rows.isEmpty()) return
    val widths = remember(rows, measurer, style) {
        List(4) { column -> rows.maxOf { measurer.measure(it.cells[column], style, softWrap = false).size.width } }
    }
    val columns = widths.indices.filter { widths[it] > 0 }
    if (columns.isEmpty()) return
    val gap = DoseSpacing.extraSmall
    val gapPx = with(density) { gap.roundToPx() }
    val timeWidth = measurer.measure("00:00", style, softWrap = false).size.width

    @Composable
    fun cell(value: String, modifier: Modifier = Modifier) {
        if (value.isNotBlank()) Text(value, modifier = modifier, style = style, color = Violet)
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val lastColumn = columns.last()
        // Long date ranges can wrap in the last column; don't force the other columns to drift.
        val dateRoom = minOf(widths[lastColumn], (constraints.maxWidth * 0.4f).toInt())
        val tableFits = columns.dropLast(1).sumOf { widths[it] } + dateRoom + gapPx * (columns.size - 1) <= constraints.maxWidth
        val paired = constraints.maxWidth >= timeWidth * 2 + gapPx
        val leftWidth = minOf(maxOf(widths[0], widths[1]), (constraints.maxWidth - gapPx).coerceAtLeast(0) / 2)
        Column(verticalArrangement = Arrangement.spacedBy(if (tableFits) 5.dp else DoseSpacing.small)) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    when {
                        tableFits -> columns.forEach { column ->
                            val modifier = if (column == lastColumn) Modifier.weight(1f)
                            else Modifier.width(with(density) { widths[column].toDp() })
                            // An empty cell must still reserve its column in mixed schedule types.
                            Box(modifier) { cell(row.cells[column]) }
                        }
                        paired -> {
                            Column(Modifier.width(with(density) { leftWidth.toDp() }), verticalArrangement = Arrangement.spacedBy(DoseSpacing.tiny)) {
                                cell(row.dose)
                                cell(row.days)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DoseSpacing.tiny)) {
                                cell(row.times)
                                cell(row.dates)
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(DoseSpacing.tiny)) {
                            row.cells.forEach { cell(it) }
                        }
                    }
                }
            }
        }
    }
}
