@file:JvmName("CountryCodeUtils")

package org.bepass.oblivion.utils

import java.util.Locale

private const val REGIONAL_INDICATOR_OFFSET = 0x1F1A5

fun CountryCode.toCountryFlagEmoji(): String {
  val normalized = value.uppercase(Locale.ROOT)
  if (normalized.length != 2 || normalized !in Locale.getISOCountries()) return ""
  return normalized
    .map { character -> String(Character.toChars(character.code + REGIONAL_INDICATOR_OFFSET)) }
    .joinToString(separator = "")
}

@JvmInline value class CountryCode(val value: String)
