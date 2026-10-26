package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Keep the label and switch state on one accessible action, with the existing switch visuals. */
@Composable
internal fun LabeledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = interactions, indication = null,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = interactions)
    }
}
