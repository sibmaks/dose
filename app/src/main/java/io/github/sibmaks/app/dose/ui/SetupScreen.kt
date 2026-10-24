package io.github.sibmaks.app.dose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R

/** Full page with the same card, spacing, typography, and balanced actions as medication entry. */
@Composable
internal fun SetupScreen(
    title: String,
    onClose: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String,
    onSecondary: () -> Unit = onClose,
    onBack: () -> Unit = onClose,
    pageKey: Any? = null,
    showSecondary: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    LaunchedEffect(pageKey) { scroll.scrollTo(0) }
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
                        .padding(start = 20.dp, end = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close_screen)) }
                }
                HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content
                )
                if (showSecondary || primaryLabel != null) Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showSecondary) OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 51.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(secondaryLabel, fontWeight = FontWeight.Bold)
                    }
                    if (primaryLabel != null) Button(
                        onClick = onPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 51.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(primaryLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
