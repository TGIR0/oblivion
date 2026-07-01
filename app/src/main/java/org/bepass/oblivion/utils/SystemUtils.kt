package org.bepass.oblivion.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Utility class for system‑level UI tweaks.
 *
 * All methods are thread‑safe (operate on the main‑thread only when called from a lifecycle‑aware
 * context; otherwise the caller must ensure proper threading).
 */
object SystemUtils {

  /**
   * Sets the status bar background color and ensures the status bar icon colors have proper
   * contrast.
   *
   * @param activity Target activity whose status bar is to be styled.
   * @param colorRes Resource ID of the color to apply (e.g. `R.color.primary`).
   * @param darkBackground `true` if the status bar background is dark – icons will be drawn in
   *   light color for contrast.
   */
  @JvmStatic
  fun setStatusBarColor(activity: Activity, @ColorRes colorRes: Int, darkBackground: Boolean) {
    val window = activity.window

    // 1. Apply background color
    @Suppress("DEPRECATION")
    try {
      val color = ContextCompat.getColor(activity, colorRes)
      window.statusBarColor = color
    } catch (e: Exception) {
      // Resource not found – log and do not crash
      Timber.e(e, "Failed to set status bar color from resource %s", colorRes)
    }

    // 2. Adjust icon appearance for contrast
    setStatusBarAppearance(window, darkBackground)
  }

  /**
   * Updates the status bar icon appearance (light/dark) based on the background.
   *
   * Uses [WindowInsetsController] on API 30+ for a modern, reliable API; falls back to
   * [View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR] on older versions.
   */
  private fun setStatusBarAppearance(window: Window, darkBackground: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val controller = window.insetsController ?: return
      if (darkBackground) {
        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
      } else {
        controller.setSystemBarsAppearance(
          WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
          WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
      }
    } else {
      @Suppress("DEPRECATION") val flags = window.decorView.systemUiVisibility
      @Suppress("DEPRECATION")
      if (darkBackground) {
        window.decorView.systemUiVisibility = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
      } else {
        window.decorView.systemUiVisibility = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      }
    }
  }
}
