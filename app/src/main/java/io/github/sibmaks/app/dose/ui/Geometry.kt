package io.github.sibmaks.app.dose.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Default spacing steps plus existing, intentional surface insets. */
internal object DoseSpacing {
    val tiny = 4.dp
    val compactGap = 6.dp
    val extraSmall = 8.dp
    val controlGap = 10.dp
    val small = 12.dp
    val cardInset = 14.dp
    val medium = 16.dp
    val large = 20.dp
    val extraLarge = 24.dp
    val pageGutter = 21.dp
    val shellMargin = small
    val shellInset = large
    val heroInset = 23.dp
}

/** Shared radii also consumed by the native reminder popup. */
internal object DoseRadii {
    val badge = 8.dp
    val choice = 12.dp
    val button = 14.dp
    val field = 15.dp
    val tile = 16.dp
    val panel = 18.dp
    val card = 20.dp
    val largePanel = 24.dp
    val shell = 26.dp
    val hero = 27.dp
    val nativeAction = 28.dp
}

internal object DoseShapes {
    val badge = RoundedCornerShape(DoseRadii.badge)
    val choice = RoundedCornerShape(DoseRadii.choice)
    val button = RoundedCornerShape(DoseRadii.button)
    val field = RoundedCornerShape(DoseRadii.field)
    val tile = RoundedCornerShape(DoseRadii.tile)
    // Fill inset by the border width so its antialiased edge stays inside the tile.
    val tileInset = RoundedCornerShape(DoseRadii.tile - DoseDimensions.border)
    val panel = RoundedCornerShape(DoseRadii.panel)
    val card = RoundedCornerShape(DoseRadii.card)
    val shell = RoundedCornerShape(DoseRadii.shell)
    val hero = RoundedCornerShape(DoseRadii.hero)
    val pill = CircleShape
}

internal object DoseDimensions {
    val border = 1.dp
    val selectionBorder = 1.5.dp
    val touchTarget = 48.dp
    val segmentedControl = 50.dp
    val dialogAction = 51.dp
    val primaryAction = 53.dp
    val field = 56.dp
    val medicationTile = 49.dp
    val medicationGlyph = 26.dp
}

internal val DoseMaterialShapes = Shapes(
    extraSmall = DoseShapes.badge,
    small = DoseShapes.choice,
    medium = DoseShapes.tile,
    large = DoseShapes.card,
    extraLarge = DoseShapes.shell,
)
