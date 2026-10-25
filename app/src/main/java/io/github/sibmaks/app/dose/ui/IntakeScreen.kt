package io.github.sibmaks.app.dose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.DoseLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-page intake screen: back navigation, centered title, optional overflow menu, bottom-anchored actions. */
@Composable
internal fun IntakeScreen(
    title: String,
    onClose: () -> Unit,
    showMenu: Boolean = false,
    menuContent: @Composable ColumnScope.() -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onClose)
    val scroll = rememberScrollState()
    Surface(color = Page, modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Panel, shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(12.dp)
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                    Text(
                        title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                    if (showMenu) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(R.string.intake_menu_accessibility))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                content = menuContent
                            )
                        }
                    } else Spacer(Modifier.width(48.dp))
                }
                HorizontalDivider(
                    color = PanelBorder,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                if (bottomBar != null) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { bottomBar() }
                }
            }
        }
    }
}

/** Medication context shared by the details and editing screens; works for deleted medications too. */
@Composable
internal fun IntakeMedicationCard(
    log: DoseLog,
    scheduledAt: Long?,
    momentFormatter: DateTimeFormatter
) {
    val zone = ZoneId.systemDefault()
    Surface(
        color = SoftPanel, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PanelBorder), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(AccentPanel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Medication, null, tint = Violet, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        log.medicationName, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(log.dose, fontSize = 13.sp, color = TextMuted)
                }
            }
            if (scheduledAt != null) {
                HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(top = 12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.scheduled_intake), fontSize = 13.sp,
                        color = TextMuted, modifier = Modifier.weight(1f),
                    )
                    Text(
                        Instant.ofEpochMilli(scheduledAt)
                            .atZone(zone)
                            .format(momentFormatter),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
