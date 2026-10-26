package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseEvent
import io.github.sibmaks.app.dose.data.DoseLog
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseTimeline
import io.github.sibmaks.app.dose.localization.AppLanguages
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only intake details: the current status stays at the top, the full reminder
 * and action rail sits below it, editing and deletion start from here.
 */
@Composable
internal fun IntakeDetailsScreen(
    log: DoseLog,
    events: List<DoseEvent>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Boolean
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable(log) { mutableStateOf(false) }
    var staleError by rememberSaveable(log) { mutableStateOf(false) }
    val style = intakeStatusStyle(log.status)
    val scheduled = log.scheduledAt
        ?: runCatching { DoseLogic.scheduledMillis(LocalDate.parse(log.date), log.time) }.getOrNull()
    val locale = AppLanguages.locale(context)
    val momentFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)

    IntakeScreen(
        title = stringResource(R.string.history_details_title),
        onClose = onDismiss,
        showMenu = true,
        menuContent = {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_action), fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = onEdit,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete_history_entry), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { confirmDelete = true },
            )
        },
        bottomBar = {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 51.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.edit_action), fontWeight = FontWeight.Bold)
            }
        },
    ) {
        IntakeMedicationCard(log, scheduled, momentFormatter)

        Surface(color = style.panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(style.tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(style.icon, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(style.label, style = DoseText.contextTitle, fontWeight = FontWeight.Bold, color = style.accent)
                    if (log.status == "taken") {
                        // The real intake instant, with the delay as its own second line.
                        Text(
                            Instant.ofEpochMilli(log.recordedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(momentFormatter),
                            style = DoseText.supporting,
                            color = style.accent, modifier = Modifier.padding(top = 2.dp),
                        )
                        DoseTimeline.delayMinutes(log)
                            ?.takeIf { it > 0 }
                            ?.let { delay ->
                                Text(
                                    stringResource(R.string.event_taken_late, durationMinutes(context, delay)),
                                    style = DoseText.caption,
                                    color = style.accent, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                    } else Text(
                        stringResource(
                            if (log.status == "pending") R.string.status_pending_hint else R.string.status_missed_hint
                        ),
                        style = DoseText.supporting,
                        color = style.accent, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        TimelineHeading(stringResource(R.string.timeline_title))
        IntakeTimeline(log, events)

        if (staleError) Text(
            stringResource(R.string.history_changed_error), style = DoseText.supporting,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_history_entry)) },
        text = { Text(stringResource(R.string.delete_history_hint)) },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; staleError = !onDelete() }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
