package org.bepass.oblivion.enums

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class VpnCoreTypeTest {

  @Test
  fun `storage ids are stable and unique`() {
    assertEquals((0..6).toList(), VpnCoreType.entries.map(VpnCoreType::storageId))
    assertEquals(
      VpnCoreType.entries.size,
      VpnCoreType.entries.map(VpnCoreType::storageId).toSet().size,
    )
    assertEquals(
      VpnCoreType.entries.size,
      VpnCoreType.entries.map(VpnCoreType::modeId).toSet().size,
    )
  }

  @Test
  fun `stored ids resolve to the expected core`() {
    VpnCoreType.entries.forEach { core ->
      assertSame(core, VpnCoreType.fromInt(core.storageId))
    }
  }

  @Test
  fun `unknown stored id falls back to warp`() {
    assertSame(VpnCoreType.WARP, VpnCoreType.fromInt(Int.MAX_VALUE))
  }

  @Test
  fun `no mode is selectable before live release gates pass`() {
    VpnCoreType.entries.forEach { core ->
      assertFalse("${core.modeId} was enabled without gate evidence", core.isReady)
    }
  }
}
