package io.github.sibmaks.app.dose.localization

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import java.util.Locale

/** Tags are stable storage keys; native names remain readable before choosing a language. */
data class AppLanguage(val tag: String, val nativeName: String)

object AppLanguages {
    val supported = listOf(
        AppLanguage("en", "English"), AppLanguage("ru", "Русский"),
        AppLanguage("be", "Беларуская"), AppLanguage("hy", "Հայերեն"),
        AppLanguage("az", "Azərbaycanca"), AppLanguage("kk", "Қазақша"),
        AppLanguage("ky", "Кыргызча"), AppLanguage("ro", "Română"),
        AppLanguage("tg", "Тоҷикӣ"), AppLanguage("tk", "Türkmençe"),
        AppLanguage("uz", "Oʻzbekcha"), AppLanguage("uk", "Українська"),
        AppLanguage("ka", "ქართული"),
    )
    private fun preferences(context: Context) = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
    fun offered(context: Context) = preferences(context).getBoolean("offered", false)
    fun markOffered(context: Context) { preferences(context).edit().putBoolean("offered", true).apply() }

    fun selectedTag(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
    } else preferences(context).getString("language", "").orEmpty()

    /** Empty tag follows Android's system language; Android 13+ is the single source of truth. */
    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || supported.any { it.tag == tag })
        markOffered(context)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            preferences(context).edit().putString("language", tag).commit()
        }
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context)
    }

    fun wrap(context: Context): Context {
        val tag = selectedTag(context)
        val locales = if (tag.isNotEmpty()) LocaleList.forLanguageTags(tag) else systemLocales(context)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(locales) })
    }

    fun systemLocales(context: Context): LocaleList = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).systemLocales
    } else android.content.res.Resources.getSystem().configuration.locales

    fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
            Uri.parse("package:${context.packageName}")))
    }
}
