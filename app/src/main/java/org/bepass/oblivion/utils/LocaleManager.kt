package org.bepass.oblivion.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.bepass.oblivion.R

object LocaleManager {
  private const val IS_SET_DEFAULT_LOCALE = "is_set_default_locale"
  private const val APP_LOCALE_TAG = "app_locale_tag"

  data class LocaleOption(val locale: Locale, val tag: String, val displayName: String)

  @JvmStatic
  fun initialize(context: Context) {
    FileManager.initialize(context.applicationContext)
    val savedTag = FileManager.getString(APP_LOCALE_TAG).trim()
    if (savedTag.isNotBlank()) {
      val locale = Locale.forLanguageTag(savedTag)
      if (locale.toLanguageTag().isNotBlank() && locale.toLanguageTag() != "und") {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
        FileManager.set(IS_SET_DEFAULT_LOCALE, true)
        return
      }
    }

    if (!FileManager.getBoolean(IS_SET_DEFAULT_LOCALE, false)) {
      // Empty locale list tells AndroidX to follow system.
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
      FileManager.set(IS_SET_DEFAULT_LOCALE, true)
    }
  }

  @JvmStatic
  fun setSystemDefaultLocaleIfFirstRun(context: Context) {
    initialize(context)
  }

  fun availableLocales(context: Context): List<LocaleOption> {
    val allowedTags = context.resources.getStringArray(R.array.allowed_languages).asSequence()

    return allowedTags
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .map(Locale::forLanguageTag)
      .map { locale ->
        LocaleOption(
          locale = locale,
          tag = locale.toLanguageTag(),
          displayName = locale.displayName,
        )
      }
      .filter { it.tag.isNotBlank() && it.tag != "und" }
      .distinctBy { it.tag }
      .toList()
  }

  fun setSystemDefaultLocale() {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    FileManager.set(APP_LOCALE_TAG, "")
    FileManager.set(IS_SET_DEFAULT_LOCALE, true)
  }

  fun setAppLocale(locale: Locale) {
    val normalized = locale.toLanguageTag()
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
    FileManager.set(APP_LOCALE_TAG, normalized)
    FileManager.set(IS_SET_DEFAULT_LOCALE, true)
  }
}
