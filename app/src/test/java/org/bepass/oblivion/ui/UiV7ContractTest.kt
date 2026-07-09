package org.bepass.oblivion.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UiV7ContractTest {
  @Test
  fun `home switch and fixed palette retain v7 dimensions and colors`() {
    val home = source("ui/HomeScreen.kt")
    val theme = source("ui/theme/OblivionTheme.kt")

    assertTrue(home.contains("Modifier.width(160.dp)"))
    assertTrue(home.contains(".height(75.dp)"))
    assertTrue(home.contains("Modifier.size(65.dp)"))
    assertTrue(home.contains("OblivionV7Tokens.SwitchTrackOff"))
    assertTrue(home.contains("OblivionV7Tokens.FixedWhite"))
    assertTrue(theme.contains("Color(0xffffa200)"))
    assertTrue(theme.contains("Color(0xff1C202C)"))
    assertTrue(theme.contains("Color(0xff7B8D9D)"))
    assertTrue(theme.contains("Color(0xffd7d7d7)"))
  }

  private fun source(relativePath: String): String {
    val candidates =
      listOf(
        File("app/src/main/java/org/bepass/oblivion/$relativePath"),
        File("src/main/java/org/bepass/oblivion/$relativePath"),
      )
    return checkNotNull(candidates.firstOrNull(File::exists)) {
        "Unable to locate $relativePath from ${File(".").absolutePath}"
      }
      .readText()
  }
}
