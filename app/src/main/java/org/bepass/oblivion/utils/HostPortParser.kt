package org.bepass.oblivion.utils

object HostPortParser {
  private const val PORT_UNSPECIFIED = -1
  private const val MIN_PORT = 1
  private const val MAX_PORT = 65_535

  data class HostPort(val host: String, val port: Int) {
    fun host(): String = host

    fun port(): Int = port
  }

  /**
   * Parses host:port in one of these forms:
   * - example.com:443
   * - 127.0.0.1:8080
   * - [2001:db8::1]:443
   * - [2001:db8::1]
   *
   * Returns null if the input is empty or invalid. If the port is missing, returns port=-1.
   */
  @JvmStatic
  fun parseOrNull(input: String?): HostPort? = run {
    val value = input?.trim() ?: return@run null
    if (value.isEmpty()) return@run null

    // Bracketed IPv6: [::1]:443 or [::1]
    if (value.startsWith("[")) {
      val close = value.indexOf(']')
      if (close <= 1) return@run null
      val host = value.substring(1, close).trim()
      if (host.isEmpty()) return@run null

      if (value.length == close + 1) {
        return@run HostPort(host, PORT_UNSPECIFIED)
      }
      if (value[close + 1] != ':') return@run null
      val portPart = value.substring(close + 2).trim()
      if (portPart.isEmpty()) return@run null
      val port = parsePortOrInvalid(portPart)
      if (port == PORT_UNSPECIFIED) return@run null
      return@run HostPort(host, port)
    }

    val lastColon = value.lastIndexOf(':')
    if (lastColon < 0) return@run HostPort(value, PORT_UNSPECIFIED)
    if (lastColon == 0) return@run null

    // If there are multiple colons, assume it's an unbracketed IPv6 without port.
    if (value.indexOf(':') != lastColon) return@run HostPort(value, PORT_UNSPECIFIED)

    val host = value.substring(0, lastColon).trim()
    val portPart = value.substring(lastColon + 1).trim()
    if (host.isEmpty() || portPart.isEmpty()) return@run null
    val port = parsePortOrInvalid(portPart)
    if (port == PORT_UNSPECIFIED) return@run null
    HostPort(host, port)
  }

  private fun parsePortOrInvalid(portPart: String): Int =
    portPart.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT } ?: PORT_UNSPECIFIED
}
