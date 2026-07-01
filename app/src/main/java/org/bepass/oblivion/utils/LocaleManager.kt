package org.bepass.oblivion.utils

import android.app.LocaleManager as AndroidLocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val tags = savedTag.takeIf(::isValidLanguageTag).orEmpty()
      context.getSystemService(AndroidLocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(tags)
    }
    FileManager.set(IS_SET_DEFAULT_LOCALE, true)
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

  fun currentLocaleTag(): String = FileManager.getString(APP_LOCALE_TAG).trim()

  fun localizedContext(context: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
    val savedTag = FileManager.getString(APP_LOCALE_TAG).trim()
    if (!isValidLanguageTag(savedTag)) return context
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocales(LocaleList.forLanguageTags(savedTag))
    return context.createConfigurationContext(configuration)
  }

  fun setSystemDefaultLocale(context: Context) {
    setSystemDefaultLocale()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.getSystemService(AndroidLocaleManager::class.java).applicationLocales =
        LocaleList.getEmptyLocaleList()
    }
  }

  fun setAppLocale(context: Context, locale: Locale) {
    setAppLocale(locale)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.getSystemService(AndroidLocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(locale.toLanguageTag())
    }
  }

  fun setSystemDefaultLocale() {
    FileManager.set(APP_LOCALE_TAG, "")
    FileManager.set(IS_SET_DEFAULT_LOCALE, true)
  }

  fun setAppLocale(locale: Locale) {
    val normalized = locale.toLanguageTag()
    require(isValidLanguageTag(normalized)) { "Invalid locale" }
    FileManager.set(APP_LOCALE_TAG, normalized)
    FileManager.set(IS_SET_DEFAULT_LOCALE, true)
  }

  private fun isValidLanguageTag(tag: String): Boolean =
    tag.isNotBlank() && Locale.forLanguageTag(tag).toLanguageTag() != "und"
}
