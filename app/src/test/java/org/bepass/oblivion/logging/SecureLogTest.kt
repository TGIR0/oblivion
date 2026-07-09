package org.bepass.oblivion.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureLogTest {
  @Test
  fun redactsSecretsAddressesDomainsAndUrls() {
    val redacted =
      SecureLog.redact(
        "token=abc password:xyz host.example 192.0.2.4 2001:db8::1 https://example.net/path"
      )

    assertFalse(redacted.contains("abc"))
    assertFalse(redacted.contains("xyz"))
    assertFalse(redacted.contains("host.example"))
    assertFalse(redacted.contains("192.0.2.4"))
    assertFalse(redacted.contains("2001:db8::1"))
    assertFalse(redacted.contains("example.net"))
    assertTrue(redacted.contains("[REDACTED]"))
  }

  @Test
  fun stripsControlCharactersAndBoundsLength() {
    val redacted = SecureLog.redact("x\n\t".repeat(400))
    assertFalse(redacted.contains('\n'))
    assertFalse(redacted.contains('\t'))
    assertTrue(redacted.length <= 512)
  }
}
