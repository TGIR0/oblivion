package org.bepass.oblivion.ui

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeHardcodedColorGuardTest {
  private val forbiddenPatterns =
    listOf(
      Regex("""Color\s*\(\s*0x"""),
      Regex("""Color\.(White|Black|Gray)\b"""),
    )

  private val guardedFileCandidates =
    listOf(
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/compose/screens/MainScreen.kt",
        "src/main/java/org/bepass/oblivion/ui/compose/screens/MainScreen.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/compose/screens/InfoRoute.kt",
        "src/main/java/org/bepass/oblivion/ui/compose/screens/InfoRoute.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/compose/screens/LogsRoute.kt",
        "src/main/java/org/bepass/oblivion/ui/compose/screens/LogsRoute.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/compose/components/GlassCard.kt",
        "src/main/java/org/bepass/oblivion/ui/compose/components/GlassCard.kt",
      ),
      listOf(
        "app/src/main/java/org/bepass/oblivion/ui/compose/components/PremiumConnectionButton.kt",
        "src/main/java/org/bepass/oblivion/ui/compose/components/PremiumConnectionButton.kt",
      ),
    )

  @Test
  fun guardedUiFiles_doNotUseForbiddenHardcodedColors() {
    val files = guardedFileCandidates.map { candidates -> resolveFirstExisting(candidates) }
    files.forEach { file ->
      val source = file.readText()
      val forbidden = firstForbidden(source)
      assertNull(
        "Forbidden hardcoded color found in ${file.path}: ${forbidden?.description}",
        forbidden,
      )
    }
  }

  private fun resolveFirstExisting(candidates: List<String>): File {
    val found = candidates.map(::File).firstOrNull { it.exists() }
    assertNotNull(
      "Guarded UI file not found. Tried: ${candidates.joinToString()}",
      found,
    )
    return found!!
  }

  private fun firstForbidden(source: String): ForbiddenMatch? {
    source.lineSequence().forEachIndexed { index, line ->
      forbiddenPatterns.forEach { pattern ->
        if (pattern.containsMatchIn(line)) {
          return ForbiddenMatch(index + 1, line.trim())
        }
      }
    }
    return null
  }

  private data class ForbiddenMatch(val line: Int, val content: String) {
    val description: String = "line $line => $content"
  }
}
