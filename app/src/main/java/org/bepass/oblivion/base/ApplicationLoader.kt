package org.bepass.oblivion.base

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bepass.oblivion.dns.DnsProfileRepository
import org.bepass.oblivion.logging.SecureLog as Log
import org.bepass.oblivion.security.AndroidSecureStore
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.LocaleManager
import org.bepass.oblivion.utils.ThemeHelper

@HiltAndroidApp
class ApplicationLoader : Application() {
  override fun onCreate() {
    super.onCreate()
    FileManager.initialize(this)
    val legacyLicense = FileManager.cleanOrMigrateSettings(this)
    if (!legacyLicense.isNullOrBlank()) {
      runCatching { AndroidSecureStore(this).put(WARP_LICENSE_REF, legacyLicense) }
        .onFailure { Log.e(TAG, "Legacy WARP license migration failed", it) }
      FileManager.set(FileManager.Keys.USERSETTING_LICENSE, "")
    }
    LocaleManager.initialize(this)
    DnsProfileRepository.initialize(this)
    ThemeHelper.getInstance().init()
    ThemeHelper.getInstance().applyTheme()
  }

  private companion object {
    const val TAG = "ApplicationLoader"
    const val WARP_LICENSE_REF = "warp.license.v2"
  }
}
