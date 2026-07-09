package org.bepass.oblivion.model

import kotlinx.serialization.Serializable

const val TUNNEL_CONFIG_SCHEMA_V2 = 2

@Serializable
enum class TunnelMode {
  WARP,
  VWARP_MASQUE,
  PSIPHON,
  PSIPHON_OVER_WARP,
  WARP_OVER_PSIPHON,
  PSIPHON_FRONTED,
  WIREGUARD,
}

@Serializable
enum class VWarpPolicy {
  STANDARD,
  ADAPTIVE,
  TCP_ONLY,
}

@Serializable
data class TunnelAccountConfig(
  val identityKeyRef: String = "warp.identity.v2",
  val masqueIdentityKeyRef: String = "masque.identity.v2",
  val licenseKeyRef: String = "warp.license.v2",
  val acceptCloudflareTos: Boolean = false,
)

@Serializable
data class RemotePolicyConfigV2(
  val required: Boolean = true,
  val envelopePath: String = "",
  val publicKeysJson: String = "",
  val stateKeyRef: String = "feature.manifest.state.v1",
)

@Serializable
data class PsiphonConfigV2(
  val country: String = "",
  val sponsorIdKeyRef: String = "",
  val propagationChannelKeyRef: String = "",
  val frontManifestPath: String = "",
)

@Serializable
data class WireGuardConfigV2(
  val profilePath: String = "",
  val privateKeyRef: String = "",
  val presharedKeyRefs: List<String> = emptyList(),
)

@Serializable
data class TunnelConfigV2(
  val schemaVersion: Int = TUNNEL_CONFIG_SCHEMA_V2,
  val mode: TunnelMode,
  val tunFd: Int = 0,
  val proxyOnly: Boolean = false,
  val mtu: Int = 1280,
  val dns: List<String> = listOf("1.1.1.1", "1.0.0.1"),
  val endpoint: String = "",
  val vwarpPolicy: VWarpPolicy = VWarpPolicy.ADAPTIVE,
  val account: TunnelAccountConfig = TunnelAccountConfig(),
  val remotePolicy: RemotePolicyConfigV2 = RemotePolicyConfigV2(),
  val psiphon: PsiphonConfigV2 = PsiphonConfigV2(),
  val wireGuard: WireGuardConfigV2 = WireGuardConfigV2(),
)
