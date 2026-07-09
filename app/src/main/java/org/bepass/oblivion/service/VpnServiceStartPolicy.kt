package org.bepass.oblivion.service

internal enum class VpnStartDirective {
  START_APP,
  START_ALWAYS_ON,
  STOP,
  IGNORE,
}

internal enum class VpnRestartMode {
  REDELIVER_INTENT,
  STICKY,
  NOT_STICKY,
}

internal data class VpnStartDecision(
  val directive: VpnStartDirective,
  val restartMode: VpnRestartMode,
  val reason: String,
)

internal object VpnServiceStartPolicy {
  fun decide(
    action: String?,
    alwaysOn: Boolean,
    selectedCoreReady: Boolean,
  ): VpnStartDecision =
    when {
      action == OblivionVpnService.FLAG_VPN_STOP ->
        VpnStartDecision(
          directive = VpnStartDirective.STOP,
          restartMode = VpnRestartMode.NOT_STICKY,
          reason = "explicit_stop",
        )
      action == OblivionVpnService.FLAG_VPN_START && selectedCoreReady ->
        VpnStartDecision(
          directive = VpnStartDirective.START_APP,
          restartMode = VpnRestartMode.REDELIVER_INTENT,
          reason = "explicit_start",
        )
      action == OblivionVpnService.FLAG_VPN_START ->
        VpnStartDecision(
          directive = VpnStartDirective.IGNORE,
          restartMode = VpnRestartMode.NOT_STICKY,
          reason = "selected_core_unavailable",
        )
      alwaysOn && selectedCoreReady ->
        VpnStartDecision(
          directive = VpnStartDirective.START_ALWAYS_ON,
          restartMode = VpnRestartMode.STICKY,
          reason = "system_always_on",
        )
      alwaysOn ->
        VpnStartDecision(
          directive = VpnStartDirective.IGNORE,
          restartMode = VpnRestartMode.NOT_STICKY,
          reason = "always_on_core_unavailable",
        )
      else ->
        VpnStartDecision(
          directive = VpnStartDirective.IGNORE,
          restartMode = VpnRestartMode.NOT_STICKY,
          reason = "unrecognized_or_stale_start",
        )
    }
}
