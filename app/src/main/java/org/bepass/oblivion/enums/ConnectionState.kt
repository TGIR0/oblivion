package org.bepass.oblivion.enums

enum class ConnectionState {
  CONNECTING,
  CONNECTED,
  DISCONNECTED;

  val isDisconnected: Boolean
    get() = this == DISCONNECTED

  val isConnecting: Boolean
    get() = this == CONNECTING
}
