package io.github.sibmaks.app.dose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
            color = Panel, shape = DoseShapes.shell,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(DoseSpacing.shellMargin)
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = DoseSpacing.shellInset, end = DoseSpacing.controlGap, top = DoseSpacing.controlGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = DoseText.shellTitle, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close_screen)) }
                }
                HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.extraSmall))
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(DoseSpacing.small), content = content
                )
                if (showSecondary || primaryLabel != null) Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DoseSpacing.shellInset, vertical = DoseSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(DoseSpacing.controlGap)
                ) {
                    if (showSecondary) OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = DoseDimensions.dialogAction),
                        shape = DoseShapes.button
                    ) {
                        Text(secondaryLabel, fontWeight = FontWeight.Bold)
                    }
                    if (primaryLabel != null) Button(
                        onClick = onPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = DoseDimensions.dialogAction),
                        shape = DoseShapes.button
                    ) {
                        Text(primaryLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
