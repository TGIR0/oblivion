package org.bepass.oblivion.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsHealthCheckerTest {
  @Test
  fun `query contains transaction id recursion flag and one question`() {
    val query = DnsHealthChecker.buildDnsQuery("example.com", 0x1234)

    assertEquals(0x12, query[0].toInt() and 0xFF)
    assertEquals(0x34, query[1].toInt() and 0xFF)
    assertEquals(0x01, query[2].toInt() and 0xFF)
    assertEquals(0x00, query[3].toInt() and 0xFF)
    assertEquals(1, query[5].toInt() and 0xFF)
    assertTrue(query.size >= 29)
  }

  @Test
  fun `valid response requires matching id response flag and accepted response code`() {
    val query = DnsHealthChecker.buildDnsQuery("example.com", 0x2233)
    val response =
      query.copyOf().apply {
        this[2] = 0x81.toByte()
        this[3] = 0x80.toByte()
      }

    assertTrue(DnsHealthChecker.isValidDnsResponse(response, 0x2233))
    assertFalse(DnsHealthChecker.isValidDnsResponse(response, 0x2234))
    assertFalse(DnsHealthChecker.isValidDnsResponse(byteArrayOf(0x22, 0x33), 0x2233))
  }
}
