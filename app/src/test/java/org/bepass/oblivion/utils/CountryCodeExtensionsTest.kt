package org.bepass.oblivion.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryCodeExtensionsTest {
  @Test
  fun toCountryFlagEmoji_validUppercase() {
    assertEquals("🇺🇸", CountryCode("US").toCountryFlagEmoji())
  }

  @Test
  fun toCountryFlagEmoji_validLowercase() {
    assertEquals("🇺🇸", CountryCode("us").toCountryFlagEmoji())
  }

  @Test
  fun toCountryFlagEmoji_invalidLength() {
    assertEquals("", CountryCode("USA").toCountryFlagEmoji())
  }

  @Test
  fun toCountryFlagEmoji_invalidCode() {
    assertEquals("", CountryCode("ZZ").toCountryFlagEmoji())
  }
}
