package io.github.sibmaks.app.dose.ui

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.*
import io.github.sibmaks.app.dose.localization.*
import io.github.sibmaks.app.dose.reminders.GroupDoseActions
import io.github.sibmaks.app.dose.reminders.ReminderAlerts
import io.github.sibmaks.app.dose.reminders.ReminderReceiver
import io.github.sibmaks.app.dose.reminders.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

private enum class Tab(
    val title: Int,
    val icon: ImageVector
) {
    Today(R.string.today, Icons.Rounded.Home),
    Medications(R.string.medications, Icons.Rounded.Medication),
    History(R.string.history, Icons.Rounded.History),
}

@Composable
fun DoseApp(openTodayRequest: Int = 0) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    // Keep positions alive while full-page details/settings replace the pager.
    val todayScroll = rememberLazyListState()
    val medicationsScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    var historyDoseId by rememberSaveable { mutableStateOf<String?>(null) }
    var todayDoseId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showReminderSettings by rememberSaveable { mutableStateOf(!ReminderAlerts.setupOffered(context)) }
    var firstReminderSetup by rememberSaveable { mutableStateOf(!ReminderAlerts.setupOffered(context)) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(!AppLanguages.offered(context)) }
    val firstLanguageOffer = !AppLanguages.offered(context)
    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest > 0) {
            pagerState.scrollToPage(Tab.Today.ordinal)
            showEditor = false
            todayDoseId = null
            historyDoseId = null
            if (ReminderAlerts.setupOffered(context)) showReminderSettings = false
            if (AppLanguages.offered(context)) showLanguagePicker = false
        }
    }
    val medications = remember(revision) { DoseStore.medications(context) }
    val editing = medications.find { it.id == editingId }
    val logs = remember(revision) { DoseStore.logs(context) }
    val snoozes = remember(revision) { DoseStore.snoozes(context) }
    val now = remember(revision) { System.currentTimeMillis() }
    val overrides = remember(revision) { DoseStore.timeOverrides(context) }
    val items = remember(medications, logs, snoozes, now, overrides) {
        DoseLogic.todayItems(medications, logs, snoozes, now, overrides = overrides)
    }
    val reminderSetup = remember(revision) { ReminderAlerts.setupState(context) }

    fun refresh() {
        revision++
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30.seconds)
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

    if (showLanguagePicker) {
        LanguagePicker(firstLanguageOffer, onDismiss = { showLanguagePicker = false })
        return
    }

    if (showReminderSettings) {
        ReminderSettingsScreen(reminderSetup, firstLaunch = firstReminderSetup, onDismiss = {
            ReminderAlerts.markSetupOffered(context)
            firstReminderSetup = false
            showReminderSettings = false
            refresh()
        }, onRefresh = ::refresh)
        return
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
                ReminderReceiver.refreshVisible(context)
                ReminderScheduler.deliverImmediate(context, med, previous)
                refresh()
                showEditor = false
                if (todayDoseId == null) scope.launch { pagerState.animateScrollToPage(Tab.Medications.ordinal) }
            },
            onDelete = { med ->
                DoseStore.delete(context, med.id)
                todayDoseId = null
                ReminderScheduler.scheduleAll(context)
                refresh()
                showEditor = false
            },
        )
    }

    val selectedDose = items.find { it.id == todayDoseId }
    LaunchedEffect(todayDoseId, selectedDose?.id, showEditor) {
        if (todayDoseId != null && selectedDose == null && !showEditor) todayDoseId = null
    }
    if (selectedDose != null) {
        TodayDoseEditor(
            selectedDose, onDismiss = { todayDoseId = null },
            onEdit = { editingId = selectedDose.medication.id; showEditor = true }, onSave = { time ->
                val success = DoseStore.moveToday(context, selectedDose.id, time)
                if (success) {
                    ReminderScheduler.cancelSnooze(
                        context,
                        selectedDose.medication.id,
                        selectedDose.time,
                        selectedDose.date
                    )
                    cancelDoseNotification(context, selectedDose)
                    ReminderScheduler.scheduleAll(context)
                    todayDoseId = null
                    refresh()
                }
                success
            })
        return
    }

    val selectedHistory = logs.find { DoseHistory.key(it) == historyDoseId }
    LaunchedEffect(historyDoseId, selectedHistory) {
        if (historyDoseId != null && selectedHistory == null) historyDoseId = null
    }
    if (selectedHistory != null) {
        HistoryDoseEditor(
            selectedHistory, onDismiss = { historyDoseId = null },
            onSave = { at ->
                val saved = DoseStore.correctHistoryTime(context, selectedHistory, at)
                if (saved) {
                    ReminderScheduler.scheduleAll(context); historyDoseId = null; refresh()
                }
                saved
            }, onDelete = {
                val deleted = DoseStore.deleteHistory(context, selectedHistory)
                if (deleted) {
                    ReminderScheduler.scheduleAll(context); historyDoseId = null; refresh()
                }
                deleted
            })
        return
    }

    Scaffold(
        containerColor = Page,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Page)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AndroidView(
                    factory = { ImageView(it).apply { setImageResource(R.mipmap.ic_launcher) } },
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text("dose", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Text(".", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Violet)
                Spacer(Modifier.weight(1f))
                LanguageButton(onClick = { showLanguagePicker = true })
                ThemeButton()
                IconButton(onClick = { firstReminderSetup = false; showReminderSettings = true }) {
                    Icon(
                        if (reminderSetup.complete) Icons.Rounded.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = if (reminderSetup.complete) context.getString(R.string.setup_complete_accessibility) else context.getString(
                            R.string.setup_incomplete_accessibility
                        ),
                        tint = if (reminderSetup.complete) Violet else TextMuted,
                    )
                }
                IconButton(onClick = { editingId = null; showEditor = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = context.getString(R.string.add_medication),
                        tint = TextPrimary
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 2.dp) {
                Tab.entries.forEach { target ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == target.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(target.ordinal) } },
                        icon = { Icon(target.icon, contentDescription = null) },
                        label = {
                            Text(
                                context.getString(target.title),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Violet, selectedTextColor = Violet,
                            indicatorColor = AccentPanel, unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState, modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalAlignment = Alignment.Top, beyondViewportPageCount = 1
        ) { page ->
            when (Tab.entries[page]) {
                Tab.Today -> TodayPage(
                    modifier = Modifier.fillMaxSize(),
                    items = items,
                    listState = todayScroll,
                    hasMedications = medications.isNotEmpty(),
                    showSetupWarning = !reminderSetup.complete,
                    onReminderSetup = { firstReminderSetup = false; showReminderSettings = true },
                    onAdd = { editingId = null; showEditor = true },
                    onOpen = { todayDoseId = it.id },
                    onManage = { scope.launch { pagerState.animateScrollToPage(Tab.Medications.ordinal) } },
                    onRecord = { item, status ->
                        ReminderScheduler.cancelSnooze(context, item.medication.id, item.time, item.date)
                        DoseStore.record(context, DoseStore.newLog(item, status))
                        cancelDoseNotification(context, item)
                        ReminderScheduler.refreshSlot(context, item.medication.id, item.time)
                        ReminderReceiver.refreshVisible(context)
                        refresh()
                    },
                    onRecordGroup = { group, status ->
                        GroupDoseActions.record(context, DoseGroups.snapshot(group), status)
                        refresh()
                    },
                    onUndo = { item ->
                        DoseStore.undo(context, item.date, item.medication.id, item.time)
                        ReminderScheduler.refreshSlot(context, item.medication.id, item.time)
                        ReminderReceiver.refreshVisible(context)
                        refresh()
                    },
                )

                Tab.Medications -> MedicationsPage(
                    modifier = Modifier.fillMaxSize(), medications = medications, listState = medicationsScroll,
                    onAdd = { editingId = null; showEditor = true },
                    onEdit = { med -> editingId = med.id; showEditor = true },
                    onToggle = { med ->
                        DoseStore.upsert(context, med.copy(active = !med.active))
                        ReminderScheduler.scheduleAll(context)
                        refresh()
                    },
                )

                Tab.History -> HistoryPage(
                    modifier = Modifier.fillMaxSize(),
                    logs = logs,
                    listState = historyScroll,
                    onOpen = { historyDoseId = DoseHistory.key(it) })
            }
        }
    }
}

@Composable
private fun LanguageButton(onClick: () -> Unit) {
    val context = LocalContext.current
    val tag =
        AppLanguages.selectedTag(context)
            .substringBefore('-')
    val selected = AppLanguages.supported.firstOrNull { it.tag == tag }
    val flag = languageFlag(tag)
    val description = context.getString(R.string.app_language) + ": " +
            (selected?.nativeName ?: context.getString(R.string.system_language))
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        if (flag == null) {
            Icon(Icons.Rounded.Language, null, tint = TextMuted)
        } else {
            Image(
                painterResource(flag), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, PanelBorder, CircleShape)
            )
        }
    }
}

private fun cancelDoseNotification(
    context: Context,
    item: DoseItem
) {
    val id = ReminderReceiver.notificationId(item.medication.id, item.time, item.date)
    NotificationManagerCompat.from(context)
        .cancel(id)
}

@Composable
private fun TodayPage(
    modifier: Modifier,
    items: List<DoseItem>,
    listState: LazyListState,
    hasMedications: Boolean,
    showSetupWarning: Boolean,
    onReminderSetup: () -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onRecord: (DoseItem, String) -> Unit,
    onRecordGroup: (List<DoseItem>, String) -> Unit,
    onUndo: (DoseItem) -> Unit,
    onOpen: (DoseItem) -> Unit,
) {
    val context = LocalContext.current
    val featured = DoseGroups.featured(items)
    val taken = items.count { it.status == "taken" }
    val skipped = items.count { it.status == "skipped" }
    val percent = if (items.isEmpty()) 0 else (taken * 100f / items.size).toInt()
    val day = dateHeading(context, LocalDate.now())

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (showSetupWarning) {
            item {
                InfoBanner(
                    context.getString(R.string.reminder_setup_banner),
                    context.getString(R.string.set_up_reminders),
                    onReminderSetup
                )
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp, bottom = 3.dp)) {
                Eyebrow(day.uppercase(AppLanguages.locale(context)))
                Spacer(Modifier.height(8.dp))
                Text(
                    context.getString(R.string.every_dose_counts),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 31.sp,
                    color = TextPrimary
                )
                Text(context.getString(R.string.a_little_care_every_single_day), fontSize = 14.sp, color = TextMuted)
            }
        }
        item {
            FeaturedCard(
                featured,
                items.isNotEmpty(),
                hasMedications,
                System.currentTimeMillis(),
                onAdd,
                onRecord,
                onRecordGroup
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                border = BorderStroke(1.dp, PanelBorder),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { percent / 100f }, modifier = Modifier.fillMaxSize(),
                            color = Violet, trackColor = AccentPanel, strokeWidth = 7.dp,
                        )
                        Text("$percent%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            context.getString(R.string.today_s_progress),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            context.getString(R.string.progress_counts, taken, items.size, skipped),
                            color = TextMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    context.getString(R.string.today_s_schedule),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManage) {
                    Text(
                        context.getString(R.string.manage),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item {
                EmptyCard(
                    if (hasMedications) context.getString(R.string.no_doses_scheduled_today) else context.getString(R.string.nothing_scheduled_yet),
                    if (hasMedications) context.getString(R.string.schedule_empty_today_hint) else context.getString(R.string.schedule_empty_hint),
                    if (hasMedications) null else onAdd
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                DoseCard(item, onRecord = onRecord, onUndo = onUndo, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    group: List<DoseItem>,
    hasSchedule: Boolean,
    hasMedications: Boolean,
    nowMillis: Long,
    onAdd: () -> Unit,
    onRecord: (DoseItem, String) -> Unit,
    onRecordGroup: (List<DoseItem>, String) -> Unit,
) {
    val context = LocalContext.current
    val item = group.firstOrNull()
    val distant = DoseGroups.isDistant(group, nowMillis)
    Card(shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = BrandViolet)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(23.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = Color(0xFFE5E0FF), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (distant) context.getString(R.string.upcoming_dose) else if (item?.status == "overdue" || item?.status == "due") context.getString(
                        R.string.dose_needs_attention
                    ) else context.getString(R.string.your_next_reminder),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5E0FF),
                )
            }
            if (item != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    Instant.ofEpochMilli(item.dueMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                    fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                if (group.size > 1) {
                    Text(
                        context.getString(R.string.several_medicines, group.size), fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                }
                group.forEach { dose ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = if (group.size > 1) 10.dp else 0.dp)
                    ) {
                        Icon(
                            painterResource(dose.medication.form.iconResource()),
                            dose.medication.localizedForm(context), tint = Color.White, modifier = Modifier.size(26.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                dose.medication.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                dose.localizedDose(context) + (if (dose.medication.note.isNotBlank()) " · ${dose.medication.note}" else ""),
                                fontSize = 13.sp, color = Color(0xFFE9E5FF)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (!distant) {
                    Button(
                        onClick = { if (group.size > 1) onRecordGroup(group, "taken") else onRecord(item, "taken") },
                        modifier = if (group.size > 1) Modifier.fillMaxWidth() else Modifier,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandViolet),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            context.getString(if (group.size > 1) R.string.mark_all_as_taken else R.string.mark_as_taken),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (group.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onRecordGroup(group, "skipped") },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(context.getString(R.string.skip_all), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(23.dp))
                Text(
                    if (hasSchedule) context.getString(R.string.all_done_for_today) else if (hasMedications) context.getString(
                        R.string.no_doses_due_today
                    ) else context.getString(R.string.your_routine_starts_here),
                    fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasSchedule) context.getString(R.string.routine_complete_hint)
                    else if (hasMedications) context.getString(R.string.routine_future_hint)
                    else context.getString(R.string.routine_add_hint),
                    color = Color(0xFFE9E5FF), fontSize = 14.sp,
                )
                if (!hasMedications) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(Color.White, contentColor = BrandViolet)
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(5.dp))
                        Text(context.getString(R.string.add_medication))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoseCard(
    item: DoseItem,
    onRecord: (DoseItem, String) -> Unit,
    onUndo: (DoseItem) -> Unit,
    onOpen: (DoseItem) -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = { onOpen(item) },
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(13.dp), verticalAlignment = Alignment.Top
        ) {
            PillAvatar(item.medication.color, item.medication.form, item.medication.localizedForm(context))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(3.dp))
                Text(
                    item.medication.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.localizedDose(context) + if (item.medication.note.isNotBlank()) " · ${item.medication.note}" else "",
                    fontSize = 12.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (item.log == null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CompactAction(
                            context.getString(R.string.taken),
                            Icons.Rounded.Check,
                            onClick = { onRecord(item, "taken") })
                        CompactAction(
                            context.getString(R.string.skip),
                            null,
                            onClick = { onRecord(item, "skipped") },
                            secondary = true
                        )
                    }
                } else {
                    CompactAction(
                        context.getString(R.string.undo),
                        Icons.AutoMirrored.Rounded.Undo,
                        onClick = { onUndo(item) },
                        secondary = true
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(item.scheduledTime, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                StatusBadge(item.status)
            }
        }
    }
}

@Composable
private fun CompactAction(
    label: String,
    icon: ImageVector?,
    secondary: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (secondary) TextMuted else Violet
    Surface(
        onClick = onClick, color = if (secondary) SoftPanel else AccentPanel,
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
internal fun StatusBadge(status: String) {
    val context = LocalContext.current
    val (label, color, background) = when (status) {
        "taken" -> Triple(context.getString(R.string.taken), Mint, themeColor(0xFFE3F8EF, 0xFF1B3C33))
        "skipped" -> Triple(context.getString(R.string.skipped), TextMuted, SoftPanel)
        "due" -> Triple(context.getString(R.string.due_now), Peach, themeColor(0xFFFFF0E5, 0xFF493329))
        "overdue" -> Triple(context.getString(R.string.overdue), Peach, themeColor(0xFFFFF0E5, 0xFF493329))
        "snoozed" -> Triple(
            context.getString(R.string.snoozed),
            themeColor(0xFF456F9B, 0xFFA3C9F3),
            themeColor(0xFFEAF2FC, 0xFF26384B)
        )

        else -> Triple(context.getString(R.string.upcoming), Violet, AccentPanel)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MedicationsPage(
    modifier: Modifier,
    medications: List<Medication>,
    onAdd: () -> Unit,
    onEdit: (Medication) -> Unit,
    onToggle: (Medication) -> Unit,
    listState: LazyListState,
) {
    val context = LocalContext.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp)) {
                Eyebrow(context.getString(R.string.your_daily_routine))
                Spacer(Modifier.height(8.dp))
                Text(context.getString(R.string.my_medications), fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text(context.getString(R.string.medication_list_hint), fontSize = 14.sp, color = TextMuted)
            }
        }
        if (medications.isEmpty()) {
            item {
                EmptyCard(
                    context.getString(R.string.no_medications_yet),
                    context.getString(R.string.medication_list_empty_hint),
                    onAdd
                )
            }
        } else {
            items(medications, key = { it.id }) { med ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = BorderStroke(1.dp, PanelBorder),
                    modifier = Modifier.alpha(if (med.active) 1f else 0.6f),
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillAvatar(med.color, med.form, med.localizedForm(context))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(med.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    med.localizedDoses(context) + if (med.note.isBlank()) "" else " · ${med.note}",
                                    fontSize = 12.sp, color = TextMuted, maxLines = 2,
                                )
                            }
                            Switch(
                                checked = med.active,
                                onCheckedChange = { onToggle(med) },
                            )
                            IconButton(onClick = { onEdit(med) }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    context.getString(R.string.edit_named, med.name),
                                    tint = TextMuted,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(top = 13.dp, bottom = 9.dp))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            med.schedules.forEach { rule ->
                                Text(
                                    scheduleSummary(context, rule, med.amount, med.quantity),
                                    fontSize = 12.sp,
                                    color = Violet
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(53.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text(context.getString(R.string.add_medication), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryPage(
    modifier: Modifier,
    logs: List<DoseLog>,
    listState: LazyListState,
    onOpen: (DoseLog) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val dates = (0..6).map { today.minusDays(it.toLong()) }
    val groups = dates.mapNotNull { date ->
        val matching =
            logs.filter { it.date == date.toString() }
                .sortedByDescending { it.time }
        if (matching.isEmpty()) null else date to matching
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 12.dp)) {
                Eyebrow(context.getString(R.string.keep_your_streak_going))
                Spacer(Modifier.height(8.dp))
                Text(context.getString(R.string.dose_history), fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text(context.getString(R.string.history_hint), fontSize = 14.sp, color = TextMuted)
            }
        }
        item { DelayReportCard(logs, today) }
        if (groups.isEmpty()) {
            item {
                EmptyCard(
                    context.getString(R.string.no_history_yet),
                    context.getString(R.string.history_empty_hint),
                    null
                )
            }
        }
        groups.forEach { (date, dayLogs) ->
            item(key = "day-$date") {
                Text(
                    when (date) {
                        today -> context.getString(R.string.today)
                        today.minusDays(1) -> context.getString(R.string.yesterday)
                        else -> dateHeading(context, date)
                    },
                    color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            items(dayLogs, key = { "${it.date}|${it.medicationId}|${it.time}" }) { log ->
                Card(
                    onClick = { onOpen(log) },
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    border = BorderStroke(1.dp, PanelBorder),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (log.status == "taken") themeColor(
                                        0xFFE3F8EF,
                                        0xFF1B3C33
                                    ) else SoftPanel
                                ),
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
                            Text(
                                context.getString(R.string.scheduled_dose, log.time, log.dose),
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            if (log.onTime) Text(context.getString(R.string.on_time), color = Violet, fontSize = 12.sp)
                            DoseReports.timing(log)
                                ?.let { timing ->
                                    val actual = Instant.ofEpochMilli(log.recordedAt)
                                        .atZone(ZoneId.systemDefault())
                                        .format(
                                            DateTimeFormatter.ofPattern(
                                                "MMM d, HH:mm",
                                                AppLanguages.locale(context)
                                            )
                                        )
                                    val difference =
                                        delayDuration(
                                            context,
                                            kotlin.math.abs(timing.differenceMillis)
                                                .toDouble()
                                        )
                                    Text(
                                        context.getString(
                                            R.string.taken_timing,
                                            actual,
                                            context.getString(if (timing.differenceMillis < 0) R.string.early else R.string.delay),
                                            difference
                                        ), color = TextMuted, fontSize = 12.sp
                                    )
                                }
                        }
                        Text(
                            if (log.status == "taken") context.getString(R.string.taken) else context.getString(R.string.skipped),
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
    Text(
        text,
        color = themeColor(0xFF796599, 0xFFB9A8DB),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp
    )
}

@Composable
internal fun PillAvatar(
    color: String,
    form: MedicationForm,
    label: String
) {
    val (foreground, background) = palette(color)
    Box(
        // Inset the fill so its antialiased edge cannot show outside the border.
        Modifier
            .size(49.dp)
            .background(PanelBorder, RoundedCornerShape(16.dp))
            .padding(1.dp)
            .background(background, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(form.iconResource()),
            label,
            tint = foreground,
            modifier = Modifier.size(26.dp)
        )
    }
}

internal fun palette(color: String): Pair<Color, Color> {
    val (foreground, background) = MedicationIconColors.palette(color)
    return Color(foreground) to Color(background)
}

@Composable
private fun InfoBanner(
    message: String,
    action: String,
    onAction: () -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = AccentPanel) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = Violet, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(action, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    explanation: String,
    onAdd: (() -> Unit)?
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Medication, null, modifier = Modifier.size(35.dp), tint = Violet)
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(explanation, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (onAdd != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onAdd) { Text(context.getString(R.string.add_medication)) }
            }
        }
    }
}
