package org.bepass.oblivion.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import org.bepass.oblivion.dns.DnsCatalogRepository
import org.bepass.oblivion.dns.DnsProfileRepository
import org.bepass.oblivion.dns.DnsSection
import org.bepass.oblivion.dns.DnsSelection
import org.bepass.oblivion.dns.DnsTransport
import org.bepass.oblivion.dns.DnsUiState
import org.bepass.oblivion.dns.DnsUriParser
import org.bepass.oblivion.repository.SettingsRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private data class DnsRefreshState(
        val isRefreshing: Boolean = false,
        val message: String? = null,
    )

    private val dnsRefreshState = MutableStateFlow(DnsRefreshState())

    init {
        DnsCatalogRepository.initialize(appContext)
        DnsProfileRepository.initialize(appContext)
    }

    val config = repository.vpnConfig.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        repository.getInt("USERSETTING_endpoint_type", 0) // Just a seed or full config
    )

    // Modern reactive accessor for full config
    val vpnConfig = repository.vpnConfig.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        org.bepass.oblivion.utils.FileManager.getVpnConfig()
    )

    val dnsUiState = combine(
        DnsProfileRepository.profileFlow,
        DnsCatalogRepository.catalogFlow,
        DnsProfileRepository.runtimePlanFlow,
        DnsCatalogRepository.lastRefreshEpochMsFlow,
        dnsRefreshState,
    ) { profile, catalog, runtimePlan, lastRefresh, refresh ->
        DnsUiState(
            profile = profile,
            catalog = catalog,
            runtimePlan = runtimePlan,
            isRefreshing = refresh.isRefreshing,
            refreshMessage = refresh.message,
            lastRefreshEpochMs = lastRefresh,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DnsUiState(
            profile = DnsProfileRepository.currentProfile(),
            catalog = DnsCatalogRepository.currentCatalog(),
            runtimePlan = DnsProfileRepository.currentRuntimePlan(),
            lastRefreshEpochMs = DnsCatalogRepository.lastRefreshEpochMsFlow.value,
        )
    )

    fun updateSetting(key: String, value: Any) {
        when (value) {
            is String -> repository.setString(key, value)
            is Boolean -> repository.setBoolean(key, value)
            is Int -> repository.setInt(key, value)
        }
    }

    fun validateManualDns(input: String): String? = DnsUriParser.validate(input)

    fun setDnsSelection(section: DnsSection, selection: DnsSelection) {
        DnsProfileRepository.updateProfile(appContext) { current ->
            current.withSelection(section, selection)
        }
        repository.updateConfig()
        dnsRefreshState.update { it.copy(message = null) }
    }

    fun setDnsProvider(section: DnsSection, providerId: String, transport: DnsTransport?) {
        setDnsSelection(section, DnsSelection.provider(providerId, transport))
    }

    fun setDnsManual(section: DnsSection, input: String): String? {
        val error = validateManualDns(input)
        if (error != null) {
            dnsRefreshState.update { it.copy(message = error) }
            return error
        }
        setDnsSelection(section, DnsSelection.manual(input.trim()))
        return null
    }

    fun setDnsSystem(section: DnsSection) {
        setDnsSelection(section, DnsSelection.system())
    }

    fun setDnsInherit(section: DnsSection) {
        setDnsSelection(section, DnsSelection.inherit())
    }

    fun clearDnsMessage() {
        dnsRefreshState.update { it.copy(message = null) }
    }

    fun refreshDnsCatalog() {
        viewModelScope.launch {
            dnsRefreshState.update { it.copy(isRefreshing = true, message = null) }
            val result = DnsProfileRepository.refreshCatalogAndRecompile(appContext)
            repository.updateConfig()
            val message =
                when {
                    result.errorMessage != null -> "DNS catalog refresh failed: ${result.errorMessage}"
                    result.updated -> "DNS catalog updated"
                    else -> "DNS catalog is already up to date"
                }
            dnsRefreshState.update { it.copy(isRefreshing = false, message = message) }
        }
    }

    fun pingEndpoint(endpoint: String, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val hostPort = endpoint.split(":")
                    if (hostPort.size != 2) return@withContext null

                    val host = hostPort[0].removePrefix("[").removeSuffix("]")
                    val port = hostPort[1].toIntOrNull() ?: return@withContext null

                    val time = measureTimeMillis {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), 2000)
                        }
                    }
                    time
                } catch (e: Exception) {
                    null
                }
            }
            onResult(result)
        }
    }
}
