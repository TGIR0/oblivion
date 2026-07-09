package org.bepass.oblivion.service

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceStartPolicyTest {
  @Test
  fun `explicit start is redelivered after process death`() {
    val decision =
      VpnServiceStartPolicy.decide(
        action = OblivionVpnService.FLAG_VPN_START,
        alwaysOn = false,
        selectedCoreReady = true,
      )

    assertEquals(VpnStartDirective.START_APP, decision.directive)
    assertEquals(VpnRestartMode.REDELIVER_INTENT, decision.restartMode)
  }

  @Test
  fun `explicit stop is never sticky`() {
    val decision =
      VpnServiceStartPolicy.decide(
        action = OblivionVpnService.FLAG_VPN_STOP,
        alwaysOn = true,
        selectedCoreReady = true,
      )

    assertEquals(VpnStartDirective.STOP, decision.directive)
    assertEquals(VpnRestartMode.NOT_STICKY, decision.restartMode)
  }

  @Test
  fun `unflagged always-on system start restores stored configuration`() {
    val decision =
      VpnServiceStartPolicy.decide(
        action = null,
        alwaysOn = true,
        selectedCoreReady = true,
      )

    assertEquals(VpnStartDirective.START_ALWAYS_ON, decision.directive)
    assertEquals(VpnRestartMode.STICKY, decision.restartMode)
  }

  @Test
  fun `stale sticky restart outside always-on is rejected`() {
    val decision =
      VpnServiceStartPolicy.decide(
        action = null,
        alwaysOn = false,
        selectedCoreReady = true,
      )

    assertEquals(VpnStartDirective.IGNORE, decision.directive)
    assertEquals(VpnRestartMode.NOT_STICKY, decision.restartMode)
  }

  @Test
  fun `unavailable core blocks explicit and always-on starts`() {
    val explicit =
      VpnServiceStartPolicy.decide(
        action = OblivionVpnService.FLAG_VPN_START,
        alwaysOn = false,
        selectedCoreReady = false,
      )
    val alwaysOn =
      VpnServiceStartPolicy.decide(
        action = null,
        alwaysOn = true,
        selectedCoreReady = false,
      )

    assertEquals(VpnStartDirective.IGNORE, explicit.directive)
    assertEquals(VpnRestartMode.NOT_STICKY, explicit.restartMode)
    assertEquals(VpnStartDirective.IGNORE, alwaysOn.directive)
    assertEquals(VpnRestartMode.NOT_STICKY, alwaysOn.restartMode)
  }
}
