package com.example.dose.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dose.data.*
import com.example.dose.reminders.ReminderAlerts
import com.example.dose.reminders.ReminderReceiver
import com.example.dose.reminders.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Violet = Color(0xFF7163E5)
internal val TextPrimary = Color(0xFF252B43)
internal val TextMuted = Color(0xFF9599AC)
private val Page = Color(0xFFFAFBFE)
internal val PanelBorder = Color(0xFFEBECF5)
private val Mint = Color(0xFF379D80)
internal val Peach = Color(0xFFE78F5C)

@Composable
fun DoseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Violet, onPrimary = Color.White,
            background = Page, onBackground = TextPrimary,
            surface = Color.White, onSurface = TextPrimary,
        ),
        content = content,
    )
}

private enum class Tab(val title: String, val icon: ImageVector) {
    Today("Today", Icons.Rounded.Home),
    Medications("Medications", Icons.Rounded.Medication),
    History("History", Icons.Rounded.History),
}

@Composable
fun DoseApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showReminderSettings by rememberSaveable { mutableStateOf(false) }
    val medications = remember(revision) { DoseStore.medications(context) }
    val editing = medications.find { it.id == editingId }
    val logs = remember(revision) { DoseStore.logs(context) }
    val snoozes = remember(revision) { DoseStore.snoozes(context) }
    val now = remember(revision) { System.currentTimeMillis() }
    val items = remember(medications, logs, snoozes, now) {
        DoseLogic.todayItems(medications, logs, snoozes, now)
    }
    val reminderSetup = remember(revision) { ReminderAlerts.setupState(context) }

    fun refresh() { revision++ }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            refresh()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ReminderScheduler.scheduleAll(context)
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Page).statusBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(Violet),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Medication, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(9.dp))
                Text("dose", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Text(".", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showReminderSettings = true }) {
                    Icon(
                        if (reminderSetup.complete) Icons.Rounded.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = if (reminderSetup.complete) "Reminder setup complete. Change reminders" else "Reminder setup incomplete. Set up reminders",
                        tint = if (reminderSetup.complete) Violet else TextMuted,
                    )
                }
                IconButton(onClick = { editingId = null; showEditor = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add medication", tint = TextPrimary)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                Tab.entries.forEach { target ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == target.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(target.ordinal) } },
                        icon = { Icon(target.icon, contentDescription = null) },
                        label = { Text(target.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Violet, selectedTextColor = Violet,
                            indicatorColor = Color(0xFFF0EDFF), unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding).fillMaxSize(),
            verticalAlignment = Alignment.Top, beyondViewportPageCount = 1) { page ->
            when (Tab.entries[page]) {
                Tab.Today -> TodayPage(
                    modifier = Modifier.fillMaxSize(),
                    items = items,
                    hasMedications = medications.isNotEmpty(),
                    showSetupWarning = !reminderSetup.complete,
                    onReminderSetup = { showReminderSettings = true },
                    onAdd = { editingId = null; showEditor = true },
                    onManage = { scope.launch { pagerState.animateScrollToPage(Tab.Medications.ordinal) } },
                    onRecord = { item, status ->
                        ReminderScheduler.cancelSnooze(context, item.medication.id, item.time, item.date)
                        DoseStore.record(context, DoseStore.newLog(item, status))
                        cancelDoseNotification(context, item)
                        ReminderScheduler.refreshSlot(context, item.medication.id, item.time)
                        refresh()
                    },
                    onUndo = { item ->
                        DoseStore.undo(context, item.date, item.medication.id, item.time)
                        ReminderScheduler.refreshSlot(context, item.medication.id, item.time)
                        refresh()
                    },
                    onSnooze = { item ->
                        ReminderScheduler.snooze(context, item.medication.id, item.time, item.date)
                        cancelDoseNotification(context, item)
                        refresh()
                    },
                )
                Tab.Medications -> MedicationsPage(
                    modifier = Modifier.fillMaxSize(), medications = medications,
                    onAdd = { editingId = null; showEditor = true },
                    onEdit = { med -> editingId = med.id; showEditor = true },
                    onToggle = { med ->
                        DoseStore.upsert(context, med.copy(active = !med.active))
                        ReminderScheduler.scheduleAll(context)
                        refresh()
                    },
                )
                Tab.History -> HistoryPage(modifier = Modifier.fillMaxSize(), logs = logs)
            }
        }
    }

    if (showReminderSettings) {
        ReminderSettingsDialog(reminderSetup, onDismiss = { showReminderSettings = false },
            onRefresh = ::refresh, onExactAlarms = { openExactAlarmSettings(context) })
    }
    if (showEditor) {
        MedicationEditor(
            original = editing,
            onDismiss = { showEditor = false },
            onSave = { med ->
                val previous = editing
                DoseStore.upsert(context, med)
                DoseStore.recordColorUse(context, med.color)
                ReminderScheduler.scheduleAll(context)
                ReminderScheduler.deliverImmediate(context, med, previous)
                refresh()
                showEditor = false
                scope.launch { pagerState.animateScrollToPage(Tab.Medications.ordinal) }
            },
            onDelete = { med ->
                DoseStore.delete(context, med.id)
                ReminderScheduler.scheduleAll(context)
                refresh()
                showEditor = false
            },
        )
    }
}

private fun cancelDoseNotification(context: Context, item: DoseItem) {
    val id = ReminderReceiver.notificationId(item.medication.id, item.time, item.date)
    androidx.core.app.NotificationManagerCompat.from(context).cancel(id)
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(context, "Open Settings → Apps → Special app access → Alarms & reminders", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun TodayPage(
    modifier: Modifier,
    items: List<DoseItem>,
    hasMedications: Boolean,
    showSetupWarning: Boolean,
    onReminderSetup: () -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onRecord: (DoseItem, String) -> Unit,
    onUndo: (DoseItem) -> Unit,
    onSnooze: (DoseItem) -> Unit,
) {
    val pending = items.filter { it.log == null }
    val featured = pending.filter { it.status == "due" || it.status == "overdue" }
        .minByOrNull { it.dueMillis } ?: pending.minByOrNull { it.dueMillis }
    val taken = items.count { it.status == "taken" }
    val skipped = items.count { it.status == "skipped" }
    val percent = if (items.isEmpty()) 0 else (taken * 100f / items.size).toInt()
    val day = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            Column(Modifier.padding(top = 10.dp, bottom = 3.dp)) {
                Eyebrow(day.uppercase(Locale.ENGLISH))
                Spacer(Modifier.height(8.dp))
                Text("Every dose counts.", fontWeight = FontWeight.ExtraBold, fontSize = 31.sp, color = TextPrimary)
                Text("A little care, every single day.", fontSize = 14.sp, color = TextMuted)
            }
        }
        item { FeaturedCard(featured, items.isNotEmpty(), hasMedications, onAdd, onRecord) }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PanelBorder),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { percent / 100f }, modifier = Modifier.fillMaxSize(),
                            color = Violet, trackColor = Color(0xFFF0EDFC), strokeWidth = 7.dp,
                        )
                        Text("$percent%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Today's progress", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "$taken of ${items.size} doses taken" + (if (skipped > 0) " · $skipped skipped" else ""),
                            color = TextMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        if (showSetupWarning) {
            item {
                InfoBanner("Choose how Dose reminds you and finish Android access.", "Set up reminders", onReminderSetup)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text("Today's schedule", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("Manage", fontWeight = FontWeight.Bold) }
            }
        }
        if (items.isEmpty()) {
            item { EmptyCard(if (hasMedications) "No doses scheduled today" else "Nothing scheduled yet",
                if (hasMedications) "Your reminders follow the dates you selected. View them in Medications." else "Add a medication and choose when you want to be reminded.",
                if (hasMedications) null else onAdd) }
        } else {
            items(items, key = { it.id }) { item ->
                DoseCard(item, onRecord = onRecord, onUndo = onUndo, onSnooze = onSnooze)
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    item: DoseItem?, hasSchedule: Boolean, hasMedications: Boolean, onAdd: () -> Unit,
    onRecord: (DoseItem, String) -> Unit,
) {
    Card(shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = Violet)) {
        Column(Modifier.fillMaxWidth().padding(23.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = Color(0xFFE5E0FF), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (item?.status == "overdue" || item?.status == "due") "DOSE NEEDS ATTENTION" else "YOUR NEXT REMINDER",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5E0FF),
                )
            }
            if (item != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    if (item.status == "snoozed" && item.snoozedUntil != null) {
                        java.time.Instant.ofEpochMilli(item.snoozedUntil).atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    } else item.time,
                    fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(item.medication.form.iconResource()), item.medication.formLabel, tint = Color.White, modifier = Modifier.size(26.dp))
                    Text(item.medication.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.medication.doseDescription + (if (item.medication.note.isNotBlank()) " · ${item.medication.note}" else ""),
                    fontSize = 13.sp, color = Color(0xFFE9E5FF),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onRecord(item, "taken") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Violet),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark as taken", fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.height(23.dp))
                Text(
                    if (hasSchedule) "All done for today!" else if (hasMedications) "No doses due today" else "Your routine starts here",
                    fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasSchedule) "You've recorded all your scheduled doses."
                    else if (hasMedications) "Your next doses follow your selected dates."
                    else "Add your first medication to build your daily routine.",
                    color = Color(0xFFE9E5FF), fontSize = 14.sp,
                )
                if (!hasMedications) {
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(Color.White, contentColor = Violet)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Add medication")
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseCard(
    item: DoseItem,
    onRecord: (DoseItem, String) -> Unit,
    onUndo: (DoseItem) -> Unit,
    onSnooze: (DoseItem) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Top) {
            PillAvatar(item.medication.color, item.medication.form, item.medication.formLabel)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(3.dp))
                Text(item.medication.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.medication.doseDescription + if (item.medication.note.isNotBlank()) " · ${item.medication.note}" else "",
                    fontSize = 12.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (item.log == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CompactAction("Taken", Icons.Rounded.Check, onClick = { onRecord(item, "taken") })
                        if (item.status == "due" || item.status == "overdue") {
                            CompactAction("+10m", Icons.Rounded.Alarm, onClick = { onSnooze(item) }, secondary = true)
                        }
                        CompactAction("Skip", null, onClick = { onRecord(item, "skipped") }, secondary = true)
                    }
                } else {
                    CompactAction("Undo", Icons.AutoMirrored.Rounded.Undo, onClick = { onUndo(item) }, secondary = true)
                }
            }
            Spacer(Modifier.width(7.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(item.time, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                StatusBadge(item.status)
            }
        }
    }
}

@Composable
private fun CompactAction(label: String, icon: ImageVector?, secondary: Boolean = false, onClick: () -> Unit) {
    val color = if (secondary) Color(0xFF83899D) else Violet
    Surface(
        onClick = onClick, color = if (secondary) Color(0xFFF6F7FB) else Color(0xFFF3F0FF),
        shape = RoundedCornerShape(9.dp),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(13.dp), tint = color)
                Spacer(Modifier.width(3.dp))
            }
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color, background) = when (status) {
        "taken" -> Triple("Taken", Mint, Color(0xFFE3F8EF))
        "skipped" -> Triple("Skipped", TextMuted, Color(0xFFF1F2F7))
        "due" -> Triple("Due now", Peach, Color(0xFFFFF0E5))
        "overdue" -> Triple("Overdue", Peach, Color(0xFFFFF0E5))
        "snoozed" -> Triple("Snoozed", Color(0xFF648BB4), Color(0xFFEAF2FC))
        else -> Triple("Upcoming", Violet, Color(0xFFF0EFFF))
    }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 7.dp, vertical = 5.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MedicationsPage(
    modifier: Modifier, medications: List<Medication>, onAdd: () -> Unit,
    onEdit: (Medication) -> Unit, onToggle: (Medication) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp)) {
                Eyebrow("YOUR DAILY ROUTINE")
                Spacer(Modifier.height(8.dp))
                Text("My medications.", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text("Manage your doses and reminder times.", fontSize = 14.sp, color = TextMuted)
            }
        }
        if (medications.isEmpty()) {
            item { EmptyCard("No medications yet", "Start by adding your first daily medication.", onAdd) }
        } else {
            items(medications, key = { it.id }) { med ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PanelBorder),
                    modifier = Modifier.alpha(if (med.active) 1f else 0.6f),
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillAvatar(med.color, med.form, med.formLabel)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(med.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    med.doseDescription + if (med.note.isBlank()) "" else " · ${med.note}",
                                    fontSize = 12.sp, color = TextMuted, maxLines = 2,
                                )
                            }
                            IconButton(onClick = { onEdit(med) }) {
                                Icon(Icons.Rounded.Edit, "Edit ${med.name}", tint = TextMuted, modifier = Modifier.size(21.dp))
                            }
                        }
                        HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(top = 13.dp, bottom = 9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                med.schedules.forEach { rule ->
                                    Text(scheduleSummary(rule), fontSize = 12.sp, color = Violet)
                                }
                            }
                            Switch(checked = med.active, onCheckedChange = { onToggle(med) }, modifier = Modifier.height(29.dp))
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(53.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("Add medication", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryPage(modifier: Modifier, logs: List<DoseLog>) {
    val today = LocalDate.now()
    val dates = (0..6).map { today.minusDays(it.toLong()) }
    val groups = dates.mapNotNull { date ->
        val matching = logs.filter { it.date == date.toString() }.sortedByDescending { it.time }
        if (matching.isEmpty()) null else date to matching
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp)) {
                Eyebrow("KEEP YOUR STREAK GOING")
                Spacer(Modifier.height(8.dp))
                Text("Dose history.", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your recent dose activity, saved on this device.", fontSize = 14.sp, color = TextMuted)
            }
        }
        item { DelayReportCard(logs, today) }
        if (groups.isEmpty()) {
            item { EmptyCard("No history yet", "Mark a dose as taken or skipped to build your history.", null) }
        }
        groups.forEach { (date, dayLogs) ->
            item(key = "day-$date") {
                Text(
                    when (date) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH))
                    },
                    color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            items(dayLogs, key = { "${it.date}|${it.medicationId}|${it.time}" }) { log ->
                Card(
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, PanelBorder),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                                .background(if (log.status == "taken") Color(0xFFE3F8EF) else Color(0xFFF1F2F7)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (log.status == "taken") Icons.Rounded.Check else Icons.Rounded.Remove,
                                null, tint = if (log.status == "taken") Mint else TextMuted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(log.medicationName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Scheduled ${log.time} · ${log.dose}", color = TextMuted, fontSize = 12.sp)
                            DoseReports.timing(log)?.let { timing ->
                                val actual = java.time.Instant.ofEpochMilli(log.recordedAt).atZone(java.time.ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                                val difference = delayDuration(kotlin.math.abs(timing.differenceMillis).toDouble())
                                Text("Taken $actual · $difference ${if (timing.differenceMillis < 0) "early" else "delay"}", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            if (log.status == "taken") "Taken" else "Skipped",
                            color = if (log.status == "taken") Mint else TextMuted,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, color = Color(0xFF847AAD), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.1.sp)
}

@Composable
private fun PillAvatar(color: String, form: MedicationForm, label: String) {
    val (foreground, background) = palette(color)
    Box(
        Modifier.size(49.dp).clip(RoundedCornerShape(16.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(androidx.compose.ui.res.painterResource(form.iconResource()), label, tint = foreground, modifier = Modifier.size(26.dp)) }
}

internal fun palette(color: String): Pair<Color, Color> {
    val foreground = Color(android.graphics.Color.parseColor(MedicationColors.hex(color)))
    return foreground to androidx.compose.ui.graphics.lerp(Color.White, foreground, 0.13f)
}

@Composable
private fun InfoBanner(message: String, action: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF0EFFF)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = Violet, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text(message, color = Color(0xFF6D6698), fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(action, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, explanation: String, onAdd: (() -> Unit)?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Medication, null, modifier = Modifier.size(35.dp), tint = Violet)
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(explanation, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (onAdd != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onAdd) { Text("Add medication") }
            }
        }
    }
}
