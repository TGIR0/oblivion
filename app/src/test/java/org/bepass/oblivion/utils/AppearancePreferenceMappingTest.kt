package org.bepass.oblivion.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePreferenceMappingTest {
  @Test
  fun `theme storage values map to expected themes`() {
    assertEquals(ThemeHelper.Theme.LIGHT, ThemeHelper.Theme.fromStorageKey(1))
    assertEquals(ThemeHelper.Theme.DARK, ThemeHelper.Theme.fromStorageKey(2))
    assertEquals(ThemeHelper.Theme.OLED, ThemeHelper.Theme.fromStorageKey(3))
  }

  @Test
  fun `invalid theme storage values fall back to oled`() {
    assertEquals(ThemeHelper.Theme.OLED, ThemeHelper.Theme.fromStorageKey(-1))
    assertEquals(ThemeHelper.Theme.OLED, ThemeHelper.Theme.fromStorageKey(0))
    assertEquals(ThemeHelper.Theme.OLED, ThemeHelper.Theme.fromStorageKey(99))
  }

  @Test
  fun `font size storage values map to expected font sizes`() {
    assertEquals(FontSizeHelper.FontSize.SMALL, FontSizeHelper.FontSize.fromStorageKey(0))
    assertEquals(FontSizeHelper.FontSize.DEFAULT, FontSizeHelper.FontSize.fromStorageKey(1))
    assertEquals(FontSizeHelper.FontSize.LARGE, FontSizeHelper.FontSize.fromStorageKey(2))
  }

  @Test
  fun `invalid font size storage values fall back to default`() {
    assertEquals(FontSizeHelper.FontSize.DEFAULT, FontSizeHelper.FontSize.fromStorageKey(-1))
    assertEquals(FontSizeHelper.FontSize.DEFAULT, FontSizeHelper.FontSize.fromStorageKey(99))
  }

  @Test
  fun `font size scale values stay stable`() {
    assertEquals(0.90f, FontSizeHelper.FontSize.SMALL.scale, 0.001f)
    assertEquals(1.00f, FontSizeHelper.FontSize.DEFAULT.scale, 0.001f)
    assertEquals(1.15f, FontSizeHelper.FontSize.LARGE.scale, 0.001f)
  }
}
