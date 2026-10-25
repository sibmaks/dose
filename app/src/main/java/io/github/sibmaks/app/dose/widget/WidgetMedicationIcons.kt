package io.github.sibmaks.app.dose.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.data.MedicationIconColors
import io.github.sibmaks.app.dose.ui.iconResource

/** RemoteViews receives finished badges, preserving each color and pale-icon contrast. */
internal object WidgetMedicationIcons {
    fun render(
        context: Context,
        medications: List<Medication>
    ): Bitmap {
        val shown =
            medications.distinctBy { it.id }
                .take(3)
                .ifEmpty { listOf(null) }
        val scale = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * scale).toInt()
            .coerceAtLeast(1)

        val size = px(38)
        val badge = if (shown.size > 1) px(26) else size
        val step = px(6)
        val origin = (size - badge - step * (shown.size - 1)) / 2
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        shown.forEachIndexed { index, medication ->
            val left = origin + index * step
            val (icon, background) = MedicationIconColors.palette(medication?.color ?: "violet")
            paint.color = background
            canvas.drawRoundRect(
                left.toFloat(),
                left.toFloat(),
                (left + badge).toFloat(),
                (left + badge).toFloat(),
                px(10).toFloat(),
                px(10).toFloat(),
                paint
            )
            ContextCompat.getDrawable(context, medication?.form?.iconResource() ?: R.drawable.ic_notification)
                ?.mutate()
                ?.let {
                    it.setTint(icon)
                    val inset = px(if (shown.size > 1) 5 else 7)
                    it.setBounds(left + inset, left + inset, left + badge - inset, left + badge - inset)
                    it.draw(canvas)
                }
        }
        return bitmap
    }
}
