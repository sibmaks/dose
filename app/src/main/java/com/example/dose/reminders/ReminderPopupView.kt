package com.example.dose.reminders

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.example.dose.data.Medication
import com.example.dose.ui.iconResource
import com.example.dose.data.MedicationColors

/** Explicit shapes and colors keep the pop-up consistent across device button themes. */
internal object ReminderPopupView {
    private val ink = Color.rgb(37, 43, 67)
    private val muted = Color.rgb(111, 116, 139)
    private val violet = Color.rgb(113, 99, 229)
    private val lavender = Color.rgb(242, 239, 255)
    private val border = Color.rgb(231, 228, 245)

    data class Entry(val medication: Medication, val time: String, val onAction: (String) -> Unit)

    fun create(context: Context, preview: Boolean, entries: List<Entry>, keepNotification: Boolean, onLater: () -> Unit): ScrollView {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        fun shape(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
        fun text(value: String, size: Float, bold: Boolean = false, color: Int = ink) = TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        fun pill(label: String, primary: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) = Button(context).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(48)
            minimumHeight = dp(48)
            maxLines = 2
            setPadding(dp(14), dp(12), dp(14), dp(12))
            // Clear OEM tint, inset background, and raised-button animation before applying our pill.
            backgroundTintList = null
            stateListAnimator = null
            elevation = 0f
            val fill = when { primary -> violet; quiet -> Color.WHITE; else -> lavender }
            setTextColor(if (primary) Color.WHITE else if (quiet) muted else violet)
            background = RippleDrawable(ColorStateList.valueOf(if (primary) 0x33FFFFFF else 0x1F7163E5),
                shape(fill, 28, if (quiet) border else null), shape(Color.WHITE, 28))
            setOnClickListener { onClick() }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(16))
        }
        content.addView(text(if (preview) "Test reminder" else "Time for your medication", 20f, true))
        if (preview) content.addView(text("Preview only. These buttons close the test without recording a dose.", 13f, color = muted),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        entries.forEach { entry ->
            val medication = entry.medication
            val medicationColor = Color.parseColor(MedicationColors.hex(medication.color))
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = shape(Color.rgb(250, 249, 254), 20, border)
            }
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(ImageView(context).apply {
                setImageResource(medication.form.iconResource())
                setColorFilter(medicationColor)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = shape(ColorUtils.blendARGB(Color.WHITE, medicationColor, 0.12f), 14)
                contentDescription = medication.form.label
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val description = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(medication.name, 17f, true))
                addView(text(medication.dose, 13f, color = muted), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
            }
            heading.addView(description, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10); marginEnd = dp(8)
            })
            heading.addView(text(entry.time, 12f, true, violet).apply {
                background = shape(lavender, 20)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                contentDescription = "Scheduled ${entry.time}"
            })
            card.addView(heading)
            if (medication.note.isNotBlank()) card.addView(text(medication.note, 13f, color = muted),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            card.addView(pill("Taken", primary = true) { entry.onAction(ReminderReceiver.ACTION_TAKEN) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
            val secondary = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            secondary.addView(pill("Snooze 10 min") { entry.onAction(ReminderReceiver.ACTION_SNOOZE) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f).apply { marginEnd = dp(8) })
            secondary.addView(pill("Skip", quiet = true) { entry.onAction(ReminderReceiver.ACTION_SKIP) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            card.addView(secondary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        }
        content.addView(pill(if (preview) "Close test" else if (keepNotification) "Later · keep notification" else "Later", quiet = true, onClick = onLater),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        return ScrollView(context).apply {
            background = shape(Color.WHITE, 24)
            clipToOutline = true
            isFillViewport = true
            addView(content)
        }
    }
}
