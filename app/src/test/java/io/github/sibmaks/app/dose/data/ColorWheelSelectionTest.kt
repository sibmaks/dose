package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ColorWheelSelectionTest {
    @Test fun `wheel hue matches its clockwise gradient`() {
        val start = ColorWheelSelection(0f, 1f, 1f)
        assertEquals("#FF0000", start.atPoint(100f, 0f, 100f).hex())
        assertEquals("#80FF00", start.atPoint(0f, 100f, 100f).hex())
        assertEquals("#00FFFF", start.atPoint(-100f, 0f, 100f).hex())
        assertEquals("#8000FF", start.atPoint(0f, -100f, 100f).hex())
    }
    @Test fun `center is white edge is saturated and touches outside clamp to edge`() {
        val start = ColorWheelSelection(120f, 1f, 1f)
        val center = start.atPoint(0f, 0f, 100f)
        assertEquals("#FFFFFF", center.hex())
        assertEquals(120f, center.hue, 0f)
        assertEquals("#FF8080", start.atPoint(50f, 0f, 100f).hex())
        assertEquals("#FF0000", start.atPoint(1000f, 0f, 100f).hex())
    }
    @Test fun `brightness includes black without losing selected hue or saturation`() {
        val selected = ColorWheelSelection(240f, 1f, 1f)
        val dark = selected.copy(brightness = 0f).atPoint(100f, 0f, 100f)
        assertEquals("#000000", dark.hex())
        assertEquals("#FF0000", dark.copy(brightness = 1f).hex())
        assertEquals("#808080", ColorWheelSelection(20f, 0f, 0.5f).hex())
    }
    @Test fun `hex colors and preset colors round trip without changing stored RGB`() {
        (MedicationColors.defaults.map(MedicationColors::hex) + listOf("#FFFFFF", "#000000", "#808080", "#123456")).forEach {
            assertEquals(it, ColorWheelSelection.fromColor(it).hex())
        }
        for (red in listOf(0, 64, 128, 192, 255)) for (green in listOf(0, 64, 128, 192, 255)) for (blue in listOf(0, 64, 128, 192, 255)) {
            val hex = "#%02X%02X%02X".format(red, green, blue)
            assertEquals(hex, ColorWheelSelection.fromColor(hex).hex())
        }
    }
    @Test fun `selection marker points back to the selected hue and saturation`() {
        for (hue in listOf(0f, 30f, 120f, 240f, 359f)) {
            val selected = ColorWheelSelection(hue, 0.6f, 0.8f)
            val (x, y) = selected.point(100f)
            val restored = selected.atPoint(x, y, 100f)
            assertTrue(abs(restored.hue - hue) < 0.001f)
            assertEquals(selected.saturation, restored.saturation, 0.001f)
            assertEquals(selected.brightness, restored.brightness, 0f)
        }
    }
}
