package org.bepass.oblivion.utils

import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeHelper {
  private const val OLED_STORAGE_VALUE = 3

  enum class Theme(val storageValue: Int, @AppCompatDelegate.NightMode val nightMode: Int) {
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_YES),
    FOLLOW_SYSTEM(
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    ),
    UNSPECIFIED(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED),
    OLED(OLED_STORAGE_VALUE, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
      fun fromStorageKey(storageValue: Int): Theme =
        entries.firstOrNull { it.storageValue == storageValue } ?: FOLLOW_SYSTEM
    }
  }

  @JvmStatic fun getInstance(): ThemeHelper = this

  private val _themeFlow = MutableStateFlow(Theme.FOLLOW_SYSTEM)
  val themeFlow: StateFlow<Theme> = _themeFlow.asStateFlow()

  var currentTheme: Theme = Theme.FOLLOW_SYSTEM
    private set

  fun init() {
    val stored =
      if (FileManager.contains(FileManager.KeyHolder.DARK_MODE)) {
        FileManager.getInt(FileManager.KeyHolder.DARK_MODE, Theme.FOLLOW_SYSTEM.storageValue)
      } else {
        Theme.FOLLOW_SYSTEM.storageValue
      }

    currentTheme = Theme.fromStorageKey(stored)
    _themeFlow.value = currentTheme
    if (!FileManager.contains(FileManager.KeyHolder.DARK_MODE)) {
      FileManager.set(FileManager.KeyHolder.DARK_MODE, currentTheme.storageValue)
    }
    applyTheme()
  }

  fun applyTheme() {
    AppCompatDelegate.setDefaultNightMode(currentTheme.nightMode)
  }

  fun select(theme: Theme) {
    currentTheme = theme
    _themeFlow.value = theme
    FileManager.set(FileManager.KeyHolder.DARK_MODE, theme.storageValue)
    applyTheme()
  }

  val isOled: Boolean
    get() = currentTheme == Theme.OLED

  fun isDarkTheme(systemDark: Boolean): Boolean =
    when (currentTheme) {
      Theme.LIGHT -> false
      Theme.DARK, Theme.OLED -> true
      Theme.FOLLOW_SYSTEM, Theme.UNSPECIFIED -> systemDark
    }
}
