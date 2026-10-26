package io.github.sibmaks.app.dose.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Existing hierarchy, with small half-size variants consolidated into shared roles. */
internal object DoseText {
    private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
    )
    val label = style(11, 16, FontWeight.Medium)
    val caption = style(12, 18)
    val supporting = style(13, 18)
    val body = style(14, 20)
    val itemTitle = style(15, 22, FontWeight.Bold)
    val input = style(16, 24)
    val contextTitle = style(16, 24, FontWeight.Bold)
    val emphasis = style(18, 24, FontWeight.Bold)
    val sectionTitle = style(19, 26, FontWeight.ExtraBold)
    val detailTitle = style(20, 28, FontWeight.ExtraBold)
    val shellTitle = style(21, 28, FontWeight.ExtraBold)
    val emptyHero = style(23, 30, FontWeight.ExtraBold)
    val brand = style(24, 30, FontWeight.ExtraBold)
    val brandDot = style(27, 32, FontWeight.ExtraBold)
    val metric = style(30, 38, FontWeight.ExtraBold)
    val pageTitle = style(31, 40, FontWeight.ExtraBold)
    val heroTime = style(38, 46, FontWeight.ExtraBold)
    val eyebrow = label.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
}

internal val DoseTypography = Typography(
    displayLarge = DoseText.heroTime,
    displayMedium = DoseText.metric,
    displaySmall = DoseText.brand,
    headlineLarge = DoseText.pageTitle,
    headlineMedium = DoseText.emptyHero,
    headlineSmall = DoseText.brand.copy(fontWeight = FontWeight.Normal),
    titleLarge = DoseText.shellTitle,
    titleMedium = DoseText.contextTitle,
    titleSmall = DoseText.itemTitle,
    bodyLarge = DoseText.input,
    bodyMedium = DoseText.body,
    bodySmall = DoseText.caption,
    labelLarge = DoseText.body.copy(fontWeight = FontWeight.Medium),
    labelMedium = DoseText.caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = DoseText.label,
)
