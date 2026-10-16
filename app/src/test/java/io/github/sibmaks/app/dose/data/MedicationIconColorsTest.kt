package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class MedicationIconColorsTest {
    @Test fun `white and pale icons keep their chosen color against a contrasting badge`() {
        for (color in listOf("#FFFFFF", "#FFFFFE", "#FFFF00", "#F8F0FF", "#EEEEEE")) {
            val (icon, background) = MedicationIconColors.palette(color)
            assertEquals(color.drop(1).toInt(16) or 0xFF000000.toInt(), icon)
            assertTrue(MedicationIconColors.contrast(icon, background) >= 3.0)
            assertNotEquals(icon, background)
        }
    }
    @Test fun `dark icons retain a pale background`() {
        val (icon, background) = MedicationIconColors.palette("#000000")
        assertEquals(0xFF000000.toInt(), icon)
        assertEquals(0xFFDEDEDE.toInt(), background)
    }
    @Test fun `contrast calculation matches known neutral and primary pairs`() {
        assertEquals(21.0, MedicationIconColors.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.00001)
        assertEquals(1.0, MedicationIconColors.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0)
        assertEquals(3.99848, MedicationIconColors.contrast(0xFFFF0000.toInt(), 0xFFFFFFFF.toInt()), 0.00001)
    }
    @Test fun `all sampled colors and presets produce readable icons without altering their color`() {
        val colors = MedicationColors.defaults + buildList {
            for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) add("#%02X%02X%02X".format(red, green, blue))
        }
        colors.forEach { color ->
            val (icon, background) = MedicationIconColors.palette(color)
            assertEquals(MedicationColors.hex(color).drop(1).toInt(16) or 0xFF000000.toInt(), icon)
            assertTrue("$color is not visible", MedicationIconColors.contrast(icon, background) >= 3.0)
        }
    }
}
