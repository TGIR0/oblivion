package org.bepass.oblivion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bepass.oblivion.R
import org.bepass.oblivion.ui.viewmodel.SettingsViewModel
import org.bepass.oblivion.utils.FileManager
import org.bepass.oblivion.utils.FontSizeHelper
import org.bepass.oblivion.utils.LocaleManager
import org.bepass.oblivion.utils.ThemeHelper
import org.bepass.oblivion.utils.isBatteryOptimizationEnabled
import org.bepass.oblivion.utils.requestIgnoreBatteryOptimizations

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onSplitTunnel: () -> Unit,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  viewModel.vpnConfig.collectAsStateWithLifecycle()
  val selectedTheme by ThemeHelper.themeFlow.collectAsStateWithLifecycle()
  val selectedFontSize by FontSizeHelper.fontSizeFlow.collectAsStateWithLifecycle()
  var endpoint by remember { mutableStateOf(FileManager.getString(FileManager.Keys.USERSETTING_ENDPOINT)) }
  var port by remember { mutableStateOf(FileManager.getString(FileManager.Keys.USERSETTING_PORT)) }
  var dns by remember { mutableStateOf(FileManager.getString(FileManager.Keys.USERSETTING_DNS).ifEmpty { "1.1.1.1" }) }
  var endpointType by remember { mutableIntStateOf(FileManager.getInt(FileManager.Keys.USERSETTING_ENDPOINT_TYPE)) }
  var region by remember { mutableIntStateOf(FileManager.getInt(FileManager.Keys.USERSETTING_REGION)) }
  var lan by remember { mutableStateOf(FileManager.getBoolean(FileManager.Keys.USERSETTING_LAN)) }
  var gool by remember { mutableStateOf(FileManager.getBoolean(FileManager.Keys.USERSETTING_GOOL)) }
  var proxy by remember { mutableStateOf(FileManager.getBoolean(FileManager.Keys.USERSETTING_PROXYMODE)) }
  var showEndpointSheet by remember { mutableStateOf(false) }
  var showPortDialog by remember { mutableStateOf(false) }
  var showDnsSheet by remember { mutableStateOf(false) }
  var showEndpointTypeDialog by remember { mutableStateOf(false) }
  var showRegionDialog by remember { mutableStateOf(false) }
  var showBatteryDialog by remember { mutableStateOf(false) }
  var showThemeDialog by remember { mutableStateOf(false) }
  var showFontSizeDialog by remember { mutableStateOf(false) }
  var showLanguageDialog by remember { mutableStateOf(false) }
  val endpointTypes = stringArrayResource(R.array.endpointType)
  val regions = stringArrayResource(R.array.regions)
  val themeOptions = remember { listOf(ThemeHelper.Theme.OLED, ThemeHelper.Theme.DARK, ThemeHelper.Theme.LIGHT) }
  val fontSizeOptions =
    remember {
      listOf(
        FontSizeHelper.FontSize.SMALL,
        FontSizeHelper.FontSize.DEFAULT,
        FontSizeHelper.FontSize.LARGE,
      )
    }
  val themeLabels =
    listOf(
      stringResource(R.string.theme_oled),
      stringResource(R.string.theme_dark),
      stringResource(R.string.theme_light),
    )
  val fontSizeLabels =
    listOf(
      stringResource(R.string.font_size_small),
      stringResource(R.string.font_size_default),
      stringResource(R.string.font_size_large),
    )
  val currentLocaleTag = LocaleManager.currentLocaleTag()
  val currentLanguage =
    if (currentLocaleTag.isBlank()) {
      stringResource(R.string.system_default)
    } else {
      LocaleManager.availableLocales(context).firstOrNull { it.tag == currentLocaleTag }?.displayName
        ?: currentLocaleTag
    }

  Column(Modifier.fillMaxSize()) {
    ScreenHeader(title = stringResource(R.string.settingsText), onBack = onBack)
    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.appearance_personalization),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      SettingsRow(
        title = stringResource(R.string.themeText),
        description = stringResource(R.string.themeTextDesc),
        value = themeLabels.getOrElse(themeOptions.indexOf(selectedTheme)) { themeLabels.first() },
        onClick = { showThemeDialog = true },
      )
      SettingsRow(
        title = stringResource(R.string.font_size_text),
        description = stringResource(R.string.font_size_desc),
        value = fontSizeLabels.getOrElse(fontSizeOptions.indexOf(selectedFontSize)) { fontSizeLabels[1] },
        onClick = { showFontSizeDialog = true },
      )
      SettingsRow(
        title = stringResource(R.string.select_language),
        description = stringResource(R.string.select_language_desc),
        value = currentLanguage,
        onClick = { showLanguageDialog = true },
      )
      SettingsRow(
        title = stringResource(R.string.endpointText),
        description = stringResource(R.string.endpointTextDesc),
        value = endpoint.ifBlank { "Auto" },
        onClick = { showEndpointSheet = true },
      )
      SettingsRow(
        title = stringResource(R.string.portTunText),
        description = stringResource(R.string.portTunTextDesc),
        value = port,
        onClick = { showPortDialog = true },
      )
      SettingsRow(
        title = stringResource(R.string.dnsText),
        description = stringResource(R.string.dnsTextDesc),
        value = dns,
        onClick = { showDnsSheet = true },
      )
      SettingsRow(
        title = stringResource(R.string.endpointTypeText),
        description = stringResource(R.string.endpointTypeTextDesc),
        value = endpointTypes.getOrElse(endpointType) { endpointTypes.firstOrNull().orEmpty() },
        onClick = { showEndpointTypeDialog = true },
      )
      SettingsRow(
        title = stringResource(R.string.regionText),
        description = stringResource(R.string.regionTextDesc),
        value = regions.getOrElse(region) { regions.firstOrNull().orEmpty() },
        onClick = { showRegionDialog = true },
      )
      SwitchSettingsRow(stringResource(R.string.goolText), stringResource(R.string.goolTextDesc), gool) {
        gool = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_GOOL, it)
      }
      SwitchSettingsRow(stringResource(R.string.connectFromLanText), stringResource(R.string.connectFromLanTextDesc), lan) {
        lan = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_LAN, it)
      }
      SwitchSettingsRow(stringResource(R.string.proxy_mode), stringResource(R.string.running_in_proxy_mode_not_vpn), proxy) {
        proxy = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_PROXYMODE, it)
      }
      SwitchSettingsRow(stringResource(R.string.masqueText), stringResource(R.string.masqueTextDesc), false, enabled = false) {}
      SettingsRow(
        title = stringResource(R.string.blackList),
        description = stringResource(R.string.blackListTextDesc),
        onClick = onSplitTunnel,
      )
      if (isBatteryOptimizationEnabled(context)) {
        SettingsRow(
          title = stringResource(R.string.batteryOpL),
          description = stringResource(R.string.batteryOpLText),
          onClick = { showBatteryDialog = true },
        )
      }
      SettingsRow(
        title = stringResource(R.string.resetAppText),
        description = stringResource(R.string.resetAppTextDesc),
        onClick = {
          FileManager.resetToDefault()
          FileManager.cleanOrMigrateSettings(context)
          ThemeHelper.getInstance().init()
          FontSizeHelper.init()
          endpoint = FileManager.getString(FileManager.Keys.USERSETTING_ENDPOINT)
          port = FileManager.getString(FileManager.Keys.USERSETTING_PORT)
          dns = FileManager.getString(FileManager.Keys.USERSETTING_DNS)
          endpointType = FileManager.getInt(FileManager.Keys.USERSETTING_ENDPOINT_TYPE)
          region = FileManager.getInt(FileManager.Keys.USERSETTING_REGION)
          lan = FileManager.getBoolean(FileManager.Keys.USERSETTING_LAN)
          gool = FileManager.getBoolean(FileManager.Keys.USERSETTING_GOOL)
          proxy = FileManager.getBoolean(FileManager.Keys.USERSETTING_PROXYMODE)
        },
      )
    }
  }

  if (showThemeDialog) {
    OptionDialog(
      title = stringResource(R.string.themeText),
      options = themeLabels,
      selectedIndex = themeOptions.indexOf(selectedTheme).let { if (it >= 0) it else 0 },
      onDismiss = { showThemeDialog = false },
      onSelected = {
        themeOptions.getOrNull(it)?.let(ThemeHelper::select)
        showThemeDialog = false
      },
    )
  }
  if (showFontSizeDialog) {
    OptionDialog(
      title = stringResource(R.string.font_size_text),
      options = fontSizeLabels,
      selectedIndex = fontSizeOptions.indexOf(selectedFontSize).let { if (it >= 0) it else 1 },
      onDismiss = { showFontSizeDialog = false },
      onSelected = {
        fontSizeOptions.getOrNull(it)?.let(FontSizeHelper::select)
        showFontSizeDialog = false
      },
    )
  }
  if (showLanguageDialog) {
    LanguageDialog(
      onDismiss = { showLanguageDialog = false },
      onLocaleChanged = { showLanguageDialog = false },
    )
  }
  if (showEndpointSheet) {
    EndpointSheet(
      onDismiss = { showEndpointSheet = false },
      onSelected = {
        endpoint = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_ENDPOINT, it)
        showEndpointSheet = false
      },
    )
  }
  if (showPortDialog) {
    EditValueDialog(
      title = stringResource(R.string.portTunText),
      value = port,
      onDismiss = { showPortDialog = false },
      onSave = {
        port = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_PORT, it)
        showPortDialog = false
      },
    )
  }
  if (showDnsSheet) {
    DnsSettingsSheet(onDismiss = {
      dns = FileManager.getString(FileManager.Keys.USERSETTING_DNS).ifEmpty { "1.1.1.1" }
      showDnsSheet = false
    })
  }
  if (showEndpointTypeDialog) {
    OptionDialog(
      title = stringResource(R.string.endpointTypeText),
      options = endpointTypes.toList(),
      selectedIndex = endpointType,
      onDismiss = { showEndpointTypeDialog = false },
      onSelected = {
        endpointType = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_ENDPOINT_TYPE, it)
        showEndpointTypeDialog = false
      },
    )
  }
  if (showRegionDialog) {
    OptionDialog(
      title = stringResource(R.string.regionText),
      options = regions.toList(),
      selectedIndex = region,
      onDismiss = { showRegionDialog = false },
      onSelected = {
        region = it
        viewModel.updateSetting(FileManager.Keys.USERSETTING_REGION, it)
        showRegionDialog = false
      },
    )
  }
  if (showBatteryDialog) {
    AlertDialog(
      onDismissRequest = { showBatteryDialog = false },
      title = { Text(stringResource(R.string.batteryOpL)) },
      text = { Text(stringResource(R.string.dialBtText)) },
      confirmButton = {
        TextButton(onClick = {
          requestIgnoreBatteryOptimizations(context)
          showBatteryDialog = false
        }) {
          Text(stringResource(R.string.goToSettings))
        }
      },
      dismissButton = { TextButton(onClick = { showBatteryDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}
