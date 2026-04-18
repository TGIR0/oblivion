package org.bepass.oblivion.model

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VpnConfig(
    val endpoint: String,
    val bindAddress: String,
    val license: String,
    val dns: String,
    val vpnDns: String,
    val region: Int,
    val endpointType: Int,
    val gool: Boolean,
    val masque: Boolean,
    val masquePreferred: Boolean,
    val masqueNoize: Boolean,
    val masquePreset: String,
    val proxyMode: Boolean,
    val anycastIPs: String,
    val preferredFingerprint: String,
    val port: String,
    val conduit: Boolean,
    val psiphonCountry: String,
    val lan: Boolean,
    val proxyBypass: String
) : Parcelable {
    companion object {
        private val MASQUE_PRESETS = arrayOf("default", "light", "medium", "heavy", "stealth", "gfw", "firewall")

        fun fromIntent(intent: Intent, bindAddress: String): VpnConfig {
            val masquePresetIndex = intent.getIntExtra("USERSETTING_masquepreset_index", 0)
            return VpnConfig(
                endpoint = getEndpoint(intent),
                bindAddress = bindAddress,
                license = intent.getStringExtra("USERSETTING_license").orEmpty().trim(),
                dns = intent.getStringExtra("USERSETTING_dns").orEmpty().ifBlank { "1.1.1.1" },
                vpnDns = intent.getStringExtra("USERSETTING_vpn_dns").orEmpty().ifBlank {
                    intent.getStringExtra("USERSETTING_dns").orEmpty().ifBlank { "1.1.1.1" }
                },
                region = intent.getIntExtra("USERSETTING_region", 0),
                endpointType = intent.getIntExtra("USERSETTING_endpoint_type", 0),
                gool = intent.getBooleanExtra("USERSETTING_gool", false),
                masque = intent.getBooleanExtra("USERSETTING_masque", false),
                masquePreferred = intent.getBooleanExtra("USERSETTING_masquepreferred", false),
                masqueNoize = intent.getBooleanExtra("USERSETTING_masquenoize", false),
                masquePreset = MASQUE_PRESETS.getOrNull(masquePresetIndex) ?: "default",
                proxyMode = intent.getBooleanExtra("USERSETTING_proxymode", false),
                anycastIPs = intent.getStringExtra("USERSETTING_anycast_ips").orEmpty(),
                preferredFingerprint = intent.getStringExtra("USERSETTING_preferred_fprint").orEmpty(),
                port = intent.getStringExtra("USERSETTING_port").orEmpty().trim(),
                conduit = intent.getBooleanExtra("USERSETTING_conduit", false),
                psiphonCountry = intent.getStringExtra("USERSETTING_psiphon_country").orEmpty(),
                lan = intent.getBooleanExtra("USERSETTING_lan", false),
                proxyBypass = intent.getStringExtra("USERSETTING_proxy_bypass").orEmpty()
            )
        }

        private fun getEndpoint(intent: Intent): String {
            val endpoint = intent.getStringExtra("USERSETTING_endpoint").orEmpty().trim()
            val isAuto = endpoint.isEmpty() ||
                        endpoint == "engage.cloudflareclient.com:2408" ||
                        endpoint.equals("auto", ignoreCase = true)
            return if (isAuto) "" else endpoint
        }
    }
}
