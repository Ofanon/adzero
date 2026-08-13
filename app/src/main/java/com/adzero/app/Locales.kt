package com.adzero.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language AdZero speaks.
 *
 * Default is "follow the device", which is what almost everyone wants and what
 * Android does on its own. The picker exists for the rest: a phone in one
 * language, a person who reads another.
 *
 * Android 13 has a proper per-app language API that the system remembers and
 * shows in Settings. Below that there is nothing, so we keep the choice in our
 * own preferences and re-wrap the context ourselves.
 */
object Locales {

    /** Language tags we ship, in the order the picker shows them. */
    val TAGS = listOf("en", "fr", "es", "pt-BR", "de", "it", "tr")

    /** Written in their own language: nobody looks for "German" in a Turkish list. */
    fun displayName(tag: String): String = when (tag) {
        "en" -> "English"
        "fr" -> "Français"
        "es" -> "Español"
        "pt-BR" -> "Português (Brasil)"
        "de" -> "Deutsch"
        "it" -> "Italiano"
        "tr" -> "Türkçe"
        else -> tag
    }

    private const val PREFS = "adsilence"
    private const val KEY = "locale"

    /** null means "follow the device". */
    fun current(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = ctx.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (list == null || list.isEmpty) return null
            return list[0]?.toLanguageTag()?.let { normalise(it) }
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    fun set(ctx: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system stores this, applies it, and recreates the activity.
            ctx.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
            return
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (tag == null) remove(KEY) else putString(KEY, tag) }
            .apply()
    }

    /**
     * Applies the stored choice on Android 12 and below. A no-op on 13+, where
     * the system has already resolved the locale before we get here.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** "pt-br" and "pt_BR" both have to match the "pt-BR" we ship. */
    private fun normalise(tag: String): String =
        TAGS.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: TAGS.firstOrNull { tag.startsWith(it.substringBefore('-'), ignoreCase = true) }
            ?: tag
}
