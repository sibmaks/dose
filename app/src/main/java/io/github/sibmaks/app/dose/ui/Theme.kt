package io.github.sibmaks.app.dose.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.ThemeMode

internal val BrandViolet = Color(0xFF7163E5)
internal val Violet: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Page: Color @Composable get() = MaterialTheme.colorScheme.background
internal val PanelBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
internal val Panel: Color @Composable get() = MaterialTheme.colorScheme.surface
internal val SoftPanel: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
internal val AccentPanel: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val Mint: Color @Composable get() = themeColor(0xFF237C62, 0xFF7BD5B5)
internal val Peach: Color @Composable get() = themeColor(0xFFAD5726, 0xFFFFB78E)
internal val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary

internal data class Appearance(val mode: ThemeMode, val dark: Boolean, val select: (ThemeMode) -> Unit)
internal val LocalAppearance = staticCompositionLocalOf { Appearance(ThemeMode.SYSTEM, false) {} }

@Composable
internal fun themeColor(light: Long, dark: Long): Color = Color(if (LocalAppearance.current.dark) dark else light)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6958D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFF0ECFF), onPrimaryContainer = Color(0xFF5141BE),
    secondary = Color(0xFF655C86), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE8F7), onSecondaryContainer = Color(0xFF282039),
    background = Color(0xFFFAFBFE), onBackground = Color(0xFF252B43),
    surface = Color.White, onSurface = Color(0xFF252B43),
    surfaceVariant = Color(0xFFF1F2F7), onSurfaceVariant = Color(0xFF62677D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FD),
    surfaceContainer = Color(0xFFF3F3FA), surfaceContainerHigh = Color(0xFFEDEEF6),
    surfaceContainerHighest = Color(0xFFE7E8F0),
    outline = Color(0xFF83899D), outlineVariant = Color(0xFFEBECF5),
    error = Color(0xFFBA1A1A), onError = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B9FF), onPrimary = Color(0xFF302367),
    primaryContainer = Color(0xFF342D52), onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCDC2E2), onSecondary = Color(0xFF342D43),
    secondaryContainer = Color(0xFF494057), onSecondaryContainer = Color(0xFFEADDFA),
    background = Color(0xFF12131B), onBackground = Color(0xFFE6E5F0),
    surface = Color(0xFF1C1D28), onSurface = Color(0xFFE6E5F0),
    surfaceVariant = Color(0xFF30313F), onSurfaceVariant = Color(0xFFB7B6C9),
    surfaceContainerLowest = Color(0xFF101119), surfaceContainerLow = Color(0xFF232430),
    surfaceContainer = Color(0xFF282935), surfaceContainerHigh = Color(0xFF30313F),
    surfaceContainerHighest = Color(0xFF393A48),
    outline = Color(0xFF8E8DA0), outlineVariant = Color(0xFF393A49),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

internal fun doseColorScheme(dark: Boolean): ColorScheme = if (dark) DarkColors else LightColors

@Composable
fun DoseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { AppAppearance.preferences(context) }
    var mode by remember(preferences) { mutableStateOf(AppAppearance.mode(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppAppearance.MODE) mode = AppAppearance.mode(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = mode.isDark(isSystemInDarkTheme())
    val activity = context.findActivity() as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0xE612131B.toInt()) { dark },
        )
    }
    CompositionLocalProvider(LocalAppearance provides Appearance(mode, dark) { selected ->
        mode = selected
        AppAppearance.select(context, selected)
    }) {
        MaterialTheme(colorScheme = doseColorScheme(dark), content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
