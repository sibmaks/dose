package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.localization.*
import io.github.sibmaks.app.dose.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.data.*
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DelayReportCard(logs: List<DoseLog>, today: LocalDate) {
    val context = LocalContext.current
    var days by remember { mutableStateOf<Int?>(7) }
    val report = remember(logs, days, today) { DoseReports.summarize(logs, days?.let { today.minusDays(it - 1L) }, today) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), border = BorderStroke(1.dp, PanelBorder)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(context.getString(R.string.time_to_take), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(7, 30, null).forEach { period ->
                    FilterChip(selected = days == period, onClick = { days = period }, label = { Text(period?.let { context.getString(R.string.period_days, it) } ?: context.getString(R.string.all_time)) })
                }
            }
            Text(report.averageDelayMillis?.let { delayDuration(context, it) } ?: "—", color = Violet, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(context.getString(R.string.average_delay_count, report.count), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (report.count == 0) {
                Text(context.getString(R.string.report_empty_hint), fontSize = 13.sp, color = TextMuted)
            } else {
                report.medications.forEach { med ->
                    HorizontalDivider(color = PanelBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.taken_count, med.count), fontSize = 12.sp, color = TextMuted)
                        }
                        Text(delayDuration(context, med.averageDelayMillis), fontSize = 14.sp, color = Violet, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(context.getString(R.string.report_explanation, report.earlyCount), fontSize = 12.sp, color = TextMuted)
            if (report.estimatedCount > 0) Text(context.getString(R.string.estimated_entries, report.estimatedCount), fontSize = 12.sp, color = TextMuted)
        }
    }
}

internal fun delayDuration(context: android.content.Context, millis: Double): String {
    if (millis == 0.0) return context.getString(R.string.duration_0_min)
    if (millis < 60_000) return context.getString(R.string.duration_1_min)
    if (millis < 3_600_000) return context.getString(R.string.duration_minutes, java.text.NumberFormat.getNumberInstance(AppLanguages.locale(context)).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }.format(millis / 60_000))
    val minutes = (millis / 60_000).toLong()
    return context.getString(R.string.duration_hours, minutes / 60, minutes % 60)
}
