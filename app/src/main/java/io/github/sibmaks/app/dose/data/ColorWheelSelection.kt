package io.github.sibmaks.app.dose.data

import java.util.Locale
import kotlin.math.*

/** Hue follows the circle clockwise; saturation is distance from the white center. */
data class ColorWheelSelection(val hue: Float, val saturation: Float, val brightness: Float) {
    init {
        require(hue.isFinite() && hue >= 0f && hue < 360f)
        require(saturation in 0f..1f && brightness in 0f..1f)
    }

    fun atPoint(dx: Float, dy: Float, radius: Float): ColorWheelSelection {
        require(radius > 0f && radius.isFinite() && dx.isFinite() && dy.isFinite())
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val angle = if (distance == 0f) hue else ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat() % 360f
        return copy(hue = angle, saturation = (distance / radius).coerceIn(0f, 1f))
    }

    fun point(radius: Float): Pair<Float, Float> {
        val angle = Math.toRadians(hue.toDouble())
        return (cos(angle) * saturation * radius).toFloat() to (sin(angle) * saturation * radius).toFloat()
    }

    fun hex(): String {
        val chroma = brightness * saturation
        val segment = hue / 60f
        val second = chroma * (1f - abs(segment % 2f - 1f))
        val channels = when (segment.toInt()) {
            0 -> floatArrayOf(chroma, second, 0f)
            1 -> floatArrayOf(second, chroma, 0f)
            2 -> floatArrayOf(0f, chroma, second)
            3 -> floatArrayOf(0f, second, chroma)
            4 -> floatArrayOf(second, 0f, chroma)
            else -> floatArrayOf(chroma, 0f, second)
        }.map { ((it + brightness - chroma) * 255f).roundToInt().coerceIn(0, 255) }
        return String.format(Locale.ROOT, "#%02X%02X%02X", channels[0], channels[1], channels[2])
    }

    companion object {
        fun fromColor(color: String): ColorWheelSelection {
            val rgb = MedicationColors.hex(color).drop(1).toInt(16)
            val red = (rgb shr 16 and 255) / 255f
            val green = (rgb shr 8 and 255) / 255f
            val blue = (rgb and 255) / 255f
            val high = maxOf(red, green, blue)
            val low = minOf(red, green, blue)
            val delta = high - low
            val hue = when {
                delta == 0f -> 0f
                high == red -> ((green - blue) / delta) % 6f
                high == green -> (blue - red) / delta + 2f
                else -> (red - green) / delta + 4f
            } * 60f
            return ColorWheelSelection((hue + 360f) % 360f, if (high == 0f) 0f else delta / high, high)
        }
    }
}
