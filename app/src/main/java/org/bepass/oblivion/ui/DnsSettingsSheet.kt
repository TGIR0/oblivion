package org.bepass.oblivion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bepass.oblivion.R
import org.bepass.oblivion.dns.DnsCatalogRegionFilter
import org.bepass.oblivion.dns.DnsSection
import org.bepass.oblivion.dns.DnsSelectionMode
import org.bepass.oblivion.dns.DnsTransport
import org.bepass.oblivion.dns.DnsUiCatalogFilters
import org.bepass.oblivion.dns.USER_CONFIGURABLE_DNS_SECTIONS
import org.bepass.oblivion.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsSheet(onDismiss: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
  val state by viewModel.dnsUiState.collectAsStateWithLifecycle()
  var providerSection by remember { mutableStateOf<DnsSection?>(null) }
  var manualSection by remember { mutableStateOf<DnsSection?>(null) }
  var manualText by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.dnsText))
      Text(
        state.runtimePlan.sectionSummaries.values.joinToString("\n").ifBlank {
          stringResource(R.string.dns_runtime_default)
        }
      )
      state.refreshMessage?.let { Text(it) }
      state.warnings.forEach { Text(stringResource(R.string.dns_warning_format, it)) }
      Button(enabled = !state.isRefreshing, onClick = viewModel::refreshDnsCatalog) {
        Text(
          if (state.isRefreshing) stringResource(R.string.refreshing)
          else stringResource(R.string.refresh_dns_catalog)
        )
      }
      DnsSectionBlock(
        DnsSection.GLOBAL,
        viewModel,
        onProvider = { providerSection = it },
        onManual = { section, input ->
          manualSection = section
          manualText = input
        },
      )
      USER_CONFIGURABLE_DNS_SECTIONS.forEach { section ->
        DnsSectionBlock(
          section,
          viewModel,
          onProvider = { providerSection = it },
          onManual = { sec, input ->
            manualSection = sec
            manualText = input
          },
        )
      }
    }
  }

  providerSection?.let { section ->
    val options =
      state
        .filteredProviders(DnsUiCatalogFilters(region = DnsCatalogRegionFilter.ALL))
        .filter { section in it.supportsInLayers }
        .flatMap { provider ->
          provider.transports
            .filterNot { it == DnsTransport.SYSTEM }
            .sortedBy { it.name }
            .map {
              Triple(provider.providerId, provider.label, it)
            }
        }
    OptionDialog(
      title = section.displayName(),
      options = options.map { "${it.second} - ${it.third.name}" },
      selectedIndex = -1,
      onDismiss = { providerSection = null },
      onSelected = {
        val selected = options[it]
        viewModel.setDnsProvider(section, selected.first, selected.third)
        providerSection = null
      },
    )
  }
  manualSection?.let { section ->
    AlertDialog(
      onDismissRequest = { manualSection = null },
      title = { Text(section.displayName()) },
      text = {
        OutlinedTextField(
          value = manualText,
          onValueChange = { manualText = it },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (viewModel.setDnsManual(section, manualText) == null) manualSection = null
          }
        ) {
          Text(stringResource(R.string.update))
        }
      },
      dismissButton = {
        TextButton(onClick = { manualSection = null }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

@Composable
private fun DnsSectionBlock(
  section: DnsSection,
  viewModel: SettingsViewModel,
  onProvider: (DnsSection) -> Unit,
  onManual: (DnsSection, String) -> Unit,
) {
  val state by viewModel.dnsUiState.collectAsStateWithLifecycle()
  val selection = state.profile.selectionFor(section)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(section.displayName())
    Text(state.runtimePlan.sectionSummaries[section].orEmpty().ifBlank { selection.mode.name })
    val modes = buildList {
      if (section != DnsSection.GLOBAL) add(DnsSelectionMode.INHERIT)
      add(DnsSelectionMode.SYSTEM)
      add(DnsSelectionMode.PROVIDER)
      add(DnsSelectionMode.MANUAL)
    }
    modes.forEach { mode ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
          selected = selection.mode == mode,
          onClick = {
            when (mode) {
              DnsSelectionMode.INHERIT -> viewModel.setDnsInherit(section)
              DnsSelectionMode.SYSTEM -> viewModel.setDnsSystem(section)
              DnsSelectionMode.PROVIDER -> onProvider(section)
              DnsSelectionMode.MANUAL -> onManual(section, selection.manualInput)
            }
          },
        )
        Text(mode.displayName())
      }
    }
  }
}

private fun DnsSection.displayName(): String =
  when (this) {
    DnsSection.GLOBAL -> "Global DNS"
    DnsSection.TUNNEL_CORE -> "Tunnel core DNS"
    DnsSection.VPN_INTERFACE -> "VPN interface DNS"
    DnsSection.APP_HTTP -> "App HTTP DNS"
    DnsSection.DIAGNOSTICS -> "Diagnostics DNS"
  }

private fun DnsSelectionMode.displayName(): String =
  when (this) {
    DnsSelectionMode.INHERIT -> "Inherit"
    DnsSelectionMode.SYSTEM -> "System"
    DnsSelectionMode.PROVIDER -> "Provider"
    DnsSelectionMode.MANUAL -> "Manual"
  }
