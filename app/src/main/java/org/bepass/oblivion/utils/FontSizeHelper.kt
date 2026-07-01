package org.bepass.oblivion.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FontSizeHelper {
  enum class FontSize(val storageValue: Int, val scale: Float) {
    SMALL(0, 0.90f),
    DEFAULT(1, 1.00f),
    LARGE(2, 1.15f);

    companion object {
      fun fromStorageKey(storageValue: Int): FontSize =
        entries.firstOrNull { it.storageValue == storageValue } ?: DEFAULT
    }
  }

  private val _fontSizeFlow = MutableStateFlow(FontSize.DEFAULT)
  val fontSizeFlow: StateFlow<FontSize> = _fontSizeFlow.asStateFlow()

  fun init() {
    val storedValue = FileManager.getInt(FileManager.Keys.FONT_SIZE, FontSize.DEFAULT.storageValue)
    _fontSizeFlow.value = FontSize.fromStorageKey(storedValue)
  }

  fun select(fontSize: FontSize) {
    _fontSizeFlow.value = fontSize
    FileManager.set(FileManager.Keys.FONT_SIZE, fontSize.storageValue)
  }
}
