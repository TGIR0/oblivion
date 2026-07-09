package org.bepass.oblivion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
  @Test
  fun `official WARP ranges and ports are accepted`() {
    assertTrue(isAllowedWarpEndpoint("162.159.192.1:2408"))
    assertTrue(isAllowedWarpEndpoint("162.159.193.255:4500"))
    assertTrue(isAllowedWarpEndpoint("[2606:4700:100::1]:500"))
  }

  @Test
  fun `untrusted ranges ports hostnames and injection are rejected`() {
    assertFalse(isAllowedWarpEndpoint("127.0.0.1:2408"))
    assertFalse(isAllowedWarpEndpoint("162.159.192.1:443"))
    assertFalse(isAllowedWarpEndpoint("example.com:2408"))
    assertFalse(isAllowedWarpEndpoint("162.159.192.1:2408\npublic_key=attacker"))
  }
}
