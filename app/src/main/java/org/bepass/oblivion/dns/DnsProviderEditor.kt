package org.bepass.oblivion.dns

object DnsProviderEditor {
  fun build(
    existing: DnsProvider?,
    providerId: String,
    label: String,
    transport: DnsTransport,
    endpointInput: String,
  ): DnsProvider {
    val normalizedLabel = label.trim()
    require(normalizedLabel.isNotBlank()) { "DNS name is required" }
    require(transport in setOf(DnsTransport.PLAIN, DnsTransport.DOH, DnsTransport.DOT)) {
      "Unsupported DNS transport"
    }

    val base =
      existing
        ?: DnsProvider(
          providerId = providerId,
          label = normalizedLabel,
          tags = listOf("custom"),
          unverified = true,
        )

    return when (transport) {
      DnsTransport.PLAIN -> {
        val parsed = DnsUriParser.parseBulk(endpointInput)
        require(parsed.isValid) { parsed.errors.joinToString("\n") }
        require(
          parsed.endpoints.all {
            it.transport == DnsTransport.PLAIN && it.isIpLiteral && !it.host.isNullOrBlank()
          }
        ) {
          "Plain DNS requires IPv4 or IPv6 addresses"
        }
        val ips = parsed.endpoints.mapNotNull { it.host }.distinct()
        base.copy(
          label = normalizedLabel,
          transports =
            base.transports + setOf(DnsTransport.PLAIN, DnsTransport.UDP, DnsTransport.TCP),
          plainIps = ips,
          bootstrapIps = (base.bootstrapIps + ips).distinct(),
          ports =
            base.ports +
              mapOf(
                DnsTransport.PLAIN to DNS_PORT,
                DnsTransport.UDP to DNS_PORT,
                DnsTransport.TCP to DNS_PORT,
              ),
        )
      }
      DnsTransport.DOH -> {
        val endpoint = DnsUriParser.parse(endpointInput)
        require(endpoint.transport == DnsTransport.DOH) { "DoH requires an HTTPS URL" }
        base.copy(
          label = normalizedLabel,
          transports = base.transports + DnsTransport.DOH,
          dohUrl = endpoint.normalized,
          ports = base.ports + (DnsTransport.DOH to (endpoint.port ?: DNS_HTTPS_PORT)),
        )
      }
      DnsTransport.DOT -> {
        val raw = endpointInput.trim()
        val endpoint = DnsUriParser.parse(if (raw.contains("://")) raw else "tls://$raw")
        require(endpoint.transport == DnsTransport.DOT) { "DoT requires a TLS hostname" }
        base.copy(
          label = normalizedLabel,
          transports = base.transports + DnsTransport.DOT,
          dotHost = endpoint.host,
          ports = base.ports + (DnsTransport.DOT to (endpoint.port ?: DNS_TLS_PORT)),
        )
      }
      else -> error("Unsupported DNS transport")
    }
  }
}
