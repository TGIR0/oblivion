package org.bepass.oblivion.utils

import androidx.annotation.ColorInt

private const val RED_LUMINANCE_WEIGHT = 0.299
private const val GREEN_LUMINANCE_WEIGHT = 0.587
private const val BLUE_LUMINANCE_WEIGHT = 0.114
private const val MAX_COLOR_CHANNEL = 255.0
private const val DARK_LUMINANCE_THRESHOLD = 0.5

fun @receiver:ColorInt Int.isColorDark(): Boolean {
  val luminance =
    (RED_LUMINANCE_WEIGHT * android.graphics.Color.red(this) +
      GREEN_LUMINANCE_WEIGHT * android.graphics.Color.green(this) +
      BLUE_LUMINANCE_WEIGHT * android.graphics.Color.blue(this)) / MAX_COLOR_CHANNEL
  return luminance <= DARK_LUMINANCE_THRESHOLD
}
