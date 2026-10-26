package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sibmaks.app.dose.R

/** A picker action with Material field decoration, without editable-text or keyboard semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutlinedPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    icon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    errorMessage: String? = null,
    supportingError: Boolean = false,
    containerColor: Color = Panel,
) {
    val interactions = remember { MutableInteractionSource() }
    val display = value.ifBlank { placeholder }
    val invalid = errorMessage != null
    val background = if (invalid) Semantic.invalidFieldContainer else containerColor
    val shape = RoundedCornerShape(15.dp)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = background,
        unfocusedContainerColor = background,
        errorContainerColor = background,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 56.dp)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button,
                    onClickLabel = label, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOf(label, display).filter { it.isNotBlank() }.joinToString(", ")
                    if (errorMessage != null) error(errorMessage)
                },
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true,
        ) {
            OutlinedTextFieldDefaults.DecorationBox(
                value = display,
                innerTextField = {
                    Text(display, fontSize = 15.sp,
                        fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (value.isBlank()) TextMuted else TextPrimary,
                        modifier = Modifier.clearAndSetSemantics {})
                },
                enabled = true,
                singleLine = false,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
                isError = invalid,
                label = { Text(label, modifier = Modifier.clearAndSetSemantics {}) },
                trailingIcon = if (onClear != null) ({
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.clear_bound, label),
                            tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }) else icon?.let { fieldIcon ->
                    { Icon(fieldIcon, null, tint = TextMuted, modifier = Modifier.size(20.dp)) }
                },
                colors = colors,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true, isError = invalid, interactionSource = interactions,
                        colors = colors, shape = shape,
                    )
                },
            )
        }
        if (supportingError && errorMessage != null) Text(
            errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
