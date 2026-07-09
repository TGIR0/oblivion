package org.bepass.oblivion.ui

import org.bepass.oblivion.enums.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAvailabilityPolicyTest {
  @Test
  fun `disconnected unavailable core cannot be started`() {
    assertFalse(canToggleVpn(ConnectionState.DISCONNECTED, selectedCoreReady = false))
  }

  @Test
  fun `ready core can be started`() {
    assertTrue(canToggleVpn(ConnectionState.DISCONNECTED, selectedCoreReady = true))
  }

  @Test
  fun `active connection can always be stopped`() {
    assertTrue(canToggleVpn(ConnectionState.CONNECTING, selectedCoreReady = false))
    assertTrue(canToggleVpn(ConnectionState.CONNECTED, selectedCoreReady = false))
  }
}
