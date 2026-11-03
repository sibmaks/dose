package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep related fields together on phones, but give each a full line when text or space needs it. */
@Composable
internal fun ResponsiveFields(
    gap: Dp = DoseSpacing.extraSmall,
    content: @Composable RowScope.(heightModifier: Modifier) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (largeText || maxWidth < 320.dp) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 1,
                verticalArrangement = Arrangement.spacedBy(DoseSpacing.medium),
            ) { content(Modifier) }
        } else {
            // Measure paired fields at their actual weighted widths before matching heights.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) { content(Modifier.fillMaxHeight()) }
        }
    }
}
