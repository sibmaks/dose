package io.github.sibmaks.app.dose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.sibmaks.app.dose.data.MedicationIconColors
import io.github.sibmaks.app.dose.data.ThemeMode
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    @Test fun `small text on brand and status containers meets normal text contrast`() {
        assertTrue(contrast(OnBrandViolet, BrandViolet) >= 4.5)
        for (dark in listOf(false, true)) {
            val colors = doseStatusColors(dark)
            listOf(
                colors.success to colors.successContainer,
                colors.attention to colors.attentionContainer,
                colors.resolved to colors.resolvedContainer,
            ).forEach { (foreground, background) ->
                assertTrue("dark=$dark foreground=$foreground background=$background", contrast(foreground, background) >= 4.5)
            }
        }
    }

    @Test fun `shipping widget small text remains readable on its actual backgrounds`() {
        val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        fun elements(path: String, tag: String): List<Element> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(resources, path)).getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }
        val colors = elements("values/colors.xml", "color").associate { it.getAttribute("name") to it.textContent.trim() }
        fun parseColor(value: String): Color {
            val hex = if (value.startsWith("@color/")) colors.getValue(value.substringAfter('/')) else value
            return Color((hex.removePrefix("#").toLong(16) or 0xFF000000).toInt())
        }
        fun background(path: String) = parseColor(elements(path, "solid").single().getAttribute("android:color"))
        for ((layout, fill) in listOf(
            "widget_upcoming_dose" to "widget_background",
            "widget_dose_list" to "widget_list_background",
        )) {
            val surface = background("drawable/$fill.xml")
            elements("layout/$layout.xml", "TextView").forEach { view ->
                val size = view.getAttribute("android:textSize").removeSuffix("sp").toFloat()
                val large = size >= if (view.getAttribute("android:textStyle") == "bold") 14f else 18f
                val foreground = parseColor(view.getAttribute("android:textColor"))
                assertTrue("${view.getAttribute("android:id")} on $fill", contrast(foreground, surface) >= if (large) 3.0 else 4.5)
            }
        }
    }

    private fun contrast(first: Color, second: Color) = MedicationIconColors.contrast(first.toArgb(), second.toArgb())
    private fun Color.luminanceIsDark() = contrast(this, Color.White) > contrast(this, Color.Black)
}
