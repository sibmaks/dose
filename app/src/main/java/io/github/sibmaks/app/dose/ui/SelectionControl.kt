package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A single action owns the visual choice's state; its indicator remains decorative. */
internal fun Modifier.selectionControl(
    selected: Boolean,
    multiple: Boolean = false,
    onClick: () -> Unit,
): Modifier = minimumInteractiveComponentSize().then(
    if (multiple) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
)
