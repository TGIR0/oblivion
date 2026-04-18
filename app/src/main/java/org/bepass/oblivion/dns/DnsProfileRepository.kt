package org.bepass.oblivion.dns

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bepass.oblivion.utils.FileManager

object DnsProfileRepository {
  private const val TAG = "DnsProfileRepo"

  const val KEY_DNS_PROFILE_JSON = "USERSETTING_dns_profile_json"
  const val KEY_DNS_MIGRATION_VERSION = "USERSETTING_dns_migration_version"
  const val KEY_DNS_RUNTIME_PLAN_JSON = "USERSETTING_dns_runtime_plan_json"
  const val KEY_LEGACY_VPN_DNS = "USERSETTING_vpn_dns"
  private const val MIGRATION_VERSION = 1

  private val lock = Any()
  @Volatile private var initialized = false

  private val planner = DnsExecutionPlanner()
  private val _profileFlow = MutableStateFlow(DnsProfile.defaults())
  val profileFlow: StateFlow<DnsProfile> = _profileFlow.asStateFlow()

  private val _runtimePlanFlow = MutableStateFlow(DnsRuntimePlan.defaults())
  val runtimePlanFlow: StateFlow<DnsRuntimePlan> = _runtimePlanFlow.asStateFlow()

  fun initialize(context: Context) {
    if (initialized) return
    synchronized(lock) {
      if (initialized) return
      FileManager.initialize(context.applicationContext)
      DnsCatalogRepository.initialize(context.applicationContext)
      val profile = loadOrMigrateProfileLocked()
      val catalog = DnsCatalogRepository.currentCatalog()
      val runtimePlan = planner.compile(profile, catalog)
      persistLocked(profile, runtimePlan)
      _profileFlow.value = profile
      _runtimePlanFlow.value = runtimePlan
      initialized = true
    }
  }

  fun currentProfile(): DnsProfile = _profileFlow.value

  fun currentRuntimePlan(): DnsRuntimePlan = _runtimePlanFlow.value

  fun updateProfile(context: Context, transform: (DnsProfile) -> DnsProfile): DnsProfile {
    initialize(context.applicationContext)
    synchronized(lock) {
      val updated = transform(_profileFlow.value).normalized()
      val runtime = planner.compile(updated, DnsCatalogRepository.currentCatalog())
      persistLocked(updated, runtime)
      _profileFlow.value = updated
      _runtimePlanFlow.value = runtime
      return updated
    }
  }

  fun recompileWithCurrentCatalog(context: Context) {
    initialize(context.applicationContext)
    synchronized(lock) {
      val runtime = planner.compile(_profileFlow.value, DnsCatalogRepository.currentCatalog())
      persistRuntimeLocked(runtime)
      _runtimePlanFlow.value = runtime
    }
  }

  fun reloadFromStorage(context: Context) {
    initialize(context.applicationContext)
    synchronized(lock) {
      val profile = loadOrMigrateProfileLocked()
      val runtime = planner.compile(profile, DnsCatalogRepository.currentCatalog())
      persistLocked(profile, runtime)
      _profileFlow.value = profile
      _runtimePlanFlow.value = runtime
    }
  }

  suspend fun refreshCatalogAndRecompile(context: Context): DnsCatalogRefreshResult {
    initialize(context.applicationContext)
    val result = DnsCatalogRepository.refreshCatalog(context.applicationContext)
    synchronized(lock) {
      val runtime = planner.compile(_profileFlow.value, result.catalog)
      persistRuntimeLocked(runtime)
      _runtimePlanFlow.value = runtime
    }
    return result
  }

  fun loadPersistedRuntimePlanOrDefault(): DnsRuntimePlan {
    val raw = FileManager.getString(KEY_DNS_RUNTIME_PLAN_JSON).trim()
    if (raw.isBlank()) return DnsRuntimePlan.defaults()
    return runCatching { DnsJsonCodec.runtimePlanFromJson(raw) }
      .onFailure { Log.w(TAG, "Failed to parse persisted runtime plan", it) }
      .getOrDefault(DnsRuntimePlan.defaults())
  }

  private fun loadOrMigrateProfileLocked(): DnsProfile {
    val raw = FileManager.getString(KEY_DNS_PROFILE_JSON).trim()
    val parsed =
      if (raw.isNotBlank()) {
        runCatching { DnsJsonCodec.profileFromJson(raw) }
          .onFailure { Log.w(TAG, "Failed to parse DNS profile JSON; migrating from legacy setting", it) }
          .getOrNull()
      } else {
        null
      }

    val profile = (parsed ?: migrateFromLegacyLocked()).normalized()
    FileManager.set(KEY_DNS_MIGRATION_VERSION, MIGRATION_VERSION)
    return profile
  }

  private fun migrateFromLegacyLocked(): DnsProfile {
    val legacyDns = FileManager.getString("USERSETTING_dns").trim()
    if (legacyDns.isBlank()) return DnsProfile.defaults()

    val validation = DnsUriParser.parseBulk(legacyDns)
    if (validation.endpoints.isEmpty()) return DnsProfile.defaults()

    val selection =
      if (validation.endpoints.size == 1 &&
        validation.endpoints.first().transport == DnsTransport.PLAIN &&
        validation.endpoints.first().host == "1.1.1.1"
      ) {
        DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)
      } else {
        DnsSelection.manual(legacyDns)
      }

    return DnsProfile.defaults().copy(global = selection)
  }

  private fun persistLocked(profile: DnsProfile, runtimePlan: DnsRuntimePlan) {
    FileManager.set(KEY_DNS_PROFILE_JSON, DnsJsonCodec.profileToJson(profile))
    persistRuntimeLocked(runtimePlan)
  }

  private fun persistRuntimeLocked(runtimePlan: DnsRuntimePlan) {
    FileManager.set(KEY_DNS_RUNTIME_PLAN_JSON, DnsJsonCodec.runtimePlanToJson(runtimePlan))
    FileManager.set("USERSETTING_dns", runtimePlan.tunnelCoreCsv)
    FileManager.set(KEY_LEGACY_VPN_DNS, runtimePlan.vpnInterfaceCsv)
  }

  private fun DnsProfile.normalized(): DnsProfile {
    val normalizedOverrides = DnsProfile.defaultOverrides().toMutableMap()
    for (section in USER_CONFIGURABLE_DNS_SECTIONS) {
      normalizedOverrides[section] = overrides[section] ?: DnsSelection.inherit()
    }
    val normalizedGlobal =
      if (global.mode == DnsSelectionMode.INHERIT) DnsSelection.provider(DnsProfile.DEFAULT_PROVIDER_ID, DnsTransport.PLAIN)
      else global
    return copy(
      version = DnsProfile.CURRENT_VERSION,
      global = normalizedGlobal,
      overrides = normalizedOverrides,
    )
  }
}
