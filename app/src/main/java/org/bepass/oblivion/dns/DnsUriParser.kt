package org.bepass.oblivion.dns

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.text.Regex

object DnsUriParser {
  private val splitRegex = Regex("[,;\\n\\r]+")
  private val ipv4CandidateRegex = Regex("^[0-9.]+$")
  private val ipv6CandidateRegex = Regex("^[0-9a-fA-F:%.]+$")

  data class ValidationResult(
    val endpoints: List<DnsEndpoint>,
    val errors: List<String>,
  ) {
    val isValid: Boolean
      get() = errors.isEmpty() && endpoints.isNotEmpty()
  }

  fun parseBulk(rawInput: String): ValidationResult {
    val tokens =
      rawInput.split(splitRegex).asSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

    if (tokens.isEmpty()) {
      return ValidationResult(emptyList(), listOf("Empty DNS input"))
    }

    val endpoints = mutableListOf<DnsEndpoint>()
    val errors = mutableListOf<String>()
    for (token in tokens) {
      runCatching { parse(token) }
        .onSuccess { endpoints += it }
        .onFailure { errors += "${token}: ${it.message.orEmpty()}" }
    }

    return ValidationResult(
      endpoints = endpoints,
      errors = errors,
    )
  }

  fun validate(rawInput: String): String? {
    val result = parseBulk(rawInput)
    return if (result.errors.isEmpty()) null else result.errors.joinToString("\n")
  }

  fun parse(rawInput: String): DnsEndpoint {
    val raw = rawInput.trim()
    require(raw.isNotEmpty()) { "DNS value is empty" }

    if (raw.equals("system", ignoreCase = true)) {
      return DnsEndpoint(raw = raw, normalized = "system", transport = DnsTransport.SYSTEM)
    }

    parsePlainIp(raw)?.let { ip ->
      return DnsEndpoint(
        raw = raw,
        normalized = ip,
        transport = DnsTransport.PLAIN,
        host = ip,
        isIpLiteral = true,
      )
    }

    val uri =
      try {
        URI(raw)
      } catch (syntaxFailure: URISyntaxException) {
        throw IllegalArgumentException("Invalid DNS URI", syntaxFailure)
      }

    val scheme =
      uri.scheme?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing URI scheme")
    val transport =
      when (scheme) {
        "udp" -> DnsTransport.UDP
        "tcp" -> DnsTransport.TCP
        "tls" -> DnsTransport.DOT
        "quic" -> DnsTransport.DOQ
        "https" -> DnsTransport.DOH
        "h3" -> DnsTransport.DOH3
        else -> throw IllegalArgumentException("Unsupported DNS scheme: $scheme")
      }

    val host = uri.host ?: parseHostFromAuthority(uri.rawAuthority)
    require(!host.isNullOrBlank()) { "Missing DNS host" }

    val port =
      when {
        uri.port >= 0 -> uri.port
        transport == DnsTransport.UDP || transport == DnsTransport.TCP -> 53
        transport == DnsTransport.DOT || transport == DnsTransport.DOQ -> 853
        else -> 443
      }

    val path =
      when (transport) {
        DnsTransport.DOH,
        DnsTransport.DOH3 -> uri.rawPath?.takeIf { it.isNotBlank() } ?: "/dns-query"
        else -> null
      }

    val normalized =
      when (transport) {
        DnsTransport.DOH -> "https://${normalizeHostForUri(host)}:${port}${path}"
        DnsTransport.DOH3 -> "h3://${normalizeHostForUri(host)}:${port}${path}"
        DnsTransport.UDP -> "udp://${normalizeHostForUri(host)}:$port"
        DnsTransport.TCP -> "tcp://${normalizeHostForUri(host)}:$port"
        DnsTransport.DOT -> "tls://${normalizeHostForUri(host)}:$port"
        DnsTransport.DOQ -> "quic://${normalizeHostForUri(host)}:$port"
        else -> raw
      }

    val ip = parsePlainIp(host)
    return DnsEndpoint(
      raw = raw,
      normalized = normalized,
      transport = transport,
      host = host,
      port = port,
      path = path,
      isIpLiteral = ip != null,
    )
  }

  private fun parseHostFromAuthority(authority: String?): String? {
    if (authority.isNullOrBlank()) return null
    val decoded = URLDecoder.decode(authority, StandardCharsets.UTF_8.name())
    val noUserInfo = decoded.substringAfterLast('@')
    return when {
        noUserInfo.startsWith("[") -> noUserInfo.substringAfter("[").substringBefore("]")
        noUserInfo.count { it == ':' } > 1 -> noUserInfo
        else -> noUserInfo.substringBefore(":")
      }
      .trim()
      .ifBlank { null }
  }

  private fun normalizeHostForUri(host: String): String =
    if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

  private fun parsePlainIp(value: String): String? {
    val candidate = value.removePrefix("[").removeSuffix("]")
    val literalCandidate =
      when {
        candidate.contains(':') -> ipv6CandidateRegex.matches(candidate)
        candidate.contains('.') -> ipv4CandidateRegex.matches(candidate)
        else -> false
      }
    if (!literalCandidate) return null
    return try {
      java.net.InetAddress.getByName(candidate)
      candidate
    } catch (_: UnknownHostException) {
      null
    }
  }
}
