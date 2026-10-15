package com.example.dose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dose.data.*
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun DelayReportCard(logs: List<DoseLog>, today: LocalDate) {
    var days by remember { mutableStateOf<Int?>(7) }
    val report = remember(logs, days, today) { DoseReports.summarize(logs, days?.let { today.minusDays(it - 1L) }, today) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), border = BorderStroke(1.dp, PanelBorder)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Time to take", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(7, 30, null).forEach { period ->
                    FilterChip(selected = days == period, onClick = { days = period }, label = { Text(period?.let { "$it days" } ?: "All time") })
                }
            }
            Text(report.averageDelayMillis?.let(::delayDuration) ?: "—", color = Violet, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Average delay · ${report.count} taken ${if (report.count == 1) "dose" else "doses"}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (report.count == 0) {
                Text("Mark a dose Taken to see how long after its scheduled time you usually record it.", fontSize = 13.sp, color = TextMuted)
            } else {
                report.medications.forEach { med ->
                    HorizontalDivider(color = PanelBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${med.count} taken ${if (med.count == 1) "dose" else "doses"}", fontSize = 12.sp, color = TextMuted)
                        }
                        Text(delayDuration(med.averageDelayMillis), fontSize = 14.sp, color = Violet, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Measured from the original scheduled time to your Taken click. Skipped doses are excluded; early clicks count as zero delay${if (report.earlyCount > 0) " (${report.earlyCount} early)" else ""}.", fontSize = 12.sp, color = TextMuted)
            if (report.estimatedCount > 0) Text("${report.estimatedCount} older ${if (report.estimatedCount == 1) "entry uses" else "entries use"} the current time zone to estimate the scheduled time.", fontSize = 12.sp, color = TextMuted)
        }
    }
}

internal fun delayDuration(millis: Double): String {
    if (millis == 0.0) return "0 min"
    if (millis < 60_000) return "<1 min"
    if (millis < 3_600_000) return "%.1f min".format(Locale.US, millis / 60_000)
    val minutes = (millis / 60_000).toLong()
    return "${minutes / 60} h ${minutes % 60} min"
}
