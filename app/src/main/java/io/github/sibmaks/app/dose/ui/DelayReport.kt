package io.github.sibmaks.app.dose.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseLog
import io.github.sibmaks.app.dose.data.DoseReports
import io.github.sibmaks.app.dose.localization.AppLanguages
import java.text.NumberFormat
import java.time.LocalDate

@Composable
internal fun DelayReportCard(
    logs: List<DoseLog>,
    today: LocalDate
) {
    val context = LocalContext.current
    var days by remember { mutableStateOf<Int?>(7) }
    val report =
        remember(logs, days, today) { DoseReports.summarize(logs, days?.let { today.minusDays(it - 1L) }, today) }
    Card(
        shape = DoseShapes.card,
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(DoseDimensions.border, PanelBorder)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(17.dp), verticalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap)
        ) {
            Text(stringResource(R.string.time_to_take), style = DoseText.sectionTitle, fontWeight = FontWeight.ExtraBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(7, 30, null).forEach { period ->
                    FilterChip(
                        selected = days == period,
                        onClick = { days = period },
                        label = {
                            Text(period?.let { stringResource(R.string.period_days, it) }
                                ?: stringResource(R.string.all_time))
                        })
                }
            }
            Text(report.averageDelayMillis?.let { delayDuration(context, it) } ?: "—",
                color = Violet,
                style = DoseText.metric,
                fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.average_delay_count, report.count),
                style = DoseText.supporting,
                fontWeight = FontWeight.Bold
            )
            if (report.count > 0) {
                report.medications.forEach { med ->
                    HorizontalDivider(color = PanelBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap)) {
                        Column(Modifier.weight(1f)) {
                            Text(med.name, style = DoseText.body, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.taken_count, med.count),
                                style = DoseText.caption,
                                color = TextMuted
                            )
                        }
                        Text(
                            delayDuration(context, med.averageDelayMillis),
                            style = DoseText.body,
                            color = Violet,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (report.estimatedCount > 0) Text(
                stringResource(R.string.estimated_entries, report.estimatedCount),
                style = DoseText.caption,
                color = TextMuted
            )
        }
    }
}

internal fun delayDuration(
    context: Context,
    millis: Double
): String {
    if (millis == 0.0) return context.getString(R.string.duration_0_min)
    if (millis < 60_000) return context.getString(R.string.duration_1_min)
    if (millis < 3_600_000) return context.getString(
        R.string.duration_minutes,
        NumberFormat.getNumberInstance(AppLanguages.locale(context))
            .apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
            .format(millis / 60_000)
    )
    val minutes = (millis / 60_000).toLong()
    return context.getString(R.string.duration_hours, minutes / 60, minutes % 60)
}

/** Snooze lengths and delays are whole minutes; they reuse the existing duration strings. */
internal fun durationMinutes(
    context: Context,
    minutes: Long
): String = when {
    minutes <= 0L -> context.getString(R.string.duration_0_min)
    minutes < 60L -> context.getString(R.string.duration_minutes, minutes.toString())
    else -> context.getString(R.string.duration_hours, minutes / 60, minutes % 60)
}
