package wojtoteka.ovh.fishingparty

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class Language(val tag: String) { AUTO(""), PL("pl"), EN("en") }

/**
 * Język gry wybrany w ustawieniach. AUTO = język telefonu (polski albo angielski).
 * Android 13+ trzyma wybór sam (per-app language, widoczny też w ustawieniach systemu);
 * starsze wersje zapisują go w preferencjach i nakładają na kontekst.
 */
object AppLanguage {
    private const val PREFS = "settings"
    private const val KEY = "lang"

    /** Po przełączeniu język odtwarza aktywność – wracamy wtedy od razu do ustawień. */
    var reopenSettings = false

    fun current(context: Context): Language {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) "" else locales[0].language
        } else {
            prefs(context).getString(KEY, "").orEmpty()
        }
        return Language.entries.firstOrNull { it.tag == tag } ?: Language.AUTO
    }

    fun set(activity: Activity, language: Language) {
        if (current(activity) == language) return
        reopenSettings = true
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == Language.AUTO) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            prefs(activity).edit(commit = true) { putString(KEY, language.tag) }
            activity.recreate()
        }
    }

    /** Android < 13: nakłada wybrany język na kontekst aktywności i powiadomień. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = prefs(base).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
