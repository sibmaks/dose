package io.github.sibmaks.app.dose.data

/** Stable preference values; missing or unrecognized settings follow Android. */
enum class ThemeMode(val preference: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String?): ThemeMode = entries.firstOrNull { it.preference == value } ?: SYSTEM
    }
}
