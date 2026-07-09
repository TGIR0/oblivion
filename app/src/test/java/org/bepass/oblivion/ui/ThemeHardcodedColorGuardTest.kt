package org.bepass.oblivion.ui

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ensures that Compose UI files do not use hardcoded colours such as `Color(0x...)` or
 * `Color.White`/`Color.Black`/`Color.Gray`, which break theming and dark‑mode support.
 */
class ThemeHardcodedColorGuardTest {

  private val forbiddenPatterns =
    listOf(Regex("""Color\s*\(\s*0x"""), Regex("""Color\.(White|Black|Gray)\b"""))

  // هر فایل یک مجموعه مسیر احتمالی دارد تا هم در ساختار پروژه‌ی اصلی
  // و هم در مسیرهای معمول CI پیدا شود.
  private val guardedFileCandidates =
    listOf(
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/HomeScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/HomeScreen.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/InfoScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/InfoScreen.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/LogScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/LogScreen.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/ComposePrimitives.kt",
        "src/main/java/org/bepass/oblivion/ui/ComposePrimitives.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/SettingsScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/SettingsScreen.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/SettingsDialogs.kt",
        "src/main/java/org/bepass/oblivion/ui/SettingsDialogs.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/LanguageDialog.kt",
        "src/main/java/org/bepass/oblivion/ui/LanguageDialog.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/SplitTunnelScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/SplitTunnelScreen.kt",
      ),
    )

  @Test
  fun `guarded Compose files must not contain forbidden hardcoded colour expressions`() {
    val files = guardedFileCandidates.map { resolveFirstExisting(it) }

    files.forEach { file ->
      val source = file.readText()
      val forbidden = firstForbidden(source)

      assertNull(
        "Forbidden hardcoded colour found in ${file.path} at $forbidden – " +
          "use theme colours (MaterialTheme.colorScheme) instead.",
        forbidden,
      )
    }
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  /**
   * از بین چند مسیر پیشنهادی، اولین فایل موجود را برمی‌گرداند. اگر هیچکدام پیدا نشد، تست شکست
   * می‌خورد.
   */
  private fun resolveFirstExisting(candidates: List<String>): File {
    val found = candidates.map { File(it) }.firstOrNull { it.exists() }
    assertNotNull("None of the candidate files were found. Tried: $candidates", found)
    return found!!
  }

  private data class ForbiddenMatch(val line: Int, val content: String) {
    override fun toString() = "line $line => ${content.trim()}"
  }

  /** اولین خطی را که شامل یکی از الگوهای ممنوعه باشد برمی‌گرداند، در غیر این‌صورت `null`. */
  private fun firstForbidden(source: String): ForbiddenMatch? {
    source.lineSequence().forEachIndexed { index, line ->
      forbiddenPatterns.forEach { pattern ->
        if (pattern.containsMatchIn(line)) {
          return ForbiddenMatch(index + 1, line)
        }
      }
    }
    return null
  }
}
