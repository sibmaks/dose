package io.github.sibmaks.app.dose.reminders

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.*
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.Medication
import io.github.sibmaks.app.dose.data.MedicationIconColors
import io.github.sibmaks.app.dose.localization.localizedDose
import io.github.sibmaks.app.dose.localization.localizedForm
import io.github.sibmaks.app.dose.ui.iconResource

/** Explicit shapes and colors keep the pop-up consistent across device button themes. */
internal object ReminderPopupView {
    data class Entry(
        val medication: Medication,
        val time: String,
        val allowOnTime: Boolean = false,
        val amount: String = medication.amount,
        val onAction: (String) -> Unit
    )

    fun create(
        context: Context,
        entries: List<Entry>,
        keepNotification: Boolean,
        onLater: () -> Unit,
        onAll: (String) -> Unit
    ): ScrollView {
        val dark = AppAppearance.isDark(context)
        val ink = if (dark) 0xFFE6E5F0.toInt() else Color.rgb(37, 43, 67)
        val muted = if (dark) 0xFFB7B6C9.toInt() else 0xFF62677D.toInt()
        val accent = if (dark) 0xFFC4B9FF.toInt() else 0xFF6958D6.toInt()
        val violet = Color.rgb(113, 99, 229)
        val lavender = if (dark) 0xFF342D52.toInt() else Color.rgb(242, 239, 255)
        val border = if (dark) 0xFF393A49.toInt() else Color.rgb(231, 228, 245)
        val surface = if (dark) 0xFF1C1D28.toInt() else Color.WHITE
        val cardSurface = if (dark) 0xFF232430.toInt() else Color.rgb(250, 249, 254)
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        fun shape(
            color: Int,
            radius: Int,
            stroke: Int? = null
        ) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

        fun text(
            value: String,
            size: Float,
            bold: Boolean = false,
            color: Int = ink
        ) = TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        fun pill(
            label: String,
            primary: Boolean = false,
            quiet: Boolean = false,
            onClick: () -> Unit
        ) =
            Button(context).apply {
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
                val fill = when {
                    primary -> violet; quiet -> surface; else -> lavender
                }
                setTextColor(if (primary) Color.WHITE else if (quiet) muted else accent)
                background = RippleDrawable(
                    ColorStateList.valueOf(if (primary) 0x33FFFFFF else 0x1F7163E5),
                    shape(fill, 28, if (quiet) border else null), shape(Color.WHITE, 28)
                )
                setOnClickListener { onClick() }
            }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(16))
        }
        content.addView(text(context.getString(R.string.time_for_your_medication), 20f, true))
        if (entries.size > 1) {
            content.addView(
                text(context.getString(R.string.several_medicines, entries.size), 14f, color = muted),
                LinearLayout.LayoutParams(-1, -2)
                    .apply { topMargin = dp(8) })
            content.addView(
                pill(context.getString(R.string.taken_all), primary = true) { onAll("taken") },
                LinearLayout.LayoutParams(-1, -2)
                    .apply { topMargin = dp(12) })
            content.addView(
                pill(context.getString(R.string.skip_all), quiet = true) { onAll("skipped") },
                LinearLayout.LayoutParams(-1, -2)
                    .apply { topMargin = dp(8) })
        }
        entries.forEach { entry ->
            val medication = entry.medication
            val (medicationColor, iconBackground) = MedicationIconColors.palette(medication.color)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = shape(cardSurface, 20, border)
            }
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(ImageView(context).apply {
                setImageResource(medication.form.iconResource())
                setColorFilter(medicationColor)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = shape(iconBackground, 14, border)
                contentDescription = medication.localizedForm(context)
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val description = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(medication.name, 17f, true))
                addView(
                    text(medication.localizedDose(context, entry.amount), 13f, color = muted),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                        .apply { topMargin = dp(3) })
            }
            heading.addView(
                description,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply {
                        marginStart = dp(10); marginEnd = dp(8)
                    })
            heading.addView(text(entry.time, 12f, true, accent).apply {
                background = shape(lavender, 20)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                contentDescription = context.getString(R.string.scheduled_time, entry.time)
            })
            card.addView(heading)
            if (medication.note.isNotBlank()) card.addView(
                text(medication.note, 13f, color = muted),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                    .apply { topMargin = dp(10) })
            val acceptance = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            acceptance.addView(
                pill(
                    context.getString(R.string.taken),
                    primary = true
                ) { entry.onAction(ReminderReceiver.ACTION_TAKEN) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (entry.allowOnTime) acceptance.addView(
                pill(context.getString(R.string.on_time)) { entry.onAction(ReminderReceiver.ACTION_ON_TIME) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(8) })
            card.addView(
                acceptance,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                    .apply { topMargin = dp(14) })
            val secondary = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            secondary.addView(
                pill(context.getString(R.string.snooze_10_min)) { entry.onAction(ReminderReceiver.ACTION_SNOOZE) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .apply { marginEnd = dp(8) })
            secondary.addView(
                pill(context.getString(R.string.skip), quiet = true) { entry.onAction(ReminderReceiver.ACTION_SKIP) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            card.addView(
                secondary, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                    .apply { topMargin = dp(8) })
            content.addView(
                card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                    .apply { topMargin = dp(14) })
        }
        content.addView(
            pill(
                if (keepNotification) context.getString(R.string.later_keep_notification) else context.getString(R.string.later),
                quiet = true,
                onClick = onLater
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) })
        return ScrollView(context).apply {
            background = shape(surface, 24)
            clipToOutline = true
            isFillViewport = true
            addView(content)
        }
    }
}
