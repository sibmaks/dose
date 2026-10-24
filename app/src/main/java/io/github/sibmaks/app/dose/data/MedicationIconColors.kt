package io.github.sibmaks.app.dose.data

import kotlin.math.pow
import kotlin.math.roundToInt

/** Keep the chosen icon color, switching its badge background when a pale tint hides it. */
object MedicationIconColors {
    fun palette(color: String): Pair<Int, Int> {
        val icon =
            MedicationColors.hex(color)
                .drop(1)
                .toInt(16) or 0xFF000000.toInt()

        fun tint(shift: Int) = (255 + (((icon ushr shift) and 255) - 255) * 0.13f).roundToInt()
        val pale = 0xFF000000.toInt() or (tint(16) shl 16) or (tint(8) shl 8) or tint(0)
        val background = if (contrast(icon, pale) >= 3.0) pale else 0xFF252B43.toInt()
        return icon to background
    }

    fun contrast(
        first: Int,
        second: Int
    ): Double {
        val firstLight = luminance(first)
        val secondLight = luminance(second)
        return (maxOf(firstLight, secondLight) + 0.05) / (minOf(firstLight, secondLight) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun linear(shift: Int): Double {
            val channel = ((color ushr shift) and 255) / 255.0
            return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }
}
