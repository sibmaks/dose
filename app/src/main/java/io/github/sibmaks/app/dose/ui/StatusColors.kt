package io.github.sibmaks.app.dose.ui

import androidx.compose.ui.graphics.Color

/** Full-opacity text and its container must remain readable together. */
internal data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val resolved: Color,
    val resolvedContainer: Color,
)

private val LightStatusColors = StatusColors(
    Color(0xFF237C62), Color(0xFFE3F8EF),
    Color(0xFFAD5726), Color(0xFFFFF0E5),
    Color(0xFF1E6B45), Color(0xFFEFF9F3),
)
private val DarkStatusColors = StatusColors(
    Color(0xFF7BD5B5), Color(0xFF1B3C33),
    Color(0xFFFFB78E), Color(0xFF493329),
    Color(0xFF8FE3BC), Color(0xFF1E3A2C),
)

internal fun doseStatusColors(dark: Boolean): StatusColors = if (dark) DarkStatusColors else LightStatusColors
