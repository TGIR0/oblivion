package org.bepass.oblivion.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [CountryCode.toCountryFlagEmoji]. */
class CountryCodeExtensionsTest {

  @Test
  fun `valid uppercase country code produces correct flag emoji`() {
    assertEquals("US should produce 🇺🇸", "🇺🇸", CountryCode("US").toCountryFlagEmoji())
  }

  @Test
  fun `valid lowercase country code produces same flag emoji as uppercase`() {
    assertEquals(
      "us should produce 🇺🇸 (case‑insensitive)",
      "🇺🇸",
      CountryCode("us").toCountryFlagEmoji(),
    )
  }

  @Test
  fun `three‑letter country code returns empty string`() {
    assertEquals(
      "Codes longer than 2 characters must return empty",
      "",
      CountryCode("USA").toCountryFlagEmoji(),
    )
  }

  @Test
  fun `invalid two‑letter code returns empty string`() {
    assertEquals(
      "ZZ is not a valid region indicator, must return empty",
      "",
      CountryCode("ZZ").toCountryFlagEmoji(),
    )
  }
}
