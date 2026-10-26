package io.github.sibmaks.app.dose.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseEvent
import io.github.sibmaks.app.dose.data.DoseLog
import io.github.sibmaks.app.dose.data.DoseTimeline
import io.github.sibmaks.app.dose.data.TimelineEntry
import io.github.sibmaks.app.dose.localization.AppLanguages
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Icon, accent and panel styling shared by the intake screens; skipped keeps its neutral list styling. */
internal data class IntakeStatusStyle(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val tile: Color,
    val accent: Color,
    val panel: Color,
)

/** Tiles are fixed mid-tones so the white icon stays readable in both themes; text follows the theme. */
@Composable
internal fun intakeStatusStyle(status: String): IntakeStatusStyle = when (status) {
    "taken" -> IntakeStatusStyle(
        stringResource(R.string.taken), stringResource(R.string.status_taken_hint),
        Icons.Rounded.Check, doseSemanticColors(false).success, Mint, SuccessPanel,
    )

    "pending" -> IntakeStatusStyle(
        stringResource(R.string.pending), stringResource(R.string.status_pending_hint),
        Icons.Rounded.Schedule, doseColorScheme(false).primary, Violet, AccentPanel,
    )

    "missed" -> IntakeStatusStyle(
        stringResource(R.string.missed), stringResource(R.string.status_missed_hint),
        Icons.Rounded.AlarmOff, doseSemanticColors(false).attention, Peach, AttentionPanel,
    )

    else -> IntakeStatusStyle(
        stringResource(R.string.skipped), stringResource(R.string.event_skipped),
        Icons.Rounded.Block, doseColorScheme(false).onSurfaceVariant, TextMuted, SoftPanel,
    )
}

@Composable
internal fun TimelineHeading(text: String) {
    Text(
        text.uppercase(AppLanguages.locale(LocalContext.current)),
        color = Semantic.eyebrow,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
    )
}

/** The full reminder and action rail: every delivery, postponement and the final outcome, in true order. */
@Composable
internal fun IntakeTimeline(
    log: DoseLog,
    events: List<DoseEvent>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val locale = AppLanguages.locale(context)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    val entries = remember(log, events) { DoseTimeline.build(log, events) }
    val rows =
        entries.map {
            it to Instant.ofEpochMilli(it.at)
                .atZone(zone)
        }

    Column(modifier) {
        rows.forEachIndexed { index, (entry, moment) ->
            val day = moment.format(dayFormatter)
            val previous = rows.getOrNull(index - 1)?.second
            if (previous == null || previous.format(dayFormatter) != day) {
                RailRow(last = false, tile = {}) {
                    Text(
                        day, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            val (tileBackground, tileTint, tileIcon) = when (entry.kind) {
                "snooze" -> Triple(SoftPanel, TextMuted, Icons.Rounded.Schedule)
                "taken" -> Triple(SuccessPanel, Mint, Icons.Rounded.Check)
                "missed" -> Triple(AttentionPanel, Peach, Icons.Rounded.Flag)
                "skipped" -> Triple(SoftPanel, TextMuted, Icons.Rounded.Block)
                else -> Triple(AccentPanel, Violet, Icons.Rounded.Notifications)
            }
            RailRow(last = index == rows.lastIndex, tile = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(tileBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tileIcon, null, tint = tileTint, modifier = Modifier.size(17.dp))
                }
            }) {
                Column(Modifier.padding(bottom = 15.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            timelineTitle(context, entry),
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            moment.format(timeFormatter), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = when (entry.kind) {
                                "taken" -> Mint
                                "missed" -> Peach
                                else -> TextMuted
                            },
                        )
                    }
                    timelineMeta(context, entry, moment)?.let {
                        Text(it, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

private fun timelineTitle(
    context: Context,
    entry: TimelineEntry
): String = when (entry.kind) {
    "snooze" -> context.getString(
        R.string.event_snooze,
        durationMinutes(context, entry.minutes ?: 0)
    )

    "taken" -> context.getString(R.string.event_taken)
    "missed" -> context.getString(R.string.event_missed)
    "skipped" -> context.getString(R.string.event_skipped)
    "repeat" -> context.getString(R.string.event_reminder_repeat)
    else -> context.getString(R.string.event_reminder)
}

private fun timelineMeta(
    context: Context,
    entry: TimelineEntry,
    moment: ZonedDateTime
): String? =
    when (entry.kind) {
        "snooze" -> entry.nextAt?.let {
            context.getString(
                R.string.event_snooze_next,
                Instant.ofEpochMilli(it)
                    .atZone(moment.zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            )
        }

        "taken" -> when {
            entry.onTime -> context.getString(R.string.event_taken_ontime)
            (entry.delayMinutes ?: 0L) > 0 -> context.getString(
                R.string.event_taken_late,
                durationMinutes(context, entry.delayMinutes ?: 0)
            )

            else -> null
        }

        "missed" -> context.getString(R.string.event_missed_meta)
        else -> null
    }

/** One rail row: a 34dp icon column with a continuous line that runs behind tiles and date headers. */
@Composable
private fun RailRow(
    last: Boolean,
    tile: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(34.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!last) Box(
                Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(PanelBorder)
            )
            tile()
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
