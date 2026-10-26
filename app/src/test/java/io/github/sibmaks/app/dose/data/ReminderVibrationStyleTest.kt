package io.github.sibmaks.app.dose.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderVibrationStyleTest {
    @Test fun `unknown preference falls back to the default pattern`() {
        assertEquals(ReminderVibrationStyle.DEFAULT, ReminderVibrationStyle.fromPreference(null))
        assertEquals(ReminderVibrationStyle.DEFAULT, ReminderVibrationStyle.fromPreference(""))
        assertEquals(ReminderVibrationStyle.DEFAULT, ReminderVibrationStyle.fromPreference("removed"))
    }

    @Test fun `every stored preference round-trips and at least five styles are offered`() {
        ReminderVibrationStyle.entries.forEach { style ->
            assertEquals(style, ReminderVibrationStyle.fromPreference(style.preference))
        }
        assertTrue(ReminderVibrationStyle.entries.size >= 5)
    }

    @Test fun `default timings keep the original double buzz`() {
        assertArrayEquals(longArrayOf(0, 180, 100, 180), ReminderVibrationStyle.DEFAULT.timings)
    }

    @Test fun `waveforms pair timings with amplitudes, alternating off and on steps`() {
        ReminderVibrationStyle.entries.forEach { style ->
            assertEquals(style.timings.size, style.amplitudes.size)
            style.timings.forEachIndexed { index, timing ->
                val on = index % 2 == 1
                val amplitude = style.amplitudes[index]
                assertTrue("$style step $index timing", if (on) timing > 0 else timing >= 0)
                if (on) assertTrue("$style step $index amplitude", amplitude in 1..255)
                else assertEquals("$style step $index amplitude", 0, amplitude)
            }
        }
    }

    @Test fun `game styles stay under two seconds so a reminder never drones on`() {
        val gameStyles = listOf(
            ReminderVibrationStyle.GAME,
            ReminderVibrationStyle.GALLOP,
            ReminderVibrationStyle.ORBS,
            ReminderVibrationStyle.MISSION_PASSED,
            ReminderVibrationStyle.HEAVY_MARCH
        )
        gameStyles.forEach { style ->
            val duration = style.timings.sum()
            assertTrue("$style duration $duration", duration in 200..2_000)
        }
    }
}
