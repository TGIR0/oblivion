package org.bepass.oblivion.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeHelper {
  private const val LIGHT_STORAGE_VALUE = 1
  private const val DARK_STORAGE_VALUE = 2
  private const val OLED_STORAGE_VALUE = 3
  const val MODE_NIGHT_FOLLOW_SYSTEM = -1
  const val MODE_NIGHT_UNSPECIFIED = -100
  const val MODE_NIGHT_NO = 1
  const val MODE_NIGHT_YES = 2

  enum class Theme(val storageValue: Int, val nightMode: Int) {
    OLED(OLED_STORAGE_VALUE, MODE_NIGHT_YES),
    DARK(DARK_STORAGE_VALUE, MODE_NIGHT_YES),
    LIGHT(LIGHT_STORAGE_VALUE, MODE_NIGHT_NO);

    companion object {
      fun fromStorageKey(storageValue: Int): Theme =
        entries.firstOrNull { it.storageValue == storageValue } ?: OLED
    }
  }

  @JvmStatic fun getInstance(): ThemeHelper = this

  private val _themeFlow = MutableStateFlow(Theme.OLED)
  val themeFlow: StateFlow<Theme> = _themeFlow.asStateFlow()

  var currentTheme: Theme = Theme.OLED
    private set

  fun init() {
    val storedValue = FileManager.getInt(FileManager.Keys.DARK_MODE, Theme.OLED.storageValue)
    currentTheme = Theme.fromStorageKey(storedValue)
    _themeFlow.value = currentTheme
    if (!FileManager.contains(FileManager.Keys.DARK_MODE) || storedValue != currentTheme.storageValue) {
      FileManager.set(FileManager.Keys.DARK_MODE, currentTheme.storageValue)
    }
    applyTheme()
  }

  fun applyTheme() = Unit

  fun select(theme: Theme) {
    currentTheme = theme
    _themeFlow.value = theme
    FileManager.set(FileManager.Keys.DARK_MODE, theme.storageValue)
    applyTheme()
  }

  val isOled: Boolean
    get() = currentTheme == Theme.OLED

  fun isDarkTheme(@Suppress("UNUSED_PARAMETER") systemDark: Boolean): Boolean = currentTheme != Theme.LIGHT
}
