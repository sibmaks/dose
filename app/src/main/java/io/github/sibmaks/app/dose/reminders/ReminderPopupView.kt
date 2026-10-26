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
import io.github.sibmaks.app.dose.ui.doseColorScheme
import androidx.compose.ui.graphics.toArgb

/** Explicit shapes and colors keep the pop-up consistent across device button themes. */
internal object ReminderPopupView {
    data class Entry(
        val medication: Medication,
        val time: String,
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
        val colors = doseColorScheme(dark)
        val ink = colors.onSurface.toArgb()
        val muted = colors.onSurfaceVariant.toArgb()
        val accent = colors.primary.toArgb()
        val lavender = colors.primaryContainer.toArgb()
        val border = colors.outlineVariant.toArgb()
        val surface = colors.surface.toArgb()
        val cardSurface = colors.surfaceContainerLow.toArgb()
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
                    primary -> accent; quiet -> surface; else -> lavender
                }
                setTextColor(if (primary) colors.onPrimary.toArgb() else if (quiet) muted else colors.onPrimaryContainer.toArgb())
                background = RippleDrawable(
                    ColorStateList.valueOf((if (primary) colors.onPrimary else colors.primary).copy(alpha = 0.12f).toArgb()),
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
            val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(
                pill(
                    context.getString(R.string.taken),
                    primary = true
                ) { entry.onAction(ReminderReceiver.ACTION_TAKEN) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            actions.addView(
                pill(context.getString(R.string.skip), quiet = true) { entry.onAction(ReminderReceiver.ACTION_SKIP) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .apply { marginStart = dp(8) })
            card.addView(
                actions,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                    .apply { topMargin = dp(14) })
            card.addView(
                pill(context.getString(R.string.snooze_10_min)) { entry.onAction(ReminderReceiver.ACTION_SNOOZE) },
                LinearLayout.LayoutParams(
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
