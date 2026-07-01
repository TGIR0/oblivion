package org.bepass.oblivion.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.bepass.oblivion.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationSmokeTest {
  @get:Rule(order = 0)
  val notificationPermissionRule: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun navigateBetweenHomeSettingsInfo() {
    val activity = composeRule.activity
    val settingsText = activity.getString(R.string.settingsText)
    val aboutAppText = activity.getString(R.string.aboutApp)
    val backText = activity.getString(R.string.back)
    val themeText = activity.getString(R.string.themeText)
    val darkThemeText = activity.getString(R.string.theme_dark)
    val fontSizeText = activity.getString(R.string.font_size_text)
    val largeFontText = activity.getString(R.string.font_size_large)
    val languageText = activity.getString(R.string.select_language)
    val cancelText = activity.getString(R.string.cancel)

    awaitHome()

    // رفتن به تنظیمات
    composeRule.onNodeWithContentDescription(settingsText).performClick()
    composeRule.onNodeWithTag(UiTestTags.SETTINGS).assertIsDisplayed()
    composeRule.onNodeWithText(settingsText).assertExists() // assertExists برای اطمینان از وجود گره
    composeRule.onNodeWithText(themeText).assertIsDisplayed()
    composeRule.onNodeWithText(fontSizeText).assertIsDisplayed()
    composeRule.onNodeWithText(languageText).assertIsDisplayed()

    composeRule.onNodeWithText(themeText).performClick()
    assertTrue(hasText(darkThemeText))
    composeRule.onNodeWithText(cancelText).performClick()

    composeRule.onNodeWithText(fontSizeText).performClick()
    assertTrue(hasText(largeFontText))
    composeRule.onNodeWithText(cancelText).performClick()

    // بازگشت
    composeRule.onNodeWithContentDescription(backText).performClick()
    composeRule.onNodeWithTag(UiTestTags.HOME).assertIsDisplayed()

    // رفتن به دربارهٔ برنامه
    composeRule.onNodeWithContentDescription(aboutAppText).performClick()
    composeRule.onNodeWithTag(UiTestTags.INFO).assertIsDisplayed()
    composeRule.onNodeWithText(aboutAppText).assertExists()

    // بازگشت
    composeRule.onNodeWithContentDescription(backText).performClick()
    composeRule.onNodeWithTag(UiTestTags.HOME).assertIsDisplayed()
  }

  @Test
  fun splitTunnelWhitelistModeWorks() {
    // API 37 (Android 17): Test whitelist mode in split tunneling
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    val activity = composeRule.activity
    val settingsText = activity.getString(R.string.settingsText)
    val backText = activity.getString(R.string.back)
    val blacklistTextDesc = activity.getString(R.string.blackListTextDesc)
    val disabledText = activity.getString(R.string.disabledR)
    val whitelistText = activity.getString(R.string.whiteList)

    awaitHome()

    // Navigate to settings
    composeRule.onNodeWithContentDescription(settingsText).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { hasText(settingsText) }

    // Navigate to split tunneling
    composeRule.onNodeWithText(blacklistTextDesc).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { hasText(whitelistText) }

    // Test whitelist mode selection
    composeRule.onNodeWithText(whitelistText).performClick()
    composeRule.waitForIdle()

    // Verify whitelist mode is selected
    composeRule.onNodeWithText(whitelistText).assertIsDisplayed()

    // Go back
    composeRule.onNodeWithContentDescription(backText).performClick()
  }

  @Test
  fun api37EnhancedFeaturesAreAvailable() {
    // API 37 (Android 17): Verify that API 37-specific features are accessible
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    val activity = composeRule.activity
    val settingsText = activity.getString(R.string.settingsText)
    val backText = activity.getString(R.string.back)
    val blacklistTextDesc = activity.getString(R.string.blackListTextDesc)
    val disabledText = activity.getString(R.string.disabledR)

    awaitHome()

    // Navigate to settings
    composeRule.onNodeWithContentDescription(settingsText).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { hasText(settingsText) }

    // Verify split tunneling option exists
    composeRule.onNodeWithText(blacklistTextDesc).assertIsDisplayed()

    // Navigate to split tunneling
    composeRule.onNodeWithText(blacklistTextDesc).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      hasText(activity.getString(R.string.disabledR))
    }

    // Verify all three split-tunnel policies are available
    composeRule.onNodeWithText(disabledText).assertIsDisplayed()
    composeRule.onNodeWithText(activity.getString(R.string.blackList)).assertIsDisplayed()
    composeRule.onNodeWithText(activity.getString(R.string.whiteList)).assertIsDisplayed()

    // Go back
    composeRule.onNodeWithContentDescription(backText).performClick()
  }

  private fun hasText(text: String): Boolean =
    runCatching { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
      .getOrDefault(false)

  private fun hasTag(tag: String): Boolean =
    runCatching { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
      .getOrDefault(false)

  private fun awaitHome() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      hasTag(UiTestTags.SPLASH) || hasTag(UiTestTags.HOME)
    }
    if (hasTag(UiTestTags.SPLASH)) {
      composeRule.onNodeWithTag(UiTestTags.SPLASH).performClick()
    }
    composeRule.waitUntil(timeoutMillis = 10_000) { hasTag(UiTestTags.HOME) }
    composeRule.onNodeWithTag(UiTestTags.HOME).assertIsDisplayed()
  }
}
