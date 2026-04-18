package org.bepass.oblivion.utils

import android.content.Context
import java.io.File

object LogFiles {
  private const val LOG_FILE_NAME = "logs.txt"
  private const val ROTATED_PREFIX = "$LOG_FILE_NAME."

  fun list(context: Context): List<File> {
    val files = context.filesDir.listFiles().orEmpty()
    return files.filter { file ->
      file.name == LOG_FILE_NAME || file.name.startsWith(ROTATED_PREFIX)
    }
  }

  fun deleteAll(context: Context): Int {
    var deletedCount = 0
    for (file in list(context)) {
      if (file.exists() && file.delete()) {
        deletedCount++
      }
    }
    return deletedCount
  }
}
