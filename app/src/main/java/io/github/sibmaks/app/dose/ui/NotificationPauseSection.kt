package io.github.sibmaks.app.dose.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.PauseWindow
import io.github.sibmaks.app.dose.data.PauseWindows
import io.github.sibmaks.app.dose.localization.AppLanguages
import io.github.sibmaks.app.dose.reminders.NotificationPause
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

/**
 * Pause management: the running pause on top, the create tile, scheduled windows,
 * a collapsible history, and a bottom sheet for creating a pause. Pauses are never edited.
 */
@Composable
internal fun NotificationPauseSection(onRefresh: () -> Unit) {
    val context = LocalContext.current
    var windows by remember { mutableStateOf(NotificationPause.windows(context)) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var resumeConfirmId by rememberSaveable { mutableLongStateOf(0L) }
    var deleteConfirmId by rememberSaveable { mutableLongStateOf(0L) }

    fun reload() {
        windows = NotificationPause.windows(context)
        onRefresh()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30.seconds)
            val current = NotificationPause.windows(context)
            if (current != windows) {
                windows = current
                onRefresh()
            }
        }
    }

    val now = System.currentTimeMillis()
    val active = PauseWindows.active(windows, now)
    val scheduled = PauseWindows.scheduled(windows, now)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.notification_pause_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (active.isEmpty()) {
            PauseStatusCard(
                neverUsed = windows.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        active.forEach { window ->
            PauseActiveCard(window, onResume = { resumeConfirmId = window.id })
        }

        AddPauseTile(onClick = { showCreate = true })

        if (scheduled.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pause_scheduled_section), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(7.dp))
                PauseCountChip(scheduled.size)
            }
            scheduled.forEach { window ->
                PauseScheduledCard(window, onDelete = { deleteConfirmId = window.id })
            }
        }
    }

    if (resumeConfirmId != 0L) AlertDialog(
        onDismissRequest = { resumeConfirmId = 0 },
        title = { Text(stringResource(R.string.pause_resume_confirm_title)) },
        text = { Text(stringResource(R.string.pause_resume_confirm_text)) },
        confirmButton = {
            TextButton(onClick = {
                NotificationPause.cancel(context, resumeConfirmId); resumeConfirmId =
                0; reload()
            }) {
                Text(stringResource(R.string.pause_resume_action), color = Violet, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { resumeConfirmId = 0 }) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (deleteConfirmId != 0L) AlertDialog(
        onDismissRequest = { deleteConfirmId = 0 },
        title = { Text(stringResource(R.string.pause_delete_confirm_title)) },
        text = { Text(stringResource(R.string.pause_delete_confirm_text)) },
        confirmButton = {
            TextButton(onClick = {
                NotificationPause.cancel(context, deleteConfirmId); deleteConfirmId =
                0; reload()
            }) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { deleteConfirmId = 0 }) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showCreate) CreatePauseSheet(
        windows = windows,
        onSave = { startAt, endAt ->
            NotificationPause.add(context, startAt, endAt)
            showCreate = false
            reload()
        },
        onDismiss = { showCreate = false },
    )
}

/** No running pause: notifications are on; a fresh account gets a slightly warmer welcome text. */
@Composable
private fun PauseStatusCard(
    neverUsed: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = themeColor(0xFFF4FBF7, 0xFF16281F),
        border = BorderStroke(1.dp, themeColor(0xFFBFE3D2, 0xFF2E5C46)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                stringResource(
                    if (neverUsed) R.string.pause_notifications_enabled else R.string.pause_notifications_active
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = Mint,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (neverUsed) Text(
                stringResource(R.string.pause_enabled_hint),
                fontSize = 12.5.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The running pause: purple-tinted, prominent, with the resume action and no alarming colors. */
@Composable
private fun PauseActiveCard(
    window: PauseWindow,
    onResume: () -> Unit
) {
    val locale = AppLanguages.locale(LocalContext.current)
    val momentFormatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = themeColor(0xFFF0EDFB, 0xFF2A2545),
        border = BorderStroke(1.dp, themeColor(0xFFD8CFF6, 0xFF4A3F73)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.pause_current_title),
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
            BoundRow(
                stringResource(R.string.pause_start_label),
                Instant.ofEpochMilli(window.startAt)
                    .atZone(ZoneId.systemDefault())
                    .format(momentFormatter)
            )
            BoundRow(
                stringResource(R.string.pause_end_label),
                Instant.ofEpochMilli(window.endAt)
                    .atZone(ZoneId.systemDefault())
                    .format(momentFormatter)
            )
            Text(
                stringResource(R.string.pause_auto_resume),
                fontSize = 12.5.sp, color = TextMuted, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Surface(
                onClick = onResume,
                shape = RoundedCornerShape(12.dp),
                color = Panel,
                border = BorderStroke(1.dp, PanelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp)
                    .height(44.dp),
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.pause_resume_action), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BoundRow(
    label: String,
    value: String
) {
    Row(Modifier.padding(top = 9.dp)) {
        Text(label, color = TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** The create action lives in the list itself, matching a pause card's height and style. */
@Composable
private fun AddPauseTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Violet, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.pause_add_action),
                color = Violet,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PauseScheduledCard(
    window: PauseWindow,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val locale = AppLanguages.locale(context)
    val start =
        Instant.ofEpochMilli(window.startAt)
            .atZone(zone)
    val end =
        Instant.ofEpochMilli(window.endAt)
            .atZone(zone)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        border = BorderStroke(1.dp, PanelBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    start.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                    fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
                    modifier = Modifier.weight(1f),
                )
                PausePill(
                    text = stringResource(R.string.pause_status_scheduled),
                    icon = Icons.Rounded.Schedule,
                    content = AccentPanel,
                    tint = Violet,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        stringResource(R.string.pause_delete_confirm_title),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                start.format(DateTimeFormatter.ofPattern("HH:mm", locale)) + " — " +
                        end.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            val crossesMidnight = end.toLocalDate() != start.toLocalDate()
            Text(
                if (crossesMidnight) stringResource(
                    R.string.pause_until_date,
                    end.format(DateTimeFormatter.ofPattern("d MMMM", locale))
                ) else durationMinutes(context, (window.endAt - window.startAt) / 60_000),
                fontSize = 12.5.sp, color = TextMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PauseCountChip(
    count: Int,
    muted: Boolean = false
) {
    Text(
        "$count",
        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
        color = if (muted) TextMuted else Violet,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (muted) themeColor(0xFFEBECF5, 0xFF30313F) else AccentPanel)
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
    )
}

@Composable
private fun PausePill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: Color,
    tint: Color
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(content)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

private enum class PausePreset {
    MIN30,
    HOUR1,
    HOURS2,
    MORNING,
    CUSTOM
}

/** A bound rendered as an outlined text field: floating label on the border, the value row is the tap target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePauseSheet(
    windows: List<PauseWindow>,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val locale = AppLanguages.locale(context)
    val now = remember { System.currentTimeMillis() }
    val momentFormatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale)
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)

    var startNow by rememberSaveable { mutableStateOf(true) }
    var startDate by rememberSaveable {
        mutableStateOf(
            LocalDate.now()
                .toString()
        )
    }
    var startTime by rememberSaveable {
        mutableStateOf(
            Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }
    var preset by rememberSaveable { mutableStateOf(PausePreset.MIN30.name) }
    var endDate by rememberSaveable {
        mutableStateOf(
            LocalDate.now()
                .toString()
        )
    }
    var endTime by rememberSaveable { mutableStateOf("") }

    fun parseStart(): Long = if (startNow) System.currentTimeMillis() else runCatching {
        LocalDate.parse(startDate)
            .atTime(LocalTime.parse(startTime))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    fun endFor(start: Long): Long? = when (PausePreset.valueOf(preset)) {
        PausePreset.MIN30 -> start + 30 * 60_000L
        PausePreset.HOUR1 -> start + 3_600_000L
        PausePreset.HOURS2 -> start + 7_200_000L
        PausePreset.MORNING -> if (start > 0) PauseWindows.morningAfter(start, zone) else null
        PausePreset.CUSTOM -> runCatching {
            LocalDate.parse(endDate)
                .atTime(LocalTime.parse(endTime))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    val startAt: Long = parseStart()
    val computedEnd = endFor(startAt)
    val endMoment = computedEnd?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val needsEndTime = preset == PausePreset.CUSTOM.name && endTime.isBlank()

    val valid = startAt > 0 && computedEnd != null && computedEnd > startAt && computedEnd > System.currentTimeMillis()
    val overlaps = valid && PauseWindows.overlaps(windows, startAt, computedEnd)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pause_new_title),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close_screen))
                }
            }

            Text(stringResource(R.string.pause_start_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PauseOptionChip(
                    stringResource(R.string.pause_start_now),
                    selected = startNow,
                    modifier = Modifier.weight(1f)
                ) { startNow = true }
                PauseOptionChip(
                    stringResource(R.string.pause_start_pick),
                    selected = !startNow,
                    modifier = Modifier.weight(1f)
                ) { startNow = false }
            }
            if (!startNow) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseField(
                        label = stringResource(R.string.pause_date_label),
                        value = runCatching {
                            LocalDate.parse(startDate)
                                .format(dateFormatter)
                        }.getOrDefault(""),
                        invalid = startAt <= 0,
                        modifier = Modifier.weight(1.4f),
                        onClick = {
                            val base = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(
                                context, AppAppearance.dialogTheme(context),
                                { _, year, month, day ->
                                    startDate =
                                        LocalDate.of(year, month + 1, day)
                                            .toString()
                                },
                                base.year, base.monthValue - 1, base.dayOfMonth,
                            ).apply {
                                datePicker.minDate = System.currentTimeMillis()
                            }
                                .show()
                        },
                    )
                    PauseField(
                        label = stringResource(R.string.pause_time_label),
                        value = if (DoseLogic.isTime(startTime)) startTime else "",
                        invalid = startAt <= 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val base = runCatching { LocalTime.parse(startTime) }.getOrDefault(LocalTime.now())
                            TimePickerDialog(
                                context, AppAppearance.dialogTheme(context),
                                { _, hour, minute ->
                                    startTime = "%02d:%02d".format(java.util.Locale.US, hour, minute)
                                },
                                base.hour, base.minute, true,
                            ).show()
                        },
                    )
                }
            }

            Text(stringResource(R.string.pause_end_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseOptionChip(stringResource(R.string.pause_30_min), selected = preset == PausePreset.MIN30.name) {
                        preset = PausePreset.MIN30.name
                    }
                    PauseOptionChip(stringResource(R.string.pause_1_hour), selected = preset == PausePreset.HOUR1.name) {
                        preset = PausePreset.HOUR1.name
                    }
                    PauseOptionChip(stringResource(R.string.pause_2_hours), selected = preset == PausePreset.HOURS2.name) {
                        preset = PausePreset.HOURS2.name
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PauseOptionChip(
                        stringResource(R.string.pause_until_morning),
                        selected = preset == PausePreset.MORNING.name
                    ) {
                        preset = PausePreset.MORNING.name
                    }
                    PauseOptionChip(
                        stringResource(R.string.pause_custom_time),
                        selected = preset == PausePreset.CUSTOM.name
                    ) {
                        preset = PausePreset.CUSTOM.name
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PauseField(
                    label = stringResource(R.string.pause_date_label),
                    value = if (preset == PausePreset.CUSTOM.name) runCatching {
                        LocalDate.parse(endDate).format(dateFormatter)
                    }.getOrDefault("") else endMoment?.format(dateFormatter).orEmpty(),
                    invalid = !valid && !needsEndTime,
                    modifier = Modifier.weight(1.4f),
                    onClick = {
                        val base = Instant.ofEpochMilli(computedEnd?.takeIf { it > 0 } ?: System.currentTimeMillis())
                            .atZone(zone)
                        DatePickerDialog(
                            context, AppAppearance.dialogTheme(context),
                            { _, year, month, day ->
                                endDate =
                                    LocalDate.of(year, month + 1, day)
                                        .toString()
                                endTime =
                                    base.toLocalTime()
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                preset = PausePreset.CUSTOM.name
                            },
                            base.year, base.monthValue - 1, base.dayOfMonth,
                        ).apply {
                            datePicker.minDate = Instant.ofEpochMilli(startAt.coerceAtLeast(System.currentTimeMillis()))
                                .atZone(zone)
                                .toLocalDate()
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        }
                            .show()
                    },
                )
                PauseField(
                    label = stringResource(R.string.pause_time_label),
                    value = endMoment?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
                    placeholder = stringResource(R.string.pick_time_placeholder),
                    invalid = !valid && !needsEndTime,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val base = Instant.ofEpochMilli(computedEnd?.takeIf { it > 0 } ?: System.currentTimeMillis())
                            .atZone(zone)
                        TimePickerDialog(
                            context, AppAppearance.dialogTheme(context),
                            { _, hour, minute ->
                                endDate =
                                    base.toLocalDate()
                                        .toString()
                                endTime = "%02d:%02d".format(java.util.Locale.US, hour, minute)
                                preset = PausePreset.CUSTOM.name
                            },
                            base.hour, base.minute, true,
                        ).show()
                    },
                )
            }

            if (overlaps) Text(
                stringResource(R.string.pause_overlap_info),
                fontSize = 12.5.sp, color = TextMuted, lineHeight = 17.sp,
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SoftPanel,
                border = BorderStroke(1.dp, PanelBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (valid) stringResource(
                        R.string.pause_summary,
                        Instant.ofEpochMilli(startAt)
                            .atZone(zone)
                            .format(momentFormatter),
                        endMoment?.format(momentFormatter).orEmpty(),
                    ) else stringResource(if (needsEndTime) R.string.pick_time_placeholder else R.string.pause_range_invalid),
                    fontSize = 13.5.sp, lineHeight = 19.sp,
                    color = if (needsEndTime) TextMuted else if (valid) TextPrimary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 51.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        // "Сейчас" resolves at the tap instant; fresh values guard the store call.
                        val freshStart = parseStart()
                        val freshEnd = endFor(freshStart)
                        if (freshEnd != null && freshStart in 1..<freshEnd) onSave(freshStart, freshEnd)
                    },
                    enabled = valid,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 51.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PauseOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (selected) AccentPanel else Panel,
        border = BorderStroke(
            1.5.dp,
            if (selected) Violet else themeColor(0xFFE2E6EE, 0xFF3A3B48)
        ),
        modifier = modifier.selectionControl(selected, onClick = onClick),
    ) {
        Text(
            label, fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Violet else TextPrimary,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** An outlined field with a floating caption; the whole field is the tap target. */
@Composable
private fun PauseField(
    label: String,
    value: String,
    invalid: Boolean,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Panel)
                .border(
                    BorderStroke(
                        1.dp,
                        if (invalid) MaterialTheme.colorScheme.error else PanelBorder
                    ),
                    RoundedCornerShape(15.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { placeholder }, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (value.isBlank()) TextMuted else TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = if (invalid) MaterialTheme.colorScheme.error else TextMuted,
            modifier = Modifier
                .offset(x = 11.dp, y = (-8).dp)
                .background(Panel)
                .padding(horizontal = 6.dp),
        )
    }
}
