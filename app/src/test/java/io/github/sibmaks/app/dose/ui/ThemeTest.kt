package io.github.sibmaks.app.dose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.sibmaks.app.dose.data.MedicationIconColors
import io.github.sibmaks.app.dose.data.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class ThemeTest {
    @Test fun `system is the default and tracks Android while explicit modes override it`() {
        for (preference in listOf(null, "", "unknown", "system")) {
            val mode = ThemeMode.fromPreference(preference)
            assertFalse(mode.isDark(false))
            assertTrue(mode.isDark(true))
        }
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.fromPreference("light").isDark(systemDark))
            assertTrue(ThemeMode.fromPreference("dark").isDark(systemDark))
        }
    }

    @Test fun `body text controls and secondary labels stay readable in both palettes`() {
        for (dark in listOf(false, true)) {
            val colors = doseColorScheme(dark)
            val pairs = listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface,
                colors.onSurfaceVariant to colors.surfaceContainerLow,
                colors.primary to colors.surface,
                colors.primary to colors.primaryContainer,
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.error to colors.surface,
            )
            pairs.forEach { (foreground, background) ->
                assertTrue("dark=$dark foreground=$foreground background=$background", contrast(foreground, background) >= 4.5)
            }
            assertEquals(dark, colors.background.luminanceIsDark())
        }
    }

    private fun contrast(first: Color, second: Color) = MedicationIconColors.contrast(first.toArgb(), second.toArgb())
    private fun Color.luminanceIsDark() = contrast(this, Color.White) > contrast(this, Color.Black)
}
