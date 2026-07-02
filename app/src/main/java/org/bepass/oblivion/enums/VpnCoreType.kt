package org.bepass.oblivion.enums

import androidx.annotation.StringRes
import org.bepass.oblivion.R
import org.bepass.oblivion.model.TunnelMode
import org.bepass.oblivion.utils.FileManager

enum class VpnCoreType(
  val storageId: Int,
  val modeId: String,
  val tunnelMode: TunnelMode,
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
  @StringRes val availabilityReasonRes: Int,
  val isReady: Boolean,
) {
  WARP(
    storageId = 0,
    modeId = "WARP",
    tunnelMode = TunnelMode.WARP,
    labelRes = R.string.core_warp,
    descriptionRes = R.string.core_warp_desc,
    availabilityReasonRes = R.string.core_gate_warp,
    isReady = false,
  ),
  VWARP(
    storageId = 1,
    modeId = "VWARP_MASQUE",
    tunnelMode = TunnelMode.VWARP_MASQUE,
    labelRes = R.string.core_vwarp,
    descriptionRes = R.string.core_vwarp_desc,
    availabilityReasonRes = R.string.core_gate_masque,
    isReady = false,
  ),
  PSIPHON(
    storageId = 2,
    modeId = "PSIPHON",
    tunnelMode = TunnelMode.PSIPHON,
    labelRes = R.string.core_psiphon,
    descriptionRes = R.string.core_psiphon_desc,
    availabilityReasonRes = R.string.core_gate_psiphon,
    isReady = false,
  ),
  PSIPHON_OVER_WARP(
    storageId = 3,
    modeId = "PSIPHON_OVER_WARP",
    tunnelMode = TunnelMode.PSIPHON_OVER_WARP,
    labelRes = R.string.core_psiphon_warp,
    descriptionRes = R.string.core_psiphon_warp_desc,
    availabilityReasonRes = R.string.core_gate_chain,
    isReady = false,
  ),
  WARP_OVER_PSIPHON(
    storageId = 4,
    modeId = "WARP_OVER_PSIPHON",
    tunnelMode = TunnelMode.WARP_OVER_PSIPHON,
    labelRes = R.string.core_warp_over_psiphon,
    descriptionRes = R.string.core_warp_over_psiphon_desc,
    availabilityReasonRes = R.string.core_gate_chain,
    isReady = false,
  ),
  PSIPHON_FRONTED(
    storageId = 5,
    modeId = "PSIPHON_FRONTED",
    tunnelMode = TunnelMode.PSIPHON_FRONTED,
    labelRes = R.string.core_psiphon_mitm,
    descriptionRes = R.string.core_psiphon_mitm_desc,
    availabilityReasonRes = R.string.core_gate_fronted,
    isReady = false,
  ),
  WIREGUARD(
    storageId = 6,
    modeId = "WIREGUARD",
    tunnelMode = TunnelMode.WIREGUARD,
    labelRes = R.string.core_wireguard,
    descriptionRes = R.string.core_wireguard_desc,
    availabilityReasonRes = R.string.core_gate_wireguard,
    isReady = false,
  );

  companion object {
    private val map = entries.associateBy(VpnCoreType::storageId)

    fun fromInt(value: Int): VpnCoreType = map[value] ?: WARP

    @JvmStatic
    fun getCurrent(): VpnCoreType =
      fromInt(FileManager.getInt(FileManager.Keys.USERSETTING_VPN_CORE))
  }
}
