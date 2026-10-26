package io.github.sibmaks.app.dose.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.appearance.AppAppearance
import io.github.sibmaks.app.dose.data.ThemeMode
import io.github.sibmaks.app.dose.ui.doseColorScheme
import io.github.sibmaks.app.dose.ui.doseStatusColors

/** Expanded widgets follow app appearance; the compact widget keeps the app's branded hero. */
internal class WidgetAppearance(context: Context) {
    private val mode = AppAppearance.mode(context)
    private val dark = AppAppearance.isDark(context)
    private val lightColors = doseColorScheme(false)
    private val darkColors = doseColorScheme(true)

    private fun RemoteViews.color(id: Int, light: Color, night: Color) {
        if (mode == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= 31) {
            setColorInt(id, "setTextColor", light.toArgb(), night.toArgb())
        } else {
            setTextColor(id, (if (dark) night else light).toArgb())
        }
    }

    private fun RemoteViews.background(id: Int, system: Int, light: Int, night: Int) {
        setInt(id, "setBackgroundResource", when (mode) {
            ThemeMode.SYSTEM -> system
            ThemeMode.LIGHT -> light
            ThemeMode.DARK -> night
        })
    }

    fun list(views: RemoteViews) = with(views) {
        background(R.id.widget_list_root, R.drawable.widget_list_background,
            R.drawable.widget_list_background_light, R.drawable.widget_list_background_dark)
        color(R.id.widget_title, lightColors.onSurface, darkColors.onSurface)
        color(R.id.widget_time, lightColors.primary, darkColors.primary)
        color(R.id.widget_empty_message, lightColors.onSurface, darkColors.onSurface)
        color(R.id.widget_dose, lightColors.primary, darkColors.primary)
    }

    fun row(views: RemoteViews, status: String) = with(views) {
        background(R.id.widget_row, R.drawable.widget_row_background,
            R.drawable.widget_row_background_light, R.drawable.widget_row_background_dark)
        color(R.id.medication_name, lightColors.onSurface, darkColors.onSurface)
        color(R.id.medication_detail, lightColors.onSurfaceVariant, darkColors.onSurfaceVariant)
        color(R.id.widget_row_time, lightColors.primary, darkColors.primary)
        val lightStatus = doseStatusColors(false)
        val darkStatus = doseStatusColors(true)
        val (light, night) = when (status) {
            "taken" -> lightStatus.success to darkStatus.success
            "due", "overdue", "missed" -> lightStatus.attention to darkStatus.attention
            "pending", "upcoming" -> lightColors.primary to darkColors.primary
            else -> lightColors.onSurfaceVariant to darkColors.onSurfaceVariant
        }
        color(R.id.widget_row_status, light, night)
        background(R.id.take_now, R.drawable.notification_action_primary,
            R.drawable.notification_action_primary_light, R.drawable.notification_action_primary_dark)
        background(R.id.skip, R.drawable.notification_action_secondary,
            R.drawable.notification_action_secondary_light, R.drawable.notification_action_secondary_dark)
        color(R.id.take_now, lightColors.onPrimary, darkColors.onPrimary)
        color(R.id.skip, lightColors.onPrimaryContainer, darkColors.onPrimaryContainer)
    }
}
