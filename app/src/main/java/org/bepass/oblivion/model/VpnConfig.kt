package org.bepass.oblivion.model

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VpnConfig(
  val endpoint: String,
  val bindAddress: String,
  val dns: String,
  val vpnDns: String,
  val proxyMode: Boolean,
  val psiphonCountry: String,
) : Parcelable {
  companion object {
    fun fromIntent(intent: Intent, bindAddress: String): VpnConfig {
      return VpnConfig(
        endpoint = getEndpoint(intent),
        bindAddress = bindAddress,
        dns = intent.getStringExtra("USERSETTING_dns").orEmpty().ifBlank { "1.1.1.1" },
        vpnDns =
          intent.getStringExtra("USERSETTING_vpn_dns").orEmpty().ifBlank {
            intent.getStringExtra("USERSETTING_dns").orEmpty().ifBlank { "1.1.1.1" }
          },
        proxyMode = intent.getBooleanExtra("USERSETTING_proxymode", false),
        psiphonCountry = intent.getStringExtra("USERSETTING_psiphon_country").orEmpty(),
      )
    }

    private fun getEndpoint(intent: Intent): String {
      val endpoint = intent.getStringExtra("USERSETTING_endpoint").orEmpty().trim()
      val isAuto =
        endpoint.isEmpty() ||
          endpoint == "engage.cloudflareclient.com:2408" ||
          endpoint.equals("auto", ignoreCase = true)
      return if (isAuto) "" else endpoint
    }
  }
}
