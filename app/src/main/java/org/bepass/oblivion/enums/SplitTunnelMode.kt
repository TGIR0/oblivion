package org.bepass.oblivion.enums

import org.bepass.oblivion.utils.FileManager

enum class SplitTunnelMode {
  DISABLED,
  BLACKLIST;

  companion object {
    @JvmStatic
    fun getSplitTunnelMode(): SplitTunnelMode {
      return runCatching { valueOf(FileManager.getString("splitTunnelMode", DISABLED.toString())) }
        .getOrDefault(DISABLED)
    }
  }
}
