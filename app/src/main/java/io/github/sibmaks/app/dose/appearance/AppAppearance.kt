package io.github.sibmaks.app.dose.appearance

import android.content.Context
import android.content.res.Configuration
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.ThemeMode

object AppAppearance {
    const val MODE = "theme_mode"
    fun preferences(context: Context) = context.applicationContext.getSharedPreferences("dose_appearance", Context.MODE_PRIVATE)
    fun mode(context: Context) = ThemeMode.fromPreference(preferences(context).getString(MODE, null))
    fun select(context: Context, mode: ThemeMode) { preferences(context).edit().putString(MODE, mode.preference).apply() }
    fun isDark(context: Context): Boolean = mode(context).isDark(
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    fun activityTheme(context: Context) = if (isDark(context)) R.style.Theme_Dose_Dark else R.style.Theme_Dose_Light
    fun dialogTheme(context: Context) = if (isDark(context)) R.style.Theme_Dose_Dialog_Dark else R.style.Theme_Dose_Dialog_Light
}
