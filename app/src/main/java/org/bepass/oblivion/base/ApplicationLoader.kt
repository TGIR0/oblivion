package org.bepass.oblivion.base

import android.app.Application
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import org.bepass.oblivion.dns.DnsProfileRepository
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.LocaleManager
import org.bepass.oblivion.utils.ThemeHelper

@HiltAndroidApp
class ApplicationLoader : Application() {
  override fun onCreate() {
    super.onCreate()
    FileManager.initialize(this)
    FileManager.cleanOrMigrateSettings(this)
    LocaleManager.initialize(this)
    DnsProfileRepository.initialize(this)
    ThemeHelper.getInstance().init()
    ThemeHelper.getInstance().applyTheme()
    DynamicColors.applyToActivitiesIfAvailable(this)
  }
}
