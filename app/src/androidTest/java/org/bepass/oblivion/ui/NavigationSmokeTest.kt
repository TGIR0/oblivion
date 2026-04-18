package org.bepass.oblivion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.bepass.oblivion.R
import org.junit.Rule
import org.junit.Test

class NavigationSmokeTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun navigateBetweenHomeSettingsInfo() {
    val activity = composeRule.activity
    val meansText = activity.getString(R.string.means)
    val basedOnWarp = activity.getString(R.string.basedOnWarp)
    val settingsText = activity.getString(R.string.settingsText)
    val aboutAppText = activity.getString(R.string.aboutApp)
    val backText = activity.getString(R.string.back)

    // Best-effort skip splash if still visible.
    if (composeRule.onAllNodesWithText(meansText).fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithText(meansText).performClick()
    }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText(basedOnWarp).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(basedOnWarp).assertIsDisplayed()

    composeRule.onNodeWithContentDescription(settingsText).performClick()
    composeRule.onNodeWithText(settingsText).assertIsDisplayed()

    composeRule.onNodeWithContentDescription(backText).performClick()
    composeRule.onNodeWithText(basedOnWarp).assertIsDisplayed()

    composeRule.onNodeWithContentDescription(aboutAppText).performClick()
    composeRule.onNodeWithText(aboutAppText).assertIsDisplayed()

    composeRule.onNodeWithContentDescription(backText).performClick()
    composeRule.onNodeWithText(basedOnWarp).assertIsDisplayed()
  }
}
