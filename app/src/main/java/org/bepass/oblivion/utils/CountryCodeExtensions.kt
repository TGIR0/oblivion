@file:JvmName("CountryCodeUtils")

package org.bepass.oblivion.utils

import java.util.Locale

fun CountryCode.toCountryFlagEmoji(): String {
  val normalized = value.uppercase(Locale.ROOT)
  if (normalized.length != 2 || normalized !in Locale.getISOCountries()) return ""
  return normalized
    .map { character -> String(Character.toChars(character.code + 0x1F1A5)) }
    .joinToString(separator = "")
}

@JvmInline value class CountryCode(val value: String)
