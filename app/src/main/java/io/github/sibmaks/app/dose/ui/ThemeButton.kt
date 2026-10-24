package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ThemeMode

@Composable
internal fun ThemeButton() {
    val appearance = LocalAppearance.current
    var expanded by remember { mutableStateOf(false) }
    val currentName = stringResource(appearance.mode.label())
    Box {
        IconButton(onClick = { expanded = true }) {
            ThemeIcon(appearance.mode, stringResource(R.string.change_theme, currentName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.label())) },
                    leadingIcon = { ThemeIcon(mode, null) },
                    trailingIcon = { if (appearance.mode == mode) Icon(Icons.Rounded.Check, null) },
                    onClick = { expanded = false; appearance.select(mode) })
            }
        }
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun ThemeIcon(
    mode: ThemeMode,
    description: String?
) {
    when (mode) {
        ThemeMode.SYSTEM -> Icon(painterResource(R.drawable.ic_theme_system), description, tint = TextMuted)
        ThemeMode.LIGHT -> Icon(Icons.Rounded.LightMode, description, tint = TextMuted)
        ThemeMode.DARK -> Icon(Icons.Rounded.DarkMode, description, tint = TextMuted)
    }
}
