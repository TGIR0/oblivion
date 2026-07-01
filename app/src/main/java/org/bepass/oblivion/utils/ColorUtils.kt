package org.bepass.oblivion.utils

import androidx.annotation.ColorInt

fun @receiver:ColorInt Int.isColorDark(): Boolean {
  val luminance =
    (0.299 * android.graphics.Color.red(this) +
      0.587 * android.graphics.Color.green(this) +
      0.114 * android.graphics.Color.blue(this)) / 255.0
  return luminance <= 0.5
}
