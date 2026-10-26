package io.github.sibmaks.app.dose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Semantic states outside Material's base scheme, shared by Compose and native surfaces. */
internal data class SemanticColors(
    val success: Color, val successContainer: Color,
    val attention: Color, val attentionContainer: Color,
    val resolved: Color, val resolvedContainer: Color, val resolvedBorder: Color, val resolvedProgress: Color,
    val warning: Color, val warningContainer: Color, val warningBorder: Color, val warningIcon: Color,
    val snooze: Color, val snoozeContainer: Color,
    val pauseAvailableContainer: Color, val pauseAvailableBorder: Color,
    val pauseActiveContainer: Color, val pauseActiveBorder: Color,
    val eyebrow: Color, val invalidFieldContainer: Color,
)

private val LightSemanticColors = SemanticColors(
    success = Color(0xFF237C62), successContainer = Color(0xFFE3F8EF),
    attention = Color(0xFFAD5726), attentionContainer = Color(0xFFFFF0E5),
    resolved = Color(0xFF1E6B45), resolvedContainer = Color(0xFFEFF9F3),
    resolvedBorder = Color(0xFFD4EBDD), resolvedProgress = Color(0xFF27A468),
    warning = Color(0xFF7A600E), warningContainer = Color(0xFFFDF4DC),
    warningBorder = Color(0xFFF0E2B8), warningIcon = Color(0xFFB8830F),
    snooze = Color(0xFF456F9B), snoozeContainer = Color(0xFFEAF2FC),
    pauseAvailableContainer = Color(0xFFF4FBF7), pauseAvailableBorder = Color(0xFFBFE3D2),
    pauseActiveContainer = Color(0xFFF0EDFB), pauseActiveBorder = Color(0xFFD8CFF6),
    eyebrow = Color(0xFF796599), invalidFieldContainer = Color(0xFFFFF6F4),
)
private val DarkSemanticColors = SemanticColors(
    success = Color(0xFF7BD5B5), successContainer = Color(0xFF1B3C33),
    attention = Color(0xFFFFB78E), attentionContainer = Color(0xFF493329),
    resolved = Color(0xFF8FE3BC), resolvedContainer = Color(0xFF1E3A2C),
    resolvedBorder = Color(0xFF2C5240), resolvedProgress = Color(0xFF2FA468),
    warning = Color(0xFFF2CE74), warningContainer = Color(0xFF3A3118),
    warningBorder = Color(0xFF5A4C22), warningIcon = Color(0xFFEBCC6B),
    snooze = Color(0xFFA3C9F3), snoozeContainer = Color(0xFF26384B),
    pauseAvailableContainer = Color(0xFF16281F), pauseAvailableBorder = Color(0xFF2E5C46),
    pauseActiveContainer = Color(0xFF2A2545), pauseActiveBorder = Color(0xFF4A3F73),
    eyebrow = Color(0xFFB9A8DB), invalidFieldContainer = Color(0xFF3A2A24),
)

internal fun doseSemanticColors(dark: Boolean): SemanticColors = if (dark) DarkSemanticColors else LightSemanticColors
internal val Semantic: SemanticColors @Composable get() = doseSemanticColors(LocalAppearance.current.dark)
