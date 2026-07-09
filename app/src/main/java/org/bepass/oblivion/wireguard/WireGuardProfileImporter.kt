package org.bepass.oblivion.wireguard

import java.nio.charset.StandardCharsets
import okio.ByteString.Companion.decodeBase64

internal data class SanitizedWireGuardProfile(
  val content: String,
  val privateKey: String,
  val presharedKeys: List<String>,
)

internal object WireGuardProfileImporter {
  private const val MAX_PROFILE_BYTES = 1024 * 1024
  private const val WIREGUARD_KEY_BYTES = 32
  private val interfaceFields = setOf("address", "dns", "mtu", "privatekey")
  private val peerFields =
    setOf("publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive")

  fun sanitize(profile: String): SanitizedWireGuardProfile {
    require(profile.toByteArray(StandardCharsets.UTF_8).size in 1..MAX_PROFILE_BYTES) {
      "WireGuard profile is empty or too large"
    }
    val interfaceValues = linkedMapOf<String, String>()
    val peers = mutableListOf<LinkedHashMap<String, String>>()
    var section = ""
    var currentPeer: LinkedHashMap<String, String>? = null

    profile.lineSequence().forEachIndexed { index, rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
      if (line.startsWith("[") && line.endsWith("]")) {
        when (line.substring(1, line.length - 1).trim().lowercase()) {
          "interface" -> {
            require(section.isEmpty() && peers.isEmpty()) {
              "WireGuard interface section must be first"
            }
            section = "interface"
          }
          "peer" -> {
            require(section.isNotEmpty()) { "WireGuard peer appears before interface" }
            currentPeer?.let(peers::add)
            currentPeer = linkedMapOf()
            section = "peer"
          }
          else -> error("Unsupported WireGuard section at line ${index + 1}")
        }
        return@forEachIndexed
      }

      val separator = line.indexOf('=')
      require(separator > 0) { "Invalid WireGuard assignment at line ${index + 1}" }
      val key = line.substring(0, separator).trim().lowercase()
      val value = line.substring(separator + 1).trim()
      require(value.isNotEmpty() && value.none(Char::isISOControl)) {
        "Invalid WireGuard value at line ${index + 1}"
      }
      val values =
        when (section) {
          "interface" -> interfaceValues
          "peer" -> requireNotNull(currentPeer)
          else -> error("WireGuard assignment appears before interface")
        }
      val allowed = if (section == "interface") interfaceFields else peerFields
      require(key in allowed) { "Unsupported WireGuard field at line ${index + 1}" }
      require(values.putIfAbsent(key, value) == null) {
        "Duplicate WireGuard field at line ${index + 1}"
      }
    }
    currentPeer?.let(peers::add)

    val privateKey = requireKey(interfaceValues.remove("privatekey"), "private")
    require(interfaceValues["address"].orEmpty().isNotBlank()) {
      "WireGuard profile requires Address"
    }
    require(peers.isNotEmpty()) { "WireGuard profile requires at least one peer" }
    val presharedKeys = peers.mapIndexed { index, peer ->
      requireKey(peer["publickey"], "peer ${index + 1} public")
      require(peer["allowedips"].orEmpty().isNotBlank()) {
        "WireGuard peer ${index + 1} requires AllowedIPs"
      }
      require(peer["endpoint"].orEmpty().isNotBlank()) {
        "WireGuard peer ${index + 1} requires Endpoint"
      }
      peer.remove("presharedkey")?.let { requireKey(it, "peer ${index + 1} preshared") }.orEmpty()
    }

    return SanitizedWireGuardProfile(
      content = render(interfaceValues, peers),
      privateKey = privateKey,
      presharedKeys = presharedKeys,
    )
  }

  fun restoreSecrets(
    sanitizedProfile: String,
    privateKey: String,
    presharedKeys: List<String>,
  ): String {
    requireKey(privateKey, "private")
    presharedKeys.filter(String::isNotEmpty).forEachIndexed { index, key ->
      requireKey(key, "peer ${index + 1} preshared")
    }
    val peerCount =
      sanitizedProfile.lineSequence().count {
        it.trim().equals("[Peer]", ignoreCase = true)
      }
    require(peerCount == presharedKeys.size) { "WireGuard peer secret count mismatch" }

    var peerIndex = -1
    return buildString {
        sanitizedProfile.lineSequence().forEach { rawLine ->
          val line = rawLine.trim()
          appendLine(rawLine)
          when {
            line.equals("[Interface]", ignoreCase = true) -> appendLine("PrivateKey = $privateKey")
            line.equals("[Peer]", ignoreCase = true) -> {
              peerIndex++
              presharedKeys[peerIndex].takeIf(String::isNotEmpty)?.let {
                appendLine("PresharedKey = $it")
              }
            }
          }
        }
      }
      .trimEnd()
      .plus("\n")
  }

  private fun render(
    interfaceValues: Map<String, String>,
    peers: List<Map<String, String>>,
  ): String =
    buildString {
        appendLine("[Interface]")
        appendField(interfaceValues, "address", "Address")
        appendField(interfaceValues, "dns", "DNS")
        appendField(interfaceValues, "mtu", "MTU")
        peers.forEach { peer ->
          appendLine()
          appendLine("[Peer]")
          appendField(peer, "publickey", "PublicKey")
          appendField(peer, "allowedips", "AllowedIPs")
          appendField(peer, "endpoint", "Endpoint")
          appendField(peer, "persistentkeepalive", "PersistentKeepalive")
        }
      }
      .trimEnd()
      .plus("\n")

  private fun StringBuilder.appendField(values: Map<String, String>, key: String, label: String) {
    values[key]?.let { appendLine("$label = $it") }
  }

  private fun requireKey(value: String?, label: String): String {
    require(!value.isNullOrBlank()) { "WireGuard $label key is missing" }
    val decoded =
      value.decodeBase64()?.toByteArray()
        ?: throw IllegalArgumentException("WireGuard $label key is invalid")
    try {
      require(decoded.size == WIREGUARD_KEY_BYTES && decoded.any { it.toInt() != 0 }) {
        "WireGuard $label key is invalid"
      }
    } finally {
      decoded.fill(0)
    }
    return value
  }
}
