package org.bepass.oblivion.wireguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardProfileImporterTest {
  @Test
  fun stripsAllSecretsAndPreservesMultiplePeers() {
    val imported =
      WireGuardProfileImporter.sanitize(
        """
        [Interface]
        PrivateKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
        Address = 10.0.0.2/32, fd00::2/128
        DNS = 1.1.1.1
        MTU = 1280

        [Peer]
        PublicKey = AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=
        PresharedKey = AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=
        AllowedIPs = 0.0.0.0/0
        Endpoint = vpn.example.com:51820
        PersistentKeepalive = 25

        [Peer]
        PublicKey = BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=
        AllowedIPs = ::/0
        Endpoint = [2001:db8::1]:51820
        """
          .trimIndent()
      )

    assertEquals(2, imported.presharedKeys.size)
    assertTrue(imported.presharedKeys[1].isEmpty())
    assertFalse(imported.content.contains("PrivateKey", ignoreCase = true))
    assertFalse(imported.content.contains("PresharedKey", ignoreCase = true))
    assertEquals(2, Regex("\\[Peer]", RegexOption.IGNORE_CASE).findAll(imported.content).count())

    val restored =
      WireGuardProfileImporter.restoreSecrets(
        imported.content,
        imported.privateKey,
        imported.presharedKeys,
      )
    assertTrue(restored.contains("PrivateKey = ${imported.privateKey}"))
    assertTrue(restored.contains("PresharedKey = ${imported.presharedKeys[0]}"))
    assertEquals(1, Regex("PresharedKey", RegexOption.IGNORE_CASE).findAll(restored).count())
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsUnsupportedFields() {
    WireGuardProfileImporter.sanitize(
      """
      [Interface]
      PrivateKey = AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=
      Address = 10.0.0.2/32
      Table = off
      """
        .trimIndent()
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsZeroKeys() {
    WireGuardProfileImporter.sanitize(
      """
      [Interface]
      PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
      Address = 10.0.0.2/32
      """
        .trimIndent()
    )
  }
}
